package com.jasper.facemirror.debug

import android.os.SystemClock
import android.util.Log

/**
 * Тайминги голосового пайплайна.
 * В Android Studio: Logcat → фильтр `JasperTiming`.
 */
object JasperTiming {
    const val TAG = "JasperTiming"

    fun now(): Long = SystemClock.elapsedRealtime()

    fun event(step: String, detail: String = "") {
        if (detail.isEmpty()) {
            Log.i(TAG, step)
        } else {
            Log.i(TAG, "$step | $detail")
        }
    }

    fun elapsed(step: String, startedAt: Long, detail: String = ""): Long {
        val ms = now() - startedAt
        event(step, if (detail.isEmpty()) "${ms}мс" else "${ms}мс | $detail")
        return ms
    }
}
