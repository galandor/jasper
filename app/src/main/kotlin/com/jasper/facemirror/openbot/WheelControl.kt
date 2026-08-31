package com.jasper.facemirror.openbot

import com.jasper.facemirror.chassis.DriveAction
import kotlin.math.abs

/** Дифференциал OpenBot: −1…1 на левый и правый борт. */
data class WheelControl(
    val left: Float = 0f,
    val right: Float = 0f,
) {
    val isIdle: Boolean get() = abs(left) < DEADZONE && abs(right) < DEADZONE

    fun toMotorInts(): Pair<Int, Int> = Pair(
        (left.coerceIn(-1f, 1f) * 255f).toInt(),
        (right.coerceIn(-1f, 1f) * 255f).toInt(),
    )

    /**
     * Шасси Jasper умеет только дискретные импульсы. Смесь газ+руль сводим к одной команде.
     */
    fun toDriveAction(): DriveAction {
        if (isIdle) return DriveAction.STOP
        val throttle = (left + right) / 2f
        val diff = left - right
        return when {
            abs(diff) > 0.38f && abs(diff) >= abs(throttle) * 0.85f -> {
                if (diff > 0f) DriveAction.ROTATE_RIGHT else DriveAction.ROTATE_LEFT
            }
            throttle > DEADZONE -> DriveAction.FORWARD
            throttle < -DEADZONE -> DriveAction.BACKWARD
            else -> DriveAction.STOP
        }
    }

    companion object {
        const val DEADZONE = 0.18f
        val Idle = WheelControl()
    }
}
