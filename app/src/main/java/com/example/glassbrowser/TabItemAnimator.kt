package com.example.glassbrowser

import android.animation.TimeInterpolator
import androidx.recyclerview.widget.DefaultItemAnimator
import androidx.recyclerview.widget.RecyclerView
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Csillapított rugó görbe (0..1): enyhén túllendül, majd lágyan beáll, mint a Safari kártyái.
 * [damping] a csillapítás (1 alatt van túllendülés), [stiffness] a rugó "sebessége" (a t = 1 végére
 * gyakorlatilag beáll; a maradékot az utolsó képkockán levágjuk).
 */
class SpringInterpolator(
    private val damping: Float = 0.74f,
    private val stiffness: Float = 9.5f
) : TimeInterpolator {
    private val wd = stiffness * sqrt(1f - damping * damping)

    override fun getInterpolation(input: Float): Float {
        if (input <= 0f) return 0f
        if (input >= 1f) return 1f
        val decay = exp(-damping * stiffness * input)
        return 1f - decay * (cos(wd * input) + (damping * stiffness / wd) * sin(wd * input))
    }
}

/**
 * A lapváltó rácsának animációja. Bezáráskor a kártyát már a bezárási effekt (applyCloseFx) eltünteti,
 * ezért az eltávolítás itt nem halványít újra (különben egy pillanatra visszavillanna): azonnal kész.
 * A megmaradt kártyák rugós mozgással (~450 ms) csúsznak a helyükre, és azonnal indulnak,
 * nem várják meg az eltávolítás végét.
 */
class TabItemAnimator : DefaultItemAnimator() {
    private val spring = SpringInterpolator()

    init {
        moveDuration = 450
        removeDuration = 160
        addDuration = 200
    }

    override fun animateRemove(holder: RecyclerView.ViewHolder): Boolean {
        val v = holder.itemView
        // a bezárási effekt már láthatatlanná tette: nincs mit animálni
        if (v.alpha < 0.05f) {
            dispatchRemoveFinished(holder)
            return false
        }
        return super.animateRemove(holder)
    }

    override fun animateMove(
        holder: RecyclerView.ViewHolder, fromX: Int, fromY: Int, toX: Int, toY: Int
    ): Boolean {
        val pending = super.animateMove(holder, fromX, fromY, toX, toY)
        // (a DefaultItemAnimator az alap görbét állítja vissza; a mozgás indításakor már ez marad rajta)
        // a mozgás idejére hardverréteg (nincs újrarajzolás csúszás közben)
        if (pending) holder.itemView.animate().setInterpolator(spring).withLayer()
        return pending
    }
}
