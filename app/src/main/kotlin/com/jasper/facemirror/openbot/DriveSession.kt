package com.jasper.facemirror.openbot

import android.content.Context
import android.graphics.Bitmap
import com.jasper.facemirror.chassis.ChassisDriver
import com.jasper.facemirror.chassis.DriveAction
import com.jasper.facemirror.debug.JasperTiming
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

data class DriveHudState(
    val gamepad: Boolean = false,
    val recording: Boolean = false,
    val autopilot: Boolean = false,
    val hasModel: Boolean = false,
    val sonarCm: Int? = null,
    val status: String = "джойстик: HID отдельно от машинки",
)

class DriveSession(
    context: Context,
    private val chassis: ChassisDriver,
) {
    private val recorder = DatasetRecorder(context)
    private val net = AutopilotNet(context)
    private val _hud = MutableStateFlow(
        DriveHudState(hasModel = net.isReady),
    )
    val hud: StateFlow<DriveHudState> = _hud

    init {
        chassis.onSonarCm = { cm ->
            recorder.logSonar(cm)
            _hud.value = _hud.value.copy(sonarCm = cm)
        }
    }

    @Volatile
    private var gamepadControl = WheelControl.Idle

    @Volatile
    private var lastAction: DriveAction? = null

    val wantsFrames: Boolean
        get() = recorder.isRecording || _hud.value.autopilot

    fun onGamepadConnected(connected: Boolean) {
        _hud.value = _hud.value.copy(
            gamepad = connected,
            status = if (connected) "джойстик есть" else "нет HID-джойстика",
        )
    }

    fun onGamepadControl(control: WheelControl) {
        gamepadControl = control
        if (!control.isIdle) {
            applyWheels(control, log = true)
        } else if (!_hud.value.autopilot) {
            applyWheels(WheelControl.Idle, log = true)
        }
    }

    fun onButton(button: GamepadButton): String? = when (button) {
        GamepadButton.TOGGLE_RECORD -> toggleRecord()
        GamepadButton.TOGGLE_AUTOPILOT -> toggleAutopilot()
        GamepadButton.EMERGENCY_STOP -> {
            stopAutopilot()
            applyWheels(WheelControl.Idle, log = true)
            "Стоп!"
        }
    }

    fun onFrame(bitmap: Bitmap) {
        if (!wantsFrames) return
        val crop = OpenBotFrames.cropForPolicy(bitmap)
        try {
            if (recorder.isRecording) {
                recorder.logFrame(crop)
            }
            if (_hud.value.autopilot && gamepadControl.isIdle) {
                val predicted = net.predict(crop) ?: return
                applyWheels(predicted, log = true)
            }
        } finally {
            if (crop != bitmap) crop.recycle()
        }
    }

    fun toggleRecord(): String {
        return if (recorder.isRecording) {
            val path = recorder.stop()
            _hud.value = _hud.value.copy(
                recording = false,
                status = "запись: ${path?.name ?: "стоп"}",
            )
            "Записал!"
        } else {
            val session = recorder.start()
            _hud.value = _hud.value.copy(
                recording = true,
                status = "REC ${session.name}",
            )
            "Пишу езду!"
        }
    }

    fun toggleAutopilot(): String? {
        if (_hud.value.autopilot) {
            stopAutopilot()
            applyWheels(WheelControl.Idle, log = false)
            return "Сам не еду."
        }
        if (!net.isReady) {
            _hud.value = _hud.value.copy(status = "нет модели autopilot_float.tflite")
            return "Сначала научи, нет модели."
        }
        _hud.value = _hud.value.copy(autopilot = true, status = "автопилот")
        return "Поеду сам!"
    }

    fun release() {
        stopAutopilot()
        chassis.onSonarCm = null
        recorder.stop()
        net.close()
        chassis.stop()
    }

    private fun stopAutopilot() {
        _hud.value = _hud.value.copy(autopilot = false, hasModel = net.isReady)
    }

    private fun applyWheels(control: WheelControl, log: Boolean) {
        if (log && recorder.isRecording) {
            recorder.logControl(control)
        }
        val action = control.toDriveAction()
        if (action == lastAction) return
        lastAction = action
        JasperTiming.event("openbot", "drive=$action left=${control.left} right=${control.right}")
        if (action == DriveAction.STOP) {
            chassis.stop()
        } else {
            chassis.holdUntilStopped(action)
        }
    }
}
