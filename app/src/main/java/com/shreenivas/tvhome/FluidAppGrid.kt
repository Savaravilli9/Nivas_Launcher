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
        tileW = newTileW
        tileH = newTileH
        gap = newGap

        for (i in 0 until childCount) {
            val child = getChildAt(i)
            val row = i / columns
            val col = i % columns
            
            val targetX = col * (tileW + gap).toFloat()
            val targetY = row * (tileH + gap).toFloat()

            if (animate) {
                // యానిమేషన్ స్మూత్‌గా ఉండటానికి delay లేకుండా start చేయడం
                child.animate()
                    .x(targetX).y(targetY)
                    .setDuration(450L)
                    .setInterpolator(LauncherUtils.FLUID_SPRING)
                    .withLayer() // GPU ఆక్సెలరేషన్ వాడటం
                    .start()

                if (child.layoutParams.width != tileW || child.layoutParams.height != tileH) {
                    val startW = child.layoutParams.width
                    val startH = child.layoutParams.height
                    ValueAnimator.ofFloat(0f, 1f).apply {
                        duration = 450L
                        interpolator = LauncherUtils.FLUID_SPRING
                        addUpdateListener { anim ->
                            val f = anim.animatedValue as Float
                            child.layoutParams.width = (startW + (tileW - startW) * f).toInt()
                            child.layoutParams.height = (startH + (tileH - startH) * f).toInt()
                            child.requestLayout()
                        }
                        start()
                    }
                }
            } else {
                child.x = targetX
                child.y = targetY
                child.layoutParams.width = tileW
                child.layoutParams.height = tileH
                child.requestLayout()
            }
        }
        requestLayout()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        if (childCount == 0) return
        val rows = ceil(childCount.toDouble() / columns).toInt()
        val totalH = rows * tileH + maxOf(0, rows - 1) * gap
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), totalH)
    }
}
