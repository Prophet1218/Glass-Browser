package com.example.glassbrowser

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

/** A lapváltó rácsának kártyái: fent a lap képe (bezáró gombbal), alatta az ikon és a cím. */
class TabAdapter(
    private val tabsOf: () -> List<Tab>,
    private val activeTab: () -> Tab?,
    /** Az a lap, amelynek kártyáját épp a "repülő" kép helyettesíti (ilyenkor rejtve van). */
    private val hiddenTab: () -> Tab?,
    /** A kijelölt lap fehér keretének átlátszósága (megnyitáskor fokozatosan jelenik meg). */
    private val ringAlpha: () -> Float,
    /** A lapnevek átlátszósága (0..1): a lapváltó nyitásakor fokozatosan úsznak be. */
    private val titleAlpha: () -> Float,
    private val onSelect: (Tab) -> Unit,
    private val onClose: (Tab) -> Unit
) : RecyclerView.Adapter<TabAdapter.VH>() {

    class VH(view: View) : RecyclerView.ViewHolder(view) {
        val card: View = view.findViewById(R.id.tabCard)
        val thumb: ImageView = view.findViewById(R.id.tabThumb)
        val active: View = view.findViewById(R.id.tabActive)
        val icon: ImageView = view.findViewById(R.id.tabIcon)
        val title: TextView = view.findViewById(R.id.tabTitle)
        val close: ImageButton = view.findViewById(R.id.tabClose)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_tab, parent, false)
        val holder = VH(v)
        holder.card.clipToOutline = true
        return holder
    }

    override fun getItemCount(): Int = tabsOf().size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val tab = tabsOf()[position]
        val start = Urls.isStart(tab.url)

        holder.title.text = tab.title.ifBlank { if (start) L.t("Kezdőlap") else Urls.host(tab.url) }
        // (újrahasznosított sornál a beúszó animáció állapota ne maradjon rajta)
        // (egy közben érkező frissítés se ugrassa a beúszó animációt)
        val ta = titleAlpha()
        val shift = (1f - ta) * 8f * holder.title.resources.displayMetrics.density
        holder.title.alpha = ta
        holder.title.translationY = shift
        holder.icon.alpha = ta
        holder.icon.translationY = shift
        holder.thumb.setImageBitmap(tab.thumbnail)
        val icon = tab.favicon
        if (icon != null && !start) {
            holder.icon.setImageBitmap(icon)
            holder.icon.visibility = View.VISIBLE
        } else {
            holder.icon.setImageDrawable(null)
            holder.icon.visibility = View.GONE
        }
        holder.active.visibility = if (tab === activeTab()) View.VISIBLE else View.GONE
        holder.active.alpha = ringAlpha()
        // az aktív lap bezáró gombja a kerettel együtt jelenik meg
        val isActive = tab === activeTab()
        holder.close.alpha = if (isActive) ringAlpha() else 1f
        holder.card.alpha = if (tab === hiddenTab()) 0f else 1f

        holder.card.setOnClickListener {
            val pos = holder.bindingAdapterPosition
            if (pos != RecyclerView.NO_POSITION) onSelect(tabsOf()[pos])
        }
        holder.close.setOnClickListener {
            val pos = holder.bindingAdapterPosition
            if (pos != RecyclerView.NO_POSITION) onClose(tabsOf()[pos])
        }
    }
}
