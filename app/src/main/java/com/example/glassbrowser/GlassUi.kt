package com.example.glassbrowser

import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.text.InputType
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils

/**
 * Az új üveg felületek (felugró lista, olvasási lista, olvasó nézet) közös eszközei. A MainActivity
 * adja az üveg "bőrét" ([makeGlass], ugyanaz, mint a keresősávé), és a bezáráskor visszakapja
 * ([releaseGlass]), hogy a bőrök listája ne nőjön a végtelenségig.
 */
class GlassUi(
    val activity: AppCompatActivity,
    /** Ebbe a nézetbe kerülnek a felületek (a teljes képernyős gyökér). */
    val host: FrameLayout,
    /** Sötét üveg (világos szöveg). */
    val dark: Boolean,
    val makeGlass: (Float) -> Drawable,
    val releaseGlass: (Drawable) -> Unit,
    val haptic: () -> Unit
) {
    private val density = activity.resources.displayMetrics.density

    fun dp(v: Int): Int = (v * density).toInt()
    fun dpf(v: Float): Float = v * density

    val text: Int get() = if (dark) Color.WHITE else 0xFF1D1F24.toInt()
    val muted: Int get() = ColorUtils.setAlphaComponent(text, 150)
    val accent: Int = 0xFF0A84FF.toInt()
    val danger: Int = 0xFFFF453A.toInt()

    /**
     * Üveglap háttér: alul erős dér (a felületek a lencse hatásán kívül vannak, ezért elmosás
     * nélkül is olvashatónak kell lenniük), fölötte az üveg bőre a peremmel.
     */
    fun surface(radius: Float, owned: MutableList<Drawable>): Drawable {
        val frost = GradientDrawable().apply {
            cornerRadius = radius
            // the real glass (blur + refraction) is drawn below by the lens: only a light frost
            setColor(if (dark) Color.argb(110, 30, 32, 38) else Color.argb(130, 248, 249, 251))
        }
        val skin = makeGlass(radius)
        owned.add(skin)
        return LayerDrawable(arrayOf(frost, skin))
    }

    /** Egy menüsor (ikon + szöveg [+ pipa]), a hárompontos menü soraihoz hasonlóan. */
    fun row(
        label: String,
        icon: Int = 0,
        check: Boolean = false,
        destructive: Boolean = false,
        enabled: Boolean = true
    ): TextView {
        val tv = TextView(activity)
        tv.text = label
        tv.textSize = 16f
        tv.isSingleLine = true
        tv.ellipsize = TextUtils.TruncateAt.END
        tv.setPadding(dp(18), dp(12), dp(18), dp(12))
        tv.gravity = Gravity.CENTER_VERTICAL
        tv.compoundDrawablePadding = dp(14)
        val col = if (destructive) danger else text
        tv.setTextColor(if (enabled) col else ColorUtils.setAlphaComponent(col, 97))
        val sz = dp(20)
        val start = if (icon != 0) ContextCompat.getDrawable(activity, icon)?.mutate()?.apply { setBounds(0, 0, sz, sz) } else null
        val end = if (check) ContextCompat.getDrawable(activity, R.drawable.ic_m_check)?.mutate()?.apply { setBounds(0, 0, sz, sz) } else null
        tv.setCompoundDrawablesRelative(start, null, end, null)
        tv.compoundDrawableTintList = ColorStateList.valueOf(if (enabled) col else ColorUtils.setAlphaComponent(col, 97))
        tv.isEnabled = enabled
        val ta = activity.obtainStyledAttributes(intArrayOf(android.R.attr.selectableItemBackground))
        tv.background = ta.getDrawable(0)
        ta.recycle()
        return tv
    }

    /** Egy nézet helye a [host]-on belül. */
    fun rectIn(v: View): RectF {
        val a = IntArray(2)
        val b = IntArray(2)
        v.getLocationInWindow(a)
        host.getLocationInWindow(b)
        val l = (a[0] - b[0]).toFloat()
        val t = (a[1] - b[1]).toFloat()
        return RectF(l, t, l + v.width * v.scaleX, t + v.height * v.scaleY)
    }

    fun hideKeyboard(v: View) {
        val imm = activity.getSystemService(InputMethodManager::class.java) ?: return
        imm.hideSoftInputFromWindow(v.windowToken, 0)
    }

    fun showKeyboard(v: View) {
        val imm = activity.getSystemService(InputMethodManager::class.java) ?: return
        imm.showSoftInput(v, InputMethodManager.SHOW_IMPLICIT)
    }
}

/**
 * Kis üveg felugró lista (pl. a lapcsoportok választója), egy horgonyhoz igazítva nyílik ki
 * rugózva. Szövegbeviteli módja is van (pl. új csoport neve), mert a lapváltó fölött a
 * MainActivity üveg űrlapja nem használható.
 */
class GlassPopup(private val ui: GlassUi) {

    class Item(
        val text: String,
        val icon: Int = 0,
        val check: Boolean = false,
        val destructive: Boolean = false,
        val enabled: Boolean = true,
        val action: () -> Unit
    )

    private var scrim: FrameLayout? = null
    private var card: View? = null
    private var back: OnBackPressedCallback? = null
    /** Az épp látható lap üvegbőrei (bezáráskor visszaadjuk őket). */
    private var owned = ArrayList<Drawable>()
    private var closing = false

    val isShowing: Boolean get() = scrim != null
    /** A látható üveglap (a MainActivity lencséje rajzolja alá a valódi üveget). */
    val cardView: View? get() = card

    /** Lista megjelenítése a [anchor] téglalap (a host koordinátáiban) alatt vagy fölött. */
    fun show(anchor: RectF, title: String?, items: List<Item>) {
        dismissNow()
        val list = LinearLayout(ui.activity)
        list.orientation = LinearLayout.VERTICAL
        list.setPadding(0, ui.dp(6), 0, ui.dp(6))
        if (title != null) {
            val tv = TextView(ui.activity)
            tv.text = title
            tv.textSize = 13f
            tv.setTypeface(tv.typeface, Typeface.BOLD)
            tv.setTextColor(ui.muted)
            tv.setPadding(ui.dp(18), ui.dp(8), ui.dp(18), ui.dp(4))
            list.addView(tv)
        }
        for (item in items) {
            val row = ui.row(item.text, item.icon, item.check, item.destructive, item.enabled)
            row.setOnClickListener {
                ui.haptic()
                dismiss { item.action() }
            }
            list.addView(row, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
        val w = minOf(ui.host.width - ui.dp(32), ui.dp(290))
        list.measure(
            View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        )
        val maxH = (ui.host.height * 0.6f).toInt()
        val h = minOf(list.measuredHeight, maxH)
        val scroll = ScrollView(ui.activity)
        scroll.isVerticalScrollBarEnabled = false
        scroll.overScrollMode = View.OVER_SCROLL_NEVER
        scroll.addView(list, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        present(scroll, anchor, w, h)
    }

    /** Szövegbevitel (cím, egy mező, Mégse / [okText]); a képernyő felső részén jelenik meg. */
    fun showInput(anchor: RectF, title: String, hint: String, value: String, okText: String, onOk: (String) -> Unit) {
        dismissNow()
        val box = LinearLayout(ui.activity)
        box.orientation = LinearLayout.VERTICAL
        box.setPadding(ui.dp(20), ui.dp(18), ui.dp(20), ui.dp(10))
        val tv = TextView(ui.activity)
        tv.text = title
        tv.textSize = 18f
        tv.setTypeface(tv.typeface, Typeface.BOLD)
        tv.setTextColor(ui.text)
        box.addView(tv)
        val e = EditText(ui.activity)
        e.hint = hint
        e.setText(value)
        e.isSingleLine = true
        e.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
        e.imeOptions = EditorInfo.IME_ACTION_DONE
        e.textSize = 16f
        e.setTextColor(ui.text)
        e.setHintTextColor(ColorUtils.setAlphaComponent(ui.text, 110))
        e.setPadding(ui.dp(14), ui.dp(12), ui.dp(14), ui.dp(12))
        e.background = GradientDrawable().apply {
            cornerRadius = ui.dpf(14f)
            setColor(if (ui.dark) Color.argb(46, 255, 255, 255) else Color.argb(26, 0, 0, 0))
        }
        val elp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        elp.topMargin = ui.dp(12)
        box.addView(e, elp)
        val row = LinearLayout(ui.activity)
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.END
        val submit = {
            val v = e.text.toString().trim()
            if (v.isNotEmpty()) {
                ui.haptic()
                ui.hideKeyboard(e)
                dismiss { onOk(v) }
            }
        }
        fun button(label: String, primary: Boolean, click: () -> Unit) {
            val b = TextView(ui.activity)
            b.text = label
            b.textSize = 16f
            b.setPadding(ui.dp(14), ui.dp(10), ui.dp(14), ui.dp(10))
            b.setTextColor(if (primary) ui.accent else ui.text)
            if (primary) b.setTypeface(b.typeface, Typeface.BOLD)
            b.setOnClickListener { click() }
            row.addView(b)
        }
        button(L.t("Mégse"), false) {
            ui.hideKeyboard(e)
            dismiss()
        }
        button(okText, true) { submit() }
        val rlp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        rlp.topMargin = ui.dp(10)
        box.addView(row, rlp)
        e.setOnEditorActionListener { _, _, _ ->
            submit()
            true
        }
        val w = minOf(ui.host.width - ui.dp(40), ui.dp(360))
        box.measure(
            View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        )
        // a billentyűzet ne takarja: a képernyő felső harmadába kerül
        val top = (ui.host.height * 0.16f)
        val fake = RectF(anchor.centerX() - 1f, top - ui.dp(8), anchor.centerX() + 1f, top - ui.dp(8))
        present(box, fake, w, box.measuredHeight)
        e.postDelayed({
            e.requestFocus()
            e.setSelection(e.text.length)
            ui.showKeyboard(e)
        }, 180L)
    }

    private fun present(content: View, anchor: RectF, w: Int, h: Int) {
        val host = ui.host
        val sc = FrameLayout(ui.activity)
        sc.isClickable = true
        sc.setBackgroundColor(Color.argb(40, 0, 0, 0))
        sc.setOnClickListener { dismiss() }
        // a keresősáv (elevation) fölé kerüljön, árnyék nélkül
        sc.outlineProvider = null
        sc.translationZ = ui.dpf(28f)

        val skins = ArrayList<Drawable>()
        owned = skins
        val cardView = FrameLayout(ui.activity)
        cardView.background = ui.surface(ui.dpf(22f), skins)
        cardView.clipToOutline = true
        cardView.isClickable = true
        cardView.elevation = ui.dpf(12f)
        cardView.addView(content, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, h))

        val margin = ui.dp(16)
        val left = (anchor.centerX() - w / 2f).toInt().coerceIn(margin, maxOf(margin, host.width - margin - w))
        val below = anchor.bottom + ui.dp(8)
        val top = if (below + h <= host.height - margin) below.toInt()
        else (anchor.top - ui.dp(8) - h).toInt().coerceAtLeast(margin)
        val lp = FrameLayout.LayoutParams(w, h)
        lp.gravity = Gravity.TOP or Gravity.START
        lp.leftMargin = left
        lp.topMargin = top
        sc.addView(cardView, lp)
        host.addView(sc, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        scrim = sc
        card = cardView
        closing = false

        // a horgony felől nyílik ki
        cardView.pivotX = (anchor.centerX() - left).coerceIn(0f, w.toFloat())
        cardView.pivotY = if (top >= anchor.bottom) 0f else h.toFloat()
        cardView.alpha = 0f
        cardView.scaleX = 0.6f
        cardView.scaleY = 0.6f
        cardView.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(320L)
            .setInterpolator(OvershootInterpolator(1.15f)).start()
        sc.alpha = 0f
        sc.animate().alpha(1f).setDuration(200L).start()

        val cb = object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                dismiss()
            }
        }
        ui.activity.onBackPressedDispatcher.addCallback(ui.activity, cb)
        back = cb
    }

    /** Bezárás animációval, utána [then]. */
    fun dismiss(then: (() -> Unit)? = null) {
        val sc = scrim
        val c = card
        if (sc == null || c == null) {
            then?.invoke()
            return
        }
        if (closing) return
        closing = true
        back?.remove()
        back = null
        sc.isClickable = false
        c.animate().cancel()
        c.animate().alpha(0f).scaleX(0.85f).scaleY(0.85f).setDuration(160L)
            .setInterpolator(DecelerateInterpolator()).start()
        val skins = owned
        sc.animate().alpha(0f).setDuration(180L).withEndAction {
            removeViews(sc, skins)
        }.start()
        then?.invoke()
    }

    /** Azonnali bezárás (animáció nélkül), pl. egy új lista megnyitása előtt. */
    fun dismissNow() {
        val sc = scrim ?: return
        back?.remove()
        back = null
        if (closing) {
            // a régi lap épp záródik: hagyjuk végigfutni (a végén saját magát takarítja el)
            scrim = null
            card = null
            closing = false
            return
        }
        sc.animate().cancel()
        card?.animate()?.cancel()
        removeViews(sc, owned)
    }

    private fun removeViews(sc: View, skins: MutableList<Drawable>) {
        (sc.parent as? ViewGroup)?.removeView(sc)
        if (scrim === sc) {
            scrim = null
            card = null
            closing = false
        }
        skins.forEach { ui.releaseGlass(it) }
        skins.clear()
    }
}
