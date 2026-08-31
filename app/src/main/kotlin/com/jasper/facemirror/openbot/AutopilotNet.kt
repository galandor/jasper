package com.jasper.facemirror.openbot

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import org.tensorflow.lite.Interpreter
import java.io.File
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

class AutopilotNet(
    context: Context,
) {
    private val interpreter: Interpreter?
    val modelPath: String?

    init {
        val loaded = loadModel(context)
        interpreter = loaded?.first
        modelPath = loaded?.second
    }

    val isReady: Boolean get() = interpreter != null

    fun predict(crop: Bitmap): WheelControl? {
        val tflite = interpreter ?: return null
        val input = bitmapToInput(crop)
        val output = Array(1) { FloatArray(2) }
        return try {
            tflite.run(input, output)
            WheelControl(
                left = output[0][0].coerceIn(-1f, 1f),
                right = output[0][1].coerceIn(-1f, 1f),
            )
        } catch (e: Exception) {
            Log.w(TAG, "inference failed: ${e.message}")
            null
        }
    }

    fun close() {
        interpreter?.close()
    }

    private fun bitmapToInput(bitmap: Bitmap): ByteBuffer {
        val scaled = if (bitmap.width != OpenBotFrames.WIDTH || bitmap.height != OpenBotFrames.HEIGHT) {
            Bitmap.createScaledBitmap(bitmap, OpenBotFrames.WIDTH, OpenBotFrames.HEIGHT, true)
        } else {
            bitmap
        }
        val buffer = ByteBuffer.allocateDirect(1 * OpenBotFrames.HEIGHT * OpenBotFrames.WIDTH * 3 * 4)
        buffer.order(ByteOrder.nativeOrder())
        val pixels = IntArray(OpenBotFrames.WIDTH * OpenBotFrames.HEIGHT)
        scaled.getPixels(pixels, 0, OpenBotFrames.WIDTH, 0, 0, OpenBotFrames.WIDTH, OpenBotFrames.HEIGHT)
        for (pixel in pixels) {
            buffer.putFloat(((pixel shr 16) and 0xFF) / 255f)
            buffer.putFloat(((pixel shr 8) and 0xFF) / 255f)
            buffer.putFloat((pixel and 0xFF) / 255f)
        }
        if (scaled != bitmap) scaled.recycle()
        buffer.rewind()
        return buffer
    }

    companion object {
        private const val TAG = "JasperOpenBot"
        const val MODEL_NAME = "autopilot_float.tflite"

        private fun loadModel(context: Context): Pair<Interpreter, String>? {
            val candidates = listOfNotNull(
                File(context.filesDir, "openbot/$MODEL_NAME"),
                File(context.getExternalFilesDir(null), "OpenBot/$MODEL_NAME"),
            )
            for (file in candidates) {
                if (!file.isFile || file.length() < 1024) continue
                return runCatching {
                    Interpreter(mapFile(file)) to file.absolutePath
                }.getOrNull()
            }
            return runCatching {
                context.assets.open("networks/$MODEL_NAME").use { input ->
                    val bytes = input.readBytes()
                    if (bytes.size < 1024) return@runCatching null
                    val buffer = ByteBuffer.allocateDirect(bytes.size).order(ByteOrder.nativeOrder())
                    buffer.put(bytes).rewind()
                    Interpreter(buffer) to "assets/networks/$MODEL_NAME"
                }
            }.getOrNull()
        }

        private fun mapFile(file: File): MappedByteBuffer {
            FileInputStream(file).use { stream ->
                val channel = stream.channel
                return channel.map(FileChannel.MapMode.READ_ONLY, 0, channel.size())
            }
        }
    }
}
