package com.example.glassbrowser

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Intent
import android.content.SharedPreferences
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.RenderEffect
import android.graphics.Shader
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.text.Editable
import android.text.InputType
import android.text.TextUtils
import android.text.TextWatcher
import android.view.GestureDetector
import android.view.Gravity
import android.view.MotionEvent
import android.view.KeyEvent
import android.view.PixelCopy
import android.view.RoundedCorner
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.view.inputmethod.EditorInfo
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsAnimationCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.doOnLayout
import androidx.core.view.doOnPreDraw
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayInputStream

class MainActivity : AppCompatActivity() {

    // ---- nézetek
    private lateinit var root: FrameLayout
    private lateinit var scene: FrameLayout
    private lateinit var contentContainer: FrameLayout
    private lateinit var overview: FrameLayout
    private lateinit var overviewBg: ImageView
    private lateinit var tabList: RecyclerView
    private lateinit var hero: HeroView
    private lateinit var progress: ProgressBar
    private lateinit var barRow: LinearLayout
    /** A rögzített (Chrome-szerű) keresősáv háttere. */
    private lateinit var dockBg: View
    private lateinit var dockSkin: GlassSkinDrawable
    private val barSkins = ArrayList<GlassSkinDrawable>()
    /** "floating" (lebegő kapszulák), "bottom" vagy "top" (rögzített sáv alul / felül). */
    private var barStyle = "floating"
    private val docked: Boolean get() = barStyle != "floating"
    private val dockTop: Boolean get() = barStyle == "top"
    /** A rendszer navigációs sávjának magassága és az utolsó alsó betét (billentyűzettel). */
    private var navBottom = 0
    private var lastBottom = 0
    /** Igaz, amíg az üveg űrlap záródik (a tartalma ilyenkor gyorsabban tűnik el). */
    private var formClosing = false
    private lateinit var barLeft: FrameLayout
    private lateinit var barCenter: FrameLayout
    private lateinit var barRight: FrameLayout
    private lateinit var browseRow: LinearLayout
    private lateinit var iconBack: ImageView
    private lateinit var iconAdd: ImageView
    private lateinit var iconDone: ImageView
    private lateinit var doneFill: View
    private lateinit var overviewLabel: TextView
    private lateinit var urlField: EditText
    private lateinit var btnMenu: ImageButton
    private lateinit var btnReload: ImageButton
    private lateinit var tabCount: TextView
    private lateinit var searchPage: FrameLayout
    private lateinit var searchColumn: LinearLayout
    private lateinit var segBar: FrameLayout
    private lateinit var segCapsule: View
    private lateinit var searchList: LinearLayout
    private lateinit var segLabels: List<TextView>
    private var searchTab = 0
    private var searchShown = false
    private var searchBlur = 0f
    private var pullSkin: GlassSkinDrawable? = null
    private var pullArmed = false
    private lateinit var pullIndicator: FrameLayout
    private lateinit var pullIcon: ImageView
    private var searchBlurAnim: ValueAnimator? = null
    private var segAnim: ValueAnimator? = null
    private var searchKeys: List<String> = emptyList()
    private var onlineItems: List<String> = emptyList()
    private lateinit var menuScrim: View
    private lateinit var menuPanel: FrameLayout
    private lateinit var menuItems: LinearLayout

    // ---- üveg
    private val skins = ArrayList<GlassSkinDrawable>()
    private var lens: LensEffect? = null
    private var lensFailed = false
    private var lensActive = false
    private var lensBlur = 0f
    private var panelBlur = 0f
    private var topBlurOn = true
    private var topStyle = "band"
    private var topStrength = 0f
    private var topScroll = 0f
    private var flowAmt = 0f
    private var topVis = 1f
    private var topVisAnim: ValueAnimator? = null
    private var flowTarget = 0f
    private var flowAnim: ValueAnimator? = null
    private var glassShade = 0f
    private var bounceOn = true
    private var bounceStrength = 0.6f
    private var hapticOn = true
    private var hapticStrength = 0.5f
    private var capturing = false
    private var snapshotFull = false
    private val tmpRect = RectF()
    private lateinit var menuClip: MorphClip
    private var menuAnim: ValueAnimator? = null
    /** A menü / üveg űrlap üvegének erőssége (az űrlap bezárásakor teljesen elhalványul). */
    private var menuGlassAlpha = 1f

    // ---- állapot
    private lateinit var prefs: SharedPreferences
    private lateinit var tabAdapter: TabAdapter
    private val tabs = mutableListOf<Tab>()
    private var activeTab: Tab? = null
    private val handler = Handler(Looper.getMainLooper())
    private var statusTop = 0
    private var imeAnimating = false
    private var screenRadius = 0f
    private var bgColor = Color.WHITE
    private var night = false
    private var collapsed = false
    private var barOverview = false
    private var darkContent = true
    private var contentColor: Int? = null
    private var contentColorAnim: ValueAnimator? = null
    private var appliedDesktop: Boolean? = null
    private var userScripts: List<UserScript> = emptyList()
    private var probeBusy = false
    private val probeBitmap: Bitmap = Bitmap.createBitmap(48, 6, Bitmap.Config.ARGB_8888)
    private val probePixels = IntArray(48 * 6)
    private var topProbeBusy = false
    private val topProbeBitmap: Bitmap = Bitmap.createBitmap(8, 1, Bitmap.Config.ARGB_8888)
    private val topProbePixels = IntArray(8)

    // ---- lapváltó
    private var overviewShown = false
    private var overviewBusy = false
    private var overviewAnim: ValueAnimator? = null
    private var heroTab: Tab? = null
    private var ringAlpha = 1f
    private var titleAlpha = 1f
    private var titleAnim: ValueAnimator? = null
    private var ringAnim: ValueAnimator? = null

    // ---- keresési javaslatok
    private var settingText = false
    private var lastTyped = ""
    private var suggestSeq = 0
    private val suggestRunnable = Runnable { updateSuggestions(typedText()) }

    /** A kereső felület egy sora. [kind]: 0 = címsor, 1 = keresési javaslat, 2 = weboldal (ikonnal). */
    private class SuggestItem(
        val text: String,
        val sub: String?,
        val url: String?,
        val icon: Int,
        val kind: Int = 2,
        val tab: Tab? = null
    )

    private val activeWebView: GlassWebView? get() = activeTab?.webView
    private val currentUrl: String get() = activeTab?.url ?: START_URL
    private val menuVisible: Boolean get() = menuPanel.visibility == View.VISIBLE

    private val settingsLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            handleSettingsResult(it.data)
        }

    private val ticker = object : Runnable {
        override fun run() {
            if (!menuVisible && !overviewBusy) sampleLuminance()
            if (!overviewShown && !overviewBusy && !searchShown) sampleTopColor()
            handler.postDelayed(this, 500L)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        prefs = PreferenceManager.getDefaultSharedPreferences(this)
        ThemeHelper.apply(prefs)
        // az "egyszerű üveg" megszűnt: helyette folyékony üveg
        if (prefs.getString("glass_mode", "liquid") == "simple") {
            prefs.edit().putString("glass_mode", "liquid").apply()
        }
        L.init(this)
        super.onCreate(savedInstanceState)
        AdBlock.init(this)
        HistoryStore.init(this)
        BookmarkStore.init(this)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        if (Build.VERSION.SDK_INT >= 29) window.isNavigationBarContrastEnforced = false
        setContentView(R.layout.activity_main)
        preferHighRefreshRate()

        night = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES
        bgColor = ContextCompat.getColor(this, R.color.app_bg)
        screenRadius = 32f * resources.displayMetrics.density

        root = findViewById(R.id.root)
        scene = findViewById(R.id.scene)
        contentContainer = findViewById(R.id.contentContainer)
        overview = findViewById(R.id.overview)
        overviewBg = findViewById(R.id.overviewBg)
        tabList = findViewById(R.id.tabList)
        hero = findViewById(R.id.hero)
        progress = findViewById(R.id.progress)
        barRow = findViewById(R.id.barRow)
        dockBg = findViewById(R.id.dockBg)
        barLeft = findViewById(R.id.barLeft)
        barCenter = findViewById(R.id.barCenter)
        barRight = findViewById(R.id.barRight)
        browseRow = findViewById(R.id.browseRow)
        iconBack = findViewById(R.id.iconBack)
        iconAdd = findViewById(R.id.iconAdd)
        iconDone = findViewById(R.id.iconDone)
        doneFill = findViewById(R.id.doneFill)
        overviewLabel = findViewById(R.id.overviewLabel)
        urlField = findViewById(R.id.urlField)
        btnMenu = findViewById(R.id.btnMenu)
        btnReload = findViewById(R.id.btnReload)
        tabCount = findViewById(R.id.tabCount)
        pullIndicator = findViewById(R.id.pullIndicator)
        pullIcon = findViewById(R.id.pullIcon)
        searchPage = findViewById(R.id.searchPage)
        searchColumn = findViewById(R.id.searchColumn)
        segBar = findViewById(R.id.segBar)
        segCapsule = findViewById(R.id.segCapsule)
        searchList = findViewById(R.id.searchList)
        segLabels = listOf(findViewById(R.id.seg0), findViewById(R.id.seg1), findViewById(R.id.seg2))
        menuScrim = findViewById(R.id.menuScrim)
        menuPanel = findViewById(R.id.menuPanel)
        menuItems = findViewById(R.id.menuItems)
        menuClip = MorphClip(menuPanel)
        menuScrim.setOnClickListener { hideMenu() }

        // a lapváltó háttere: az oldal elmosott képe
        if (Build.VERSION.SDK_INT >= 31) {
            overviewBg.setRenderEffect(RenderEffect.createBlurEffect(70f, 70f, Shader.TileMode.CLAMP))
        }

        WindowCompat.getInsetsController(window, root).apply {
            // az állapotsor ikonjai a világos / sötét módhoz igazodnak
            isAppearanceLightStatusBars = !night
            isAppearanceLightNavigationBars = !night
        }

        setupGlass()
        setupControls()
        setupOverview()
        setupInsets()
        setupDockMenuButton()
        L.tree(root)

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                val wv = activeWebView
                when {
                    menuVisible -> hideMenu()
                    overviewShown -> hideOverview()
                    urlField.hasFocus() -> releaseFocus()
                    wv != null && wv.canGoBack() -> wv.goBack()
                    else -> {
                        isEnabled = false
                        onBackPressedDispatcher.onBackPressed()
                    }
                }
            }
        })

        restoreTabs()
        intent?.dataString?.let { openNewTab(it, capture = false) }
        applyAllSettings()
    }

    // ---------------------------------------------------------------- üveg

    private fun setupGlass() {
        val d = resources.displayMetrics.density
        // A keresősáv három része, a menü és a javaslatok ugyanazt a "bőrt" kapják: egyforma szín.
        barSkins.add(addSkin(barLeft, null))
        barSkins.add(addSkin(barCenter, null))
        barSkins.add(addSkin(barRight, null))
        // a rögzített sáv háttere: szögletes, perem nélkül (a képernyő széléig ér)
        dockSkin = GlassSkinDrawable(d)
        dockSkin.cornerRadius = 0f
        dockBg.background = dockSkin
        addSkin(menuItems, 26f * d)
        setupSearchPage()

        barRow.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> updateLens() }
        applyContentColor(!night)
    }

    private fun addSkin(view: View, radius: Float?): GlassSkinDrawable {
        val skin = GlassSkinDrawable(resources.displayMetrics.density)
        skin.cornerRadius = radius
        view.background = skin
        skins.add(skin)
        return skin
    }

    /**
     * Az üveg típusa a beállítás szerint (mindkettő valós idejű shader, Android 13+):
     *  liquid – folyékony üveg: enyhe elmosás, a széleken fénytörés és színszóródás
     *  frost  – matt üveg: erős elmosás, fénytörés nélkül
     *  simple – egyszerű áttetsző sáv (régebbi Androidon is ez marad)
     */
    private fun applyGlassMode() {
        val d = resources.displayMetrics.density
        barStyle = when (prefs.getString("bar_style", "floating")) {
            "bottom" -> "bottom"
            "top" -> "top"
            else -> "floating"
        }
        // Rögzített (Chrome-szerű) sávnál nincs üveg: tömör felületek, mint a Chrome-ban.
        val mode = if (docked) "off" else when (prefs.getString("glass_mode", "liquid")) {
            "frost" -> "frost"
            "off" -> "off"
            else -> "liquid"
        }
        val blur = prefs.getInt("glass_blur", 2) * d
        val refH = prefs.getInt("glass_refraction_height", 30) * d
        val refO = prefs.getInt("glass_refraction_offset", 50) * d
        val disp = prefs.getInt("glass_dispersion", 0) / 100f
        val frost = mode == "frost"
        lensBlur = if (frost) maxOf(blur * 2.4f, 16f * d) else blur
        // a menü és a javaslatok ugyanazt az üveget kapják, mint a keresősáv (együtt állíthatók)
        panelBlur = lensBlur
        // felül rögzített sávnál az állapotsor mögé is a sáv kerül: ott nincs külön felső elmosás
        // rögzített sávnál az állapotsor mögött fix világos / sötét háttér van (a telefon módja szerint)
        topBlurOn = prefs.getBoolean("top_blur", true) && !docked
        // csak a "band" (színsáv) és a "flow" (átúszó) stílus maradt
        topStyle = if (prefs.getString("top_style", "flow") == "band") "band" else "flow"
        topStrength = prefs.getInt("top_blur_strength", 10) * d

        lensActive = false
        if (mode != "off" && Build.VERSION.SDK_INT >= 33 && !lensFailed) {
            try {
                val l = lens ?: LensEffect(scene).also { lens = it }
                if (frost) {
                    l.setParams(refH, 0f, 0f)
                } else {
                    l.setParams(refH, refO * 0.6f, disp * 0.6f)
                }
                l.setRim(0, 1f)
                lensActive = true
            } catch (e: Throwable) {
                lensFailed = true
            }
        }
        if (!lensActive && Build.VERSION.SDK_INT >= 33) {
            try {
                lens?.clear()
            } catch (e: Throwable) {
                // nincs teendő
            }
        }

        // az összes üvegfelület színe egyszerre (a beállításokban választott színnel együtt)
        val strength = prefs.getInt("bar_tint_strength", 0)
        val hue = prefs.getInt("bar_tint_hue", 210)
        val overlay = ColorUtils.setAlphaComponent(
            HuePreference.colorOf(hue), (strength * 2.55f).toInt().coerceIn(0, 255)
        )
        val tintAlpha = when {
            !lensActive -> 1f
            frost -> 0.9f
            else -> 0.45f
        }
        // elmosás nélkül (egyszerű üveg) tömörebb lap kell, különben a szöveg olvashatatlan lenne
        // kikapcsolt üvegnél teljesen tömör felületek
        val plateAlpha = 255
        val plate: Int? = when {
            lensActive -> null
            night -> Color.argb(plateAlpha, 27, 30, 38)
            else -> Color.argb(plateAlpha, 248, 249, 251)
        }
        val rimOn = prefs.getBoolean("glass_rim", true) && mode != "off"
        bounceOn = prefs.getBoolean("bounce", true)
        bounceStrength = prefs.getInt("bounce_strength", 60) / 100f
        hapticOn = prefs.getBoolean("haptic", true)
        hapticStrength = prefs.getInt("haptic_strength", 50) / 100f
        // kikapcsolt felső elmosásnál az állapotsor mögött egyszínű (világos / sötét) háttér van
        if (!topBlurOn || docked) contentContainer.setBackgroundColor(bgColor)
        applyStatusColor()
        // sötétítés: fekete fátyol minden üvegfelületen
        glassShade = prefs.getInt("glass_dark", 0).coerceIn(0, 100) / 100f
        skins.forEach {
            it.setTint(tintAlpha, plate)
            it.setOverlay(overlay)
            it.setShade((glassShade * 255f).toInt())
            it.setRim(rimOn)
        }
        dockSkin.setTint(tintAlpha, plate)
        dockSkin.setOverlay(overlay)
        dockSkin.setShade((glassShade * 255f).toInt())
        dockSkin.setRim(false)
        applyBarStyle()
        if (!lensActive) applyContentColor(!night)

        tabs.forEach { applyWebTop(it.webView) }
        updateLens()
        sampleLuminance()
    }

    /** Igaz, ha az oldal valóban átúszik az állapotsor mögött (az "átúszó" felső elmosás). */
    private fun flowLayout(): Boolean = lensActive && topBlurOn && topStyle == "flow" && !dockTop

    /** Alul rögzített sáv: mennyire úszott le görgetés közben (0 = teljesen látszik). */
    private var dockOffset = 0f
    private var dockAnim: ValueAnimator? = null
    private val dockSnap = Runnable { snapDock() }

    /**
     * Firefox-szerű viselkedés: lefelé görgetve a sáv az ujjal együtt lecsúszik a képernyőről,
     * felfelé görgetve visszajön. Ha félúton áll meg a görgetés, a közelebbi állapotba ugrik.
     */
    private fun onDockScroll(scrollY: Int, dy: Int) {
        if (urlField.hasFocus() || menuVisible || overviewShown || overviewBusy) return
        dockAnim?.cancel()
        if (scrollY <= 4) {
            setDockOffset(0f)
        } else {
            setDockOffset(dockOffset + dy)
        }
        handler.removeCallbacks(dockSnap)
        handler.postDelayed(dockSnap, 140L)
    }

    private fun dockTravel(): Float = (dockHeight() + lastBottom).toFloat()

    private fun setDockOffset(v: Float) {
        dockOffset = v.coerceIn(0f, dockTravel())
        applyDockOffset()
    }

    private fun applyDockOffset() {
        val o = if (docked && !dockTop) dockOffset else 0f
        if (!docked || dockTop) dockOffset = 0f
        barRow.translationY = o
        dockBg.translationY = o
    }

    private fun snapDock() {
        val travel = dockTravel()
        if (dockOffset <= 0f || dockOffset >= travel) return
        animateDock(if (dockOffset > travel / 2f) travel else 0f)
    }

    /** A sáv visszaúszik a helyére (pl. új oldal betöltésekor, a mezőre koppintva). */
    private fun showDock(animate: Boolean = true) {
        handler.removeCallbacks(dockSnap)
        if (dockOffset <= 0f) return
        if (animate) animateDock(0f) else {
            dockAnim?.cancel()
            setDockOffset(0f)
        }
    }

    private fun animateDock(target: Float) {
        dockAnim?.cancel()
        val a = ValueAnimator.ofFloat(dockOffset, target)
        a.duration = 220L
        a.interpolator = DecelerateInterpolator()
        a.addUpdateListener { setDockOffset(it.animatedValue as Float) }
        dockAnim = a
        a.start()
    }

    /** A rögzített sáv magassága (a gombsor + körülötte kis térköz), a rendszersávok nélkül. */
    private fun dockHeight(): Int = barRow.layoutParams.height + dp(8)

    /**
     * A keresősáv stílusa. Lebegő: három külön üvegkapszula a tartalom fölött. Rögzített: egy
     * teljes szélességű sáv (üveg, matt üveg vagy tömör), az oldal pedig a sáv széléig tart, nem
     * fut alá (mint a Chrome-ban). A középső mező ilyenkor egy halvány kapszula a sávon belül.
     */
    private fun applyBarStyle() {
        if (docked && collapsed) setCollapsed(false)
        dockAnim?.cancel()
        dockOffset = 0f
        applyDockOffset()
        dockBg.visibility = if (docked) View.VISIBLE else View.GONE
        if (docked) {
            barLeft.background = null
            barRight.background = null
            // kisebb, teljesen lekerekített kapszula (függőlegesen beljebb húzva)
            val pill = GradientDrawable().apply { cornerRadius = dp(100).toFloat() }
            barCenter.background = android.graphics.drawable.InsetDrawable(pill, 0, dp(6), 0, dp(6))
            updateDockPill()
        } else {
            barLeft.background = barSkins[0]
            barCenter.background = barSkins[1]
            barRight.background = barSkins[2]
        }
        applyDockLayout()
        val lp = barRow.layoutParams as FrameLayout.LayoutParams
        val side = if (docked) dp(6) else dp(14)
        lp.marginStart = side
        lp.marginEnd = side
        lp.gravity = if (dockTop) Gravity.TOP else Gravity.BOTTOM
        barRow.layoutParams = lp
        applyLayoutInsets()
    }

    /** A rögzített sáv középső mezőjének halvány kapszulája (a sáv szövegszínéhez igazodik). */
    private fun updateDockPill() {
        if (!docked) return
        val c = contentColor ?: if (night) Color.WHITE else Color.BLACK
        val light = Color.red(c) > 128
        val bg = barCenter.background
        val pill = (bg as? android.graphics.drawable.InsetDrawable)?.drawable ?: bg
        (pill as? GradientDrawable)?.setColor(
            if (light) Color.argb(34, 255, 255, 255) else Color.argb(18, 0, 0, 0)
        )
    }

    /** Minden, ami a rendszersávoktól és a sáv stílusától függ: margók, térközök. */
    private fun applyLayoutInsets() {
        val barHeight = barRow.layoutParams.height
        val webTopY = if (dockTop) statusTop + dockHeight() else statusTop
        (progress.layoutParams as FrameLayout.LayoutParams).topMargin = webTopY
        tabList.setPadding(
            dp(9), webTopY + dp(10), dp(9),
            navBottom + if (dockTop) dp(24) else barHeight + dp(30)
        )
        tabs.forEach { applyWebTop(it.webView) }
        applyBottomInset(lastBottom)
        root.requestLayout()
        updateLens()
    }

    /** Az aktív WebView felső széle a képernyőn. */
    private fun webTop(): Int {
        if (dockTop) return statusTop + dockHeight()
        activeWebView ?: return statusTop
        return if (flowLayout()) 0 else statusTop
    }

    /**
     * A WebView felső széle. Sáv stílusnál az állapotsor alatt kezdődik. Átúszó stílusnál a
     * képernyő tetejéig ér, és az oldal tetején az állapotsor magasságával lejjebb van tolva; ez
     * a betét húzáskor összecsukódik (lásd [GlassWebView]).
     */
    private fun applyWebTop(wv: GlassWebView) {
        val flow = flowLayout()
        val lp = wv.layoutParams as? FrameLayout.LayoutParams ?: return
        val margin = when {
            dockTop -> statusTop + dockHeight()
            flow -> 0
            else -> statusTop
        }
        // alul rögzített sávnál az oldal a sáv tetejéig tart (nem fut alá)
        // alul rögzített sávnál is a képernyő aljáig ér: a sáv görgetéskor elúszik (mint a Firefoxban)
        val bottomMargin = 0
        if (lp.topMargin != margin || lp.bottomMargin != bottomMargin) {
            lp.topMargin = margin
            lp.bottomMargin = bottomMargin
            wv.layoutParams = lp
        }
        // A WebView maga nem mozog (attól akadt és villant a kép): átúszó módban az OLDAL kap
        // felül térközt, lásd topPadJs.
        wv.topInset = 0
        wv.evaluateJavascript(topPadJs(), null)
    }

    /**
     * Átúszó mód: az oldal teteje kap akkora térközt, amilyen magas az állapotsor, a képernyő
     * tetejéhez rögzített elemek (fejlécek) pedig ennyivel lejjebb kerülnek. Így az oldal eleje nem
     * kerül takarásba, görgetéskor viszont a tartalom valóban átúszik az állapotsor mögött.
     * Más módban ugyanez a szkript mindent visszaállít.
     */
    private fun topPadJs(): String {
        val px = if (flowLayout()) (statusTop / resources.displayMetrics.density).toInt() else 0
        return "(function(){var H=$px;var d=document;if(!d.documentElement)return;" +
            "var st=d.getElementById('__glass_top');" +
            "if(H>0){if(!st){st=d.createElement('style');st.id='__glass_top';d.documentElement.appendChild(st);}" +
            "st.textContent='html{padding-top:'+H+'px !important;box-sizing:border-box !important}';}" +
            "else if(st){st.remove();}" +
            "function fix(){if(!d.body)return;var all=d.body.getElementsByTagName('*');" +
            "for(var i=0;i<all.length;i++){var e=all[i];" +
            "if(e.__gbT!==undefined){if(e.__gbH!==H){" +
            "if(H>0)e.style.setProperty('top',(e.__gbT+H)+'px','important');else{e.style.removeProperty('top');delete e.__gbT;}" +
            "e.__gbH=H;}continue;}" +
            "if(H<=0)continue;var cs=getComputedStyle(e);" +
            "if(cs.position==='fixed'||cs.position==='sticky'){var t=parseFloat(cs.top);" +
            "if(!isNaN(t)&&t>=0&&t<4&&e.getBoundingClientRect().height<innerHeight*0.5){" +
            "e.__gbT=t;e.__gbH=H;e.style.setProperty('top',(t+H)+'px','important');}}}}" +
            "fix();if(H>0&&window.__gbTimer!==H){window.__gbTimer=H;setTimeout(fix,500);setTimeout(fix,1500);setTimeout(fix,4000);}" +
            "})();"
    }

    /** Egy nézet tényleges (nagyítással, eltolással együtt számolt) helye a gyökérnézetben. */
    private fun visualRect(v: View, out: RectF) {
        out.set(0f, 0f, v.width.toFloat(), v.height.toFloat())
        var cur: View = v
        while (true) {
            cur.matrix.mapRect(out)
            out.offset(cur.left.toFloat(), cur.top.toFloat())
            val p = cur.parent
            if (p !is View || p === root) break
            cur = p
        }
    }

    private fun barSlot(l: LensEffect, index: Int, v: View) {
        val strength = v.alpha * barRow.alpha
        if (v.width <= 0 || strength < 0.02f) {
            l.clearSlot(index)
            return
        }
        visualRect(v, tmpRect)
        // az üveg a felülettel együtt halványul el (nem ugrik be / ki)
        l.setSlot(
            index, tmpRect.left, tmpRect.top, tmpRect.right, tmpRect.bottom,
            tmpRect.height() / 2f, lensBlur, strength
        )
    }

    /** Megadja a shadernek, hol vannak éppen az üvegfelületek, és milyen a felső elmosás. */
    private fun updateLens() {
        if (!lensActive || Build.VERSION.SDK_INT < 33) return
        val l = lens ?: return
        if (scene.width <= 0 || scene.height <= 0) return
        try {
            val d = resources.displayMetrics.density
            if (capturing) {
                // pillanatkép készül: egy képkockára minden üveg lekerül a képről
                for (i in 0 until LensEffect.SLOTS) l.clearSlot(i)
                l.setTop(0f, 0f, 0f, 0, 0f, false)
                l.setFlow(0f, 0f, 0f, 0)
                l.apply()
                return
            }

            // felső elmosás; a lapváltó nyitásakor / zárásakor finoman halványul ki / be (topVis)
            val vis = topVis
            // (a térköz kitöltése akkor is él, ha az elmosás épp ki van halványítva: különben a lapváltó
            // bezárása után egy pillanatra szürke sáv villanna az állapotsor mögött)
            val searchVis = if (searchPage.visibility == View.VISIBLE) searchPage.alpha else 0f
            if (searchVis > 0.01f) {
                // Kereső felület: a fülek sávja mögé görgetett szöveg (és az állapotsor mögötti rész)
                // elmosódik, lefelé lágyan elhalványuló átmenettel.
                val bandH = segBar.top + segBar.translationY + segBar.height + 4f * d
                val tint = if (night) Color.argb((80 * searchVis).toInt(), 14, 16, 22)
                else Color.argb((110 * searchVis).toInt(), 243, 244, 248)
                l.setFlow(0f, 0f, 0f, 0)
                l.setTop(bandH, 30f * d, 16f * d * searchVis, tint, 0f, true)
            } else if (topBlurOn && statusTop > 0 && !searchShown && (vis > 0.01f || (flowLayout() && !overviewShown))) {
                // 0 erősségnél se elmosás, se árnyalat: az oldal tisztán látszik az állapotsor mögött
                val k = (topStrength / (6f * d)).coerceIn(0f, 1f) * vis
                val tint = if (night) Color.argb((96 * k).toInt(), 0, 0, 0) else Color.argb((80 * k).toInt(), 255, 255, 255)
                l.setFlow(0f, 0f, 0f, 0)
                if (flowLayout()) {
                    // amíg az oldal a tetején van, a fölötte lévő térköz ennyi képpont: ezt az oldal
                    // első sorának színével töltjük ki (különben szürke / sötét sáv látszana)
                    val pad = if (overviewShown) 0f else (statusTop - (activeWebView?.scrollY ?: statusTop)).coerceAtLeast(0).toFloat()
                    val amt = flowAmt * vis
                    if (amt <= 0.01f) {
                        // az oldal tetején (vagy kihalványított elmosásnál): csak a térköz kitöltése, élesen
                        if (!overviewShown && pad > 0f) {
                            l.setTop(statusTop.toFloat(), 0f, 0f, 0, pad, true)
                        } else {
                            l.setTop(0f, 0f, 0f, 0, 0f, false)
                        }
                    } else {
                        val flowTint = if (night) {
                            Color.argb((50 * k * flowAmt).toInt(), 0, 0, 0)
                        } else {
                            Color.argb((60 * k * flowAmt).toInt(), 255, 255, 255)
                        }
                        // csak kicsit nyúlik az állapotsor alá, hogy a rögzített fejléceket ne mossa el
                        l.setTop(statusTop.toFloat(), 18f * d * amt, topStrength * amt, flowTint, pad, true)
                    }
                } else {
                    // elmosott színsáv; görgetéskor lejjebb nyúlik
                    l.setTop(
                        statusTop.toFloat(), (8f + 36f * topScroll) * d, topStrength * vis, tint,
                        statusTop.toFloat(), false
                    )
                }
            } else {
                l.setTop(0f, 0f, 0f, 0, 0f, false)
                l.setFlow(0f, 0f, 0f, 0)
            }

            // a keresősáv három része
            if (docked) {
                // rögzített sáv: egyetlen, teljes szélességű üveg
                if (dockBg.visibility == View.VISIBLE && dockBg.width > 0 && dockBg.height > 0) {
                    visualRect(dockBg, tmpRect)
                    l.setSlot(
                        SLOT_CENTER, tmpRect.left, tmpRect.top, tmpRect.right, tmpRect.bottom,
                        0f, lensBlur, dockBg.alpha
                    )
                } else {
                    l.clearSlot(SLOT_CENTER)
                }
                l.clearSlot(SLOT_LEFT)
                l.clearSlot(SLOT_RIGHT)
            } else {
                barSlot(l, SLOT_CENTER, barCenter)
                barSlot(l, SLOT_LEFT, barLeft)
                barSlot(l, SLOT_RIGHT, barRight)
            }

            // hárompontos menü: a kinyíló alakot követi
            val r = menuClip.current
            if (menuVisible && r.width() > 1f && r.height() > 1f) {
                val ox = menuPanel.left.toFloat()
                val oy = menuPanel.top.toFloat()
                val rad = minOf(menuClip.currentRadius, r.width() / 2f, r.height() / 2f)
                // a rugózó nagyítást is követjük, különben a panel széle kilógna az üvegből
                val sc = menuPanel.scaleX
                val px = menuPanel.pivotX
                val py = menuPanel.pivotY
                l.setSlot(
                    SLOT_MENU,
                    ox + px + (r.left - px) * sc, oy + py + (r.top - py) * sc,
                    ox + px + (r.right - px) * sc, oy + py + (r.bottom - py) * sc,
                    rad * sc, panelBlur, menuGlassAlpha
                )
            } else {
                l.clearSlot(SLOT_MENU)
            }

            // keresési javaslatok
            // a kereső felület fülei közötti kapszula sima üveg (fénytörés nélkül, hogy olvasható legyen)
            // fordítási buborék: ugyanaz az üveg, mint a keresősávé
            val tb = trBar
            if (tb != null && tb.visibility == View.VISIBLE && tb.alpha > 0.02f && tb.width > 0) {
                visualRect(tb, tmpRect)
                l.setSlot(
                    SLOT_SUGGEST, tmpRect.left, tmpRect.top, tmpRect.right, tmpRect.bottom,
                    20f * d, lensBlur, tb.alpha
                )
            } else {
                l.clearSlot(SLOT_SUGGEST)
            }
            // "Vágólapra másolva" kapszula
            val ct = copyToast
            if (ct != null && ct.visibility == View.VISIBLE && ct.alpha > 0.02f && ct.width > 0 &&
                pullIndicator.alpha <= 0.02f
            ) {
                visualRect(ct, tmpRect)
                l.setSlot(
                    SLOT_SPARE, tmpRect.left, tmpRect.top, tmpRect.right, tmpRect.bottom,
                    tmpRect.height() / 2f, lensBlur, ct.alpha
                )
            } else if (pullIndicator.alpha > 0.02f && pullIndicator.width > 0) {
                visualRect(pullIndicator, tmpRect)
                l.setSlot(
                    SLOT_SPARE, tmpRect.left, tmpRect.top, tmpRect.right, tmpRect.bottom,
                    tmpRect.height() / 2f, lensBlur, pullIndicator.alpha
                )
            } else {
                l.clearSlot(SLOT_SPARE)
            }
            l.apply()
        } catch (e: Throwable) {
            // ha a telefon nem bírja a shadert, egyszerű üvegre váltunk
            lensFailed = true
            handler.post { applyGlassMode() }
        }
    }

    /** A sáv szövege és ikonjai sötétre vagy világosra váltanak, finom átmenettel. */
    private fun applyContentColor(dark: Boolean) {
        darkContent = dark
        val target = if (dark) Color.argb(235, 16, 20, 28) else Color.argb(245, 255, 255, 255)
        contentColorAnim?.cancel()
        val from = contentColor
        if (from == null) {
            setContentColor(target)
            return
        }
        if (from == target) return
        val anim = ValueAnimator.ofArgb(from, target)
        anim.duration = 320
        anim.addUpdateListener { setContentColor(it.animatedValue as Int) }
        contentColorAnim = anim
        anim.start()
    }

    private fun setContentColor(c: Int) {
        contentColor = c
        skins.forEach { it.setContentColor(c) }
        val tint = ColorStateList(
            arrayOf(intArrayOf(android.R.attr.state_enabled), intArrayOf()),
            intArrayOf(c, ColorUtils.setAlphaComponent(c, (Color.alpha(c) * 0.38f).toInt()))
        )
        listOf<ImageView>(iconBack, iconAdd, iconDone, btnMenu, btnReload).forEach { it.imageTintList = tint }
        if (::dockMenuBtn.isInitialized) dockMenuBtn.imageTintList = tint
        urlField.setTextColor(c)
        urlField.setHintTextColor(ColorUtils.setAlphaComponent(c, (Color.alpha(c) * 0.58f).toInt()))
        overviewLabel.setTextColor(c)
        tabCount.setTextColor(c)
        (tabCount.background.mutate() as? GradientDrawable)?.setStroke(dp(2), c)
        if (!glassFormOpen) tintTree(menuItems, menuTextColor())
        updateDockPill()
    }

    /**
     * A menü és a javaslatok sorainak színe. A TextView "tag"-je egy szorzó lehet (halványabb
     * másodlagos vagy letiltott szöveghez).
     */
    private fun tintTree(v: View, c: Int) {
        when (v) {
            is TextView -> {
                val factor = (v.tag as? Float) ?: 1f
                val col = ColorUtils.setAlphaComponent(c, (Color.alpha(c) * factor).toInt())
                v.setTextColor(col)
                // a menüsorok kis ikonjai a szöveggel együtt színeződnek (kicsit halványabban)
                v.compoundDrawableTintList = ColorStateList.valueOf(
                    ColorUtils.setAlphaComponent(c, (Color.alpha(c) * factor * 0.85f).toInt())
                )
            }
            is ImageView -> v.imageTintList =
                ColorStateList.valueOf(ColorUtils.setAlphaComponent(c, (Color.alpha(c) * 0.7f).toInt()))
            is ViewGroup -> for (i in 0 until v.childCount) tintTree(v.getChildAt(i), c)
        }
    }

    /**
     * A sáv fölötti képernyőrész világosságát méri (PixelCopy), hogy a sáv szövege és ikonjai
     * világosra vagy sötétre válthassanak. A WebView-hoz nem nyúl, így a görgetést nem zavarja.
     */
    private fun sampleLuminance() {
        if (docked && lensActive) {
            // rögzített sávnál az oldal nem fut a sáv alá: a sáv mögött a háttérszín van
            applyContentColor(!night)
            return
        }
        if (!lensActive || probeBusy || capturing || barRow.width <= 0) return
        val left = barRow.left.coerceAtLeast(0)
        val right = barRow.right.coerceAtMost(root.width)
        val bottom = barRow.top - dp(4)
        val top = (bottom - dp(14)).coerceAtLeast(0)
        if (bottom <= top || right <= left) return
        probeBusy = true
        try {
            PixelCopy.request(
                window, Rect(left, top, right, bottom), probeBitmap,
                PixelCopy.OnPixelCopyFinishedListener { result ->
                    if (result == PixelCopy.SUCCESS && lensActive) {
                        probeBitmap.getPixels(probePixels, 0, 48, 0, 0, 48, 6)
                        var sum = 0.0
                        for (p in probePixels) {
                            sum += 0.299 * Color.red(p) + 0.587 * Color.green(p) + 0.114 * Color.blue(p)
                        }
                        // a sötétítés a sávot is sötétebbé teszi, ezt beszámítjuk
                        val lum = (sum / probePixels.size / 255.0).toFloat() * (1f - glassShade)
                        // hiszterézis, hogy ne villogjon a szín
                        val newDark = if (darkContent) lum >= 0.42f else lum > 0.58f
                        if (newDark != darkContent) applyContentColor(newDark)
                    }
                    probeBusy = false
                },
                handler
            )
        } catch (e: Exception) {
            probeBusy = false
        }
    }

    /**
     * Az oldal felső szélének színe lesz a WebView mögötti háttér. Így ha a WebView fölött egy
     * pillanatra üres sáv marad (az állapotsor mögött), az az oldal színében látszik, nem feketén.
     */
    private fun sampleTopColor() {
        if (topProbeBusy || capturing || !topBlurOn || docked) return
        val wv = activeWebView ?: return
        val w = scene.width
        val top = webTop().coerceAtLeast(0) + dp(1)
        if (w <= 0 || wv.parent == null || top + dp(3) >= scene.height) return
        topProbeBusy = true
        try {
            PixelCopy.request(
                window, Rect(0, top, w, top + dp(3)), topProbeBitmap,
                PixelCopy.OnPixelCopyFinishedListener { result ->
                    if (result == PixelCopy.SUCCESS) {
                        topProbeBitmap.getPixels(topProbePixels, 0, 8, 0, 0, 8, 1)
                        var r = 0
                        var g = 0
                        var b = 0
                        for (p in topProbePixels) {
                            r += Color.red(p)
                            g += Color.green(p)
                            b += Color.blue(p)
                        }
                        contentContainer.setBackgroundColor(Color.rgb(r / 8, g / 8, b / 8))
                    }
                    topProbeBusy = false
                },
                handler
            )
        } catch (e: Exception) {
            topProbeBusy = false
        }
    }

    /** Az állapotsor ikonjai a világos / sötét módot követik. */
    private fun applyStatusColor() {
        WindowCompat.getInsetsController(window, root).isAppearanceLightStatusBars = !night
    }

    /** A frissítés jelzője a húzással együtt ereszkedik és forog. */
    private fun showPull(dist: Float) {
        if (pullSkin == null) {
            // ugyanaz az üveg, mint a keresősávé (a "bőrét" is a többivel együtt állítjuk)
            val d0 = resources.displayMetrics.density
            val skin = GlassSkinDrawable(d0)
            pullIndicator.background = skin
            skins.add(skin)
            pullSkin = skin
            applyGlassMode()
        }
        pullIcon.imageTintList = ColorStateList.valueOf(contentColor ?: Color.WHITE)
        val d = resources.displayMetrics.density
        val trigger = PULL_TRIGGER * d
        val p = (dist / trigger).coerceIn(0f, 1.4f)
        pullIndicator.animate().cancel()
        pullIcon.animate().cancel()
        pullIndicator.translationY = webTop().coerceAtLeast(statusTop) + d * 4f + dist.coerceAtMost(trigger * 1.3f) - dp(44)
        pullIndicator.alpha = (p * 1.4f).coerceIn(0f, 1f)
        val s = 0.6f + 0.4f * p.coerceAtMost(1f)
        pullIndicator.scaleX = s
        pullIndicator.scaleY = s
        pullIcon.rotation = p * 300f
        updateLens()
        if (p >= 1f && !pullArmed) {
            pullArmed = true
            haptic()
        } else if (p < 1f) {
            pullArmed = false
        }
    }

    private fun finishPull(refreshing: Boolean) {
        pullArmed = false
        if (refreshing) {
            // pörög egy kicsit, majd felúszik
            pullIcon.animate().rotationBy(360f).setDuration(520).setInterpolator(DecelerateInterpolator()).start()
            pullIndicator.animate().alpha(0f).translationY(statusTop - dp(44).toFloat())
                .setStartDelay(420).setDuration(260).setUpdateListener { updateLens() }.start()
        } else {
            pullIndicator.animate().alpha(0f).translationY(statusTop - dp(44).toFloat())
                .setStartDelay(0).setDuration(200).setUpdateListener { updateLens() }.start()
        }
    }

    /** A felső elmosás ki- / beúsztatása (a lapváltó nyitásakor és zárásakor). */
    private fun animateTopVis(target: Float, duration: Long) {
        topVisAnim?.cancel()
        val anim = ValueAnimator.ofFloat(topVis, target)
        anim.duration = duration
        anim.interpolator = DecelerateInterpolator()
        anim.addUpdateListener {
            topVis = it.animatedValue as Float
            updateLens()
        }
        topVisAnim = anim
        anim.start()
    }

    private fun updateTopFade(scrollY: Int) {
        val a = (scrollY / (48f * resources.displayMetrics.density)).coerceIn(0f, 1f)
        if (a != topScroll) {
            topScroll = a
            updateLens()
        }
        // Átúszó módban az oldal tetején nincs elmosás (a fejléc tisztán látszik); amint görgetni
        // kezdesz, az elmosás finoman bekapcsol, visszagörgetve a tetejére finoman kikapcsol.
        val want = if (scrollY > dp(2)) 1f else 0f
        if (want != flowTarget) {
            flowTarget = want
            flowAnim?.cancel()
            val anim = ValueAnimator.ofFloat(flowAmt, want)
            anim.duration = if (want > 0f) 260L else 320L
            anim.interpolator = DecelerateInterpolator()
            anim.addUpdateListener {
                flowAmt = it.animatedValue as Float
                updateLens()
            }
            flowAnim = anim
            anim.start()
        }
    }

    // ---------------------------------------------------------------- beállítások

    private fun userAgentBase(): String = WebSettings.getDefaultUserAgent(this)

    /** A WebView alap UA-jából kivesszük a "wv" és "Version/x" jelzőket, így Chrome-nak látszik. */
    private fun mobileChromeUa(): String =
        userAgentBase().replace("; wv", "").replace(Regex("Version/\\d+\\.\\d+\\s"), "")

    private fun desktopUa(): String {
        val v = Regex("Chrome/([\\d.]+)").find(userAgentBase())?.groupValues?.get(1) ?: "120.0.0.0"
        return "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/$v Safari/537.36"
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun applyWebSettings(wv: WebView) {
        val desktop = prefs.getBoolean("desktop_mode", false)
        wv.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            loadWithOverviewMode = true
            useWideViewPort = true
            setSupportZoom(true)
            builtInZoomControls = true
            displayZoomControls = false
            userAgentString = if (desktop) desktopUa() else mobileChromeUa()
        }
        if (Build.VERSION.SDK_INT >= 33) {
            // sötét témában a világos oldalakat is sötétíti
            wv.settings.setAlgorithmicDarkeningAllowed(prefs.getBoolean("dark_web", true))
        }
        val cm = CookieManager.getInstance()
        cm.setAcceptCookie(true)
        cm.setAcceptThirdPartyCookies(wv, prefs.getBoolean("third_party_cookies", true))
    }

    private fun applyAllSettings() {
        val desktop = prefs.getBoolean("desktop_mode", false)
        tabs.forEach { applyWebSettings(it.webView) }
        val before = appliedDesktop
        if (before != null && before != desktop) activeWebView?.reload()
        appliedDesktop = desktop
        AdBlock.enabled = prefs.getBoolean("adblock", true)
        userScripts = UserScripts.load(prefs).filter { it.enabled && it.code.isNotBlank() }
        val wasEn = L.en
        L.init(this)
        if (wasEn != L.en) onLanguageChanged()
        applyGlassMode()
    }

    /** Nyelvváltás után minden felirat az új nyelvre vált (újraindítás nélkül). */
    private fun onLanguageChanged() {
        L.tree(root)
        updateTabCount()
        if (::tabAdapter.isInitialized) tabAdapter.notifyDataSetChanged()
        if (!urlField.hasFocus()) setFieldText(displayText(currentUrl))
        tabs.forEach { t ->
            if (Urls.isStart(t.webView.url.orEmpty())) t.webView.evaluateJavascript(startPageJs(), null)
        }
    }

    private fun handleSettingsResult(data: Intent?) {
        if (data == null) return
        if (data.getBooleanExtra(SettingsActivity.EXTRA_CLEAR_CACHE, false)) {
            tabs.forEach {
                it.webView.clearCache(true)
                it.webView.clearHistory()
            }
        }
        data.getStringExtra(SettingsActivity.EXTRA_OPEN_URL)?.let { openNewTab(it, capture = false) }
    }

    /** Megnyitás előtt pillanatképet készít a képernyőről: ez lesz a beállítások üveghátterének alapja. */
    private fun openSettings() {
        val launch = { settingsLauncher.launch(Intent(this, SettingsActivity::class.java)) }
        val w = root.width
        val h = root.height
        if (w <= 0 || h <= 0) {
            launch()
            return
        }
        try {
            val bmp = Bitmap.createBitmap((w / 6).coerceAtLeast(1), (h / 6).coerceAtLeast(1), Bitmap.Config.ARGB_8888)
            PixelCopy.request(
                window, bmp,
                PixelCopy.OnPixelCopyFinishedListener { result ->
                    if (result == PixelCopy.SUCCESS) SettingsActivity.backdrop = bmp
                    launch()
                },
                handler
            )
        } catch (e: Exception) {
            launch()
        }
    }

    // ---------------------------------------------------------------- lapok

    private fun createTab(): Tab {
        val wv = GlassWebView(this)
        // A rendszer saját túlgörgetési hatása (nyújtás) kikapcsolva: lehúzáskor az oldal teteje
        // ne nyúljon meg (attól lett szürke a színes fejléc); helyette a frissítés jelzője jön.
        wv.overScrollMode = View.OVER_SCROLL_NEVER
        wv.layoutParams = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
        // így a telefon jelszókezelője (pl. a Google) fel tudja ajánlani a mentett jelszavakat
        wv.importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_YES
        val tab = Tab(wv)
        applyWebSettings(wv)
        // A kezdőlapon a hosszú nyomás csak a csempe szerkesztőjét nyitja meg: a WebView saját
        // reakciója (a link "kiemelése", húzása, szövegkijelölés) itt ki van kapcsolva.
        wv.setOnLongClickListener { Urls.isStart(wv.url.orEmpty()) }
        wv.onInsetChanged = { if (tab === activeTab) updateLens() }
        wv.onPull = { dist -> if (tab === activeTab) showPull(dist) }
        wv.onPullRelease = { dist ->
            if (tab === activeTab) {
                if (dist >= PULL_TRIGGER * resources.displayMetrics.density) {
                    haptic()
                    tab.webView.reload()
                    finishPull(true)
                } else {
                    finishPull(false)
                }
            }
        }

        wv.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val uri = request?.url ?: return false
                val scheme = uri.scheme ?: return false
                if (scheme == "http" || scheme == "https") {
                    // linkre koppintva: ha van hozzá telepített alkalmazás, abban nyílik meg
                    return request.isForMainFrame && !tab.stayInBrowser &&
                        (request.hasGesture() || request.isRedirect) && openInApp(uri)
                }
                if (scheme == "glass") {
                    // a kezdőlap csempéi: hosszú nyomásra szerkeszthetők
                    if (uri.host == "tile") {
                        formAnchor = tileAnchor(view, uri)
                        editTile(uri.lastPathSegment)
                        return true
                    }
                    // a kezdőlap keresőmezője: a böngésző saját keresősávja nyílik meg
                    urlField.requestFocus()
                    WindowCompat.getInsetsController(window, urlField).show(WindowInsetsCompat.Type.ime())
                    return true
                }
                if (scheme in setOf("file", "about", "data", "javascript")) return false
                runCatching {
                    val i = if (scheme == "intent") {
                        Intent.parseUri(uri.toString(), Intent.URI_INTENT_SCHEME)
                    } else {
                        Intent(Intent.ACTION_VIEW, uri)
                    }
                    startActivity(i)
                }
                return true
            }

            override fun shouldInterceptRequest(
                view: WebView?,
                request: WebResourceRequest?
            ): WebResourceResponse? {
                // Reklámblokkoló: a listán szereplő szerverek kérései üres választ kapnak
                if (request != null && !request.isForMainFrame && AdBlock.isBlocked(request.url?.host)) {
                    return WebResourceResponse("text/plain", "utf-8", ByteArrayInputStream(ByteArray(0)))
                }
                return null
            }

            override fun onPageCommitVisible(view: WebView?, url: String?) {
                // amint az új oldal megjelenik, megkapja a felső térközt (ne ugorjon később)
                view?.evaluateJavascript(topPadJs(), null)
            }

            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                tab.loading = true
                tab.progress = 5
                if (url != null) tab.url = url
                wv.resetInset()
                tab.translated = false
                tab.trPending = false
                if (tab === activeTab) {
                    showDock()
                    hideTranslateBar()
                    trFrom = ""
                }
                if (tab === activeTab) syncUi()
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                tab.loading = false
                tab.stayInBrowser = false
                // Üres hely az oldal aljára, hogy a lebegő sáv ne takarja a tartalmat
                view?.evaluateJavascript(SPACER_JS, null)
                view?.evaluateJavascript(topPadJs(), null)
                val pageUrl = url.orEmpty()
                userScripts.forEach { script ->
                    if (script.match.isBlank() || pageUrl.contains(script.match, ignoreCase = true)) {
                        view?.evaluateJavascript(UserScripts.wrap(script.code), null)
                    }
                }
                HistoryStore.add(url, view?.title)
                if (Urls.isStart(url.orEmpty())) view?.evaluateJavascript(startPageJs(), null)
                if (tab === activeTab && view != null) offerTranslate(tab, view)
                if (tab === activeTab) syncUi()
            }

            override fun doUpdateVisitedHistory(view: WebView?, url: String?, isReload: Boolean) {
                if (url != null) tab.url = url
                if (tab === activeTab) syncUi()
            }
        }

        wv.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                tab.progress = newProgress
                if (tab === activeTab) syncUi()
            }

            override fun onReceivedTitle(view: WebView?, title: String?) {
                tab.title = title.orEmpty()
                if (overviewShown) tabAdapter.notifyDataSetChanged()
            }

            override fun onReceivedIcon(view: WebView?, icon: Bitmap?) {
                tab.favicon = icon
                if (overviewShown) tabAdapter.notifyDataSetChanged()
            }
        }

        wv.setOnScrollChangeListener { _, _, scrollY, _, oldScrollY ->
            if (tab === activeTab && !overviewShown) {
                val dy = scrollY - oldScrollY
                when {
                    scrollY <= 4 -> setCollapsed(false)
                    dy > 6 -> setCollapsed(true)
                    dy < -6 -> setCollapsed(false)
                }
                if (docked && !dockTop) onDockScroll(scrollY, dy)
                updateTopFade(scrollY)
            }
        }

        tabs.add(tab)
        return tab
    }

    /**
     * Megpróbálja a linket a hozzá tartozó alkalmazásban megnyitni (YouTube, Reddit, X, Facebook…).
     * Csak akkor sikerül, ha van ilyen telepített alkalmazás, és az nem böngésző.
     */
    private fun openInApp(uri: Uri): Boolean {
        if (Build.VERSION.SDK_INT < 30 || !prefs.getBoolean("open_in_apps", true)) return false
        return try {
            val intent = Intent(Intent.ACTION_VIEW, uri)
            intent.addCategory(Intent.CATEGORY_BROWSABLE)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REQUIRE_NON_BROWSER)
            startActivity(intent)
            true
        } catch (e: Exception) {
            false // nincs hozzá alkalmazás: marad a böngészőben
        }
    }

    /** Csak az aktív lap WebView-ja marad a képernyőn; a többit levesszük és szüneteltetjük. */
    private fun removeInactiveWebViews() {
        val current = activeWebView
        for (i in contentContainer.childCount - 1 downTo 0) {
            val child = contentContainer.getChildAt(i)
            if (child !== current && child.visibility != View.GONE) {
                // A WebView nem kerül le a képernyőről, csak elrejtjük: a levett, majd visszatett
                // WebView néha nem rajzolta ki magát (szürke maradt, amíg nem görgettél).
                child.visibility = View.GONE
                (child as? WebView)?.onPause()
            }
        }
    }

    /**
     * Egy újra megjelenített WebView-t rákényszerít, hogy kirajzolja a tartalmát (egy alig
     * észrevehető, 1 képpontos görgetéssel oda-vissza), különben néha szürke maradna.
     */
    private fun kickRepaint(wv: WebView) {
        wv.postDelayed({
            if (wv.visibility != View.VISIBLE) return@postDelayed
            if (wv.canScrollVertically(1)) {
                wv.scrollBy(0, 1)
                wv.scrollBy(0, -1)
            } else if (wv.canScrollVertically(-1)) {
                wv.scrollBy(0, -1)
                wv.scrollBy(0, 1)
            }
            wv.invalidate()
        }, 60L)
    }

    private fun selectTab(tab: Tab, animate: Boolean = true) {
        hideTranslateBar()
        showDock(animate = false)
        val old = activeTab
        val wv = tab.webView
        if (old !== tab) {
            activeTab = tab
            applyWebTop(wv)
            applyStatusColor()
            // az új lap a régi fölé kerül; a régit csak az átmenet végén vesszük le
            if (wv.parent == null) contentContainer.addView(wv) else wv.bringToFront()
            wv.visibility = View.VISIBLE
            wv.onResume()
            kickRepaint(wv)
            wv.animate().cancel()
            if (old != null && animate) {
                val ow = old.webView
                ow.animate().cancel()
                ow.alpha = 1f
                wv.alpha = 0f
                wv.scaleX = 0.96f
                wv.scaleY = 0.96f
                wv.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(280)
                    .setInterpolator(DecelerateInterpolator())
                    .withEndAction { removeInactiveWebViews() }
                    .start()
            } else {
                wv.alpha = 1f
                wv.scaleX = 1f
                wv.scaleY = 1f
                removeInactiveWebViews()
            }
        } else {
            applyWebTop(wv)
            if (wv.parent == null) contentContainer.addView(wv)
            wv.visibility = View.VISIBLE
            wv.onResume()
        }

        val pending = tab.pendingUrl
        if (pending != null) {
            tab.pendingUrl = null
            tab.stayInBrowser = true
            wv.loadUrl(pending)
        }
        setCollapsed(false)
        updateTopFade(wv.scrollY)
        syncUi()
        updateTabCount()
        updateLens()
    }

    private fun openNewTab(raw: String?, focusField: Boolean = false, capture: Boolean = true) {
        val url = if (raw.isNullOrBlank()) homeUrl() else resolveInput(raw)
        val focus = {
            if (focusField) {
                urlField.requestFocus()
                WindowCompat.getInsetsController(window, urlField).show(WindowInsetsCompat.Type.ime())
            }
        }
        if (overviewBusy) {
            // éppen nyílik vagy záródik a lapváltó: kicsit később próbáljuk újra
            handler.postDelayed({ openNewTab(raw, focusField, capture) }, 500L)
            return
        }
        if (overviewShown) {
            // a lapváltóból: az új lapra váltunk, és a lapváltó eltűnik
            val tab = createTab()
            tab.url = url
            tab.pendingUrl = url
            openTabFromOverview(tab)
            handler.postDelayed({ focus() }, 520L)
            return
        }
        val proceed = {
            val tab = createTab()
            tab.url = url
            tab.pendingUrl = url
            selectTab(tab)
            focus()
        }
        val current = activeTab
        if (capture && current != null) {
            captureThumbnail(current) { proceed() }
        } else {
            proceed()
        }
    }

    /** Lap bezárása (a lapváltó kártyájának × gombjával). */
    private fun closeTab(tab: Tab) {
        if (overviewBusy) return
        val idx = tabs.indexOf(tab)
        if (idx < 0) return
        tabs.removeAt(idx)
        val wasActive = tab === activeTab
        if (wasActive) activeTab = null
        (tab.webView.parent as? ViewGroup)?.removeView(tab.webView)
        tab.webView.destroy()
        tab.thumbnail = null
        ThumbStore.delete(applicationContext, tab.id)

        if (tabs.isEmpty()) {
            // az utolsó lap is bezárult: új kezdőlap nyílik
            val fresh = createTab()
            fresh.url = homeUrl()
            fresh.pendingUrl = fresh.url
            if (overviewShown) {
                tabAdapter.notifyItemRemoved(idx)
                openTabFromOverview(fresh)
            } else {
                tabAdapter.notifyDataSetChanged()
                selectTab(fresh, animate = false)
            }
        } else {
            tabAdapter.notifyItemRemoved(idx)
            if (wasActive) {
                selectTab(tabs[idx.coerceAtMost(tabs.size - 1)], animate = !overviewShown)
                tabAdapter.notifyItemChanged(tabs.indexOf(activeTab))
            }
        }
        updateTabCount()
    }

    private fun updateTabCount() {
        tabCount.text = tabs.size.toString()
        overviewLabel.text = if (L.en) {
            "${tabs.size} " + if (tabs.size == 1) "tab" else "tabs"
        } else {
            "${tabs.size} lap"
        }
    }

    private fun restoreTabs() {
        val json = prefs.getString(KEY_TABS, null)
        if (!json.isNullOrEmpty()) {
            try {
                val arr = JSONArray(json)
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    val tab = createTab()
                    tab.url = o.optString("url", START_URL).ifBlank { START_URL }
                    tab.title = o.optString("title", "")
                    tab.pendingUrl = tab.url
                    val savedId = o.optLong("id", 0L)
                    if (savedId != 0L) tab.id = savedId
                }
            } catch (e: Exception) {
                // sérült mentés: üres lappal indulunk
            }
        }
        if (tabs.isEmpty()) {
            val tab = createTab()
            tab.url = homeUrl()
            tab.pendingUrl = tab.url
        }
        val idx = prefs.getInt(KEY_ACTIVE, 0).coerceIn(0, tabs.size - 1)
        selectTab(tabs[idx], animate = false)

        // A lapok bélyegképei lemezről: így újraindítás után sem szürkék a kártyák a lapváltóban.
        val restored = tabs.toList()
        val app = applicationContext
        Thread {
            val loaded = restored.map { it to ThumbStore.load(app, it.id) }
            handler.post {
                if (isDestroyed) return@post
                for ((tab, bmp) in loaded) {
                    if (bmp != null && tab.thumbnail == null) tab.thumbnail = bmp
                }
                tabAdapter.notifyDataSetChanged()
            }
            ThumbStore.prune(app, restored.map { it.id }.toSet())
        }.start()
    }

    private fun saveTabs() {
        val arr = JSONArray()
        tabs.forEach { t ->
            val o = JSONObject()
            o.put("id", t.id)
            o.put("url", t.webView.url ?: t.pendingUrl ?: t.url)
            o.put("title", t.title)
            arr.put(o)
        }
        prefs.edit()
            .putString(KEY_TABS, arr.toString())
            .putInt(KEY_ACTIVE, tabs.indexOf(activeTab).coerceAtLeast(0))
            .apply()
    }

    /**
     * A lap teljes képe (az egész WebView, a keresősáv alatti rész is), a sáv nélkül. Közvetlenül
     * a WebView-t rajzoljuk egy képre: ez nem a képernyőről olvas, ezért a sávot nem kell hozzá
     * elrejteni. Ha nem sikerül (üres kép), a képernyőről olvasunk a sáv fölötti részről.
     */
    private fun renderPage(tab: Tab, scale: Float): Bitmap? {
        val wv = tab.webView
        val w = wv.width
        val h = wv.height
        if (w <= 0 || h <= 0 || wv.parent == null) return null
        return try {
            val bmp = Bitmap.createBitmap(
                (w * scale).toInt().coerceAtLeast(1), (h * scale).toInt().coerceAtLeast(1),
                Bitmap.Config.ARGB_8888
            )
            val c = Canvas(bmp)
            c.drawColor(bgColor)
            c.scale(scale, scale)
            c.translate(-wv.scrollX.toFloat(), -wv.scrollY.toFloat())
            wv.draw(c)
            if (looksBlank(bmp)) {
                null
            } else {
                fillTopPad(bmp, scale, wv)
                bmp
            }
        } catch (e: Throwable) {
            null
        }
    }

    /** Átúszó módban az oldal fölötti térközt (az állapotsor helyét) az oldal első sorával töltjük ki. */
    private fun fillTopPad(bmp: Bitmap, scale: Float, wv: GlassWebView) {
        if (!flowLayout()) return
        val pad = ((statusTop - wv.scrollY).coerceAtLeast(0) * scale).toInt()
        if (pad <= 0 || pad + 2 >= bmp.height) return
        val c = Canvas(bmp)
        val row = Rect(0, pad + 1, bmp.width, pad + 2)
        c.drawBitmap(bmp, row, Rect(0, 0, bmp.width, pad + 1), null)
    }

    /** Igaz, ha a képen néhány mintapont mind ugyanolyan (vagyis a WebView nem rajzolt semmit). */
    private fun looksBlank(bmp: Bitmap): Boolean {
        val first = bmp.getPixel(bmp.width / 2, bmp.height / 2)
        for (fy in floatArrayOf(0.15f, 0.35f, 0.55f, 0.75f, 0.9f)) {
            for (fx in floatArrayOf(0.2f, 0.5f, 0.8f)) {
                val p = bmp.getPixel((bmp.width * fx).toInt(), (bmp.height * fy).toInt())
                if (p != first) return false
            }
        }
        return true
    }

    /** Bélyegkép az aktív lapról, mielőtt másik lapra váltunk. */
    private fun captureThumbnail(tab: Tab, done: () -> Unit) {
        val bmp = renderPage(tab, 0.55f)
        if (bmp != null) {
            tab.thumbnail = bmp
            ThumbStore.save(applicationContext, tab.id, bmp)
            done()
            return
        }
        capturePixels(tab, 0.35f, done)
    }

    /** Kép az aktív oldalról a lapváltó animációjához: a teljes oldal, a sáv mögötti rész is. */
    private fun captureSnapshot(tab: Tab, done: () -> Unit) {
        val bmp = renderPage(tab, 0.6f)
        if (bmp != null) {
            tab.thumbnail = bmp
            snapshotFull = true
            ThumbStore.save(applicationContext, tab.id, bmp)
            shrinkOtherThumbnails(tab)
            done()
            return
        }
        capturePixels(tab, 0.5f) {
            shrinkOtherThumbnails(tab)
            done()
        }
    }

    /** Tartalék: a képernyőről olvas, a keresősáv fölötti részről. */
    private fun capturePixels(tab: Tab, scale: Float, done: () -> Unit) {
        snapshotFull = false
        val w = scene.width
        val top = webTop().coerceAtLeast(0)
        val bottom = snapshotBottom()
        if (w <= 0 || bottom - top <= 0 || tab.webView.parent == null) {
            done()
            return
        }
        try {
            val bmp = Bitmap.createBitmap(
                (w * scale).toInt().coerceAtLeast(1),
                ((bottom - top) * scale).toInt().coerceAtLeast(1),
                Bitmap.Config.ARGB_8888
            )
            PixelCopy.request(
                window, Rect(0, top, w, bottom), bmp,
                PixelCopy.OnPixelCopyFinishedListener { result ->
                    if (result == PixelCopy.SUCCESS) {
                        tab.thumbnail = bmp
                        ThumbStore.save(applicationContext, tab.id, bmp)
                    }
                    done()
                },
                handler
            )
        } catch (e: Exception) {
            done()
        }
    }

    /** Eddig tart a pillanatkép: a keresősáv teteje fölött egy kevéssel. */
    private fun snapshotBottom(): Int {
        if (docked) return (activeWebView?.let { webFrame(it).bottom.toInt() } ?: scene.height).coerceAtLeast(1)
        return (barRow.top - dp(6)).coerceAtLeast(1)
    }

    /** A többi lap nagy képét kisebbre cseréljük, hogy sok lapnál se fogyjon el a memória. */
    private fun shrinkOtherThumbnails(keep: Tab) {
        // csak sok lapnál kicsinyítünk (különben a lap megnyitásakor elmosódott lenne a kép)
        if (tabs.size <= 10) return
        val limit = (scene.width * 0.3f).toInt().coerceAtLeast(64)
        for (t in tabs) {
            if (t === keep) continue
            val bmp = t.thumbnail ?: continue
            if (bmp.width > limit) {
                try {
                    t.thumbnail = Bitmap.createScaledBitmap(
                        bmp, (bmp.width / 2).coerceAtLeast(1), (bmp.height / 2).coerceAtLeast(1), true
                    )
                } catch (e: Throwable) {
                    // marad a régi kép
                }
            }
        }
    }

    // ---------------------------------------------------------------- lapváltó

    private fun setupOverview() {
        tabAdapter = TabAdapter(
            tabs = tabs,
            activeTab = { activeTab },
            hiddenTab = { heroTab },
            ringAlpha = { ringAlpha },
            titleAlpha = { titleAlpha },
            // a kártyára koppintva a lap teljes képernyőre nagyít
            onSelect = { tab -> hideOverview(tab) },
            onClose = { tab -> closeTab(tab) }
        )
        tabList.layoutManager = GridLayoutManager(this, 2)
        tabList.adapter = tabAdapter
        // bezáráskor a többi kártya lágyan csúszik a helyére
        tabList.itemAnimator = androidx.recyclerview.widget.DefaultItemAnimator().apply {
            moveDuration = 340
            removeDuration = 160
            addDuration = 200
        }
        setupSwipeToClose()

        // görgetéskor az aktív lap üveg bezáró gombja a kártyával együtt mozog
        tabList.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                if (overviewShown && !overviewBusy) updateLens()
            }
        })

        // a kártyák közötti üres háttérre koppintva a lapváltó bezárul
        val tapDetector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onSingleTapUp(e: MotionEvent): Boolean {
                if (tabList.findChildViewUnder(e.x, e.y) == null) hideOverview()
                return false
            }
        })
        tabList.addOnItemTouchListener(object : RecyclerView.SimpleOnItemTouchListener() {
            override fun onInterceptTouchEvent(rv: RecyclerView, e: MotionEvent): Boolean {
                tapDetector.onTouchEvent(e)
                return false
            }
        })
    }

    /**
     * A lapváltóban a kártyát jobbra húzva bezárható: húzás közben a kártya követi az ujjat,
     * kicsit elfordul, összemegy és elhalványul; elengedve kirepül, a többi kártya a helyére csúszik.
     */
    private fun setupSwipeToClose() {
        val callback = object : androidx.recyclerview.widget.ItemTouchHelper.SimpleCallback(
            0, androidx.recyclerview.widget.ItemTouchHelper.RIGHT
        ) {
            private var armed = false

            override fun getMovementFlags(rv: RecyclerView, vh: RecyclerView.ViewHolder): Int {
                if (!overviewShown || overviewBusy) return 0
                return super.getMovementFlags(rv, vh)
            }

            override fun onMove(
                rv: RecyclerView,
                vh: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean = false

            override fun getSwipeThreshold(vh: RecyclerView.ViewHolder): Float = 0.3f

            override fun getSwipeEscapeVelocity(defaultValue: Float): Float = defaultValue * 0.6f

            override fun onSwiped(vh: RecyclerView.ViewHolder, direction: Int) {
                val pos = vh.bindingAdapterPosition
                val tab = tabs.getOrNull(pos)
                armed = false
                if (tab == null || overviewBusy) {
                    if (pos >= 0) tabAdapter.notifyItemChanged(pos)
                    return
                }
                closeTab(tab)
            }

            override fun onChildDraw(
                c: Canvas,
                rv: RecyclerView,
                vh: RecyclerView.ViewHolder,
                dX: Float,
                dY: Float,
                actionState: Int,
                isCurrentlyActive: Boolean
            ) {
                val v = vh.itemView
                val w = rv.width.toFloat().coerceAtLeast(1f)
                val p = (dX / w).coerceIn(0f, 1f)
                v.translationX = dX
                v.pivotX = v.width / 2f
                v.pivotY = v.height.toFloat()
                v.rotation = 8f * p
                val sc = 1f - 0.12f * p
                v.scaleX = sc
                v.scaleY = sc
                v.alpha = (1f - 1.3f * p).coerceIn(0f, 1f)
                // a küszöböt átlépve (és visszahúzva) egy kis rezgés jelez
                if (isCurrentlyActive) {
                    val over = p >= 0.3f
                    if (over != armed) {
                        armed = over
                        haptic()
                    }
                }
            }

            override fun clearView(rv: RecyclerView, vh: RecyclerView.ViewHolder) {
                super.clearView(rv, vh)
                val v = vh.itemView
                v.translationX = 0f
                v.rotation = 0f
                v.scaleX = 1f
                v.scaleY = 1f
                v.alpha = 1f
                armed = false
            }
        }
        androidx.recyclerview.widget.ItemTouchHelper(callback).attachToRecyclerView(tabList)
    }

    /** A weboldal helye a képernyőn (innen indul és ide érkezik a "repülő" kép). */
    /** Az oldalkép helye teljes méretben: az egész WebView, vagy (tartalék képnél) a sáv fölötti rész. */
    private fun webRect(): RectF {
        val wv = activeWebView
        return if (snapshotFull && wv != null) {
            webFrame(wv)
        } else {
            RectF(0f, webTop().toFloat(), scene.width.toFloat(), snapshotBottom().toFloat())
        }
    }

    /**
     * A WebView helye. Egy épp visszatett (más lapról átváltott) WebView-nak még nincs mérete, ezért
     * ilyenkor a margóiból számoljuk: különben a "repülő" kép egy vonalba zsugorodna a képernyő
     * tetején, és utána egy pillanatra fekete képernyő látszana.
     */
    private fun webFrame(wv: WebView): RectF {
        if (wv.height > 0 && wv.isLaidOut) {
            return RectF(0f, wv.top.toFloat(), scene.width.toFloat(), wv.bottom.toFloat())
        }
        val lp = wv.layoutParams as? FrameLayout.LayoutParams
        val top = (lp?.topMargin ?: 0).toFloat()
        val bottom = (contentContainer.height - (lp?.bottomMargin ?: 0)).toFloat()
        return RectF(0f, top, scene.width.toFloat(), bottom.coerceAtLeast(top + 1f))
    }

    private fun cardView(tab: Tab?): View? {
        val idx = tabs.indexOf(tab)
        if (idx < 0) return null
        val item = tabList.layoutManager?.findViewByPosition(idx) ?: return null
        return item.findViewById(R.id.tabCard)
    }

    /** A lap kártyájának helye a képernyőn, ha éppen látszik a rácsban. */
    private fun cardRect(tab: Tab?): RectF? {
        val card = cardView(tab) ?: return null
        if (card.width <= 0 || card.height <= 0) return null
        val a = IntArray(2)
        val b = IntArray(2)
        card.getLocationInWindow(a)
        scene.getLocationInWindow(b)
        val l = (a[0] - b[0]).toFloat()
        val t = (a[1] - b[1]).toFloat()
        return RectF(l, t, l + card.width, t + card.height)
    }

    private fun showOverview() {
        if (overviewShown || overviewBusy) return
        val tab = activeTab ?: return
        hideTranslateBar()
        showDock(animate = false)
        hideMenu()
        releaseFocus()
        setCollapsed(false)
        overviewBusy = true
        captureSnapshot(tab) {
            animateTopVis(0f, 380)
            overviewShown = true
            ringAnim?.cancel()
            ringAlpha = 0f
            overviewBg.setImageBitmap(tab.thumbnail)
            heroTab = tab
            tabAdapter.notifyDataSetChanged()
            tabList.scaleX = 1f
            tabList.scaleY = 1f
            tabList.scrollToPosition(tabs.indexOf(tab).coerceAtLeast(0))
            overview.alpha = 0f
            overview.visibility = View.VISIBLE
            // a sáv végig látszik, csak a tartalma tűnik át a lapváltó gombjaira
            setBarMode(true, animate = true)
            // a rács elrendezése után tudjuk meg, hová kell a képnek zsugorodnia
            tabList.doOnPreDraw {
                setTitlesHidden()
                runOverview(true, tab, cardRect(tab)) {
                    overviewBusy = false
                    updateLens()
                    fadeInRing()
                    fadeInTitles()
                }
            }
        }
    }

    /** A kijelölt lap fehér kerete a megnyitás végén fokozatosan jelenik meg. */
    private fun fadeInRing() {
        ringAnim?.cancel()
        val anim = ValueAnimator.ofFloat(0f, 1f)
        anim.duration = 380
        anim.interpolator = DecelerateInterpolator()
        anim.addUpdateListener {
            ringAlpha = it.animatedValue as Float
            for (i in 0 until tabList.childCount) {
                val child = tabList.getChildAt(i)
                val ring = child.findViewById<View>(R.id.tabActive) ?: continue
                ring.alpha = ringAlpha
                if (ring.visibility == View.VISIBLE) {
                    child.findViewById<View>(R.id.tabClose)?.alpha = ringAlpha
                }
            }
            updateLens()
        }
        ringAnim = anim
        anim.start()
    }

    /**
     * Bezárja a lapváltót, és a [target] lapra vált (alapból az aktív lapra). [zoom] esetén a
     * lap kártyája nagyít teljes képernyőre, különben a lapváltó egyszerűen elhalványul.
     */
    private fun hideOverview(target: Tab? = activeTab, zoom: Boolean = true) {
        if (!overviewShown || overviewBusy) return
        val tab = target ?: return
        overviewBusy = true
        tabList.scaleX = 1f
        tabList.scaleY = 1f
        val card = if (zoom) cardRect(tab) else null
        if (tab !== activeTab) selectTab(tab, animate = false)
        // a kiválasztott lap legfrissebb képe repül vissza (ha más lapra váltottunk, annak a képe)
        snapshotFull = tab.thumbnail?.let { it.width * 1f / it.height < scene.width * 1f / maxOf(1, snapshotBottom() - webTop()) - 0.02f } ?: false
        // Az oldal kép teljes képernyőre úszik; a keresősáv addig rejtve van, és csak a végén,
        // a helyén kibontakozva jelenik meg (mint a Safariban).
        val hideBar = card != null
        if (hideBar) {
            // a lapok nevei gyorsan elhalványulnak, ahogy a kép kinagyítódik
            animateTitles(0f, 160)
            // a lapváltó gombjai elhalványulnak és kicsit összemennek; a keresősáv tartalma csak
            // akkor vált vissza, amikor már nem látszik (így nem villan fel közben)
            barRow.animate().cancel()
            barRow.pivotX = barRow.width / 2f
            barRow.pivotY = barRow.height.toFloat()
            barRow.animate().alpha(0f).scaleX(0.92f).scaleY(0.92f).setDuration(160)
                .setInterpolator(DecelerateInterpolator())
                .setUpdateListener { updateLens() }
                .withEndAction { setBarMode(false, animate = false) }
                .start()
        } else {
            setBarMode(false, animate = true)
        }
        runOverview(false, tab, card) {
            overview.visibility = View.GONE
            overviewBg.setImageBitmap(null)
            overviewShown = false
            overviewBusy = false
            if (hideBar) revealBar()
            activeWebView?.let { updateTopFade(it.scrollY) }
            animateTopVis(1f, 300)
            updateLens()
            syncUi()
        }
    }

    /**
     * Új lap a lapváltóból: az új oldal egy lekerekített "lapként" a + gombból nő ki teljes
     * képernyőre (mint a menü morph-ja), mögötte a lapváltó kártyái kicsit hátrébb húzódnak és
     * elhalványulnak. A landolás után a lap finoman átúszik a valódi (már betöltött) oldalba.
     */
    private fun openTabFromOverview(tab: Tab) {
        if (!overviewShown) {
            selectTab(tab, animate = false)
            return
        }
        overviewBusy = true
        overviewAnim?.cancel()
        // az új kártya a rácsban rejtve marad (nem villan fel üres kártya)
        heroTab = tab
        tabAdapter.notifyItemInserted(tabs.indexOf(tab))

        // a lap a képernyő közepéről, egy lapváltó-kártya méretéből nagyít ki (mint amikor a
        // lapváltóból egy lapot megnyitsz)
        val cw = hero.width * 0.44f
        val ch = cw * 1.32f
        val cx = hero.width / 2f
        val cy = hero.height / 2f
        val s = dp(22).toFloat()
        val from = RectF(cx - cw / 2f, cy - ch / 2f, cx + cw / 2f, cy + ch / 2f)
        val full = RectF(0f, 0f, hero.width.toFloat(), hero.height.toFloat())
        val night = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES
        val color = if (Urls.isStart(tab.url.orEmpty())) {
            if (night) 0xFF0E1016.toInt() else 0xFFF7F8FB.toInt()
        } else if (night) 0xFF121212.toInt() else Color.WHITE
        hero.animate().cancel()
        hero.setup(null, full, from, screenRadius, s, color)
        hero.progress = 1f
        hero.alpha = 0f
        hero.visibility = View.VISIBLE

        // az új oldal már most elkezd betöltődni a lapváltó alatt
        selectTab(tab, animate = false)

        // a lapnevek és a lapváltó gombjai gyorsan eltűnnek
        animateTitles(0f, 140)
        barRow.animate().cancel()
        barRow.pivotX = barRow.width / 2f
        barRow.pivotY = barRow.height.toFloat()
        barRow.animate().alpha(0f).scaleX(0.92f).scaleY(0.92f).setDuration(150)
            .setInterpolator(DecelerateInterpolator())
            .setUpdateListener { updateLens() }
            .withEndAction { setBarMode(false, animate = false) }
            .start()

        tabList.pivotX = tabList.width / 2f
        tabList.pivotY = tabList.height / 2f
        val anim = ValueAnimator.ofFloat(0f, 1f)
        anim.duration = 520L
        anim.interpolator = android.view.animation.PathInterpolator(0.3f, 0f, 0f, 1f)
        anim.addUpdateListener {
            val f = it.animatedValue as Float
            hero.progress = 1f - f
            // a kártya gyorsan előtűnik, majd teljes képernyőre nő
            hero.alpha = (f / 0.18f).coerceIn(0f, 1f)
            val sc = 1f - 0.08f * f
            tabList.scaleX = sc
            tabList.scaleY = sc
            tabList.alpha = (1f - f * 1.4f).coerceIn(0f, 1f)
            updateLens()
        }
        anim.addListener(object : AnimatorListenerAdapter() {
            private var cancelled = false
            override fun onAnimationCancel(animation: Animator) {
                cancelled = true
            }

            override fun onAnimationEnd(animation: Animator) {
                overview.visibility = View.GONE
                overviewBg.setImageBitmap(null)
                tabList.scaleX = 1f
                tabList.scaleY = 1f
                tabList.alpha = 1f
                heroTab = null
                overviewShown = false
                overviewBusy = false
                tabAdapter.notifyDataSetChanged()
                revealBar()
                activeWebView?.let { updateTopFade(it.scrollY) }
                animateTopVis(1f, 300)
                syncUi()
                updateLens()
                // a lap a valódi oldalba úszik át, amint az kirajzolódott
                whenPageDrawn(tab, 1500L) { fadeOutHero() }
            }
        })
        overviewAnim = anim
        anim.start()
    }

    /** Ennek változása megszakítja a "repülő" kép függőben lévő eltüntetését. */
    private var heroHold = 0

    /**
     * Akkor hívja [cb]-t, amikor a lap tartalma már kirajzolódott (a WebView jelzi), és ha éppen
     * töltődik, legalább a nagyja betöltött. Legfeljebb [maxMs] ideig vár.
     */
    private fun whenPageDrawn(tab: Tab, maxMs: Long, cb: () -> Unit) {
        val token = ++heroHold
        val start = android.os.SystemClock.uptimeMillis()
        var drawn = false
        try {
            tab.webView.postVisualStateCallback(token.toLong(), object : WebView.VisualStateCallback() {
                override fun onComplete(requestId: Long) {
                    drawn = true
                }
            })
        } catch (e: Throwable) {
            drawn = true
        }
        val check = object : Runnable {
            override fun run() {
                if (token != heroHold) return
                val ready = drawn && (!tab.loading || tab.progress >= 60)
                if (ready || android.os.SystemClock.uptimeMillis() - start > maxMs) {
                    // még egy képkocka, hogy a kirajzolt tartalom biztosan a képernyőn legyen
                    handler.postDelayed({ if (token == heroHold) cb() }, 34L)
                } else {
                    handler.postDelayed(this, 40L)
                }
            }
        }
        handler.post(check)
    }

    /** A "repülő" kép finoman átúszik a valódi oldalba, majd eltűnik. */
    private fun fadeOutHero() {
        if (hero.visibility != View.VISIBLE) return
        activeWebView?.let { kickRepaint(it) }
        hero.animate().cancel()
        hero.animate().alpha(0f).setDuration(180)
            .setInterpolator(DecelerateInterpolator())
            .withEndAction {
                hero.visibility = View.GONE
                hero.alpha = 1f
            }.start()
    }

    /** A keresősáv a helyén kibontakozik: elhalványulásból, kicsit kisebbről, enyhe rugózással. */
    private fun revealBar() {
        barRow.animate().cancel()
        setBarMode(false, animate = false)
        barRow.pivotX = barRow.width / 2f
        barRow.pivotY = barRow.height.toFloat()
        barRow.alpha = 0f
        barRow.scaleX = 0.9f
        barRow.scaleY = 0.9f
        barRow.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(340)
            .setInterpolator(OvershootInterpolator(1.6f))
            .setUpdateListener { updateLens() }
            .withEndAction { updateLens() }
            .start()
    }

    /**
     * A kijelző legmagasabb képfrissítését kérjük (pl. 120 Hz): így az animációk sokkal simábbak.
     * A rendszer dönthet másképp (pl. energiatakarékos módban).
     */
    private fun preferHighRefreshRate() {
        if (Build.VERSION.SDK_INT < 30) return
        try {
            val disp = display ?: return
            val cur = disp.mode
            val best = disp.supportedModes
                .filter { it.physicalWidth == cur.physicalWidth && it.physicalHeight == cur.physicalHeight }
                .maxByOrNull { it.refreshRate } ?: return
            val lp = window.attributes
            lp.preferredDisplayModeId = best.modeId
            window.attributes = lp
        } catch (e: Exception) {
            // marad az alapértelmezett
        }
    }

    /**
     * A lapok nevei a kártyák alatt: a kép landolása után finoman, alulról úsznak be; a lapváltó
     * bezárásakor gyorsan elhalványulnak. Egyetlen érték vezérli (a lista frissítése sem ugrasztja).
     */
    private fun applyTitleAlpha() {
        val shift = (1f - titleAlpha) * dp(8)
        for (i in 0 until tabList.childCount) {
            val child = tabList.getChildAt(i)
            listOf(R.id.tabTitle, R.id.tabIcon).forEach { id ->
                child.findViewById<View>(id)?.let {
                    it.alpha = titleAlpha
                    it.translationY = shift
                }
            }
        }
    }

    private fun setTitlesHidden() {
        titleAnim?.cancel()
        titleAlpha = 0f
        applyTitleAlpha()
    }

    private fun animateTitles(target: Float, duration: Long) {
        titleAnim?.cancel()
        val anim = ValueAnimator.ofFloat(titleAlpha, target)
        anim.duration = duration
        anim.interpolator = android.view.animation.PathInterpolator(0.2f, 0f, 0f, 1f)
        anim.addUpdateListener {
            titleAlpha = it.animatedValue as Float
            applyTitleAlpha()
        }
        titleAnim = anim
        anim.start()
    }

    private fun fadeInTitles() = animateTitles(1f, 420)

    /**
     * A lapváltó nyitó / záró animációja. Ha ismert a kártya helye, az oldal képe a teljes
     * képernyő és a kártya között "repül", a sarkai a képernyő saroksugarából a kártyáéba váltanak.
     */
    private fun runOverview(opening: Boolean, tab: Tab, card: RectF?, onEnd: () -> Unit) {
        overviewAnim?.cancel()
        val useHero = card != null
        if (card != null) {
            heroTab = tab
            cardView(tab)?.alpha = 0f
            hero.setup(tab.thumbnail, webRect(), card, screenRadius, dp(22).toFloat(), bgColor)
            hero.progress = if (opening) 0f else 1f
            heroHold++
            hero.animate().cancel()
            hero.alpha = 1f
            hero.visibility = View.VISIBLE
        } else {
            heroTab = null
            cardView(tab)?.alpha = 1f
            heroHold++
            hero.animate().cancel()
            hero.visibility = View.GONE
            hero.alpha = 1f
        }
        val anim = if (opening) ValueAnimator.ofFloat(0f, 1f) else ValueAnimator.ofFloat(1f, 0f)
        // hosszabb, lágyan lassuló mozgás (nem "csapódik" a helyére)
        anim.duration = if (opening) 520L else 480L
        anim.interpolator = if (opening) {
            android.view.animation.PathInterpolator(0.2f, 0f, 0f, 1f)
        } else {
            android.view.animation.PathInterpolator(0.3f, 0f, 0f, 1f)
        }
        anim.addUpdateListener {
            val f = it.animatedValue as Float
            if (useHero) hero.progress = f
            val c = f.coerceIn(0f, 1f)
            // Záráskor (ha van repülő kép) a lapváltó végig fedi a valódi oldalt, és csak a kép
            // landolása után tűnik el (lásd lent). Így nem látszik ki az oldal, és a sáv mögött
            // sem marad üres, sötét rész.
            if (useHero) {
                // A lapváltó háttere (az oldal elmosott képe) azonnal teljesen látszik: a repülő kép
                // eleinte az egész képernyőt fedi, így a váltás nem látszik, a valódi oldal pedig
                // nem dereng át "szellemképként". Nyitáskor csak a többi kártya úszik be.
                overview.alpha = 1f
                tabList.alpha = if (opening) (c / 0.55f).coerceIn(0f, 1f) else 1f
            } else {
                overview.alpha = (c / 0.55f).coerceIn(0f, 1f)
                tabList.alpha = 1f
            }
            updateLens()
        }
        anim.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                if (!opening && useHero && overviewAnim === anim) {
                    // a kép már pontosan a valódi oldal helyén van: a lapváltó finoman elhalványul
                    // alóla, majd a kép is eltűnik (a mögötte lévő oldal ugyanaz)
                    overview.animate().cancel()
                    // A kép addig marad a helyén, amíg a valódi oldal ki nem rajzolódott (különben
                    // hosszabb szünet után egy pillanatra fekete / üres képernyő látszana).
                    overview.animate().alpha(0f).setDuration(170)
                        .setUpdateListener { updateLens() }
                        .withEndAction {
                            heroTab = null
                            cardView(tab)?.alpha = 1f
                            onEnd()
                            whenPageDrawn(tab, 2500L) { fadeOutHero() }
                        }.start()
                    return
                }
                hero.visibility = View.GONE
                heroTab = null
                cardView(tab)?.alpha = 1f
                tabList.alpha = 1f
                onEnd()
            }
        })
        overviewAnim = anim
        anim.start()
    }

    // ---------------------------------------------------------------- keresősáv

    /**
     * Rugózó lenyomás: érintésre a felület kicsit összemegy, elengedéskor visszapattan.
     * Az üveg (a shader) együtt mozog vele.
     */
    private fun pressBounce(v: View, down: Boolean) {
        if (down) haptic()
        if (collapsed || !bounceOn) return
        v.pivotX = v.width / 2f
        v.pivotY = v.height / 2f
        v.animate().cancel()
        val pressed = 1f - (0.04f + 0.16f * bounceStrength)
        val anim = v.animate()
            .scaleX(if (down) pressed else 1f)
            .scaleY(if (down) pressed else 1f)
            // ha épp a kinyíló animáció közben érkezik, azt is befejezzük
            .translationX(0f)
            .translationY(0f)
            .alpha(1f)
            .setUpdateListener { updateLens() }
        if (down) {
            anim.setDuration(90).setInterpolator(DecelerateInterpolator())
        } else {
            anim.setDuration(420).setInterpolator(OvershootInterpolator(2.5f + 5f * bounceStrength))
        }
        anim.start()
    }

    /** Rövid rezgés érintéskor; az erőssége a beállításokban állítható. */
    private fun haptic() {
        if (!hapticOn || hapticStrength <= 0f) return
        try {
            val vib = getSystemService(Vibrator::class.java) ?: return
            if (!vib.hasVibrator()) return
            val ms = (6f + 22f * hapticStrength).toLong()
            val amplitude = if (vib.hasAmplitudeControl()) {
                (20f + 235f * hapticStrength).toInt().coerceIn(1, 255)
            } else {
                VibrationEffect.DEFAULT_AMPLITUDE
            }
            vib.vibrate(VibrationEffect.createOneShot(ms, amplitude))
        } catch (e: Exception) {
            // rezgés nélkül is működik
        }
    }

    private fun setupControls() {
        listOf(barLeft, barCenter, barRight).forEach { part ->
            (part as? PressLayout)?.onPress = { down -> pressBounce(part, down) }
        }
        barLeft.setOnClickListener {
            if (barOverview) {
                openNewTab(null, focusField = false, capture = false)
            } else if (docked) {
                // rögzített sáv: kezdőlap gomb
                activeTab?.stayInBrowser = true
                activeWebView?.loadUrl(homeUrl())
            } else {
                activeWebView?.let { if (it.canGoBack()) it.goBack() }
            }
        }
        barRight.setOnClickListener { if (barOverview) hideOverview() else showOverview() }
        btnMenu.setOnClickListener { if (menuVisible) hideMenu() else showMenu() }
        btnReload.setOnClickListener {
            if (urlField.hasFocus()) {
                // gépelés közben ez a gomb törli a mezőt
                urlField.setText("")
                return@setOnClickListener
            }
            val t = activeTab ?: return@setOnClickListener
            if (t.loading) t.webView.stopLoading() else t.webView.reload()
        }

        // Hosszú nyomás a címen (gépelés nélkül): a webcím a vágólapra kerül, más nem történik.
        urlField.isLongClickable = false
        val copyDetector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onLongPress(e: MotionEvent) {
                if (urlField.hasFocus()) return
                urlCopied = true
                copyUrl()
                // a mező ne kapja meg a felengedést (különben szerkesztésbe lépne)
                val cancel = MotionEvent.obtain(e)
                cancel.action = MotionEvent.ACTION_CANCEL
                urlField.onTouchEvent(cancel)
                cancel.recycle()
            }
        })
        urlField.setOnTouchListener { _, ev ->
            if (ev.actionMasked == MotionEvent.ACTION_DOWN) urlCopied = false
            copyDetector.onTouchEvent(ev)
            urlCopied
        }

        // összehúzott állapotban a kapszulára koppintva a sáv visszanő
        urlField.setOnClickListener { if (collapsed) setCollapsed(false) }

        urlField.setOnFocusChangeListener { _, hasFocus ->
            // gépelés közben a hosszú nyomás a szokásos (beillesztés, kijelölés), egyébként másol
            urlField.isLongClickable = hasFocus
            if (hasFocus) {
                setCollapsed(false)
                showDock()
                urlField.gravity = Gravity.CENTER_VERTICAL or Gravity.START
                if (docked) btnReload.visibility = View.VISIBLE
                setFieldText(if (Urls.isStart(currentUrl)) "" else currentUrl)
                lastTyped = ""
                urlField.post { urlField.selectAll() }
                showSearchPage()
            } else {
                urlField.gravity = if (docked) Gravity.CENTER_VERTICAL or Gravity.START else Gravity.CENTER
                if (docked) btnReload.visibility = View.GONE
                setFieldText(displayText(currentUrl))
                hideSuggestions()
            }
            syncUi()
        }

        urlField.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                if (settingText || !urlField.hasFocus()) return
                autocomplete(s)
                handler.removeCallbacks(suggestRunnable)
                handler.postDelayed(suggestRunnable, 120L)
            }
        })

        urlField.setOnEditorActionListener { _, actionId, event ->
            val enter = event?.keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_DOWN
            if (actionId == EditorInfo.IME_ACTION_GO || actionId == EditorInfo.IME_ACTION_DONE || enter) {
                activeTab?.stayInBrowser = true
                activeWebView?.loadUrl(resolveInput(urlField.text.toString()))
                releaseFocus()
                true
            } else {
                false
            }
        }
    }

    /** Amit a felhasználó tényleg begépelt (a kijelölt, felajánlott kiegészítés nélkül). */
    private fun typedText(): String {
        val text = urlField.text?.toString().orEmpty()
        val a = urlField.selectionStart
        val b = urlField.selectionEnd
        return if (a in 0 until b && b == text.length) text.substring(0, a) else text
    }

    /**
     * Cím-kiegészítés: ha a begépelt szöveg egy ismert webhely nevének eleje, a folytatást
     * kijelölve a mezőbe írja. Enterrel egyből megnyílik; tovább gépelve vagy törölve eltűnik.
     */
    private fun autocomplete(s: Editable?) {
        if (s == null) return
        val now = s.toString()
        val previous = lastTyped
        lastTyped = now
        // csak előre gépeléskor (törléskor nem), a szöveg végén, és ha webcímnek néz ki
        if (now.isEmpty() || now.length <= previous.length || !now.startsWith(previous)) return
        if (urlField.selectionEnd != now.length || now.contains(' ') || now.contains('/')) return
        val q = now.lowercase()
        val match = (HistoryStore.hosts() + POPULAR_HOSTS).firstOrNull { it.length > q.length && it.startsWith(q) }
            ?: return
        settingText = true
        s.append(match.substring(q.length))
        settingText = false
        urlField.setSelection(now.length, s.length)
    }

    /** A mező szövegének beállítása úgy, hogy az ne számítson gépelésnek (ne kérjen javaslatot). */
    private fun setFieldText(text: String) {
        settingText = true
        urlField.setText(text)
        settingText = false
    }

    private fun fade(v: View, show: Boolean, animate: Boolean) {
        v.animate().cancel()
        if (!animate) {
            v.alpha = if (show) 1f else 0f
            v.visibility = if (show) View.VISIBLE else View.INVISIBLE
        } else if (show) {
            v.visibility = View.VISIBLE
            v.animate().alpha(1f).setDuration(200).start()
        } else {
            v.animate().alpha(0f).setDuration(140)
                .withEndAction { v.visibility = View.INVISIBLE }
                .start()
        }
    }

    /**
     * A sáv három része a lapváltóban mást mutat: balra "új lap", középen a lapok száma, jobbra a
     * "kész" pipa. Ugyanazok az üvegfelületek maradnak, csak a tartalmuk tűnik át.
     */
    private fun setBarMode(overviewMode: Boolean, animate: Boolean) {
        barOverview = overviewMode
        updateTabCount()
        fade(iconBack, !overviewMode, animate)
        fade(iconAdd, overviewMode, animate)
        fade(browseRow, !overviewMode, animate)
        fade(overviewLabel, overviewMode, animate)
        fade(tabCount, !overviewMode, animate)
        fade(iconDone, overviewMode, animate)
        applyDockLayout()
    }

    /**
     * Görgetéskor a két szélső gomb eltűnik, a középső rész pedig kis kapszulává húzódik össze,
     * benne az oldal nevével. Felfelé görgetve (vagy a kapszulára koppintva) visszaáll.
     */
    private fun setCollapsed(value: Boolean) {
        if (value && (docked || urlField.hasFocus() || overviewShown || overviewBusy || menuVisible)) return
        if (collapsed == value) return
        collapsed = value
        val duration = 260L
        val scale = if (value) 0.58f else 1f

        barCenter.pivotX = barCenter.width / 2f
        barCenter.pivotY = barCenter.height.toFloat()
        barCenter.animate().cancel()
        barCenter.animate()
            .scaleX(scale)
            .scaleY(scale)
            .translationY(if (value) dp(6).toFloat() else 0f)
            .setDuration(duration)
            .setInterpolator(DecelerateInterpolator())
            .setUpdateListener { updateLens() }
            .start()

        // a cím nagyobb marad, mint maga a kapszula, hogy olvasható legyen
        val textScale = if (value) 0.84f / scale else 1f
        urlField.animate().cancel()
        urlField.animate()
            .scaleX(textScale)
            .scaleY(textScale)
            .setDuration(duration)
            .setInterpolator(DecelerateInterpolator())
            .start()
        urlField.isFocusable = !value
        urlField.isFocusableInTouchMode = !value

        val a = if (value) 0f else 1f
        listOf<View>(btnMenu, btnReload).forEach {
            it.animate().alpha(a).setDuration(160).start()
            it.isClickable = !value
        }

        // a szélső gombok a közép felé húzódva tűnnek el
        val sideScale = if (value) 0.6f else 1f
        val shift = dp(36).toFloat()
        barLeft.animate().cancel()
        barLeft.animate().alpha(a).scaleX(sideScale).scaleY(sideScale)
            .translationX(if (value) shift else 0f)
            .setDuration(duration).setInterpolator(DecelerateInterpolator()).start()
        barRight.animate().cancel()
        barRight.animate().alpha(a).scaleX(sideScale).scaleY(sideScale)
            .translationX(if (value) -shift else 0f)
            .setDuration(duration).setInterpolator(DecelerateInterpolator()).start()
        barLeft.isClickable = !value
        barRight.isClickable = !value
    }

    // ---------------------------------------------------------------- keresési javaslatok

    /** A fülek sávja és a köztük váltó üvegkapszula. */
    private fun setupSearchPage() {
        val d = resources.displayMetrics.density
        val bg = GradientDrawable()
        bg.cornerRadius = 23f * d
        bg.setColor(if (night) Color.argb(30, 255, 255, 255) else Color.argb(18, 0, 0, 0))
        segBar.background = bg
        segLabels.forEachIndexed { i, label ->
            label.setOnClickListener {
                haptic()
                selectSearchTab(i, animate = true)
            }
        }
        segBar.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> placeCapsule() }
        // a megnövő kapszula kilóghat a sávból: ne vágja le se a sáv, se a szülője
        segBar.clipChildren = false
        segBar.clipToPadding = false
        searchColumn.clipChildren = false
        // A fülek sávja a tartalom fölé kerül (a képernyő gyökerébe): a lista alatta görgethető, és
        // a mögötte lévő szöveget az üveg elmossa (az üveg így nem mossa el magát a sávot).
        searchColumn.removeView(segBar)
        findViewById<android.widget.ScrollView>(R.id.searchScroll).clipToPadding = false
        val shade = View(this)
        shade.visibility = View.GONE
        searchShade = shade
        val at = root.indexOfChild(dockBg)
        root.addView(shade, at, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(120)))
        val slp = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(46))
        slp.gravity = Gravity.TOP
        slp.marginStart = dp(16)
        slp.marginEnd = dp(16)
        root.addView(segBar, at + 1, slp)
        segBar.visibility = View.GONE
        // a kapszula: áttetsző fehér üveg, vékony fényes szegéllyel
        val cap = GradientDrawable()
        cap.cornerRadius = 20f * d
        cap.setColor(if (night) Color.argb(46, 255, 255, 255) else Color.argb(235, 255, 255, 255))
        cap.setStroke((1f * d).toInt().coerceAtLeast(1), Color.argb(if (night) 90 else 40, 255, 255, 255))
        segCapsule.background = cap
        segCapsule.elevation = if (night) 0f else 2f * d

        // üres területre koppintva a kereső felület bezárul, és visszajön a weboldal
        searchPage.setOnClickListener { releaseFocus() }
        val tapOutside = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onSingleTapUp(e: MotionEvent): Boolean {
                val scroll = findViewById<View>(R.id.searchScroll)
                // a lista alatti üres rész
                if (e.y + scroll.scrollY > searchList.height) releaseFocus()
                return false
            }
        })
        findViewById<View>(R.id.searchScroll).setOnTouchListener { _, ev ->
            tapOutside.onTouchEvent(ev)
            false
        }
    }

    private fun pageTextColor(): Int = if (night) Color.argb(245, 255, 255, 255) else Color.argb(235, 16, 20, 28)

    /** A kapszula mérete és helye a kiválasztott fül szerint (animáció nélkül). */
    private fun placeCapsule() {
        val inner = segBar.width - segBar.paddingLeft - segBar.paddingRight
        if (inner <= 0) return
        val w = inner / 3
        val lp = segCapsule.layoutParams
        if (lp.width != w) {
            lp.width = w
            segCapsule.layoutParams = lp
        }
        if (segAnim == null) segCapsule.translationX = (w * searchTab).toFloat()
        updateLens()
    }

    private fun selectSearchTab(index: Int, animate: Boolean) {
        val changed = index != searchTab
        searchTab = index
        val text = pageTextColor()
        segLabels.forEachIndexed { i, label ->
            label.setTextColor(if (i == index) text else ColorUtils.setAlphaComponent(text, 150))
        }
        val w = segCapsule.width
        segAnim?.cancel()
        segAnim = null
        if (!animate || !changed || w <= 0) {
            placeCapsule()
        } else {
            // a kapszula megnő, átcsúszik, majd visszahúzódik (az üveg végig követi)
            val from = segCapsule.translationX
            val to = (w * index).toFloat()
            val anim = ValueAnimator.ofFloat(0f, 1f)
            anim.duration = 380
            anim.interpolator = OvershootInterpolator(1.3f)
            anim.addUpdateListener {
                val t = it.animatedValue as Float
                // a rugózás se vigye túl a sáv két végén
                segCapsule.translationX = (from + (to - from) * t).coerceIn(0f, (w * 2).toFloat())
                val g = kotlin.math.sin(Math.PI * it.animatedFraction).toFloat()
                segCapsule.scaleX = 1f + 0.06f * g
                segCapsule.scaleY = 1f + 0.16f * g
                updateLens()
            }
            anim.addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    if (segAnim === anim) segAnim = null
                    segCapsule.scaleX = 1f
                    segCapsule.scaleY = 1f
                    updateLens()
                }
            })
            segAnim = anim
            anim.start()
        }
        if (changed) searchKeys = emptyList() // fülváltáskor minden sor újra beúszik
        renderSearch()
    }

    /** A címsorra koppintva: a kereső felület beúszik a weboldal fölé. */
    /** Üveg nélkül: a fülek sávja mögött lefelé elhalványuló, tömör sáv (olvashatóság). */
    private var searchShade: View? = null

    private fun searchOverlayIn() {
        val views = listOfNotNull(segBar, searchShade?.takeIf { !lensActive })
        searchShade?.let { if (lensActive) it.visibility = View.GONE }
        searchShade?.background = GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            if (night) intArrayOf(0xF20E1016.toInt(), 0xF20E1016.toInt(), 0x000E1016)
            else intArrayOf(0xF2F3F4F8.toInt(), 0xF2F3F4F8.toInt(), 0x00F3F4F8)
        )
        for (v in views) {
            v.animate().cancel()
            if (v.visibility != View.VISIBLE) {
                v.visibility = View.VISIBLE
                v.alpha = 0f
            }
            v.animate().alpha(1f).setDuration(220).setInterpolator(DecelerateInterpolator()).start()
        }
        segBar.translationY = dp(26).toFloat()
        segBar.animate().translationY(0f).setDuration(380)
            .setInterpolator(OvershootInterpolator(1.4f))
            .setUpdateListener { updateLens() }.start()
    }

    private fun searchOverlayOut() {
        for (v in listOfNotNull(segBar, searchShade)) {
            if (v.visibility != View.VISIBLE) continue
            v.animate().cancel()
            v.animate().alpha(0f).setDuration(200)
                .withEndAction { if (!searchShown) v.visibility = View.GONE }.start()
        }
        segBar.animate().translationY(dp(18).toFloat())
    }

    private fun showSearchPage() {
        onlineItems = emptyList()
        searchKeys = emptyList()
        searchShown = true
        searchOverlayIn()
        selectSearchTab(0, animate = false)
        if (searchPage.visibility != View.VISIBLE) {
            searchPage.alpha = 0f
            searchPage.visibility = View.VISIBLE
        }
        searchColumn.translationY = dp(26).toFloat()
        searchColumn.animate().cancel()
        searchColumn.animate().translationY(0f).setDuration(380)
            .setInterpolator(OvershootInterpolator(1.4f)).start()
        animateSearchBlur(true)
        searchPage.animate().cancel()
        searchPage.animate().alpha(1f).setDuration(220)
            .setInterpolator(DecelerateInterpolator())
            .setUpdateListener { updateLens() }
            .start()
        updateSuggestions("")
        updateLens()
    }

    /** A weboldal elmosódik a kereső felület mögött (Android 12+). */
    private fun animateSearchBlur(on: Boolean) {
        if (Build.VERSION.SDK_INT < 31) return
        searchBlurAnim?.cancel()
        val target = if (on) 1f else 0f
        val anim = ValueAnimator.ofFloat(searchBlur, target)
        anim.duration = if (on) 260L else 200L
        anim.interpolator = DecelerateInterpolator()
        anim.addUpdateListener {
            searchBlur = it.animatedValue as Float
            val r = 30f * resources.displayMetrics.density * searchBlur
            contentContainer.setRenderEffect(
                if (r < 0.5f) null else RenderEffect.createBlurEffect(r, r, Shader.TileMode.CLAMP)
            )
        }
        searchBlurAnim = anim
        anim.start()
    }

    private fun hideSuggestions() {
        suggestSeq++
        handler.removeCallbacks(suggestRunnable)
        if (!searchShown) return
        searchShown = false
        searchOverlayOut()
        searchColumn.animate().cancel()
        searchColumn.animate().translationY(dp(18).toFloat()).setDuration(200).start()
        animateSearchBlur(false)
        searchPage.animate().cancel()
        searchPage.animate().alpha(0f).setDuration(200)
            .setUpdateListener { updateLens() }
            .withEndAction {
                if (!searchShown) searchPage.visibility = View.GONE
                updateLens()
            }
            .start()
    }

    /**
     * Gépelés közben: a keresőmotor javaslatait lekéri, és újrarajzolja a listát. Az új javaslatok
     * megérkezéséig a korábbiak maradnak a helyükön (nem villog, nem ugrál).
     */
    private fun updateSuggestions(raw: String) {
        if (!urlField.hasFocus()) {
            hideSuggestions()
            return
        }
        val q = raw.trim()
        val seq = ++suggestSeq
        if (q.isEmpty() || !prefs.getBoolean("search_suggestions", true)) {
            onlineItems = emptyList()
            renderSearch()
            return
        }
        renderSearch()
        val engine = prefs.getString("search_engine", DEFAULT_SEARCH) ?: DEFAULT_SEARCH
        Suggest.fetch(q, engine) { list ->
            // csak akkor mutatjuk, ha közben nem gépeltek tovább
            if (seq == suggestSeq && !isDestroyed && urlField.hasFocus()) {
                onlineItems = list
                renderSearch()
            }
        }
    }

    /** A kiválasztott fül sorai a begépelt szöveg szerint. */
    private fun searchItems(): List<SuggestItem> {
        val q = typedText().trim()
        val out = ArrayList<SuggestItem>()
        when (searchTab) {
            1 -> for (b in BookmarkStore.list(q)) {
                out.add(SuggestItem(b.title.ifBlank { Urls.host(b.url) }, b.url, b.url, R.drawable.ic_bookmark))
            }
            2 -> for (e in HistoryStore.list(q, 120)) {
                val host = Urls.host(e.url)
                out.add(SuggestItem(e.title.ifBlank { host }, e.url, e.url, R.drawable.ic_history))
            }
            else -> {
                // Firefox-szerű: felül a keresőmotor javaslatai, alatta a weboldal-ajánlatok
                if (q.isEmpty()) {
                    val recent = HistoryStore.recent(10)
                    if (recent.isNotEmpty()) out.add(SuggestItem(L.t("Legutóbb megnyitott"), null, null, 0, kind = 0))
                    for (e in recent) {
                        val host = Urls.host(e.url)
                        out.add(SuggestItem(e.title.ifBlank { host }, e.url, e.url, R.drawable.ic_history))
                    }
                } else {
                    out.add(SuggestItem(engineTitle(), null, null, 0, kind = 0))
                    out.add(SuggestItem(q, null, null, R.drawable.ic_search, kind = 1))
                    // összesen 3 keresési sor
                    for (text in onlineItems) {
                        if (out.size >= 4) break
                        if (!text.equals(q, ignoreCase = true)) {
                            out.add(SuggestItem(text, null, null, R.drawable.ic_search, kind = 1))
                        }
                    }
                    val sites = ArrayList<SuggestItem>()
                    // nyitott lapok: koppintásra arra a lapra váltunk
                    for (t in tabs) {
                        if (t === activeTab || Urls.isStart(t.url)) continue
                        if (t.title.contains(q, true) || t.url.contains(q, true)) {
                            sites.add(SuggestItem(t.title.ifBlank { Urls.host(t.url) }, L.t("Váltás erre a lapra"), t.url, 0, tab = t))
                        }
                        if (sites.size >= 3) break
                    }
                    for (b in BookmarkStore.list(q).take(3)) {
                        if (sites.none { it.url == b.url }) {
                            sites.add(SuggestItem(b.title.ifBlank { Urls.host(b.url) }, b.url, b.url, R.drawable.ic_bookmark))
                        }
                    }
                    for (e in HistoryStore.search(q, 6)) {
                        if (sites.none { it.url == e.url } && sites.size < 8) {
                            sites.add(SuggestItem(e.title.ifBlank { Urls.host(e.url) }, e.url, e.url, R.drawable.ic_history))
                        }
                    }
                    if (sites.isNotEmpty()) {
                        out.add(SuggestItem(L.t("Javaslatok"), null, null, 0, kind = 0))
                        out.addAll(sites)
                    }
                }
            }
        }
        return out
    }

    /** A keresőmotor neve a javaslatok fölött (pl. "Google keresés" / "Google Search"). */
    private fun engineTitle(): String {
        val engine = prefs.getString("search_engine", DEFAULT_SEARCH) ?: DEFAULT_SEARCH
        val host = Uri.parse(engine).host.orEmpty()
        val name = when {
            "google" in host -> "Google"
            "bing" in host -> "Bing"
            "duckduckgo" in host -> "DuckDuckGo"
            "brave" in host -> "Brave"
            "startpage" in host -> "Startpage"
            else -> host.removePrefix("www.").substringBefore('.').replaceFirstChar { it.uppercase() }
        }
        return if (L.en) "$name Search" else "$name keresés"
    }

    /** Címsor a kereső felület szakaszai fölött. */
    private fun makeHeaderRow(): TextView {
        val tv = TextView(this)
        tv.textSize = 14f
        tv.setPadding(dp(22), dp(16), dp(22), dp(6))
        tv.tag = "h"
        return tv
    }

    /** Keresési javaslat: nagyító, a szöveg, jobbra a "mezőbe írás" nyíl (mint a Firefoxban). */
    private fun makeQueryRow(): LinearLayout {
        val row = LinearLayout(this)
        row.tag = "q"
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.CENTER_VERTICAL
        row.setPadding(dp(22), 0, dp(8), 0)
        val ta = obtainStyledAttributes(intArrayOf(android.R.attr.selectableItemBackground))
        row.background = ta.getDrawable(0)
        ta.recycle()
        val icon = ImageView(this)
        row.addView(icon, LinearLayout.LayoutParams(dp(22), dp(22)))
        val title = TextView(this)
        title.textSize = 16.5f
        title.maxLines = 1
        title.ellipsize = TextUtils.TruncateAt.END
        val lp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        lp.marginStart = dp(18)
        row.addView(title, lp)
        val fill = ImageButton(this)
        fill.setImageResource(R.drawable.ic_fill)
        fill.background = null
        fill.setPadding(dp(12), dp(12), dp(12), dp(12))
        fill.scaleType = ImageView.ScaleType.FIT_CENTER
        row.addView(fill, LinearLayout.LayoutParams(dp(46), dp(46)))
        return row
    }

    private fun makeSearchRow(): LinearLayout {
        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.CENTER_VERTICAL
        row.setPadding(dp(22), 0, dp(22), 0)
        val ta = obtainStyledAttributes(intArrayOf(android.R.attr.selectableItemBackground))
        row.background = ta.getDrawable(0)
        ta.recycle()

        row.tag = "s"
        val icon = ImageView(this)
        icon.scaleType = ImageView.ScaleType.FIT_CENTER
        icon.outlineProvider = object : android.view.ViewOutlineProvider() {
            override fun getOutline(v: View, o: android.graphics.Outline) {
                o.setRoundRect(0, 0, v.width, v.height, dp(6).toFloat())
            }
        }
        icon.clipToOutline = true
        row.addView(icon, LinearLayout.LayoutParams(dp(26), dp(26)))

        val texts = LinearLayout(this)
        texts.orientation = LinearLayout.VERTICAL
        val title = TextView(this)
        title.textSize = 16f
        title.maxLines = 1
        title.ellipsize = TextUtils.TruncateAt.END
        texts.addView(title)
        val sub = TextView(this)
        sub.textSize = 12.5f
        sub.maxLines = 1
        sub.ellipsize = TextUtils.TruncateAt.END
        texts.addView(sub)
        val lp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        lp.marginStart = dp(16)
        row.addView(texts, lp)
        return row
    }

    /**
     * Kirajzolja a kereső felület sorait. A meglévő sorokat újrahasznosítja; csak azok a sorok
     * "úsznak be" (alulról, egymás után), amelyeknek a tartalma megváltozott, így gépelés közben
     * a lista él, de nem villog.
     */
    private fun renderSearch() {
        if (!searchShown) return
        val items = searchItems()
        val text = pageTextColor()
        val dim = ColorUtils.setAlphaComponent(text, 150)
        val keys = ArrayList<String>(items.size)

        if (items.isEmpty()) {
            searchList.removeAllViews()
            val hint = TextView(this)
            hint.text = when (searchTab) {
                1 -> L.t("Még nincs könyvjelző. Egy oldalt a ⋯ menü „Könyvjelző hozzáadása” pontjával menthetsz el.")
                2 -> L.t("Nincs találat az előzményekben.")
                else -> L.t("Írj be egy webcímet vagy keresőszót.")
            }
            hint.textSize = 15f
            hint.setTextColor(dim)
            hint.setPadding(dp(24), dp(22), dp(24), dp(22))
            searchList.addView(hint)
            searchKeys = emptyList()
            return
        }
        // ha az előbb a tájékoztató szöveg volt itt, azt levesszük
        if (searchList.childCount > 0 && searchList.getChildAt(0).tag == null) searchList.removeAllViews()

        var animated = 0
        for ((i, item) in items.withIndex()) {
            val key = item.kind.toString() + "|" + item.icon + "|" + item.text + "|" + item.url
            keys.add(key)
            val type = when (item.kind) {
                0 -> "h"
                1 -> "q"
                else -> "s"
            }
            var row: View? = searchList.getChildAt(i)
            if (row == null || row.tag != type) {
                if (row != null) searchList.removeViewAt(i)
                row = when (type) {
                    "h" -> makeHeaderRow()
                    "q" -> makeQueryRow()
                    else -> makeSearchRow()
                }
                val h = when (type) {
                    "h" -> ViewGroup.LayoutParams.WRAP_CONTENT
                    "q" -> dp(52)
                    else -> dp(62)
                }
                searchList.addView(row, i, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, h))
            }
            when (type) {
                "h" -> {
                    val tv = row as TextView
                    tv.text = item.text
                    tv.setTextColor(dim)
                }
                "q" -> {
                    val r = row as LinearLayout
                    val icon = r.getChildAt(0) as ImageView
                    icon.setImageResource(R.drawable.ic_search)
                    icon.imageTintList = ColorStateList.valueOf(dim)
                    val title = r.getChildAt(1) as TextView
                    title.text = item.text
                    title.setTextColor(text)
                    val fill = r.getChildAt(2) as ImageButton
                    fill.imageTintList = ColorStateList.valueOf(dim)
                    fill.setOnClickListener {
                        // a javaslat a mezőbe kerül, lehet tovább gépelni
                        haptic()
                        urlField.setText(item.text + " ")
                        urlField.setSelection(urlField.text.length)
                    }
                    r.setOnClickListener {
                        activeTab?.stayInBrowser = true
                        activeWebView?.loadUrl(resolveInput(item.text))
                        releaseFocus()
                    }
                }
                else -> {
                    val r = row as LinearLayout
                    val icon = r.getChildAt(0) as ImageView
                    val texts = r.getChildAt(1) as LinearLayout
                    val title = texts.getChildAt(0) as TextView
                    title.text = item.text
                    title.setTextColor(text)
                    val sub = texts.getChildAt(1) as TextView
                    sub.text = item.sub.orEmpty()
                    sub.setTextColor(dim)
                    sub.visibility = if (item.sub != null) View.VISIBLE else View.GONE
                    // színes webhely-ikon; amíg nem tölt be (vagy ha nincs), egy földgömb
                    val host = Uri.parse(item.url.orEmpty()).host.orEmpty()
                    val cachedIcon = Favicons.cached(host)
                    if (cachedIcon != null) {
                        icon.imageTintList = null
                        icon.setImageBitmap(cachedIcon)
                    } else {
                        icon.setImageResource(R.drawable.ic_globe)
                        icon.imageTintList = ColorStateList.valueOf(dim)
                        icon.tag = host
                        Favicons.load(host) { bmp ->
                            if (bmp != null && icon.tag == host) {
                                icon.imageTintList = null
                                icon.setImageBitmap(bmp)
                                icon.alpha = 0f
                                icon.animate().alpha(1f).setDuration(160).start()
                            }
                        }
                    }
                    val switchTo = item.tab
                    r.setOnClickListener {
                        if (switchTo != null && switchTo in tabs) {
                            releaseFocus()
                            selectTab(switchTo)
                        } else {
                            activeTab?.stayInBrowser = true
                            activeWebView?.loadUrl(item.url ?: resolveInput(item.text))
                            releaseFocus()
                        }
                    }
                    val url = item.url
                    if (searchTab == 1 && url != null) {
                        r.setOnLongClickListener {
                            BookmarkStore.remove(applicationContext, url)
                            Toast.makeText(this, L.t("Könyvjelző törölve"), Toast.LENGTH_SHORT).show()
                            renderSearch()
                            true
                        }
                    } else {
                        r.setOnLongClickListener(null)
                        r.isLongClickable = false
                    }
                }
            }

            if (searchKeys.getOrNull(i) != key && animated < 14) {
                row.animate().cancel()
                row.alpha = 0f
                row.translationY = dp(14).toFloat()
                row.animate().alpha(1f).translationY(0f)
                    .setStartDelay(animated * 22L).setDuration(240)
                    .setInterpolator(DecelerateInterpolator(1.6f)).start()
                animated++
            }
        }
        while (searchList.childCount > items.size) searchList.removeViewAt(searchList.childCount - 1)
        searchKeys = keys
    }

    // ---------------------------------------------------------------- hárompontos menü

    private fun rectOf(v: View, inside: View): RectF {
        val a = IntArray(2)
        val b = IntArray(2)
        v.getLocationInWindow(a)
        inside.getLocationInWindow(b)
        val l = (a[0] - b[0]).toFloat()
        val t = (a[1] - b[1]).toFloat()
        return RectF(l, t, l + v.width, t + v.height)
    }

    private fun menuRow(text: String, enabled: Boolean = true, icon: Int = 0, check: Boolean = false, action: () -> Unit) {
        val tv = TextView(this)
        tv.text = text
        tv.textSize = 16.5f
        tv.tag = if (enabled) 1f else 0.38f      // a szín szorzója, lásd tintTree
        tv.setPadding(dp(18), dp(13), dp(22), dp(13))
        tv.gravity = Gravity.CENTER_VERTICAL
        tv.compoundDrawablePadding = dp(16)
        val sz = dp(20)
        val start = if (icon != 0) ContextCompat.getDrawable(this, icon)?.mutate()?.apply { setBounds(0, 0, sz, sz) } else null
        val end = if (check) ContextCompat.getDrawable(this, R.drawable.ic_m_check)?.mutate()?.apply { setBounds(0, 0, sz, sz) } else null
        tv.setCompoundDrawablesRelative(start, null, end, null)
        tv.isEnabled = enabled
        val ta = obtainStyledAttributes(intArrayOf(android.R.attr.selectableItemBackground))
        tv.background = ta.getDrawable(0)
        ta.recycle()
        tv.setOnClickListener {
            hideMenu()
            action()
        }
        menuItems.addView(
            tv,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        )
    }

    /** Megnyitja a kereső felületet a megadott fülön (1 = könyvjelzők, 2 = előzmények). */
    private fun openSearchTab(index: Int) {
        urlField.requestFocus()
        urlField.post { selectSearchTab(index, animate = true) }
    }

    /** A rögzített sáv jobb szélső menügombja (mint a Chrome-ban). */
    private lateinit var dockMenuBtn: ImageButton

    private fun setupDockMenuButton() {
        val b = ImageButton(this)
        b.setImageResource(R.drawable.ic_more)
        b.background = null
        b.contentDescription = L.t("Menü")
        b.setOnClickListener { if (menuVisible) hideMenu() else showMenu() }
        b.visibility = View.GONE
        barRow.addView(b, LinearLayout.LayoutParams(dp(44), ViewGroup.LayoutParams.MATCH_PARENT))
        dockMenuBtn = b
        b.imageTintList = ColorStateList.valueOf(contentColor ?: Color.WHITE)
        applyDockLayout()
    }

    /** Ahonnan a menü kinyílik: lebegő sávnál a kapszula menügombja, rögzítettnél a jobb szélső. */
    private fun menuAnchor(): View =
        if (docked && ::dockMenuBtn.isInitialized) dockMenuBtn else btnMenu

    /**
     * Rögzített sáv, Chrome-elrendezés: [ cím mező (kapszula) ] [ lapok ] [ ⋮ ]. A vissza gomb
     * nincs a sávon (a rendszer vissza gesztusa azt intézi), a lapváltóban viszont bal oldalt
     * megjelenik az új lap (+) gomb. Lebegő sávnál minden a régi helyén van.
     */
    private fun applyDockLayout() {
        if (!::dockMenuBtn.isInitialized) return
        if (docked) {
            // bal szélen kezdőlap gomb (a lapváltóban helyette az új lap gomb)
            barLeft.visibility = View.VISIBLE
            iconBack.setImageResource(R.drawable.ic_home)
            (barLeft.layoutParams as LinearLayout.LayoutParams).width = dp(44)
            btnMenu.visibility = View.GONE
            btnReload.visibility = if (urlField.hasFocus()) View.VISIBLE else View.GONE
            dockMenuBtn.visibility = if (barOverview) View.INVISIBLE else View.VISIBLE
            (barCenter.layoutParams as LinearLayout.LayoutParams).marginStart = dp(2)
            browseRow.setPadding(dp(14), 0, dp(6), 0)
            if (!urlField.hasFocus()) urlField.gravity = Gravity.CENTER_VERTICAL or Gravity.START
        } else {
            barLeft.visibility = View.VISIBLE
            iconBack.setImageResource(R.drawable.ic_back)
            (barLeft.layoutParams as LinearLayout.LayoutParams).width = dp(54)
            btnMenu.visibility = View.VISIBLE
            btnReload.visibility = View.VISIBLE
            dockMenuBtn.visibility = View.GONE
            (barCenter.layoutParams as LinearLayout.LayoutParams).marginStart = dp(8)
            browseRow.setPadding(dp(6), 0, dp(6), 0)
            if (!urlField.hasFocus()) urlField.gravity = Gravity.CENTER
        }
        barCenter.requestLayout()
    }

    // ---------------------------------------------------------------- Google Fordító

    private fun translateTarget(): String {
        val v = prefs.getString("translate_lang", "auto") ?: "auto"
        return if (v == "auto") (if (L.en) "en" else "hu") else v
    }

    /** A Google Fordító "*.translate.goog" címen mutatja a lefordított oldalt. */
    private fun isTranslated(url: String): Boolean =
        Uri.parse(url).host?.endsWith(".translate.goog") == true

    /** A fordítás sávja (a Chrome "Lefordítod az oldalt?" sávjához hasonló). */
    private var trBar: LinearLayout? = null
    private var trTitle: TextView? = null
    private var trSub: TextView? = null
    private var trAction: TextView? = null
    private var trTab: Tab? = null
    private var trFrom = ""
    /** Webhelyek, ahol ebben a munkamenetben bezártad az ajánlatot (ott nem kérdezünk újra). */
    private val trDismissed = HashSet<String>()

    private fun langName(code: String): String {
        val ui = java.util.Locale(if (L.en) "en" else "hu")
        val n = java.util.Locale(code).getDisplayLanguage(ui)
        return if (n.isBlank() || n == code) code.uppercase() else n.replaceFirstChar { it.uppercase() }
    }

    /** Az oldal betöltése után: ha más nyelvű, mint a célnyelv, felajánljuk a fordítást. */
    private fun offerTranslate(tab: Tab, wv: WebView) {
        if (!prefs.getBoolean("translate_offer", true)) return
        val url = tab.url
        if (Urls.isStart(url) || isTranslated(url) || tab.translated) return
        val host = Urls.host(url)
        if (host in trDismissed) return
        wv.evaluateJavascript(
            "(function(){var l=(document.documentElement.lang||'').toLowerCase();" +
                "if(!l){var m=document.querySelector('meta[http-equiv=\"content-language\" i]');if(m)l=(m.content||'').toLowerCase();}" +
                "return l.split(/[-_,;\\s]/)[0];})()"
        ) { res ->
            val lang = res?.trim('"').orEmpty()
            if (lang.length !in 2..3 || tab !== activeTab || tab.translated) return@evaluateJavascript
            if (lang == translateTarget()) return@evaluateJavascript
            trFrom = lang
            showTranslateBar(tab, translating = false, done = false)
        }
    }

    /**
     * Üvegbuborék háttér: az üveg "bőre" (a többi felülettel együtt állítódik) és rajta egy enyhe
     * dér, hogy a szöveg bármilyen oldal fölött olvasható legyen.
     */
    private fun glassBubble(radius: Float): android.graphics.drawable.Drawable {
        val skin = GlassSkinDrawable(resources.displayMetrics.density)
        skin.cornerRadius = radius
        skins.add(skin)
        applyGlassMode()
        val frost = GradientDrawable()
        frost.cornerRadius = radius
        frost.setColor(if (night) Color.argb(90, 20, 22, 28) else Color.argb(120, 255, 255, 255))
        return android.graphics.drawable.LayerDrawable(arrayOf(skin, frost))
    }

    private var copyToast: TextView? = null

    /** Kis üvegkapszula a sáv fölött: "Vágólapra másolva", rugózva jelenik meg és tűnik el. */
    private fun showCopyToast() {
        val tv = copyToast ?: TextView(this).also { t ->
            t.text = L.t("Vágólapra másolva")
            t.textSize = 14.5f
            t.setTypeface(t.typeface, android.graphics.Typeface.BOLD)
            t.setPadding(dp(18), dp(10), dp(18), dp(10))
            t.gravity = Gravity.CENTER
            t.compoundDrawablePadding = dp(8)
            val ic = ContextCompat.getDrawable(this, R.drawable.ic_m_check)?.mutate()
            ic?.setBounds(0, 0, dp(18), dp(18))
            t.setCompoundDrawablesRelative(ic, null, null, null)
            t.background = glassBubble(dp(100).toFloat())
            val lp = FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            lp.gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            root.addView(t, lp)
            t.visibility = View.GONE
            copyToast = t
        }
        tv.text = L.t("Vágólapra másolva")
        val col = if (night) Color.WHITE else 0xFF1D1F24.toInt()
        tv.setTextColor(col)
        tv.compoundDrawableTintList = ColorStateList.valueOf(col)
        // a keresősáv fölött jelenik meg
        val lp = tv.layoutParams as FrameLayout.LayoutParams
        val barTop = if (dockTop) root.height - lastBottom else (barRow.top + barRow.translationY).toInt()
        lp.bottomMargin = (root.height - barTop + dp(12)).coerceAtLeast(dp(24))
        tv.layoutParams = lp
        tv.removeCallbacks(copyToastHide)
        tv.animate().cancel()
        tv.visibility = View.VISIBLE
        tv.alpha = 0f
        tv.scaleX = 0.6f
        tv.scaleY = 0.6f
        tv.translationY = dp(10).toFloat()
        tv.animate().alpha(1f).scaleX(1f).scaleY(1f).translationY(0f).setDuration(380)
            .setInterpolator(OvershootInterpolator(2f))
            .setUpdateListener { updateLens() }.start()
        tv.postDelayed(copyToastHide, 1500L)
    }

    private val copyToastHide = Runnable {
        val tv = copyToast ?: return@Runnable
        tv.animate().cancel()
        tv.animate().alpha(0f).scaleX(0.85f).scaleY(0.85f).translationY(dp(6).toFloat()).setDuration(240)
            .setInterpolator(DecelerateInterpolator())
            .setUpdateListener { updateLens() }
            .withEndAction {
                tv.visibility = View.GONE
                updateLens()
            }.start()
    }

    private fun ensureTranslateBar(): LinearLayout {
        trBar?.let { return it }
        val text = if (night) Color.WHITE else 0xFF1D1F24.toInt()
        val bar = LinearLayout(this)
        bar.orientation = LinearLayout.HORIZONTAL
        bar.gravity = Gravity.CENTER_VERTICAL
        bar.setPadding(dp(14), dp(8), dp(6), dp(8))
        // üveg buborék (a többi üvegfelülettel együtt változik, kikapcsolt üvegnél tömör)
        bar.background = glassBubble(dp(20).toFloat())
        bar.isClickable = true
        val icon = ImageView(this)
        icon.setImageResource(R.drawable.ic_m_translate)
        icon.imageTintList = ColorStateList.valueOf(0xFF0A84FF.toInt())
        bar.addView(icon, LinearLayout.LayoutParams(dp(22), dp(22)))
        val texts = LinearLayout(this)
        texts.orientation = LinearLayout.VERTICAL
        val t = TextView(this)
        t.textSize = 15f
        t.setTextColor(text)
        t.setTypeface(t.typeface, android.graphics.Typeface.BOLD)
        val sub = TextView(this)
        sub.textSize = 12.5f
        sub.setTextColor(ColorUtils.setAlphaComponent(text, 150))
        texts.addView(t)
        texts.addView(sub)
        val tlp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        tlp.marginStart = dp(12)
        bar.addView(texts, tlp)
        val action = TextView(this)
        action.textSize = 15f
        action.setTextColor(0xFF0A84FF.toInt())
        action.setTypeface(action.typeface, android.graphics.Typeface.BOLD)
        action.setPadding(dp(12), dp(10), dp(12), dp(10))
        bar.addView(action)
        val close = ImageButton(this)
        close.setImageResource(R.drawable.ic_close)
        close.background = null
        close.imageTintList = ColorStateList.valueOf(ColorUtils.setAlphaComponent(text, 160))
        close.setPadding(dp(10), dp(10), dp(10), dp(10))
        close.scaleType = ImageView.ScaleType.FIT_CENTER
        close.setOnClickListener {
            trTab?.let { trDismissed.add(Urls.host(it.url)) }
            hideTranslateBar()
        }
        bar.addView(close, LinearLayout.LayoutParams(dp(40), dp(40)))
        val lp = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        lp.gravity = Gravity.TOP
        lp.leftMargin = dp(10)
        lp.rightMargin = dp(10)
        root.addView(bar, lp)
        bar.visibility = View.GONE
        bar.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> updateLens() }
        trBar = bar
        trTitle = t
        trSub = sub
        trAction = action
        return bar
    }

    private fun showTranslateBar(tab: Tab, translating: Boolean, done: Boolean) {
        val bar = ensureTranslateBar()
        trTab = tab
        val target = translateTarget()
        val from = if (trFrom.isNotEmpty()) langName(trFrom) else ""
        trSub?.text = if (from.isNotEmpty()) "$from → ${langName(target)}" else "→ ${langName(target)}"
        trTitle?.text = when {
            done -> L.t("Oldal lefordítva")
            translating -> L.t("Fordítás…")
            else -> L.t("Lefordítod az oldalt?")
        }
        trAction?.text = when {
            done -> L.t("Visszavonás")
            translating -> ""
            else -> L.t("Fordítás")
        }
        trAction?.setOnClickListener {
            haptic()
            if (done) undoTranslate() else startTranslate()
        }
        (bar.layoutParams as FrameLayout.LayoutParams).topMargin =
            (if (dockTop) statusTop + dockHeight() else statusTop) + dp(6)
        bar.requestLayout()
        if (bar.visibility != View.VISIBLE) {
            bar.visibility = View.VISIBLE
            bar.alpha = 0f
            bar.translationY = -dp(16).toFloat()
            bar.scaleX = 0.92f
            bar.scaleY = 0.92f
            bar.animate().alpha(1f).translationY(0f).scaleX(1f).scaleY(1f).setDuration(320)
                .setInterpolator(OvershootInterpolator(1.3f))
                .setUpdateListener { updateLens() }.start()
        }
        bar.removeCallbacks(trAutoHide)
        // a "lefordítva" sáv pár másodperc múlva magától eltűnik
        if (done) bar.postDelayed(trAutoHide, 4000L)
    }

    private val trAutoHide = Runnable { hideTranslateBar() }

    private fun hideTranslateBar() {
        val bar = trBar ?: return
        bar.removeCallbacks(trAutoHide)
        if (bar.visibility != View.VISIBLE) return
        bar.animate().cancel()
        bar.animate().alpha(0f).translationY(-dp(12).toFloat()).scaleX(0.94f).scaleY(0.94f).setDuration(200)
            .setUpdateListener { updateLens() }
            .withEndAction {
                bar.visibility = View.GONE
                updateLens()
            }.start()
    }

    /**
     * Fordítás a helyén, valós időben (mint a Chrome-ban): az oldal szövegeit összegyűjtjük, a
     * Google Fordítóval részletekben lefordíttatjuk, és a szövegeket a helyükön cseréljük. Nincs
     * újratöltés, a webcím és a görgetés marad; a visszavonás az eredeti szövegeket teszi vissza.
     */
    private fun startTranslate() {
        val tab = activeTab ?: return
        val wv = tab.webView
        val url = tab.url
        if (url.isBlank() || Urls.isStart(url) || tab.trPending) return
        val target = translateTarget()
        showTranslateBar(tab, translating = true, done = false)
        tab.trPending = true
        val seq = ++trSeq
        wv.evaluateJavascript(TR_COLLECT_JS) { res ->
            if (seq != trSeq || !tab.trPending) return@evaluateJavascript
            val texts = try {
                val str = org.json.JSONTokener(res).nextValue() as? String
                JSONArray(str)
            } catch (e: Exception) {
                null
            }
            if (texts == null || texts.length() == 0) {
                onTranslateResult(tab, false)
                return@evaluateJavascript
            }
            translateBatches(tab, seq, texts, target)
        }
    }

    private var trSeq = 0

    /** Részletek (kb. 3500 karakter) párhuzamosan; amelyik megjön, azonnal a helyére kerül. */
    private fun translateBatches(tab: Tab, seq: Int, texts: JSONArray, target: String) {
        val batches = ArrayList<Pair<Int, List<String>>>()
        var start = 0
        var cur = ArrayList<String>()
        var len = 0
        for (k in 0 until texts.length()) {
            val t = texts.optString(k)
            if (cur.isNotEmpty() && (len + t.length > 3500 || cur.size >= 120)) {
                batches.add(start to cur)
                start = k
                cur = ArrayList()
                len = 0
            }
            cur.add(t)
            len += t.length
        }
        if (cur.isNotEmpty()) batches.add(start to cur)
        var remaining = batches.size
        var okCount = 0
        for ((from, list) in batches) {
            trPool.execute {
                val out = googleTranslate(list, target)
                handler.post {
                    if (seq != trSeq || !tab.trPending) return@post
                    if (out != null) {
                        okCount++
                        if (out.second.isNotEmpty() && trFrom.isEmpty()) {
                            trFrom = out.second
                            if (tab === activeTab) showTranslateBar(tab, translating = true, done = false)
                        }
                        tab.webView.evaluateJavascript(
                            "window.__gtApply&&window.__gtApply($from," + JSONArray(out.first).toString() + ");", null
                        )
                    }
                    remaining--
                    if (remaining == 0) onTranslateResult(tab, okCount > 0)
                }
            }
        }
    }

    private val trPool = java.util.concurrent.Executors.newFixedThreadPool(3)

    /** Egy részlet fordítása. Visszaadja a fordításokat és a felismert forrásnyelvet. */
    private fun googleTranslate(list: List<String>, target: String): Pair<List<String>, String>? {
        return try {
            val url = java.net.URL("https://translate.googleapis.com/translate_a/t?client=gtx&sl=auto&tl=$target&dj=0")
            val c = url.openConnection() as java.net.HttpURLConnection
            c.requestMethod = "POST"
            c.doOutput = true
            c.connectTimeout = 8000
            c.readTimeout = 12000
            c.setRequestProperty("Content-Type", "application/x-www-form-urlencoded;charset=UTF-8")
            val body = list.joinToString("&") { "q=" + java.net.URLEncoder.encode(it, "UTF-8") }
            c.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            if (c.responseCode != 200) {
                c.disconnect()
                return null
            }
            val txt = c.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            c.disconnect()
            val arr = JSONArray(txt)
            val res = ArrayList<String>(list.size)
            var lang = ""
            if (list.size == 1 && arr.length() >= 1 && arr.opt(0) is String) {
                // egyetlen szövegnél: ["fordítás", "nyelv"]
                res.add(arr.optString(0))
                lang = arr.optString(1)
            } else {
                for (k in 0 until arr.length()) {
                    val e = arr.opt(k)
                    if (e is JSONArray) {
                        res.add(e.optString(0))
                        if (lang.isEmpty()) lang = e.optString(1)
                    } else {
                        res.add(e?.toString().orEmpty())
                    }
                }
            }
            if (res.size != list.size) null else res to lang.substringBefore('-')
        } catch (e: Exception) {
            null
        }
    }

    private fun onTranslateResult(tab: Tab, ok: Boolean) {
        if (!tab.trPending) return
        tab.trPending = false
        if (ok) {
            tab.translated = true
            if (tab === activeTab) showTranslateBar(tab, translating = false, done = true)
        } else if (tab === activeTab) {
            hideTranslateBar()
            // tartalék: a Google Fordító lefordított oldala
            translatePage(tab.url)
        }
    }

    /** Vissza az eredeti szövegekre, újratöltés nélkül. */
    private fun undoTranslate() {
        val tab = activeTab ?: return
        trSeq++
        tab.trPending = false
        hideTranslateBar()
        if (isTranslated(tab.url)) {
            showOriginal()
        } else {
            tab.translated = false
            trDismissed.add(Urls.host(tab.url))
            tab.webView.evaluateJavascript("window.__gtUndo&&window.__gtUndo();", null)
        }
    }

    private fun translatePage(url: String) {
        if (url.isBlank() || Urls.isStart(url)) return
        val target = translateTarget()
        val t = "https://translate.google.com/translate?sl=auto&tl=$target&hl=$target&u=" + Uri.encode(url)
        activeTab?.let { it.stayInBrowser = true }
        activeWebView?.loadUrl(t)
    }

    /** Vissza az eredeti oldalra: a fordító címéből visszaállítjuk az eredeti webcímet. */
    private fun showOriginal() {
        val wv = activeWebView ?: return
        val u = Uri.parse(wv.url.orEmpty())
        val host = u.host.orEmpty().removeSuffix(".translate.goog")
        // a fordító a pontokat kötőjelre, a kötőjeleket dupla kötőjelre cseréli
        val original = host.replace("--", "\u0000").replace('-', '.').replace("\u0000", "-")
        val b = Uri.Builder().scheme("https").authority(original).encodedPath(u.encodedPath)
        val q = u.queryParameterNames.filterNot { it.startsWith("_x_tr_") }
        q.forEach { k -> u.getQueryParameters(k).forEach { v -> b.appendQueryParameter(k, v) } }
        u.encodedFragment?.let { b.encodedFragment(it) }
        wv.loadUrl(b.build().toString())
    }

    private var urlCopied = false

    private fun menuTextColor(): Int = if (night) Color.WHITE else 0xFF1D1F24.toInt()

    private fun copyUrl() {
        val url = currentUrl
        if (url.isBlank() || Urls.isStart(url)) return
        haptic()
        val cm = getSystemService(android.content.ClipboardManager::class.java) ?: return
        cm.setPrimaryClip(android.content.ClipData.newPlainText("URL", url))
        showCopyToast()
    }

    private fun showMenu() {
        if (menuVisible || overviewShown || overviewBusy) return
        showDock(animate = false)
        releaseFocus()
        setCollapsed(false)
        glassFormOpen = false
        menuItems.scaleX = 1f
        menuItems.scaleY = 1f
        menuItems.setPadding(0, dp(8), 0, dp(8))
        menuItems.layoutParams = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
        )
        menuPanel.layoutParams = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
        )
        menuItems.removeAllViews()
        menuRow(L.t("Új lap"), icon = R.drawable.ic_m_add) { openNewTab(null, focusField = true) }
        menuRow(L.t("Előre"), activeWebView?.canGoForward() == true, icon = R.drawable.ic_m_forward) {
            activeWebView?.goForward()
        }
        if (docked) {
            // a rögzített sávon nincs külön frissítés gomb (mint a Chrome-ban, a menüben van)
            menuRow(L.t("Újratöltés"), activeTab != null, icon = R.drawable.ic_m_reload) {
                activeTab?.let { if (it.loading) it.webView.stopLoading() else it.webView.reload() }
            }
        }
        menuRow(L.t("Megosztás"), !Urls.isStart(currentUrl), icon = R.drawable.ic_m_share) {
            val send = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, currentUrl)
            }
            startActivity(Intent.createChooser(send, null))
        }
        val pageUrl = currentUrl
        val canMark = !Urls.isStart(pageUrl)
        if (canMark && BookmarkStore.has(pageUrl)) {
            menuRow(L.t("Könyvjelző eltávolítása"), icon = R.drawable.ic_m_star) {
                BookmarkStore.remove(applicationContext, pageUrl)
                Toast.makeText(this, L.t("Könyvjelző eltávolítva"), Toast.LENGTH_SHORT).show()
            }
        } else {
            menuRow(L.t("Könyvjelző hozzáadása"), canMark, icon = R.drawable.ic_m_star_border) {
                BookmarkStore.add(applicationContext, pageUrl, activeTab?.title.orEmpty())
                Toast.makeText(this, L.t("Könyvjelző mentve"), Toast.LENGTH_SHORT).show()
            }
        }
        // Google Fordító: az oldal a helyén fordul le (mint a Chrome-ban), vissza is állítható
        val translated = activeTab?.translated == true || isTranslated(pageUrl)
        menuRow(
            if (translated) L.t("Eredeti oldal") else L.t("Oldal fordítása"),
            canMark, icon = R.drawable.ic_m_translate
        ) {
            if (translated) undoTranslate() else startTranslate()
        }
        val desktop = prefs.getBoolean("desktop_mode", false)
        menuRow(L.t("Asztali oldal"), icon = R.drawable.ic_m_desktop, check = desktop) {
            prefs.edit().putBoolean("desktop_mode", !desktop).apply()
            applyAllSettings()
        }
        menuRow(L.t("Beállítások"), icon = R.drawable.ic_m_settings) { openSettings() }
        // Olvashatóság: az üveg alá enyhe dér kerül, a szöveg a világos / sötét módhoz igazodik
        // (így világos és sötét oldal fölött is mindig látszik).
        menuPanel.background = if (lensActive) GradientDrawable().apply {
            cornerRadius = dp(26).toFloat()
            setColor(if (night) Color.argb(110, 22, 24, 30) else Color.argb(150, 250, 250, 252))
        } else null
        tintTree(menuItems, menuTextColor())

        // a panel a menügomb mellé kerül: lebegő sávnál a középső kapszula bal széle fölé,
        // rögzített sávnál (mint a Chrome-ban) a jobb szélső menügombhoz
        visualRect(if (docked) menuAnchor() else barCenter, tmpRect)
        val lp = menuPanel.layoutParams as FrameLayout.LayoutParams
        lp.leftMargin = tmpRect.left.toInt().coerceAtLeast(0)
        lp.rightMargin = 0
        val hGravity = if (docked) Gravity.RIGHT else Gravity.LEFT
        if (docked) {
            lp.leftMargin = 0
            lp.rightMargin = (root.width - tmpRect.right).toInt().coerceAtLeast(dp(6))
        }
        if (dockTop) {
            // felül rögzített sávnál a menü lefelé nyílik
            lp.gravity = Gravity.TOP or hGravity
            lp.topMargin = tmpRect.bottom.toInt() + dp(8)
            lp.bottomMargin = 0
        } else {
            lp.gravity = Gravity.BOTTOM or hGravity
            lp.topMargin = 0
            lp.bottomMargin = (root.height - tmpRect.top).toInt().coerceAtLeast(0) + dp(8)
        }
        menuPanel.layoutParams = lp

        menuAnim?.cancel()
        menuClip.update(0f, 0f, 0f, 0f, 0f)
        menuFrame(0f)
        menuScrim.visibility = View.VISIBLE
        menuPanel.visibility = View.VISIBLE
        // animáció közben a sorok egy rétegként mozognak (nem rajzolódnak újra képkockánként)
        menuItems.setLayerType(View.LAYER_TYPE_HARDWARE, null)
        menuPanel.doOnLayout {
            val origin = rectOf(menuAnchor(), menuPanel)
            menuPanel.pivotX = origin.centerX()
            menuPanel.pivotY = origin.centerY()
            menuAnim = menuClip.morph(
                opening = true,
                from = origin,
                fromRadius = menuAnchor().height / 2f,
                toRadius = dp(26).toFloat(),
                durationMs = 540,
                onFrame = { f -> menuFrame(f) },
                onEnd = {
                    menuClip.update(0f, 0f, menuPanel.width.toFloat(), menuPanel.height.toFloat(), dp(26).toFloat())
                    menuFrame(1f)
                    menuItems.setLayerType(View.LAYER_TYPE_NONE, null)
                },
                // gyorsan indul, majd hosszan, lágyan lassul (túllendülés nélkül, nem rángat)
                interpolator = android.view.animation.PathInterpolator(0.05f, 0.7f, 0.1f, 1f)
            )
        }
    }

    /**
     * A menü egy képkockája: a panel alakja a gombból "fújódik ki", a sorok együtt, simán
     * úsznak be.
     */
    private fun menuFrame(f: Float) {
        // a túllendülés a kivágáson (a menü alakján) látszik, a tartalom nem nagyítódik külön
        menuPanel.scaleX = 1f
        menuPanel.scaleY = 1f
        val c = f.coerceIn(0f, 1f)
        fun smooth(x: Float): Float {
            val t = x.coerceIn(0f, 1f)
            return t * t * (3f - 2f * t)
        }
        if (glassFormOpen) {
            // Üveg űrlap: a tartalom a csempe felé zsugorodik / onnan nő ki. Záráskor a szöveg már
            // az út elején eltűnik, az üveg pedig a csempéhez érve teljesen elhalványul, így nem
            // marad szellemkép és üres folt sem.
            menuGlassAlpha = if (formClosing) smooth(c / 0.55f) else smooth(c / 0.3f)
            val a = if (formClosing) smooth((c - 0.5f) / 0.5f) else smooth((c - 0.3f) / 0.55f)
            val sc = 0.55f + 0.45f * smooth(c)
            menuItems.scaleX = sc
            menuItems.scaleY = sc
            for (i in 0 until menuItems.childCount) {
                val row = menuItems.getChildAt(i)
                row.alpha = a
                row.translationY = 0f
            }
            updateLens()
            return
        }
        menuGlassAlpha = 1f
        // a tartalom egyben, simán úszik be (nem soronként, ami darabosnak hat)
        val a = ((c - 0.2f) / 0.6f).coerceIn(0f, 1f)
        val eased = a * a * (3f - 2f * a)
        // csak a sorok halványulnak; az üveg háttér végig látszik
        for (i in 0 until menuItems.childCount) {
            val row = menuItems.getChildAt(i)
            row.alpha = eased
            row.translationY = (1f - eased) * dp(10)
        }
        updateLens()
    }

    private fun hideMenu() {
        if (!menuVisible) return
        menuAnim?.cancel()
        menuScrim.visibility = View.GONE
        val form = glassFormOpen
        menuItems.setLayerType(View.LAYER_TYPE_HARDWARE, null)
        if (form) {
            releaseFocus()
            formClosing = true
            morphForm(opening = false) {
                formClosing = false
                menuItems.scaleX = 1f
                menuItems.scaleY = 1f
                menuPanel.visibility = View.GONE
                menuItems.setLayerType(View.LAYER_TYPE_NONE, null)
                glassFormOpen = false
                menuClip.update(0f, 0f, 0f, 0f, 0f)
                updateLens()
            }
            return
        }
        menuAnim = menuClip.morph(
            opening = false,
            from = rectOf(menuAnchor(), menuPanel),
            fromRadius = menuAnchor().height / 2f,
            toRadius = dp(26).toFloat(),
            durationMs = 380,
            onFrame = { f -> menuFrame(f) },
            onEnd = {
                menuPanel.visibility = View.GONE
                menuItems.setLayerType(View.LAYER_TYPE_NONE, null)
                updateLens()
            },
            // gyorsan indul, a gombnál lágyan érkezik
            interpolator = android.view.animation.PathInterpolator(0.3f, 0f, 0.1f, 1f)
        )
    }

    private fun releaseFocus() {
        root.requestFocus()
        WindowCompat.getInsetsController(window, root).hide(WindowInsetsCompat.Type.ime())
    }

    /** Az alsó betét (navigációs sáv vagy billentyűzet) fölé igazítja a keresősávot. */
    private fun applyBottomInset(bottom: Int) {
        lastBottom = bottom
        val barHeight = barRow.layoutParams.height
        // a kereső felület listája a sáv (és a billentyűzet) fölött ér véget
        // a lista a képernyő tetejéig ér (a fülek sávja és az állapotsor mögé is görgethető)
        val segArea = dp(10) + dp(46) + dp(8)
        val topOff = if (dockTop) statusTop + dockHeight() else statusTop
        if (dockTop) {
            searchColumn.setPadding(0, topOff, 0, bottom + dp(8))
            searchList.setPadding(0, segArea, 0, dp(12))
        } else {
            searchColumn.setPadding(0, 0, 0, bottom + dp(10) + barHeight + dp(8))
            searchList.setPadding(0, statusTop + segArea, 0, dp(12))
        }
        (segBar.layoutParams as? FrameLayout.LayoutParams)?.let {
            if (it.topMargin != topOff + dp(10)) {
                it.topMargin = topOff + dp(10)
                segBar.layoutParams = it
            }
        }
        searchShade?.let { sh ->
            val lp = sh.layoutParams
            val h = topOff + segArea + dp(26)
            if (lp.height != h) {
                lp.height = h
                sh.layoutParams = lp
            }
        }
        val barLp = barRow.layoutParams as FrameLayout.LayoutParams
        val dockLp = dockBg.layoutParams as FrameLayout.LayoutParams
        if (dockTop) {
            // felül: a sáv az állapotsor alatt, a háttere az állapotsor mögé is felér
            barLp.topMargin = statusTop + dp(4)
            barLp.bottomMargin = 0
            dockLp.gravity = Gravity.TOP
            dockLp.height = statusTop + dockHeight()
        } else {
            barLp.topMargin = 0
            barLp.bottomMargin = bottom + if (docked) dp(4) else dp(10)
            // alul: a háttér a navigációs sáv (vagy a billentyűzet) mögé is leér
            dockLp.gravity = Gravity.BOTTOM
            dockLp.height = bottom + dockHeight()
        }
        barRow.layoutParams = barLp
        dockBg.layoutParams = dockLp
    }

    private fun setupInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(root) { _, insets ->
            val status = insets.getInsets(WindowInsetsCompat.Type.statusBars())
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars()).bottom
            val bottom = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.ime()
            ).bottom
            statusTop = status.top

            // a készülék kijelzőjének saroksugara (ezt használja a lapváltó nagyító animációja)
            if (Build.VERSION.SDK_INT >= 31) {
                val corner = insets.toWindowInsets()?.getRoundedCorner(RoundedCorner.POSITION_TOP_LEFT)
                val radius = corner?.radius ?: 0
                if (radius > 0) screenRadius = radius.toFloat()
            }

            navBottom = bars
            // amíg a billentyűzet mozog, a sáv helyét az animáció adja (lásd lent)
            if (!imeAnimating) lastBottom = bottom
            applyLayoutInsets()
            WindowInsetsCompat.CONSUMED
        }

        // A keresősáv a billentyűzettel együtt úszik fel és le, nem ugrik a helyére.
        ViewCompat.setWindowInsetsAnimationCallback(
            root,
            object : WindowInsetsAnimationCompat.Callback(WindowInsetsAnimationCompat.Callback.DISPATCH_MODE_STOP) {
                override fun onPrepare(animation: WindowInsetsAnimationCompat) {
                    if (animation.typeMask and WindowInsetsCompat.Type.ime() != 0) imeAnimating = true
                }

                override fun onProgress(
                    insets: WindowInsetsCompat,
                    runningAnimations: MutableList<WindowInsetsAnimationCompat>
                ): WindowInsetsCompat {
                    if (imeAnimating) {
                        applyBottomInset(
                            insets.getInsets(
                                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.ime()
                            ).bottom
                        )
                    }
                    return insets
                }

                override fun onEnd(animation: WindowInsetsAnimationCompat) {
                    if (animation.typeMask and WindowInsetsCompat.Type.ime() != 0) {
                        imeAnimating = false
                        ViewCompat.getRootWindowInsets(root)?.let {
                            applyBottomInset(
                                it.getInsets(
                                    WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.ime()
                                ).bottom
                            )
                        }
                    }
                }
            }
        )
        ViewCompat.requestApplyInsets(root)
    }

    // ---------------------------------------------------------------- navigáció

    /**
     * A kezdőlap 8 csempéje. Amíg nem szerkesztetted, a leggyakrabban látogatott oldalaid
     * (kiegészítve az alapértelmezettekkel); szerkesztés után a saját listád.
     */
    private fun startTiles(): MutableList<Pair<String, String>> {
        val saved = prefs.getString(KEY_SHORTCUTS, null)
        if (saved != null) {
            runCatching {
                val arr = JSONArray(saved)
                val list = mutableListOf<Pair<String, String>>()
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    list.add(o.optString("title") to o.optString("url"))
                }
                return list
            }
        }
        val list = mutableListOf<Pair<String, String>>()
        HistoryStore.topSites(8).forEach { e ->
            val h = Uri.parse(e.url).host ?: return@forEach
            list.add(tileName(e.url) to "https://$h")
        }
        DEFAULT_TILES.forEach { d ->
            if (list.size < 8 && list.none { Urls.host(it.second) == Urls.host(d.second) }) list.add(L.t(d.first) to d.second)
        }
        return list
    }

    private fun tileName(url: String): String =
        Urls.host(url).substringBefore('.').replaceFirstChar { it.uppercase() }

    private fun saveTiles(list: List<Pair<String, String>>?) {
        if (list == null) {
            prefs.edit().remove(KEY_SHORTCUTS).apply()
        } else {
            val arr = JSONArray()
            list.forEach { arr.put(JSONObject().put("title", it.first).put("url", it.second)) }
            prefs.edit().putString(KEY_SHORTCUTS, arr.toString()).apply()
        }
        // a nyitott kezdőlap azonnal frissül
        val wv = activeWebView ?: return
        if (Urls.isStart(wv.url.orEmpty())) wv.evaluateJavascript(startPageJs(), null)
    }

    /** A csempe ikonjának helye az ablakban (a kezdőlap CSS képpontban küldi a helyét). */
    private fun tileAnchor(view: WebView?, uri: Uri): RectF? {
        val wv = view ?: return null
        val vw = uri.getQueryParameter("vw")?.toFloatOrNull() ?: return null
        val l = uri.getQueryParameter("l")?.toFloatOrNull() ?: return null
        val t = uri.getQueryParameter("t")?.toFloatOrNull() ?: return null
        val r = uri.getQueryParameter("r")?.toFloatOrNull() ?: return null
        val b = uri.getQueryParameter("b")?.toFloatOrNull() ?: return null
        if (vw <= 0f) return null
        val k = wv.width / vw
        val loc = IntArray(2)
        wv.getLocationInWindow(loc)
        return RectF(loc[0] + l * k, loc[1] + t * k, loc[0] + r * k, loc[1] + b * k)
    }

    /** Csempe szerkesztése / hozzáadása / törlése ([arg]: a csempe sorszáma, "new" vagy "reset"). */
    private fun editTile(arg: String?) {
        haptic()
        if (arg == "reset") {
            formAnchor = null
            showGlassForm(
                title = L.t("Csempék visszaállítása"),
                message = L.t("A kezdőlapon újra a leggyakrabban látogatott oldalaid jelennek meg."),
                fields = emptyList(),
                buttons = listOf(
                    GlassButton(L.t("Mégse")) { },
                    GlassButton(L.t("Visszaállítás"), primary = true) { saveTiles(null) }
                )
            )
            return
        }
        val list = startTiles()
        val idx = arg?.toIntOrNull()?.takeIf { it in list.indices } ?: -1
        if (idx < 0 && list.size >= 8) return
        val cur = list.getOrNull(idx)

        val buttons = mutableListOf<GlassButton>()
        if (cur != null) {
            buttons.add(GlassButton(L.t("Törlés"), destructive = true) {
                list.removeAt(idx)
                saveTiles(list)
            })
        }
        buttons.add(GlassButton(L.t("Mégse")) { })
        buttons.add(GlassButton(L.t("Mentés"), primary = true) { values ->
            var u = values[1].trim()
            if (u.isNotEmpty()) {
                if (!u.contains("://")) u = "https://$u"
                val n = values[0].trim().ifEmpty { tileName(u) }
                if (idx >= 0) list[idx] = n to u else list.add(n to u)
                saveTiles(list)
            }
        })
        showGlassForm(
            title = if (cur != null) L.t("Csempe szerkesztése") else L.t("Új csempe"),
            message = null,
            fields = listOf(
                GlassField(L.t("Név"), cur?.first.orEmpty(), InputType.TYPE_CLASS_TEXT),
                GlassField(
                    L.t("Webcím (pl. telex.hu)"), cur?.second.orEmpty(),
                    InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
                )
            ),
            buttons = buttons,
            focusIndex = if (cur == null) 1 else 0
        )
    }

    private class GlassField(val hint: String, val value: String, val inputType: Int)
    private class GlassButton(
        val text: String,
        val primary: Boolean = false,
        val destructive: Boolean = false,
        val action: (List<String>) -> Unit
    )

    /** Igaz, amíg a menü panelje egy üveg űrlapot (pl. csempe szerkesztése) mutat. */
    private var glassFormOpen = false

    /**
     * Liquid glass párbeszédablak: ugyanaz az üveg, mint a keresősávé és a hárompontos menüé.
     * A képernyő felső részén, a közepéből nyílik ki (így a billentyűzet nem takarja).
     */
    private fun showGlassForm(
        title: String,
        message: String?,
        fields: List<GlassField>,
        buttons: List<GlassButton>,
        focusIndex: Int = 0
    ) {
        if (menuVisible || overviewShown || overviewBusy) return
        releaseFocus()
        setCollapsed(false)
        glassFormOpen = true
        formClosing = false
        menuPanel.background = null
        menuItems.removeAllViews()
        menuItems.setPadding(0, 0, 0, 0)

        val text = if (night) Color.WHITE else 0xFF1D1F24.toInt()
        val muted = ColorUtils.setAlphaComponent(text, 150)
        val box = LinearLayout(this)
        box.orientation = LinearLayout.VERTICAL
        box.setPadding(dp(20), dp(18), dp(20), dp(10))
        // enyhe dér az üvegen, hogy a szöveg bármilyen oldal fölött olvasható legyen
        box.background = GradientDrawable().apply {
            cornerRadius = dp(26).toFloat()
            val a = if (lensActive) 1f else 2.6f
            setColor(
                if (night) Color.argb((70 * a).toInt(), 20, 22, 28)
                else Color.argb((90 * a).toInt().coerceAtMost(255), 255, 255, 255)
            )
        }

        val tv = TextView(this)
        tv.text = title
        tv.textSize = 18f
        tv.setTypeface(tv.typeface, android.graphics.Typeface.BOLD)
        tv.setTextColor(text)
        box.addView(tv)
        if (message != null) {
            val mv = TextView(this)
            mv.text = message
            mv.textSize = 14.5f
            mv.setTextColor(muted)
            mv.setPadding(0, dp(8), 0, dp(4))
            box.addView(mv)
        }
        val edits = fields.map { f ->
            val e = EditText(this)
            e.hint = f.hint
            e.setText(f.value)
            e.isSingleLine = true
            e.inputType = f.inputType
            e.textSize = 16f
            e.setTextColor(text)
            e.setHintTextColor(ColorUtils.setAlphaComponent(text, 110))
            e.setPadding(dp(14), dp(12), dp(14), dp(12))
            e.background = GradientDrawable().apply {
                cornerRadius = dp(14).toFloat()
                setColor(if (night) Color.argb(46, 255, 255, 255) else Color.argb(26, 0, 0, 0))
            }
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            lp.topMargin = dp(10)
            box.addView(e, lp)
            e
        }
        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.END
        val rowLp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        rowLp.topMargin = dp(12)
        buttons.forEachIndexed { i, b ->
            val bt = TextView(this)
            bt.text = b.text
            bt.textSize = 16f
            bt.setPadding(dp(14), dp(10), dp(14), dp(10))
            bt.setTextColor(
                when {
                    b.destructive -> 0xFFFF453A.toInt()
                    b.primary -> 0xFF0A84FF.toInt()
                    else -> text
                }
            )
            if (b.primary) bt.setTypeface(bt.typeface, android.graphics.Typeface.BOLD)
            bt.setOnClickListener {
                haptic()
                val values = edits.map { it.text.toString() }
                hideMenu()
                b.action(values)
            }
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            row.addView(bt, lp)
            // a törlés gomb balra kerül, a többi jobbra
            if (b.destructive && i == 0 && buttons.size > 1) {
                row.addView(View(this), LinearLayout.LayoutParams(0, 1, 1f))
            }
        }
        box.addView(row, rowLp)
        // Enter az utolsó mezőben = mentés
        edits.lastOrNull()?.setOnEditorActionListener { _, _, _ ->
            row.getChildAt(row.childCount - 1)?.performClick()
            true
        }

        val w = minOf(root.width - dp(40), dp(380))
        box.measure(
            View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        )
        val h = box.measuredHeight
        menuItems.addView(box, LinearLayout.LayoutParams(w, ViewGroup.LayoutParams.WRAP_CONTENT))

        // Az ablak a szerkesztett csempe fölé kerül (ha ott nincs elég hely, alá); csempe nélkül
        // a képernyő felső részén, középen jelenik meg.
        val a = formAnchor
        val left: Int
        val top: Int
        if (a != null) {
            left = (a.centerX() - w / 2f).toInt().coerceIn(dp(20), maxOf(dp(20), root.width - dp(20) - w))
            val above = (a.top - dp(14) - h).toInt()
            top = if (above >= maxOf(statusTop, webTop()) + dp(8)) above else (a.bottom + dp(14)).toInt()
        } else {
            left = (root.width - w) / 2
            top = maxOf(statusTop, webTop()) + dp(64)
        }
        formTarget.set(left.toFloat(), top.toFloat(), (left + w).toFloat(), (top + h).toFloat())

        // a panel kitölti a képernyőt (így a kivágás a csempétől indulhat), a tartalom a helyén van
        menuPanel.layoutParams = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
        )
        val ilp = FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        ilp.gravity = Gravity.TOP or Gravity.LEFT
        ilp.leftMargin = left
        ilp.topMargin = top
        menuItems.layoutParams = ilp

        menuAnim?.cancel()
        menuClip.update(0f, 0f, 0f, 0f, 0f)
        menuFrame(0f)
        menuScrim.visibility = View.VISIBLE
        menuPanel.visibility = View.VISIBLE
        menuItems.setLayerType(View.LAYER_TYPE_HARDWARE, null)
        menuPanel.doOnLayout {
            menuPanel.scaleX = 1f
            menuPanel.scaleY = 1f
            morphForm(opening = true) {
                menuItems.setLayerType(View.LAYER_TYPE_NONE, null)
                edits.getOrNull(focusIndex)?.let { e ->
                    e.requestFocus()
                    e.setSelection(e.text.length)
                    WindowCompat.getInsetsController(window, e).show(WindowInsetsCompat.Type.ime())
                }
            }
        }
    }

    /** A szerkesztett csempe ikonjának helye az ablakban (ebből nyílik ki és ide záródik az űrlap). */
    private var formAnchor: RectF? = null
    private val formTarget = RectF()

    /** Az űrlap kiinduló alakja a panel koordinátáiban: a csempe ikonja, vagy egy kis középső alak. */
    private fun formOrigin(): RectF {
        val loc = IntArray(2)
        menuPanel.getLocationInWindow(loc)
        val a = formAnchor
        if (a != null) {
            return RectF(a.left - loc[0], a.top - loc[1], a.right - loc[0], a.bottom - loc[1])
        }
        val t = formTarget
        val fw = t.width() * 0.4f
        val fh = minOf(t.height() * 0.4f, dp(52).toFloat())
        return RectF(t.centerX() - fw / 2f, t.centerY() - fh / 2f, t.centerX() + fw / 2f, t.centerY() + fh / 2f)
    }

    /**
     * Az üveg űrlap kinyílása / bezárása: a kivágás a csempe ikonjából nő az ablak méretére, a
     * sarkai a kör alakból kerekítettbe váltanak. Záráskor a csempébe húzódik vissza, és közben az
     * üveg is teljesen elhalványul, így a végén nem marad semmi a képernyőn.
     */
    private fun morphForm(opening: Boolean, onEnd: () -> Unit) {
        val from = formOrigin()
        val loc = IntArray(2)
        menuPanel.getLocationInWindow(loc)
        val to = RectF(formTarget)
        to.offset(-loc[0].toFloat(), -loc[1].toFloat())
        val fromR = minOf(from.width(), from.height()) / 2f
        val toR = dp(26).toFloat()
        // a tartalom a csempe közepe felé zsugorodik / onnan nő ki
        menuItems.pivotX = from.centerX() + loc[0] - formTarget.left
        menuItems.pivotY = from.centerY() + loc[1] - formTarget.top
        val anim = if (opening) ValueAnimator.ofFloat(0f, 1f) else ValueAnimator.ofFloat(1f, 0f)
        anim.duration = if (opening) 520L else 460L
        anim.interpolator = if (opening) {
            android.view.animation.PathInterpolator(0.05f, 0.7f, 0.1f, 1f)
        } else {
            android.view.animation.PathInterpolator(0.4f, 0f, 0.2f, 1f)
        }
        anim.addUpdateListener {
            val f = it.animatedValue as Float
            menuClip.update(
                from.left + (to.left - from.left) * f,
                from.top + (to.top - from.top) * f,
                from.right + (to.right - from.right) * f,
                from.bottom + (to.bottom - from.bottom) * f,
                fromR + (toR - fromR) * f
            )
            menuFrame(f)
        }
        anim.addListener(object : AnimatorListenerAdapter() {
            private var cancelled = false
            override fun onAnimationCancel(animation: Animator) {
                cancelled = true
            }

            override fun onAnimationEnd(animation: Animator) {
                if (!cancelled) onEnd()
            }
        })
        menuAnim = anim
        anim.start()
    }

    /** A kezdőlap csempéi és a legutóbb megnyitott oldalak. */
    private fun startPageJs(): String {
        fun item(title: String, url: String) = JSONObject().put("title", title).put("url", url)
        val tiles = JSONArray()
        startTiles().forEach { tiles.put(item(it.first, it.second)) }
        val recent = JSONArray()
        HistoryStore.recent(5).forEach { e -> recent.put(item(e.title, e.url)) }
        val data = JSONObject().put("tiles", tiles).put("recent", recent)
            .put("exact", true).put("custom", prefs.contains(KEY_SHORTCUTS))
            .put("lang", if (L.en) "en" else "hu")
        return "window.glassData && window.glassData($data);"
    }

    private fun homeUrl(): String {
        val home = prefs.getString("home_page", "").orEmpty().trim()
        return if (home.isEmpty()) START_URL else resolveInput(home)
    }

    private fun resolveInput(raw: String): String {
        val t = raw.trim()
        if (t.isEmpty()) return START_URL
        if (t.contains("://")) return t
        val looksLikeHost = !t.contains(' ') && (t.contains('.') || t.startsWith("localhost"))
        if (looksLikeHost) return "https://$t"
        val engine = prefs.getString("search_engine", DEFAULT_SEARCH) ?: DEFAULT_SEARCH
        return engine + Uri.encode(t)
    }

    private fun displayText(url: String): String = if (Urls.isStart(url)) "" else Urls.host(url)

    private fun syncUi() {
        val tab = activeTab ?: return
        val focused = urlField.hasFocus()
        progress.progress = tab.progress
        // a kék töltősáv nem kell
        progress.visibility = View.GONE
        if (!focused) setFieldText(displayText(tab.url))
        iconBack.isEnabled = tab.webView.canGoBack()
        btnReload.setImageResource(if (focused || tab.loading) R.drawable.ic_close else R.drawable.ic_reload)
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    // ---------------------------------------------------------------- életciklus

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intent.dataString?.let { openNewTab(it) }
    }

    override fun onResume() {
        super.onResume()
        activeWebView?.onResume()
        applyAllSettings()
        handler.post(ticker)
    }

    override fun onPause() {
        handler.removeCallbacks(ticker)
        saveTabs()
        HistoryStore.save(this)
        CookieManager.getInstance().flush()
        activeWebView?.onPause()
        super.onPause()
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        tabs.forEach {
            (it.webView.parent as? ViewGroup)?.removeView(it.webView)
            it.webView.destroy()
        }
        super.onDestroy()
    }

    companion object {
        const val START_URL = "file:///android_asset/start.html"
        const val DEFAULT_SEARCH = "https://www.google.com/search?q="

        /** Gyakori webhelyek a cím-kiegészítéshez (a saját előzményeid ezek előtt jönnek). */
        private val POPULAR_HOSTS = listOf(
            "google.com", "youtube.com", "facebook.com", "wikipedia.org", "hu.wikipedia.org",
            "gmail.com", "maps.google.com", "drive.google.com", "translate.google.com",
            "instagram.com", "messenger.com", "whatsapp.com", "tiktok.com", "twitter.com", "x.com",
            "reddit.com", "twitch.tv", "netflix.com", "spotify.com", "linkedin.com", "amazon.com",
            "github.com", "stackoverflow.com", "news.ycombinator.com", "chatgpt.com", "claude.ai",
            "index.hu", "telex.hu", "444.hu", "hvg.hu", "24.hu", "origo.hu", "portfolio.hu",
            "idokep.hu", "port.hu", "arukereso.hu", "emag.hu", "alza.hu", "jofogas.hu",
            "hasznaltauto.hu", "yettel.hu", "telekom.hu", "otpbank.hu", "mav.hu"
        )
        /** Ennyi dp lehúzás után frissül az oldal. */
        private const val PULL_TRIGGER = 80f
        private const val KEY_TABS = "tabs_state"
        private const val KEY_SHORTCUTS = "start_shortcuts"
        private val DEFAULT_TILES = listOf(
            "Google" to "https://www.google.com",
            "YouTube" to "https://www.youtube.com",
            "Wikipedia" to "https://hu.wikipedia.org",
            "Index" to "https://index.hu",
            "Telex" to "https://telex.hu",
            "GitHub" to "https://github.com",
            "Térkép" to "https://www.openstreetmap.org",
            "Hacker News" to "https://news.ycombinator.com"
        )
        private const val KEY_ACTIVE = "tabs_active"

        // az üvegfelületek sorszámai a shaderben (a kisebb van felül)
        private const val SLOT_MENU = 0
        private const val SLOT_SUGGEST = 1
        private const val SLOT_CENTER = 2
        private const val SLOT_LEFT = 3
        private const val SLOT_RIGHT = 4
        private const val SLOT_SPARE = 5

        /** A fordításhoz: összegyűjti az oldal látható szövegeit, és előkészíti a cserét / visszaállítást. */
        const val TR_COLLECT_JS = """(function(){
var SKIP={SCRIPT:1,STYLE:1,NOSCRIPT:1,TEXTAREA:1,CODE:1,PRE:1,SVG:1,TEMPLATE:1,IFRAME:1,KBD:1,SAMP:1};
if(!document.body)return '[]';
var nodes=[],out=[],total=0;
var w=document.createTreeWalker(document.body,NodeFilter.SHOW_TEXT,{acceptNode:function(n){
var v=n.nodeValue;if(!v||!/[A-Za-z\u00C0-\u024F\u0370-\u03FF\u0400-\u04FF\u0590-\u06FF\u0900-\u097F\u3040-\u30FF\u4E00-\u9FFF\uAC00-\uD7AF]/.test(v))return NodeFilter.FILTER_REJECT;
var p=n.parentNode;while(p&&p!==document.body){var nn=(p.nodeName||'').toUpperCase();if(SKIP[nn]||p.isContentEditable)return NodeFilter.FILTER_REJECT;p=p.parentNode;}
return NodeFilter.FILTER_ACCEPT;}});
var n;while((n=w.nextNode())&&nodes.length<2500&&total<120000){nodes.push(n);var t=n.nodeValue.trim();out.push(t);total+=t.length;}
window.__gt={nodes:nodes,orig:nodes.map(function(x){return x.nodeValue;})};
window.__gtApply=function(s,arr){var g=window.__gt;if(!g)return;for(var i=0;i<arr.length;i++){var nd=g.nodes[s+i];if(!nd)continue;var o=g.orig[s+i];var a=o.match(/^\s*/)[0],b=o.match(/\s*$/)[0];nd.nodeValue=a+arr[i]+b;}};
window.__gtUndo=function(){var g=window.__gt;if(!g)return;for(var i=0;i<g.nodes.length;i++)g.nodes[i].nodeValue=g.orig[i];};
return JSON.stringify(out);})()"""

        const val SPACER_JS =
            "(function(){if(document.getElementById('__glass_spacer'))return;" +
                "var b=document.body;if(!b)return;var s=document.createElement('div');" +
                "s.id='__glass_spacer';s.style.cssText='height:96px;width:100%;pointer-events:none;flex:none';" +
                "b.appendChild(s);})();"
    }
}
