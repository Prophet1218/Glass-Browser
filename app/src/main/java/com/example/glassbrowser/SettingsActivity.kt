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
import androidx.annotation.RequiresApi
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
import androidx.recyclerview.widget.RecyclerView

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
        applyDialogStyle(PreferenceManager.getDefaultSharedPreferences(this).getBoolean("glass_convex", false))
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
        onBackPressedDispatcher.addCallback(this, webBack)
        commit()
    }

    // ---------------------------------------------------------------- in-settings web page

    private var webPage: FrameLayout? = null
    private var webView: android.webkit.WebView? = null
    private val webBack = object : androidx.activity.OnBackPressedCallback(false) {
        override fun handleOnBackPressed() {
            val wv = webView
            if (wv != null && wv.canGoBack()) wv.goBack() else closeWebPage()
        }
    }

    /**
     * Google account pages (sign-in, passwords, activity) open here, inside the settings, over the
     * list, instead of in a new browser tab. They share the normal browsing cookies, so signing in
     * here signs in the browser too.
     */
    @android.annotation.SuppressLint("SetJavaScriptEnabled")
    fun openWebPage(url: String, title: String) {
        if (webPage != null) return
        val d = resources.displayMetrics.density
        val root = settingsRoot as FrameLayout
        val page = FrameLayout(this)
        page.setBackgroundColor(if (night) Color.rgb(14, 16, 22) else Color.WHITE)
        page.isClickable = true
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            setPadding((8 * d).toInt(), 0, (16 * d).toInt(), 0)
        }
        val back = android.widget.ImageButton(this).apply {
            setImageResource(R.drawable.ic_back)
            imageTintList = android.content.res.ColorStateList.valueOf(getColor(R.color.settings_text))
            background = null
            contentDescription = L.t("Vissza")
            setOnClickListener { closeWebPage() }
        }
        bar.addView(back, LinearLayout.LayoutParams((44 * d).toInt(), (44 * d).toInt()))
        val tv = TextView(this).apply {
            text = title
            setTextColor(getColor(R.color.settings_text))
            textSize = 20f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
        }
        bar.addView(tv, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
            marginStart = (6 * d).toInt()
        })
        val progress = View(this).apply { setBackgroundColor(Color.rgb(10, 132, 255)) }
        val progressHost = FrameLayout(this)
        progressHost.addView(progress, FrameLayout.LayoutParams(0, (2 * d).toInt()))
        val wv = android.webkit.WebView(this)
        wv.isVerticalScrollBarEnabled = false
        wv.isHorizontalScrollBarEnabled = false
        wv.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            setSupportMultipleWindows(false)
            val base = android.webkit.WebSettings.getDefaultUserAgent(this@SettingsActivity)
            userAgentString = base.replace("; wv", "").replace(Regex("Version/\\d+\\.\\d+\\s"), "")
        }
        if (Build.VERSION.SDK_INT >= 33) {
            wv.settings.setAlgorithmicDarkeningAllowed(
                PreferenceManager.getDefaultSharedPreferences(this).getBoolean("dark_web", true)
            )
        }
        CookieManager.getInstance().setAcceptThirdPartyCookies(wv, true)
        wv.webViewClient = object : android.webkit.WebViewClient() {
            override fun shouldOverrideUrlLoading(
                view: android.webkit.WebView?,
                request: android.webkit.WebResourceRequest?
            ): Boolean {
                val u = request?.url ?: return false
                return u.scheme != "http" && u.scheme != "https"
            }
        }
        wv.webChromeClient = object : android.webkit.WebChromeClient() {
            override fun onProgressChanged(view: android.webkit.WebView?, newProgress: Int) {
                val w = progressHost.width
                progress.layoutParams = progress.layoutParams.apply {
                    width = if (newProgress >= 100) 0 else (w * newProgress / 100f).toInt()
                }
                progress.requestLayout()
            }
        }
        HighRefresh.view(wv)
        col.addView(bar, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (56 * d).toInt()))
        col.addView(progressHost, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (2 * d).toInt()))
        col.addView(wv, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        page.addView(col, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(page) { v, ins ->
            val b = ins.getInsets(
                androidx.core.view.WindowInsetsCompat.Type.systemBars() or
                    androidx.core.view.WindowInsetsCompat.Type.ime()
            )
            v.setPadding(0, b.top, 0, b.bottom)
            ins
        }
        root.addView(page, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        page.requestApplyInsets()
        webPage = page
        webView = wv
        webBack.isEnabled = true
        // slides in from the right, like a settings sub-page
        page.translationX = root.width.toFloat().coerceAtLeast(1f)
        page.animate().translationX(0f).setDuration(300L)
            .setInterpolator(android.view.animation.PathInterpolator(0.2f, 0.9f, 0.25f, 1f))
            .start()
        wv.loadUrl(url)
    }

    fun closeWebPage() {
        val page = webPage ?: return
        val wv = webView
        webPage = null
        webView = null
        webBack.isEnabled = false
        CookieManager.getInstance().flush()
        page.animate().translationX(settingsRoot.width.toFloat()).setDuration(260L)
            .setInterpolator(android.view.animation.PathInterpolator(0.4f, 0f, 0.6f, 1f))
            .withEndAction {
                (settingsRoot as FrameLayout).removeView(page)
                wv?.destroy()
            }
            .start()
        (supportFragmentManager.findFragmentById(R.id.settingsContainer) as? SettingsFragment)?.updateGoogleStatus()
    }

    override fun onDestroy() {
        webView?.destroy()
        webView = null
        SliderGlass.host = null
        super.onDestroy()
    }

    fun commit() {
        setResult(RESULT_OK, result)
    }

    /** A felugró ablakok üveglapja domború (3D) vagy sima, a kapcsoló szerint. */
    fun applyDialogStyle(convex: Boolean) {
        theme.applyStyle(if (convex) R.style.GlassDialogs_Convex else R.style.GlassDialogs_Flat, true)
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
            // domború (3D) üveg a csúszka kapszuláján is (a beállítás azonnal érvényes)
            val convexOn = PreferenceManager.getDefaultSharedPreferences(this).getBoolean("glass_convex", false)
            l.setConvex(if (convexOn) 1f else 0f, if (convexOn) 1f else 0f)
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
        // A láthatóság-változásokat a lista tételenként kapja meg (DiffUtil), nem egy teljes
        // újrarajzolásként: így nyitáskor / záráskor a sorok valóban csúsznak, nem csak áttűnnek.
        preferenceManager.preferenceComparisonCallback = PreferenceManager.SimplePreferenceComparisonCallback()
        val act = requireActivity() as SettingsActivity
        L.prefs(preferenceScreen)
        setupSections()

        // nyelvváltás: a beállítások képernyő azonnal az új nyelven jelenik meg
        findPreference<SeekBarPreference>("anim_speed")?.setOnPreferenceChangeListener { _, v ->
            AnimSpeed.apply(requireContext(), v as Int)
            true
        }
        findPreference<ListPreference>("app_language")?.let {
            // show the effective language (phone language by default) without saving it
            it.isPersistent = false
            it.value = L.code(requireContext())
            it.isPersistent = true
        }
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

        // domború üveg: a beállítások saját felugró ablakai is azonnal követik
        findPreference<SwitchPreference>("glass_convex")?.setOnPreferenceChangeListener { _, v ->
            act.applyDialogStyle(v as? Boolean ?: true)
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
            findPreference<SwitchPreference>("glass_convex")?.isChecked = false
            act.applyDialogStyle(false)
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

    private val sections = ArrayList<SectionPreference>()

    /**
     * Every category becomes a collapsible section: its title is a row with a chevron, the items
     * show only when it is open (one section open at a time, so the list stays short).
     */
    private fun setupSections() {
        val screen = preferenceScreen
        for (i in 0 until screen.preferenceCount) {
            val cat = screen.getPreference(i) as? androidx.preference.PreferenceCategory ?: continue
            val head = SectionPreference(requireContext(), cat)
            head.title = cat.title
            val names = (0 until cat.preferenceCount).mapNotNull { cat.getPreference(it).title?.toString() }
                .filter { it.isNotBlank() }
            head.summary = names.take(3).joinToString(", ") + if (names.size > 3) ", …" else ""
            head.order = -1
            cat.title = null
            // a kategória üres sora csak térköz a kártyák között
            cat.layoutResource = R.layout.pref_section_gap
            cat.addPreference(head)
            head.setExpanded(false)
            head.onToggle = { opened -> onSectionToggled(opened) }
            sections.add(head)
        }
    }

    private var sectionAnimator: SectionItemAnimator? = null
    private var toggleToken = 0

    /**
     * Szakasz nyitása / zárása. Egyetlen, összefüggő animáció: a másik nyitott szakasz bezárul,
     * a megnyitott címsora ugyanabban a mozdulatban a lista tetejére csúszik, alatta lépcsőzetesen
     * megjelennek a sorai (nincs külön, utólagos görgetés, ami "összeveszne" az animációval).
     */
    private fun onSectionToggled(opened: SectionPreference) {
        val list = listView ?: return
        val anim = sectionAnimator ?: SectionItemAnimator(requireContext()).also { sectionAnimator = it }
        if (list.itemAnimator !== anim) list.itemAnimator = anim
        val token = ++toggleToken
        if (opened.expanded) {
            // the opened section stays exactly where it was on screen: it only opens downward
            // (closing another section above it must not pull it upward)
            val cb = list.adapter as? androidx.preference.PreferenceGroup.PreferencePositionCallback
            val lm = list.layoutManager as? androidx.recyclerview.widget.LinearLayoutManager
            val pos0 = cb?.getPreferenceAdapterPosition(opened) ?: -1
            val oldTop = if (pos0 >= 0) lm?.findViewByPosition(pos0)?.top else null
            for (s in sections) if (s !== opened && s.expanded) s.setExpanded(false)
            if (oldTop != null) {
                list.post {
                    if (token != toggleToken || !isAdded) return@post
                    val pos = cb?.getPreferenceAdapterPosition(opened) ?: -1
                    if (pos >= 0 && lm != null) {
                        val v = lm.findViewByPosition(pos)
                        if (v == null || v.top != oldTop) lm.scrollToPositionWithOffset(pos, oldTop)
                    }
                }
            }
        }
        releaseAnimatorLater(list, token, 900L)
    }

    /** Ha minden animáció lefutott, a lista animátora újra ki van kapcsolva (a kapcsolók miatt). */
    private fun releaseAnimatorLater(list: RecyclerView, token: Int, delay: Long) {
        list.postDelayed({
            val anim = sectionAnimator
            if (token == toggleToken && anim != null && list.itemAnimator === anim) {
                if (anim.isRunning) releaseAnimatorLater(list, token, 120L) else list.itemAnimator = null
            }
        }, delay)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        // Átkapcsoláskor a sor ne cserélődjön ki áttűnéssel: így a kapcsoló kapszulája ténylegesen
        // átcsúszik a másik oldalra.
        listView.itemAnimator = null
        // a szakaszok lekerekített üvegkártyákon, köztük térköz; a régi elválasztó vonalak nélkül
        setDivider(null)
        val night = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES
        val anim = sectionAnimator ?: SectionItemAnimator(requireContext()).also { sectionAnimator = it }
        listView.addItemDecoration(SectionCardDecoration(requireContext(), sections, anim, night))
        listView.clipToPadding = false
        listView.setPadding(
            listView.paddingLeft, listView.paddingTop, listView.paddingRight,
            listView.paddingBottom + (24 * resources.displayMetrics.density).toInt()
        )
        // the list fades out softly at the top and bottom edges (no hard cut / blur band)
        listView.isVerticalFadingEdgeEnabled = true
        listView.setFadingEdgeLength((44 * resources.displayMetrics.density).toInt())
    }

    /**
     * A lista felső és alsó szélén a tartalom elmosódik és elhalványul, de csak ha ott valóban van
     * még tartalom (felül: már legörgettél; alul: van még lejjebb). Az erősség a görgetéssel együtt,
     * fokozatosan nő, így nem ugrik be.
     */
    @RequiresApi(33)
    private fun setupEdgeBlur() {
        val list = listView ?: return
        val d = resources.displayMetrics.density
        val eb = try {
            EdgeBlur(list)
        } catch (e: Throwable) {
            return
        }
        val ramp = 28f * d
        fun refresh() {
            try {
                val off = list.computeVerticalScrollOffset()
                val below = list.computeVerticalScrollRange() - list.computeVerticalScrollExtent() - off
                val top = if (list.canScrollVertically(-1)) (off / ramp).coerceIn(0.15f, 1f) else 0f
                val bottom = if (list.canScrollVertically(1)) (below / ramp).coerceIn(0.15f, 1f) else 0f
                eb.update(top, bottom, 34f * d, 9f * d)
            } catch (e: Throwable) {
                eb.clear()
            }
        }
        list.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                refresh()
            }
        })
        list.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> refresh() }
        list.post { refresh() }
    }

    private fun openInBrowser(key: String, url: String) {
        findPreference<Preference>(key)?.setOnPreferenceClickListener { pref ->
            (requireActivity() as SettingsActivity).openWebPage(url, pref.title?.toString().orEmpty())
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
        activity?.let { HighRefresh.apply(it) }
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

    fun updateGoogleStatus() {
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
