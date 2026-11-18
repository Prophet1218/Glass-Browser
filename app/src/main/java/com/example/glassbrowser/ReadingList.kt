package com.example.glassbrowser

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import androidx.preference.PreferenceManager
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener

/** Egy olvasási lista elem. */
class ReadingItem(val url: String, var title: String, var snippet: String, val time: Long, var read: Boolean)

/**
 * Olvasási lista (később elolvasandó oldalak), a beállítások között JSON-ként, mint a
 * könyvjelzők. Mindig a főszálról hívandó.
 */
object ReadingListStore {
    private const val KEY = "reading_list"
    private const val MAX = 500
    private val items = ArrayList<ReadingItem>()   // a legújabb van elöl
    private var loaded = false

    /** A lap rövid leírása (meta description, vagy az első hosszabb bekezdés eleje). */
    const val SNIPPET_JS =
        "(function(){try{var m=document.querySelector('meta[name=\"description\"],meta[property=\"og:description\"]');" +
            "var s=(m&&m.content)||'';if(!s){var ps=document.querySelectorAll('p');for(var i=0;i<ps.length;i++){" +
            "var t=(ps[i].textContent||'').replace(/\\s+/g,' ').trim();if(t.length>60){s=t;break;}}}" +
            "return s.replace(/\\s+/g,' ').trim().slice(0,220);}catch(e){return '';}})()"

    private fun load(context: Context) {
        if (loaded) return
        loaded = true
        try {
            val p = PreferenceManager.getDefaultSharedPreferences(context)
            val arr = JSONArray(p.getString(KEY, "[]") ?: "[]")
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val url = o.optString("u")
                if (url.isNotEmpty()) {
                    items.add(ReadingItem(url, o.optString("t"), o.optString("s"), o.optLong("d"), o.optBoolean("r")))
                }
            }
        } catch (e: Exception) {
            items.clear()
        }
    }

    private fun save(context: Context) {
        val arr = JSONArray()
        for (e in items) {
            arr.put(JSONObject().put("u", e.url).put("t", e.title).put("s", e.snippet).put("d", e.time).put("r", e.read))
        }
        PreferenceManager.getDefaultSharedPreferences(context).edit().putString(KEY, arr.toString()).apply()
    }

    fun list(context: Context): List<ReadingItem> {
        load(context)
        return items.toList()
    }

    fun has(context: Context, url: String): Boolean {
        load(context)
        return items.any { it.url == url }
    }

    /** Hozzáadás; hamis, ha már a listán volt. */
    fun add(context: Context, url: String, title: String, snippet: String = ""): Boolean {
        load(context)
        if (items.any { it.url == url }) return false
        items.add(0, ReadingItem(url, title, snippet, System.currentTimeMillis(), false))
        while (items.size > MAX) items.removeAt(items.size - 1)
        save(context)
        return true
    }

    fun setSnippet(context: Context, url: String, snippet: String) {
        load(context)
        val item = items.firstOrNull { e -> e.url == url } ?: return
        item.snippet = snippet
        save(context)
    }

    fun setRead(context: Context, url: String, read: Boolean) {
        load(context)
        val item = items.firstOrNull { e -> e.url == url } ?: return
        if (item.read == read) return
        item.read = read
        save(context)
    }

    fun remove(context: Context, url: String) {
        load(context)
        if (items.removeAll { it.url == url }) save(context)
    }

    /** Az evaluateJavascript szöveges eredménye (JSON-idézett) sima szövegként. */
    fun decodeJsString(result: String?): String {
        if (result.isNullOrBlank() || result == "null") return ""
        return try {
            (JSONTokener(result).nextValue() as? String).orEmpty()
        } catch (e: Exception) {
            ""
        }
    }
}

/**
 * Oldalra húzható sor: vízszintes húzásra a tartalom ([content]) elcsúszik, mögötte piros törlés
 * sáv tűnik fel; elég messzire húzva kirepül, és meghívódik az [onSwiped]. A koppintást és a
 * hosszú nyomást a tartalom kapja meg (függőleges görgetés közben sem indul húzás).
 */
@SuppressLint("ViewConstructor")
class SwipeRow(context: Context, private val onSwiped: (SwipeRow) -> Unit) : FrameLayout(context) {
    private val slop = ViewConfiguration.get(context).scaledTouchSlop
    private var downX = 0f
    private var downY = 0f
    private var startX = 0f
    private var dragging = false
    val reveal = FrameLayout(context)
    lateinit var content: View
        private set

    fun setContent(v: View, revealColor: Int, icon: Drawable?) {
        reveal.background = GradientDrawable().apply {
            cornerRadius = 14f * resources.displayMetrics.density
            setColor(revealColor)
        }
        reveal.alpha = 0f
        if (icon != null) {
            val iv = ImageView(context)
            iv.setImageDrawable(icon)
            iv.imageTintList = ColorStateList.valueOf(Color.WHITE)
            val s = (22 * resources.displayMetrics.density).toInt()
            val lp = LayoutParams(s, s, Gravity.CENTER_VERTICAL or Gravity.END)
            lp.marginEnd = (20 * resources.displayMetrics.density).toInt()
            reveal.addView(iv, lp)
            val iv2 = ImageView(context)
            iv2.setImageDrawable(icon.constantState?.newDrawable()?.mutate() ?: icon)
            iv2.imageTintList = ColorStateList.valueOf(Color.WHITE)
            val lp2 = LayoutParams(s, s, Gravity.CENTER_VERTICAL or Gravity.START)
            lp2.marginStart = (20 * resources.displayMetrics.density).toInt()
            reveal.addView(iv2, lp2)
        }
        addView(reveal, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        addView(v, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        content = v
    }

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = ev.x
                downY = ev.y
                dragging = false
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = ev.x - downX
                val dy = ev.y - downY
                if (!dragging && kotlin.math.abs(dx) > slop && kotlin.math.abs(dx) > kotlin.math.abs(dy) * 1.5f) {
                    dragging = true
                    startX = ev.x - content.translationX
                    parent?.requestDisallowInterceptTouchEvent(true)
                    content.cancelLongPress()
                    content.isPressed = false
                }
            }
        }
        return dragging
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(ev: MotionEvent): Boolean {
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> return true
            MotionEvent.ACTION_MOVE -> if (dragging) {
                val tx = ev.x - startX
                content.translationX = tx
                val f = (kotlin.math.abs(tx) / width.coerceAtLeast(1)).coerceIn(0f, 1f)
                reveal.alpha = (f * 3f).coerceAtMost(1f)
                content.alpha = 1f - f * 0.6f
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> if (dragging) {
                dragging = false
                val tx = content.translationX
                if (ev.actionMasked == MotionEvent.ACTION_UP && kotlin.math.abs(tx) > width * 0.35f) {
                    val target = if (tx > 0) width.toFloat() else -width.toFloat()
                    content.animate().translationX(target).alpha(0f).setDuration(180L)
                        .setInterpolator(DecelerateInterpolator())
                        .withEndAction { onSwiped(this) }.start()
                } else {
                    content.animate().translationX(0f).alpha(1f).setDuration(260L)
                        .setInterpolator(OvershootInterpolator(1.2f)).start()
                    reveal.animate().alpha(0f).setDuration(200L).start()
                }
            }
        }
        return true
    }

    /** A sor összecsukódik, majd eltűnik a listából. */
    fun collapse(then: () -> Unit) {
        val h = height
        if (h <= 0) {
            then()
            return
        }
        val anim = ValueAnimator.ofInt(h, 0)
        anim.duration = 200L
        anim.interpolator = DecelerateInterpolator()
        anim.addUpdateListener {
            val lp = layoutParams
            lp.height = it.animatedValue as Int
            layoutParams = lp
        }
        anim.addListener(object : android.animation.AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: android.animation.Animator) {
                then()
            }
        })
        anim.start()
    }
}

/**
 * Az olvasási lista üveg lapja (a hárompontos menüből). Koppintásra megnyitja az oldalt,
 * hosszú nyomásra: megnyitás új lapon, olvasottnak / olvasatlannak jelölés, törlés; oldalra
 * húzva törlődik.
 */
class ReadingListSheet(
    private val ui: GlassUi,
    private val topInset: Int,
    private val bottomInset: Int,
    private val onOpen: (String) -> Unit,
    private val onOpenNewTab: (String) -> Unit
) {
    private val ctx = ui.activity
    private var scrim: FrameLayout? = null
    private var card: LinearLayout? = null
    private lateinit var listBox: LinearLayout
    private lateinit var countLabel: TextView
    private val owned = ArrayList<Drawable>()
    private var back: OnBackPressedCallback? = null
    private val popup = GlassPopup(ui)
    private var closing = false

    fun show() {
        val host = ui.host
        val sc = FrameLayout(ctx)
        sc.isClickable = true
        sc.setBackgroundColor(Color.argb(70, 0, 0, 0))
        sc.outlineProvider = null
        sc.translationZ = ui.dpf(26f)
        sc.setOnClickListener { dismiss() }

        val c = LinearLayout(ctx)
        c.orientation = LinearLayout.VERTICAL
        c.background = ui.surface(ui.dpf(28f), owned)
        c.clipToOutline = true
        c.isClickable = true
        c.elevation = ui.dpf(14f)

        // fejléc: cím, darabszám, bezárás
        val head = LinearLayout(ctx)
        head.orientation = LinearLayout.HORIZONTAL
        head.gravity = Gravity.CENTER_VERTICAL
        head.setPadding(ui.dp(20), ui.dp(14), ui.dp(8), ui.dp(6))
        val title = TextView(ctx)
        title.text = L.t("Olvasási lista")
        title.textSize = 20f
        title.setTypeface(title.typeface, Typeface.BOLD)
        title.setTextColor(ui.text)
        head.addView(title)
        countLabel = TextView(ctx)
        countLabel.textSize = 14f
        countLabel.setTextColor(ui.muted)
        countLabel.setPadding(ui.dp(10), ui.dp(3), 0, 0)
        head.addView(countLabel, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        val close = ImageButton(ctx)
        close.setImageResource(R.drawable.ic_close)
        close.background = null
        close.imageTintList = ColorStateList.valueOf(ui.text)
        close.setPadding(ui.dp(10), ui.dp(10), ui.dp(10), ui.dp(10))
        close.scaleType = ImageView.ScaleType.CENTER_INSIDE
        close.contentDescription = L.t("Bezárás")
        close.setOnClickListener { dismiss() }
        head.addView(close, LinearLayout.LayoutParams(ui.dp(44), ui.dp(44)))
        c.addView(head)

        listBox = LinearLayout(ctx)
        listBox.orientation = LinearLayout.VERTICAL
        listBox.setPadding(ui.dp(8), 0, ui.dp(8), ui.dp(12))
        val scroll = ScrollView(ctx)
        scroll.isVerticalScrollBarEnabled = false
        scroll.overScrollMode = View.OVER_SCROLL_NEVER
        scroll.addView(listBox, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        c.addView(scroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        fill()

        // a lap a képernyő közepén, a rendszersávoktól távol
        val w = minOf(host.width - ui.dp(24), ui.dp(520))
        val lp = FrameLayout.LayoutParams(w, ViewGroup.LayoutParams.MATCH_PARENT)
        lp.gravity = Gravity.CENTER_HORIZONTAL
        lp.topMargin = topInset + ui.dp(28)
        lp.bottomMargin = bottomInset + ui.dp(28)
        sc.addView(c, lp)
        host.addView(sc, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        scrim = sc
        card = c

        c.alpha = 0f
        c.translationY = ui.dpf(40f)
        c.scaleX = 0.94f
        c.scaleY = 0.94f
        c.animate().alpha(1f).translationY(0f).scaleX(1f).scaleY(1f).setDuration(360L)
            .setInterpolator(OvershootInterpolator(0.9f)).start()
        sc.alpha = 0f
        sc.animate().alpha(1f).setDuration(220L).start()

        val cb = object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                dismiss()
            }
        }
        ctx.onBackPressedDispatcher.addCallback(ctx, cb)
        back = cb
    }

    private fun updateCount(items: List<ReadingItem>) {
        val unread = items.count { !it.read }
        countLabel.text = if (items.isEmpty()) "" else if (L.en) "$unread unread" else "$unread olvasatlan"
    }

    private fun fill() {
        listBox.removeAllViews()
        val items = ReadingListStore.list(ctx)
        updateCount(items)
        if (items.isEmpty()) {
            val e = TextView(ctx)
            e.text = L.t("Az olvasási lista üres. A hárompontos menüben adhatsz hozzá oldalakat.")
            e.textSize = 15f
            e.gravity = Gravity.CENTER
            e.setTextColor(ui.muted)
            e.setPadding(ui.dp(24), ui.dp(40), ui.dp(24), ui.dp(40))
            listBox.addView(e)
            return
        }
        for (item in items) listBox.addView(rowFor(item), LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
    }

    private fun rowFor(item: ReadingItem): SwipeRow {
        val row = SwipeRow(ctx) { r -> delete(item, r) }
        val content = LinearLayout(ctx)
        content.orientation = LinearLayout.HORIZONTAL
        content.gravity = Gravity.CENTER_VERTICAL
        content.setPadding(ui.dp(12), ui.dp(10), ui.dp(12), ui.dp(10))
        val ta = ctx.obtainStyledAttributes(intArrayOf(android.R.attr.selectableItemBackground))
        content.background = ta.getDrawable(0)
        ta.recycle()

        // olvasatlan: kék pötty
        val dot = View(ctx)
        dot.background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(if (item.read) Color.TRANSPARENT else ui.accent)
        }
        val dlp = LinearLayout.LayoutParams(ui.dp(9), ui.dp(9))
        dlp.marginEnd = ui.dp(12)
        content.addView(dot, dlp)

        val texts = LinearLayout(ctx)
        texts.orientation = LinearLayout.VERTICAL
        val t = TextView(ctx)
        t.text = item.title.ifBlank { Urls.host(item.url) }
        t.textSize = 16f
        t.setTypeface(t.typeface, Typeface.BOLD)
        t.maxLines = 2
        t.ellipsize = TextUtils.TruncateAt.END
        t.setTextColor(ui.text)
        texts.addView(t)
        val sub = TextView(ctx)
        sub.text = if (item.snippet.isNotBlank()) "${Urls.host(item.url)} · ${item.snippet}" else Urls.host(item.url)
        sub.textSize = 13.5f
        sub.maxLines = 2
        sub.ellipsize = TextUtils.TruncateAt.END
        sub.setTextColor(ui.muted)
        sub.setPadding(0, ui.dp(2), 0, 0)
        texts.addView(sub)
        content.addView(texts, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        texts.alpha = if (item.read) 0.6f else 1f

        content.setOnClickListener {
            ui.haptic()
            ReadingListStore.setRead(ctx, item.url, true)
            dismiss { onOpen(item.url) }
        }
        content.setOnLongClickListener {
            ui.haptic()
            popup.show(
                ui.rectIn(row), null,
                listOf(
                    GlassPopup.Item(L.t("Megnyitás új lapon"), R.drawable.ic_m_add) {
                        ReadingListStore.setRead(ctx, item.url, true)
                        dismiss { onOpenNewTab(item.url) }
                    },
                    GlassPopup.Item(
                        if (item.read) L.t("Megjelölés olvasatlanként") else L.t("Megjelölés olvasottként"),
                        R.drawable.ic_m_check
                    ) {
                        ReadingListStore.setRead(ctx, item.url, !item.read)
                        fill()
                    },
                    GlassPopup.Item(L.t("Törlés"), R.drawable.ic_m_delete, destructive = true) {
                        delete(item, row)
                    }
                )
            )
            true
        }
        row.setContent(content, ui.danger, ContextCompat.getDrawable(ctx, R.drawable.ic_m_delete)?.mutate())
        return row
    }

    private fun delete(item: ReadingItem, row: SwipeRow) {
        ReadingListStore.remove(ctx, item.url)
        row.collapse {
            listBox.removeView(row)
            val left = ReadingListStore.list(ctx)
            if (left.isEmpty()) fill() else updateCount(left)
        }
    }

    fun dismiss(then: (() -> Unit)? = null) {
        val sc = scrim ?: return
        val c = card ?: return
        if (closing) return
        closing = true
        popup.dismissNow()
        back?.remove()
        back = null
        sc.isClickable = false
        c.animate().cancel()
        c.animate().alpha(0f).translationY(ui.dpf(30f)).scaleX(0.96f).scaleY(0.96f).setDuration(200L)
            .setInterpolator(DecelerateInterpolator()).start()
        sc.animate().alpha(0f).setDuration(220L).withEndAction {
            (sc.parent as? ViewGroup)?.removeView(sc)
            scrim = null
            card = null
            owned.forEach { ui.releaseGlass(it) }
            owned.clear()
        }.start()
        then?.invoke()
    }
}
