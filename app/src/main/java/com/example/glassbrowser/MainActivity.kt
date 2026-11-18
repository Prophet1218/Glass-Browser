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
    /** A normál és a privát lapok külön listában; [tabs] mindig az éppen látható csoport. */
    private val normalTabs = mutableListOf<Tab>()
    private val privateTabs = mutableListOf<Tab>()
    private var tabs = normalTabs
    private var privateMode = false
    private var normalActive: Tab? = null
    private var privateActive: Tab? = null
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
        ContextCompat.registerReceiver(
            this, downloadDone,
            android.content.IntentFilter(android.app.DownloadManager.ACTION_DOWNLOAD_COMPLETE),
            ContextCompat.RECEIVER_EXPORTED
        )

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
        setupPrivateButton()
        setupGlow()
        // az előző futásból maradt privát adatok törlése
        handler.postDelayed({ deletePrivateData() }, 1500L)
        L.tree(root)

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                val wv = activeWebView
                val tab = activeTab
                when {
                    rowActions != null -> hideRowActions()
                    menuVisible -> hideMenu()
                    overviewShown -> hideOverview()
                    searchShown || urlField.hasFocus() -> releaseFocus()
                    // on the start page the next back gesture leaves the app
                    wv != null && tab != null && Urls.isStart(wv.url ?: tab.url) -> {
                        isEnabled = false
                        onBackPressedDispatcher.onBackPressed()
                        isEnabled = true
                    }
                    wv != null && wv.canGoBack() -> wv.goBack()
                    // no more history in this tab (e.g. restored tab, link opened in a new tab):
                    // one step back is the start page; only from there does back leave the app
                    wv != null && tab != null -> {
                        tab.stayInBrowser = true
                        wv.loadUrl(START_URL)
                    }
                    else -> {
                        // the callback stays enabled afterwards (the old code switched it off for
                        // good, so every later back gesture closed the app)
                        isEnabled = false
                        onBackPressedDispatcher.onBackPressed()
                        isEnabled = true
                    }
                }
            }
        })

        restoreTabs()
        intent?.dataString?.let { openNewTab(it, capture = false) }
        applyAllSettings()
    }

    // ---------------------------------------------------------------- üveg

    /** Érintéskor fény a sáv részein: az ujj alatt világít, és követi az ujjat (mint az iOS-en). */
    private fun setupGlow() {
        for (v in listOf<FrameLayout>(barLeft, barCenter, barRight)) {
            val glow = GlowDrawable(resources.displayMetrics.density)
            v.foreground = glow
            var downX = 0f
            var downY = 0f
            var held = false
            (v as? PressLayout)?.onTouchMove = { x, y, down ->
                glow.track(x, y, down, Color.WHITE)
                if (down && !held) {
                    held = true
                    downX = x
                    downY = y
                } else if (!down) {
                    held = false
                } else if (!collapsed && v.width > 0 && v.height > 0) {
                    // a kapszula az ujj felé nyúlik (gumiszerűen, egyre nehezebben)
                    val dx = x - downX
                    val dy = y - downY
                    if (kotlin.math.abs(dx) > dp(3) || kotlin.math.abs(dy) > dp(3)) {
                        fun rubber(t: Float) = t / (1f + kotlin.math.abs(t))
                        val ax = rubber(dx / v.width * 2f)
                        val ay = rubber(dy / v.height)
                        val base = if (bounceOn) 1f - (0.04f + 0.16f * bounceStrength) * 0.5f else 1f
                        v.animate().cancel()
                        v.pivotX = v.width / 2f
                        v.pivotY = v.height / 2f
                        v.scaleX = base * (1f + 0.12f * kotlin.math.abs(ax))
                        v.scaleY = base * (1f + 0.10f * kotlin.math.abs(ay) - 0.04f * kotlin.math.abs(ax))
                        v.translationX = ax * dp(10)
                        v.translationY = ay * dp(6)
                        updateLens()
                    }
                }
            }
        }
    }

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
        // az összegyűjtött üveg-frissítések a kirajzolás előtt, egyszer futnak le (lásd updateLens)
        scene.viewTreeObserver.addOnPreDrawListener(lensPreDraw)
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
        // domború (3D) üveg: fénycsillanás a peremen, erősebb fénytörés a széleken
        val convexOn = prefs.getBoolean("glass_convex", false) && mode != "off"
        // a felugró ablakok üveglapja is domború (a következő megnyitott ablaktól)
        theme.applyStyle(
            if (prefs.getBoolean("glass_convex", false)) R.style.GlassDialogs_Convex else R.style.GlassDialogs_Flat,
            true
        )
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
                // matt üvegnél nincs fénytörés: ott csak a fény- és árnyékhatás marad
                l.setConvex(if (convexOn) 1f else 0f, if (convexOn && !frost) 1f else 0f)
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
            (if (glassClear()) night else glassDark()) -> Color.argb(plateAlpha, 27, 30, 38)
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
            // a shader alatt csak finom ráerősítés (a dér réteg tompítja a shader fényeit),
            // shader nélkül (egyszerű üveg) a bőr adja a teljes domború hatást
            it.setConvex(if (!convexOn) 0f else if (lensActive) 0.5f else 1f)
            // dér réteg: az üveg a választott árnyalatot kapja (olvasható szöveg minden oldal fölött)
            it.setFrost(
                if (!lensActive || glassClear()) Color.TRANSPARENT
                else if (glassDark()) Color.argb(110, 22, 24, 30) else Color.argb(150, 250, 250, 252)
            )
        }
        dockSkin.setTint(tintAlpha, plate)
        dockSkin.setOverlay(overlay)
        dockSkin.setShade((glassShade * 255f).toInt())
        dockSkin.setRim(false)
        applyBarStyle()
        if (!glassClear()) applyContentColor(!glassDark())

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
            dp(9), webTopY + dp(10) + dp(GROUP_BAR_SPACE_DP), dp(9),
            navBottom + if (dockTop) dp(24) else barHeight + dp(30)
        )
        layoutGroupCapsule(webTopY)
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
        // Ugyanazt a szkriptet nem futtatjuk újra (pl. minden billentyűzet-nyitáskor az összes lapon):
        // az oldal teljes bejárása a közös renderelő szálon akadást okozna. Új oldalnál
        // (onPageStarted) a jegyzet törlődik, és a betöltéskor úgyis lefut.
        val js = topPadJs()
        if (wv.appliedPadJs != js) {
            wv.appliedPadJs = js
            wv.evaluateJavascript(js, null)
        }
    }

    /**
     * Átúszó mód: az oldal teteje kap akkora térközt, amilyen magas az állapotsor, a képernyő
     * tetejéhez rögzített elemek (fejlécek) pedig ennyivel lejjebb kerülnek. Így az oldal eleje nem
     * kerül takarásba, görgetéskor viszont a tartalom valóban átúszik az állapotsor mögött.
     * Más módban ugyanez a szkript mindent visszaállít.
     */
    /**
     * A lebegő / alul rögzített sáv magassága CSS képpontban: ennyivel emeljük meg az oldal alján
     * rögzített elemeket (pl. süti-sáv), hogy ne a keresősáv mögé essenek.
     */
    private fun bottomBarCss(): Int {
        val d = resources.displayMetrics.density
        val px = when {
            dockTop -> 0
            docked -> dockHeight() + navBottom
            else -> barRow.layoutParams.height + dp(10) + dp(6) + navBottom
        }
        return (px / d).toInt()
    }

    private fun topPadJs(): String {
        val px = if (flowLayout()) (statusTop / resources.displayMetrics.density).toInt() else 0
        val bpx = bottomBarCss()
        return """(function(){var H=$px,B=$bpx;var d=document;if(!d.documentElement)return;
var st=d.getElementById('__glass_top');
if(H>0){if(!st){st=d.createElement('style');st.id='__glass_top';d.documentElement.appendChild(st);}
st.textContent='html{padding-top:'+H+'px !important;box-sizing:border-box !important}';}else if(st){st.remove();}
window.__gbHdrEls=window.__gbHdrEls||[];
function chk(e){if(!e||e.nodeType!==1)return;
var done=false;
if(e.__gbT!==undefined){done=true;if(e.__gbH!==H){if(H>0)e.style.setProperty('top',(e.__gbT+H)+'px','important');else{e.style.removeProperty('top');delete e.__gbT;}e.__gbH=H;}}
else if(e.__gbP!==undefined){done=true;if(e.__gbH!==H){if(H>0)e.style.setProperty('padding-top',(e.__gbP+H)+'px','important');else{e.style.removeProperty('padding-top');delete e.__gbP;}e.__gbH=H;}}
if(e.__gbBo!==undefined){if(e.__gbBB!==B){if(B>0)e.style.setProperty('bottom',(e.__gbBo+B)+'px','important');else{e.style.removeProperty('bottom');delete e.__gbBo;}e.__gbBB=B;}return;}
if(done||(H<=0&&B<=0))return;
var cs=getComputedStyle(e);var pos=cs.position;if(pos!=='fixed'&&pos!=='sticky')return;
var r=e.getBoundingClientRect();if(r.height<=0)return;
var t=parseFloat(cs.top);var tall=r.height>=innerHeight*0.5;
if(H>0&&!isNaN(t)&&t>=0&&t<4){
if(!tall){e.__gbT=t;e.__gbH=H;e.style.setProperty('top',(t+H)+'px','important');
if(r.width>=innerWidth*0.9&&r.top<H+innerHeight*0.2){e.__gbW=1;window.__gbHdrEls.push(e);}return;}
if(pos==='fixed'){var pt=parseFloat(cs.paddingTop)||0;e.__gbP=pt;e.__gbH=H;e.style.setProperty('padding-top',(pt+H)+'px','important');return;}}
var b=parseFloat(cs.bottom);
if(B>0&&pos==='fixed'&&!isNaN(b)&&b>=0&&b<4&&r.height<innerHeight*0.6){e.__gbBo=b;e.__gbBB=B;e.style.setProperty('bottom',(b+B)+'px','important');}}
function full(){if(!d.body)return;var all=d.body.getElementsByTagName('*');for(var i=0;i<all.length;i++)chk(all[i]);}
window.__gbHdrNow=function(){var L=window.__gbHdrEls;for(var i=0;i<L.length;i++){var e=L[i];if(!e.isConnected)continue;
var r=e.getBoundingClientRect();if(r.height>0&&r.top<=H+2&&r.bottom>H+8){var c=getComputedStyle(e);if(c.visibility!=='hidden'&&c.opacity>0.5&&c.display!=='none')return 1;}}return 0;};
full();
if(!window.__gbScr&&window.GlassScroll){window.__gbScr=1;var raf=0,ly=-1,lt=null;
d.addEventListener('scroll',function(ev){var t=ev.target;if(!t||t===d||t===d.documentElement||t===d.body)return;
if(!t.clientHeight||t.clientHeight<innerHeight*0.6)return;lt=t;if(raf)return;
raf=requestAnimationFrame(function(){raf=0;var y=Math.round(lt.scrollTop||0);if(y!==ly){ly=y;GlassScroll.inner(y);}});},true);}
var key=H+'/'+B;if(window.__gbTimer!==key){window.__gbTimer=key;setTimeout(full,600);setTimeout(full,2000);}
if(!window.__gbObs&&window.MutationObserver){var pend=[],tm=0;
function flush(){tm=0;var p=pend;pend=[];for(var i=0;i<p.length;i++){var n=p[i];n.__gbQ=0;chk(n);
if(n.__gbSub&&n.getElementsByTagName){var sub=n.getElementsByTagName('*');for(var j=0;j<sub.length&&j<400;j++)chk(sub[j]);}n.__gbSub=0;}}
window.__gbObs=new MutationObserver(function(ms){for(var i=0;i<ms.length;i++){var m=ms[i];
if(m.type==='childList'){for(var k=0;k<m.addedNodes.length;k++){var n=m.addedNodes[k];if(n.nodeType===1){n.__gbSub=1;if(!n.__gbQ){n.__gbQ=1;pend.push(n);}}}}
else if(m.target.nodeType===1&&!m.target.__gbQ&&m.target.__gbT===undefined&&m.target.__gbBo===undefined){m.target.__gbQ=1;pend.push(m.target);}}
if(!tm&&pend.length)tm=setTimeout(flush,180);});
window.__gbObs.observe(d.documentElement,{childList:true,subtree:true,attributes:true,attributeFilter:['class','style']});}
})();"""
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

    // ---- az üveg frissítése képkockánként legfeljebb egyszer
    /** Kérték az üveg frissítését; a következő kirajzolás előtt (onPreDraw) egyszer fut le. */
    private var lensUpdatePending = false
    /** Igaz az onPreDraw után, a kirajzolás végéig: ilyenkor a kérés azonnal lefut (ugyanebben a képkockában). */
    private var lensInDraw = false
    private val lensDrawEnd = Runnable { lensInDraw = false }
    private val lensPreDraw = android.view.ViewTreeObserver.OnPreDrawListener {
        if (lensUpdatePending) {
            lensUpdatePending = false
            updateLensNow()
        }
        lensInDraw = true
        // aszinkron üzenet: a képkocka (a rajzolás) után azonnal lefut, a szinkron gát sem tartja vissza
        val m = android.os.Message.obtain(handler, lensDrawEnd)
        m.isAsynchronous = true
        handler.sendMessageAtFrontOfQueue(m)
        true
    }

    /**
     * Az üveg frissítését kéri. Egy képkockán belül sokszor is hívódhat (görgetés, több animáció,
     * elrendezés-figyelők): a számítás és a RenderEffect újraépítése mégis csak egyszer fut, közvetlenül
     * a kirajzolás előtt, amikor már minden nézet a végső helyén van (így az üveg sem késik egy
     * képkockát). Ha a kérés a kirajzolás közben érkezik (pl. a WebView görgetése), azonnal lefut.
     */
    private fun updateLens() {
        if (!lensActive || Build.VERSION.SDK_INT < 33) return
        if (lensInDraw) {
            updateLensNow()
            return
        }
        if (!lensUpdatePending) {
            lensUpdatePending = true
            // biztosan legyen kirajzolás (és így onPreDraw), akkor is, ha más nem változott
            scene.invalidate()
        }
    }

    /** Megadja a shadernek, hol vannak éppen az üvegfelületek, és milyen a felső elmosás. */
    private fun updateLensNow() {
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
            // rögzített fejlécnél élőben a fejléc felső sora nyúlik fel (görgetéskor is pontos)
            // rögzített fejlécnél a fejléc (görgetéskor is megmaradó) tömör színe
            l.setPadColor(
                if (activeTab?.fixedHeader == true) activeTab?.headerColor ?: 0 else activeTab?.padColor ?: 0
            )
            syncStatusIcons()
            val searchVis = if (searchPage.visibility == View.VISIBLE) searchPage.alpha else 0f
            // lapváltó: csak görgetés közben kapcsol be, a görgetéssel együtt erősödik
            val ovScroll = (tabList.computeVerticalScrollOffset() / (48f * d)).coerceIn(0f, 1f)
            val ovVis = if (overview.visibility == View.VISIBLE && overviewShown) {
                overview.alpha * tabList.alpha * ovScroll
            } else 0f
            if (searchVis <= 0.01f && ovVis > 0.01f) {
                // a kártyák az állapotsor alatt lágy, hosszú átmenettel mosódnak el
                val tint = Color.argb((60 * ovVis).toInt(), 10, 12, 16)
                l.setFlow(0f, 0f, 0f, 0)
                l.setTop(statusTop.toFloat(), 64f * d, 14f * d * ovVis, tint, 0f, true)
            } else if (searchVis > 0.01f || searchShown) {
                // Kereső felület: a fülek sávja mögé görgetett szöveg (és az állapotsor mögötti rész)
                // elmosódik, lefelé lágyan elhalványuló átmenettel.
                val bandH = segBar.top + segBar.translationY + segBar.height
                val tint = if (night) Color.argb((80 * searchVis).toInt(), 14, 16, 22)
                else Color.argb((110 * searchVis).toInt(), 243, 244, 248)
                // The solid status-bar fill (header color) fades out together with the panel
                // fading in, instead of vanishing in one frame (that was the flicker on open).
                val pc = if (activeTab?.fixedHeader == true) activeTab?.headerColor ?: 0 else activeTab?.padColor ?: 0
                val padNow = if (!flowLayout() || overviewShown || pc == 0) 0f
                else if (activeTab?.fixedHeader == true) statusTop.toFloat()
                else (statusTop - (activeTab?.let { effScroll(it) } ?: statusTop)).coerceAtLeast(0).toFloat()
                val fade = (1f - searchVis).coerceIn(0f, 1f)
                l.setPadColor(if (padNow > 0f && fade > 0.01f) ColorUtils.setAlphaComponent(pc, (255 * fade).toInt()) else 0)
                l.setFlow(0f, 0f, 0f, 0)
                l.setTop(bandH, 22f * d, 16f * d * searchVis, tint, if (fade > 0.01f) padNow else 0f, true, hardEdge = true)
            } else if (topBlurOn && statusTop > 0 && !searchShown && contentContainer.scaleX > 0.999f &&
                (vis > 0.01f || (flowLayout() && !overviewShown))
            ) {
                // 0 erősségnél se elmosás, se árnyalat: az oldal tisztán látszik az állapotsor mögött
                val k = (topStrength / (6f * d)).coerceIn(0f, 1f) * vis
                val tint = if (night) Color.argb((96 * k).toInt(), 0, 0, 0) else Color.argb((80 * k).toInt(), 255, 255, 255)
                l.setFlow(0f, 0f, 0f, 0)
                if (flowLayout()) {
                    // amíg az oldal a tetején van, a fölötte lévő térköz ennyi képpont: ezt az oldal
                    // első sorának színével töltjük ki (különben szürke / sötét sáv látszana)
                    // Rögzített fejlécű oldalnál (pl. vivo.com) az állapotsor mögött nem a görgetett
                    // tartalom úszik át, hanem a fejléc folytatódik (a színével kitöltve).
                    val hdr = activeTab?.fixedHeader == true
                    val pad = if (overviewShown) 0f
                    else if (hdr) statusTop.toFloat()
                    else (statusTop - (activeTab?.let { effScroll(it) } ?: statusTop)).coerceAtLeast(0).toFloat()
                    val amt = if (hdr) 0f else flowAmt * vis
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
                    tmpRect.height() / 2f, lensBlur, tb.alpha
                )
            } else {
                l.clearSlot(SLOT_SUGGEST)
            }
            // "Vágólapra másolva" kapszula (vagy a kereső sorainak hosszú nyomásra megjelenő kapszulája)
            val ct = (rowActions ?: rowActionsFading)?.takeIf { it.visibility == View.VISIBLE && it.alpha > 0.02f } ?: copyToast
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
        updatePrivateUi()
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
        // A sáv szövege a választott árnyalathoz igazodik (világos üvegen sötét, sötéten világos),
        // így minden oldal fölött olvasható, mint a hárompontos menüben.
        if (!glassClear()) {
            applyContentColor(!glassDark())
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
        // görgetés közben nem olvasunk vissza a képernyőről (a GPU-t terhelné); megáll -> következő ütem
        if (android.os.SystemClock.uptimeMillis() - lastScrollAt < 300L) return
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
                        // csak változáskor (különben fölöslegesen újrarajzolná a tartalom keretét)
                        val col = Color.rgb(r / 8, g / 8, b / 8)
                        if ((contentContainer.background as? android.graphics.drawable.ColorDrawable)?.color != col) {
                            contentContainer.setBackgroundColor(col)
                        }
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
    private var statusLightCache: Boolean? = null
    /** Az állapotsor mögötti rész valóban világos-e (a kirajzolt képből mérve); null = nem tudjuk. */
    private var statusBright: Boolean? = null
    private val statusProbe: Bitmap = Bitmap.createBitmap(24, 2, Bitmap.Config.ARGB_8888)
    private val statusPixels = IntArray(48)
    private var statusProbeBusy = false

    /** Megméri, milyen világos az állapotsor mögötti rész (fehér fejlécnél sötét ikonok kellenek). */
    private fun probeStatusBand() {
        if (statusProbeBusy || statusTop <= 0 || scene.width <= 0 || !flowLayout()) return
        statusProbeBusy = true
        try {
            PixelCopy.request(window, Rect(0, 0, scene.width, statusTop), statusProbe, { result ->
                statusProbeBusy = false
                if (result != PixelCopy.SUCCESS) return@request
                statusProbe.getPixels(statusPixels, 0, 24, 0, 0, 24, 2)
                var sum = 0.0
                for (px in statusPixels) sum += ColorUtils.calculateLuminance(px or (0xFF shl 24))
                val lum = sum / statusPixels.size
                val prev = statusBright
                // hiszterézis: ne villogjon a határon
                statusBright = if (prev == true) lum > 0.4 else lum > 0.55
                syncStatusIcons()
            }, handler)
        } catch (e: Exception) {
            statusProbeBusy = false
        }
    }

    /**
     * Az állapotsor ikonjai: ha az oldal teteje világos (pl. fehér fejléc), sötét ikonok; ha
     * görgetés közben sötét tartalom kerül mögé, világosak. Egyébként a világos / sötét mód szerint.
     */
    private fun syncStatusIcons() {
        val sb = statusBright
        val searchOver = searchPage.visibility == View.VISIBLE && searchPage.alpha >= 0.5f
        val light = if (flowLayout() && sb != null && !overviewShown && !searchOver &&
            contentContainer.scaleX > 0.999f
        ) sb else !night
        if (statusLightCache == light) return
        statusLightCache = light
        WindowCompat.getInsetsController(window, root).isAppearanceLightStatusBars = light
    }

    private fun applyStatusColor() {
        statusLightCache = null
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
            // gyorsabb, akadásmentes görgetés: a látható részen kívüli csempéket is előre kirajzolja
            offscreenPreRaster = true
        }
        // az aktív lap renderelője kapja a legnagyobb prioritást
        wv.setRendererPriorityPolicy(WebView.RENDERER_PRIORITY_IMPORTANT, true)
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
        applyTabLayout()
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
    private fun openSettings() = snapBackdrop { settingsLauncher.launch(Intent(this, SettingsActivity::class.java)) }

    /** Fresh blurred-backdrop snapshot (settings / downloads glass background), then [launch]. */
    private fun snapBackdrop(launch: () -> Unit) {
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
        wv.isVerticalScrollBarEnabled = false
        wv.isHorizontalScrollBarEnabled = false
        HighRefresh.view(wv)
        // Privát lap: külön WebView-profil (saját sütik, tárhely, gyorsítótár), ha a telefon
        // WebView-ja tudja (Android System WebView 110+).
        if (privateMode && privateProfileSupported()) {
            try {
                androidx.webkit.WebViewCompat.setProfile(wv, PRIVATE_PROFILE)
            } catch (e: Throwable) {
                // marad a normál profil
            }
        }
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
        tab.isPrivate = privateMode
        applyWebSettings(wv)
        // belső görgetésű oldalak (pl. samsung.com): az oldal egy belső eleme görget, nem a WebView
        wv.addJavascriptInterface(ScrollBridge(tab), "GlassScroll")
        // A kezdőlapon a hosszú nyomás csak a csempe szerkesztőjét nyitja meg: a WebView saját
        // reakciója (a link "kiemelése", húzása, szövegkijelölés) itt ki van kapcsolva.
        wv.setOnLongClickListener { Urls.isStart(wv.url.orEmpty()) }
        // letöltések: a rendszer letöltéskezelője tölti le a Letöltések mappába
        wv.setDownloadListener { url, userAgent, contentDisposition, mimetype, _ ->
            val ok = Downloads.start(applicationContext, url, userAgent, contentDisposition, mimetype)
            showCopyToast(if (ok) L.t("Letöltés elindult") else L.t("Ez a letöltés nem támogatott"))
        }
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
                probePadColor(tab, 250L)
                if (AdBlock.enabled) view?.evaluateJavascript(AD_COSMETIC_JS, null)
            }

            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                tab.loading = true
                tab.progress = 5
                if (url != null) tab.url = url
                wv.appliedPadJs = null
                wv.resetInset()
                tab.padColor = 0
                tab.fixedHeader = false
                tab.headerColor = 0
                tab.innerScroll = 0
                tab.noHeader = false
                tab.hdrHits = 0
                tab.maxScroll = 0
                // a site whose header we already know: the status bar is solid right away
                // (no late darkening while the header is re-detected on every page)
                rememberedHeader(tab.url).let { c ->
                    if (c != 0) {
                        tab.fixedHeader = true
                        tab.headerColor = c
                        tab.padColor = c
                        if (tab === activeTab) updateLens()
                    }
                }
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
                if (!tab.isPrivate) HistoryStore.add(url, view?.title)
                if (Urls.isStart(url.orEmpty())) view?.evaluateJavascript(startPageJs(), null)
                probePadColor(tab, 300L)
                probePadColor(tab, 1500L)
                if (AdBlock.enabled) view?.evaluateJavascript(AD_COSMETIC_JS, null)
                if (tab === activeTab && view != null) offerTranslate(tab, view)
                if (tab === activeTab) syncUi()
            }

            override fun doUpdateVisitedHistory(view: WebView?, url: String?, isReload: Boolean) {
                if (url != null) tab.url = url
                if (tab === activeTab) syncUi()
            }
        }

        wv.webChromeClient = object : WebChromeClient() {
            // a videók helyén betöltés előtt ne a szürke "lejátszás" kép látsszon
            override fun getDefaultVideoPoster(): Bitmap? =
                Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)

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
                if (scrollY == 0 && oldScrollY > 0) probePadColor(tab, 120L)
                // görgetés után újra megnézzük (rögzített fejléc csak görgetéskor jelenhet meg)
                if (kotlin.math.abs(dy) > 0) {
                    lastScrollAt = android.os.SystemClock.uptimeMillis()
                    // görgetés közben is rendszeresen (a fejléc elrejtőzhet / előjöhet), és a
                    // görgetés végén még egyszer (hogy a végső állapot biztosan jó legyen)
                    if (!padRecheckPending) {
                        padRecheckPending = true
                        handler.postDelayed(padRecheck, 120L)
                    }
                    handler.removeCallbacks(padTrail)
                    handler.postDelayed(padTrail, 260L)
                }
                onTranslateScroll(dy)
                updateTopFade(maxOf(scrollY, tab.innerScroll))
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
        if (tab !== activeTab) {
            statusBright = null
            handler.postDelayed({ probeStatusBand() }, 350L)
        }
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
        updateTopFade(effScroll(tab))
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

        if (tabs.isEmpty() && tab.isPrivate) {
            // az utolsó privát lap bezárult: vissza a normál lapokhoz
            leavePrivate()
            updateTabCount()
            return
        }
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
            "${tabs.size} " + (if (privateMode) "private " else "") + if (tabs.size == 1) "tab" else "tabs"
        } else {
            "${tabs.size} " + (if (privateMode) "privát lap" else "lap")
        }
        updateGroupCapsule()
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
                    tab.group = o.optString("group", "")
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
        restoreTabGroup()
    }

    private fun saveTabs() {
        // a privát lapokat nem mentjük
        val arr = JSONArray()
        val active = if (privateMode) normalActive else activeTab
        normalTabs.forEach { t ->
            val o = JSONObject()
            o.put("id", t.id)
            o.put("url", t.webView.url ?: t.pendingUrl ?: t.url)
            o.put("title", t.title)
            if (t.group.isNotEmpty()) o.put("group", t.group)
            arr.put(o)
        }
        prefs.edit()
            .putString(KEY_TABS, arr.toString())
            .putInt(KEY_ACTIVE, normalTabs.indexOf(active).coerceAtLeast(0))
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
        val wrapped = done
        @Suppress("NAME_SHADOWING")
        val done = {
            rememberStartShot(tab)
            wrapped()
        }
        val bmp = renderPage(tab, 0.55f)
        if (bmp != null) {
            tab.thumbnail = bmp
            if (!tab.isPrivate) ThumbStore.save(applicationContext, tab.id, bmp)
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
            // A WebView szoftveres rajzából kimaradnak a videók, animációk (ezért "tűnt el" a
            // tartalom): a képernyőn látható részt ráolvassuk a képre (a keresősáv fölött).
            val wv = tab.webView
            val top = wv.top.coerceAtLeast(0)
            val barTopY = (barRow.top + barRow.translationY).toInt() - dp(4)
            val bottom = minOf(wv.bottom, if (dockTop) wv.bottom else barTopY).coerceAtMost(scene.height)
            val finish = {
                rememberStartShot(tab)
                if (!tab.isPrivate) ThumbStore.save(applicationContext, tab.id, bmp)
                shrinkOtherThumbnails(tab)
                done()
            }
            if (bottom - top < dp(40) || wv.visibility != View.VISIBLE) {
                finish()
                return
            }
            try {
                val part = Bitmap.createBitmap(
                    (scene.width * 0.6f).toInt().coerceAtLeast(1),
                    ((bottom - top) * 0.6f).toInt().coerceAtLeast(1),
                    Bitmap.Config.ARGB_8888
                )
                PixelCopy.request(window, Rect(0, top, scene.width, bottom), part, { result ->
                    if (result == PixelCopy.SUCCESS) {
                        val c = Canvas(bmp)
                        c.drawBitmap(
                            part, null,
                            RectF(0f, (top - wv.top) * 0.6f, bmp.width.toFloat(), (bottom - wv.top) * 0.6f),
                            android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG)
                        )
                    }
                    part.recycle()
                    finish()
                }, handler)
            } catch (e: Exception) {
                finish()
            }
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
                        if (!tab.isPrivate) ThumbStore.save(applicationContext, tab.id, bmp)
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
            tabsOf = { tabs },
            activeTab = { activeTab },
            hiddenTab = { heroTab },
            ringAlpha = { ringAlpha },
            titleAlpha = { titleAlpha },
            // a kártyára koppintva a lap teljes képernyőre nagyít
            onSelect = { tab -> hideOverview(tab) },
            onClose = { tab -> closeTabAnimated(tab) }
        )
        tabList.layoutManager = GridLayoutManager(this, 2)
        tabList.adapter = tabAdapter
        // bezáráskor a többi kártya lágyan csúszik a helyére
        tabList.itemAnimator = androidx.recyclerview.widget.DefaultItemAnimator().apply {
            moveDuration = 340
            removeDuration = 160
            addDuration = 200
        }
        // (a halom elrendezés figyelője a húzás előtt kapja meg az érintést)
        setupStackLayout()
        setupSwipeToClose()
        setupTabGroups()

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
     * A lapváltóban a kártyát jobbra vagy balra húzva bezárható: húzás közben a beállított bezárási
     * animáció követi az ujjat (lásd [applyCloseFx]); elengedve végigfut, a többi kártya a helyére csúszik.
     */
    private fun setupSwipeToClose() {
        val callback = object : androidx.recyclerview.widget.ItemTouchHelper.SimpleCallback(
            0, androidx.recyclerview.widget.ItemTouchHelper.LEFT or androidx.recyclerview.widget.ItemTouchHelper.RIGHT
        ) {
            private var armed = false

            override fun getMovementFlags(rv: RecyclerView, vh: RecyclerView.ViewHolder): Int {
                if (!overviewShown || overviewBusy) return 0
                // halomban csak a ténylegesen megérintett (legfelső) kártya húzható
                if (stackLayout && !stackSwipeAllowed(vh)) return 0
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
                val p = (kotlin.math.abs(dX) / w).coerceIn(0f, 1f)
                if (dX != 0f) fxViews.add(v)
                applyCloseFx(v, dX, closeAnimStyle())
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
                fxViews.remove(v)
                resetCloseFx(v)
                armed = false
            }
        }
        androidx.recyclerview.widget.ItemTouchHelper(callback).attachToRecyclerView(tabList)
    }

    // ---------------------------------------------------------------- lapváltó: halom elrendezés, bezárási animációk

    /** Igaz, ha a lapváltó "halom" elrendezésű (egymásra rakott, hátradöntött lapok, mint a régi Safariban). */
    private var stackLayout = false
    /** A halomban a kártyák oldalaránya (magasság / szélesség). */
    private val stackRatio = 1.2f
    /** A halomban egy kártyából ennyi (a magasság hányada) látszik ki a következő alól. */
    private val stackPeek = 0.3f
    /** Azok a kártyák, amelyeken épp bezárási animáció fut (ilyenkor a saját mozgásuk nem számít a halomba). */
    private val fxViews = HashSet<View>()
    private var stackDownX = 0f
    private var stackDownY = 0f
    private var stackGuardFor: RecyclerView.ViewHolder? = null
    private var stackGuardOk = true
    private val stackRect = RectF()

    /** A beállított bezárási animáció ("fly", "shrink", "blur", "toss", "fold"). */
    private fun closeAnimStyle(): String = prefs.getString("close_anim", "fade") ?: "fade"

    /** A beállított elrendezés (rács / halom) alkalmazása, újraindítás nélkül. */
    private fun applyTabLayout() {
        if (!::tabAdapter.isInitialized) return
        val stack = false // only the grid layout is kept
        if (stack == stackLayout) return
        stackLayout = stack
        fxViews.clear()
        stackGuardFor = null
        tabList.layoutManager = if (stack) {
            androidx.recyclerview.widget.LinearLayoutManager(this)
        } else {
            GridLayoutManager(this, 2)
        }
        // a kártyák újra létrejönnek (a halomban más a felépítésük)
        tabList.recycledViewPool.clear()
        tabList.adapter = tabAdapter
        tabList.invalidateItemDecorations()
        if (overviewShown) scrollTabListTo(tabs.indexOf(activeTab).coerceAtLeast(0))
    }

    /** A halom elrendezés kellékei: átfedés, a kártyák felépítése, a 3D-s döntés görgetés közben. */
    private fun setupStackLayout() {
        // a halomban a kártyák nagy része az előző kártya fölé csúszik (csak a teteje látszik ki)
        tabList.addItemDecoration(object : RecyclerView.ItemDecoration() {
            override fun getItemOffsets(out: Rect, view: View, parent: RecyclerView, state: RecyclerView.State) {
                if (!stackLayout || parent.getChildLayoutPosition(view) <= 0) {
                    out.set(0, 0, 0, 0)
                } else {
                    out.set(0, -stackOverlap(parent.width), 0, 0)
                }
            }
        })
        tabList.addOnChildAttachStateChangeListener(object : RecyclerView.OnChildAttachStateChangeListener {
            override fun onChildViewAttachedToWindow(view: View) {
                if (stackLayout) styleStackItem(view)
            }

            override fun onChildViewDetachedFromWindow(view: View) {
                // egy eltávolított / újrahasznosított kártyán ne maradjon rajta a bezárási animáció
                if (fxViews.remove(view)) resetCloseFx(view)
            }
        })
        // minden képkocka előtt (görgetés, beúszó / helyére csúszó kártyák) frissül a döntés
        tabList.viewTreeObserver.addOnPreDrawListener {
            if (stackLayout && overview.visibility == View.VISIBLE) applyStackTransforms()
            true
        }
        // az érintés helye: halomban ebből derül ki, melyik kártyát húzzák valójában
        tabList.addOnItemTouchListener(object : RecyclerView.SimpleOnItemTouchListener() {
            override fun onInterceptTouchEvent(rv: RecyclerView, e: MotionEvent): Boolean {
                if (e.actionMasked == MotionEvent.ACTION_DOWN) {
                    stackDownX = e.x
                    stackDownY = e.y
                    stackGuardFor = null
                }
                return false
            }
        })
    }

    /** Ennyivel csúszik a halomban egy kártya az előzőre (a kártya magasságának nagy része). */
    private fun stackOverlap(listWidth: Int): Int {
        val w = listWidth - tabList.paddingLeft - tabList.paddingRight - dp(14)
        if (w <= 0) return 0
        return (w * stackRatio * (1f - stackPeek)).toInt()
    }

    /**
     * Halom elrendezésnél a kártya felépítése: alacsonyabb kártya, a cím a kártya tetejére kerül
     * (sötét átmeneten, a bezáró gomb mellé), így a következő kártya nem takarja el.
     */
    private fun styleStackItem(item: View) {
        val card = item.findViewById<AspectFrameLayout>(R.id.tabCard) ?: return
        // a feltorlódó kártyák kilóghatnak a saját soruk alól
        (item as? ViewGroup)?.clipChildren = false
        if (card.ratio != stackRatio) {
            card.ratio = stackRatio
            card.requestLayout()
        }
        val title = item.findViewById<View>(R.id.tabTitle) ?: return
        val row = title.parent as? LinearLayout ?: return
        if (row.parent === card) return
        (row.parent as? ViewGroup)?.removeView(row)
        row.gravity = Gravity.CENTER_VERTICAL or Gravity.START
        row.setPadding(dp(14), 0, dp(46), 0)
        row.background = GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(Color.argb(150, 0, 0, 0), Color.TRANSPARENT)
        )
        val lp = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48), Gravity.TOP)
        // a kép fölé, de a kijelölő keret és a bezáró gomb alá
        card.addView(row, minOf(1, card.childCount), lp)
    }

    /**
     * A halom 3D-s hatása: minden kártya kicsit hátra van döntve (perspektívával), a lista teteje
     * felé egyre jobban; a lista teteje fölé görgetett kártyák egy keskeny sávban torlódnak fel.
     * A transzformáció a kártyán (nem a soron) van, így a húzás és a beúszó animációk nem ütköznek vele.
     */
    private fun applyStackTransforms() {
        val rv = tabList
        val top0 = rv.paddingTop.toFloat()
        val contentH = (rv.height - rv.paddingTop - rv.paddingBottom).toFloat().coerceAtLeast(1f)
        val pile = dp(26).toFloat()
        val cam = 1600f * resources.displayMetrics.density
        for (i in 0 until rv.childCount) {
            val item = rv.getChildAt(i)
            val card = item.findViewById<View>(R.id.tabCard) ?: continue
            val y = item.top + card.top - top0 + (if (item in fxViews) 0f else item.translationY)
            val c = if (y < 0f) 1f - kotlin.math.exp(y / pile) else 0f
            val mapped = if (y < 0f) -pile * c else y
            val u = (mapped / contentH).coerceIn(0f, 1f)
            card.pivotX = card.width / 2f
            card.pivotY = 0f
            if (kotlin.math.abs(kotlin.math.abs(card.cameraDistance) - cam) > 1f) card.cameraDistance = cam
            card.translationY = mapped - y
            card.rotationX = 4f + 8f * (1f - u) + 14f * c
            val s = 1f - 0.05f * (1f - u) - 0.06f * c
            card.scaleX = s
            card.scaleY = s
        }
    }

    /** A lapváltó görgetése a lapra; halomban úgy, hogy a lap ne a feltorlódott sávba kerüljön. */
    private fun scrollTabListTo(idx: Int) {
        val lm = tabList.layoutManager
        if (stackLayout && lm is androidx.recyclerview.widget.LinearLayoutManager) {
            val w = tabList.width.takeIf { it > 0 } ?: root.width
            val h = tabList.height.takeIf { it > 0 } ?: root.height
            val contentH = h - tabList.paddingTop - tabList.paddingBottom
            val off = (contentH * 0.28f).toInt() + if (idx > 0) stackOverlap(w) else 0
            lm.scrollToPositionWithOffset(idx, off)
        } else {
            tabList.scrollToPosition(idx)
        }
    }

    /** Halomban a húzás csak akkor indul, ha az ujj alatt valóban ez a kártya van legfelül. */
    private fun stackSwipeAllowed(vh: RecyclerView.ViewHolder): Boolean {
        if (stackGuardFor !== vh) {
            stackGuardFor = vh
            stackGuardOk = topItemAt(stackDownX, stackDownY) === vh.itemView
        }
        return stackGuardOk
    }

    /** A legfelső kártya sora a lista egy pontján (a kártyák tényleges, transzformált helye szerint). */
    private fun topItemAt(x: Float, y: Float): View? {
        for (i in tabList.childCount - 1 downTo 0) {
            val child = tabList.getChildAt(i)
            val card = child.findViewById<View>(R.id.tabCard) ?: continue
            stackRect.set(0f, 0f, card.width.toFloat(), card.height.toFloat())
            var cur: View = card
            while (cur !== tabList) {
                cur.matrix.mapRect(stackRect)
                stackRect.offset(cur.left.toFloat(), cur.top.toFloat())
                cur = cur.parent as? View ?: break
            }
            if (stackRect.contains(x, y)) return child
        }
        return null
    }

    /**
     * A bezárási animáció egy állapota a kártya során. [dX] a vízszintes elmozdulás (húzásnál az
     * ujjé, a × gombnál a lejátszott animációé), az előjele adja az irányt.
     */
    private fun applyCloseFx(v: View, dX: Float, style: String) {
        val w = tabList.width.toFloat().coerceAtLeast(1f)
        val p = (kotlin.math.abs(dX) / w).coerceIn(0f, 1f)
        val dir = if (dX < 0f) -1f else 1f
        val d = resources.displayMetrics.density
        var px = v.width / 2f
        var py = v.height / 2f
        var tx = dX
        var ty = 0f
        var rot = 0f
        var rotY = 0f
        var sc = 1f
        var alpha = (1f - 1.3f * p).coerceIn(0f, 1f)
        var blur = 0f
        when (style) {
            "fade" -> {
                // slides to the side with the finger and fades out, no tilt
                tx = dX
                sc = 1f - 0.04f * p
                alpha = (1f - 1.4f * p).coerceIn(0f, 1f)
            }
            "shrink" -> {
                // a közepe felé zsugorodik és elhalványul
                tx = dX * 0.25f
                sc = 1f - 0.7f * p
                alpha = (1f - 1.25f * p).coerceIn(0f, 1f)
            }
            "blur" -> {
                // elmosódva elhalványul
                tx = dX * 0.2f
                sc = 1f - 0.06f * p
                alpha = (1f - 1.1f * p).coerceIn(0f, 1f)
                blur = 28f * d * p
            }
            "toss" -> {
                // felfelé kilökődik, kicsit elfordulva
                py = v.height.toFloat()
                tx = dX * 0.55f
                ty = -p * (v.height * 0.8f + 60f * d)
                rot = 14f * p * dir
                sc = 1f - 0.15f * p
                alpha = (1f - 1.6f * (p - 0.2f)).coerceIn(0f, 1f)
            }
            "fold" -> {
                // a húzás felőli éle körül, mint egy lap, elfordul és összecsukódik
                val cam = 1600f * d
                if (kotlin.math.abs(kotlin.math.abs(v.cameraDistance) - cam) > 1f) v.cameraDistance = cam
                px = if (dir > 0f) v.width.toFloat() else 0f
                tx = dX * 0.45f
                rotY = -dir * 85f * p
                alpha = (1f - 1.1f * p * p).coerceIn(0f, 1f)
            }
            else -> {
                // kirepül: követi az ujjat, kicsit elfordul, összemegy és elhalványul
                py = v.height.toFloat()
                rot = 8f * p * dir
                sc = 1f - 0.12f * p
            }
        }
        v.pivotX = px
        v.pivotY = py
        v.translationX = tx
        v.translationY = ty
        v.rotation = rot
        v.rotationY = rotY
        v.scaleX = sc
        v.scaleY = sc
        v.alpha = alpha
        if (style == "blur" && Build.VERSION.SDK_INT >= 31) {
            v.setRenderEffect(
                if (blur < 0.5f) null else RenderEffect.createBlurEffect(blur, blur, Shader.TileMode.DECAL)
            )
        }
    }

    /** A bezárási animáció nyomainak eltávolítása a kártya soráról. */
    private fun resetCloseFx(v: View) {
        v.translationX = 0f
        v.translationY = 0f
        v.rotation = 0f
        v.rotationY = 0f
        v.scaleX = 1f
        v.scaleY = 1f
        v.alpha = 1f
        v.pivotX = v.width / 2f
        v.pivotY = v.height / 2f
        if (Build.VERSION.SDK_INT >= 31) v.setRenderEffect(null)
    }

    /** Bezárás a × gombbal: ugyanaz a bezárási animáció fut le, mint húzásnál, utána zárul be a lap. */
    private fun closeTabAnimated(tab: Tab) {
        if (overviewBusy) return
        val idx = tabs.indexOf(tab)
        val item = if (overviewShown && idx >= 0) tabList.layoutManager?.findViewByPosition(idx) else null
        if (item == null) {
            closeTab(tab)
            return
        }
        if (!fxViews.add(item)) return
        val style = closeAnimStyle()
        val w = tabList.width.toFloat().coerceAtLeast(1f)
        val anim = ValueAnimator.ofFloat(0f, 1f)
        anim.duration = if (style == "fly" || style == "fade") 240L else 280L
        anim.interpolator = android.view.animation.PathInterpolator(0.4f, 0f, 0.8f, 1f)
        anim.addUpdateListener {
            // (ha közben a kártya kigördült és újrahasznosult, már nem mozgatjuk)
            if (item in fxViews) applyCloseFx(item, (it.animatedValue as Float) * w, style)
        }
        anim.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                if (overviewBusy || tab !in tabs) {
                    if (fxViews.remove(item)) resetCloseFx(item)
                    return
                }
                closeTab(tab)
                // ha a kártya nem tűnt el a listából (pl. a lista újrarajzolódott), visszaáll
                handler.postDelayed({
                    if (item.parent === tabList && item in fxViews &&
                        tabList.getChildViewHolder(item).bindingAdapterPosition != RecyclerView.NO_POSITION
                    ) {
                        fxViews.remove(item)
                        resetCloseFx(item)
                    }
                }, 120L)
            }
        })
        anim.start()
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
        if (stackLayout) {
            // halomban a kártya el van tolva, döntve és kicsinyítve: a tényleges (látható) helye kell
            applyStackTransforms()
            val r = RectF()
            visualRect(card, r)
            return r
        }
        val a = IntArray(2)
        val b = IntArray(2)
        card.getLocationInWindow(a)
        scene.getLocationInWindow(b)
        val l = (a[0] - b[0]).toFloat()
        val t = (a[1] - b[1]).toFloat()
        return RectF(l, t, l + card.width, t + card.height)
    }

    /** Igaz, ha a lapváltó a keresősávon felfelé legyintéssel nyílt (a kép a közepen át repül). */
    private var overviewViaCenter = false

    private var swipeStartY = -1f
    private var swipeStartX = 0f
    private var swipeConsumed = false

    private var swipeActive = false
    private var swipeVel: android.view.VelocityTracker? = null
    private var swipeAnim: ValueAnimator? = null
    /** A felhúzás közben összezsugorított oldal helye (innen indul a lapváltó animációja). */
    private var heroStartRect: RectF? = null
    private var pendingPageReset = false

    /**
     * Felhúzás a keresősávról, mint a gesztusvezérlés az alkalmazásoknál: az oldal az ujjal együtt
     * zsugorodik és emelkedik, amíg nem engeded el. Elengedéskor (elég nagy húzásnál vagy gyors
     * legyintésnél) megnyílik a lapváltó, különben az oldal visszaugrik a helyére.
     */
    private var dragProgress = 0f

    private fun startPageDrag() {
        swipeAnim?.cancel()
        haptic()
        hideTranslateBar()
        val c = contentContainer
        c.pivotX = c.width / 2f
        c.pivotY = c.height.toFloat()
        c.outlineProvider = object : android.view.ViewOutlineProvider() {
            override fun getOutline(v: View, o: android.graphics.Outline) {
                o.setRoundRect(0, 0, v.width, v.height, screenRadius * (0.6f + 0.4f / v.scaleX.coerceAtLeast(0.3f)))
            }
        }
        c.clipToOutline = true
    }

    /** A húzás állapota: a zsugorodás egyre lassul (gumiszerű), az oldal követi az ujjat. */
    private fun dragPage(dy: Float, dx: Float) {
        val h = scene.height.toFloat().coerceAtLeast(1f)
        val p = (dy / (h * 0.55f)).coerceIn(0f, 1.4f)
        val eased = 1f - 1f / (1f + p * 1.6f)
        dragProgress = p
        val c = contentContainer
        val sc = 1f - 0.5f * eased
        c.scaleX = sc
        c.scaleY = sc
        c.translationY = -dy * 0.55f
        c.translationX = dx * 0.25f
        c.invalidateOutline()
        barRow.alpha = (1f - p * 2.2f).coerceIn(0f, 1f)
        updateLens()
    }

    private fun resetPageTransform() {
        val c = contentContainer
        c.scaleX = 1f
        c.scaleY = 1f
        c.translationX = 0f
        c.translationY = 0f
        c.clipToOutline = false
        c.outlineProvider = android.view.ViewOutlineProvider.BACKGROUND
    }

    private fun endPageDrag(open: Boolean) {
        swipeVel?.recycle()
        swipeVel = null
        if (open) {
            // a lapváltó pontosan az összezsugorított oldal helyéről indul
            activeWebView?.let { wv ->
                visualRect(wv, tmpRect)
                heroStartRect = RectF(tmpRect)
            }
            showOverview()
            return
        }
        // vissza a helyére, rugózva
        val c = contentContainer
        val startS = c.scaleX
        val startTy = c.translationY
        val startTx = c.translationX
        val startA = barRow.alpha
        val a = ValueAnimator.ofFloat(0f, 1f)
        a.duration = 380L
        a.interpolator = OvershootInterpolator(1.1f)
        a.addUpdateListener {
            val f = it.animatedValue as Float
            val sc = startS + (1f - startS) * f
            c.scaleX = sc
            c.scaleY = sc
            c.translationY = startTy * (1f - f)
            c.translationX = startTx * (1f - f)
            c.invalidateOutline()
            barRow.alpha = (startA + (1f - startA) * f).coerceIn(0f, 1f)
            updateLens()
        }
        a.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                resetPageTransform()
                barRow.alpha = 1f
                updateLens()
            }
        })
        swipeAnim = a
        a.start()
    }

    private fun showOverview() {
        if (overviewShown || overviewBusy) {
            overviewViaCenter = false
            heroStartRect = null
            if (!overviewShown) resetPageTransform()
            return
        }
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
            scrollTabListTo(tabs.indexOf(tab).coerceAtLeast(0))
            overview.alpha = 0f
            overview.visibility = View.VISIBLE
            // a sáv végig látszik, csak a tartalma tűnik át a lapváltó gombjaira
            setBarMode(true, animate = true)
            if (barRow.alpha < 1f) barRow.animate().alpha(1f).setDuration(220).start()
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
    /** A kezdőlap legutóbbi képe (az új lap nyitásakor ebből rajzolódik ki a növekvő ablak). */
    private var startShot: Bitmap? = null

    private fun rememberStartShot(tab: Tab) {
        val b = tab.thumbnail ?: return
        if (Urls.isStart(tab.url) && !b.isRecycled) startShot = b
    }

    private fun openTabFromOverview(tab: Tab, notify: Boolean = true) {
        if (!overviewShown) {
            selectTab(tab, animate = false)
            return
        }
        overviewBusy = true
        overviewAnim?.cancel()
        // az új kártya a rácsban rejtve marad (nem villan fel üres kártya)
        heroTab = tab
        if (notify) tabAdapter.notifyItemInserted(tabs.indexOf(tab))

        // Mint a Safariban: az új lap alulról, abból a sarokból nő ki, ahová a rácsban kerül
        // (bal oszlop: bal alulról, jobb oszlop: jobb alulról).
        val col = tabs.indexOf(tab).coerceAtLeast(0) % 2
        val anyCard = tabList.getChildAt(0)?.findViewById<View>(R.id.tabCard)
        val cw = (anyCard?.width?.toFloat()?.takeIf { it > 0f }) ?: (hero.width * 0.44f)
        val ch = (anyCard?.height?.toFloat()?.takeIf { it > 0f }) ?: (cw * 1.32f)
        val margin = dp(18).toFloat()
        val left = if (col == 0) margin else hero.width - margin - cw
        val top = hero.height - ch * 0.45f
        val s = dp(22).toFloat()
        val from = if (stackLayout) {
            // halomban az új lap a halom aljára kerül: alulról, középről nő ki
            val sw = cw * 0.86f
            val sh = ch * 0.86f
            val sl = (hero.width - sw) / 2f
            val st = hero.height - sh * 0.4f
            RectF(sl, st, sl + sw, st + sh)
        } else RectF(left, top, left + cw, top + ch)
        val full = RectF(0f, 0f, hero.width.toFloat(), hero.height.toFloat())
        val night = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES
        val color = if (Urls.isStart(tab.url.orEmpty())) {
            if (night) 0xFF0E1016.toInt() else 0xFFF7F8FB.toInt()
        } else if (night) 0xFF121212.toInt() else Color.WHITE
        hero.animate().cancel()
        // a kezdőlap képe már a megnyomástól látszik a növekvő ablakban (elmosódottból élesedve)
        val shot = if (Urls.isStart(tab.url)) {
            startShot?.takeIf { !it.isRecycled }
                ?: tabs.firstOrNull { Urls.isStart(it.url) && it.thumbnail != null && it !== tab }?.thumbnail
        } else null
        hero.setup(shot, full, from, screenRadius, s, color)
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
            hero.alpha = (f / 0.12f).coerceIn(0f, 1f)
            if (shot != null && Build.VERSION.SDK_INT >= 31) {
                // elmosódottból élesedik, ahogy nő
                val r = 36f * resources.displayMetrics.density * (1f - f) * (1f - f)
                hero.setRenderEffect(
                    if (r < 0.5f) null else RenderEffect.createBlurEffect(r, r, Shader.TileMode.CLAMP)
                )
            }
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
                if (Build.VERSION.SDK_INT >= 31) hero.setRenderEffect(null)
                // a lap a valódi oldalba úszik át, amint az kirajzolódott
                whenPageDrawn(tab, 1500L) { fadeOutHero() }
            }
        })
        overviewAnim = anim
        anim.start()
    }

    /**
     * Az oldal fejlécének háttérszíne (CSS-ből), az állapotsor mögötti térköz egyszínű
     * kitöltéséhez. Így nem nyúlnak fel az ikonok és a fejléc elemei az állapotsor mögé.
     */
    private var padRecheckPending = false
    /** Az utolsó görgetés ideje (uptimeMillis): görgetés közben a ritkább mérések várnak. */
    private var lastScrollAt = 0L
    private val padTrail = Runnable {
        activeTab?.let { probePadColor(it, 0L) }
        probeStatusBand()
    }
    private val padRecheck = Runnable {
        padRecheckPending = false
        val t = activeTab
        t?.let {
            // Görgetés közben (az oldal tetejétől távol) a JS-lekérdezés eredményét semmi sem
            // használná fel: ilyenkor kihagyjuk (a görgetés végén a padTrail úgyis megnézi).
            if (effScroll(it) < statusTop || (it.fixedHeader && it.headerColor == 0)) probePadColor(it, 0L)
            handler.postDelayed({ probeHeaderConsistency(it) }, 40L)
        }
        // ismert színű rögzített fejlécnél az állapotsor mögött egyszínű kitöltés van: nincs mit mérni
        if (t == null || !t.fixedHeader || t.headerColor == 0) probeStatusBand()
    }

    /** Sites (host -> header color) whose fixed header was detected once. */
    private val hdrHosts by lazy { getSharedPreferences("header_hosts", MODE_PRIVATE) }

    private fun hostOf(url: String?): String =
        try { Uri.parse(url.orEmpty()).host.orEmpty().removePrefix("www.") } catch (e: Exception) { "" }

    private fun rememberedHeader(url: String?): Int {
        val h = hostOf(url)
        return if (h.isEmpty()) 0 else hdrHosts.getInt(h, 0)
    }

    private fun rememberHeader(tab: Tab, color: Int) {
        if (tab.isPrivate || color == 0) return
        val h = hostOf(tab.url)
        if (h.isNotEmpty() && hdrHosts.getInt(h, 0) != color) hdrHosts.edit().putInt(h, color).apply()
    }

    private val padProbe: Bitmap = Bitmap.createBitmap(96, 1, Bitmap.Config.ARGB_8888)
    private val padPixels = IntArray(96)
    /** A sor színeinek megszámolásához (HashMap és dobozolás nélkül, nincs foglalás méréskor). */
    private val padKeys = IntArray(96)
    private val padCounts = IntArray(96)
    /** Egyszerre csak egy mérés használja a [padProbe] képet. */
    private var padProbeBusy = false

    /**
     * A fejléc színe a valóban kirajzolt képből (közvetlenül az állapotsor alatti sorból): a
     * leggyakoribb szín, így az ikonok nem számítanak bele. (A CSS-ben megadott szín sötétített
     * oldalaknál félrevezető, attól lett fehér a sáv.)
     */
    private fun probePadColor(tab: Tab, delay: Long) {
        if (!flowLayout()) return
        handler.postDelayed({
            val wv = tab.webView
            if (tab !== activeTab || overviewShown || searchShown || capturing) return@postDelayed
            // van-e az oldal tetején rögzített fejléc (a térköz-szkript jegyzi meg)
            // Van-e az oldalnak rögzített fejléce, és most éppen látszik-e. Mint a Safariban: ha van
            // fejléc, az állapotsor tömören felveszi a színét, és az is marad (görgetéskor is).
            wv.evaluateJavascript(
                "(window.__gbHdrEls&&window.__gbHdrEls.length?1:0)+','+(window.__gbHdrNow?window.__gbHdrNow():0)"
            ) { r ->
                val parts = r.orEmpty().trim('"').split(',')
                val now = parts.getOrNull(1) == "1"
                // Betöltéskor (az oldal tetején) dől el, van-e fejléc, és akkor mérjük a színét is;
                // utána a szín már nem változik görgetéskor (mint a Safariban).
                if (!tab.fixedHeader && now && effScroll(tab) < statusTop) {
                    tab.fixedHeader = true
                    if (tab === activeTab) updateLens()
                }
                if (tab.fixedHeader && tab.headerColor == 0 && now) probeRow(tab, header = true)
            }
            if (effScroll(tab) != 0) return@postDelayed
            probeRow(tab, header = false)
        }, delay)
    }

    /** Az állapotsor alatti sor leggyakoribb színe (a fejléc / az oldal tetejének színe). */
    private fun colorClose(a: Int, b: Int): Boolean =
        kotlin.math.abs(Color.red(a) - Color.red(b)) + kotlin.math.abs(Color.green(a) - Color.green(b)) +
            kotlin.math.abs(Color.blue(a) - Color.blue(b)) < 36

    /** Görgetés közben: ugyanaz a szín maradt-e az állapotsor alatt (lásd [probeRow]). */
    private fun probeHeaderConsistency(tab: Tab) {
        if (!flowLayout() || tab.fixedHeader || tab.noHeader || tab.padColor == 0) return
        val sc = effScroll(tab)
        tab.maxScroll = maxOf(tab.maxScroll, sc)
        if (sc < dp(60) || overviewShown || searchShown || capturing) return
        probeRow(tab, header = false, consistency = true)
    }

    private fun probeRow(tab: Tab, header: Boolean, consistency: Boolean = false) {
        run {
            val w = scene.width
            val y = statusTop + dp(2)
            if (w <= 0 || y + 1 >= scene.height) return@run
            // ne fusson egyszerre két mérés ugyanabba a képbe (a következő ütem úgyis megismétli)
            if (padProbeBusy) return@run
            padProbeBusy = true
            try {
                PixelCopy.request(window, Rect(0, y, w, y + 1), padProbe, { result ->
                    padProbeBusy = false
                    if (result != PixelCopy.SUCCESS || tab !== activeTab) return@request
                    padProbe.getPixels(padPixels, 0, 96, 0, 0, 96, 1)
                    // a leggyakoribb szín (5 bites csatornákkal), foglalás nélkül
                    var bestKey = -1
                    var bestCount = 0
                    var nk = 0
                    for (px in padPixels) {
                        val key = ((Color.red(px) shr 3) shl 10) or ((Color.green(px) shr 3) shl 5) or (Color.blue(px) shr 3)
                        var j = 0
                        while (j < nk && padKeys[j] != key) j++
                        if (j == nk) {
                            padKeys[nk] = key
                            padCounts[nk] = 0
                            nk++
                        }
                        padCounts[j]++
                        if (padCounts[j] > bestCount) {
                            bestCount = padCounts[j]
                            bestKey = key
                        }
                    }
                    if (bestCount <= 0) return@request
                    val c = if (bestCount >= 96 * 0.45f) {
                        var r = 0
                        var g = 0
                        var b = 0
                        var n = 0
                        for (px in padPixels) {
                            val key = ((Color.red(px) shr 3) shl 10) or ((Color.green(px) shr 3) shl 5) or (Color.blue(px) shr 3)
                            if (key == bestKey) {
                                r += Color.red(px)
                                g += Color.green(px)
                                b += Color.blue(px)
                                n++
                            }
                        }
                        Color.rgb(r / n, g / n, b / n)
                    } else {
                        0
                    }
                    if (consistency) {
                        // Képi fejléc-felismerés (bármilyen oldalon, pl. uraharashop.hu): ha görgetés
                        // után is ugyanaz a szín van közvetlenül az állapotsor alatt, mint betöltéskor,
                        // akkor ott egy fejléc áll -> az állapotsor tömören ezt a színt kapja.
                        if (tab.fixedHeader || tab.noHeader) return@request
                        val share = bestCount / 96f
                        if (c != 0 && share >= 0.75f && tab.padColor != 0 && colorClose(c, tab.padColor)) {
                            tab.hdrHits++
                            if (tab.hdrHits >= 2 && tab.maxScroll >= dp(140)) {
                                tab.fixedHeader = true
                                tab.headerColor = tab.padColor
                                rememberHeader(tab, tab.headerColor)
                                updateLens()
                            }
                        } else {
                            tab.hdrHits = 0
                            if (tab.maxScroll > dp(160)) tab.noHeader = true
                        }
                        return@request
                    }
                    if (header) {
                        if (c != 0 && c != tab.headerColor) {
                            tab.headerColor = c
                            rememberHeader(tab, c)
                            updateLens()
                        }
                    } else if (c != tab.padColor) {
                        tab.padColor = c
                        updateLens()
                    }
                    handler.postDelayed({ probeStatusBand() }, 50L)
                }, handler)
            } catch (e: Exception) {
                // marad az eddigi
                padProbeBusy = false
            }
        }
    }

    private fun parseCssColor(css: String): Int {
        val m = Regex("rgba?\\(([^)]*)\\)").find(css) ?: return 0
        val p = m.groupValues[1].split(",").map { it.trim() }
        if (p.size < 3) return 0
        val r = p[0].toFloatOrNull() ?: return 0
        val g = p[1].toFloatOrNull() ?: return 0
        val b = p[2].toFloatOrNull() ?: return 0
        val a = p.getOrNull(3)?.toFloatOrNull() ?: 1f
        if (a < 0.95f) return 0
        return Color.rgb(r.toInt().coerceIn(0, 255), g.toInt().coerceIn(0, 255), b.toInt().coerceIn(0, 255))
    }

    /** Belső görgetés jelzése az oldalról (CSS képpont). */
    inner class ScrollBridge(private val tab: Tab) {
        @android.webkit.JavascriptInterface
        fun inner(y: Int) {
            handler.post {
                val px = (y * resources.displayMetrics.density).toInt()
                val dy = px - tab.innerScroll
                tab.innerScroll = px
                if (tab === activeTab && !overviewShown) {
                    lastScrollAt = android.os.SystemClock.uptimeMillis()
                    updateTopFade(effScroll(tab))
                    if (docked && !dockTop) onDockScroll(effScroll(tab), dy)
                    onTranslateScroll(dy)
                    updateLens()
                    if (!padRecheckPending) {
                        padRecheckPending = true
                        handler.postDelayed(padRecheck, 120L)
                    }
                    handler.removeCallbacks(padTrail)
                    handler.postDelayed(padTrail, 260L)
                }
            }
        }
    }

    /** A lap tényleges görgetése: a WebView-é, vagy (ha az oldal belül görget) a belső elemé. */
    private fun effScroll(tab: Tab?): Int {
        if (tab == null) return 0
        return maxOf(tab.webView.scrollY, tab.innerScroll)
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
        hero.animate().alpha(0f).setDuration(110)
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
        HighRefresh.apply(this)
        if (::scene.isInitialized) HighRefresh.view(scene)
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
            val startRect = if (opening) heroStartRect else null
            hero.setup(tab.thumbnail, startRect ?: webRect(), card, screenRadius, dp(22).toFloat(), bgColor)
            if (startRect != null) {
                // a felhúzott oldal helyét a kép veszi át, az oldal maga visszaáll (a lapváltó alatt)
                heroStartRect = null
                pendingPageReset = true
            }
            // felfelé legyintésnél a kép előbb a képernyő közepére húzódik, onnan úszik a helyére
            hero.via = if (opening && overviewViaCenter) {
                val cw = card.width() * 1.18f
                val ch = card.height() * 1.18f
                val cx = scene.width / 2f
                val cy = scene.height * 0.46f
                RectF(cx - cw / 2f, cy - ch / 2f, cx + cw / 2f, cy + ch / 2f)
            } else null
            overviewViaCenter = false
            hero.progress = if (opening) 0f else 1f
            heroHold++
            hero.animate().cancel()
            if (Build.VERSION.SDK_INT >= 31) hero.setRenderEffect(null)
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
        anim.duration = if (opening) (if (hero.via != null) 600L else 520L) else 480L
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
            // a felhúzott oldal csak akkor áll vissza, amikor már a lapváltó takarja
            if (pendingPageReset && overview.alpha >= 0.99f) {
                pendingPageReset = false
                resetPageTransform()
            }
            updateLens()
        }
        // a kártyák rácsa az animáció alatt kész rétegként mozog / halványul (nem rajzolódik újra
        // minden képkockában)
        tabList.setLayerType(View.LAYER_TYPE_HARDWARE, null)
        anim.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                tabList.setLayerType(View.LAYER_TYPE_NONE, null)
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
        if (::privateBtn.isInitialized) fade(privateBtn, overviewMode, animate)
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
        hideRowActions(immediate = true)
        searchList.animate().cancel()
        searchList.translationX = 0f
        searchList.alpha = 1f
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
                    if (url != null && !searchSwiping) {
                        r.setOnLongClickListener {
                            showRowActions(r, item)
                            true
                        }
                    } else {
                        r.setOnLongClickListener(null)
                        r.isLongClickable = false
                    }
                }
            }

            if (!searchNoAnim && searchKeys.getOrNull(i) != key && animated < 14) {
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
            showTranslateBar(tab, 0)
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
        return skin
    }

    private var copyToast: TextView? = null

    // ------------------------------------------------ kereső felület: hosszú nyomás, oldalra húzás

    /** Hosszú nyomásra a sor fölött megjelenő üvegkapszula (Másolás / Törlés). */
    private var rowActions: LinearLayout? = null
    private var rowCatcher: View? = null
    private var rowActionRow: View? = null

    private fun showRowActions(row: View, item: SuggestItem) {
        val url = item.url ?: return
        haptic()
        hideRowActions(immediate = true)
        val col = menuTextColor()
        val bar = LinearLayout(this)
        bar.orientation = LinearLayout.HORIZONTAL
        bar.gravity = Gravity.CENTER_VERTICAL
        bar.setPadding(dp(6), 0, dp(6), 0)
        bar.background = glassBubble(dp(100).toFloat())
        fun action(icon: Int, label: String, tint: Int, onTap: () -> Unit): TextView {
            val t = TextView(this)
            t.text = label
            t.textSize = 14.5f
            t.setTypeface(t.typeface, android.graphics.Typeface.BOLD)
            t.setTextColor(tint)
            t.gravity = Gravity.CENTER
            t.setPadding(dp(14), 0, dp(14), 0)
            t.compoundDrawablePadding = dp(8)
            val ic = ContextCompat.getDrawable(this, icon)?.mutate()
            ic?.setBounds(0, 0, dp(18), dp(18))
            t.setCompoundDrawablesRelative(ic, null, null, null)
            t.compoundDrawableTintList = ColorStateList.valueOf(tint)
            t.setOnClickListener { onTap() }
            t.setOnTouchListener { v, ev ->
                when (ev.actionMasked) {
                    MotionEvent.ACTION_DOWN -> v.animate().scaleX(0.9f).scaleY(0.9f).setDuration(120).start()
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL ->
                        v.animate().scaleX(1f).scaleY(1f).setDuration(260).setInterpolator(OvershootInterpolator(3f)).start()
                }
                false
            }
            bar.addView(t, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT))
            return t
        }
        action(R.drawable.ic_m_copy, L.t("Másolás"), col) {
            val cm = getSystemService(android.content.ClipboardManager::class.java)
            cm?.setPrimaryClip(android.content.ClipData.newPlainText("URL", url))
            hideRowActions()
            showCopyToast()
        }
        // nyitott lapot nem törlünk innen (ott a lapváltó a helye)
        if (item.tab == null) {
            val sep = View(this)
            sep.setBackgroundColor(ColorUtils.setAlphaComponent(col, 50))
            bar.addView(sep, LinearLayout.LayoutParams(dp(1), dp(22)))
            action(R.drawable.ic_m_delete, L.t("Törlés"), 0xFFFF453A.toInt()) {
                hideRowActions()
                deleteSearchRow(row, item, url)
            }
        }
        // a képernyő többi részére koppintva eltűnik
        val catcher = View(this)
        catcher.setOnTouchListener { _, ev ->
            if (ev.actionMasked == MotionEvent.ACTION_DOWN) hideRowActions()
            true
        }
        root.addView(catcher, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        val lp = FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(48))
        lp.gravity = Gravity.TOP or Gravity.START
        root.addView(bar, lp)
        bar.measure(
            View.MeasureSpec.makeMeasureSpec(root.width, View.MeasureSpec.AT_MOST),
            View.MeasureSpec.makeMeasureSpec(dp(48), View.MeasureSpec.EXACTLY)
        )
        val loc = IntArray(2)
        val rloc = IntArray(2)
        row.getLocationInWindow(loc)
        root.getLocationInWindow(rloc)
        val rowTop = loc[1] - rloc[1]
        val w = bar.measuredWidth
        var y = rowTop - dp(48) - dp(6)
        // a fülek sávja alá ne csússzon: ilyenkor a sor alá kerül
        val minY = (segBar.top + segBar.translationY + segBar.height).toInt() + dp(4)
        val below = y < minY
        if (below) y = rowTop + row.height + dp(6)
        bar.translationX = ((root.width - w) / 2f)
        bar.translationY = y.toFloat()
        bar.pivotX = w / 2f
        bar.pivotY = if (below) 0f else dp(48).toFloat()
        bar.alpha = 0f
        bar.scaleX = 0.6f
        bar.scaleY = 0.6f
        bar.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(380)
            .setInterpolator(OvershootInterpolator(2f))
            .setUpdateListener { updateLens() }.start()
        // a megnyomott sor kicsit „felemelkedik”
        row.animate().cancel()
        row.animate().scaleX(0.97f).scaleY(0.97f).setDuration(200).setInterpolator(DecelerateInterpolator()).start()
        rowActions = bar
        rowCatcher = catcher
        rowActionRow = row
        updateLens()
    }

    private fun hideRowActions(immediate: Boolean = false) {
        val bar = rowActions ?: return
        rowActions = null
        rowCatcher?.let { root.removeView(it) }
        rowCatcher = null
        rowActionRow?.animate()?.scaleX(1f)?.scaleY(1f)?.setDuration(260)?.setInterpolator(OvershootInterpolator(2f))?.start()
        rowActionRow = null
        bar.animate().cancel()
        if (immediate) {
            root.removeView(bar)
            updateLens()
            return
        }
        // a kapszula a lens rajzolásához még egy darabig a rowActions-ben marad
        bar.animate().alpha(0f).scaleX(0.85f).scaleY(0.85f).setDuration(200)
            .setInterpolator(DecelerateInterpolator())
            .setUpdateListener {
                rowActionsFading = bar
                updateLens()
            }
            .withEndAction {
                if (rowActionsFading === bar) rowActionsFading = null
                root.removeView(bar)
                updateLens()
            }.start()
    }

    private var rowActionsFading: LinearLayout? = null

    /** A sor összecsukódik és eltűnik, a többi felcsúszik a helyére. */
    private fun deleteSearchRow(row: View, item: SuggestItem, url: String) {
        haptic()
        when {
            searchTab == 1 || item.icon == R.drawable.ic_bookmark -> {
                BookmarkStore.remove(applicationContext, url)
                if (searchTab != 1) HistoryStore.remove(applicationContext, url)
            }
            else -> HistoryStore.remove(applicationContext, url)
        }
        val h = row.height
        row.animate().cancel()
        row.animate().alpha(0f).translationX(-dp(40).toFloat()).setDuration(180)
            .setInterpolator(DecelerateInterpolator()).withEndAction {
                val anim = ValueAnimator.ofInt(h, 0)
                anim.duration = 220
                anim.interpolator = DecelerateInterpolator(1.6f)
                anim.addUpdateListener {
                    val lp = row.layoutParams
                    lp.height = it.animatedValue as Int
                    row.layoutParams = lp
                }
                anim.addListener(object : android.animation.AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: android.animation.Animator) {
                        searchList.removeView(row)
                        // a maradék sorok már a helyükön vannak: ne ússzanak be újra
                        renderSearchQuiet()
                    }
                })
                anim.start()
            }.start()
    }

    /** Újrarajzolás beúszó animáció nélkül (törlés után). */
    private fun renderSearchQuiet() {
        searchList.removeAllViews()
        searchKeys = emptyList()
        searchNoAnim = true
        renderSearch()
        searchNoAnim = false
    }

    private var searchNoAnim = false

    /** Oldalra húzás a kereső felületen: fülváltás (Találatok / Könyvjelzők / Előzmények). */
    private var searchSwiping = false
    private var swipeDownX = 0f
    private var swipeDownY = 0f
    private var swipeTracking = false

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        if (searchShown && rowActions == null && searchPage.visibility == View.VISIBLE && searchPage.alpha > 0.9f) {
            when (ev.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    val scroll = findViewById<View>(R.id.searchScroll)
                    val loc = IntArray(2)
                    scroll.getLocationOnScreen(loc)
                    swipeTracking = ev.rawY >= loc[1] && ev.rawY <= loc[1] + scroll.height
                    swipeDownX = ev.rawX
                    swipeDownY = ev.rawY
                    searchSwiping = false
                }
                MotionEvent.ACTION_MOVE -> if (swipeTracking) {
                    val dx = ev.rawX - swipeDownX
                    val dy = ev.rawY - swipeDownY
                    if (!searchSwiping) {
                        if (kotlin.math.abs(dx) > dp(18) && kotlin.math.abs(dx) > kotlin.math.abs(dy) * 1.6f) {
                            searchSwiping = true
                            // a sorok ne kapják meg a koppintást / hosszú nyomást
                            val cancel = MotionEvent.obtain(ev)
                            cancel.action = MotionEvent.ACTION_CANCEL
                            super.dispatchTouchEvent(cancel)
                            cancel.recycle()
                        } else if (kotlin.math.abs(dy) > dp(18)) {
                            swipeTracking = false
                        }
                    }
                    if (searchSwiping) {
                        // a lista követi az ujjat; a szélső fülön túl gumiszerűen ellenáll
                        val edge = (searchTab == 0 && dx > 0) || (searchTab == 2 && dx < 0)
                        val follow = if (edge) dx * 0.18f else dx * 0.45f
                        searchList.translationX = follow
                        searchList.alpha = 1f - (kotlin.math.abs(follow) / (root.width * 0.9f)).coerceIn(0f, 0.6f)
                        return true
                    }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> if (searchSwiping) {
                    val dx = ev.rawX - swipeDownX
                    searchSwiping = false
                    swipeTracking = false
                    val next = when {
                        dx < -dp(64) && searchTab < 2 -> searchTab + 1
                        dx > dp(64) && searchTab > 0 -> searchTab - 1
                        else -> searchTab
                    }
                    if (next != searchTab && ev.actionMasked == MotionEvent.ACTION_UP) {
                        haptic()
                        val dir = if (next > searchTab) 1f else -1f
                        searchList.animate().cancel()
                        searchList.animate().translationX(-dir * dp(60)).alpha(0f).setDuration(110)
                            .setInterpolator(DecelerateInterpolator())
                            .withEndAction {
                                selectSearchTab(next, animate = true)
                                searchList.translationX = dir * dp(60)
                                searchList.animate().translationX(0f).alpha(1f).setDuration(320)
                                    .setInterpolator(DecelerateInterpolator(2f)).start()
                            }.start()
                    } else {
                        searchList.animate().cancel()
                        searchList.animate().translationX(0f).alpha(1f).setDuration(360)
                            .setInterpolator(OvershootInterpolator(1.6f)).start()
                    }
                    return true
                }
            }
        }
        return super.dispatchTouchEvent(ev)
    }

    /** Kis üvegkapszula a sáv fölött: "Vágólapra másolva", rugózva jelenik meg és tűnik el. */
    private fun showCopyToast(
        message: String = L.t("Vágólapra másolva"),
        icon: Int = R.drawable.ic_m_check,
        stayMs: Long = 1500L,
        onTap: (() -> Unit)? = null
    ) {
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
        tv.text = message
        ContextCompat.getDrawable(this, icon)?.mutate()?.let { ic ->
            ic.setBounds(0, 0, dp(18), dp(18))
            tv.setCompoundDrawablesRelative(ic, null, null, null)
        }
        if (onTap != null) {
            tv.setOnClickListener {
                copyToastHide.run()
                onTap()
            }
        } else {
            tv.setOnClickListener(null)
            tv.isClickable = false
        }
        val col = menuTextColor()
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
        tv.postDelayed(copyToastHide, stayMs)
    }

    /** A rendszer letöltéskezelője szól, ha egy (általunk indított) letöltés befejeződött. */
    private val downloadDone = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: android.content.Context?, intent: Intent?) {
            val id = intent?.getLongExtra(android.app.DownloadManager.EXTRA_DOWNLOAD_ID, -1L) ?: return
            if (id < 0 || id !in Downloads.ids(this@MainActivity)) return
            val dm = getSystemService(android.app.DownloadManager::class.java) ?: return
            var ok = false
            try {
                dm.query(android.app.DownloadManager.Query().setFilterById(id))?.use { c ->
                    if (c.moveToFirst()) {
                        ok = c.getInt(c.getColumnIndexOrThrow(android.app.DownloadManager.COLUMN_STATUS)) ==
                            android.app.DownloadManager.STATUS_SUCCESSFUL
                    }
                }
            } catch (e: Exception) {
                return
            }
            if (ok) {
                showCopyToast(L.t("A letöltés kész"), R.drawable.ic_m_download, 2600L) {
                    snapBackdrop { startActivity(Intent(this@MainActivity, DownloadsActivity::class.java)) }
                }
            } else {
                showCopyToast(L.t("A letöltés nem sikerült"), R.drawable.ic_close, 2200L)
            }
        }
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

    /** A fordító kapszula állapota: 0 = ajánlat, 1 = fordít, 2 = kész, 3 = hiba. */
    private var trState = 0
    /** Igaz, ha a kapszulának látszania kell (görgetéskor csak ideiglenesen úszik ki). */
    private var trWanted = false
    private var trScrolledAway = false

    private fun ensureTranslateBar(): LinearLayout {
        trBar?.let { return it }
        val bar = LinearLayout(this)
        bar.orientation = LinearLayout.HORIZONTAL
        bar.gravity = Gravity.CENTER_VERTICAL
        bar.setPadding(dp(14), 0, dp(4), 0)
        // teljesen lekerekített üvegkapszula (a többi üvegfelülettel együtt változik)
        bar.background = glassBubble(dp(100).toFloat())
        bar.isClickable = true
        val icon = ImageView(this)
        icon.setImageResource(R.drawable.ic_m_translate)
        icon.imageTintList = ColorStateList.valueOf(0xFF0A84FF.toInt())
        bar.addView(icon, LinearLayout.LayoutParams(dp(18), dp(18)))
        val t = TextView(this)
        t.textSize = 14f
        t.maxLines = 1
        t.ellipsize = TextUtils.TruncateAt.END
        val tlp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        tlp.marginStart = dp(8)
        bar.addView(t, tlp)
        val action = TextView(this)
        action.textSize = 14f
        action.setTextColor(0xFF0A84FF.toInt())
        action.setTypeface(action.typeface, android.graphics.Typeface.BOLD)
        action.setPadding(dp(12), dp(8), dp(8), dp(8))
        bar.addView(action)
        val close = ImageButton(this)
        close.setImageResource(R.drawable.ic_close)
        close.background = null
        close.setPadding(dp(9), dp(9), dp(9), dp(9))
        close.scaleType = ImageView.ScaleType.FIT_CENTER
        close.setOnClickListener {
            trTab?.let { trDismissed.add(Urls.host(it.url)) }
            hideTranslateBar()
        }
        bar.addView(close, LinearLayout.LayoutParams(dp(34), dp(34)))
        val lp = FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(42))
        lp.gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
        root.addView(bar, lp)
        bar.visibility = View.GONE
        bar.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> updateLens() }
        trBar = bar
        trTitle = t
        trSub = null
        trAction = action
        trClose = close
        return bar
    }

    private var trClose: ImageButton? = null

    private var trMorph: ValueAnimator? = null

    /** [state]: 0 = ajánlat, 1 = fordít, 2 = kész, 3 = hiba. */
    private fun showTranslateBar(tab: Tab, state: Int) {
        val bar = ensureTranslateBar()
        // a kapszula a régi méretéről nő / húzódik az újra (ha már látszik)
        val morphFrom = if (bar.visibility == View.VISIBLE && !trScrolledAway && bar.width > 0 && trState != state) bar.width else -1
        trTab = tab
        trState = state
        trWanted = true
        val text = menuTextColor()
        trTitle?.setTextColor(text)
        trClose?.imageTintList = ColorStateList.valueOf(ColorUtils.setAlphaComponent(text, 160))
        val target = langName(translateTarget())
        val from = if (trFrom.isNotEmpty()) langName(trFrom) else ""
        trTitle?.text = when (state) {
            1 -> L.t("Fordítás…")
            2 -> (if (from.isNotEmpty()) "$from → " else "") + target
            3 -> L.t("Nem sikerült lefordítani")
            else -> if (from.isNotEmpty()) "$from → $target" else L.t("Lefordítod az oldalt?")
        }
        trAction?.text = when (state) {
            1 -> ""
            2 -> L.t("Eredeti")
            3 -> L.t("Újra")
            else -> L.t("Fordítás")
        }
        trAction?.visibility = if (state == 1) View.GONE else View.VISIBLE
        trAction?.setOnClickListener {
            haptic()
            if (trState == 2) undoTranslate() else startTranslate()
        }
        val lp = bar.layoutParams as FrameLayout.LayoutParams
        lp.topMargin = (if (dockTop) statusTop + dockHeight() else statusTop) + dp(8)
        if (morphFrom > 0) {
            morphTranslateBar(bar, morphFrom)
        } else {
            trMorph?.cancel()
            lp.width = ViewGroup.LayoutParams.WRAP_CONTENT
        }
        bar.layoutParams = lp
        if (bar.visibility != View.VISIBLE || trScrolledAway) {
            trScrolledAway = false
            popTranslateBar(true)
        }
    }

    /**
     * A fordító kapszula tartalma megváltozott (pl. „Fordítás…” -> „Angol → Magyar  Eredeti”):
     * a kapszula rugózva nyúlik az új méretre, a tartalom közben finoman beúszik, a szélek alatt
     * az üveg végig követi.
     */
    private fun morphTranslateBar(bar: LinearLayout, fromW: Int) {
        trMorph?.cancel()
        bar.measure(
            View.MeasureSpec.makeMeasureSpec(root.width - dp(24), View.MeasureSpec.AT_MOST),
            View.MeasureSpec.makeMeasureSpec(dp(42), View.MeasureSpec.EXACTLY)
        )
        val toW = bar.measuredWidth
        if (toW <= 0 || kotlin.math.abs(toW - fromW) < 2) {
            (bar.layoutParams as FrameLayout.LayoutParams).width = ViewGroup.LayoutParams.WRAP_CONTENT
            return
        }
        val lp = bar.layoutParams as FrameLayout.LayoutParams
        lp.width = fromW
        // a tartalom (szöveg, gomb) késleltetve, oldalról úszik be
        for (i in 1 until bar.childCount) {
            val c = bar.getChildAt(i)
            if (c.visibility != View.VISIBLE) continue
            c.animate().cancel()
            c.alpha = 0f
            c.translationX = if (toW > fromW) -dp(10).toFloat() else dp(6).toFloat()
            c.animate().alpha(1f).translationX(0f).setStartDelay(90L + 40L * i).setDuration(300L)
                .setInterpolator(DecelerateInterpolator(1.8f)).start()
        }
        bar.getChildAt(0)?.let { ic ->
            ic.animate().cancel()
            ic.rotation = -30f
            ic.animate().rotation(0f).setDuration(520L).setInterpolator(OvershootInterpolator(2.2f)).start()
        }
        val anim = ValueAnimator.ofFloat(0f, 1f)
        anim.duration = 520L
        anim.interpolator = OvershootInterpolator(1.15f)
        anim.addUpdateListener {
            val t = it.animatedValue as Float
            val w = (fromW + (toW - fromW) * t).toInt().coerceAtLeast(dp(42))
            val l = bar.layoutParams as FrameLayout.LayoutParams
            if (l.width != w) {
                l.width = w
                bar.layoutParams = l
            }
            // nyúlás közben kicsit lapul (mint egy folyadékcsepp)
            val g = kotlin.math.sin(Math.PI * it.animatedFraction).toFloat()
            bar.scaleY = 1f - 0.05f * g
            updateLens()
        }
        anim.addListener(object : android.animation.AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: android.animation.Animator) {
                if (trMorph !== anim) return
                trMorph = null
                bar.scaleY = 1f
                val l = bar.layoutParams as FrameLayout.LayoutParams
                l.width = ViewGroup.LayoutParams.WRAP_CONTENT
                bar.layoutParams = l
                updateLens()
            }
        })
        trMorph = anim
        anim.start()
    }

    /** Megjelenés / eltűnés: felülről rugózva úszik be, és felfelé úszik ki. */
    private fun popTranslateBar(show: Boolean, gone: Boolean = false) {
        val bar = trBar ?: return
        bar.animate().cancel()
        if (show) {
            if (bar.visibility != View.VISIBLE) {
                bar.visibility = View.VISIBLE
                bar.alpha = 0f
                bar.translationY = -dp(20).toFloat()
                bar.scaleX = 0.85f
                bar.scaleY = 0.85f
            }
            bar.animate().alpha(1f).translationY(0f).scaleX(1f).scaleY(1f).setDuration(360)
                .setInterpolator(OvershootInterpolator(1.4f))
                .setUpdateListener { updateLens() }.start()
        } else {
            bar.animate().alpha(0f).translationY(-dp(24).toFloat()).scaleX(0.9f).scaleY(0.9f).setDuration(220)
                .setInterpolator(DecelerateInterpolator())
                .setUpdateListener { updateLens() }
                .withEndAction {
                    if (gone) bar.visibility = View.GONE
                    updateLens()
                }.start()
        }
    }

    /** Görgetés: lefelé a kapszula kiúszik, a másik irányba visszajön. */
    private fun onTranslateScroll(dy: Int) {
        if (!trWanted || trBar == null) return
        if (dy > 8 && !trScrolledAway) {
            trScrolledAway = true
            popTranslateBar(false)
        } else if (dy < -8 && trScrolledAway) {
            trScrolledAway = false
            popTranslateBar(true)
        }
    }

    private fun hideTranslateBar() {
        trWanted = false
        trScrolledAway = false
        val bar = trBar ?: return
        if (bar.visibility != View.VISIBLE) return
        popTranslateBar(false, gone = true)
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
        showTranslateBar(tab, 1)
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
            if (texts == null || texts.length() == 0 || !tab.trPending) {
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
            // a szövegen belüli sortörés összezavarná a darabolást
            val t = texts.optString(k).replace(Regex("\\s+"), " ")
            if (cur.isNotEmpty() && (len + t.length > 1500 || cur.size >= 60)) {
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
                            if (tab === activeTab) showTranslateBar(tab, 1)
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

    /**
     * Egy részlet fordítása. A szövegeket sortöréssel összefűzve egyben küldjük el; ha a válasz
     * sorai nem egyeznek a részlet elemeivel, kettévágjuk a részletet, és úgy próbáljuk újra.
     * Visszaadja a fordításokat és a felismert forrásnyelvet.
     */
    private fun googleTranslate(list: List<String>, target: String): Pair<List<String>, String>? {
        if (list.isEmpty()) return emptyList<String>() to ""
        val r = gtRequest(list.joinToString("\n"), target) ?: return null
        val parts = r.first.split("\n")
        if (parts.size == list.size) return parts.map { it.trim() } to r.second
        if (list.size == 1) return listOf(r.first.trim()) to r.second
        val mid = list.size / 2
        val a = googleTranslate(list.subList(0, mid), target) ?: return null
        val b = googleTranslate(list.subList(mid, list.size), target) ?: return null
        return (a.first + b.first) to a.second.ifEmpty { b.second }
    }

    /** A Google Fordító (a böngészőbővítmények által is használt) végpontja. */
    private fun gtRequest(text: String, target: String): Pair<String, String>? {
        val base = "https://translate.googleapis.com/translate_a/single?client=gtx&sl=auto&tl=$target&dt=t&dj=1&ie=UTF-8&oe=UTF-8"
        val q = java.net.URLEncoder.encode(text, "UTF-8")
        for (post in listOf(false, true)) {
            try {
                val c = java.net.URL(if (post) base else "$base&q=$q").openConnection() as java.net.HttpURLConnection
                c.connectTimeout = 8000
                c.readTimeout = 12000
                c.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0 Mobile Safari/537.36")
                if (post) {
                    c.requestMethod = "POST"
                    c.doOutput = true
                    c.setRequestProperty("Content-Type", "application/x-www-form-urlencoded;charset=UTF-8")
                    c.outputStream.use { it.write(("q=$q").toByteArray(Charsets.UTF_8)) }
                }
                if (c.responseCode != 200) {
                    c.disconnect()
                    continue
                }
                val txt = c.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                c.disconnect()
                val o = JSONObject(txt)
                val sentences = o.optJSONArray("sentences") ?: continue
                val sb = StringBuilder()
                for (k in 0 until sentences.length()) {
                    sb.append(sentences.optJSONObject(k)?.optString("trans").orEmpty())
                }
                return sb.toString() to o.optString("src").substringBefore('-')
            } catch (e: Exception) {
                // a másik módszerrel próbáljuk
            }
        }
        return null
    }

    private fun onTranslateResult(tab: Tab, ok: Boolean) {
        if (!tab.trPending) return
        tab.trPending = false
        if (ok) {
            tab.translated = true
            if (tab === activeTab) showTranslateBar(tab, 2)
        } else if (tab === activeTab) {
            // nincs újratöltés / átirányítás: újra lehet próbálni
            showTranslateBar(tab, 3)
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

    // ---------------------------------------------------------------- privát böngészés

    private lateinit var privateBtn: ImageButton

    private fun privateProfileSupported(): Boolean = try {
        androidx.webkit.WebViewFeature.isFeatureSupported(androidx.webkit.WebViewFeature.MULTI_PROFILE)
    } catch (e: Throwable) {
        false
    }

    /** A lapváltó kapszulájában, a lapszám mellett bal oldalt: privát mód kapcsoló. */
    private fun setupPrivateButton() {
        val b = ImageButton(this)
        b.setImageResource(R.drawable.ic_private)
        b.setPadding(dp(8), dp(8), dp(8), dp(8))
        b.scaleType = ImageView.ScaleType.FIT_CENTER
        b.contentDescription = L.t("Privát böngészés")
        b.visibility = View.INVISIBLE
        b.alpha = 0f
        b.setOnClickListener {
            // a gomb összehúzódik, majd rugózva visszaugrik, közben vált a kitöltése
            b.animate().cancel()
            b.animate().scaleX(0.6f).scaleY(0.6f).rotation(if (privateMode) 0f else -12f).setDuration(110)
                .setInterpolator(DecelerateInterpolator())
                .withEndAction {
                    setPrivateMode(!privateMode)
                    b.animate().scaleX(1f).scaleY(1f).rotation(0f).setDuration(420)
                        .setInterpolator(OvershootInterpolator(3f)).start()
                }.start()
        }
        val lp = FrameLayout.LayoutParams(dp(38), dp(38))
        lp.gravity = Gravity.START or Gravity.CENTER_VERTICAL
        lp.marginStart = dp(7)
        barCenter.addView(b, lp)
        privateBtn = b
        updatePrivateUi()
    }

    /** A kapszula felirata kicserélődik: a régi felfelé úszik ki, az új alulról be. */
    private fun animateOverviewLabel() {
        val v = overviewLabel
        v.animate().cancel()
        v.animate().alpha(0f).translationY(-dp(10).toFloat()).setDuration(120)
            .setInterpolator(DecelerateInterpolator())
            .withEndAction {
                updateTabCount()
                v.translationY = dp(12).toFloat()
                v.animate().alpha(1f).translationY(0f).setDuration(300)
                    .setInterpolator(OvershootInterpolator(1.6f)).start()
            }.start()
    }

    private fun updatePrivateUi(animateLabel: Boolean = false) {
        if (!::privateBtn.isInitialized) return
        val c = contentColor ?: Color.WHITE
        if (privateMode) {
            privateBtn.background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(c)
            }
            val inv = if (Color.red(c) > 128) 0xFF1D1F24.toInt() else Color.WHITE
            privateBtn.imageTintList = ColorStateList.valueOf(inv)
        } else {
            privateBtn.background = null
            privateBtn.imageTintList = ColorStateList.valueOf(c)
        }
        if (animateLabel && barOverview) animateOverviewLabel() else updateTabCount()
    }

    /** Váltás a normál és a privát lapok között (a lapváltóban a rács átúszik). */
    private fun setPrivateMode(on: Boolean) {
        if (privateMode == on || overviewBusy) return
        haptic()
        if (on) normalActive = activeTab else privateActive = activeTab
        privateMode = on
        tabs = if (on) privateTabs else normalShown()
        updatePrivateUi(animateLabel = true)
        if (tabs.isEmpty()) {
            // első privát lap: egy új kezdőlap nyílik
            val t = createTab()
            t.url = homeUrl()
            t.pendingUrl = t.url
            tabAdapter.notifyDataSetChanged()
            updateTabCount()
            if (overviewShown) openTabFromOverview(t, notify = false) else selectTab(t)
            return
        }
        val target = (if (on) privateActive else normalActive)?.takeIf { it in tabs } ?: tabs.last()
        if (overviewShown) {
            tabList.animate().cancel()
            tabList.animate().alpha(0f).setDuration(130).withEndAction {
                selectTab(target, animate = false)
                tabAdapter.notifyDataSetChanged()
                updateTabCount()
                scrollTabListTo(tabs.indexOf(target).coerceAtLeast(0))
                tabList.animate().alpha(1f).setDuration(200).start()
            }.start()
        } else {
            selectTab(target)
        }
    }

    /** Az utolsó privát lap bezárása után: vissza a normál lapokhoz, a privát adatok törlődnek. */
    private fun leavePrivate() {
        privateMode = false
        privateActive = null
        tabs = normalShown()
        tabAdapter.notifyDataSetChanged()
        updatePrivateUi()
        val target = normalActive?.takeIf { it in tabs } ?: tabs.lastOrNull() ?: createTab().also {
            it.url = homeUrl()
            it.pendingUrl = it.url
            tabAdapter.notifyDataSetChanged()
        }
        selectTab(target, animate = false)
        handler.postDelayed({ deletePrivateData() }, 600L)
    }

    private fun deletePrivateData() {
        if (privateTabs.isNotEmpty() || !privateProfileSupported()) return
        try {
            androidx.webkit.ProfileStore.getInstance().deleteProfile(PRIVATE_PROFILE)
        } catch (e: Throwable) {
            // még használatban van, vagy nem létezik
        }
    }

    // ---------------------------------------------------------------- lapcsoportok

    /** A lapváltóban kiválasztott csoport ("" = minden lap); csak a normál lapokra vonatkozik. */
    private var groupSel = ""
    private var groupCapsule: TextView? = null
    private var groupPopup: GlassPopup? = null

    /** A normál lapok közül a látható rész: a kiválasztott csoport élő nézete, vagy mind. */
    private fun normalShown(): MutableList<Tab> =
        if (groupSel.isEmpty()) normalTabs else GroupTabList(normalTabs, groupSel)

    /** Az összes ismert csoport: a mentett nevek és a lapokon szereplők. */
    private fun allGroups(): List<String> {
        val out = TabGroups.names(prefs)
        for (t in normalTabs) if (t.group.isNotEmpty() && t.group !in out) out.add(t.group)
        return out
    }

    /** Induláskor: a legutóbb kiválasztott csoport marad, ha az aktív lap abban van. */
    private fun restoreTabGroup() {
        val sel = TabGroups.selected(prefs)
        if (sel.isNotEmpty() && !privateMode && activeTab?.group == sel) {
            groupSel = sel
            tabs = normalShown()
        } else if (sel.isNotEmpty()) {
            TabGroups.setSelected(prefs, "")
        }
        updateTabCount()
    }

    /** A lapváltó tetején a csoportválasztó üvegkapszula, és a kártyák hosszú nyomása. */
    private fun setupTabGroups() {
        val tv = TextView(this)
        tv.textSize = 14.5f
        tv.setTypeface(tv.typeface, android.graphics.Typeface.BOLD)
        tv.gravity = Gravity.CENTER
        tv.isSingleLine = true
        tv.ellipsize = TextUtils.TruncateAt.END
        tv.maxWidth = dp(240)
        tv.setPadding(dp(16), 0, dp(12), 0)
        tv.compoundDrawablePadding = dp(4)
        // az üveg bőre az indulás végén (a glassBubble az üveg beállításait is újra alkalmazza)
        handler.post { if (!isDestroyed) tv.background = glassBubble(dp(100).toFloat()) }
        tv.contentDescription = L.t("Lapcsoportok")
        tv.setOnClickListener { showGroupMenu() }
        val lp = FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(34))
        lp.gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
        overview.addView(tv, lp)
        groupCapsule = tv
        // hosszú nyomás a kártyán: áthelyezés másik csoportba
        tabList.addOnChildAttachStateChangeListener(object : RecyclerView.OnChildAttachStateChangeListener {
            override fun onChildViewAttachedToWindow(view: View) {
                val card = view.findViewById<View>(R.id.tabCard) ?: return
                card.setOnLongClickListener {
                    val tab = tabs.getOrNull(tabList.getChildAdapterPosition(view))
                    if (tab == null || tab.isPrivate || overviewBusy || !overviewShown) {
                        return@setOnLongClickListener false
                    }
                    haptic()
                    showMoveToGroup(tab, card)
                    true
                }
            }

            override fun onChildViewDetachedFromWindow(view: View) {}
        })
        layoutGroupCapsule(-1)
        updateGroupCapsule()
    }

    /** A kapszula helye: a rács fölött (a rács ennyivel lejjebb kezdődik, lásd applyLayoutInsets). */
    private fun layoutGroupCapsule(webTopY: Int) {
        val tv = groupCapsule ?: return
        val lp = tv.layoutParams as? FrameLayout.LayoutParams ?: return
        val top = if (webTopY >= 0) webTopY + dp(12) else tabList.paddingTop - dp(GROUP_BAR_SPACE_DP - 2)
        if (lp.topMargin != top) {
            lp.topMargin = top
            tv.layoutParams = lp
        }
    }

    private fun updateGroupCapsule() {
        val tv = groupCapsule ?: return
        tv.visibility = if (privateMode) View.GONE else View.VISIBLE
        tv.text = if (groupSel.isEmpty()) L.t("Minden lap") else groupSel
        val col = menuTextColor()
        tv.setTextColor(col)
        val ic = ContextCompat.getDrawable(this, R.drawable.ic_chevron_down)?.mutate()
        ic?.setBounds(0, 0, dp(18), dp(18))
        ic?.setTint(col)
        tv.setCompoundDrawablesRelative(null, null, ic, null)
    }

    /** Az új üveg felületek (felugró lista, olvasási lista, olvasó) közös eszközei. */
    private fun glassUi(): GlassUi = GlassUi(
        activity = this,
        host = root,
        dark = if (glassClear()) night else glassDark(),
        makeGlass = { r -> glassBubble(r) },
        releaseGlass = { d -> if (d is GlassSkinDrawable) skins.remove(d) },
        haptic = { haptic() }
    )

    private fun newGlassPopup(): GlassPopup {
        groupPopup?.dismissNow()
        return GlassPopup(glassUi()).also { groupPopup = it }
    }

    /** A kapszulára koppintva: Minden lap, a csoportok, Új csoport…, Csoport törlése. */
    private fun showGroupMenu() {
        if (!overviewShown || overviewBusy || privateMode) return
        val anchorView = groupCapsule ?: return
        haptic()
        val popup = newGlassPopup()
        val anchor = rectOf(anchorView, root)
        val items = ArrayList<GlassPopup.Item>()
        items.add(GlassPopup.Item(L.t("Minden lap"), R.drawable.ic_m_reading_list, check = groupSel.isEmpty()) { switchGroup("") })
        for (g in allGroups()) {
            items.add(GlassPopup.Item(g, R.drawable.ic_m_folder, check = g == groupSel) { switchGroup(g) })
        }
        items.add(GlassPopup.Item(L.t("Új csoport…"), R.drawable.ic_m_add) {
            askNewGroup(anchor) { name -> switchGroup(name) }
        })
        if (groupSel.isNotEmpty()) {
            val sel = groupSel
            items.add(GlassPopup.Item(L.t("Csoport törlése"), R.drawable.ic_m_delete, destructive = true) { deleteGroup(sel) })
        }
        popup.show(anchor, L.t("Lapcsoportok"), items)
    }

    /** Új csoport neve (üveg szövegmezővel); a [done] a létrehozott csoport nevét kapja. */
    private fun askNewGroup(anchor: RectF, done: (String) -> Unit) {
        val popup = newGlassPopup()
        popup.showInput(anchor, L.t("Új csoport"), L.t("Csoport neve"), "", L.t("Létrehozás")) { raw ->
            val name = TabGroups.add(prefs, raw)
            if (name.isNotEmpty()) done(name)
        }
    }

    /** Váltás egy másik csoportra (a lapváltóban a rács átúszik, mint a privát módnál). */
    private fun switchGroup(name: String) {
        if (privateMode || overviewBusy || name == groupSel) return
        groupSel = name
        TabGroups.setSelected(prefs, name)
        tabs = normalShown()
        // a rejtett csoportokban lévő lapok lemaradhattak a beállítások változásairól
        tabs.forEach {
            applyWebSettings(it.webView)
            applyWebTop(it.webView)
        }
        updateTabCount()
        if (tabs.isEmpty()) {
            // üres csoport: egy új kezdőlap nyílik benne
            val t = createTab()
            t.url = homeUrl()
            t.pendingUrl = t.url
            tabAdapter.notifyDataSetChanged()
            updateTabCount()
            if (overviewShown) openTabFromOverview(t, notify = false) else selectTab(t)
            saveTabs()
            return
        }
        val target = activeTab?.takeIf { it in tabs } ?: tabs.last()
        if (overviewShown) {
            tabList.animate().cancel()
            tabList.animate().alpha(0f).setDuration(130).withEndAction {
                if (target !== activeTab) selectTab(target, animate = false)
                tabAdapter.notifyDataSetChanged()
                updateTabCount()
                scrollTabListTo(tabs.indexOf(target).coerceAtLeast(0))
                tabList.animate().alpha(1f).setDuration(200).start()
            }.start()
        } else if (target !== activeTab) {
            selectTab(target)
        }
        saveTabs()
    }

    /** A csoport megszűnik; a lapjai megmaradnak, csak kikerülnek a csoportból. */
    private fun deleteGroup(name: String) {
        if (name.isEmpty() || privateMode || overviewBusy) return
        haptic()
        normalTabs.forEach { if (it.group == name) it.group = "" }
        TabGroups.remove(prefs, name)
        if (groupSel == name) {
            switchGroup("")
        } else {
            tabAdapter.notifyDataSetChanged()
            updateTabCount()
            saveTabs()
        }
    }

    /** Hosszú nyomás egy kártyán: a lap áthelyezése egy másik csoportba. */
    private fun showMoveToGroup(tab: Tab, card: View) {
        val popup = newGlassPopup()
        val anchor = rectOf(card, root)
        val items = ArrayList<GlassPopup.Item>()
        items.add(GlassPopup.Item(L.t("Nincs csoport"), R.drawable.ic_m_reading_list, check = tab.group.isEmpty()) {
            moveTabToGroup(tab, "")
        })
        for (g in allGroups()) {
            items.add(GlassPopup.Item(g, R.drawable.ic_m_folder, check = tab.group == g) { moveTabToGroup(tab, g) })
        }
        items.add(GlassPopup.Item(L.t("Új csoport…"), R.drawable.ic_m_add) {
            askNewGroup(anchor) { name -> moveTabToGroup(tab, name) }
        })
        popup.show(anchor, L.t("Áthelyezés csoportba"), items)
    }

    private fun moveTabToGroup(tab: Tab, name: String) {
        if (tab.isPrivate || overviewBusy || tab.group == name || normalTabs.none { it === tab }) return
        tab.group = name
        if (groupSel.isNotEmpty() && name != groupSel) {
            // a lap kikerült a látható csoportból
            if (tabs.isEmpty()) {
                val t = createTab()
                t.url = homeUrl()
                t.pendingUrl = t.url
            }
            if (tab === activeTab) selectTab(tabs.last(), animate = false)
        }
        tabAdapter.notifyDataSetChanged()
        updateTabCount()
        saveTabs()
        if (overviewShown) updateLens()
    }

    // ---------------------------------------------------------------- olvasó nézet, olvasási lista

    private var readerView: ReaderView? = null

    /** Olvasó nézet: a cikk kinyerése az oldalból, és megjelenítése teljes képernyőn. */
    private fun openReader() {
        val tab = activeTab ?: return
        if (readerView != null) return
        val noReader = L.t("Ehhez az oldalhoz nem érhető el olvasó nézet")
        if (Urls.isStart(tab.url)) {
            showCopyToast(noReader, R.drawable.ic_m_reader, 2200L)
            return
        }
        tab.webView.evaluateJavascript(Reader.EXTRACT_JS) { res ->
            if (isFinishing || isDestroyed || tab !== activeTab || readerView != null) return@evaluateJavascript
            val article = Reader.parse(res)
            if (article == null) {
                showCopyToast(noReader, R.drawable.ic_m_reader, 2200L)
                return@evaluateJavascript
            }
            releaseFocus()
            val rv = ReaderView(
                glassUi(), prefs, statusTop, navBottom,
                onOpenUrl = { url ->
                    tab.stayInBrowser = true
                    tab.webView.loadUrl(url)
                },
                onClosed = {
                    readerView = null
                    applyStatusColor()
                }
            )
            readerView = rv
            rv.open(article, tab.webView.url ?: tab.url)
        }
    }

    /** Az aktuális oldal az olvasási listára kerül (a rövid leírása utólag érkezik). */
    private fun addToReadingList() {
        val tab = activeTab ?: return
        val url = tab.webView.url ?: tab.url
        if (Urls.isStart(url)) return
        haptic()
        val added = ReadingListStore.add(applicationContext, url, tab.title.ifBlank { Urls.host(url) })
        showCopyToast(
            if (added) L.t("Hozzáadva az olvasási listához") else L.t("Már az olvasási listán van"),
            R.drawable.ic_m_reading_list
        ) { showReadingList() }
        if (added) {
            tab.webView.evaluateJavascript(ReadingListStore.SNIPPET_JS) { res ->
                val s = ReadingListStore.decodeJsString(res)
                if (s.isNotBlank()) ReadingListStore.setSnippet(applicationContext, url, s)
            }
        }
    }

    private fun showReadingList() {
        if (overviewShown || overviewBusy) return
        releaseFocus()
        ReadingListSheet(
            glassUi(), statusTop, navBottom,
            onOpen = { url ->
                val t = activeTab
                if (t != null) {
                    t.stayInBrowser = true
                    t.webView.loadUrl(url)
                } else {
                    openNewTab(url)
                }
            },
            onOpenNewTab = { url -> openNewTab(url) }
        ).show()
    }

    private var urlCopied = false

    private fun menuTextColor(): Int = if (glassClear()) (contentColor ?: Color.WHITE)
        else if (glassDark()) Color.WHITE else 0xFF1D1F24.toInt()

    /** Az üvegfelületek árnyalata: a rendszer módja szerint, vagy kézzel világos / sötét. */
    private fun glassDark(): Boolean = when (prefs.getString("glass_tone", "system")) {
        "light" -> false
        "dark" -> true
        "clear" -> !darkContent
        else -> night
    }

    /** Átlátszó üveg: nincs dér, a szöveg színe a mögötte lévő oldalhoz igazodik. */
    private fun glassClear(): Boolean = prefs.getString("glass_tone", "system") == "clear" && lensActive

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
        menuRow(L.t("Letöltések"), icon = R.drawable.ic_m_download) {
            snapBackdrop { startActivity(Intent(this, DownloadsActivity::class.java)) }
        }
        // olvasó nézet és olvasási lista (lásd Reader.kt, ReadingList.kt)
        menuRow(L.t("Olvasó nézet"), canMark, icon = R.drawable.ic_m_reader) { openReader() }
        menuRow(L.t("Hozzáadás az olvasási listához"), canMark, icon = R.drawable.ic_m_reading_add) { addToReadingList() }
        menuRow(L.t("Olvasási lista"), icon = R.drawable.ic_m_reading_list) { showReadingList() }
        val desktop = prefs.getBoolean("desktop_mode", false)
        menuRow(L.t("Asztali oldal"), icon = R.drawable.ic_m_desktop, check = desktop) {
            prefs.edit().putBoolean("desktop_mode", !desktop).apply()
            applyAllSettings()
        }
        menuRow(L.t("Beállítások"), icon = R.drawable.ic_m_settings) { openSettings() }
        // Olvashatóság: az üveg alá enyhe dér kerül, a szöveg a világos / sötét módhoz igazodik
        // (így világos és sötét oldal fölött is mindig látszik).
        menuPanel.background = null
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
            // záráskor a szöveg az elején eltűnik, az üveg pedig még a csempe elérése előtt teljesen
            // elhalványul: nem marad kis szürke folt, és nem látszik a szöveg összenyomódni
            menuGlassAlpha = if (formClosing) smooth((c - 0.08f) / 0.5f) else smooth(c / 0.25f)
            val a = if (formClosing) smooth((c - 0.62f) / 0.38f) else smooth((c - 0.35f) / 0.5f)
            val sc = 0.8f + 0.2f * smooth(c)
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

        val text = menuTextColor()
        val muted = ColorUtils.setAlphaComponent(text, 150)
        val box = LinearLayout(this)
        box.orientation = LinearLayout.VERTICAL
        box.setPadding(dp(20), dp(18), dp(20), dp(10))
        // enyhe dér az üvegen, hogy a szöveg bármilyen oldal fölött olvasható legyen
        box.background = GradientDrawable().apply {
            cornerRadius = dp(26).toFloat()
            val a = if (lensActive) 1f else 2.6f
            setColor(
                if (glassDark()) Color.argb((70 * a).toInt(), 20, 22, 28)
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
        anim.duration = if (opening) 500L else 360L
        anim.interpolator = if (opening) {
            android.view.animation.PathInterpolator(0.05f, 0.7f, 0.1f, 1f)
        } else {
            android.view.animation.PathInterpolator(0.3f, 0f, 0.15f, 1f)
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
        preferHighRefreshRate()
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
        try {
            unregisterReceiver(downloadDone)
        } catch (e: Exception) {
            // not registered
        }
        handler.removeCallbacksAndMessages(null)
        (normalTabs + privateTabs).forEach {
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

        private const val PRIVATE_PROFILE = "GlassPrivate"

        /** Az oldal tetején lévő fejléc háttérszíne (ha háromponton is ugyanaz, egyébként üres). */
        const val PAD_COLOR_JS = """(function(){
var H=parseFloat(getComputedStyle(document.documentElement).paddingTop)||0;var y=H+2;
function clear(c){return !c||c==='transparent'||/rgba\([^)]*,\s*0\)$/.test(c);}
function bg(e){while(e&&e.nodeType===1){var s=getComputedStyle(e);
if(s.backgroundImage&&s.backgroundImage!=='none'&&e!==document.body&&e!==document.documentElement)return '';
if(!clear(s.backgroundColor))return s.backgroundColor;e=e.parentElement;}
var b=getComputedStyle(document.body).backgroundColor;if(!clear(b))return b;
var h=getComputedStyle(document.documentElement).backgroundColor;return clear(h)?'':h;}
var xs=[3,innerWidth/2,innerWidth-4];var r=null;
for(var i=0;i<xs.length;i++){var c=bg(document.elementFromPoint(xs[i],y));if(!c)return '';if(r===null)r=c;else if(r!==c)return '';}
return r||'';})()"""

        /**
         * Reklámblokkoló, látható rész: a hirdetések helye is eltűnik (nem marad üres doboz).
         * Ismert hirdetéselemek elrejtése, valamint a "Hirdetés / Advertisement" feliratú, tartalom
         * nélküli dobozok összecsukása. Az oldal változásait is figyeli.
         */
        const val AD_COSMETIC_JS = """(function(){
if(window.__glassAds)return;window.__glassAds=1;
var css='ins.adsbygoogle,.adsbygoogle,[id^="google_ads_iframe"],[id^="div-gpt-ad"],[id^="gpt-ad"],[data-ad-slot],[data-ad-unit],[data-adunit],[data-google-query-id],'+
'iframe[src*="doubleclick.net"],iframe[src*="googlesyndication"],iframe[id^="google_ads"],.ad-slot,.ad-container,.ad-wrapper,.ad-unit,.ad-banner,.adbox,.advert,.advertisement,'+
'.sponsored-ad,amp-ad,.ains,.gemini-ad,[aria-label="Advertisement"],[aria-label="Hirdetés"]'+
'{display:none!important;min-height:0!important;height:0!important;margin:0!important;padding:0!important}';
var st=document.createElement('style');st.textContent=css;(document.head||document.documentElement).appendChild(st);
var LABEL=/^(advertisement|advertisements|ad|ads|hirdetés|reklám|sponsored|szponzorált|anzeige|publicité|publicidad)$/i;
function hasContent(e){if(!e)return false;var t=(e.innerText||'').replace(/\s+/g,' ').trim();
if(t.length>24)return true;var m=e.querySelectorAll('img,video,canvas,svg,input,button,a[href]');
for(var i=0;i<m.length;i++){var x=m[i];if(x.offsetWidth>40&&x.offsetHeight>40&&x.tagName!=='A')return true;if(x.tagName==='A'&&(x.innerText||'').trim().length>3)return true;}return false;}
function collapse(e){e.style.setProperty('display','none','important');}
function scanEl(e){if(e.__gAd)return;
if(e.children.length<=2){var tx=(e.textContent||'').trim();if(tx.length<20&&LABEL.test(tx)){e.__gAd=1;
var p=e.parentElement,best=e;for(var k=0;k<3&&p&&p!==document.body;k++){var clone=(p.innerText||'').replace(tx,'').trim();
if(clone.length<4&&p.offsetHeight<innerHeight*0.9){best=p;p=p.parentElement;}else break;}
collapse(best);var n=best.nextElementSibling;if(n&&!hasContent(n)&&n.offsetHeight>30)collapse(n);return;}}
var tag=e.tagName;if((tag==='DIV'||tag==='SECTION'||tag==='ASIDE')&&e.offsetHeight>60){
var fr=e.querySelector('iframe,ins');if(fr&&!hasContent(e)){var src=(fr.src||'')+(fr.id||'')+(fr.className||'');
if(/ad|google|doubleclick|syndication|banner|sponsor/i.test(src)||fr.offsetHeight<4){e.__gAd=1;collapse(e);}}}}
function scan(root){var all=(root||document.body);if(!all)return;scanEl(all);all=all.getElementsByTagName?all.getElementsByTagName('*'):[];
for(var i=0;i<all.length;i++)scanEl(all[i]);}
scan();var pend=[],tm=0;new MutationObserver(function(ms){for(var i=0;i<ms.length;i++){var a=ms[i].addedNodes;
for(var k=0;k<a.length;k++)if(a[k].nodeType===1)pend.push(a[k]);}
if(!tm&&pend.length)tm=setTimeout(function(){tm=0;var p=pend;pend=[];for(var i=0;i<p.length&&i<200;i++)scan(p[i]);},700);})
.observe(document.documentElement,{childList:true,subtree:true});setTimeout(function(){scan();},1500);setTimeout(function(){scan();},4000);
})()"""

        const val SPACER_JS =
            "(function(){if(document.getElementById('__glass_spacer'))return;" +
                "var b=document.body;if(!b)return;var s=document.createElement('div');" +
                "s.id='__glass_spacer';s.style.cssText='height:96px;width:100%;pointer-events:none;flex:none';" +
                "b.appendChild(s);})();"
    }
}
