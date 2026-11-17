package com.example.glassbrowser

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.graphics.Outline
import android.graphics.RectF
import android.view.View
import android.view.ViewOutlineProvider
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.OvershootInterpolator

/**
 * "Folyadék-átalakulás" (morph): egy panel egy gomb méretéből kinyílik a teljes méretére, és
 * visszazáródik oda. A panel tartalma végig teljes méretű marad, csak a kivágás (outline)
 * animálódik, ezért nincs újraméretezés és nincs villogás.
 */
class MorphClip(private val view: View) : ViewOutlineProvider() {
    private val rect = RectF()
    private var radius = 0f

    /** Az éppen látható (kivágott) terület a nézet koordinátáiban. */
    val current: RectF get() = rect
    val currentRadius: Float get() = radius

    init {
        view.outlineProvider = this
        view.clipToOutline = true
        update(0f, 0f, 0f, 0f, 0f) // kezdetben üres: semmi sem látszik
    }

    fun update(l: Float, t: Float, r: Float, b: Float, rad: Float) {
        rect.set(l, t, r, b)
        radius = rad.coerceAtLeast(0f)
        view.invalidateOutline()
    }

    override fun getOutline(v: View, outline: Outline) {
        if (rect.width() <= 0f || rect.height() <= 0f) {
            outline.setEmpty()
        } else {
            outline.setRoundRect(
                rect.left.toInt(), rect.top.toInt(), rect.right.toInt(), rect.bottom.toInt(), radius
            )
        }
    }

    /**
     * [from]: a gomb téglalapja a panel koordinátáiban. A cél a panel teljes területe.
     * [onFrame] a nyers animációs értéket kapja (0 = gomb, 1 = teljesen nyitott); nyitáskor a
     * rugózás miatt rövid időre 1 fölé is mehet.
     */
    fun morph(
        opening: Boolean,
        from: RectF,
        fromRadius: Float,
        toRadius: Float,
        durationMs: Long,
        onFrame: (Float) -> Unit,
        onEnd: () -> Unit,
        radiusAt: ((Float) -> Float)? = null,
        interpolator: android.animation.TimeInterpolator? = null
    ): ValueAnimator {
        val w = view.width.toFloat()
        val h = view.height.toFloat()
        val anim = if (opening) ValueAnimator.ofFloat(0f, 1f) else ValueAnimator.ofFloat(1f, 0f)
        anim.duration = durationMs
        anim.interpolator = interpolator
            ?: if (opening) OvershootInterpolator(1.4f) else AccelerateDecelerateInterpolator()
        anim.addUpdateListener {
            val f = it.animatedValue as Float
            val l = (from.left + (0f - from.left) * f).coerceAtLeast(0f)
            val t = (from.top + (0f - from.top) * f).coerceAtLeast(0f)
            val r = (from.right + (w - from.right) * f).coerceAtMost(w)
            val b = (from.bottom + (h - from.bottom) * f).coerceAtMost(h)
            update(l, t, r, b, radiusAt?.invoke(f) ?: (fromRadius + (toRadius - fromRadius) * f))
            onFrame(f)
        }
        anim.addListener(object : AnimatorListenerAdapter() {
            private var cancelled = false
            override fun onAnimationCancel(animation: Animator) {
                cancelled = true
            }

            override fun onAnimationEnd(animation: Animator) {
                if (!cancelled) onEnd()
            }
        })
        anim.start()
        return anim
    }
}
