package com.jasper.facemirror.openbot

import android.graphics.Bitmap

object OpenBotFrames {
    const val WIDTH = 256
    const val HEIGHT = 96

    /**
     * Как в OpenBot Autopilot: отрезаем верхнюю треть кадра (потолок / лица),
     * оставляем пол и препятствия, жмём до входа сети 256×96.
     */
    fun cropForPolicy(source: Bitmap): Bitmap {
        val top = (source.height * (240f / 720f)).toInt().coerceIn(0, source.height - 1)
        val cropHeight = (source.height - top).coerceAtLeast(1)
        val cropped = Bitmap.createBitmap(source, 0, top, source.width, cropHeight)
        if (cropped.width == WIDTH && cropped.height == HEIGHT) return cropped
        val scaled = Bitmap.createScaledBitmap(cropped, WIDTH, HEIGHT, true)
        if (scaled != cropped) cropped.recycle()
        return scaled
    }
}
