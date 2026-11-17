package com.example.glassbrowser

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.view.MotionEvent
import android.view.animation.DecelerateInterpolator
import android.webkit.WebView

/**
 * WebView, amely az oldal tetején "felső betéttel" indul: annyival lejjebb van tolva, amilyen
 * magas az állapotsor, hogy az oldal eleje ne legyen takarásban. Amikor felfelé húzod, először ez
 * a betét csukódik össze (az ujjaddal pontosan együtt mozog, ezért nem akad), és csak utána
 * kezd görgetni az oldal. Így a tartalom valóban átúszik az állapotsor mögött.
 *
 * [topInset] = 0 esetén sima WebView-ként viselkedik.
 */
@SuppressLint("ViewConstructor")
class GlassWebView(context: Context) : WebView(context) {

    /** A betét magassága képpontban (az állapotsor magassága), 0 = kikapcsolva. */
    var topInset = 0
        set(value) {
            if (field == value) return
            field = value
            insetAnim?.cancel()
            translationY = if (value > 0 && scrollY <= 0) value.toFloat() else 0f
            onInsetChanged?.invoke()
        }

    /**
     * Húzásra frissítés: [onPull] a lehúzás mértékét adja képpontban (0 = vége), [onPullRelease]
     * elengedéskor hívódik a végső mértékkel. Csak akkor indul, ha az oldal tényleg a tetején van
     * (a WebView jelezte, hogy felfelé már nem tud tovább görgetni), így egy oldalon belüli
     * görgethető lista tetején nem frissít véletlenül.
     */
    var onPull: ((Float) -> Unit)? = null
    var onPullRelease: ((Float) -> Unit)? = null
    private var pull = 0f
    private var topReached = false
    private var pullLastY = 0f

    /** Akkor hívódik, amikor a betét (a translationY) megváltozik. */
    var onInsetChanged: (() -> Unit)? = null

    /** Az utoljára lefuttatott térköz-szkript (lásd MainActivity.applyWebTop); null = még nem / új oldal. */
    var appliedPadJs: String? = null

    private var lastRawY = 0f

    /** Ennyi mozgást engedünk át a WebView-nak a betét csukódása közben (lásd lent). */
    private val slopPass = android.view.ViewConfiguration.get(context).scaledTouchSlop * 1.4f
    private var passedAbs = 0f      // ebből már ennyit átengedtünk ebben a húzásban
    private var passedSum = 0f      // az átengedett mozgás előjeles összege
    private var touching = false
    private var insetAnim: ValueAnimator? = null

    /** Új oldal betöltésekor: a betét újra teljesen nyitva. */
    fun resetInset() {
        if (topInset > 0 && !touching) animateInset(topInset.toFloat())
    }

    override fun onOverScrolled(scrollX: Int, scrollY: Int, clampedX: Boolean, clampedY: Boolean) {
        super.onOverScrolled(scrollX, scrollY, clampedX, clampedY)
        if (clampedY && scrollY <= 0) topReached = true
    }

    /** A lehúzás követése; az eseményt nem nyeli el, a WebView ugyanúgy megkapja. */
    private fun trackPull(event: MotionEvent) {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                pull = 0f
                topReached = false
                pullLastY = event.rawY
            }
            MotionEvent.ACTION_MOVE -> {
                val dy = event.rawY - pullLastY
                pullLastY = event.rawY
                if (event.pointerCount != 1) return
                if (dy > 0f && topReached && scrollY <= 0) {
                    pull += dy * 0.5f               // ellenállással: fele annyit mozdul, mint az ujj
                    onPull?.invoke(pull)
                } else if (dy < 0f && pull > 0f) {
                    pull = (pull + dy * 0.5f).coerceAtLeast(0f)
                    onPull?.invoke(pull)
                    if (pull <= 0f) topReached = false
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (pull > 0f) onPullRelease?.invoke(if (event.actionMasked == MotionEvent.ACTION_UP) pull else 0f)
                pull = 0f
                topReached = false
            }
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        trackPull(event)
        val inset = topInset
        if (inset <= 0) return super.onTouchEvent(event)

        var consumed = 0f
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                insetAnim?.cancel()
                touching = true
                lastRawY = event.rawY
                passedAbs = 0f
                passedSum = 0f
            }
            MotionEvent.ACTION_MOVE -> {
                val rawY = event.rawY
                val dy = rawY - lastRawY          // pozitív: az ujj lefelé mozog
                lastRawY = rawY
                if (event.pointerCount == 1) {
                    val t = translationY
                    if (dy < 0f && t > 0f) {
                        consumed = maxOf(dy, -t)                    // felfelé: előbb a betét csukódik
                    } else if (dy > 0f && t < inset && scrollY <= 0) {
                        consumed = minOf(dy, inset - t)             // az oldal tetején lefelé: kinyílik
                    }
                    if (consumed != 0f) {
                        translationY = t + consumed
                        onInsetChanged?.invoke()
                    }
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                touching = false
                settle()
            }
        }

        // Amennyit a betét elmozdult, annyit "elrejtünk" az eseményből, így a WebView ezt a mozgást
        // nem látja görgetésnek. Egy keveset (a görgetés indulási küszöbénél kicsit többet) mégis
        // átengedünk: így a WebView már a betét csukódása alatt "elindul", és amikor a betét
        // elfogyott, az oldal azonnal, megtorpanás nélkül görget tovább.
        var hide = consumed
        if (consumed != 0f) {
            val allow = minOf(kotlin.math.abs(consumed), (slopPass - passedAbs).coerceAtLeast(0f))
            if (allow > 0f) {
                hide = if (consumed < 0f) consumed + allow else consumed - allow
            }
            passedAbs += allow
        }
        // a nézet közben elmozdult: a korábban átengedett rész miatt állandó eltolás is kell
        val offset = passedSum - hide
        passedSum += consumed - hide
        if (offset != 0f) event.offsetLocation(0f, offset)
        val handled = super.onTouchEvent(event)
        if (offset != 0f) event.offsetLocation(0f, -offset)
        return handled
    }

    override fun onScrollChanged(l: Int, t: Int, oldl: Int, oldt: Int) {
        super.onScrollChanged(l, t, oldl, oldt)
        val inset = topInset
        if (inset <= 0 || touching) return
        // lendületből vagy az oldal saját görgetéséből adódó helyzet: a betétet utánaigazítjuk
        if (t <= 0 && translationY < inset) {
            animateInset(inset.toFloat())
        } else if (t > inset && translationY > 0f) {
            animateInset(0f)
        }
    }

    /** Elengedéskor a félig nyitott betét a közelebbi végállásba áll. */
    private fun settle() {
        val inset = topInset
        val t = translationY
        if (inset <= 0 || t <= 0f || t >= inset) return
        animateInset(if (scrollY <= 0 && t > inset / 2f) inset.toFloat() else 0f)
    }

    private fun animateInset(target: Float) {
        insetAnim?.cancel()
        val from = translationY
        if (from == target) return
        val anim = ValueAnimator.ofFloat(from, target)
        anim.duration = 180
        anim.interpolator = DecelerateInterpolator()
        anim.addUpdateListener {
            translationY = it.animatedValue as Float
            onInsetChanged?.invoke()
        }
        insetAnim = anim
        anim.start()
    }
}
