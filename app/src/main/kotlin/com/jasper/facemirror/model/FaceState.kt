package com.jasper.facemirror.model

/**
 * Состояние лица, полученное из ML Kit.
 * Все значения нормализованы для плавной анимации UI.
 */
data class FaceState(
    val leftEyeOpen: Float = 1f,
    val rightEyeOpen: Float = 1f,
    val smile: Float = 0f,
    /** Поворот головы влево/вправо, градусы */
    val yaw: Float = 0f,
    /** Наклон вверх/вниз */
    val pitch: Float = 0f,
    /** Наклон головы на бок */
    val roll: Float = 0f,
    val isDetected: Boolean = false,
) {
    companion object {
        val Idle = FaceState()
    }
}
