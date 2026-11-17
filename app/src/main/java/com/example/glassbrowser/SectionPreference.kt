package com.example.glassbrowser

import android.content.Context
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
    private var chevronAngle = 0f
    /** Items hidden by the app itself (not by collapsing) stay hidden when the section opens. */
    private val hiddenByApp = HashSet<Preference>()
    var onToggle: ((SectionPreference) -> Unit)? = null

    init {
        layoutResource = R.layout.pref_section
        isPersistent = false
        isIconSpaceReserved = false
    }

    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        super.onBindViewHolder(holder)
        holder.isDividerAllowedAbove = false
        holder.isDividerAllowedBelow = false
        val chev = holder.findViewById(R.id.sectionChevron) ?: return
        val target = if (expanded) 180f else 0f
        if (chev.rotation != target) {
            chev.rotation = chevronAngle
            chev.animate().rotation(target).setDuration(260L)
                .setInterpolator(PathInterpolator(0.2f, 0.9f, 0.25f, 1f)).start()
        }
        chevronAngle = target
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
