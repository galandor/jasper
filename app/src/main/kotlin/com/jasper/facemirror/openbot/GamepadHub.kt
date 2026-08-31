package com.jasper.facemirror.openbot

import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlin.math.abs

enum class GamepadButton {
    TOGGLE_RECORD,
    TOGGLE_AUTOPILOT,
    EMERGENCY_STOP,
}

/**
 * HID-геймпад (DualShock / Xbox / дешёвый BT-стик).
 * Это не Bluetooth SPP машинки: ОС видит джойстик как клавиатуру/HID, HC-06 — как последовательный порт.
 * Оба соединения живут одновременно.
 */
object GamepadHub {
    private val _control = MutableStateFlow(WheelControl.Idle)
    val control: StateFlow<WheelControl> = _control

    private val _connected = MutableStateFlow(false)
    val connected: StateFlow<Boolean> = _connected

    private val _buttons = MutableSharedFlow<GamepadButton>(extraBufferCapacity = 16)
    val buttons: SharedFlow<GamepadButton> = _buttons

    fun onMotion(event: MotionEvent): Boolean {
        if (!isJoystick(event.source)) return false
        _connected.value = true
        _control.value = controlFromMotion(event)
        return true
    }

    fun onKey(event: KeyEvent): Boolean {
        if (!isGamepadKey(event)) return false
        _connected.value = true
        if (event.repeatCount > 0) return true
        if (event.action != KeyEvent.ACTION_DOWN) {
            if (isDpad(event.keyCode) && event.action == KeyEvent.ACTION_UP) {
                _control.value = WheelControl.Idle
                return true
            }
            return true
        }
        when (event.keyCode) {
            KeyEvent.KEYCODE_BUTTON_A, KeyEvent.KEYCODE_BUTTON_1 ->
                _buttons.tryEmit(GamepadButton.TOGGLE_RECORD)
            KeyEvent.KEYCODE_BUTTON_Y, KeyEvent.KEYCODE_BUTTON_4 ->
                _buttons.tryEmit(GamepadButton.TOGGLE_AUTOPILOT)
            KeyEvent.KEYCODE_BUTTON_B, KeyEvent.KEYCODE_BUTTON_2, KeyEvent.KEYCODE_BUTTON_START ->
                _buttons.tryEmit(GamepadButton.EMERGENCY_STOP)
            KeyEvent.KEYCODE_DPAD_UP -> _control.value = WheelControl(1f, 1f)
            KeyEvent.KEYCODE_DPAD_DOWN -> _control.value = WheelControl(-1f, -1f)
            KeyEvent.KEYCODE_DPAD_LEFT -> _control.value = WheelControl(-1f, 1f)
            KeyEvent.KEYCODE_DPAD_RIGHT -> _control.value = WheelControl(1f, -1f)
            else -> return false
        }
        return true
    }

    private fun controlFromMotion(event: MotionEvent): WheelControl {
        val gas = absAxis(event, MotionEvent.AXIS_GAS).let {
            if (it == 0f) absAxis(event, MotionEvent.AXIS_RTRIGGER) else it
        }
        val brake = absAxis(event, MotionEvent.AXIS_BRAKE).let {
            if (it == 0f) absAxis(event, MotionEvent.AXIS_LTRIGGER) else it
        }
        val steer = firstAxis(
            event,
            MotionEvent.AXIS_X,
            MotionEvent.AXIS_HAT_X,
            MotionEvent.AXIS_Z,
        )
        if (gas > 0.05f || brake > 0.05f) {
            return convertGame(brake, gas, steer)
        }
        val x = firstAxis(event, MotionEvent.AXIS_X, MotionEvent.AXIS_HAT_X, MotionEvent.AXIS_Z)
        val y = firstAxis(event, MotionEvent.AXIS_Y, MotionEvent.AXIS_HAT_Y, MotionEvent.AXIS_RZ)
        return convertJoystick(x, y)
    }

    /** OpenBot GAME: R2 газ, L2 назад, стик — руль. */
    private fun convertGame(brake: Float, gas: Float, steer: Float): WheelControl {
        var left = gas - brake
        var right = gas - brake
        if (left >= 0f) left += steer else left -= steer
        if (right >= 0f) right -= steer else right += steer
        return WheelControl(left.coerceIn(-1f, 1f), right.coerceIn(-1f, 1f))
    }

    /** OpenBot JOYSTICK: левый стик. На Android Y вверх обычно отрицательный. */
    private fun convertJoystick(x: Float, y: Float): WheelControl {
        var left = -y
        var right = -y
        if (left >= 0f) left += x else left -= x
        if (right >= 0f) right -= x else right += x
        return WheelControl(left.coerceIn(-1f, 1f), right.coerceIn(-1f, 1f))
    }

    private fun firstAxis(event: MotionEvent, vararg axes: Int): Float {
        for (axis in axes) {
            val value = centeredAxis(event, axis)
            if (abs(value) > 0.01f) return value
        }
        return 0f
    }

    private fun absAxis(event: MotionEvent, axis: Int): Float =
        centeredAxis(event, axis).coerceAtLeast(0f)

    private fun centeredAxis(event: MotionEvent, axis: Int): Float {
        val device = event.device ?: return 0f
        val range = device.getMotionRange(axis, event.source) ?: return 0f
        val value = event.getAxisValue(axis)
        return if (abs(value) > range.flat) value else 0f
    }

    private fun isJoystick(source: Int): Boolean {
        val joystick = source and InputDevice.SOURCE_JOYSTICK == InputDevice.SOURCE_JOYSTICK
        val gamepad = source and InputDevice.SOURCE_GAMEPAD == InputDevice.SOURCE_GAMEPAD
        return joystick || gamepad
    }

    private fun isGamepadKey(event: KeyEvent): Boolean {
        if (event.deviceId < 0) return false
        return event.source and InputDevice.SOURCE_GAMEPAD == InputDevice.SOURCE_GAMEPAD ||
            event.source and InputDevice.SOURCE_JOYSTICK == InputDevice.SOURCE_JOYSTICK ||
            event.source and InputDevice.SOURCE_DPAD == InputDevice.SOURCE_DPAD
    }

    private fun isDpad(code: Int): Boolean = code in KeyEvent.KEYCODE_DPAD_UP..KeyEvent.KEYCODE_DPAD_RIGHT
}
