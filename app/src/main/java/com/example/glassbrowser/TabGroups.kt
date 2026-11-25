package com.example.glassbrowser

import android.content.SharedPreferences
import org.json.JSONArray

/**
 * Egy lapcsoport élő nézete a normál lapok listáján: csak a [group] csoport lapjait mutatja, de
 * minden módosítás (hozzáadás, törlés) az alap listán történik. Így a MainActivity minden
 * meglévő kódja (indexOf, removeAt, add, a lapváltó adaptere) változatlanul működik vele.
 * Az ide hozzáadott lap automatikusan a csoportba kerül.
 */
class GroupTabList(private val base: MutableList<Tab>, val group: String) : AbstractMutableList<Tab>() {

    /** A nézet [index]-edik elemének helye az alap listában. */
    private fun baseIndex(index: Int): Int {
        var n = -1
        for (j in base.indices) {
            if (base[j].group == group) {
                n++
                if (n == index) return j
            }
        }
        throw IndexOutOfBoundsException("Index: $index, Size: ${n + 1}")
    }

    override val size: Int
        get() = base.count { it.group == group }

    override fun get(index: Int): Tab = base[baseIndex(index)]

    override fun set(index: Int, element: Tab): Tab {
        element.group = group
        return base.set(baseIndex(index), element)
    }

    override fun removeAt(index: Int): Tab = base.removeAt(baseIndex(index))

    override fun add(index: Int, element: Tab) {
        val s = size
        if (index < 0 || index > s) throw IndexOutOfBoundsException("Index: $index, Size: $s")
        element.group = group
        if (index == s) base.add(element) else base.add(baseIndex(index), element)
    }
}

/** A lapváltó tetején a csoportválasztó kapszula helye (dp): ennyivel lejjebb kezdődik a rács. */
const val GROUP_BAR_SPACE_DP = 0

/** A lapcsoportok nevei és a kiválasztott csoport (a beállítások között, JSON-ként). */
object TabGroups {
    private const val KEY_NAMES = "tab_groups"
    private const val KEY_SEL = "tab_group_sel"

    fun names(prefs: SharedPreferences): MutableList<String> {
        val out = ArrayList<String>()
        try {
            val arr = JSONArray(prefs.getString(KEY_NAMES, "[]") ?: "[]")
            for (i in 0 until arr.length()) {
                val n = arr.optString(i).trim()
                if (n.isNotEmpty() && n !in out) out.add(n)
            }
        } catch (e: Exception) {
            // sérült mentés: csoportok nélkül
        }
        return out
    }

    fun saveNames(prefs: SharedPreferences, names: List<String>) {
        val arr = JSONArray()
        names.forEach { arr.put(it) }
        prefs.edit().putString(KEY_NAMES, arr.toString()).apply()
    }

    /** Új csoport felvétele; a név levágott formáját adja vissza (üres név esetén ""). */
    fun add(prefs: SharedPreferences, name: String): String {
        val n = name.trim().take(40)
        if (n.isEmpty()) return ""
        val list = names(prefs)
        if (n !in list) {
            list.add(n)
            saveNames(prefs, list)
        }
        return n
    }

    fun remove(prefs: SharedPreferences, name: String) {
        val list = names(prefs)
        if (list.remove(name)) saveNames(prefs, list)
    }

    /** A kiválasztott csoport ("" = minden lap). */
    fun selected(prefs: SharedPreferences): String = prefs.getString(KEY_SEL, "") ?: ""

    fun setSelected(prefs: SharedPreferences, name: String) {
        prefs.edit().putString(KEY_SEL, name).apply()
    }
}
