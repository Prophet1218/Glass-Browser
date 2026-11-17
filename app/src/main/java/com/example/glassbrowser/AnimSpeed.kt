package com.example.glassbrowser

import android.animation.ValueAnimator
import android.content.Context
import androidx.preference.PreferenceManager

/**
 * Global animation speed (settings slider "anim_speed", 50–200 %, default 125). It scales every
 * ValueAnimator / ViewPropertyAnimator in the app through the animator duration scale, and the
 * spring-driven glass capsules through [factor].
 */
object AnimSpeed {
    /** Speed multiplier: 2 = twice as fast. */
    @Volatile
    var factor = 1f
        private set
    private var systemScale = -1f

    fun apply(ctx: Context, percent: Int? = null) {
        val prefs = PreferenceManager.getDefaultSharedPreferences(ctx)
        // 1.4: the default became 125 %; the 100 saved automatically by 1.3 is dropped once
        if (!prefs.getBoolean("anim_speed_v2", false)) {
            prefs.edit().remove("anim_speed").putBoolean("anim_speed_v2", true).apply()
        }
        val p = percent ?: prefs.getInt("anim_speed", 125)
        factor = (p.coerceIn(25, 300)) / 100f
        if (systemScale < 0f) systemScale = ValueAnimator.getDurationScale()
        // the system setting "animations off" (0) stays off
        if (systemScale == 0f) return
        try {
            ValueAnimator::class.java.getMethod("setDurationScale", Float::class.javaPrimitiveType)
                .invoke(null, systemScale / factor)
        } catch (e: Throwable) {
            // hidden API not reachable: only the springs follow the setting
        }
    }
}
