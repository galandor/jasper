package com.jasper.facemirror

import android.os.Bundle
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.jasper.facemirror.openbot.GamepadHub
import com.jasper.facemirror.ui.FaceMirrorScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        enableEdgeToEdge()
        setContent {
            FaceMirrorScreen()
        }
    }

    override fun dispatchGenericMotionEvent(event: MotionEvent): Boolean {
        if (GamepadHub.onMotion(event)) return true
        return super.dispatchGenericMotionEvent(event)
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (GamepadHub.onKey(event)) return true
        return super.dispatchKeyEvent(event)
    }
}
