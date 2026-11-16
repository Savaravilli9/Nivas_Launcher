package com.shreenivas.tvhome

import android.animation.ValueAnimator
import android.content.Context
import android.view.View
import android.widget.FrameLayout
import kotlin.math.ceil

class FluidAppGrid(context: Context) : FrameLayout(context) {
    var columns = 6
    var gap = 0
    var tileW = 0
    var tileH = 0

    init {
        clipChildren = false
        clipToPadding = false
    }

    fun updateGridState(newCols: Int, newTileW: Int, newTileH: Int, newGap: Int, animate: Boolean = true) {
        columns = newCols
        gap = newGap
        
        val sizeChanged = (newTileW != tileW || newTileH != tileH) && childCount > 0
        val oldTileW = tileW
        val oldTileH = tileH
        
        tileW = newTileW
        tileH = newTileH

        for (i in 0 until childCount) {
            val child = getChildAt(i)
            val row = i / columns
            val col = i % columns
            
            val targetX = col * (tileW + gap).toFloat()
            val targetY = row * (tileH + gap).toFloat()

            if (animate) {
                // ఎక్స్, వై కదలికలకు GPU ఆక్సెలరేషన్ (Zero Lag)
                child.animate()
                    .x(targetX).y(targetY)
                    .setDuration(450L)
                    .setInterpolator(LauncherUtils.FLUID_SPRING)
                    .start()
            } else {
                child.x = targetX
                child.y = targetY
                if (sizeChanged) {
                    child.layoutParams.width = tileW
                    child.layoutParams.height = tileH
                }
            }
        }
        
        if (!animate && sizeChanged) requestLayout()

        // FIX: 151 యానిమేటర్స్‌కి బదులు ఒకే ఒక్క మాస్టర్ యానిమేటర్ వాడటం వల్ల స్టక్ అయ్యే సమస్య తీరింది!
        if (animate && sizeChanged) {
            ValueAnimator.ofFloat(0f, 1f).apply {
                duration = 450L
                interpolator = LauncherUtils.FLUID_SPRING
                addUpdateListener { anim ->
                    val f = anim.animatedValue as Float
                    val curW = (oldTileW + (tileW - oldTileW) * f).toInt()
                    val curH = (oldTileH + (tileH - oldTileH) * f).toInt()
                    for (i in 0 until childCount) {
                        val c = getChildAt(i)
                        c.layoutParams.width = curW
                        c.layoutParams.height = curH
                    }
                    requestLayout() // సెకనుకు ఒక్కసారే లేఅవుట్ పాస్, 150 సార్లు కాదు!
                }
                start()
            }
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        if (childCount == 0) return
        val rows = ceil(childCount.toDouble() / columns).toInt()
        val totalH = rows * tileH + maxOf(0, rows - 1) * gap
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), totalH)
    }
}
