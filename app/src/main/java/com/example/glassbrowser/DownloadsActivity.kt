package com.example.glassbrowser

import android.app.DownloadManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.text.format.Formatter
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.MimeTypeMap
import android.webkit.URLUtil
import android.widget.BaseAdapter
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.PreferenceManager

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
 * Letöltések képernyő a böngésző stílusában: nagy cím, kerek vissza gomb, kapszula alakú
 * "Mappa" gomb, a fájlok lekerekített kártyákon, típus szerinti színes ikonnal, folyamatjelzővel.
 * Koppintásra megnyílik a fájl, a × gombbal törölhető.
 */
class DownloadsActivity : AppCompatActivity() {

    private class Item(
        val id: Long, val title: String, val status: Int, val total: Long, val got: Long,
        val mime: String?, val time: Long
    )

    private val items = ArrayList<Item>()
    private lateinit var list: LinearLayout
    private lateinit var empty: LinearLayout
    private val handler = android.os.Handler(android.os.Looper.getMainLooper())
    private var night = false
    private var lastKey = ""
    private val refresher = object : Runnable {
        override fun run() {
            load()
            handler.postDelayed(this, 800L)
        }
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private val textColor get() = if (night) 0xFFEEF0F5.toInt() else 0xFF1D1F24.toInt()
    private val mutedColor get() = if (night) 0xFF9AA0AD.toInt() else 0xFF5F6470.toInt()
    private val cardColor get() = if (night) 0xFF1B1E26.toInt() else 0xFFFFFFFF.toInt()
    private val bgColor get() = if (night) 0xFF0E1016.toInt() else 0xFFF3F4F8.toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        ThemeHelper.apply(prefs)
        L.init(this)
        super.onCreate(savedInstanceState)
        supportActionBar?.hide()
        night = (resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
            android.content.res.Configuration.UI_MODE_NIGHT_YES
        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, false)
        androidx.core.view.WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = !night
            isAppearanceLightNavigationBars = !night
        }

        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.setBackgroundColor(bgColor)

        // fejléc: kerek vissza gomb, nagy cím, jobbra a "Mappa" kapszula
        val header = LinearLayout(this)
        header.orientation = LinearLayout.HORIZONTAL
        header.gravity = Gravity.CENTER_VERTICAL
        header.setPadding(dp(14), dp(10), dp(16), dp(6))
        val back = android.widget.ImageButton(this)
        back.setImageResource(R.drawable.ic_back)
        back.imageTintList = android.content.res.ColorStateList.valueOf(textColor)
        back.background = android.graphics.drawable.GradientDrawable().apply {
            shape = android.graphics.drawable.GradientDrawable.OVAL
            setColor(cardColor)
        }
        back.elevation = dp(2).toFloat()
        back.setOnClickListener { finish() }
        header.addView(back, LinearLayout.LayoutParams(dp(44), dp(44)))
        val title = TextView(this)
        title.text = L.t("Letöltések")
        title.textSize = 30f
        title.setTypeface(title.typeface, android.graphics.Typeface.BOLD)
        title.setTextColor(textColor)
        val tlp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        tlp.marginStart = dp(14)
        header.addView(title, tlp)
        val folder = TextView(this)
        folder.text = L.t("Mappa")
        folder.textSize = 14f
        folder.setTypeface(folder.typeface, android.graphics.Typeface.BOLD)
        folder.setTextColor(0xFF0A84FF.toInt())
        folder.setPadding(dp(16), dp(9), dp(16), dp(9))
        folder.background = android.graphics.drawable.GradientDrawable().apply {
            cornerRadius = dp(100).toFloat()
            setColor(if (night) 0x260A84FF else 0x1A0A84FF)
        }
        folder.setOnClickListener {
            try {
                startActivity(Intent(DownloadManager.ACTION_VIEW_DOWNLOADS))
            } catch (e: ActivityNotFoundException) {
                // nincs ilyen képernyő
            }
        }
        header.addView(folder)
        root.addView(header)

        val scroll = android.widget.ScrollView(this)
        scroll.isFillViewport = true
        scroll.clipToPadding = false
        val column = LinearLayout(this)
        column.orientation = LinearLayout.VERTICAL
        list = LinearLayout(this)
        list.orientation = LinearLayout.VERTICAL
        list.setPadding(dp(14), dp(8), dp(14), dp(24))
        column.addView(list)

        // üres állapot
        empty = LinearLayout(this)
        empty.orientation = LinearLayout.VERTICAL
        empty.gravity = Gravity.CENTER
        val eIcon = android.widget.ImageView(this)
        eIcon.setImageResource(R.drawable.ic_m_download)
        eIcon.imageTintList = android.content.res.ColorStateList.valueOf(mutedColor)
        eIcon.background = android.graphics.drawable.GradientDrawable().apply {
            shape = android.graphics.drawable.GradientDrawable.OVAL
            setColor(cardColor)
        }
        eIcon.setPadding(dp(22), dp(22), dp(22), dp(22))
        empty.addView(eIcon, LinearLayout.LayoutParams(dp(84), dp(84)))
        val eText = TextView(this)
        eText.text = L.t("Még nincs letöltés.")
        eText.textSize = 16f
        eText.setTextColor(mutedColor)
        eText.setPadding(0, dp(14), 0, 0)
        empty.addView(eText)
        column.addView(empty, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(360)))
        scroll.addView(column)
        root.addView(scroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
            val bars = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            v.setPadding(0, bars.top, 0, 0)
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

    private fun render() {
        empty.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
        while (list.childCount > items.size) list.removeViewAt(list.childCount - 1)
        items.forEachIndexed { i, it ->
            val row = (list.getChildAt(i) as? LinearLayout) ?: makeRow().also { r ->
                val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                lp.bottomMargin = dp(10)
                list.addView(r, lp)
                r.alpha = 0f
                r.translationY = dp(12).toFloat()
                r.animate().alpha(1f).translationY(0f).setStartDelay(i * 30L).setDuration(260).start()
            }
            bind(row, it)
        }
    }

    private fun makeRow(): LinearLayout {
        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.CENTER_VERTICAL
        row.setPadding(dp(14), dp(14), dp(6), dp(14))
        row.background = android.graphics.drawable.GradientDrawable().apply {
            cornerRadius = dp(22).toFloat()
            setColor(cardColor)
        }
        val icon = TextView(this)
        icon.gravity = Gravity.CENTER
        icon.textSize = 11f
        icon.setTypeface(icon.typeface, android.graphics.Typeface.BOLD)
        icon.setTextColor(android.graphics.Color.WHITE)
        row.addView(icon, LinearLayout.LayoutParams(dp(46), dp(46)))
        val texts = LinearLayout(this)
        texts.orientation = LinearLayout.VERTICAL
        val t = TextView(this)
        t.textSize = 15.5f
        t.maxLines = 2
        t.ellipsize = android.text.TextUtils.TruncateAt.MIDDLE
        t.setTextColor(textColor)
        texts.addView(t)
        val bar = android.widget.ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal)
        bar.max = 1000
        bar.progressTintList = android.content.res.ColorStateList.valueOf(0xFF0A84FF.toInt())
        bar.progressBackgroundTintList = android.content.res.ColorStateList.valueOf(mutedColor)
        val blp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(4))
        blp.topMargin = dp(8)
        texts.addView(bar, blp)
        val sub = TextView(this)
        sub.textSize = 13f
        sub.setTextColor(mutedColor)
        sub.setPadding(0, dp(4), 0, 0)
        texts.addView(sub)
        val lp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        lp.marginStart = dp(14)
        lp.marginEnd = dp(4)
        row.addView(texts, lp)
        val del = android.widget.ImageButton(this)
        del.setImageResource(R.drawable.ic_close)
        del.imageTintList = android.content.res.ColorStateList.valueOf(mutedColor)
        del.background = null
        del.setPadding(dp(12), dp(12), dp(12), dp(12))
        del.scaleType = android.widget.ImageView.ScaleType.FIT_CENTER
        row.addView(del, LinearLayout.LayoutParams(dp(42), dp(42)))
        return row
    }

    /** Fájltípus szerint: felirat és szín az ikonhoz. */
    private fun kind(item: Item): Pair<String, Int> {
        val ext = item.title.substringAfterLast('.', "").lowercase()
        val m = item.mime.orEmpty()
        return when {
            ext == "apk" || m.contains("android.package") -> "APK" to 0xFF34C759.toInt()
            m.startsWith("image/") -> "IMG" to 0xFFFF9F0A.toInt()
            m.startsWith("video/") -> "VID" to 0xFFFF375F.toInt()
            m.startsWith("audio/") -> "MP3" to 0xFFBF5AF2.toInt()
            ext == "pdf" || m.contains("pdf") -> "PDF" to 0xFFFF453A.toInt()
            ext in setOf("zip", "rar", "7z", "gz", "tar") -> "ZIP" to 0xFF8E8E93.toInt()
            else -> (ext.take(4).uppercase().ifEmpty { "FILE" }) to 0xFF0A84FF.toInt()
        }
    }

    private fun bind(row: LinearLayout, item: Item) {
        val icon = row.getChildAt(0) as TextView
        val (label, color) = kind(item)
        icon.text = label
        icon.background = android.graphics.drawable.GradientDrawable().apply {
            cornerRadius = dp(14).toFloat()
            setColor(color)
        }
        val texts = row.getChildAt(1) as LinearLayout
        (texts.getChildAt(0) as TextView).text = item.title
        val bar = texts.getChildAt(1) as android.widget.ProgressBar
        val sub = texts.getChildAt(2) as TextView
        val size = { b: Long -> Formatter.formatShortFileSize(this, b) }
        val running = item.status == DownloadManager.STATUS_RUNNING || item.status == DownloadManager.STATUS_PENDING ||
            item.status == DownloadManager.STATUS_PAUSED
        bar.visibility = if (running) View.VISIBLE else View.GONE
        bar.isIndeterminate = running && item.total <= 0
        if (item.total > 0) bar.progress = (item.got * 1000 / item.total).toInt()
        sub.text = when (item.status) {
            DownloadManager.STATUS_SUCCESSFUL -> {
                val when_ = android.text.format.DateUtils.getRelativeTimeSpanString(
                    item.time, System.currentTimeMillis(), android.text.format.DateUtils.MINUTE_IN_MILLIS
                )
                "${size(item.total)}  ·  $when_"
            }
            DownloadManager.STATUS_FAILED -> L.t("A letöltés nem sikerült")
            DownloadManager.STATUS_PAUSED -> L.t("Szüneteltetve")
            else -> if (item.total > 0) "${size(item.got)} / ${size(item.total)}  ·  ${item.got * 100 / item.total}%"
            else L.t("Letöltés…")
        }
        row.setOnClickListener { open(item) }
        (row.getChildAt(2) as android.widget.ImageButton).setOnClickListener {
            row.animate().alpha(0f).translationX(row.width.toFloat() * 0.3f).setDuration(200).withEndAction {
                getSystemService(DownloadManager::class.java)?.remove(item.id)
                Downloads.forget(this, item.id)
                row.alpha = 1f
                row.translationX = 0f
                lastKey = ""
                load()
            }.start()
        }
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
}
