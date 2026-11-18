package com.shreenivas.tvhome.homescreen

import android.animation.ValueAnimator
import android.content.Context
import android.view.View
import android.widget.FrameLayout
import com.shreenivas.tvhome.LauncherUtils
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

            val topPad = LauncherUtils.dpF(context, 10f)
            val targetX = col * (tileW + gap).toFloat()
            val targetY = topPad + row * (tileH + gap).toFloat()

            if (animate) {
                child.animate()
                    .x(targetX).y(targetY)
                    .setDuration(420L)
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

        if (animate && sizeChanged) {
            ValueAnimator.ofFloat(0f, 1f).apply {
                duration = 420L
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
                    requestLayout()
                }
                start()
            }
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        if (childCount == 0) return
        val rows = ceil(childCount.toDouble() / columns).toInt()
        val extraPad = LauncherUtils.dp(context, 22)
        val totalH = rows * tileH + maxOf(0, rows - 1) * gap + extraPad
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), totalH)
    }

    override fun focusSearch(focused: View, direction: Int): View? {
        val index = indexOfChild(focused)
        if (index < 0) return super.focusSearch(focused, direction)

        val row = index / columns
        val col = index % columns
        val totalRows = ceil(childCount.toDouble() / columns).toInt()

        return when (direction) {
            View.FOCUS_RIGHT -> {
                if (col < columns - 1 && index + 1 < childCount) {
                    getChildAt(index + 1)
                } else {
                    focused // Never escape to TopNavBar on Right arrow
                }
            }
            View.FOCUS_LEFT -> {
                if (col > 0) {
                    getChildAt(index - 1)
                } else {
                    focused // Never escape to TopNavBar on Left arrow
                }
            }
            View.FOCUS_DOWN -> {
                if (row < totalRows - 1) {
                    val nextIndex = minOf(index + columns, childCount - 1)
                    getChildAt(nextIndex)
                } else {
                    focused // Lock on bottom row
                }
            }
            View.FOCUS_UP -> {
                if (row > 0) {
                    getChildAt(index - columns)
                } else {
                    super.focusSearch(focused, direction) // Only UP on top row exits to Favorites / TopNavBar
                }
            }
            else -> focused
        }
    }
}
