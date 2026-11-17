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

/** A böngészőből letöltött fájlok listája: koppintásra megnyílik, hosszan nyomva törölhető. */
class DownloadsActivity : AppCompatActivity() {

    private class Item(val id: Long, val title: String, val info: String, val done: Boolean, val mime: String?)

    private val items = ArrayList<Item>()
    private lateinit var adapter: BaseAdapter
    private lateinit var empty: TextView
    private val handler = android.os.Handler(android.os.Looper.getMainLooper())
    private val refresher = object : Runnable {
        override fun run() {
            load()
            handler.postDelayed(this, 1000L)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        ThemeHelper.apply(prefs)
        L.init(this)
        super.onCreate(savedInstanceState)
        title = L.t("Letöltések")
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        val pad = (16 * resources.displayMetrics.density).toInt()

        val column = LinearLayout(this)
        column.orientation = LinearLayout.VERTICAL
        val folder = Button(this)
        folder.text = L.t("Letöltések mappa megnyitása")
        folder.setOnClickListener {
            try {
                startActivity(Intent(DownloadManager.ACTION_VIEW_DOWNLOADS))
            } catch (e: ActivityNotFoundException) {
                // nincs ilyen képernyő a telefonon
            }
        }
        val flp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        flp.gravity = Gravity.START
        flp.setMargins(pad, pad / 2, pad, pad / 2)
        column.addView(folder, flp)

        empty = TextView(this)
        empty.text = L.t("Még nincs letöltés.")
        empty.setPadding(pad, pad, pad, pad)
        column.addView(empty)

        val list = ListView(this)
        adapter = object : BaseAdapter() {
            override fun getCount() = items.size
            override fun getItem(position: Int) = items[position]
            override fun getItemId(position: Int) = items[position].id
            override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
                val row = (convertView as? LinearLayout) ?: LinearLayout(this@DownloadsActivity).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(pad, pad * 3 / 4, pad, pad * 3 / 4)
                    addView(TextView(context).apply { textSize = 16f; maxLines = 2 })
                    addView(TextView(context).apply { textSize = 13f; alpha = 0.65f })
                }
                val it = items[position]
                (row.getChildAt(0) as TextView).text = it.title
                (row.getChildAt(1) as TextView).text = it.info
                return row
            }
        }
        list.adapter = adapter
        list.setOnItemClickListener { _, _, position, _ -> open(items[position]) }
        list.setOnItemLongClickListener { _, _, position, _ ->
            val it = items[position]
            AlertDialog.Builder(this)
                .setTitle(it.title)
                .setPositiveButton(L.t("Törlés")) { _, _ ->
                    getSystemService(DownloadManager::class.java)?.remove(it.id)
                    Downloads.forget(this, it.id)
                    load()
                }
                .setNegativeButton(L.t("Mégse"), null)
                .show()
            true
        }
        column.addView(list, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        setContentView(column)
    }

    override fun onResume() {
        super.onResume()
        handler.post(refresher)
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(refresher)
    }

    private fun load() {
        val dm = getSystemService(DownloadManager::class.java) ?: return
        val ids = Downloads.ids(this)
        items.clear()
        if (ids.isNotEmpty()) {
            var c: Cursor? = null
            try {
                c = dm.query(DownloadManager.Query().setFilterById(*ids))
                val byId = HashMap<Long, Item>()
                while (c.moveToNext()) {
                    val id = c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_ID))
                    val title = c.getString(c.getColumnIndexOrThrow(DownloadManager.COLUMN_TITLE)).orEmpty()
                    val status = c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
                    val total = c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
                    val got = c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
                    val mime = c.getString(c.getColumnIndexOrThrow(DownloadManager.COLUMN_MEDIA_TYPE))
                    val size = { b: Long -> Formatter.formatShortFileSize(this, b) }
                    val info = when (status) {
                        DownloadManager.STATUS_SUCCESSFUL -> size(total)
                        DownloadManager.STATUS_FAILED -> L.t("A letöltés nem sikerült")
                        DownloadManager.STATUS_PAUSED -> L.t("Szüneteltetve")
                        else -> if (total > 0) "${size(got)} / ${size(total)}  ·  ${got * 100 / total}%"
                        else L.t("Letöltés…")
                    }
                    byId[id] = Item(id, title, info, status == DownloadManager.STATUS_SUCCESSFUL, mime)
                }
                ids.forEach { id -> byId[id]?.let { items.add(it) } }
            } catch (e: Exception) {
                // marad az üres lista
            } finally {
                c?.close()
            }
        }
        empty.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
        adapter.notifyDataSetChanged()
    }

    private fun open(item: Item) {
        if (!item.done) return
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

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}
