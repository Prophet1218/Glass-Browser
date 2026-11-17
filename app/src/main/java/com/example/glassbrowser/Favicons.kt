package com.example.glassbrowser

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Handler
import android.os.Looper
import android.util.LruCache
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

/**
 * A weboldalak színes ikonjai a kereső felület soraihoz. A Google ikonszolgáltatásától kéri le
 * őket (64 px), a memóriában tárolja, és egyszerre csak néhány kérés fut.
 */
object Favicons {
    private val cache = LruCache<String, Bitmap>(120)
    private val missing = HashSet<String>()
    private val waiting = HashMap<String, MutableList<(Bitmap?) -> Unit>>()
    private val pool = Executors.newFixedThreadPool(3)
    private val main = Handler(Looper.getMainLooper())

    fun cached(host: String): Bitmap? = cache.get(host)

    /** Az ikon betöltése; a visszahívás a fő szálon fut (null, ha nincs ikon). */
    fun load(host: String, cb: (Bitmap?) -> Unit) {
        if (host.isBlank()) {
            cb(null)
            return
        }
        cache.get(host)?.let {
            cb(it)
            return
        }
        if (host in missing) {
            cb(null)
            return
        }
        val list = waiting[host]
        if (list != null) {
            list.add(cb)
            return
        }
        waiting[host] = mutableListOf(cb)
        pool.execute {
            var bmp: Bitmap? = null
            try {
                val c = URL("https://www.google.com/s2/favicons?domain=$host&sz=64")
                    .openConnection() as HttpURLConnection
                c.connectTimeout = 5000
                c.readTimeout = 5000
                c.instanceFollowRedirects = true
                if (c.responseCode == 200) {
                    c.inputStream.use { bmp = BitmapFactory.decodeStream(it) }
                }
                c.disconnect()
            } catch (e: Exception) {
                // nincs ikon
            }
            main.post {
                val b = bmp
                if (b != null && b.width >= 8) cache.put(host, b) else missing.add(host)
                waiting.remove(host)?.forEach { it(cache.get(host)) }
            }
        }
    }
}
