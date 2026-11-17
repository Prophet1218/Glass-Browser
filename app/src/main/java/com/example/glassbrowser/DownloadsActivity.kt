package com.example.glassbrowser

import android.animation.Animator
import android.animation.LayoutTransition
import android.animation.AnimatorListenerAdapter
import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.app.DownloadManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.database.Cursor
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.RenderEffect
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.SystemClock
import android.text.TextUtils
import android.text.format.Formatter
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.OvershootInterpolator
import android.view.animation.PathInterpolator
import android.webkit.CookieManager
import android.webkit.MimeTypeMap
import android.webkit.URLUtil
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.preference.PreferenceManager
import kotlin.math.max
import kotlin.math.min

/** Letöltések: a böngészőből indított letöltések a rendszer letöltéskezelőjével futnak. */
object Downloads {
    private const val PREF = "my_download_ids"

    /** Letöltés indítása a Letöltések mappába. Igazat ad, ha sikerült elindítani. */
    fun start(ctx: Context, url: String, userAgent: String?, contentDisposition: String?, mime: String?): Boolean {
        if (!url.startsWith("http://") && !url.startsWith("https://")) return false
        return try {
            val name = URLUtil.guessFileName(url, contentDisposition, mime)
            val req = DownloadManager.Request(Uri.parse(url))
            CookieManager.getInstance().getCookie(url)?.let { req.addRequestHeader("Cookie", it) }
            if (!userAgent.isNullOrBlank()) req.addRequestHeader("User-Agent", userAgent)
            val type = mime?.takeIf { it.isNotBlank() && it != "application/octet-stream" }
                ?: MimeTypeMap.getSingleton().getMimeTypeFromExtension(name.substringAfterLast('.', ""))
            if (type != null) req.setMimeType(type)
            req.setTitle(name)
            req.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            req.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, name)
            val dm = ctx.getSystemService(DownloadManager::class.java) ?: return false
            val id = dm.enqueue(req)
            remember(ctx, id)
            true
        } catch (e: Exception) {
            false
        }
    }

    private fun remember(ctx: Context, id: Long) {
        val p = PreferenceManager.getDefaultSharedPreferences(ctx)
        val ids = p.getString(PREF, "").orEmpty().split(',').filter { it.isNotBlank() }.toMutableList()
        ids.add(0, id.toString())
        p.edit().putString(PREF, ids.take(200).joinToString(",")).apply()
    }

    fun ids(ctx: Context): LongArray {
        val p = PreferenceManager.getDefaultSharedPreferences(ctx)
        return p.getString(PREF, "").orEmpty().split(',').mapNotNull { it.toLongOrNull() }.toLongArray()
    }

    fun forget(ctx: Context, id: Long) {
        val p = PreferenceManager.getDefaultSharedPreferences(ctx)
        val ids = p.getString(PREF, "").orEmpty().split(',').filter { it.isNotBlank() && it != id.toString() }
        p.edit().putString(PREF, ids.joinToString(",")).apply()
    }
}

/**
 * Letöltések képernyő a böngésző üvegstílusában (mint a beállítások): a háttér a böngésző
 * elmosott pillanatképe, a fájlok áttetsző, finom peremű üvegkártyákon, típus szerinti színes
 * ikonlapkával, vékony, lekerekített folyamatjelzővel. Nagy cím, görgetéskor a felső sávba
 * úszó kis címmel. Koppintásra megnyílik a fájl; megosztás és törlés a kártya gombjaival.
 */
class DownloadsActivity : AppCompatActivity() {

    private class Item(
        val id: Long, val title: String, val status: Int, val total: Long, val got: Long,
        val mime: String?, val time: Long
    )

    /** Egy letöltés kártyájának nézetei (frissítéskor újrahasznosítjuk őket, nem villog). */
    private class Row(
        val card: LinearLayout,
        val art: FileIconDrawable,
        val name: TextView,
        val bar: ThinProgress,
        val sub: TextView,
        val share: ImageButton,
        val del: ImageButton
    ) {
        var deleting = false
        var press: Animator? = null
    }

    private val items = ArrayList<Item>()
    private val rows = ArrayList<Row>()
    private val shownIds = HashSet<Long>()
    private lateinit var list: LinearLayout
    private lateinit var empty: LinearLayout
    private lateinit var emptyBadge: View
    private lateinit var countText: TextView
    private lateinit var bigTitle: TextView
    private lateinit var smallTitle: TextView
    private lateinit var scroll: ScrollView
    private val handler = android.os.Handler(android.os.Looper.getMainLooper())
    private var night = false
    private var lastKey = ""
    private var firstRender = true
    private var emptyShown = false
    private var onScrollExtra: (() -> Unit)? = null
    private val refresher = object : Runnable {
        override fun run() {
            load()
            handler.postDelayed(this, 800L)
        }
    }

    private val ease = PathInterpolator(0.2f, 0.9f, 0.25f, 1f)

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun dpf(v: Float) = v * resources.displayMetrics.density
    private val textColor get() = if (night) 0xFFF2F3F7.toInt() else 0xFF14171F.toInt()
    private val mutedColor get() = if (night) 0xFF9AA0AD.toInt() else 0xFF5F6470.toInt()
    /** Az üvegkártyák kitöltése és pereme (ugyanaz, mint a beállítások kártyáin). */
    private val glassFill get() = if (night) 0x1CFFFFFF else 0x9EFFFFFF.toInt()
    private val glassRim get() = if (night) 0x33FFFFFF else 0x17000000
    private val pressTint get() = if (night) 0x24FFFFFF else 0x16000000
    private val accent = 0xFF0A84FF.toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        ThemeHelper.apply(prefs)
        L.init(this)
        super.onCreate(savedInstanceState)
        supportActionBar?.hide()
        night = (resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
            android.content.res.Configuration.UI_MODE_NIGHT_YES
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = !night
            isAppearanceLightNavigationBars = !night
        }

        val root = FrameLayout(this)
        root.setBackgroundColor(getColor(R.color.app_bg))

        // Üveghatás: a böngésző elmosott pillanatképe (ha van), különben lágy színátmenet
        val bg = ImageView(this)
        bg.scaleType = ImageView.ScaleType.FIT_XY
        bg.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        val shot = SettingsActivity.backdrop
        if (shot != null && !shot.isRecycled) {
            bg.setImageBitmap(shot)
            if (Build.VERSION.SDK_INT >= 31) {
                bg.setRenderEffect(RenderEffect.createBlurEffect(40f, 40f, Shader.TileMode.CLAMP))
            }
        } else {
            bg.setImageDrawable(
                GradientDrawable(
                    GradientDrawable.Orientation.TL_BR,
                    if (night) intArrayOf(0xFF1A2440.toInt(), 0xFF141823.toInt(), 0xFF2A1B33.toInt())
                    else intArrayOf(0xFFDCE8FF.toInt(), 0xFFF3F4F8.toInt(), 0xFFF6E6F2.toInt())
                )
            )
        }
        root.addView(bg, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        val scrim = View(this)
        scrim.setBackgroundColor(getColor(R.color.settings_scrim))
        root.addView(scrim, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))

        val column = LinearLayout(this)
        column.orientation = LinearLayout.VERTICAL
        root.addView(column, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))

        // felső sáv: kerek üveg vissza gomb, középen a (görgetéskor beúszó) kis cím, jobbra "Mappa"
        val bar = FrameLayout(this)
        bar.setPadding(dp(12), 0, dp(14), 0)
        val back = ImageButton(this)
        back.setImageResource(R.drawable.ic_back)
        back.imageTintList = ColorStateList.valueOf(textColor)
        back.background = glassDrawable(oval = true)
        back.contentDescription = L.t("Vissza")
        back.setPadding(dp(9), dp(9), dp(9), dp(9))
        back.scaleType = ImageView.ScaleType.FIT_CENTER
        back.setOnClickListener { finish() }
        bar.addView(back, FrameLayout.LayoutParams(dp(42), dp(42), Gravity.START or Gravity.CENTER_VERTICAL))
        smallTitle = TextView(this)
        smallTitle.text = L.t("Letöltések")
        smallTitle.textSize = 17f
        smallTitle.setTypeface(smallTitle.typeface, Typeface.BOLD)
        smallTitle.setTextColor(textColor)
        smallTitle.alpha = 0f
        bar.addView(
            smallTitle,
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER)
        )
        val folder = TextView(this)
        folder.text = L.t("Mappa")
        folder.textSize = 14f
        folder.setTypeface(folder.typeface, Typeface.BOLD)
        folder.setTextColor(accent)
        folder.gravity = Gravity.CENTER
        folder.setPadding(dp(16), 0, dp(16), 0)
        folder.background = glassDrawable(oval = false, radius = dpf(100f))
        folder.setOnClickListener {
            try {
                startActivity(Intent(DownloadManager.ACTION_VIEW_DOWNLOADS))
            } catch (e: ActivityNotFoundException) {
                // nincs ilyen képernyő
            }
        }
        bar.addView(folder, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(38), Gravity.END or Gravity.CENTER_VERTICAL))
        column.addView(bar, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(56)))

        scroll = ScrollView(this)
        scroll.isFillViewport = true
        scroll.clipToPadding = false
        scroll.isVerticalScrollBarEnabled = false
        val content = LinearLayout(this)
        content.orientation = LinearLayout.VERTICAL
        content.setPadding(dp(14), 0, dp(14), dp(24))

        // nagy cím és alatta az összesítés (hány fájl, hány fut még)
        bigTitle = TextView(this)
        bigTitle.text = L.t("Letöltések")
        bigTitle.textSize = 34f
        bigTitle.setTypeface(bigTitle.typeface, Typeface.BOLD)
        bigTitle.setTextColor(textColor)
        bigTitle.setPadding(dp(6), dp(2), dp(6), 0)
        content.addView(bigTitle)
        countText = TextView(this)
        countText.textSize = 14f
        countText.setTextColor(mutedColor)
        countText.setPadding(dp(7), dp(2), dp(6), 0)
        content.addView(countText)

        list = LinearLayout(this)
        list.orientation = LinearLayout.VERTICAL
        list.setPadding(0, dp(16), 0, 0)
        content.addView(list)

        // üres állapot: üveg korong a letöltés ikonnal, cím és rövid magyarázat
        empty = LinearLayout(this)
        empty.orientation = LinearLayout.VERTICAL
        empty.gravity = Gravity.CENTER
        empty.setPadding(dp(24), 0, dp(24), dp(48))
        val badge = FrameLayout(this)
        badge.background = glassDrawable(oval = true)
        val eIcon = ImageView(this)
        eIcon.setImageResource(R.drawable.ic_m_download)
        eIcon.imageTintList = ColorStateList.valueOf(accent)
        eIcon.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        badge.addView(eIcon, FrameLayout.LayoutParams(dp(44), dp(44), Gravity.CENTER))
        empty.addView(badge, LinearLayout.LayoutParams(dp(104), dp(104)))
        emptyBadge = badge
        val eTitle = TextView(this)
        eTitle.text = L.t("Még nincs letöltés.")
        eTitle.textSize = 19f
        eTitle.setTypeface(eTitle.typeface, Typeface.BOLD)
        eTitle.setTextColor(textColor)
        eTitle.gravity = Gravity.CENTER
        eTitle.setPadding(0, dp(20), 0, 0)
        empty.addView(eTitle)
        val eHint = TextView(this)
        eHint.text = L.t("A böngészőből letöltött fájlok itt jelennek meg.")
        eHint.textSize = 14f
        eHint.setTextColor(mutedColor)
        eHint.gravity = Gravity.CENTER
        eHint.setPadding(0, dp(6), 0, 0)
        empty.addView(eHint)
        empty.visibility = View.GONE
        content.addView(empty, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

        scroll.addView(content, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        column.addView(scroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

        scroll.setOnScrollChangeListener { _, _, _, _, _ -> onScrolled() }
        scroll.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> onScrolled() }
        if (Build.VERSION.SDK_INT >= 33) setupEdgeBlur()

        ViewCompat.setOnApplyWindowInsetsListener(root) { _, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            column.setPadding(bars.left, bars.top, bars.right, 0)
            scroll.setPadding(0, 0, 0, bars.bottom)
            insets
        }
        setContentView(root)
    }

    override fun onResume() {
        super.onResume()
        HighRefresh.apply(this)
        lastKey = ""
        handler.post(refresher)
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(refresher)
    }

    /** Görgetéskor a nagy cím halványul, a kis cím beúszik a felső sávba. */
    private fun onScrolled() {
        val y = scroll.scrollY.toFloat()
        val h = bigTitle.height.toFloat().coerceAtLeast(1f)
        val f = ((y - h * 0.35f) / (h * 0.55f)).coerceIn(0f, 1f)
        smallTitle.alpha = f
        smallTitle.translationY = (1f - f) * dpf(6f)
        bigTitle.alpha = 1f - f * 0.85f
        onScrollExtra?.invoke()
    }

    /** A görgethető rész teteje és alja üveg alá csúszva elmosódik (mint a beállításokban). */
    @androidx.annotation.RequiresApi(33)
    private fun setupEdgeBlur() {
        val eb = try {
            EdgeBlur(scroll)
        } catch (e: Throwable) {
            return
        }
        val ramp = dpf(28f)
        onScrollExtra = {
            try {
                val child = scroll.getChildAt(0)
                val below = (child?.height ?: 0) + scroll.paddingBottom - scroll.height - scroll.scrollY
                val top = if (scroll.canScrollVertically(-1)) (scroll.scrollY / ramp).coerceIn(0.15f, 1f) else 0f
                val bottom = if (scroll.canScrollVertically(1)) (below / ramp).coerceIn(0.15f, 1f) else 0f
                eb.update(top, bottom, dpf(34f), dpf(9f))
            } catch (e: Throwable) {
                eb.clear()
            }
        }
    }

    private fun load() {
        val dm = getSystemService(DownloadManager::class.java) ?: return
        val ids = Downloads.ids(this)
        val fresh = ArrayList<Item>()
        if (ids.isNotEmpty()) {
            var c: Cursor? = null
            try {
                c = dm.query(DownloadManager.Query().setFilterById(*ids))
                val byId = HashMap<Long, Item>()
                while (c.moveToNext()) {
                    val id = c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_ID))
                    byId[id] = Item(
                        id,
                        c.getString(c.getColumnIndexOrThrow(DownloadManager.COLUMN_TITLE)).orEmpty(),
                        c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS)),
                        c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)),
                        c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)),
                        c.getString(c.getColumnIndexOrThrow(DownloadManager.COLUMN_MEDIA_TYPE)),
                        c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_LAST_MODIFIED_TIMESTAMP))
                    )
                }
                ids.forEach { id -> byId[id]?.let { fresh.add(it) } }
            } catch (e: Exception) {
                // üres lista
            } finally {
                c?.close()
            }
        }
        // csak akkor rajzoljuk újra, ha változott valami (nem villog)
        val key = fresh.joinToString("|") { "${it.id}:${it.status}:${it.got}:${it.total}" }
        if (key == lastKey) return
        lastKey = key
        items.clear()
        items.addAll(fresh)
        render()
    }

    private fun isActive(item: Item) =
        item.status == DownloadManager.STATUS_RUNNING || item.status == DownloadManager.STATUS_PENDING ||
            item.status == DownloadManager.STATUS_PAUSED

    private fun render() {
        showEmpty(items.isEmpty())
        val n = items.size
        val active = items.count { isActive(it) }
        countText.visibility = if (n == 0) View.INVISIBLE else View.VISIBLE
        countText.text = if (L.en) {
            (if (n == 1) "1 file" else "$n files") + (if (active > 0) "  ·  $active in progress" else "")
        } else {
            "$n fájl" + (if (active > 0) "  ·  $active folyamatban" else "")
        }

        while (rows.size > n) {
            val r = rows.removeAt(rows.size - 1)
            r.card.animate().cancel()
            list.removeView(r.card)
        }
        val prev = HashSet(shownIds)
        shownIds.clear()
        var k = 0
        items.forEachIndexed { i, it ->
            val row = rows.getOrNull(i) ?: makeRow().also { r ->
                rows.add(r)
                val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                lp.bottomMargin = dp(10)
                list.addView(r.card, lp)
            }
            bind(row, it)
            shownIds.add(it.id)
            // csak az újonnan megjelenő letöltések úsznak be (lépcsőzetesen), a meglévők a helyükön maradnak
            if (it.id !in prev && !row.deleting) {
                enter(row.card, min(k, 10) * 45L + if (firstRender) 60L else 0L)
                k++
            }
        }
        firstRender = false
    }

    /** Beúszás: alulról, halványan, kicsit kisebbről a helyére (hardveres rétegen). */
    private fun enter(card: View, delay: Long) {
        card.animate().cancel()
        card.alpha = 0f
        card.translationY = dpf(22f)
        card.scaleX = 0.97f
        card.scaleY = 0.97f
        card.animate().alpha(1f).translationY(0f).scaleX(1f).scaleY(1f)
            .setStartDelay(delay).setDuration(480L).setInterpolator(ease)
            .setListener(null).withLayer().start()
    }

    private fun showEmpty(on: Boolean) {
        if (on == emptyShown && !firstRender) return
        emptyShown = on
        if (!on) {
            empty.animate().cancel()
            empty.visibility = View.GONE
            return
        }
        empty.visibility = View.VISIBLE
        empty.alpha = 0f
        empty.translationY = dpf(16f)
        empty.animate().alpha(1f).translationY(0f).setStartDelay(80L).setDuration(460L)
            .setInterpolator(ease).setListener(null).start()
        emptyBadge.scaleX = 0.8f
        emptyBadge.scaleY = 0.8f
        emptyBadge.animate().scaleX(1f).scaleY(1f).setStartDelay(80L).setDuration(620L)
            .setInterpolator(OvershootInterpolator(2.2f)).start()
    }

    /** Üveg felület: áttetsző kitöltés, finom perem, lenyomásra lekerekített fényfolt. */
    private fun glassDrawable(oval: Boolean, radius: Float = 0f): Drawable {
        val body = GradientDrawable()
        if (oval) body.shape = GradientDrawable.OVAL else body.cornerRadius = radius
        body.setColor(glassFill)
        body.setStroke(max(1, dp(1) * 3 / 4), glassRim)
        return android.graphics.drawable.RippleDrawable(ColorStateList.valueOf(pressTint), body, null)
    }

    private fun roundButton(icon: Int, label: String): ImageButton {
        val b = ImageButton(this)
        b.setImageResource(icon)
        b.imageTintList = ColorStateList.valueOf(textColor)
        b.background = android.graphics.drawable.RippleDrawable(
            ColorStateList.valueOf(pressTint),
            GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(if (night) 0x14FFFFFF else 0x0D000000)
            },
            null
        )
        b.setPadding(dp(9), dp(9), dp(9), dp(9))
        b.scaleType = ImageView.ScaleType.FIT_CENTER
        b.contentDescription = label
        return b
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun makeRow(): Row {
        val card = LinearLayout(this)
        card.orientation = LinearLayout.HORIZONTAL
        card.gravity = Gravity.CENTER_VERTICAL
        card.setPadding(dp(12), dp(12), dp(10), dp(12))
        card.background = glassDrawable(oval = false, radius = dpf(22f))
        // a megosztás gomb és a folyamatjelző eltűnése / megjelenése finoman animál
        card.layoutTransition = LayoutTransition()

        val icon = ImageView(this)
        val art = FileIconDrawable()
        icon.setImageDrawable(art)
        icon.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        card.addView(icon, LinearLayout.LayoutParams(dp(48), dp(48)))

        val texts = LinearLayout(this)
        texts.orientation = LinearLayout.VERTICAL
        texts.layoutTransition = LayoutTransition()
        val name = TextView(this)
        name.textSize = 15.5f
        name.isSingleLine = true
        name.ellipsize = TextUtils.TruncateAt.MIDDLE
        name.typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        name.setTextColor(textColor)
        texts.addView(name)
        val bar = ThinProgress(this, night)
        val blp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(5))
        blp.topMargin = dp(8)
        blp.bottomMargin = dp(1)
        texts.addView(bar, blp)
        val sub = TextView(this)
        sub.textSize = 13f
        sub.setTextColor(mutedColor)
        sub.isSingleLine = true
        sub.ellipsize = TextUtils.TruncateAt.END
        sub.setPadding(0, dp(4), 0, 0)
        texts.addView(sub)
        val tlp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        tlp.marginStart = dp(14)
        tlp.marginEnd = dp(6)
        card.addView(texts, tlp)

        val share = roundButton(R.drawable.ic_m_share, L.t("Megosztás"))
        val slp = LinearLayout.LayoutParams(dp(38), dp(38))
        slp.marginEnd = dp(6)
        card.addView(share, slp)
        val del = roundButton(R.drawable.ic_close, L.t("Törlés"))
        card.addView(del, LinearLayout.LayoutParams(dp(38), dp(38)))

        val row = Row(card, art, name, bar, sub, share, del)
        // lenyomásra a kártya finoman összenyomódik, elengedve rugózva visszaugrik
        card.setOnTouchListener { _, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> pressCard(row, true)
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> pressCard(row, false)
            }
            false
        }
        return row
    }

    private fun pressCard(row: Row, down: Boolean) {
        if (row.deleting) return
        row.press?.cancel()
        val s = if (down) 0.975f else 1f
        val a = ObjectAnimator.ofPropertyValuesHolder(
            row.card,
            PropertyValuesHolder.ofFloat(View.SCALE_X, s),
            PropertyValuesHolder.ofFloat(View.SCALE_Y, s)
        )
        if (down) {
            a.duration = 140L
            a.interpolator = PathInterpolator(0.3f, 0f, 0.2f, 1f)
        } else {
            a.duration = 460L
            a.interpolator = OvershootInterpolator(2.6f)
        }
        row.press = a
        a.start()
    }

    /** Fájltípus a kiterjesztés és a MIME-típus szerint. */
    private fun kind(item: Item): FileKind {
        val ext = item.title.substringAfterLast('.', "").lowercase()
        val m = item.mime.orEmpty()
        return when {
            ext == "apk" || m.contains("android.package") -> FileKind.APK
            m.startsWith("image/") || ext in setOf("jpg", "jpeg", "png", "gif", "webp", "heic", "avif", "bmp", "svg") -> FileKind.IMAGE
            m.startsWith("video/") || ext in setOf("mp4", "mkv", "webm", "mov", "avi", "3gp") -> FileKind.VIDEO
            m.startsWith("audio/") || ext in setOf("mp3", "m4a", "aac", "ogg", "opus", "flac", "wav") -> FileKind.AUDIO
            ext == "pdf" || m.contains("pdf") -> FileKind.PDF
            ext in setOf("zip", "rar", "7z", "gz", "tar", "xz", "bz2", "tgz") || m.contains("zip") ||
                m.contains("compressed") -> FileKind.ARCHIVE
            else -> FileKind.OTHER
        }
    }

    private fun bind(row: Row, item: Item) {
        row.art.kind = kind(item)
        row.name.text = item.title
        val size = { b: Long -> Formatter.formatShortFileSize(this, b) }
        val running = isActive(item)
        val bar = row.bar
        val wasShown = bar.visibility == View.VISIBLE
        val wantBar = if (running) View.VISIBLE else View.GONE
        if (bar.visibility != wantBar) bar.visibility = wantBar
        bar.indeterminate = running && item.total <= 0
        if (item.total > 0) bar.setProgress(item.got.toFloat() / item.total, animate = wasShown && running)
        val failed = item.status == DownloadManager.STATUS_FAILED
        row.sub.setTextColor(if (failed) 0xFFFF453A.toInt() else mutedColor)
        row.sub.text = when (item.status) {
            DownloadManager.STATUS_SUCCESSFUL -> {
                val when_ = android.text.format.DateUtils.getRelativeTimeSpanString(
                    item.time, System.currentTimeMillis(), android.text.format.DateUtils.MINUTE_IN_MILLIS
                )
                val ext = item.title.substringAfterLast('.', "").uppercase().take(5)
                listOf(ext, if (item.total > 0) size(item.total) else "", when_.toString())
                    .filter { it.isNotBlank() }.joinToString("  ·  ")
            }
            DownloadManager.STATUS_FAILED -> L.t("A letöltés nem sikerült")
            DownloadManager.STATUS_PAUSED -> L.t("Szüneteltetve")
            else -> if (item.total > 0) "${size(item.got)} / ${size(item.total)}  ·  ${item.got * 100 / item.total}%"
            else L.t("Letöltés…")
        }
        val wantShare = if (item.status == DownloadManager.STATUS_SUCCESSFUL) View.VISIBLE else View.GONE
        if (row.share.visibility != wantShare) row.share.visibility = wantShare
        row.card.setOnClickListener { open(item) }
        row.share.setOnClickListener { share(item) }
        row.del.setOnClickListener { delete(row, item) }
    }

    /**
     * Törlés: a kártya jobbra kicsúszva elhalványul, majd a helye simán összezárul (a többi
     * kártya nem ugrik), csak utána töröljük a letöltést és frissül a lista.
     */
    private fun delete(row: Row, item: Item) {
        if (row.deleting) return
        row.deleting = true
        row.press?.cancel()
        val card = row.card
        card.animate().cancel()
        card.animate().alpha(0f).translationX(card.width * 0.35f).scaleX(0.96f).scaleY(0.96f)
            .setStartDelay(0L).setDuration(220L).setInterpolator(PathInterpolator(0.4f, 0f, 0.7f, 1f))
            .setListener(null).withLayer()
            .withEndAction {
                val lp = card.layoutParams as LinearLayout.LayoutParams
                val h = card.height
                val m = lp.bottomMargin
                val a = ValueAnimator.ofFloat(1f, 0f)
                a.duration = 280L
                a.interpolator = ease
                a.addUpdateListener { v ->
                    val f = v.animatedValue as Float
                    lp.height = (h * f).toInt()
                    lp.bottomMargin = (m * f).toInt()
                    card.layoutParams = lp
                }
                a.addListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) {
                        getSystemService(DownloadManager::class.java)?.remove(item.id)
                        Downloads.forget(this@DownloadsActivity, item.id)
                        shownIds.remove(item.id)
                        lp.height = ViewGroup.LayoutParams.WRAP_CONTENT
                        lp.bottomMargin = m
                        card.layoutParams = lp
                        card.alpha = 1f
                        card.translationX = 0f
                        card.scaleX = 1f
                        card.scaleY = 1f
                        row.deleting = false
                        lastKey = ""
                        load()
                    }
                })
                a.start()
            }.start()
    }

    private fun open(item: Item) {
        if (item.status != DownloadManager.STATUS_SUCCESSFUL) return
        val dm = getSystemService(DownloadManager::class.java) ?: return
        val uri = dm.getUriForDownloadedFile(item.id) ?: return
        val type = item.mime ?: dm.getMimeTypeForDownloadedFile(item.id) ?: "*/*"
        try {
            startActivity(
                Intent(Intent.ACTION_VIEW).setDataAndType(uri, type)
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            )
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(this, L.t("Nincs alkalmazás a fájl megnyitásához"), Toast.LENGTH_SHORT).show()
        }
    }

    /** A kész fájl megosztása más alkalmazással (rendszer megosztó lap). */
    private fun share(item: Item) {
        if (item.status != DownloadManager.STATUS_SUCCESSFUL) return
        val dm = getSystemService(DownloadManager::class.java) ?: return
        val uri: Uri = dm.getUriForDownloadedFile(item.id) ?: return
        val type = item.mime ?: dm.getMimeTypeForDownloadedFile(item.id) ?: "*/*"
        val send = Intent(Intent.ACTION_SEND)
            .setType(type)
            .putExtra(Intent.EXTRA_STREAM, uri)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        try {
            startActivity(Intent.createChooser(send, L.t("Megosztás")))
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(this, L.t("Nincs alkalmazás a fájl megnyitásához"), Toast.LENGTH_SHORT).show()
        }
    }
}

/** A letöltött fájl típusa és az ikonlapka színe (iOS rendszerszínek). */
internal enum class FileKind(val color: Int) {
    IMAGE(0xFFFF9F0A.toInt()),
    VIDEO(0xFFFF375F.toInt()),
    AUDIO(0xFFBF5AF2.toInt()),
    PDF(0xFFFF453A.toInt()),
    ARCHIVE(0xFFA2845E.toInt()),
    APK(0xFF30D158.toInt()),
    OTHER(0xFF0A84FF.toInt())
}

/**
 * Fájltípus-ikon: színes, lekerekített lapka (felül világosabb, üveges fénnyel és finom
 * peremmel), rajta fehér, egyszerű jel: kép, film, hang, PDF, tömörített, APK vagy dokumentum.
 */
internal class FileIconDrawable : Drawable() {
    var kind = FileKind.OTHER
        set(v) {
            if (field != v) {
                field = v
                invalidateSelf()
            }
        }

    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = 0x59FFFFFF
    }
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Color.WHITE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.WHITE
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        typeface = Typeface.DEFAULT_BOLD
        textAlign = Paint.Align.CENTER
    }
    private val path = Path()
    private val r = RectF()

    private fun lighten(c: Int, f: Float): Int {
        val rr = Color.red(c) + ((255 - Color.red(c)) * f).toInt()
        val gg = Color.green(c) + ((255 - Color.green(c)) * f).toInt()
        val bb = Color.blue(c) + ((255 - Color.blue(c)) * f).toInt()
        return Color.argb(255, rr, gg, bb)
    }

    override fun draw(c: Canvas) {
        val b = bounds
        val s = min(b.width(), b.height()).toFloat()
        if (s <= 0f) return
        val ox = b.left + (b.width() - s) / 2f
        val oy = b.top + (b.height() - s) / 2f
        val corner = s * 0.28f

        // a lapka: felül világosabb színátmenet
        bgPaint.shader = LinearGradient(
            0f, oy, 0f, oy + s, lighten(kind.color, 0.30f), kind.color, Shader.TileMode.CLAMP
        )
        r.set(ox, oy, ox + s, oy + s)
        c.drawRoundRect(r, corner, corner, bgPaint)
        rimPaint.strokeWidth = max(1f, s * 0.02f)
        val half = rimPaint.strokeWidth / 2f
        r.inset(half, half)
        c.drawRoundRect(r, corner - half, corner - half, rimPaint)

        // a jel egy középre tett négyzetben (u, v: 0..1)
        val g = s * 0.52f
        val gx = ox + (s - g) / 2f
        val gy = oy + (s - g) / 2f
        fun x(u: Float) = gx + u * g
        fun y(v: Float) = gy + v * g
        stroke.strokeWidth = s * 0.06f

        fun document() {
            path.reset()
            path.moveTo(x(0.18f), y(0.04f))
            path.lineTo(x(0.60f), y(0.04f))
            path.lineTo(x(0.84f), y(0.28f))
            path.lineTo(x(0.84f), y(0.96f))
            path.lineTo(x(0.18f), y(0.96f))
            path.close()
            c.drawPath(path, stroke)
            path.reset()
            path.moveTo(x(0.60f), y(0.04f))
            path.lineTo(x(0.60f), y(0.28f))
            path.lineTo(x(0.84f), y(0.28f))
            c.drawPath(path, stroke)
        }

        when (kind) {
            FileKind.IMAGE -> {
                r.set(x(0.04f), y(0.14f), x(0.96f), y(0.86f))
                c.drawRoundRect(r, g * 0.14f, g * 0.14f, stroke)
                path.reset()
                path.moveTo(x(0.18f), y(0.72f))
                path.lineTo(x(0.40f), y(0.46f))
                path.lineTo(x(0.56f), y(0.62f))
                path.lineTo(x(0.68f), y(0.52f))
                path.lineTo(x(0.82f), y(0.72f))
                path.close()
                c.drawPath(path, fillPaint)
                c.drawCircle(x(0.68f), y(0.34f), g * 0.08f, fillPaint)
            }
            FileKind.VIDEO -> {
                r.set(x(0.04f), y(0.14f), x(0.96f), y(0.86f))
                c.drawRoundRect(r, g * 0.14f, g * 0.14f, stroke)
                path.reset()
                path.moveTo(x(0.40f), y(0.33f))
                path.lineTo(x(0.40f), y(0.67f))
                path.lineTo(x(0.69f), y(0.50f))
                path.close()
                c.drawPath(path, fillPaint)
            }
            FileKind.AUDIO -> {
                c.drawCircle(x(0.36f), y(0.76f), g * 0.15f, fillPaint)
                c.drawLine(x(0.49f), y(0.76f), x(0.49f), y(0.10f), stroke)
                path.reset()
                path.moveTo(x(0.49f), y(0.10f))
                path.cubicTo(x(0.62f), y(0.18f), x(0.80f), y(0.22f), x(0.78f), y(0.44f))
                c.drawPath(path, stroke)
            }
            FileKind.PDF -> {
                document()
                textPaint.textSize = g * 0.30f
                c.drawText("PDF", x(0.51f), y(0.78f), textPaint)
            }
            FileKind.ARCHIVE -> {
                r.set(x(0.10f), y(0.06f), x(0.90f), y(0.94f))
                c.drawRoundRect(r, g * 0.14f, g * 0.14f, stroke)
                val w0 = stroke.strokeWidth
                stroke.strokeWidth = w0 * 0.8f
                for (k in 0 until 4) {
                    val yy = y(0.16f + k * 0.11f)
                    if (k % 2 == 0) c.drawLine(x(0.50f), yy, x(0.60f), yy, stroke)
                    else c.drawLine(x(0.40f), yy, x(0.50f), yy, stroke)
                }
                r.set(x(0.40f), y(0.62f), x(0.60f), y(0.82f))
                c.drawRoundRect(r, g * 0.05f, g * 0.05f, stroke)
                stroke.strokeWidth = w0
            }
            FileKind.APK -> {
                // az Android robot feje: félkör, két szem, két antenna
                r.set(x(0.12f), y(0.37f), x(0.88f), y(1.13f))
                c.drawArc(r, 180f, 180f, true, fillPaint)
                val eye = kind.color
                fillPaint.color = eye
                c.drawCircle(x(0.36f), y(0.59f), g * 0.055f, fillPaint)
                c.drawCircle(x(0.64f), y(0.59f), g * 0.055f, fillPaint)
                fillPaint.color = Color.WHITE
                c.drawLine(x(0.30f), y(0.45f), x(0.20f), y(0.25f), stroke)
                c.drawLine(x(0.70f), y(0.45f), x(0.80f), y(0.25f), stroke)
            }
            FileKind.OTHER -> {
                document()
                c.drawLine(x(0.34f), y(0.56f), x(0.68f), y(0.56f), stroke)
                c.drawLine(x(0.34f), y(0.74f), x(0.68f), y(0.74f), stroke)
            }
        }
    }

    override fun setAlpha(alpha: Int) {}
    override fun setColorFilter(colorFilter: ColorFilter?) {}
    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}

/**
 * Vékony, lekerekített folyamatjelző: az érték simán úszik az újhoz (a frissítés nem ugrik),
 * ismeretlen méretnél egy fénycsík fut végig rajta.
 */
internal class ThinProgress(ctx: Context, night: Boolean) : View(ctx) {
    private val track = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = if (night) 0x24FFFFFF else 0x1A000000
    }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF0A84FF.toInt() }
    private val r = RectF()
    private var shown = 0f
    private var target = -1f
    private var anim: ValueAnimator? = null

    var indeterminate = false
        set(v) {
            if (field != v) {
                field = v
                invalidate()
            }
        }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        fill.shader = LinearGradient(
            0f, 0f, w.toFloat().coerceAtLeast(1f), 0f,
            0xFF0A84FF.toInt(), 0xFF5AC8FA.toInt(), Shader.TileMode.CLAMP
        )
    }

    fun setProgress(p: Float, animate: Boolean) {
        val v = p.coerceIn(0f, 1f)
        if (v == target) return
        target = v
        anim?.cancel()
        if (!animate || !isAttachedToWindow) {
            shown = v
            invalidate()
            return
        }
        val from = shown
        val a = ValueAnimator.ofFloat(0f, 1f)
        a.duration = 700L
        a.interpolator = PathInterpolator(0.25f, 0.1f, 0.25f, 1f)
        a.addUpdateListener {
            shown = from + (v - from) * it.animatedFraction
            invalidate()
        }
        anim = a
        a.start()
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return
        val rad = h / 2f
        r.set(0f, 0f, w, h)
        canvas.drawRoundRect(r, rad, rad, track)
        if (indeterminate) {
            val seg = w * 0.32f
            val ph = (SystemClock.uptimeMillis() % 1300L) / 1300f
            val x0 = -seg + ph * (w + seg)
            r.set(max(0f, x0), 0f, min(w, x0 + seg), h)
            if (r.width() > 0f) canvas.drawRoundRect(r, rad, rad, fill)
            if (isShown) postInvalidateOnAnimation()
        } else if (shown > 0f) {
            r.set(0f, 0f, max(h, w * shown), h)
            canvas.drawRoundRect(r, rad, rad, fill)
        }
    }

    override fun onDetachedFromWindow() {
        anim?.cancel()
        super.onDetachedFromWindow()
    }
}
