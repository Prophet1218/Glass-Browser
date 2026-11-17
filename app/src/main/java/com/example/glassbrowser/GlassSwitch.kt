package com.example.glassbrowser

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.Configuration
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.Choreographer
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import android.view.ViewConfiguration
import android.widget.Checkable
import androidx.preference.PreferenceViewHolder
import androidx.preference.SwitchPreference
import kotlin.math.abs
import kotlin.math.exp

/**
 * Kapcsoló a csúszkák kapszulájával: nyugalomban fehér kapszula a sávon; lenyomva megnő és
 * valódi üveggé válik (ugyanaz a fénytörő shader rajzolja, mint a csúszkáknál, a [SliderGlass]
 * hostján át). Átkapcsoláskor rugóval csúszik át a másik oldalra (enyhe túllendüléssel, a
 * haladás irányában a sebességgel arányosan megnyúlva), ujjal húzható is: lemaradva, nyúlva
 * követi az ujjat, elengedéskor a közelebbi oldalra pattan. Minden mozgást a [GlassSpring]
 * rugók számolnak képkockánként (Choreographer, 120 Hz-en is), nyugalomban a képkockázás leáll.
 */
class GlassSwitchView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs), Checkable {

    private val d = resources.displayMetrics.density
    private val night = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
        Configuration.UI_MODE_NIGHT_YES

    private val trackW = 52f * d
    private val trackH = 30f * d
    private val restW = 30f * d
    private val restH = 24f * d
    private val glassW = 46f * d
    private val glassH = 34f * d

    private var checked = false
    /** false = még nincs kezdőhelyzet (a következő setChecked azonnal, animáció nélkül állít) */
    private var posInit = false
    /** 0 = fehér kapszula, 1 = üveg (rugóval, enyhe túllendüléssel) */
    private var glass = 0f
    private var shaderDraws = false
    private var boundKey: String? = null

    /**
     * A felhasználó (koppintással vagy húzással) erre az értékre kapcsolná: a beállítás dönti el,
     * elfogadja-e (változásfigyelő, mentés); igazat ad, ha elfogadta. Ha nincs megadva, a nézet
     * maga kapcsol.
     */
    var onUserToggle: ((Boolean) -> Boolean)? = null

    // ---- folyékony mozgás: rugók (a csúszka kapszulájával azonos integrátor)
    /** A kapszula helyzete: 0 = ki, 1 = be; enyhén túllendül. */
    private val posSpring = GlassSpring(380f, 0.62f)
    /** Lenyomás / elengedés: üveggé nő, majd visszahúzódik. */
    private val pressSpring = GlassSpring(420f, 0.58f)
    /** Nyúlás a mozgás irányában, a sebességgel arányosan; megálláskor visszarugózik. */
    private val stretchSpring = GlassSpring(520f, 0.42f)
    /** A sáv színe: 0 = kikapcsolt szürke, 1 = kék (túllendülés nélkül). */
    private val colorSpring = GlassSpring(260f, 1f)
    private var lastFrame = 0L
    private var running = false
    private val frame = Choreographer.FrameCallback { now -> onFrame(now) }

    // ---- érintés
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop.toFloat()
    private var touching = false
    private var dragging = false
    /** Átkapcsolás közben üveg marad, és csak a cél közelében húzódik vissza fehér kapszulává. */
    private var transit = false
    private var downX = 0f
    private var downY = 0f
    private var downPos = 0f
    private var velocity: VelocityTracker? = null

    private val track = Paint(Paint.ANTI_ALIAS_FLAG)
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1f * d
    }
    private val shadow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(1, 0, 0, 0)
        setShadowLayer(5f * d, 0f, 2f * d, Color.argb(60, 0, 0, 0))
    }
    private val rect = RectF()
    private val screenRect = RectF()
    private val loc = IntArray(2)

    /** A kapszula középpontjának útja a sáv két vége között (képpont). */
    private val travel: Float get() = trackW - (trackH - restH) - restW

    /**
     * A sor újrahasznosításakor (másik beállítás kerül ugyanebbe a nézetbe) nem animálunk,
     * csak a tényleges átkapcsoláskor.
     */
    fun bindKey(key: String?) {
        if (boundKey != key) {
            boundKey = key
            stopFrames()
            resetToRest()
            posInit = false // a következő setChecked azonnal beállítja
        }
    }

    override fun isChecked(): Boolean = checked

    override fun toggle() {
        setChecked(!checked)
    }

    override fun setChecked(value: Boolean) {
        val target = if (value) 1f else 0f
        checked = value
        if (!posInit || !isLaidOut || !isAttachedToWindow) {
            // nincs mit animálni (első kötés, még nincs elrendezve): azonnal a helyére
            posInit = true
            posSpring.target = target
            posSpring.snap()
            colorSpring.target = target
            colorSpring.snap()
            invalidate()
            return
        }
        if (dragging) return // húzás közben az ujj vezeti a kapszulát; elengedéskor dől el
        if (posSpring.target == target && colorSpring.target == target && !running &&
            abs(posSpring.value - target) < 0.001f
        ) return
        animateTo(target)
    }

    /** Rugóval a [target] oldalra; útközben üveggé válik, a cél közelében visszahúzódik. */
    private fun animateTo(target: Float) {
        posSpring.stiffness = 380f
        posSpring.damping = 0.62f
        posSpring.target = target
        colorSpring.target = target
        if (abs(posSpring.value - target) > 0.18f) {
            transit = true
            setPressTarget(true)
        } else if (!touching) {
            transit = false
            setPressTarget(false)
        }
        startFrames()
    }

    private fun setPressTarget(on: Boolean) {
        // lenyomva gyors, rugózó "kipattanás" üveggé; elengedve lágyabb, alig túllendülő visszahúzódás
        if (on) {
            pressSpring.target = 1f
            pressSpring.stiffness = 420f
            pressSpring.damping = 0.58f
        } else {
            pressSpring.target = 0f
            pressSpring.stiffness = 300f
            pressSpring.damping = 0.82f
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!isEnabled) return false
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                touching = true
                dragging = false
                transit = false
                downX = event.x
                downY = event.y
                downPos = posSpring.value.coerceIn(0f, 1f)
                velocity?.recycle()
                velocity = VelocityTracker.obtain().also { it.addMovement(event) }
                setPressTarget(true)
                startFrames()
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (!touching) return false
                velocity?.addMovement(event)
                val dx = event.x - downX
                val dy = event.y - downY
                if (!dragging) {
                    if (abs(dx) > touchSlop && abs(dx) > abs(dy)) {
                        // vízszintes húzás: a lista ne görgessen el alóla
                        dragging = true
                        parent?.requestDisallowInterceptTouchEvent(true)
                        downX = event.x
                        downPos = posSpring.value.coerceIn(0f, 1f)
                    } else {
                        return true
                    }
                }
                var p = downPos + (event.x - downX) / travel
                // a végeken túl gumiszerűen, egyre nehezebben megy tovább
                if (p > 1f) p = 1f + rubber(p - 1f)
                if (p < 0f) p = -rubber(-p)
                // húzás közben merevebb, csillapítottabb rugó: kicsit lemarad az ujjtól, majd utoléri
                posSpring.stiffness = 900f
                posSpring.damping = 0.74f
                posSpring.target = p
                colorSpring.target = if (p >= 0.5f) 1f else 0f
                startFrames()
                return true
            }
            MotionEvent.ACTION_UP -> {
                if (!touching) return false
                velocity?.addMovement(event)
                val wasDragging = dragging
                var vx = 0f
                velocity?.let {
                    it.computeCurrentVelocity(1000)
                    vx = it.xVelocity
                }
                endTouch()
                val newVal = if (wasDragging) {
                    // a lendület is számít: egy gyors pöccintés átviszi a másik oldalra
                    val projected = posSpring.target + (vx / travel) * 0.08f
                    projected >= 0.5f
                } else {
                    !checked
                }
                if (newVal != checked) {
                    // a beállítás csak a következő elrendezéskor köti újra a sort: addig is a
                    // nézet már az új értékkel animál (az újrakötés ugyanezt a célt kapja)
                    val ok = onUserToggle?.invoke(newVal) ?: true
                    if (ok) checked = newVal
                }
                // elfogadva az új oldalra, elutasítva / változatlanul vissza a helyére
                animateTo(if (checked) 1f else 0f)
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                if (!touching) return false
                endTouch()
                animateTo(if (checked) 1f else 0f)
                return true
            }
        }
        return touching
    }

    private fun endTouch() {
        touching = false
        dragging = false
        velocity?.recycle()
        velocity = null
        parent?.requestDisallowInterceptTouchEvent(false)
    }

    /** Gumiszalag-hatás a végeken túl: legfeljebb kb. 0,14-nyi túlhúzás. */
    private fun rubber(over: Float): Float = 0.14f * (1f - exp(-over * 2.2f))

    private fun startFrames() {
        if (running) return
        running = true
        lastFrame = 0L
        Choreographer.getInstance().postFrameCallback(frame)
    }

    private fun stopFrames() {
        if (!running) return
        running = false
        Choreographer.getInstance().removeFrameCallback(frame)
    }

    /** Azonnal nyugalmi állapot: fehér kapszula a jelenlegi oldalon, nincs üveg. */
    private fun resetToRest() {
        val target = if (checked) 1f else 0f
        touching = false
        dragging = false
        transit = false
        velocity?.recycle()
        velocity = null
        posSpring.stiffness = 380f
        posSpring.damping = 0.62f
        posSpring.target = target
        posSpring.snap()
        colorSpring.target = target
        colorSpring.snap()
        pressSpring.target = 0f
        pressSpring.snap()
        stretchSpring.target = 0f
        stretchSpring.snap()
        glass = 0f
        shaderDraws = false
        SliderGlass.hide(this)
    }

    private fun onFrame(now: Long) {
        if (!running) return
        if (!isAttachedToWindow) {
            // a sor eltűnt (görgetés, bezárás): azonnal nyugalmi állapot
            running = false
            resetToRest()
            return
        }
        val dt = if (lastFrame == 0L) 1f / 120f else ((now - lastFrame) / 1e9f).coerceIn(1f / 480f, 1f / 24f)
        lastFrame = now

        // átkapcsolás vége felé az üveg visszahúzódik fehér kapszulává
        if (transit && !touching && abs(posSpring.value - posSpring.target) < 0.18f) {
            transit = false
            setPressTarget(false)
        }

        // nyúlás a kapszula sebességéből (dp / mp)
        val dpSpeed = abs(posSpring.velocity) * travel / d
        stretchSpring.target = MAX_STRETCH * (1f - exp(-dpSpeed / 160f))

        posSpring.step(dt)
        pressSpring.step(dt)
        stretchSpring.step(dt)
        colorSpring.step(dt)
        glass = pressSpring.value
        if (!touching && !transit && glass < 0.005f && pressSpring.velocity < 0f) glass = 0f

        syncHost()
        invalidate()

        // nyugalomban (lenyomva tartott, mozdulatlan ujjnál is) leáll a képkockázás; mozdulásra,
        // elengedésre vagy átkapcsolásra újraindul
        val rest = !transit && posSpring.atRest(0.001f) && pressSpring.atRest(0.002f) &&
            stretchSpring.atRest(0.002f) && colorSpring.atRest(0.002f)
        if (rest) {
            posSpring.snap()
            pressSpring.snap()
            stretchSpring.snap()
            colorSpring.snap()
            glass = pressSpring.value
            running = false
            syncHost()
            invalidate()
        } else {
            Choreographer.getInstance().postFrameCallback(frame)
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        stopFrames()
        resetToRest()
    }

    /**
     * A kapszula pillanatnyi helye és mérete: rugózva kicsit túl is lendülhet, mozgás közben a
     * haladás irányában megnyúlik (és közben kicsit elvékonyodik).
     */
    private fun thumbRect(out: RectF) {
        val left = (width - trackW) / 2f
        val cy = height / 2f
        val p = posSpring.value.coerceIn(-0.2f, 1.2f)
        val inset = (trackH - restH) / 2f
        val startCx = left + inset + restW / 2f
        val cx = startCx + travel * p
        val g = glass.coerceIn(-0.05f, 1.15f)
        val s = stretchSpring.value.coerceIn(-0.12f, MAX_STRETCH * 1.3f)
        val w = (restW + (glassW - restW) * g) * (1f + s)
        val h = (restH + (glassH - restH) * g) * (1f - 0.32f * s)
        out.set(cx - w / 2f, cy - h / 2f, cx + w / 2f, cy + h / 2f)
    }

    /** Megmondja a képernyőnek, hol kell üveget rajzolnia (vagy hogy már nem kell). */
    private fun syncHost() {
        if (glass <= 0.04f || !isAttachedToWindow) {
            shaderDraws = false
            SliderGlass.hide(this)
            return
        }
        thumbRect(rect)
        getLocationInWindow(loc)
        screenRect.set(rect.left + loc[0], rect.top + loc[1], rect.right + loc[0], rect.bottom + loc[1])
        shaderDraws = SliderGlass.show(this, screenRect)
    }

    override fun onDraw(canvas: Canvas) {
        val left = (width - trackW) / 2f
        val top = (height - trackH) / 2f

        // sáv: kikapcsolva szürke, bekapcsolva kék (a szín rugóval, lágyan vált)
        val off = if (night) Color.argb(70, 255, 255, 255) else Color.argb(46, 0, 0, 0)
        track.color = blend(off, BLUE, colorSpring.value.coerceIn(0f, 1f))
        rect.set(left, top, left + trackW, top + trackH)
        canvas.drawRoundRect(rect, trackH / 2f, trackH / 2f, track)

        thumbRect(rect)
        val r = rect.height() / 2f
        val g = glass.coerceIn(0f, 1f)

        // lágy árnyék a kapszula alatt (a kitöltése szinte átlátszó, csak az árnyéka látszik)
        canvas.drawRoundRect(rect, r, r, shadow)

        fill.color = Color.WHITE
        if (shaderDraws) {
            // a fehér kapszula elhalványul, a helyén az üveg (a shader) marad
            fill.alpha = (255 * (1f - g)).toInt().coerceIn(0, 255)
            if (fill.alpha > 0) canvas.drawRoundRect(rect, r, r, fill)
        } else {
            // shader nélkül (régebbi Android): áttetsző kapszula szegéllyel
            val glassAlpha = if (night) 70f else 120f
            fill.alpha = (255f + (glassAlpha - 255f) * g).toInt().coerceIn(0, 255)
            canvas.drawRoundRect(rect, r, r, fill)
            stroke.color = if (night) Color.argb(230, 255, 255, 255) else Color.argb(90, 40, 50, 70)
            stroke.alpha = (stroke.alpha * g).toInt().coerceIn(0, 255)
            if (stroke.alpha > 0) canvas.drawRoundRect(rect, r, r, stroke)
        }
        if (g < 1f) {
            // nyugalmi állapotban hajszálvékony szegély, hogy világos háttéren is látsszon
            stroke.color = Color.argb(34, 0, 0, 0)
            stroke.alpha = (34 * (1f - g)).toInt().coerceIn(0, 255)
            if (stroke.alpha > 0) canvas.drawRoundRect(rect, r, r, stroke)
        }
    }

    private fun blend(a: Int, b: Int, t: Float): Int {
        fun ch(shift: Int): Int {
            val x = (a shr shift) and 0xFF
            val y = (b shr shift) and 0xFF
            return (x + (y - x) * t).toInt().coerceIn(0, 255)
        }
        return (ch(24) shl 24) or (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
    }

    companion object {
        private const val BLUE = 0xFF0A84FF.toInt()
        /** A legnagyobb nyúlás gyors mozgásnál (a szélesség ennyiszeresével nő). */
        private const val MAX_STRETCH = 0.26f
    }
}

/** Kapcsolós beállítás az üvegkapszulás kapcsolóval. */
class GlassSwitchPreference @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : SwitchPreference(context, attrs) {

    init {
        widgetLayoutResource = R.layout.pref_glass_switch
    }

    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        (holder.findViewById(android.R.id.switch_widget) as? GlassSwitchView)?.let { v ->
            v.bindKey(key)
            // a kapszulán koppintás / húzás ugyanúgy megy, mint a sorra koppintás: előbb a
            // változásfigyelő dönt, utána mentés és értesítés
            v.onUserToggle = { newValue ->
                if (newValue == isChecked) {
                    true
                } else if (callChangeListener(newValue)) {
                    isChecked = newValue
                    true
                } else {
                    false
                }
            }
        }
        super.onBindViewHolder(holder)
    }
}
