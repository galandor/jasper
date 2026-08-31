package com.jasper.facemirror.chassis

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.IOException
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger

/**
 * Bluetooth Classic SPP → HC-06. Протокол машинки: `%A#`, `%S#`, …
 * Импульсные команды A–F повторяются, пока не придёт стоп.
 */
class ChassisDriver(
    private val context: Context,
    private val scope: CoroutineScope,
) {
    @Volatile
    var isConnected: Boolean = false
        private set

    @Volatile
    var connectFinished: Boolean = false
        private set

    @Volatile
    var isDriving: Boolean = false
        private set

    /** Последняя дистанция HC-SR04, см. 0 = нет эха. */
    private val _sonarCm = MutableStateFlow<Int?>(null)
    val sonarCm: StateFlow<Int?> = _sonarCm

    var onSonarCm: ((Int) -> Unit)? = null

    private val mutex = Mutex()
    private val holdEpoch = AtomicInteger(0)
    private var socket: BluetoothSocket? = null
    private var holdJob: Job? = null
    private var connectJob: Job? = null
    private var queueJob: Job? = null
    private var readJob: Job? = null

    fun start() {
        connectFinished = false
        connectJob?.cancel()
        connectJob = scope.launch(Dispatchers.IO) {
            connectLocked()
        }
    }

    suspend fun reconnect(): Boolean {
        connectJob?.cancel()
        connectJob = null
        readJob?.cancel()
        readJob = null
        connectFinished = false
        isConnected = false
        runCatching { socket?.close() }
        socket = null
        _sonarCm.value = null
        withContext(Dispatchers.IO) {
            connectLocked()
        }
        return isConnected
    }

    fun execute(action: DriveAction, holdMs: Long = action.holdMs) {
        queueJob?.cancel()
        queueJob = null
        executeNow(action, holdMs)
    }

    /** Держать моторы, пока не придёт другая команда. Джойстик и автопилот. */
    fun holdUntilStopped(action: DriveAction) {
        if (!action.hold) {
            execute(action)
            return
        }
        execute(action, holdMs = Long.MAX_VALUE)
    }

    /**
     * Несколько команд из одной реплики, по очереди: каждая ждёт свой импульс.
     * Любая одиночная команда и «стоп» сбрасывают очередь.
     */
    fun executeSequence(actions: List<DriveAction>) {
        val queue = actions.take(MAX_QUEUE_SIZE)
        if (queue.size <= 1) {
            queue.firstOrNull()?.let { execute(it) }
            return
        }
        queueJob?.cancel()
        queueJob = scope.launch {
            for (action in queue) {
                executeNow(action, action.holdMs)
                if (action == DriveAction.STOP) break
                delay(if (action.hold) action.holdMs + QUEUE_GAP_MS else QUEUE_GAP_MS)
            }
        }
    }

    private fun executeNow(action: DriveAction, holdMs: Long = action.holdMs) {
        if (action == DriveAction.CONNECT) {
            start()
            return
        }

        if (!isConnected && connectFinished) {
            start()
        }

        val epoch = holdEpoch.incrementAndGet()
        holdJob?.cancel()
        holdJob = null

        if (action == DriveAction.STOP) {
            isDriving = false
            scope.launch(Dispatchers.IO) { write('S') }
            return
        }

        isDriving = true
        if (action.hold) {
            holdJob = scope.launch(Dispatchers.IO) {
                val startedAt = System.currentTimeMillis()
                try {
                    val unlimited = holdMs == Long.MAX_VALUE
                    while (isActive && (unlimited || System.currentTimeMillis() - startedAt < holdMs)) {
                        write(action.code)
                        delay(HOLD_INTERVAL_MS)
                    }
                    if (isActive) write('S')
                } finally {
                    if (holdEpoch.get() == epoch) isDriving = false
                }
            }
        } else {
            scope.launch(Dispatchers.IO) { write(action.code) }
        }
    }

    fun stop() {
        execute(DriveAction.STOP)
    }

    fun release() {
        queueJob?.cancel()
        holdJob?.cancel()
        connectJob?.cancel()
        readJob?.cancel()
        queueJob = null
        holdJob = null
        connectJob = null
        readJob = null
        isConnected = false
        isDriving = false
        _sonarCm.value = null
        val toClose = socket
        socket = null
        try {
            toClose?.outputStream?.write("%S#".toByteArray(Charsets.US_ASCII))
            toClose?.outputStream?.flush()
        } catch (_: Exception) {
            // Сокет уже закрыт или ещё не открыт
        }
        runCatching { toClose?.close() }
    }

    @SuppressLint("MissingPermission")
    private suspend fun connectLocked() {
        try {
            mutex.withLock {
                closeSocketLocked()
                val adapter = bluetoothAdapter() ?: return
                if (!adapter.isEnabled) {
                    Log.w(TAG, "Bluetooth выключен")
                    return
                }
                val device = findChassis(adapter) ?: run {
                    Log.w(TAG, "HC-06 не в сопряжённых устройствах")
                    return
                }
                runCatching { adapter.cancelDiscovery() }
                val opened = openSocket(device) ?: return
                socket = opened
                isConnected = true
                startReader(opened)
                Log.i(TAG, "Подключен к ${device.name}")
            }
        } finally {
            connectFinished = true
        }
    }

    @SuppressLint("MissingPermission")
    private fun findChassis(adapter: BluetoothAdapter): BluetoothDevice? =
        adapter.bondedDevices.firstOrNull { device ->
            val name = device.name.orEmpty()
            CHASSIS_NAME_HINTS.any { hint -> name.contains(hint, ignoreCase = true) }
        }

    @SuppressLint("MissingPermission")
    private fun openSocket(device: BluetoothDevice): BluetoothSocket? {
        val primary = device.createRfcommSocketToServiceRecord(SPP_UUID)
        try {
            primary.connect()
            return primary
        } catch (e: IOException) {
            Log.w(TAG, "SPP UUID не открылся, пробую канал 1", e)
            runCatching { primary.close() }
        }
        return try {
            val fallback = device.javaClass
                .getMethod("createRfcommSocket", Int::class.javaPrimitiveType)
                .invoke(device, 1) as BluetoothSocket
            fallback.connect()
            fallback
        } catch (e: Exception) {
            Log.e(TAG, "Не удалось открыть сокет к ${device.name}", e)
            null
        }
    }

    private suspend fun write(code: Char) {
        mutex.withLock {
            val current = socket
            if (current == null || !current.isConnected) {
                isConnected = false
                return
            }
            try {
                current.outputStream.write("%$code#".toByteArray(Charsets.US_ASCII))
                current.outputStream.flush()
            } catch (e: IOException) {
                Log.w(TAG, "Запись в HC-06 не удалась", e)
                closeSocketLocked()
            }
        }
    }

    private fun startReader(opened: BluetoothSocket) {
        readJob?.cancel()
        readJob = scope.launch(Dispatchers.IO) {
            val incoming = StringBuilder()
            val buffer = ByteArray(64)
            try {
                val stream = opened.inputStream
                while (isActive) {
                    val n = stream.read(buffer)
                    if (n <= 0) break
                    incoming.append(String(buffer, 0, n, Charsets.US_ASCII))
                    var newline = incoming.indexOf("\n")
                    while (newline >= 0) {
                        val line = incoming.substring(0, newline).trim('\r', ' ', '\t')
                        incoming.delete(0, newline + 1)
                        parseSonarLine(line)
                        newline = incoming.indexOf("\n")
                    }
                    if (incoming.length > 32) incoming.clear()
                }
            } catch (e: IOException) {
                if (isActive) Log.w(TAG, "Чтение HC-06 оборвалось", e)
            }
        }
    }

    private fun parseSonarLine(line: String) {
        if (line.isEmpty()) return
        val payload = when {
            line.startsWith("D:") -> line.substring(2)
            line.startsWith("D,") -> line.substring(2)
            else -> return
        }
        val cm = payload.trim().toIntOrNull() ?: return
        val clipped = cm.coerceIn(0, 500)
        _sonarCm.value = clipped
        onSonarCm?.invoke(clipped)
    }

    private fun closeSocketLocked() {
        isConnected = false
        readJob?.cancel()
        readJob = null
        runCatching { socket?.close() }
        socket = null
        _sonarCm.value = null
    }

    private fun bluetoothAdapter(): BluetoothAdapter? {
        val manager = context.getSystemService(BluetoothManager::class.java)
        return manager?.adapter
    }

    companion object {
        private const val TAG = "JasperChassis"
        private const val HOLD_INTERVAL_MS = 200L
        private const val QUEUE_GAP_MS = 250L
        private const val MAX_QUEUE_SIZE = 4
        private val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
        private val CHASSIS_NAME_HINTS = listOf("HC-06", "HC-05", "HC-08", "linvor", "BT04", "MLN")
    }
}
