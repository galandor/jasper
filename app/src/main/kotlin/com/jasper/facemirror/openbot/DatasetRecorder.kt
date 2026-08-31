package com.jasper.facemirror.openbot

import android.content.Context
import android.graphics.Bitmap
import android.os.SystemClock
import android.util.Log
import java.io.BufferedWriter
import java.io.File
import java.io.FileOutputStream
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicLong

/**
 * Логи в раскладке OpenBot, чтобы потом кормить `policy/train.py`
 * или оригинальный Jupyter из репозитория OpenBot.
 *
 * session/
 *   images/{frame}_crop.jpeg
 *   sensor_data/rgbFrames.txt
 *   sensor_data/ctrlLog.txt
 *   sensor_data/indicatorLog.txt
 *   sensor_data/sonarLog.txt   timestamp[ns],distance[cm]
 */
class DatasetRecorder(
    context: Context,
) {
    private val root = File(context.getExternalFilesDir(null), "OpenBot")
    private val frameSeq = AtomicLong(0)
    private var sessionDir: File? = null
    private var imagesDir: File? = null
    private var framesLog: BufferedWriter? = null
    private var ctrlLog: BufferedWriter? = null
    private var indicatorLog: BufferedWriter? = null
    private var sonarLog: BufferedWriter? = null

    @Volatile
    var isRecording: Boolean = false
        private set

    val lastSessionPath: String?
        get() = sessionDir?.absolutePath

    @Synchronized
    fun start(): File {
        stop()
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val session = File(root, stamp)
        val images = File(session, "images")
        val sensors = File(session, "sensor_data")
        images.mkdirs()
        sensors.mkdirs()

        framesLog = open(sensors, "rgbFrames.txt", "timestamp[ns],frame")
        ctrlLog = open(sensors, "ctrlLog.txt", "timestamp[ns],leftCtrl,rightCtrl")
        indicatorLog = open(sensors, "indicatorLog.txt", "timestamp[ns],signal")
        sonarLog = open(sensors, "sonarLog.txt", "timestamp[ns],distance[cm]")
        append(indicatorLog, "${SystemClock.elapsedRealtimeNanos()},0")

        sessionDir = session
        imagesDir = images
        frameSeq.set(0)
        isRecording = true
        Log.i(TAG, "запись → $session")
        return session
    }

    @Synchronized
    fun logControl(control: WheelControl) {
        if (!isRecording) return
        val (left, right) = control.toMotorInts()
        append(ctrlLog, "${SystemClock.elapsedRealtimeNanos()},$left,$right")
    }

    @Synchronized
    fun logSonar(cm: Int) {
        if (!isRecording) return
        append(sonarLog, "${SystemClock.elapsedRealtimeNanos()},$cm")
    }

    @Synchronized
    fun logFrame(crop: Bitmap) {
        if (!isRecording) return
        val dir = imagesDir ?: return
        val id = frameSeq.incrementAndGet()
        val ts = SystemClock.elapsedRealtimeNanos()
        val file = File(dir, "${id}_crop.jpeg")
        FileOutputStream(file).use { out ->
            crop.compress(Bitmap.CompressFormat.JPEG, 80, out)
        }
        append(framesLog, "$ts,$id")
    }

    @Synchronized
    fun stop(): File? {
        if (!isRecording && sessionDir == null) return null
        isRecording = false
        runCatching { framesLog?.close() }
        runCatching { ctrlLog?.close() }
        runCatching { indicatorLog?.close() }
        runCatching { sonarLog?.close() }
        framesLog = null
        ctrlLog = null
        indicatorLog = null
        sonarLog = null
        val done = sessionDir
        sessionDir = null
        imagesDir = null
        Log.i(TAG, "запись стоп ${done?.absolutePath}")
        return done
    }

    private fun open(dir: File, name: String, header: String): BufferedWriter {
        val writer = BufferedWriter(FileWriter(File(dir, name), false))
        writer.append(header)
        writer.newLine()
        writer.flush()
        return writer
    }

    private fun append(writer: BufferedWriter?, line: String) {
        if (writer == null) return
        writer.append(line)
        writer.newLine()
        writer.flush()
    }

    companion object {
        private const val TAG = "JasperOpenBot"
    }
}
