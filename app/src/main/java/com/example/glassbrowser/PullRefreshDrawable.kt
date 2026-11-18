package com.example.glassbrowser

import android.animation.ValueAnimator
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.view.animation.LinearInterpolator
import android.view.animation.PathInterpolator
import kotlin.math.cos
import kotlin.math.sin

/**
 * A húzásra frissítés jelének rajza (Safari-szerű): körív nyíllal, amely a lehúzással együtt
 * "rajzolódik ki"; a küszöbnél a kör kitöltődik (halvány korong + teljes ív), frissítés közben a nyíl
 * hegye eltűnik és a nyitott ív folyamatosan pörög.
 */
class PullRefreshDrawable(private val density: Float) : Drawable() {

    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        strokeWidth = 2.2f * density
    }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val headPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val arc = RectF()
    private val head = Path()

    var color: Int = Color.WHITE
        set(v) { if (field != v) { field = v; invalidateSelf() } }

    /** A lehúzás mértéke a küszöbhöz képest (0..1): ennyi rajzolódik ki az ívből. */
    var progress = 0f
        set(v) { val c = v.coerceIn(0f, 1f); if (field != c) { field = c; invalidateSelf() } }

    /** A küszöb elérésekor kitöltődő korong (0..1). */
    private var fill = 0f
    /** A nyílhegy láthatósága (frissítés közben eltűnik). */
    private var headVis = 1f
    /** A pörgés szöge frissítés közben. */
    private var spin = 0f
    private var spinning = false

    private var fillAnim: ValueAnimator? = null
    private var headAnim: ValueAnimator? = null
    private var spinAnim: ValueAnimator? = null

    /** A küszöb elérése / elhagyása: a korong lágyan kitöltődik vagy kiürül. */
    fun setArmed(armed: Boolean) {
        val target = if (armed) 1f else 0f
        fillAnim?.cancel()
        if (fill == target) return
        fillAnim = ValueAnimator.ofFloat(fill, target).apply {
            duration = if (armed) 220L else 160L
            interpolator = PathInterpolator(0.2f, 0f, 0f, 1f)
            addUpdateListener { fill = it.animatedValue as Float; invalidateSelf() }
            start()
        }
    }

    /** Frissítés közben: a nyílhegy elhalványul, az ív nyitott marad és körbe pörög. */
    fun startSpin() {
        if (spinning) return
        spinning = true
        headAnim?.cancel()
        headAnim = ValueAnimator.ofFloat(headVis, 0f).apply {
            duration = 180L
            addUpdateListener { headVis = it.animatedValue as Float; invalidateSelf() }
            start()
        }
        spinAnim?.cancel()
        spinAnim = ValueAnimator.ofFloat(0f, 360f).apply {
            duration = 820L
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            addUpdateListener { spin = it.animatedValue as Float; invalidateSelf() }
            start()
        }
    }

    /** Alaphelyzet (új húzás vagy a jel eltűnése után). */
    fun reset() {
        spinning = false
        fillAnim?.cancel()
        headAnim?.cancel()
        spinAnim?.cancel()
        fill = 0f
        headVis = 1f
        spin = 0f
        progress = 0f
        invalidateSelf()
    }

    override fun draw(canvas: Canvas) {
        val b = bounds
        if (b.isEmpty) return
        val cx = b.exactCenterX()
        val cy = b.exactCenterY()
        val half = minOf(b.width(), b.height()) / 2f
        // az ív sugara állandó (a jel a 44 dp-s üvegkör közepén ül), a korong szinte kitölti a kört
        val r = minOf(7.6f * density, half - 4f * density)
        val a = Color.alpha(color)

        // a küszöbnél kitöltődő halvány korong
        if (fill > 0.001f) {
            fillPaint.color = color
            fillPaint.alpha = (a * 0.22f * fill).toInt()
            canvas.drawCircle(cx, cy, (half - 3f * density) * (0.5f + 0.5f * fill), fillPaint)
        }

        // az ív: húzás közben a lehúzással nő, a küszöbön (és pörgéskor) ~300°-os nyitott kör
        val sweep = if (spinning) 290f else (40f + 260f * progress + 20f * fill)
        val start = -90f + spin
        stroke.color = color
        stroke.alpha = (a * (0.55f + 0.45f * maxOf(progress, fill))).toInt()
        arc.set(cx - r, cy - r, cx + r, cy + r)
        canvas.drawArc(arc, start, sweep, false, stroke)

        // nyílhegy az ív végén (a húzás elején még kicsi, a küszöbnél teljes)
        val hv = headVis * (0.35f + 0.65f * maxOf(progress, fill))
        if (hv > 0.01f) {
            val endA = Math.toRadians((start + sweep).toDouble())
            val ex = cx + r * cos(endA).toFloat()
            val ey = cy + r * sin(endA).toFloat()
            // érintő irány (az ív haladási iránya)
            val tx = -sin(endA).toFloat()
            val ty = cos(endA).toFloat()
            // normális (a kör közepétől kifelé)
            val nx = cos(endA).toFloat()
            val ny = sin(endA).toFloat()
            val s = 3.6f * density * hv
            head.reset()
            head.moveTo(ex + tx * s * 1.1f, ey + ty * s * 1.1f)
            head.lineTo(ex + nx * s - tx * s * 0.35f, ey + ny * s - ty * s * 0.35f)
            head.lineTo(ex - nx * s - tx * s * 0.35f, ey - ny * s - ty * s * 0.35f)
            head.close()
            headPaint.color = color
            headPaint.alpha = (stroke.alpha * headVis).toInt()
            canvas.drawPath(head, headPaint)
        }
    }

    override fun setAlpha(alpha: Int) {
        // az átlátszóságot a nézet (pullIndicator) kezeli
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        stroke.colorFilter = colorFilter
        fillPaint.colorFilter = colorFilter
        headPaint.colorFilter = colorFilter
        invalidateSelf()
    }

    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT

    override fun getIntrinsicWidth(): Int = (44 * density).toInt()
    override fun getIntrinsicHeight(): Int = (44 * density).toInt()
}
