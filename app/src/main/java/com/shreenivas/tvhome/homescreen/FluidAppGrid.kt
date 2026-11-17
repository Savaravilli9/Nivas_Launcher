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
        val totalH = rows * tileH + maxOf(0, rows - 1) * gap
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), totalH)
    }

    // ==========================================
    // THE FIX: D-PAD SMART FOCUS ROUTER
    // ==========================================
    override fun focusSearch(focused: View, direction: Int): View? {
        val index = indexOfChild(focused)
        if (index < 0) return super.focusSearch(focused, direction)

        val row = index / columns
        val col = index % columns
        val totalRows = ceil(childCount.toDouble() / columns).toInt()

        return when (direction) {
            View.FOCUS_RIGHT -> {
                // కుడివైపుకి నొక్కినప్పుడు పక్కన యాప్ ఉంటే దానికి వెళ్తుంది
                if (col < columns - 1 && index + 1 < childCount) getChildAt(index + 1)
                else super.focusSearch(focused, direction) // చివర్లో ఉంటే సిస్టమ్‌కి వదిలేస్తుంది (ఉదా: సైడ్ ప్యానెల్‌కి వెళ్లడానికి)
            }
            View.FOCUS_LEFT -> {
                // ఎడమవైపుకి నొక్కినప్పుడు వెనక యాప్ కి వెళ్తుంది
                if (col > 0) getChildAt(index - 1)
                else super.focusSearch(focused, direction)
            }
            View.FOCUS_DOWN -> {
                // కిందకి నొక్కినప్పుడు కచ్చితంగా కింది యాప్ కే వెళ్తుంది.
                if (row < totalRows - 1) {
                    val nextIndex = minOf(index + columns, childCount - 1)
                    getChildAt(nextIndex)
                } else {
                    focused // చిట్టచివరి వరుసలో కిందకి నొక్కితే ఎక్కడికీ జంప్ అవ్వకుండా అక్కడే లాక్ అవుతుంది
                }
            }
            View.FOCUS_UP -> {
                // పైకి నొక్కినప్పుడు పై యాప్ కి వెళ్తుంది
                if (row > 0) {
                    getChildAt(index - columns)
                } else {
                    super.focusSearch(focused, direction) // మొదటి వరుసలో పైకి నొక్కితే ఫేవరెట్స్ లేదా టాప్ బార్ కి వెళ్తుంది
                }
            }
            else -> super.focusSearch(focused, direction)
        }
    }
}
