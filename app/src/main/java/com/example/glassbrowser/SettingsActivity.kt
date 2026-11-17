package com.example.glassbrowser

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Outline
import android.graphics.RectF
import android.graphics.RenderEffect
import android.graphics.Shader
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.webkit.CookieManager
import android.webkit.WebStorage
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.view.WindowCompat
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceManager
import androidx.preference.SeekBarPreference
import androidx.preference.SwitchPreference

/**
 * A választható alkalmazásikonok. Mindegyikhez tartozik egy "álnév" (activity-alias) a
 * manifestben; az ikon váltása annyi, hogy a kiválasztott álnevet bekapcsoljuk, a többit ki.
 */
object AppIcons {
    const val DEFAULT = "frost_blue"

    val styles get() = listOf("flat" to L.t("Sima"), "frost" to L.t("Matt üveg"), "liquid" to L.t("Folyékony üveg"))
    val colors get() = listOf(
        "blue" to L.t("kék"), "purple" to L.t("lila"), "green" to L.t("zöld"),
        "orange" to L.t("narancs"), "pink" to L.t("rózsaszín"), "graphite" to L.t("grafit")
    )

    private fun aliasName(key: String): String =
        "com.example.glassbrowser.Icon" + key.split('_').joinToString("") { part ->
            part.replaceFirstChar { it.uppercase() }
        }

    fun label(key: String): String {
        val parts = key.split('_')
        val style = styles.firstOrNull { it.first == parts.getOrNull(0) }?.second ?: return key
        val color = colors.firstOrNull { it.first == parts.getOrNull(1) }?.second ?: return key
        return "$style, $color"
    }

    /** A kép erőforrása az ikonválasztóhoz. */
    fun artRes(context: Context, key: String): Int =
        context.resources.getIdentifier("icon_art_$key", "drawable", context.packageName)

    fun apply(context: Context, key: String): Boolean {
        return try {
            val pm = context.packageManager
            val pkg = context.packageName
            // előbb az újat kapcsoljuk be, hogy egy pillanatra se maradjon ikon nélkül az app
            pm.setComponentEnabledSetting(
                ComponentName(pkg, aliasName(key)),
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP
            )
            for ((style, _) in styles) {
                for ((color, _) in colors) {
                    val other = "${style}_$color"
                    if (other == key) continue
                    pm.setComponentEnabledSetting(
                        ComponentName(pkg, aliasName(other)),
                        PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP
                    )
                }
            }
            true
        } catch (e: Exception) {
            false
        }
    }
}

class SettingsActivity : AppCompatActivity() {

    /** Ebben adjuk át a főablaknak, mit kell tennie (új lap nyitása, gyorsítótár törlése). */
    val result = Intent()

    private lateinit var settingsRoot: View
    private var lens: LensEffect? = null
    private var lensFailed = false
    private var dialogBlur = false
    private var night = false
    private val rootLoc = IntArray(2)

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeHelper.apply(PreferenceManager.getDefaultSharedPreferences(this))
        L.init(this)
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        night = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = !night
            isAppearanceLightNavigationBars = !night
        }
        setContentView(R.layout.activity_settings)
        settingsRoot = findViewById(R.id.settingsRoot)
        L.tree(settingsRoot)
        findViewById<View>(R.id.settingsBack).setOnClickListener { finish() }
        // Üveghatás: a háttér a böngésző elmosott pillanatképe (a főablak készíti megnyitáskor)
        val bg = findViewById<ImageView>(R.id.settingsBg)
        backdrop?.let { bg.setImageBitmap(it) }
        if (Build.VERSION.SDK_INT >= 31) {
            bg.setRenderEffect(RenderEffect.createBlurEffect(40f, 40f, Shader.TileMode.CLAMP))
        }
        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.settingsContainer, SettingsFragment())
                .commit()
        }
        SliderGlass.host = { rect -> thumbGlass(rect) }
        commit()
    }

    override fun onDestroy() {
        SliderGlass.host = null
        super.onDestroy()
    }

    fun commit() {
        setResult(RESULT_OK, result)
    }

    /**
     * A csúszka lenyomott kapszulája: a helyén a böngésző keresősávjával azonos shader rajzol
     * üveget (fénytöréssel), amíg húzod. [rect] az ablak koordinátáiban; null = vége.
     */
    private fun thumbGlass(rect: RectF?): Boolean {
        if (Build.VERSION.SDK_INT < 33 || lensFailed) return false
        return try {
            if (rect == null) {
                if (!dialogBlur) lens?.clear()
                return true
            }
            if (dialogBlur) return false
            val d = resources.displayMetrics.density
            val l = lens ?: LensEffect(settingsRoot).also {
                it.setParams(13f * d, 15f * d, 0.12f)
                it.setTop(0f, 0f, 0f, 0, 0f, false)
                it.setRim(
                    if (night) Color.argb(200, 255, 255, 255) else Color.argb(150, 96, 104, 120),
                    1.4f * d
                )
                lens = it
            }
            settingsRoot.getLocationInWindow(rootLoc)
            l.setSlot(
                0,
                rect.left - rootLoc[0], rect.top - rootLoc[1],
                rect.right - rootLoc[0], rect.bottom - rootLoc[1],
                rect.height() / 2f, 0.5f * d
            )
            l.apply()
            true
        } catch (e: Throwable) {
            lensFailed = true
            false
        }
    }

    /** Felugró ablak megnyitásakor a mögötte lévő képernyő elmosódik, így az ablak üvegnek hat. */
    fun setDialogBlur(on: Boolean) {
        if (Build.VERSION.SDK_INT < 31) return
        dialogBlur = on
        if (on && Build.VERSION.SDK_INT >= 33) {
            try {
                lens?.clear()
            } catch (e: Throwable) {
                // nincs teendő
            }
        }
        settingsRoot.setRenderEffect(
            if (on) RenderEffect.createBlurEffect(28f, 28f, Shader.TileMode.CLAMP) else null
        )
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    companion object {
        const val EXTRA_OPEN_URL = "open_url"
        const val EXTRA_CLEAR_CACHE = "clear_cache"

        /** A böngésző kicsinyített képe, a beállítások üveghátteréhez. */
        var backdrop: Bitmap? = null
        const val GOOGLE_SIGN_IN_URL = "https://accounts.google.com/"
        const val GOOGLE_PASSWORDS_URL = "https://passwords.google.com/"
        const val GOOGLE_ACTIVITY_URL = "https://myactivity.google.com/"
    }
}

class SettingsFragment : PreferenceFragmentCompat() {

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.preferences, rootKey)
        val act = requireActivity() as SettingsActivity
        L.prefs(preferenceScreen)

        // nyelvváltás: a beállítások képernyő azonnal az új nyelven jelenik meg
        findPreference<ListPreference>("app_language")?.setOnPreferenceChangeListener { _, _ ->
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                L.init(act)
                act.recreate()
            }
            true
        }

        // rögzített keresősávnál nincs üveg: a típusa ilyenkor nem állítható
        fun updateGlassEnabled(style: String?) {
            val floating = style == null || style == "floating"
            findPreference<ListPreference>("glass_mode")?.let { g ->
                g.isEnabled = floating
                if (floating) {
                    g.summaryProvider = ListPreference.SimpleSummaryProvider.getInstance()
                } else {
                    g.summaryProvider = null
                    g.summary = L.t("Rögzített keresősávnál az üveg ki van kapcsolva.")
                }
            }
        }
        updateGlassEnabled(
            PreferenceManager.getDefaultSharedPreferences(requireContext()).getString("bar_style", "floating")
        )
        findPreference<ListPreference>("bar_style")?.setOnPreferenceChangeListener { _, v ->
            updateGlassEnabled(v as? String)
            true
        }

        findPreference<ListPreference>("app_theme")?.setOnPreferenceChangeListener { _, value ->
            // az új érték még nincs elmentve, ezért közvetlenül alkalmazzuk
            AppCompatDelegate.setDefaultNightMode(
                when (value as? String) {
                    "light" -> AppCompatDelegate.MODE_NIGHT_NO
                    "dark" -> AppCompatDelegate.MODE_NIGHT_YES
                    else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
                }
            )
            true
        }

        updateIconSummary()
        findPreference<Preference>("app_icon")?.setOnPreferenceClickListener {
            showIconChooser()
            true
        }

        findPreference<Preference>("glass_reset")?.setOnPreferenceClickListener {
            findPreference<ListPreference>("glass_mode")?.value = "liquid"
            findPreference<HuePreference>("bar_tint_hue")?.setHue(HuePreference.DEFAULT)
            findPreference<SeekBarPreference>("bar_tint_strength")?.value = 0
            findPreference<SwitchPreference>("top_blur")?.isChecked = true
            findPreference<ListPreference>("top_style")?.value = "flow"
            findPreference<SeekBarPreference>("top_blur_strength")?.value = 10
            findPreference<SeekBarPreference>("glass_blur")?.value = 2
            findPreference<SeekBarPreference>("glass_refraction_height")?.value = 30
            findPreference<SeekBarPreference>("glass_refraction_offset")?.value = 50
            findPreference<SeekBarPreference>("glass_dispersion")?.value = 0
            findPreference<SeekBarPreference>("glass_dark")?.value = 0
            findPreference<SwitchPreference>("glass_rim")?.isChecked = true
            Toast.makeText(requireContext(), L.t("Üvegbeállítások visszaállítva"), Toast.LENGTH_SHORT).show()
            true
        }

        // Felugró ablak megnyitásakor a mögötte lévő képernyő elmosódik, így az ablak üvegnek hat
        parentFragmentManager.registerFragmentLifecycleCallbacks(dialogWatcher, false)

        findPreference<Preference>("user_scripts")?.setOnPreferenceClickListener {
            startActivity(Intent(requireContext(), ScriptsActivity::class.java))
            true
        }

        updateAdblockSummary()
        findPreference<Preference>("adblock_update")?.setOnPreferenceClickListener { pref ->
            val url = PreferenceManager.getDefaultSharedPreferences(requireContext())
                .getString("adblock_url", "").orEmpty().trim()
            if (url.isEmpty()) {
                Toast.makeText(requireContext(), L.t("Nincs megadva szűrőlista-cím"), Toast.LENGTH_SHORT).show()
            } else {
                pref.summary = L.t("Letöltés…")
                val appContext = requireContext().applicationContext
                AdBlock.update(appContext, url) { ok, count ->
                    android.os.Handler(android.os.Looper.getMainLooper()).post {
                        Toast.makeText(
                            appContext,
                            if (ok) {
                                if (L.en) "Filter list updated: $count domains" else "Szűrőlista frissítve: $count domain"
                            } else {
                                L.t("A letöltés nem sikerült")
                            },
                            Toast.LENGTH_LONG
                        ).show()
                        if (isAdded) updateAdblockSummary()
                    }
                }
            }
            true
        }

        // ezek a pontok egy weboldalt nyitnak meg új lapon, a böngészőben
        openInBrowser("google_signin", SettingsActivity.GOOGLE_SIGN_IN_URL)
        openInBrowser("google_passwords", SettingsActivity.GOOGLE_PASSWORDS_URL)
        openInBrowser("google_activity", SettingsActivity.GOOGLE_ACTIVITY_URL)

        findPreference<Preference>("google_autofill")?.setOnPreferenceClickListener {
            // a telefon "automatikus kitöltés" beállítása: itt választható ki a Google
            val opened = try {
                startActivity(
                    Intent(
                        Settings.ACTION_REQUEST_SET_AUTOFILL_SERVICE,
                        Uri.parse("package:" + requireContext().packageName)
                    )
                )
                true
            } catch (e: Exception) {
                false
            }
            if (!opened) {
                try {
                    startActivity(Intent(Settings.ACTION_SETTINGS))
                } catch (e: Exception) {
                    // nincs elérhető beállítások képernyő
                }
                Toast.makeText(
                    requireContext(),
                    L.t("Keresd a telefon beállításaiban: Jelszavak és automatikus kitöltés"),
                    Toast.LENGTH_LONG
                ).show()
            }
            true
        }

        findPreference<Preference>("google_signout")?.setOnPreferenceClickListener {
            confirm(
                L.t("Kijelentkezés"),
                L.t("Ez az összes weboldalról kijelentkeztet, mert minden süti törlődik. Folytatod?")
            ) {
                clearCookies()
                updateGoogleStatus()
                Toast.makeText(requireContext(), L.t("Kijelentkeztél"), Toast.LENGTH_SHORT).show()
            }
            true
        }

        findPreference<Preference>("clear_data")?.setOnPreferenceClickListener {
            confirm(
                L.t("Böngészési adatok törlése"),
                L.t("A sütik, a gyorsítótár, a helyi tárolók és az előzmények törlődnek.")
            ) {
                clearCookies()
                WebStorage.getInstance().deleteAllData()
                HistoryStore.init(requireContext())
                HistoryStore.clear(requireContext())
                act.result.putExtra(SettingsActivity.EXTRA_CLEAR_CACHE, true)
                act.commit()
                updateGoogleStatus()
                Toast.makeText(requireContext(), L.t("Adatok törölve"), Toast.LENGTH_SHORT).show()
            }
            true
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        // Átkapcsoláskor a sor ne cserélődjön ki áttűnéssel: így a kapcsoló kapszulája ténylegesen
        // átcsúszik a másik oldalra.
        listView.itemAnimator = null
    }

    private fun openInBrowser(key: String, url: String) {
        findPreference<Preference>(key)?.setOnPreferenceClickListener {
            val act = requireActivity() as SettingsActivity
            act.result.putExtra(SettingsActivity.EXTRA_OPEN_URL, url)
            act.commit()
            act.finish()
            true
        }
    }

    // ---------------------------------------------------------------- alkalmazásikon

    private fun currentIcon(): String =
        PreferenceManager.getDefaultSharedPreferences(requireContext())
            .getString("app_icon", AppIcons.DEFAULT) ?: AppIcons.DEFAULT

    private fun updateIconSummary() {
        findPreference<Preference>("app_icon")?.summary = AppIcons.label(currentIcon())
    }

    /** Ikonválasztó: három stílus (sima, matt üveg, folyékony üveg), mindegyik hat színben. */
    private fun showIconChooser() {
        val ctx = requireContext()
        val d = resources.displayMetrics.density
        val current = currentIcon()
        var dialog: AlertDialog? = null

        val column = LinearLayout(ctx)
        column.orientation = LinearLayout.VERTICAL
        column.setPadding((18 * d).toInt(), (6 * d).toInt(), (18 * d).toInt(), (4 * d).toInt())

        for ((style, styleName) in AppIcons.styles) {
            val label = TextView(ctx)
            label.text = styleName
            label.textSize = 14f
            label.alpha = 0.7f
            label.setPadding((4 * d).toInt(), (12 * d).toInt(), 0, (6 * d).toInt())
            column.addView(label)

            val row = LinearLayout(ctx)
            row.orientation = LinearLayout.HORIZONTAL
            for ((color, _) in AppIcons.colors) {
                val key = "${style}_$color"

                // a kijelölt ikon körül kék keret
                val cell = FrameLayout(ctx)
                val pad = (3 * d).toInt()
                cell.setPadding(pad, pad, pad, pad)
                if (key == current) {
                    val ring = GradientDrawable()
                    ring.cornerRadius = 15 * d
                    ring.setColor(Color.TRANSPARENT)
                    ring.setStroke((2 * d).toInt(), 0xFF0A84FF.toInt())
                    cell.background = ring
                }

                // lekerekített "maszk", mint a kezdőképernyőn
                val mask = FrameLayout(ctx)
                mask.clipToOutline = true
                mask.outlineProvider = object : ViewOutlineProvider() {
                    override fun getOutline(view: View, outline: Outline) {
                        outline.setRoundRect(0, 0, view.width, view.height, view.width * 0.26f)
                    }
                }
                val img = ImageView(ctx)
                img.adjustViewBounds = true
                img.setImageResource(AppIcons.artRes(ctx, key))
                // az ikonképnek csak a közepe látszik a kezdőképernyőn (a széle tartalék)
                img.scaleX = 1.5f
                img.scaleY = 1.5f
                img.contentDescription = AppIcons.label(key)
                mask.addView(
                    img,
                    FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
                    )
                )
                cell.addView(
                    mask,
                    FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
                    )
                )
                cell.setOnClickListener {
                    dialog?.dismiss()
                    selectIcon(key)
                }
                row.addView(cell, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            }
            column.addView(row)
        }

        val act = activity as? SettingsActivity
        dialog = AlertDialog.Builder(ctx)
            .setTitle(L.t("Alkalmazásikon"))
            .setView(column)
            .setNegativeButton(L.t("Mégse"), null)
            .setOnDismissListener { act?.setDialogBlur(false) }
            .show()
        act?.setDialogBlur(true)
    }

    private fun selectIcon(key: String) {
        val ctx = requireContext()
        if (key == currentIcon()) return
        if (AppIcons.apply(ctx.applicationContext, key)) {
            PreferenceManager.getDefaultSharedPreferences(ctx).edit().putString("app_icon", key).apply()
            updateIconSummary()
            Toast.makeText(
                ctx, L.t("Ikon átállítva. A kezdőképernyőn néhány másodperc múlva frissül."), Toast.LENGTH_LONG
            ).show()
        } else {
            Toast.makeText(ctx, L.t("Az ikont nem sikerült átállítani"), Toast.LENGTH_SHORT).show()
        }
    }

    // ---------------------------------------------------------------- segédek

    private val dialogWatcher = object : FragmentManager.FragmentLifecycleCallbacks() {
        override fun onFragmentStarted(fm: FragmentManager, f: Fragment) {
            if (f is DialogFragment) setBackgroundBlur(true)
        }

        override fun onFragmentStopped(fm: FragmentManager, f: Fragment) {
            if (f is DialogFragment) setBackgroundBlur(false)
        }
    }

    private fun setBackgroundBlur(on: Boolean) {
        (activity as? SettingsActivity)?.setDialogBlur(on)
    }

    override fun onDestroy() {
        parentFragmentManager.unregisterFragmentLifecycleCallbacks(dialogWatcher)
        super.onDestroy()
    }

    override fun onResume() {
        super.onResume()
        updateGoogleStatus()
    }

    private fun updateAdblockSummary() {
        findPreference<Preference>("adblock_update")?.summary =
            if (L.en) "${AdBlock.count} domains on the list. Tap to download the full list."
            else "Jelenleg ${AdBlock.count} domain van a listán. Koppints a teljes lista letöltéséhez."
    }

    private fun clearCookies() {
        val cm = CookieManager.getInstance()
        cm.removeAllCookies(null)
        cm.flush()
    }

    private fun updateGoogleStatus() {
        val cookies = CookieManager.getInstance().getCookie("https://accounts.google.com").orEmpty()
        val signedIn = cookies.contains("SID=")
        findPreference<Preference>("google_status")?.summary =
            if (signedIn) L.t("Be vagy jelentkezve a Google-fiókodba.") else L.t("Nem vagy bejelentkezve.")
    }

    private fun confirm(title: String, message: String, action: () -> Unit) {
        AlertDialog.Builder(requireContext())
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton(L.t("Igen")) { _, _ -> action() }
            .setNegativeButton(L.t("Mégse"), null)
            .setOnDismissListener { setBackgroundBlur(false) }
            .show()
        setBackgroundBlur(true)
    }
}
