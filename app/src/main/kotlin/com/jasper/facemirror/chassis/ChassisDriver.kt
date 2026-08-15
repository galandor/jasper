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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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

    private val mutex = Mutex()
    private val holdEpoch = AtomicInteger(0)
    private var socket: BluetoothSocket? = null
    private var holdJob: Job? = null
    private var connectJob: Job? = null

    fun start() {
        connectFinished = false
        connectJob?.cancel()
        connectJob = scope.launch(Dispatchers.IO) {
            connectLocked()
        }
    }

    fun execute(action: DriveAction) {
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
                    while (isActive && System.currentTimeMillis() - startedAt < action.holdMs) {
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
        holdJob?.cancel()
        connectJob?.cancel()
        holdJob = null
        connectJob = null
        isConnected = false
        isDriving = false
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

    private fun closeSocketLocked() {
        isConnected = false
        runCatching { socket?.close() }
        socket = null
    }

    private fun bluetoothAdapter(): BluetoothAdapter? {
        val manager = context.getSystemService(BluetoothManager::class.java)
        return manager?.adapter
    }

    companion object {
        private const val TAG = "JasperChassis"
        private const val HOLD_INTERVAL_MS = 200L
        private val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
        private val CHASSIS_NAME_HINTS = listOf("HC-06", "HC-05", "HC-08", "linvor", "BT04", "MLN")
    }
}
