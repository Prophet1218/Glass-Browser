package com.example.glassbrowser

import android.app.Activity
import android.os.Build
import android.view.View

/**
 * Requests the display's highest refresh rate (e.g. 120 Hz) for a window, and on Android 15+
 * also asks the frame-rate system to keep views in the "high" category so the panel does not
 * drop back to 60 Hz while animating or scrolling.
 */
object HighRefresh {
    fun apply(a: Activity) {
        AnimSpeed.apply(a)
        if (Build.VERSION.SDK_INT < 30) return
        try {
            val disp = a.display ?: return
            val cur = disp.mode
            val best = disp.supportedModes
                .filter { it.physicalWidth == cur.physicalWidth && it.physicalHeight == cur.physicalHeight }
                .maxByOrNull { it.refreshRate } ?: return
            val lp = a.window.attributes
            if (lp.preferredDisplayModeId != best.modeId || lp.preferredRefreshRate != best.refreshRate) {
                lp.preferredDisplayModeId = best.modeId
                lp.preferredRefreshRate = best.refreshRate
                a.window.attributes = lp
            }
        } catch (e: Exception) {
            // keep the default
        }
        view(a.window.decorView)
    }

    /** Android 15+: REQUESTED_FRAME_RATE_CATEGORY_HIGH (-4) for the given view. */
    fun view(v: View?) {
        if (v == null || Build.VERSION.SDK_INT < 35) return
        try {
            View::class.java.getMethod("setRequestedFrameRate", Float::class.javaPrimitiveType)
                .invoke(v, -4f)
        } catch (e: Throwable) {
            // not available
        }
    }
}
