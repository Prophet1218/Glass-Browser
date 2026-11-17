package com.example.glassbrowser

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.drawable.Drawable
import android.view.View

/**
 * A lapkártyák bezáró gombjának háttere: kis, kerek folyékony üveg.
 *
 * A kártyák egy görgethető listában vannak, ezért a shader üvegfelületei (csak 6 van) itt nem
 * használhatók. Helyette a kártya saját bélyegképéből készül az üveg: a gomb alatti rész kicsit
 * felnagyítva (lencse) és elmosva látszik a körben, rajta fényperem, felső csillanás és a domború
 * árnyékolás. Ha nincs bélyegkép, a korábbi áttetsző kör marad.
 *
 * [host] maga a gomb; a szülője a kártya, amelyen a bélyegkép a teljes szélességre nagyítva, a
 * tetejéhez igazítva látszik (TopCropImageView), ebből számoljuk a gomb alatti képrészt.
 */
class GlassCloseDrawable(private val host: View) : Drawable() {

    private val d = host.resources.displayMetrics.density

    /** Domború (3D) árnyékolás a beállítás szerint. */
    var convex = true
        set(value) {
            if (field == value) return
            field = value
            invalidateSelf()
        }

    private var thumb: Bitmap? = null
    private var small: Bitmap? = null
    private var smallShader: BitmapShader? = null
    // a kis kép ebből készült (bélyegkép, változatszám, a gomb helye és mérete a képen)
    private var keyGen = -1
    private var keyX = Int.MIN_VALUE
    private var keyY = Int.MIN_VALUE
    private var keyS = Int.MIN_VALUE
    private var keySrc: Bitmap? = null
    /** A gomb alatti kép átlagos világossága (0..1): világos oldalon sötétebb az üveg. */
    private var lum = 0.5f
    private var layerAlpha = 255

    private val filter = Paint(Paint.FILTER_BITMAP_FLAG)
    private val glassPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val tintPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val shadePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val sheenPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1f * d
    }
    private val oldFill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x73202328 }
    private val oldStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1f * d
        color = 0x59FFFFFF
    }
    private val m = Matrix()
    private val srcF = RectF()
    private val dstF = RectF()
    private val oval = RectF()

    /** A kártya bélyegképe (null = nincs: a régi áttetsző kör látszik). */
    fun setThumbnail(bm: Bitmap?) {
        if (thumb === bm) return
        thumb = bm
        invalidateSelf()
    }

    override fun onBoundsChange(bounds: Rect) {
        if (bounds.isEmpty) return
        val cx = bounds.exactCenterX()
        val cy = bounds.exactCenterY()
        val r = minOf(bounds.width(), bounds.height()) / 2f
        // a fény balról fölülről jön: a jobb alsó perem felé sötétedik (az üveg vastagsága)
        shadePaint.shader = RadialGradient(
            cx - 0.28f * r, cy - 0.32f * r, 1.45f * r,
            intArrayOf(Color.TRANSPARENT, Color.TRANSPARENT, Color.argb(78, 0, 0, 0)),
            floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP
        )
        // felső csillanás: lágy, lefelé elhalványuló fényfolt a kör tetején
        sheenPaint.shader = LinearGradient(
            0f, cy - 0.92f * r, 0f, cy - 0.05f * r,
            Color.argb(120, 255, 255, 255), Color.argb(0, 255, 255, 255), Shader.TileMode.CLAMP
        )
        // fényperem: bal felül erős, középen halvány, jobb alul (átütő fény) közepes
        rimPaint.shader = LinearGradient(
            cx - 0.7f * r, cy - 0.7f * r, cx + 0.7f * r, cy + 0.7f * r,
            intArrayOf(Color.argb(225, 255, 255, 255), Color.argb(45, 255, 255, 255), Color.argb(150, 255, 255, 255)),
            floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP
        )
    }

    /**
     * Elkészíti (vagy újrahasznosítja) a gomb alatti képrész kicsinyített, ezért elmosott
     * változatát. Hamis, ha nincs használható bélyegkép.
     */
    private fun ensureSmall(): Boolean {
        val src = thumb ?: return false
        if (src.isRecycled || src.width <= 0 || src.height <= 0) return false
        // hardveres képből szoftveresen nem lehet olvasni: ilyenkor a régi kinézet marad
        if (src.config == Bitmap.Config.HARDWARE) return false
        val card = host.parent as? View ?: return false
        val cw = card.width
        if (cw <= 0 || host.width <= 0 || host.height <= 0) return false
        // a kártyán a kép a teljes szélességre nagyítva, a tetejéhez igazítva látszik
        val s = cw.toFloat() / src.width
        val cxB = (host.left + host.width / 2f) / s
        val cyB = (host.top + host.height / 2f) / s
        // lencse: a gombnál kisebb területet nagyítunk a teljes körre
        val half = host.width / s / 2f / LENS
        val kx = cxB.toInt()
        val ky = cyB.toInt()
        val ks = (half * 4f).toInt()
        val gen = src.generationId
        if (small != null && keySrc === src && keyGen == gen && keyX == kx && keyY == ky && keyS == ks) return true

        val l = (cxB - half).coerceIn(0f, src.width - 1f)
        val t = (cyB - half).coerceIn(0f, src.height - 1f)
        val r = (cxB + half).coerceIn(l + 1f, src.width.toFloat())
        val b = (cyB + half).coerceIn(t + 1f, src.height.toFloat())
        if (cyB - half >= src.height) return false // a gomb alatt már nincs kép
        val srcRect = Rect(l.toInt(), t.toInt(), r.toInt().coerceAtLeast(l.toInt() + 1), b.toInt().coerceAtLeast(t.toInt() + 1))

        // kétlépcsős kicsinyítés: olcsó, lágy elmosás (nagyításkor a szűrés simára mossa)
        val mid = Bitmap.createBitmap(MID, MID, Bitmap.Config.ARGB_8888)
        Canvas(mid).drawBitmap(src, srcRect, Rect(0, 0, MID, MID), filter)
        val sm = Bitmap.createBitmap(SMALL, SMALL, Bitmap.Config.ARGB_8888)
        Canvas(sm).drawBitmap(mid, null, Rect(0, 0, SMALL, SMALL), filter)
        mid.recycle()

        val px = IntArray(SMALL * SMALL)
        sm.getPixels(px, 0, SMALL, 0, 0, SMALL, SMALL)
        var sum = 0f
        for (c in px) {
            sum += (0.299f * Color.red(c) + 0.587f * Color.green(c) + 0.114f * Color.blue(c)) / 255f
        }
        lum = sum / px.size

        small = sm
        smallShader = BitmapShader(sm, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
        keySrc = src
        keyGen = gen
        keyX = kx
        keyY = ky
        keyS = ks
        return true
    }

    override fun draw(canvas: Canvas) {
        val bb = bounds
        if (bb.isEmpty) return
        val cx = bb.exactCenterX()
        val cy = bb.exactCenterY()
        val r = minOf(bb.width(), bb.height()) / 2f

        val layer = if (layerAlpha < 255) {
            canvas.saveLayerAlpha(bb.left.toFloat(), bb.top.toFloat(), bb.right.toFloat(), bb.bottom.toFloat(), layerAlpha)
        } else {
            -1
        }

        val ok = try {
            ensureSmall()
        } catch (e: Throwable) {
            false
        }
        val sm = small
        val sh = smallShader
        if (!ok || sm == null || sh == null) {
            // nincs bélyegkép: a korábbi áttetsző kör
            canvas.drawCircle(cx, cy, r, oldFill)
            canvas.drawCircle(cx, cy, r - oldStroke.strokeWidth / 2f, oldStroke)
            if (layer >= 0) canvas.restoreToCount(layer)
            return
        }

        // a nagyított, elmosott képrész a körben
        srcF.set(0f, 0f, sm.width.toFloat(), sm.height.toFloat())
        dstF.set(cx - r, cy - r, cx + r, cy + r)
        m.setRectToRect(srcF, dstF, Matrix.ScaleToFit.FILL)
        sh.setLocalMatrix(m)
        glassPaint.shader = sh
        canvas.drawCircle(cx, cy, r, glassPaint)

        // sötétebb fátyol világos oldalon, hogy a fehér X mindig jól látsszon
        tintPaint.color = Color.argb((38 + 120 * lum).toInt().coerceIn(0, 255), 18, 20, 26)
        canvas.drawCircle(cx, cy, r, tintPaint)

        if (convex) {
            canvas.drawCircle(cx, cy, r, shadePaint)
            oval.set(cx - 0.62f * r, cy - 0.92f * r, cx + 0.62f * r, cy - 0.05f * r)
            canvas.drawOval(oval, sheenPaint)
        }
        canvas.drawCircle(cx, cy, r - rimPaint.strokeWidth / 2f, rimPaint)

        if (layer >= 0) canvas.restoreToCount(layer)
    }

    override fun setAlpha(alpha: Int) {
        if (layerAlpha == alpha) return
        layerAlpha = alpha
        invalidateSelf()
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        glassPaint.colorFilter = colorFilter
        tintPaint.colorFilter = colorFilter
    }

    @Suppress("OVERRIDE_DEPRECATION")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT

    companion object {
        /** A lencse nagyítása. */
        private const val LENS = 1.22f
        private const val MID = 24
        private const val SMALL = 9
    }
}
