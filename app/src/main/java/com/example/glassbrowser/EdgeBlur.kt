package com.example.glassbrowser

import android.graphics.BlendMode
import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.view.View
import androidx.annotation.RequiresApi

/**
 * Görgethető lista felső és alsó szélének elmosása (Android 13+).
 *
 * A lista tartalma a felső és az alsó szélén fokozatosan elmosódik és kicsit elhalványul, mintha
 * üveg alá csúszna. A rendszer saját (Gauss) elmosását használja, nem a mintavételes közelítést,
 * így görgetés közben sem vibrál. Csak a listára kerül (a fejlécre és a háttérre nem), ezért a
 * fejléc szövege éles marad, a háttérkép pedig nem mosódik el kétszer.
 *
 * Felépítés: éles kép * (1 - w) + elmosott kép * w, ahol w a szélek felé 1-re nő (PLUS keverés,
 * előszorzott színekkel ez pontos átúsztatás).
 */
@RequiresApi(33)
class EdgeBlur(private val target: View) {

    private val sharp = RuntimeShader(SRC)
    private val soft = RuntimeShader(SRC)
    private val ext = FloatArray(2)
    private val amt = FloatArray(2)
    private var height = -1f
    private var blur = 0f
    private var attached = false

    /**
     * [topAmount] / [bottomAmount]: a felső / alsó elmosás erőssége (0..1; 0, ha ott nincs több
     * tartalom). [extentPx]: ilyen magas sávban halványul el. [blurPx]: az elmosás sugara.
     */
    fun update(topAmount: Float, bottomAmount: Float, extentPx: Float, blurPx: Float) {
        val t = topAmount.coerceIn(0f, 1f)
        val b = bottomAmount.coerceIn(0f, 1f)
        val h = target.height.toFloat()
        if (h <= 0f || (t <= 0.01f && b <= 0.01f) || blurPx < 1f) {
            clear()
            return
        }
        if (attached && amt[0] == t && amt[1] == b && height == h && ext[0] == extentPx && blur == blurPx) return
        amt[0] = t
        amt[1] = b
        ext[0] = extentPx
        ext[1] = extentPx
        height = h
        blur = blurPx
        sharp.setFloatUniform("h", h)
        sharp.setFloatUniform("ext", ext)
        sharp.setFloatUniform("amt", amt)
        sharp.setFloatUniform("mode", 0f)
        soft.setFloatUniform("h", h)
        soft.setFloatUniform("ext", ext)
        soft.setFloatUniform("amt", amt)
        soft.setFloatUniform("mode", 1f)
        val keep = RenderEffect.createRuntimeShaderEffect(sharp, "content")
        // elmosás (a széleken túl átlátszó: DECAL) -> maszk (a szélek felé erősödik)
        val blurred = RenderEffect.createChainEffect(
            RenderEffect.createRuntimeShaderEffect(soft, "content"),
            RenderEffect.createBlurEffect(blurPx, blurPx, Shader.TileMode.DECAL)
        )
        target.setRenderEffect(RenderEffect.createBlendModeEffect(keep, blurred, BlendMode.PLUS))
        attached = true
    }

    fun clear() {
        if (attached) {
            attached = false
            target.setRenderEffect(null)
        }
    }

    companion object {
        private const val SRC = """
uniform shader content;
uniform float h;
uniform vec2 ext;
uniform vec2 amt;
uniform float mode;

half4 main(vec2 c) {
    float wt = clamp(1.0 - c.y / max(ext.x, 1.0), 0.0, 1.0);
    float wb = clamp(1.0 - (h - c.y) / max(ext.y, 1.0), 0.0, 1.0);
    wt = wt * wt * (3.0 - 2.0 * wt) * amt.x;
    wb = wb * wb * (3.0 - 2.0 * wb) * amt.y;
    float w = max(wt, wb);
    // a legszélén a tartalom kissé el is halványul: lágy átmenet a fejléc / a képernyő alja felé
    float fade = 1.0 - 0.45 * w * w;
    float k = mode < 0.5 ? (1.0 - w) : w;
    half4 col = content.eval(c);
    return col * half(k * fade);
}
"""
    }
}
