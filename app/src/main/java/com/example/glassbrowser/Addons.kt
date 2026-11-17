package com.example.glassbrowser

import android.content.Context
import android.content.SharedPreferences
import androidx.appcompat.app.AppCompatDelegate
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import kotlin.concurrent.thread

/** Világos / sötét téma az app beállítása szerint (alapból a telefont követi). */
object ThemeHelper {
    fun apply(prefs: SharedPreferences) {
        AppCompatDelegate.setDefaultNightMode(
            when (prefs.getString("app_theme", "system")) {
                "light" -> AppCompatDelegate.MODE_NIGHT_NO
                "dark" -> AppCompatDelegate.MODE_NIGHT_YES
                else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
            }
        )
    }
}

/**
 * Reklámblokkoló: domain-alapú szűrés. A lista hosts formátumú ("0.0.0.0 pelda.hu"),
 * sima domainlista vagy egyszerű "||pelda.hu^" szabályok lehetnek.
 */
object AdBlock {
    private const val FILE = "adblock_hosts.txt"

    @Volatile
    private var hosts: Set<String> = emptySet()

    @Volatile
    var enabled = true

    @Volatile
    private var loaded = false

    val count: Int get() = hosts.size

    fun init(context: Context) {
        if (loaded) return
        loaded = true
        val app = context.applicationContext
        thread {
            try {
                val file = File(app.filesDir, FILE)
                val reader = if (file.exists()) file.bufferedReader() else
                    BufferedReader(InputStreamReader(app.assets.open(FILE)))
                hosts = reader.use { parse(it) }
            } catch (e: Exception) {
                // lista nélkül egyszerűen nem blokkolunk
            }
        }
    }

    fun isBlocked(host: String?): Boolean {
        if (!enabled || host.isNullOrEmpty()) return false
        val set = hosts
        if (set.isEmpty()) return false
        var h = host.lowercase()
        while (true) {
            if (set.contains(h)) return true
            val dot = h.indexOf('.')
            if (dot < 0) return false
            h = h.substring(dot + 1)
            if (!h.contains('.')) return false // a legfelső szintet (pl. "com") nem vizsgáljuk
        }
    }

    /** Letölti és elmenti a listát. A [done] háttérszálon hívódik: (siker, domainek száma). */
    fun update(context: Context, url: String, done: (Boolean, Int) -> Unit) {
        val app = context.applicationContext
        thread {
            try {
                val conn = URL(url).openConnection() as HttpURLConnection
                conn.connectTimeout = 15000
                conn.readTimeout = 30000
                val tmp = File(app.filesDir, "$FILE.tmp")
                conn.inputStream.use { input -> tmp.outputStream().use { input.copyTo(it) } }
                val parsed = tmp.bufferedReader().use { parse(it) }
                if (parsed.isEmpty()) {
                    tmp.delete()
                    done(false, 0)
                } else {
                    val file = File(app.filesDir, FILE)
                    file.delete()
                    tmp.renameTo(file)
                    hosts = parsed
                    done(true, parsed.size)
                }
            } catch (e: Exception) {
                done(false, 0)
            }
        }
    }

    private fun parse(reader: BufferedReader): Set<String> {
        val set = HashSet<String>()
        reader.forEachLine { raw ->
            var line = raw.trim()
            if (line.isEmpty() || line.startsWith("#") || line.startsWith("!")) return@forEachLine
            val hash = line.indexOf('#')
            if (hash > 0) line = line.substring(0, hash).trim()
            var domain = if (line.startsWith("||")) {
                line.substring(2).substringBefore('^').substringBefore('/')
            } else {
                val parts = line.split(' ', '\t').filter { it.isNotEmpty() }
                if (parts.isEmpty()) return@forEachLine
                if (parts.size >= 2) parts[1] else parts[0]
            }
            domain = domain.lowercase()
            if (domain.contains('.') && !domain.contains('/') && !domain.contains('*') &&
                domain != "0.0.0.0" && domain != "127.0.0.1" && !domain.startsWith("localhost")
            ) {
                set.add(domain)
            }
        }
        return set
    }
}

/** Felhasználói szkript: a [match] szöveget tartalmazó című oldalakon fut le (üres = mindenhol). */
class UserScript(
    var name: String,
    var match: String,
    var code: String,
    var enabled: Boolean
)

object UserScripts {
    private const val KEY = "user_scripts"

    fun load(prefs: SharedPreferences): MutableList<UserScript> {
        val list = mutableListOf<UserScript>()
        try {
            val arr = JSONArray(prefs.getString(KEY, "[]"))
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                list.add(
                    UserScript(
                        o.optString("name"),
                        o.optString("match"),
                        o.optString("code"),
                        o.optBoolean("enabled", true)
                    )
                )
            }
        } catch (e: Exception) {
            // sérült mentés: üres lista
        }
        return list
    }

    fun save(prefs: SharedPreferences, list: List<UserScript>) {
        val arr = JSONArray()
        list.forEach {
            arr.put(
                JSONObject()
                    .put("name", it.name)
                    .put("match", it.match)
                    .put("code", it.code)
                    .put("enabled", it.enabled)
            )
        }
        prefs.edit().putString(KEY, arr.toString()).apply()
    }

    /** A szkriptet saját függvénybe csomagolja, hogy egy hiba ne állítsa meg a többit. */
    fun wrap(code: String): String =
        "(function(){try{\n$code\n}catch(e){console.error('GlassBrowser szkript hiba:',e);}})();"
}
