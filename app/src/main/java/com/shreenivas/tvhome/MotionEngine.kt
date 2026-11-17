package com.shreenivas.tvhome

import android.animation.TimeInterpolator
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator

object MotionEngine {
    const val STYLE_ELASTIC = "elastic"
    const val STYLE_FLIP = "flip"
    const val STYLE_QUANTUM = "quantum"
    const val STYLE_SILK = "silk"
    const val STYLE_SWING = "swing"

    val NAMES = listOf("Elastic", "3D Flip", "Quantum", "Silk", "Swing")
    val KEYS = listOf(STYLE_ELASTIC, STYLE_FLIP, STYLE_QUANTUM, STYLE_SILK, STYLE_SWING)

    private val OVERSHOOT = OvershootInterpolator(1.45f)
    private val QUANTUM_POP = OvershootInterpolator(1.85f)
    private val SILK_EASE = DecelerateInterpolator(2.0f)

    fun getInterpolator(style: String): TimeInterpolator = when (style) {
        STYLE_QUANTUM -> QUANTUM_POP
        STYLE_SILK -> SILK_EASE
        else -> OVERSHOOT
    }

    fun getDuration(style: String, focused: Boolean): Long = when (style) {
        STYLE_SILK -> if (focused) 240L else 170L
        STYLE_FLIP -> if (focused) 260L else 180L
        STYLE_QUANTUM -> if (focused) 200L else 140L
        else -> if (focused) 220L else 150L
    }

    fun animateFocus(view: View, focused: Boolean, style: String, scale: Float = 1.08f, elevationDp: Float = 8f) {
        view.animate().cancel()
        val interp = getInterpolator(style)
        val dur = getDuration(style, focused)
        val tz = if (focused) LauncherUtils.dpF(view.context, elevationDp) else 0f

        when (style) {
            STYLE_FLIP -> {
                view.pivotX = view.width / 2f
                view.pivotY = view.height / 2f
                view.animate()
                    .scaleX(if (focused) scale else 1f)
                    .scaleY(if (focused) scale else 1f)
                    .rotationY(if (focused) 7f else 0f)
                    .rotationX(if (focused) -4f else 0f)
                    .translationZ(tz)
                    .setDuration(dur).setInterpolator(interp).start()
            }
            STYLE_QUANTUM -> {
                view.animate()
                    .scaleX(if (focused) scale * 1.04f else 1f)
                    .scaleY(if (focused) scale * 1.04f else 1f)
                    .translationZ(tz)
                    .setDuration(dur).setInterpolator(interp).start()
            }
            STYLE_SWING -> {
                view.pivotX = 0f
                view.pivotY = view.height / 2f
                view.animate()
                    .scaleX(if (focused) scale else 1f)
                    .scaleY(if (focused) scale else 1f)
                    .rotationY(if (focused) -6f else 0f)
                    .translationZ(tz)
                    .setDuration(dur).setInterpolator(interp).start()
            }
            else -> {
                view.animate()
                    .scaleX(if (focused) scale else 1f)
                    .scaleY(if (focused) scale else 1f)
                    .rotationX(0f).rotationY(0f)
                    .translationZ(tz)
                    .setDuration(dur).setInterpolator(interp).start()
            }
        }
    }

    fun animatePanelEnter(panel: View, stageWidget: View?, widthPx: Int, style: String, onDone: () -> Unit) {
        panel.animate().cancel()
        stageWidget?.animate()?.cancel()
        val interp = getInterpolator(style)

        stageWidget?.animate()
            ?.alpha(0f)
            ?.scaleX(if (style == STYLE_QUANTUM) 0.84f else 0.94f)
            ?.scaleY(if (style == STYLE_QUANTUM) 0.84f else 0.94f)
            ?.rotationY(if (style == STYLE_FLIP) -25f else 0f)
            ?.setDuration(260L)
            ?.withEndAction { stageWidget.visibility = View.GONE }
            ?.start()

        when (style) {
            STYLE_FLIP -> {
                panel.pivotX = widthPx / 2f
                panel.translationX = 0f
                panel.rotationY = 45f
                panel.alpha = 0f
                panel.scaleX = 0.92f; panel.scaleY = 0.92f
                panel.animate().rotationY(0f).alpha(1f).scaleX(1f).scaleY(1f)
                    .setDuration(420L).setInterpolator(interp).withEndAction(onDone).start()
            }
            STYLE_QUANTUM -> {
                panel.translationX = 0f; panel.rotationY = 0f
                panel.scaleX = 1.12f; panel.scaleY = 1.12f; panel.alpha = 0f
                panel.animate().scaleX(1f).scaleY(1f).alpha(1f)
                    .setDuration(360L).setInterpolator(interp).withEndAction(onDone).start()
            }
            STYLE_SWING -> {
                panel.pivotX = widthPx.toFloat()
                panel.translationX = 0f; panel.rotationY = -55f; panel.alpha = 0f
                panel.animate().rotationY(0f).alpha(1f)
                    .setDuration(420L).setInterpolator(interp).withEndAction(onDone).start()
            }
            else -> {
                panel.rotationY = 0f; panel.scaleX = 1f; panel.scaleY = 1f
                panel.translationX = (widthPx + 30).toFloat(); panel.alpha = 0f
                panel.animate().translationX(0f).alpha(1f)
                    .setDuration(400L).setInterpolator(interp).withEndAction(onDone).start()
            }
        }
    }

    fun animatePanelExit(panel: View, stageWidget: View?, widthPx: Int, style: String, onDone: () -> Unit) {
        panel.animate().cancel()
        stageWidget?.animate()?.cancel()
        val interp = getInterpolator(style)

        stageWidget?.apply {
            visibility = View.VISIBLE
            alpha = 0f
            scaleX = 0.92f; scaleY = 0.92f
            rotationY = if (style == STYLE_FLIP) 25f else 0f
            animate().alpha(1f).scaleX(1f).scaleY(1f).rotationY(0f)
                .setDuration(380L).setInterpolator(interp).start()
        }

        when (style) {
            STYLE_FLIP -> {
                panel.animate().rotationY(-45f).alpha(0f).scaleX(0.92f).scaleY(0.92f)
                    .setDuration(340L).withEndAction(onDone).start()
            }
            STYLE_QUANTUM -> {
                panel.animate().scaleX(0.88f).scaleY(0.88f).alpha(0f)
                    .setDuration(300L).withEndAction(onDone).start()
            }
            STYLE_SWING -> {
                panel.pivotX = widthPx.toFloat()
                panel.animate().rotationY(-55f).alpha(0f)
                    .setDuration(340L).withEndAction(onDone).start()
            }
            else -> {
                panel.animate().translationX((widthPx + 30).toFloat()).alpha(0f)
                    .setDuration(360L).setInterpolator(interp).withEndAction(onDone).start()
            }
        }
    }
}
