package com.example.glassbrowser

import android.annotation.SuppressLint
import android.content.SharedPreferences
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.core.graphics.ColorUtils
import androidx.core.view.WindowCompat
import org.json.JSONObject
import org.json.JSONTokener

/** Az olvasó nézethez kinyert cikk. A [content] már megtisztított HTML (csak engedélyezett elemek). */
class ReaderArticle(
    val title: String,
    val byline: String,
    val site: String,
    val content: String,
    val lang: String,
    val dir: String,
    val image: String,
    val words: Int
)

/** Az olvasó nézet: a cikk kinyerése az oldalból (JS), és az eredmény feldolgozása. */
object Reader {

    /**
     * Readability-szerű kinyerés: a bekezdések szülői pontozása (szöveghossz, vesszők, osztálynevek),
     * a legjobb elem a linksűrűséggel súlyozva; utána egy engedélyezett elemekre szűkített, attribútum
     * nélküli HTML-be írjuk át (szkriptek, űrlapok, reklámok, megosztó sávok kimaradnak).
     * Ha nincs elég szöveg, {ok:false}.
     */
    val EXTRACT_JS = """
(function(){try{
var d=document,body=d.body;if(!body)return {ok:false};
function txt(e){return ((e&&e.textContent)||'').replace(/\s+/g,' ').trim();}
var bad=/comment|disqus|footer|footnote|sidebar|sponsor|advert|\bads?\b|ad-|promo|related|share|sharing|social|newsletter|subscribe|cookie|consent|popup|modal|navbar|\bnav\b|menu|breadcrumb|widget|outbrain|taboola|banner|recommend|paywall|signup|login|toolbar|masthead/i;
var good=/article|body|content|entry|main|page|post|text|blog|story|hentry|prose/i;
function cls(e){var c=e.className;return (typeof c==='string'?c:(c&&c.baseVal)||'')+' '+(e.id||'');}
function linkDensity(e){var t=txt(e).length;if(!t)return 0;var l=0,as=e.getElementsByTagName('a');for(var i=0;i<as.length;i++)l+=txt(as[i]).length;return l/t;}
var scores=new Map();
function add(e,s){if(!e||e===d.documentElement||e.nodeType!==1)return;
 if(!scores.has(e)){var b=0,c=cls(e);if(bad.test(c)&&!good.test(c))b-=25;if(good.test(c))b+=25;
  var tg=e.tagName;if(tg==='ARTICLE'||tg==='MAIN')b+=30;if(tg==='SECTION'||tg==='DIV')b+=5;if(/^(FORM|UL|OL|ASIDE|NAV|FOOTER|HEADER|TD|TABLE)$/.test(tg))b-=8;scores.set(e,b);}
 scores.set(e,scores.get(e)+s);}
var ps=body.querySelectorAll('p,pre,blockquote');
for(var i=0;i<ps.length;i++){var p=ps[i],t=txt(p);if(t.length<25)continue;
 var s=1+t.split(/[,，、]/).length+Math.min(Math.floor(t.length/100),3);
 add(p.parentElement,s);if(p.parentElement)add(p.parentElement.parentElement,s/2);
 if(p.parentElement&&p.parentElement.parentElement)add(p.parentElement.parentElement.parentElement,s/3);}
var best=null,bs=-1e9;
scores.forEach(function(v,e){var sc=v*(1-linkDensity(e));if(sc>bs){bs=sc;best=e;}});
if(!best)return {ok:false};
// ha a szülő is majdnem ilyen jó, inkább az (a cikk több részre lehet bontva)
var par=best.parentElement;
if(par&&par!==body&&scores.has(par)&&scores.get(par)*(1-linkDensity(par))>=bs*0.8)best=par;
if(txt(best).length<450)return {ok:false};
var title='';var og=d.querySelector('meta[property="og:title"]');
var h1=best.querySelector('h1')||d.querySelector('h1');var h1t=txt(h1);
if(h1t.length>8&&h1t.length<300)title=h1t;else if(og&&og.content)title=og.content.trim();else title=(d.title||'').trim();
var byline='';var ma=d.querySelector('meta[name="author"]');if(ma&&ma.content)byline=ma.content.trim();
if(!byline){var be=d.querySelector('[rel="author"],[itemprop="author"],.byline,.author');var bt=txt(be);if(bt.length>0&&bt.length<100)byline=bt;}
var sn=d.querySelector('meta[property="og:site_name"]');var site=(sn&&sn.content)?sn.content.trim():location.hostname.replace(/^www\./,'');
var oi=d.querySelector('meta[property="og:image"]');var img=(oi&&oi.content)||'';
var allowed={P:1,H2:1,H3:1,H4:1,H5:1,H6:1,UL:1,OL:1,LI:1,BLOCKQUOTE:1,PRE:1,CODE:1,EM:1,I:1,STRONG:1,B:1,FIGURE:1,FIGCAPTION:1,TABLE:1,THEAD:1,TBODY:1,TR:1,TD:1,TH:1,SUP:1,SUB:1,DL:1,DT:1,DD:1,MARK:1,SMALL:1,S:1,U:1,Q:1,CITE:1,ABBR:1,TIME:1};
var drop={SCRIPT:1,STYLE:1,NOSCRIPT:1,IFRAME:1,FORM:1,BUTTON:1,INPUT:1,SELECT:1,TEXTAREA:1,NAV:1,ASIDE:1,FOOTER:1,SVG:1,CANVAS:1,OBJECT:1,EMBED:1,VIDEO:1,AUDIO:1,TEMPLATE:1,DIALOG:1,LINK:1,META:1};
var blockSel='p,div,section,article,h1,h2,h3,h4,h5,h6,ul,ol,blockquote,pre,figure,table,img,dl';
function esc(s){return String(s).replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;').replace(/"/g,'&quot;');}
function abs(u){try{return new URL(u,location.href).href;}catch(e){return '';}}
var imgs=0,words=0;
function walk(n,depth){
 if(depth>60)return '';
 if(n.nodeType===3){return esc(n.nodeValue);}
 if(n.nodeType!==1)return '';
 var tag=n.tagName.toUpperCase();
 if(drop[tag])return '';
 if(n.getAttribute('aria-hidden')==='true'||n.hidden)return '';
 var c=cls(n);
 if(tag!=='P'&&bad.test(c)&&!good.test(c)&&txt(n).length<1200)return '';
 try{var st=getComputedStyle(n);if(st.display==='none'||st.visibility==='hidden')return '';}catch(e){}
 if(tag==='IMG'){var src=n.currentSrc||n.getAttribute('data-src')||n.getAttribute('data-lazy-src')||n.getAttribute('data-original')||n.src||'';
  if(!src||/^data:/i.test(src))return '';
  if(n.naturalWidth&&n.naturalWidth<80)return '';if(n.width&&n.width<60&&n.height&&n.height<60)return '';
  src=abs(src);if(!/^https?:/i.test(src))return '';imgs++;
  return '<img src="'+esc(src)+'" alt="'+esc(n.alt||'')+'">';}
 if(tag==='PICTURE'){var im=n.querySelector('img');return im?walk(im,depth+1):'';}
 if(tag==='BR')return '<br>';
 if(tag==='HR')return '<hr>';
 if(/^H[1-6]$/.test(tag)&&txt(n)===title)return '';
 var inner='';for(var ch=n.firstChild;ch;ch=ch.nextSibling)inner+=walk(ch,depth+1);
 if(tag==='A'){var h=n.href;if(!h||/^(javascript|data):/i.test(h))return inner;
  if(linkDensity(n.parentElement||n)>0.9&&inner.length<2)return '';
  return '<a href="'+esc(h)+'">'+inner+'</a>';}
 var empty=!inner.replace(/<br>|&nbsp;|\s/g,'');
 if(tag==='H1')tag='H2';
 if(allowed[tag]){if(empty&&tag!=='TD'&&tag!=='TH')return '';var t=tag.toLowerCase();return '<'+t+'>'+inner+'</'+t+'>';}
 if(/^(DIV|SECTION|ARTICLE|MAIN|HEADER)$/.test(tag)){
  if(empty)return '';
  if(!n.querySelector(blockSel)&&txt(n).length>0)return '<p>'+inner+'</p>';
  return inner;}
 return inner;}
var html=walk(best,0);
words=txt(best).split(' ').length;
if(!imgs&&img&&/^https?:/i.test(img))img=abs(img);else img='';
return {ok:true,title:title,byline:byline,site:site,content:html,lang:(d.documentElement.lang||''),dir:(d.dir||d.documentElement.dir||''),image:img,words:words};
}catch(e){return {ok:false,err:String(e)};}})()
"""

    /** Az evaluateJavascript eredményéből a cikk (null, ha nincs olvasható cikk). */
    fun parse(result: String?): ReaderArticle? {
        if (result.isNullOrBlank() || result == "null") return null
        return try {
            val v = JSONTokener(result).nextValue()
            val o = when (v) {
                is JSONObject -> v
                is String -> JSONObject(v)
                else -> return null
            }
            if (!o.optBoolean("ok", false)) return null
            val content = o.optString("content")
            if (content.isBlank()) return null
            ReaderArticle(
                title = o.optString("title"),
                byline = o.optString("byline"),
                site = o.optString("site"),
                content = content,
                lang = o.optString("lang"),
                dir = o.optString("dir"),
                image = o.optString("image"),
                words = o.optInt("words", 0)
            )
        } catch (e: Exception) {
            null
        }
    }

    fun esc(s: String): String = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
}

/**
 * Teljes képernyős olvasó nézet: a cikk tiszta HTML-ként egy saját WebView-ban, Safari-szerű
 * betűkkel. Fent üveg sáv: bezárás, a webhely neve és az "aA" gomb (betűméret, téma, betűtípus).
 * A beállítások a prefs-ben maradnak meg.
 */
class ReaderView(
    private val ui: GlassUi,
    private val prefs: SharedPreferences,
    private val topInset: Int,
    private val bottomInset: Int,
    /** Egy linkre koppintva: az olvasó bezárul, és a lap ezt a címet tölti be. */
    private val onOpenUrl: (String) -> Unit,
    /** A nézet eltűnt (az állapotsor színét a hívó állítja vissza). */
    private val onClosed: () -> Unit
) {
    private val ctx = ui.activity
    private lateinit var root: FrameLayout
    private lateinit var web: WebView
    private lateinit var bar: LinearLayout
    private lateinit var siteLabel: TextView
    private lateinit var closeBtn: ImageButton
    private lateinit var aaBtn: TextView
    private var panel: LinearLayout? = null
    private var catcher: View? = null
    private var back: OnBackPressedCallback? = null
    private var closing = false

    private var fontSize = prefs.getInt(KEY_SIZE, 19).coerceIn(MIN_SIZE, MAX_SIZE)
    private var theme = prefs.getString(KEY_THEME, null) ?: defaultTheme()
    private var serif = prefs.getBoolean(KEY_SERIF, true)

    private fun defaultTheme(): String {
        val night = (ctx.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
            android.content.res.Configuration.UI_MODE_NIGHT_YES
        return if (night) "dark" else "light"
    }

    private fun bgColor(t: String = theme): Int = when (t) {
        "sepia" -> 0xFFF8F1E3.toInt()
        "dark" -> 0xFF1C1C1E.toInt()
        else -> Color.WHITE
    }

    private fun fgColor(t: String = theme): Int = when (t) {
        "sepia" -> 0xFF4F3A26.toInt()
        "dark" -> 0xFFE5E5E7.toInt()
        else -> 0xFF1D1D1F.toInt()
    }

    @SuppressLint("SetJavaScriptEnabled")
    fun open(a: ReaderArticle, baseUrl: String) {
        root = FrameLayout(ctx)
        root.isClickable = true
        root.setBackgroundColor(bgColor())
        // a keresősáv (elevation) fölé kerüljön, árnyék nélkül
        root.outlineProvider = null
        root.translationZ = ui.dpf(24f)

        web = WebView(ctx)
        web.setBackgroundColor(bgColor())
        web.isVerticalScrollBarEnabled = false
        web.overScrollMode = View.OVER_SCROLL_NEVER
        web.settings.javaScriptEnabled = true
        web.settings.domStorageEnabled = false
        web.settings.allowFileAccess = false
        web.settings.setSupportZoom(false)
        web.settings.textZoom = 100
        web.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val url = request.url?.toString().orEmpty()
                if (url.startsWith("http://") || url.startsWith("https://")) {
                    close { onOpenUrl(url) }
                }
                return true
            }
        }
        root.addView(web, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))

        buildBar(a)
        ui.host.addView(root, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        web.loadDataWithBaseURL(baseUrl, html(a), "text/html", "utf-8", null)
        applyStyle(updatePage = false)

        // finom beúszás alulról
        root.alpha = 0f
        root.translationY = ui.dpf(28f)
        root.scaleX = 0.97f
        root.scaleY = 0.97f
        root.animate().alpha(1f).translationY(0f).scaleX(1f).scaleY(1f).setDuration(340L)
            .setInterpolator(DecelerateInterpolator(2f)).start()

        val cb = object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (panel != null) hidePanel() else close()
            }
        }
        ctx.onBackPressedDispatcher.addCallback(ctx, cb)
        back = cb
    }

    private fun buildBar(a: ReaderArticle) {
        bar = LinearLayout(ctx)
        bar.orientation = LinearLayout.HORIZONTAL
        bar.gravity = Gravity.CENTER_VERTICAL
        bar.setPadding(ui.dp(4), 0, ui.dp(6), 0)
        bar.elevation = ui.dpf(6f)
        bar.isClickable = true

        closeBtn = ImageButton(ctx)
        closeBtn.setImageResource(R.drawable.ic_close)
        closeBtn.background = null
        closeBtn.scaleType = android.widget.ImageView.ScaleType.CENTER_INSIDE
        closeBtn.setPadding(ui.dp(10), ui.dp(10), ui.dp(10), ui.dp(10))
        closeBtn.contentDescription = L.t("Olvasó nézet bezárása")
        closeBtn.setOnClickListener {
            ui.haptic()
            close()
        }
        bar.addView(closeBtn, LinearLayout.LayoutParams(ui.dp(44), ui.dp(44)))

        siteLabel = TextView(ctx)
        siteLabel.text = a.site.ifBlank { a.title }
        siteLabel.textSize = 14f
        siteLabel.setTypeface(siteLabel.typeface, Typeface.BOLD)
        siteLabel.gravity = Gravity.CENTER
        siteLabel.isSingleLine = true
        siteLabel.ellipsize = TextUtils.TruncateAt.END
        bar.addView(siteLabel, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))

        aaBtn = TextView(ctx)
        aaBtn.text = "aA"
        aaBtn.textSize = 17f
        aaBtn.setTypeface(Typeface.SERIF, Typeface.BOLD)
        aaBtn.gravity = Gravity.CENTER
        aaBtn.contentDescription = L.t("Megjelenés")
        aaBtn.setOnClickListener {
            ui.haptic()
            if (panel != null) hidePanel() else showPanel()
        }
        bar.addView(aaBtn, LinearLayout.LayoutParams(ui.dp(48), ui.dp(44)))

        val lp = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ui.dp(48))
        lp.gravity = Gravity.TOP
        lp.topMargin = topInset + ui.dp(6)
        lp.leftMargin = ui.dp(12)
        lp.rightMargin = ui.dp(12)
        root.addView(bar, lp)
    }

    /** Üveg háttér a téma színéből (enyhén áttetsző, vékony peremmel). */
    private fun glass(radius: Float): GradientDrawable = GradientDrawable().apply {
        cornerRadius = radius
        setColor(ColorUtils.setAlphaComponent(bgColor(), 222))
        val rim = if (theme == "dark") Color.argb(46, 255, 255, 255) else Color.argb(30, 0, 0, 0)
        setStroke(ui.dp(1).coerceAtLeast(1), rim)
    }

    /** A téma, a betűméret és a betűtípus alkalmazása a felületre (és az oldalra is). */
    private fun applyStyle(updatePage: Boolean = true) {
        val fg = fgColor()
        root.setBackgroundColor(bgColor())
        web.setBackgroundColor(bgColor())
        bar.background = glass(ui.dpf(24f))
        siteLabel.setTextColor(ColorUtils.setAlphaComponent(fg, 170))
        closeBtn.imageTintList = ColorStateList.valueOf(fg)
        aaBtn.setTextColor(fg)
        // világos témánál sötét állapotsor-ikonok
        try {
            WindowCompat.getInsetsController(ctx.window, root).isAppearanceLightStatusBars = theme != "dark"
        } catch (e: Exception) {
            // nincs teendő
        }
        if (updatePage) {
            web.evaluateJavascript(
                "document.body.className='${theme} ${if (serif) "serif" else "sans"}';" +
                    "document.documentElement.style.fontSize='${fontSize}px';",
                null
            )
        }
        panel?.let { buildPanelContent(it) }
    }

    private fun save() {
        prefs.edit().putInt(KEY_SIZE, fontSize).putString(KEY_THEME, theme).putBoolean(KEY_SERIF, serif).apply()
    }

    // ------------------------------------------------ "aA" panel

    private fun showPanel() {
        val c = View(ctx)
        c.isClickable = true
        c.setOnClickListener { hidePanel() }
        root.addView(c, root.indexOfChild(bar), FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        catcher = c

        val p = LinearLayout(ctx)
        p.orientation = LinearLayout.VERTICAL
        p.setPadding(ui.dp(14), ui.dp(12), ui.dp(14), ui.dp(14))
        p.elevation = ui.dpf(10f)
        p.isClickable = true
        buildPanelContent(p)
        val lp = FrameLayout.LayoutParams(ui.dp(264), ViewGroup.LayoutParams.WRAP_CONTENT)
        lp.gravity = Gravity.TOP or Gravity.END
        lp.topMargin = topInset + ui.dp(6) + ui.dp(48) + ui.dp(8)
        lp.rightMargin = ui.dp(14)
        root.addView(p, lp)
        panel = p
        p.pivotX = ui.dpf(240f)
        p.pivotY = 0f
        p.alpha = 0f
        p.scaleX = 0.7f
        p.scaleY = 0.7f
        p.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(300L)
            .setInterpolator(OvershootInterpolator(1.1f)).start()
    }

    private fun hidePanel() {
        val p = panel ?: return
        panel = null
        catcher?.let { root.removeView(it) }
        catcher = null
        p.animate().cancel()
        p.animate().alpha(0f).scaleX(0.85f).scaleY(0.85f).setDuration(150L)
            .withEndAction { root.removeView(p) }.start()
    }

    private fun buildPanelContent(p: LinearLayout) {
        p.removeAllViews()
        p.background = glass(ui.dpf(22f))
        val fg = fgColor()
        val divider = ColorUtils.setAlphaComponent(fg, 36)

        // 1. betűméret: kisebb / nagyobb
        val sizeRow = LinearLayout(ctx)
        sizeRow.orientation = LinearLayout.HORIZONTAL
        sizeRow.background = GradientDrawable().apply {
            cornerRadius = ui.dpf(14f)
            setColor(divider)
        }
        fun sizeBtn(sp: Float, desc: String, delta: Int) {
            val b = TextView(ctx)
            b.text = "A"
            b.textSize = sp
            b.gravity = Gravity.CENTER
            b.setTextColor(fg)
            b.contentDescription = desc
            val enabled = if (delta < 0) fontSize > MIN_SIZE else fontSize < MAX_SIZE
            b.alpha = if (enabled) 1f else 0.35f
            b.setOnClickListener {
                val n = (fontSize + delta).coerceIn(MIN_SIZE, MAX_SIZE)
                if (n != fontSize) {
                    ui.haptic()
                    fontSize = n
                    save()
                    applyStyle()
                }
            }
            sizeRow.addView(b, LinearLayout.LayoutParams(0, ui.dp(44), 1f))
        }
        sizeBtn(14f, L.t("Kisebb betű"), -1)
        sizeRow.addView(View(ctx).apply { setBackgroundColor(divider) }, LinearLayout.LayoutParams(ui.dp(1), ui.dp(26)).apply { gravity = Gravity.CENTER_VERTICAL })
        sizeBtn(22f, L.t("Nagyobb betű"), 1)
        p.addView(sizeRow, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        // 2. téma: világos / szépia / sötét
        val themeRow = LinearLayout(ctx)
        themeRow.orientation = LinearLayout.HORIZONTAL
        themeRow.gravity = Gravity.CENTER
        for ((key, label) in listOf("light" to L.t("Világos"), "sepia" to L.t("Szépia"), "dark" to L.t("Sötét"))) {
            val dot = View(ctx)
            dot.contentDescription = label
            dot.background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(bgColor(key))
                if (key == theme) setStroke(ui.dp(3), 0xFF0A84FF.toInt())
                else setStroke(ui.dp(1).coerceAtLeast(1), ColorUtils.setAlphaComponent(fg, 70))
            }
            dot.setOnClickListener {
                if (theme != key) {
                    ui.haptic()
                    theme = key
                    save()
                    applyStyle()
                }
            }
            val lp = LinearLayout.LayoutParams(ui.dp(40), ui.dp(40))
            lp.marginStart = ui.dp(10)
            lp.marginEnd = ui.dp(10)
            themeRow.addView(dot, lp)
        }
        val tlp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        tlp.topMargin = ui.dp(14)
        p.addView(themeRow, tlp)

        // 3. betűtípus: talpas / talpatlan
        val fontRow = LinearLayout(ctx)
        fontRow.orientation = LinearLayout.HORIZONTAL
        fontRow.setPadding(ui.dp(3), ui.dp(3), ui.dp(3), ui.dp(3))
        fontRow.background = GradientDrawable().apply {
            cornerRadius = ui.dpf(14f)
            setColor(divider)
        }
        for ((isSerif, label) in listOf(true to L.t("Talpas"), false to L.t("Talpatlan"))) {
            val b = TextView(ctx)
            b.text = label
            b.textSize = 15f
            b.gravity = Gravity.CENTER
            b.typeface = if (isSerif) Typeface.SERIF else Typeface.SANS_SERIF
            b.setTextColor(fg)
            if (isSerif == serif) {
                b.background = GradientDrawable().apply {
                    cornerRadius = ui.dpf(11f)
                    setColor(ColorUtils.setAlphaComponent(bgColor(), 255))
                }
                b.setTypeface(b.typeface, Typeface.BOLD)
            }
            b.setOnClickListener {
                if (serif != isSerif) {
                    ui.haptic()
                    serif = isSerif
                    save()
                    applyStyle()
                }
            }
            fontRow.addView(b, LinearLayout.LayoutParams(0, ui.dp(38), 1f))
        }
        val flp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        flp.topMargin = ui.dp(14)
        p.addView(fontRow, flp)
    }

    // ------------------------------------------------ bezárás

    fun close(then: (() -> Unit)? = null) {
        if (closing) return
        closing = true
        back?.remove()
        back = null
        root.animate().cancel()
        root.animate().alpha(0f).translationY(ui.dpf(28f)).scaleX(0.97f).scaleY(0.97f).setDuration(220L)
            .setInterpolator(DecelerateInterpolator())
            .withEndAction {
                (root.parent as? ViewGroup)?.removeView(root)
                try {
                    web.stopLoading()
                    web.destroy()
                } catch (e: Exception) {
                    // nincs teendő
                }
                onClosed()
                then?.invoke()
            }.start()
    }

    // ------------------------------------------------ HTML

    private fun html(a: ReaderArticle): String {
        val d = ctx.resources.displayMetrics.density
        val padTop = (topInset / d + 76).toInt()
        val padBottom = (bottomInset / d + 40).toInt()
        val minutes = if (a.words > 0) maxOf(1, Math.round(a.words / 220f)) else 0
        val meta = listOfNotNull(
            a.byline.takeIf { it.isNotBlank() }?.let { Reader.esc(it) },
            if (minutes > 0) "$minutes ${Reader.esc(L.t("perc olvasás"))}" else null
        ).joinToString(" · ")
        val lead = if (a.image.isNotBlank()) "<img class=\"lead\" src=\"${Reader.esc(a.image)}\" alt=\"\">" else ""
        val lang = Reader.esc(a.lang.ifBlank { "en" })
        val dir = if (a.dir.equals("rtl", true)) "rtl" else "ltr"
        return """<!DOCTYPE html><html lang="$lang" dir="$dir" style="font-size:${fontSize}px"><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1,maximum-scale=1">
<style>
html{-webkit-text-size-adjust:100%}
body{margin:0;padding:${padTop}px 24px ${padBottom}px;font-size:1rem;line-height:1.62;word-wrap:break-word;overflow-wrap:break-word;-webkit-tap-highlight-color:transparent;transition:background-color .25s,color .25s}
body.light{background:#fff;color:#1d1d1f}
body.sepia{background:#f8f1e3;color:#4f3a26}
body.dark{background:#1c1c1e;color:#e5e5e7}
body.serif{font-family:Georgia,'Noto Serif','Droid Serif',serif}
body.sans{font-family:-apple-system,Roboto,'Noto Sans','Helvetica Neue',sans-serif}
article{max-width:680px;margin:0 auto}
.site{font-family:-apple-system,Roboto,sans-serif;font-size:.72rem;letter-spacing:.06em;text-transform:uppercase;opacity:.55;margin-bottom:.5em}
h1.title{font-size:1.75rem;line-height:1.18;margin:0 0 .4em;font-weight:700}
.meta{font-family:-apple-system,Roboto,sans-serif;font-size:.8rem;opacity:.6;margin-bottom:1.8em}
h2,h3,h4,h5,h6{line-height:1.25;margin:1.5em 0 .5em}
h2{font-size:1.3rem}h3{font-size:1.15rem}h4,h5,h6{font-size:1rem}
p{margin:0 0 1em}
a{color:#0a84ff;text-decoration:none}
body.sepia a{color:#a0522d}
img{max-width:100%;height:auto;display:block;margin:1.2em auto;border-radius:12px}
img.lead{margin:0 0 1.4em}
figure{margin:1.4em 0}
figcaption{font-family:-apple-system,Roboto,sans-serif;font-size:.78rem;opacity:.65;text-align:center;margin-top:-.6em}
blockquote{margin:1.2em 0;padding:.1em 0 .1em 1em;border-left:3px solid currentColor;opacity:.85;font-style:italic}
pre,code{font-family:'Roboto Mono',monospace;font-size:.82em}
pre{overflow-x:auto;padding:12px 14px;border-radius:12px;background:rgba(127,127,127,.13)}
ul,ol{padding-left:1.4em}li{margin:.3em 0}
table{border-collapse:collapse;display:block;overflow-x:auto;font-size:.85em;margin:1em 0}
td,th{border:1px solid rgba(127,127,127,.3);padding:5px 8px}
hr{border:0;border-top:1px solid rgba(127,127,127,.3);margin:2em 0}
</style></head>
<body class="$theme ${if (serif) "serif" else "sans"}"><article>
<div class="site">${Reader.esc(a.site)}</div>
<h1 class="title">${Reader.esc(a.title)}</h1>
${if (meta.isNotEmpty()) "<div class=\"meta\">$meta</div>" else ""}
$lead
${a.content}
</article></body></html>"""
    }

    companion object {
        const val KEY_SIZE = "reader_font_size"
        const val KEY_THEME = "reader_theme"
        const val KEY_SERIF = "reader_serif"
        const val MIN_SIZE = 14
        const val MAX_SIZE = 30
    }
}
