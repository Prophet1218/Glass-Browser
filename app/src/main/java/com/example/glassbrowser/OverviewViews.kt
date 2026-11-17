package com.example.glassbrowser

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View
import android.widget.FrameLayout
import androidx.appcompat.widget.AppCompatImageView

/**
 * A lapváltó "repülő" kártyája: megnyitáskor a teljes oldal képe innen zsugorodik a rácsban lévő
 * kártya helyére, visszaváltáskor pedig a kártya nagyobbodik teljes képernyőre.
 *
 * [progress] 0 = teljes méret, 1 = a kártya helye. A sarkok közben a képernyő saroksugarából a
 * kártya saroksugarába mennek át, a kép alja pedig fokozatosan levágódik a kártya arányára.
 */
class HeroView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private val full = RectF()
    private val card = RectF()
    private var fullRadius = 0f
    private var cardRadius = 0f
    private var bitmap: Bitmap? = null
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val shaderMatrix = Matrix()
    private val src = RectF()
    private val dst = RectF()

    var progress = 0f
        set(value) {
            field = value
            invalidate()
        }

    fun setup(image: Bitmap?, fullRect: RectF, cardRect: RectF, fullR: Float, cardR: Float, fallbackColor: Int) {
        bitmap = image
        full.set(fullRect)
        card.set(cardRect)
        fullRadius = fullR
        cardRadius = cardR
        fillPaint.color = fallbackColor
        paint.shader = if (image != null) {
            BitmapShader(image, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
        } else {
            null
        }
        invalidate()
    }

    /**
     * Ha meg van adva, a kép nem egyenesen repül a kártyára, hanem egy ívben ezen a téglalapon át
     * (pl. a képernyő közepén át, mint a Safariban a felfelé legyintéskor).
     */
    var via: RectF? = null

    override fun onDraw(canvas: Canvas) {
        val f = progress
        val v = via
        if (v != null) {
            // másodfokú Bézier-ív: teljes kép -> közép -> kártya
            val a = (1f - f) * (1f - f)
            val b = 2f * (1f - f) * f
            val c = f * f
            dst.set(
                a * full.left + b * v.left + c * card.left,
                a * full.top + b * v.top + c * card.top,
                a * full.right + b * v.right + c * card.right,
                a * full.bottom + b * v.bottom + c * card.bottom
            )
        } else dst.set(
            full.left + (card.left - full.left) * f,
            full.top + (card.top - full.top) * f,
            full.right + (card.right - full.right) * f,
            full.bottom + (card.bottom - full.bottom) * f
        )
        if (dst.width() <= 0f || dst.height() <= 0f) return
        val r = (fullRadius + (cardRadius - fullRadius) * f).coerceAtLeast(0f)

        val bmp = bitmap
        val shader = paint.shader
        if (bmp == null || shader == null) {
            canvas.drawRoundRect(dst, r, r, fillPaint)
            return
        }
        // forrás: teljes kép -> a kártyán látszó felső rész
        val fullH = bmp.height.toFloat()
        val cropH = if (card.width() > 0f) {
            (bmp.width * card.height() / card.width()).coerceAtMost(fullH)
        } else {
            fullH
        }
        val h = fullH + (cropH - fullH) * f.coerceIn(0f, 1f)
        src.set(0f, 0f, bmp.width.toFloat(), h)
        shaderMatrix.setRectToRect(src, dst, Matrix.ScaleToFit.FILL)
        shader.setLocalMatrix(shaderMatrix)
        canvas.drawRoundRect(dst, r, r, paint)
    }
}

/** Kép, amely a szélességet tölti ki, és a kép TETEJÉT mutatja (nem a közepét). */
class TopCropImageView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : AppCompatImageView(context, attrs) {

    private val m = Matrix()

    init {
        scaleType = ScaleType.MATRIX
    }

    override fun setImageBitmap(bm: Bitmap?) {
        super.setImageBitmap(bm)
        updateMatrix()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        updateMatrix()
    }

    private fun updateMatrix() {
        val d = drawable ?: return
        val dw = d.intrinsicWidth
        if (dw <= 0 || width <= 0) return
        val s = width.toFloat() / dw
        m.reset()
        m.setScale(s, s)
        imageMatrix = m
    }
}

/** Keret, amelynek magassága a szélességének [ratio]-szorosa (a lapkártyákhoz). */
class AspectFrameLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : FrameLayout(context, attrs) {

    var ratio = 1.32f

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        val h = (w * ratio).toInt()
        super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(h, MeasureSpec.EXACTLY))
    }
}

/** Keret, amely szól, amikor megérintik / elengedik (a keresősáv "rugózó" lenyomásához). */
class PressLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : FrameLayout(context, attrs) {

    var onPress: ((Boolean) -> Unit)? = null
    /** Az ujj helye (a nézeten belül) és hogy le van-e nyomva, a fényhatáshoz. */
    var onTouchMove: ((Float, Float, Boolean) -> Unit)? = null

    override fun dispatchTouchEvent(ev: android.view.MotionEvent): Boolean {
        when (ev.actionMasked) {
            android.view.MotionEvent.ACTION_DOWN -> {
                onPress?.invoke(true)
                onTouchMove?.invoke(ev.x, ev.y, true)
            }
            android.view.MotionEvent.ACTION_MOVE -> onTouchMove?.invoke(ev.x, ev.y, true)
            android.view.MotionEvent.ACTION_UP, android.view.MotionEvent.ACTION_CANCEL -> {
                onPress?.invoke(false)
                onTouchMove?.invoke(ev.x, ev.y, false)
            }
        }
        return super.dispatchTouchEvent(ev)
    }
}

/**
 * Fény az üvegen az ujj alatt: lágy, kör alakú ragyogás, amely követi az ujjat, érintéskor
 * felerősödik, elengedéskor elhalványul. A felület (kapszula) alakjára van vágva.
 */
class GlowDrawable(private val density: Float) : android.graphics.drawable.Drawable() {
    private val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
    private val path = android.graphics.Path()
    private val rect = RectF()
    private var x = 0f
    private var y = 0f
    private var tx = 0f
    private var ty = 0f
    private var level = 0f
    private var target = 0f
    private var color = android.graphics.Color.WHITE
    private var anim: android.animation.ValueAnimator? = null

    fun track(px: Float, py: Float, down: Boolean, c: Int) {
        color = c
        tx = px
        ty = py
        if (level <= 0.01f) {
            x = px
            y = py
        }
        val t = if (down) 1f else 0f
        if (t != target || anim == null) {
            target = t
            anim?.cancel()
            val a = android.animation.ValueAnimator.ofFloat(0f, 1f)
            a.duration = if (down) 1200L else 520L
            a.interpolator = android.view.animation.LinearInterpolator()
            var last = 0L
            a.addUpdateListener {
                val now = it.currentPlayTime
                val dt = ((now - last).coerceAtLeast(1L)).toFloat() / 1000f
                last = now
                // a fény lágyan követi az ujjat (kicsit lemaradva, folyékonyan)
                val k = (1f - Math.exp(-dt * 14.0).toFloat())
                x += (tx - x) * k
                y += (ty - y) * k
                val kl = (1f - Math.exp(-dt * (if (target > level) 10.0 else 6.0)).toFloat())
                level += (target - level) * kl
                invalidateSelf()
                if (target == 0f && level < 0.01f) {
                    level = 0f
                    it.cancel()
                }
            }
            anim = a
            if (down) a.repeatCount = android.animation.ValueAnimator.INFINITE
            a.start()
        }
        if (!down && anim?.isRunning != true) invalidateSelf()
    }

    override fun draw(canvas: Canvas) {
        if (level <= 0.01f) return
        val b = bounds
        if (b.isEmpty) return
        rect.set(b)
        val r = minOf(b.width(), b.height()) / 2f
        path.reset()
        path.addRoundRect(rect, r, r, android.graphics.Path.Direction.CW)
        canvas.save()
        canvas.clipPath(path)
        val radius = 70f * density
        val a = (90 * level).toInt().coerceIn(0, 255)
        val c0 = (color and 0x00FFFFFF) or (a shl 24)
        val c1 = (color and 0x00FFFFFF) or ((a / 3) shl 24)
        paint.shader = android.graphics.RadialGradient(
            x, y, radius, intArrayOf(c0, c1, 0), floatArrayOf(0f, 0.45f, 1f),
            android.graphics.Shader.TileMode.CLAMP
        )
        canvas.drawRect(rect, paint)
        // vékony fényes perem az ujj közelében
        paint.shader = android.graphics.RadialGradient(
            x, y, radius * 1.6f, intArrayOf((color and 0x00FFFFFF) or ((a * 2 / 3) shl 24), 0),
            null, android.graphics.Shader.TileMode.CLAMP
        )
        paint.style = android.graphics.Paint.Style.STROKE
        paint.strokeWidth = 1.5f * density
        rect.inset(0.75f * density, 0.75f * density)
        canvas.drawRoundRect(rect, r, r, paint)
        paint.style = android.graphics.Paint.Style.FILL
        canvas.restore()
    }

    override fun setAlpha(alpha: Int) {}
    override fun setColorFilter(colorFilter: android.graphics.ColorFilter?) {}

    @Suppress("OVERRIDE_DEPRECATION")
    override fun getOpacity(): Int = android.graphics.PixelFormat.TRANSLUCENT
}
