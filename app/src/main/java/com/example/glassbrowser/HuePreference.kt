package com.example.glassbrowser

import android.content.Context
import android.content.res.TypedArray
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.util.AttributeSet
import android.view.View
import android.widget.SeekBar
import androidx.preference.Preference
import androidx.preference.PreferenceViewHolder

/** Színválasztó csúszka: a sáv maga a színskála, mellette a kiválasztott szín mintája. */
class HuePreference @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : Preference(context, attrs) {

    private var hue = DEFAULT
    private var preview: View? = null
    private var seek: SeekBar? = null

    init {
        layoutResource = R.layout.pref_hue
        isSelectable = false
    }

    fun setHue(value: Int) {
        hue = value.coerceIn(0, 360)
        persistInt(hue)
        seek?.progress = hue
        updatePreview()
    }

    override fun onGetDefaultValue(a: TypedArray, index: Int): Any = a.getInt(index, DEFAULT)

    override fun onSetInitialValue(defaultValue: Any?) {
        hue = getPersistedInt((defaultValue as? Int) ?: DEFAULT)
    }

    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        super.onBindViewHolder(holder)
        val d = context.resources.displayMetrics.density
        preview = holder.findViewById(R.id.huePreview)
        val bar = holder.findViewById(R.id.hueSeek) as? SeekBar ?: return
        seek = bar

        SliderStyle.apply(bar, IntArray(13) { colorOf(it * 30) })
        bar.progress = hue
        bar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (!fromUser) return
                hue = progress
                persistInt(hue)
                updatePreview()
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
        updatePreview()
    }

    private fun updatePreview() {
        val v = preview ?: return
        val d = context.resources.displayMetrics.density
        val g = GradientDrawable()
        g.cornerRadius = 13 * d
        g.setColor(colorOf(hue))
        g.setStroke((1 * d).toInt().coerceAtLeast(1), Color.argb(90, 255, 255, 255))
        v.background = g
    }

    companion object {
        const val DEFAULT = 210
        fun colorOf(hue: Int): Int = Color.HSVToColor(floatArrayOf((hue % 360).toFloat(), 0.85f, 1f))
    }
}
