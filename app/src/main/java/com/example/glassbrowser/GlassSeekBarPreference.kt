package com.example.glassbrowser

import android.animation.ValueAnimator
import android.content.Context
import android.content.res.Configuration
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.drawable.ClipDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.util.AttributeSet
import android.view.Gravity
import android.view.animation.OvershootInterpolator
import android.widget.SeekBar
import androidx.preference.PreferenceViewHolder
import androidx.preference.SeekBarPreference

/**
 * Összeköti a csúszkák kapszuláját a beállítások képernyővel: amíg egy kapszulát lenyomva tartasz,
 * a képernyő a helyén valódi üveget rajzol (fénytöréssel), ugyanazzal a shaderrel, mint a
 * böngésző keresősávja.
 */
object SliderGlass {
    /**
     * A beállítások képernyő állítja be. A paraméter a kapszula helye az ablakban (null = nincs
     * lenyomott kapszula). Igazat ad vissza, ha az üveget a shader rajzolja.
     */
    var host: ((RectF?) -> Boolean)? = null

    private var owner: Any? = null

    fun show(who: Any, rect: RectF): Boolean {
        val h = host ?: return false
        owner = who
        return h(rect)
    }

    fun hide(who: Any) {
        if (owner === who) {
            owner = null
            host?.invoke(null)
        }
    }
}

/**
 * A csúszka kapszulája. Nyugalomban fehér kapszula; lenyomva megnő, és átlátszó üveggé válik,
 * amelyen át a sáv megtörve látszik (az üveget a [SliderGlass] hostja rajzolja alá).
 */
class SliderThumbDrawable(private val bar: SeekBar) : Drawable() {

    private val d = bar.resources.displayMetrics.density
    private val night = (bar.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
        Configuration.UI_MODE_NIGHT_YES

    private val restW = 44f * d
    private val restH = 27f * d
    private val glassW = 64f * d
    private val glassH = 40f * d

    /** 0 = nyugalmi fehér kapszula, 1 = üveg */
    private var glass = 0f
    private var pressed = false
    private var anim: ValueAnimator? = null
    private var shaderDraws = false
    private var layerAlpha = 255

    private val rect = RectF()
    private val screenRect = RectF()
    private val loc = IntArray(2)

    private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(1, 0, 0, 0)
        setShadowLayer(7f * d, 0f, 2.5f * d, Color.argb(70, 0, 0, 0))
    }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1f * d
    }

    override fun getIntrinsicWidth(): Int = glassW.toInt()
    override fun getIntrinsicHeight(): Int = glassH.toInt()

    /** Ennyivel lóg túl a kapszula a sáv végein, hogy nyugalomban épp a sáv elejéig érjen. */
    val thumbOffset: Int get() = ((glassW - restW) / 2f).toInt()

    override fun isStateful(): Boolean = true

    override fun onStateChange(state: IntArray): Boolean {
        val p = state.contains(android.R.attr.state_pressed)
        if (p == pressed) return false
        pressed = p
        animateTo(if (p) 1f else 0f)
        return true
    }

    private fun animateTo(target: Float) {
        anim?.cancel()
        val a = ValueAnimator.ofFloat(glass, target)
        a.duration = if (target > glass) 260L else 200L
        if (target > glass) a.interpolator = OvershootInterpolator(2f)
        a.addUpdateListener {
            glass = it.animatedValue as Float
            syncHost()
            invalidateSelf()
        }
        anim = a
        a.start()
    }

    override fun onBoundsChange(bounds: Rect) {
        syncHost()
    }

    /** A kapszula pillanatnyi mérete (a rugózás miatt az üveg kicsit túl is lendülhet). */
    private fun currentSize(out: RectF, cx: Float, cy: Float) {
        val g = glass.coerceIn(0f, 1.15f)
        val w = restW + (glassW - restW) * g
        val h = restH + (glassH - restH) * g
        out.set(cx - w / 2f, cy - h / 2f, cx + w / 2f, cy + h / 2f)
    }

    /** Megmondja a képernyőnek, hol kell üveget rajzolnia (vagy hogy már nem kell). */
    private fun syncHost() {
        if (glass <= 0.04f || !bar.isAttachedToWindow) {
            shaderDraws = false
            SliderGlass.hide(this)
            return
        }
        val b = bounds
        bar.getLocationInWindow(loc)
        // a SeekBar a kapszulát (paddingLeft - thumbOffset, paddingTop) eltolással rajzolja
        val cx = loc[0] + bar.paddingLeft - bar.thumbOffset + b.exactCenterX()
        val cy = loc[1] + bar.paddingTop + b.exactCenterY()
        currentSize(screenRect, cx, cy)
        shaderDraws = SliderGlass.show(this, screenRect)
    }

    override fun draw(canvas: Canvas) {
        val b = bounds
        if (b.isEmpty) return
        currentSize(rect, b.exactCenterX(), b.exactCenterY())
        val r = rect.height() / 2f
        val g = glass.coerceIn(0f, 1f)
        val alphaScale = layerAlpha / 255f

        // lágy árnyék a kapszula alatt (a kitöltése szinte átlátszó, csak az árnyéka látszik)
        if (layerAlpha > 128) canvas.drawRoundRect(rect, r, r, shadowPaint)

        if (shaderDraws) {
            // a fehér kapszula elhalványul, a helyén az üveg (a shader) marad
            fillPaint.color = Color.WHITE
            fillPaint.alpha = (255 * (1f - g) * alphaScale).toInt().coerceIn(0, 255)
            if (fillPaint.alpha > 0) canvas.drawRoundRect(rect, r, r, fillPaint)
        } else {
            // shader nélkül (régebbi Android, egyszerű üveg): áttetsző kapszula szegéllyel
            fillPaint.color = Color.WHITE
            val restAlpha = 255f
            val glassAlpha = if (night) 70f else 120f
            fillPaint.alpha = ((restAlpha + (glassAlpha - restAlpha) * g) * alphaScale).toInt().coerceIn(0, 255)
            canvas.drawRoundRect(rect, r, r, fillPaint)
            strokePaint.color = if (night) Color.argb(230, 255, 255, 255) else Color.argb(90, 40, 50, 70)
            strokePaint.alpha = (strokePaint.alpha * g * alphaScale).toInt().coerceIn(0, 255)
            if (strokePaint.alpha > 0) canvas.drawRoundRect(rect, r, r, strokePaint)
        }
        if (g < 1f) {
            // nyugalmi állapotban hajszálvékony szegély, hogy fehér háttéren is látsszon
            strokePaint.color = Color.argb(34, 0, 0, 0)
            strokePaint.alpha = (34 * (1f - g) * alphaScale).toInt().coerceIn(0, 255)
            canvas.drawRoundRect(rect, r, r, strokePaint)
        }
    }

    override fun setAlpha(alpha: Int) {
        layerAlpha = alpha
        invalidateSelf()
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {}

    @Suppress("OVERRIDE_DEPRECATION")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}

/** A beállítások csúszkáinak kinézete: vastag kék sáv, fehér kapszula, lenyomva üvegkapszula. */
object SliderStyle {

    private const val BLUE = 0xFF0A84FF.toInt()

    /** [rainbow]: ha meg van adva, a sáv ez a színskála lesz (a színválasztóhoz). */
    fun apply(bar: SeekBar, rainbow: IntArray? = null) {
        if (bar.thumb is SliderThumbDrawable) return // ez a csúszka már megkapta a kinézetét
        val d = bar.resources.displayMetrics.density
        val night = (bar.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES

        val layers: LayerDrawable
        if (rainbow != null) {
            val g = GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, rainbow)
            g.cornerRadius = 5 * d
            layers = LayerDrawable(arrayOf<Drawable>(g))
            layers.setLayerHeight(0, (10 * d).toInt())
            layers.setLayerGravity(0, Gravity.CENTER_VERTICAL)
        } else {
            val trackH = (7 * d).toInt()
            val bg = GradientDrawable()
            bg.cornerRadius = 3.5f * d
            bg.setColor(if (night) Color.argb(60, 255, 255, 255) else Color.argb(36, 0, 0, 0))
            val fg = GradientDrawable()
            fg.cornerRadius = 3.5f * d
            fg.setColor(BLUE)
            val clip = ClipDrawable(fg, Gravity.START, ClipDrawable.HORIZONTAL)
            layers = LayerDrawable(arrayOf<Drawable>(bg, clip))
            layers.setId(0, android.R.id.background)
            layers.setId(1, android.R.id.progress)
            for (i in 0..1) {
                layers.setLayerHeight(i, trackH)
                layers.setLayerGravity(i, Gravity.CENTER_VERTICAL)
            }
        }
        val progress = bar.progress
        bar.progressDrawable = layers
        // a sáv újrarajzolásához (különben a régi kitöltés maradhat)
        bar.progress = 0
        bar.progress = progress

        val thumb = SliderThumbDrawable(bar)
        bar.thumb = thumb
        bar.thumbOffset = thumb.thumbOffset
        bar.splitTrack = false
        bar.background = null
        val side = (20 * d).toInt()
        val vertical = (9 * d).toInt()
        bar.setPadding(side, vertical, side, vertical)
    }
}

/** A szokásos csúszkás beállítás, az új kinézettel. */
class GlassSeekBarPreference @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : SeekBarPreference(context, attrs) {

    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        super.onBindViewHolder(holder)
        val bar = holder.findViewById(androidx.preference.R.id.seekbar) as? SeekBar ?: return
        SliderStyle.apply(bar)
    }
}
