package com.example.glassbrowser

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.view.View
import android.view.animation.Interpolator
import android.view.animation.LinearInterpolator
import android.view.animation.PathInterpolator
import androidx.preference.PreferenceGroup
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.SimpleItemAnimator
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Csillapított rugó görbéje (0 → 1): gyors indulás, finom (~1,5 %-os) túllendülés, lágy
 * megérkezés, mint az iOS rugós animációi. A végén pontosan 1.
 */
class SpringInterpolator(private val damping: Float = 0.8f) : Interpolator {
    // a burkológörbe t = 1-nél kb. 0,2 %-ra csillapodik
    private val zw = 6.2f
    private val w = zw / damping
    private val wd = w * sqrt(1f - damping * damping)

    override fun getInterpolation(t: Float): Float {
        if (t <= 0f) return 0f
        if (t >= 1f) return 1f
        val e = exp(-zw * t)
        return 1f - e * (cos(wd * t) + (zw / wd) * sin(wd * t))
    }
}

/**
 * A beállítások szakaszainak nyitás / zárás animációja (csak a váltás idejére van bekapcsolva):
 * - a megmaradó sorok rugós görbével csúsznak az új helyükre,
 * - az új sorok lépcsőzetesen, fentről kicsit lecsúszva tűnnek fel,
 * - az eltűnők gyorsan elhalványulnak, közben kicsit felfelé csúsznak.
 * Áttűnéses "change" animáció nincs: a sor ugyanabban a nézetben frissül (a kapcsolók
 * kapszulája így tényleg átcsúszik, a nyíl forgása sem ugrik).
 */
class SectionItemAnimator(context: Context) : SimpleItemAnimator() {

    private val density = context.resources.displayMetrics.density
    private val shift = 14f * density

    private val pendingRemoves = ArrayList<RecyclerView.ViewHolder>()
    private val pendingAdds = ArrayList<RecyclerView.ViewHolder>()
    private val pendingMoves = ArrayList<RecyclerView.ViewHolder>()
    private val running = HashMap<RecyclerView.ViewHolder, Animator>()

    /** A most megjelenő / eltűnő sorok: a kártya ezek átlátszóságával arányosan nyúlik. */
    val fading = HashSet<View>()

    private val spring = SpringInterpolator(0.8f)
    private val decel = PathInterpolator(0.2f, 0.9f, 0.25f, 1f)
    private val accel = PathInterpolator(0.4f, 0f, 0.8f, 0.6f)

    init {
        supportsChangeAnimations = false
        // ~15% slower than before (user request)
        moveDuration = 575L
        addDuration = 440L
        removeDuration = 200L
        changeDuration = 0L
    }

    override fun animateRemove(holder: RecyclerView.ViewHolder): Boolean {
        endAnimation(holder)
        pendingRemoves.add(holder)
        return true
    }

    override fun animateAdd(holder: RecyclerView.ViewHolder): Boolean {
        endAnimation(holder)
        val v = holder.itemView
        v.alpha = 0f
        v.translationY = -shift
        fading.add(v)
        pendingAdds.add(holder)
        return true
    }

    override fun animateMove(
        holder: RecyclerView.ViewHolder, fromX: Int, fromY: Int, toX: Int, toY: Int
    ): Boolean {
        val v = holder.itemView
        // ha épp mozgott, a látható helyéről folytatja (nincs ugrás)
        val fx = fromX + v.translationX.toInt()
        val fy = fromY + v.translationY.toInt()
        endAnimation(holder)
        val dx = toX - fx
        val dy = toY - fy
        if (dx == 0 && dy == 0) {
            dispatchMoveFinished(holder)
            return false
        }
        v.translationX = -dx.toFloat()
        v.translationY = -dy.toFloat()
        pendingMoves.add(holder)
        return true
    }

    override fun animateChange(
        oldHolder: RecyclerView.ViewHolder, newHolder: RecyclerView.ViewHolder?,
        fromLeft: Int, fromTop: Int, toLeft: Int, toTop: Int
    ): Boolean {
        if (newHolder == null || oldHolder === newHolder) {
            return animateMove(oldHolder, fromLeft, fromTop, toLeft, toTop)
        }
        // külön nézet (ritka eset): áttűnés nélkül, azonnal a végállapot
        endAnimation(oldHolder)
        endAnimation(newHolder)
        resetView(oldHolder.itemView)
        resetView(newHolder.itemView)
        dispatchChangeFinished(oldHolder, true)
        dispatchChangeFinished(newHolder, false)
        return false
    }

    override fun runPendingAnimations() {
        if (pendingRemoves.isEmpty() && pendingMoves.isEmpty() && pendingAdds.isEmpty()) return
        val hadRemoves = pendingRemoves.isNotEmpty()

        // eltűnők: alulról felfelé, gyorsan
        val removes = pendingRemoves.sortedByDescending { it.itemView.top }
        pendingRemoves.clear()
        removes.forEachIndexed { i, h -> startRemove(h, min(i, 8) * 10L) }

        // mozgók: mind egyszerre indulnak, rugós görbével. Bezáráskor kicsit később, hogy a
        // felcsúszó kártyák ne takarják el a még halványuló sorokat (nincs átfedés).
        val moves = ArrayList(pendingMoves)
        pendingMoves.clear()
        val moveDelay = if (hadRemoves) (removeDuration * 0.55f).toLong() else 0L
        for (h in moves) startMove(h, moveDelay)

        // újak: fentről lefelé, akkor jelennek meg, amikor a lefelé csúszó tartalom már
        // elhagyta a helyüket (nyitáskor a következő kártya nem fut rá a beúszó sorokra)
        val adds = pendingAdds.sortedBy { it.itemView.top }
        pendingAdds.clear()
        if (adds.isNotEmpty()) {
            val top = adds.first().itemView.top
            val span = (adds.last().itemView.bottom - top).coerceAtLeast(1)
            val hasMoves = moves.isNotEmpty()
            adds.forEach { h ->
                val frac = (h.itemView.top - top).toFloat() / span
                val d = if (hasMoves) (moveDuration * (0.10f + 0.34f * frac)).toLong() else (24f + 200f * frac).toLong()
                startAdd(h, moveDelay + d)
            }
        }
    }

    private fun startMove(holder: RecyclerView.ViewHolder, delay: Long = 0L) {
        val v = holder.itemView
        val sx = v.translationX
        val sy = v.translationY
        val a = ValueAnimator.ofFloat(0f, 1f)
        a.duration = moveDuration
        a.startDelay = delay
        a.interpolator = LinearInterpolator()
        a.addUpdateListener {
            val f = 1f - spring.getInterpolation(it.animatedFraction)
            v.translationX = sx * f
            v.translationY = sy * f
        }
        a.addListener(object : AnimatorListenerAdapter() {
            private var started = false
            override fun onAnimationStart(animation: Animator) {
                if (started) return
                started = true
                dispatchMoveStarting(holder)
            }

            override fun onAnimationEnd(animation: Animator) {
                if (running.remove(holder) == null) return
                v.translationX = 0f
                v.translationY = 0f
                dispatchMoveFinished(holder)
                finishIfDone()
            }
        })
        running[holder] = a
        a.start()
    }

    private fun startAdd(holder: RecyclerView.ViewHolder, delay: Long) {
        val v = holder.itemView
        val oldLayer = v.layerType
        val a = ValueAnimator.ofFloat(0f, 1f)
        a.duration = addDuration
        a.startDelay = delay
        a.interpolator = LinearInterpolator()
        a.addUpdateListener {
            val r = it.animatedFraction
            v.alpha = decel.getInterpolation(min(1f, r * 1.25f))
            v.translationY = -shift * (1f - spring.getInterpolation(r))
        }
        a.addListener(object : AnimatorListenerAdapter() {
            private var started = false
            override fun onAnimationStart(animation: Animator) {
                if (started) return
                started = true
                // áttűnés közben hardveres rétegen: a sor egyben halványul, nincs szellemkép
                if (oldLayer == View.LAYER_TYPE_NONE) v.setLayerType(View.LAYER_TYPE_HARDWARE, null)
                dispatchAddStarting(holder)
            }

            override fun onAnimationEnd(animation: Animator) {
                if (running.remove(holder) == null) return
                if (oldLayer == View.LAYER_TYPE_NONE) v.setLayerType(View.LAYER_TYPE_NONE, null)
                resetView(v)
                fading.remove(v)
                dispatchAddFinished(holder)
                finishIfDone()
            }
        })
        running[holder] = a
        a.start()
    }

    private fun startRemove(holder: RecyclerView.ViewHolder, delay: Long) {
        val v = holder.itemView
        val oldLayer = v.layerType
        val a0 = v.alpha
        val ty0 = v.translationY
        fading.add(v)
        val a = ValueAnimator.ofFloat(0f, 1f)
        a.duration = removeDuration
        a.startDelay = delay
        a.interpolator = LinearInterpolator()
        a.addUpdateListener {
            val r = it.animatedFraction
            v.alpha = a0 * (1f - decel.getInterpolation(r))
            v.translationY = ty0 - shift * 0.7f * accel.getInterpolation(r)
        }
        a.addListener(object : AnimatorListenerAdapter() {
            private var started = false
            override fun onAnimationStart(animation: Animator) {
                if (started) return
                started = true
                if (oldLayer == View.LAYER_TYPE_NONE) v.setLayerType(View.LAYER_TYPE_HARDWARE, null)
                dispatchRemoveStarting(holder)
            }

            override fun onAnimationEnd(animation: Animator) {
                if (running.remove(holder) == null) return
                if (oldLayer == View.LAYER_TYPE_NONE) v.setLayerType(View.LAYER_TYPE_NONE, null)
                resetView(v)
                fading.remove(v)
                dispatchRemoveFinished(holder)
                finishIfDone()
            }
        })
        running[holder] = a
        a.start()
    }

    private fun resetView(v: View) {
        v.alpha = 1f
        v.translationX = 0f
        v.translationY = 0f
    }

    private fun finishIfDone() {
        if (!isRunning) dispatchAnimationsFinished()
    }

    override fun endAnimation(item: RecyclerView.ViewHolder) {
        val v = item.itemView
        if (pendingMoves.remove(item)) {
            v.translationX = 0f
            v.translationY = 0f
            dispatchMoveFinished(item)
        }
        if (pendingAdds.remove(item)) {
            resetView(v)
            fading.remove(v)
            dispatchAddFinished(item)
        }
        if (pendingRemoves.remove(item)) {
            resetView(v)
            fading.remove(v)
            dispatchRemoveFinished(item)
        }
        // a futó animáció végére ugrik; a befejezést a saját figyelője intézi
        running[item]?.end()
        finishIfDone()
    }

    override fun endAnimations() {
        for (h in ArrayList(pendingMoves)) {
            pendingMoves.remove(h)
            h.itemView.translationX = 0f
            h.itemView.translationY = 0f
            dispatchMoveFinished(h)
        }
        for (h in ArrayList(pendingAdds)) {
            pendingAdds.remove(h)
            resetView(h.itemView)
            fading.remove(h.itemView)
            dispatchAddFinished(h)
        }
        for (h in ArrayList(pendingRemoves)) {
            pendingRemoves.remove(h)
            resetView(h.itemView)
            fading.remove(h.itemView)
            dispatchRemoveFinished(h)
        }
        for (a in ArrayList(running.values)) a.end()
        fading.clear()
        dispatchAnimationsFinished()
    }

    override fun isRunning(): Boolean =
        pendingMoves.isNotEmpty() || pendingAdds.isNotEmpty() || pendingRemoves.isNotEmpty() ||
            running.isNotEmpty()
}

/**
 * A beállítások szakaszai lekerekített üvegkártyákon: a szakasz címsora és (nyitva) a sorai
 * mögé egy áttetsző, finom peremű lapot rajzol. A lap a sorok pillanatnyi (animált) helyét
 * követi, így nyitáskor / záráskor együtt nyúlik és zsugorodik a tartalommal.
 */
class SectionCardDecoration(
    context: Context,
    private val sections: List<SectionPreference>,
    private val animator: SectionItemAnimator,
    night: Boolean
) : RecyclerView.ItemDecoration() {

    private val d = context.resources.displayMetrics.density
    private val inset = (14 * d).toInt()
    private val radius = 22f * d
    private val gap = 14f * d
    private val sepInset = 18f * d

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = if (night) 0x1CFFFFFF else 0x9EFFFFFF.toInt()
    }
    private val rimShader = LinearGradient(
        0f, 0f, 0f, 1f,
        if (night) 0x47FFFFFF else 0xF2FFFFFF.toInt(),
        if (night) 0x12FFFFFF else 0x1A000000,
        Shader.TileMode.CLAMP
    )
    private val rim = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = max(1f, 0.8f * d)
        shader = rimShader
    }
    private val sep = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = max(1f, 0.6f * d)
        color = if (night) 0x24FFFFFF else 0x1F000000
    }
    private val rimMatrix = Matrix()
    private val rect = RectF()

    // pozíció → szakasz sorszáma (-1: nem szakaszhoz tartozó sor, pl. a kártyák közti rés)
    private var map = IntArray(0)
    private var headerPos = IntArray(0)
    private var dirty = true
    private var adapter: RecyclerView.Adapter<*>? = null
    private val observer = object : RecyclerView.AdapterDataObserver() {
        override fun onChanged() { dirty = true }
        override fun onItemRangeInserted(positionStart: Int, itemCount: Int) { dirty = true }
        override fun onItemRangeRemoved(positionStart: Int, itemCount: Int) { dirty = true }
        override fun onItemRangeMoved(fromPosition: Int, toPosition: Int, itemCount: Int) { dirty = true }
    }

    /** Az eltűnő (már pozíció nélküli) sorokhoz az utolsó ismert szakasz. */
    private val lastGroup = java.util.WeakHashMap<View, Int>()

    // szakaszonkénti gyűjtők (rajzolásonként újrahasznosítva)
    private var gTop = FloatArray(0)
    private var gBottom = FloatArray(0)
    private var gFadeBottom = FloatArray(0)
    private var gFadeSum = FloatArray(0)
    private var gFadeCount = IntArray(0)
    private var gLeft = FloatArray(0)
    private var gRight = FloatArray(0)
    private var gHeaderBottom = FloatArray(0)
    private var gHasHeader = BooleanArray(0)
    private var gRows = IntArray(0)
    private var gMaxPos = IntArray(0)
    private var gOutTop = FloatArray(0)
    private var gOutBottom = FloatArray(0)

    override fun getItemOffsets(outRect: Rect, view: View, parent: RecyclerView, state: RecyclerView.State) {
        outRect.set(inset, 0, inset, 0)
    }

    private fun ensureMap(parent: RecyclerView) {
        val ad = parent.adapter
        if (ad !== adapter) {
            try {
                adapter?.unregisterAdapterDataObserver(observer)
            } catch (e: Exception) {
                // nem volt regisztrálva
            }
            adapter = ad
            ad?.registerAdapterDataObserver(observer)
            dirty = true
        }
        if (!dirty) return
        dirty = false
        val count = ad?.itemCount ?: 0
        if (map.size != count) map = IntArray(count)
        map.fill(-1)
        if (headerPos.size != sections.size) headerPos = IntArray(sections.size)
        headerPos.fill(-1)
        val cb = ad as? PreferenceGroup.PreferencePositionCallback ?: return
        sections.forEachIndexed { gi, s ->
            val hp = cb.getPreferenceAdapterPosition(s)
            headerPos[gi] = hp
            if (hp in 0 until count) map[hp] = gi
            val cat = s.category
            for (k in 0 until cat.preferenceCount) {
                val p = cat.getPreference(k)
                if (p === s || !p.isVisible) continue
                val pp = cb.getPreferenceAdapterPosition(p)
                if (pp in 0 until count) map[pp] = gi
            }
        }
    }

    private fun ensureArrays(n: Int) {
        if (gTop.size == n) return
        gTop = FloatArray(n); gBottom = FloatArray(n); gFadeBottom = FloatArray(n)
        gFadeSum = FloatArray(n); gFadeCount = IntArray(n); gLeft = FloatArray(n)
        gRight = FloatArray(n); gHeaderBottom = FloatArray(n); gHasHeader = BooleanArray(n)
        gRows = IntArray(n); gMaxPos = IntArray(n); gOutTop = FloatArray(n); gOutBottom = FloatArray(n)
    }

    override fun onDraw(c: Canvas, parent: RecyclerView, state: RecyclerView.State) {
        val n = sections.size
        if (n == 0) return
        ensureMap(parent)
        ensureArrays(n)
        for (g in 0 until n) {
            gTop[g] = Float.MAX_VALUE; gBottom[g] = -Float.MAX_VALUE; gFadeBottom[g] = -Float.MAX_VALUE
            gFadeSum[g] = 0f; gFadeCount[g] = 0; gLeft[g] = Float.MAX_VALUE; gRight[g] = -Float.MAX_VALUE
            gHeaderBottom[g] = 0f; gHasHeader[g] = false; gRows[g] = 0; gMaxPos[g] = -1
        }

        for (i in 0 until parent.childCount) {
            val v = parent.getChildAt(i)
            val pos = parent.getChildAdapterPosition(v)
            val g: Int
            if (pos != RecyclerView.NO_POSITION) {
                g = if (pos < map.size) map[pos] else -1
                if (g >= 0) lastGroup[v] = g else lastGroup.remove(v)
            } else {
                g = lastGroup[v] ?: -1
            }
            if (g < 0 || g >= n) continue
            // a sor látható helye (eltolás és nagyítás a lenyomásnál / animációnál)
            val sx = v.scaleX
            val sy = v.scaleY
            val vt = v.top + v.translationY + v.pivotY * (1f - sy)
            val vb = vt + v.height * sy
            val vl = v.left + v.translationX + v.pivotX * (1f - sx)
            val vr = vl + v.width * sx
            gTop[g] = min(gTop[g], vt)
            gLeft[g] = min(gLeft[g], vl)
            gRight[g] = max(gRight[g], vr)
            val isHeader = pos != RecyclerView.NO_POSITION && pos == headerPos[g]
            if (animator.fading.contains(v)) {
                gFadeBottom[g] = max(gFadeBottom[g], vb)
                gFadeSum[g] += v.alpha.coerceIn(0f, 1f)
                gFadeCount[g]++
            } else {
                gBottom[g] = max(gBottom[g], vb)
                if (!isHeader) gRows[g]++
            }
            if (isHeader) {
                gHasHeader[g] = true
                gHeaderBottom[g] = vb
            }
            if (pos != RecyclerView.NO_POSITION && pos > gMaxPos[g]) gMaxPos[g] = pos
        }

        // nyers határok (a megjelenő / eltűnő sorok az átlátszóságuk arányában számítanak)
        for (g in 0 until n) {
            if (gTop[g] == Float.MAX_VALUE) {
                gOutTop[g] = Float.NaN
                continue
            }
            val base = if (gBottom[g] > -Float.MAX_VALUE) gBottom[g] else gTop[g]
            var b = base
            if (gFadeCount[g] > 0 && gFadeBottom[g] > base) {
                b = base + (gFadeBottom[g] - base) * (gFadeSum[g] / gFadeCount[g])
            }
            gOutTop[g] = gTop[g]
            gOutBottom[g] = b
        }

        val h = parent.height.toFloat()
        var prevBottom = Float.NaN
        for (g in 0 until n) {
            if (gOutTop[g].isNaN()) continue
            var t = gOutTop[g]
            var b = gOutBottom[g]
            // a kártya sosem lóg rá a szomszédjára (animáció közben sem)
            var next = Float.NaN
            for (g2 in g + 1 until n) {
                if (!gOutTop[g2].isNaN()) {
                    next = gOutTop[g2]
                    break
                }
            }
            if (!next.isNaN()) b = min(b, next - gap)
            if (!prevBottom.isNaN()) t = max(t, prevBottom + gap)
            prevBottom = gOutBottom[g]
            // ha a szakasz a képernyőn túl folytatódik, a kártya is túlnyúlik (nincs kerek sarok középen)
            if (!gHasHeader[g]) t = min(t, -radius * 2f)
            val mp = gMaxPos[g]
            if (mp >= 0 && mp + 1 < map.size && map[mp + 1] == g && gFadeCount[g] == 0) b = max(b, h + radius * 2f)
            if (b - t < 1f) continue
            rect.set(gLeft[g], t, gRight[g], b)
            c.drawRoundRect(rect, radius, radius, fill)
            // elválasztó a címsor alatt, ha a szakasz nyitva van
            if (gHasHeader[g] && (gRows[g] > 0 || gFadeCount[g] > 0)) {
                val frac = if (gRows[g] > 0) 1f else gFadeSum[g] / gFadeCount[g]
                val y = gHeaderBottom[g]
                if (y < b - 1f && frac > 0.01f) {
                    val baseAlpha = sep.alpha
                    sep.alpha = (baseAlpha * frac).toInt().coerceIn(0, 255)
                    c.drawLine(rect.left + sepInset, y, rect.right - sepInset, y, sep)
                    sep.alpha = baseAlpha
                }
            }
            val half = rim.strokeWidth / 2f
            rect.inset(half, half)
            rimMatrix.setScale(1f, max(1f, rect.height()))
            rimMatrix.postTranslate(0f, rect.top)
            rimShader.setLocalMatrix(rimMatrix)
            c.drawRoundRect(rect, radius - half, radius - half, rim)
        }
    }
}
