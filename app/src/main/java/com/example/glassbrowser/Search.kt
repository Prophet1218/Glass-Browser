package com.example.glassbrowser

import android.content.Context
import android.os.Handler
import android.os.Looper
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale
import kotlin.concurrent.thread

/** Egy meglátogatott oldal. */
class HistoryEntry(val url: String, var title: String, var time: Long)

/**
 * Helyi böngészési előzmények (csak ezen a telefonon, egy fájlban). A keresősáv javaslataihoz
 * használjuk. Mindig a főszálról hívandó.
 */
object HistoryStore {
    private const val FILE = "history.json"
    private const val MAX = 600

    private val items = ArrayList<HistoryEntry>()   // a legfrissebb van elöl
    private var loaded = false
    private var dirty = false

    fun init(context: Context) {
        if (loaded) return
        loaded = true
        try {
            val file = File(context.applicationContext.filesDir, FILE)
            if (!file.exists()) return
            val arr = JSONArray(file.readText())
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val url = o.optString("u")
                if (url.isNotEmpty()) items.add(HistoryEntry(url, o.optString("t"), o.optLong("d")))
            }
        } catch (e: Exception) {
            // sérült fájl: üres előzményekkel indulunk
            items.clear()
        }
    }

    fun add(url: String?, title: String?) {
        if (url.isNullOrEmpty()) return
        if (!url.startsWith("http://") && !url.startsWith("https://")) return
        val idx = items.indexOfFirst { it.url == url }
        val entry = if (idx >= 0) items.removeAt(idx) else HistoryEntry(url, "", 0L)
        if (!title.isNullOrBlank()) entry.title = title
        entry.time = System.currentTimeMillis()
        items.add(0, entry)
        while (items.size > MAX) items.removeAt(items.size - 1)
        dirty = true
    }

    /** A meglátogatott webhelyek nevei (a legfrissebb elöl), a cím-kiegészítéshez. */
    fun hosts(): List<String> {
        val out = LinkedHashSet<String>()
        for (e in items) {
            val h = Urls.host(e.url)
            if (h.contains('.')) out.add(h)
        }
        return out.toList()
    }

    fun recent(limit: Int): List<HistoryEntry> = items.take(limit)

    /**
     * A leggyakrabban látogatott webhelyek (a kezdőlap csempéihez): webhelyenként a legutóbbi
     * oldal címe, a látogatások száma szerint rendezve.
     */
    fun topSites(limit: Int): List<HistoryEntry> {
        val counts = LinkedHashMap<String, Int>()
        val first = HashMap<String, HistoryEntry>()
        for (e in items) {
            val h = Urls.host(e.url)
            if (!h.contains('.')) continue
            counts[h] = (counts[h] ?: 0) + 1
            if (h !in first) first[h] = e
        }
        return counts.entries.sortedByDescending { it.value }.take(limit).mapNotNull { first[it.key] }
    }

    /** Az előzmények listája a kereső felülethez: üres szűrővel a legutóbbiak. */
    fun list(query: String, limit: Int): List<HistoryEntry> =
        if (query.isBlank()) items.take(limit) else search(query, limit)

    /** A címben vagy a webcímben keres; a legfrissebb találatok jönnek elöl. */
    fun search(query: String, limit: Int): List<HistoryEntry> {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return emptyList()
        val out = ArrayList<HistoryEntry>()
        for (e in items) {
            if (e.url.lowercase().contains(q) || e.title.lowercase().contains(q)) {
                out.add(e)
                if (out.size >= limit) break
            }
        }
        return out
    }

    fun save(context: Context) {
        if (!dirty) return
        dirty = false
        try {
            val arr = JSONArray()
            for (e in items) {
                arr.put(JSONObject().put("u", e.url).put("t", e.title).put("d", e.time))
            }
            File(context.applicationContext.filesDir, FILE).writeText(arr.toString())
        } catch (e: Exception) {
            // nem sikerült menteni: legközelebb újra megpróbáljuk
            dirty = true
        }
    }

    fun remove(context: Context, url: String) {
        if (items.removeAll { it.url == url }) {
            dirty = true
            save(context)
        }
    }

    fun clear(context: Context) {
        items.clear()
        dirty = false
        try {
            File(context.applicationContext.filesDir, FILE).delete()
        } catch (e: Exception) {
            // nincs teendő
        }
    }
}

/** Keresési javaslatok a keresőmotortól (gépelés közben). */
object Suggest {
    private val main = Handler(Looper.getMainLooper())

    /** A [done] a főszálon hívódik; hiba esetén üres listával. */
    fun fetch(query: String, engineUrl: String, done: (List<String>) -> Unit) {
        thread {
            val result: List<String> = try {
                val q = URLEncoder.encode(query, "UTF-8")
                val address = if (engineUrl.contains("duckduckgo")) {
                    "https://duckduckgo.com/ac/?type=list&q=$q"
                } else {
                    val lang = Locale.getDefault().language
                    "https://suggestqueries.google.com/complete/search?client=firefox&ie=UTF-8&oe=UTF-8&hl=$lang&q=$q"
                }
                val conn = URL(address).openConnection() as HttpURLConnection
                conn.connectTimeout = 4000
                conn.readTimeout = 4000
                conn.setRequestProperty("User-Agent", "Mozilla/5.0")
                val text = conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                conn.disconnect()
                // a válasz: ["lekérdezés", ["javaslat 1", "javaslat 2", ...]]
                val arr = JSONArray(text).optJSONArray(1)
                val out = ArrayList<String>()
                if (arr != null) {
                    for (i in 0 until minOf(arr.length(), 6)) {
                        val s = arr.optString(i)
                        if (s.isNotBlank()) out.add(s)
                    }
                }
                out
            } catch (e: Exception) {
                emptyList()
            }
            main.post { done(result) }
        }
    }
}

/** Egy könyvjelző. */
class Bookmark(val url: String, val title: String)

/** Könyvjelzők (csak ezen a telefonon, egy fájlban). Mindig a főszálról hívandó. */
object BookmarkStore {
    private const val FILE = "bookmarks.json"
    private val items = ArrayList<Bookmark>()   // a legújabb van elöl
    private var loaded = false

    fun init(context: Context) {
        if (loaded) return
        loaded = true
        try {
            val file = File(context.applicationContext.filesDir, FILE)
            if (!file.exists()) return
            val arr = JSONArray(file.readText())
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val url = o.optString("u")
                if (url.isNotEmpty()) items.add(Bookmark(url, o.optString("t")))
            }
        } catch (e: Exception) {
            items.clear()
        }
    }

    fun has(url: String): Boolean = items.any { it.url == url }

    fun list(query: String): List<Bookmark> {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return items.toList()
        return items.filter { it.url.lowercase().contains(q) || it.title.lowercase().contains(q) }
    }

    fun add(context: Context, url: String, title: String) {
        if (has(url)) return
        items.add(0, Bookmark(url, title))
        save(context)
    }

    fun remove(context: Context, url: String) {
        if (items.removeAll { it.url == url }) save(context)
    }

    private fun save(context: Context) {
        val arr = JSONArray()
        for (b in items) arr.put(JSONObject().put("u", b.url).put("t", b.title))
        val text = arr.toString()
        val file = File(context.applicationContext.filesDir, FILE)
        thread {
            try {
                file.writeText(text)
            } catch (e: Exception) {
                // a mentés legközelebb újra megpróbálható
            }
        }
    }
}

/**
 * A lapok bélyegképei lemezen: így akkor is megvannak, ha a rendszer közben leállította az appot
 * (különben visszatéréskor üres, szürke kártyák látszanának a lapváltóban).
 */
object ThumbStore {
    private fun dir(context: Context) = File(context.applicationContext.filesDir, "thumbs").apply { mkdirs() }
    private fun file(context: Context, id: Long) = File(dir(context), "$id.jpg")

    fun save(context: Context, id: Long, bitmap: android.graphics.Bitmap) {
        val target = file(context, id)
        thread {
            // háttérprioritás: a JPEG-tömörítés ne vegye el a CPU-t a lapváltó animációjától
            android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_BACKGROUND)
            try {
                // kicsinyítve mentünk: a kártyához ennyi bőven elég
                val w = 480
                val h = (bitmap.height * (w.toFloat() / bitmap.width)).toInt().coerceAtLeast(1)
                val small = if (bitmap.width > w) android.graphics.Bitmap.createScaledBitmap(bitmap, w, h, true) else bitmap
                target.outputStream().use { small.compress(android.graphics.Bitmap.CompressFormat.JPEG, 82, it) }
            } catch (e: Throwable) {
                // bélyegkép nélkül is működik
            }
        }
    }

    fun load(context: Context, id: Long): android.graphics.Bitmap? = try {
        val f = file(context, id)
        if (f.exists()) android.graphics.BitmapFactory.decodeFile(f.path) else null
    } catch (e: Throwable) {
        null
    }

    fun delete(context: Context, id: Long) {
        try {
            file(context, id).delete()
        } catch (e: Exception) {
            // nincs teendő
        }
    }

    /** A már nem létező lapok képeinek törlése. */
    fun prune(context: Context, keep: Set<Long>) {
        val d = dir(context)
        thread {
            try {
                d.listFiles()?.forEach { f ->
                    val id = f.name.removeSuffix(".jpg").toLongOrNull()
                    if (id == null || id !in keep) f.delete()
                }
            } catch (e: Exception) {
                // nincs teendő
            }
        }
    }
}
