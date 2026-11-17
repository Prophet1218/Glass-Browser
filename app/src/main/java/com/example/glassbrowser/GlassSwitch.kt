package com.example.glassbrowser

import android.animation.ValueAnimator
import android.content.Context
import android.content.res.Configuration
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.widget.Checkable
import androidx.preference.PreferenceViewHolder
import androidx.preference.SwitchPreference
import kotlin.math.PI
import kotlin.math.sin

/**
 * Kapcsoló a csúszkák kapszulájával: nyugalomban fehér kapszula a sávon; átkapcsoláskor megnő,
 * üveggé válik (ugyanaz a fénytörő shader, mint a csúszkáknál), átcsúszik a másik oldalra, majd
 * visszahúzódik.
 */
class GlassSwitchView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs), Checkable {

    private val d = resources.displayMetrics.density
    private val night = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
        Configuration.UI_MODE_NIGHT_YES

    private val trackW = 52f * d
    private val trackH = 30f * d
    private val restW = 30f * d
    private val restH = 24f * d
    private val glassW = 44f * d
    private val glassH = 36f * d

    private var checked = false
    /** 0 = ki, 1 = be (animált helyzet) */
    private var pos = 0f
    /** 0 = fehér kapszula, 1 = üveg (csak mozgás közben) */
    private var glass = 0f
    private var anim: ValueAnimator? = null
    private var shaderDraws = false
    private var boundKey: String? = null

    private val track = Paint(Paint.ANTI_ALIAS_FLAG)
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1f * d
    }
    private val shadow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(1, 0, 0, 0)
        setShadowLayer(5f * d, 0f, 2f * d, Color.argb(60, 0, 0, 0))
    }
    private val rect = RectF()
    private val screenRect = RectF()
    private val loc = IntArray(2)

    /**
     * A sor újrahasznosításakor (másik beállítás kerül ugyanebbe a nézetbe) nem animálunk,
     * csak a tényleges átkapcsoláskor.
     */
    fun bindKey(key: String?) {
        if (boundKey != key) {
            boundKey = key
            anim?.cancel()
            anim = null
            glass = 0f
            pos = -1f // a következő setChecked azonnal beállítja
            SliderGlass.hide(this)
        }
    }

    override fun isChecked(): Boolean = checked

    override fun toggle() {
        isChecked = !checked
    }

    override fun setChecked(value: Boolean) {
        val target = if (value) 1f else 0f
        checked = value
        if (pos < 0f || !isLaidOut) {
            pos = target
            invalidate()
            return
        }
        if (pos == target && anim == null) return
        anim?.cancel()
        val from = pos
        val a = ValueAnimator.ofFloat(0f, 1f)
        a.duration = 360
        a.interpolator = DecelerateInterpolator(1.4f)
        a.addUpdateListener {
            val t = it.animatedValue as Float
            pos = from + (target - from) * t
            glass = sin(PI * it.animatedFraction).toFloat()
            sync()
            invalidate()
        }
        a.addListener(object : android.animation.AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: android.animation.Animator) {
                if (anim === a) {
                    anim = null
                    pos = target
                    glass = 0f
                    sync()
                    invalidate()
                }
            }
        })
        anim = a
        a.start()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        anim?.cancel()
        anim = null
        glass = 0f
        SliderGlass.hide(this)
    }

    private fun thumbRect(out: RectF) {
        val left = (width - trackW) / 2f
        val cy = height / 2f
        val p = pos.coerceIn(0f, 1f)
        val inset = (trackH - restH) / 2f
        val startCx = left + inset + restW / 2f
        val endCx = left + trackW - inset - restW / 2f
        val cx = startCx + (endCx - startCx) * p
        val w = restW + (glassW - restW) * glass
        val h = restH + (glassH - restH) * glass
        out.set(cx - w / 2f, cy - h / 2f, cx + w / 2f, cy + h / 2f)
    }

    /** Mozgás közben a képernyő valódi üveget rajzol a kapszula helyére. */
    private fun sync() {
        if (glass <= 0.05f || !isAttachedToWindow) {
            shaderDraws = false
            SliderGlass.hide(this)
            return
        }
        thumbRect(rect)
        getLocationInWindow(loc)
        screenRect.set(rect.left + loc[0], rect.top + loc[1], rect.right + loc[0], rect.bottom + loc[1])
        shaderDraws = SliderGlass.show(this, screenRect)
    }

    override fun onDraw(canvas: Canvas) {
        val p = pos.coerceIn(0f, 1f)
        val left = (width - trackW) / 2f
        val top = (height - trackH) / 2f

        // sáv: kikapcsolva szürke, bekapcsolva kék
        val off = if (night) Color.argb(70, 255, 255, 255) else Color.argb(46, 0, 0, 0)
        val on = 0xFF0A84FF.toInt()
        track.color = blend(off, on, p)
        rect.set(left, top, left + trackW, top + trackH)
        canvas.drawRoundRect(rect, trackH / 2f, trackH / 2f, track)

        thumbRect(rect)
        val r = rect.height() / 2f
        canvas.drawRoundRect(rect, r, r, shadow)
        val g = glass.coerceIn(0f, 1f)
        fill.color = Color.WHITE
        if (shaderDraws) {
            fill.alpha = (255 * (1f - g)).toInt().coerceIn(0, 255)
        } else {
            fill.alpha = (255 - 150 * g).toInt().coerceIn(0, 255)
            stroke.color = Color.argb((220 * g).toInt().coerceIn(0, 255), 255, 255, 255)
            if (g > 0f) canvas.drawRoundRect(rect, r, r, stroke)
        }
        if (fill.alpha > 0) canvas.drawRoundRect(rect, r, r, fill)
    }

    private fun blend(a: Int, b: Int, t: Float): Int {
        fun ch(shift: Int): Int {
            val x = (a shr shift) and 0xFF
            val y = (b shr shift) and 0xFF
            return (x + (y - x) * t).toInt().coerceIn(0, 255)
        }
        return (ch(24) shl 24) or (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
    }
}

/** Kapcsolós beállítás az üvegkapszulás kapcsolóval. */
class GlassSwitchPreference @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : SwitchPreference(context, attrs) {

    init {
        widgetLayoutResource = R.layout.pref_glass_switch
    }

    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        (holder.findViewById(android.R.id.switch_widget) as? GlassSwitchView)?.bindKey(key)
        super.onBindViewHolder(holder)
    }
}
