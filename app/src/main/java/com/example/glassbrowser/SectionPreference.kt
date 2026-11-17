package com.example.glassbrowser

import android.animation.Animator
import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.annotation.SuppressLint
import android.content.Context
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.view.MotionEvent
import android.view.View
import android.view.animation.OvershootInterpolator
import android.view.animation.PathInterpolator
import androidx.preference.Preference
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceViewHolder

/**
 * The clickable title row of a collapsible settings section. Tapping it opens / closes the
 * section; the summary lists what is inside, so the closed list stays short and readable.
 */
class SectionPreference(ctx: Context, private val group: PreferenceCategory) : Preference(ctx) {
    var expanded = false
        private set
    /** A nyíl utoljára beállított célszöge (ebből tudjuk, hogy kell-e forgatni). */
    private var chevronTarget = 0f
    /** Items hidden by the app itself (not by collapsing) stay hidden when the section opens. */
    private val hiddenByApp = HashSet<Preference>()
    var onToggle: ((SectionPreference) -> Unit)? = null
    private var pressAnim: Animator? = null

    /** A szakasz kategóriája (a kártya kirajzolásához). */
    val category: PreferenceCategory get() = group

    init {
        layoutResource = R.layout.pref_section
        isPersistent = false
        isIconSpaceReserved = false
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        super.onBindViewHolder(holder)
        holder.isDividerAllowedAbove = false
        holder.isDividerAllowedBelow = false
        val row = holder.itemView
        val d = context.resources.displayMetrics.density

        // lekerekített lenyomás-visszajelzés: a kártya sarkait követi (zárva mind a négy kerek)
        val r = 22f * d
        val radii = if (expanded) floatArrayOf(r, r, r, r, 0f, 0f, 0f, 0f) else FloatArray(8) { r }
        val mask = (row.background as? RippleDrawable)?.findDrawableByLayerId(android.R.id.mask) as? GradientDrawable
        if (mask != null) {
            mask.cornerRadii = radii
        } else {
            val night = (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
                Configuration.UI_MODE_NIGHT_YES
            val m = GradientDrawable().apply {
                setColor(0xFFFFFFFF.toInt())
                cornerRadii = radii
            }
            row.background = RippleDrawable(
                ColorStateList.valueOf(if (night) 0x24FFFFFF else 0x16000000), null, m
            )
        }

        // lenyomásra a címsor finoman összenyomódik, elengedve rugózva visszaugrik
        row.setOnTouchListener { v, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> press(v, true)
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> press(v, false)
            }
            false
        }
        if (pressAnim?.isRunning != true && !row.isPressed) {
            row.scaleX = 1f
            row.scaleY = 1f
        }

        val chev = holder.findViewById(R.id.sectionChevron) ?: return
        val target = if (expanded) 180f else 0f
        if (chev.tag !== this) {
            // új (vagy újrahasznosított) nézet: azonnal a helyes állás, animáció nélkül
            chev.animate().cancel()
            chev.rotation = target
            chev.tag = this
        } else if (chevronTarget != target) {
            chev.animate().rotation(target).setStartDelay(0L).setDuration(420L)
                .setInterpolator(PathInterpolator(0.3f, 1.3f, 0.45f, 1f)).start()
        }
        chevronTarget = target
    }

    private fun press(v: View, down: Boolean) {
        pressAnim?.cancel()
        val s = if (down) 0.975f else 1f
        val a = ObjectAnimator.ofPropertyValuesHolder(
            v,
            PropertyValuesHolder.ofFloat(View.SCALE_X, s),
            PropertyValuesHolder.ofFloat(View.SCALE_Y, s)
        )
        if (down) {
            a.duration = 140L
            a.interpolator = PathInterpolator(0.3f, 0f, 0.2f, 1f)
        } else {
            a.duration = 460L
            a.interpolator = OvershootInterpolator(2.6f)
        }
        // a mögötte lévő üvegkártya a sor méretét követi: a listát is újra kell rajzolni
        a.addUpdateListener { (v.parent as? View)?.invalidate() }
        pressAnim = a
        a.start()
    }

    override fun onClick() {
        setExpanded(!expanded)
        onToggle?.invoke(this)
    }

    fun setExpanded(open: Boolean) {
        if (open == expanded && open) return
        expanded = open
        for (i in 0 until group.preferenceCount) {
            val p = group.getPreference(i)
            if (p === this) continue
            if (open) {
                p.isVisible = p !in hiddenByApp
            } else {
                if (!p.isVisible) hiddenByApp.add(p) else hiddenByApp.remove(p)
                p.isVisible = false
            }
        }
        notifyChanged()
    }
}
