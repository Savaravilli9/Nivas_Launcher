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

    private fun ensureCameraDistance(view: View) {
        val d = view.resources.displayMetrics.density
        view.cameraDistance = 14000f * d
    }

    fun resetTransform(view: View) {
        view.animate().cancel()
        view.alpha = 1f
        view.scaleX = 1f
        view.scaleY = 1f
        view.rotationX = 0f
        view.rotationY = 0f
        view.translationX = 0f
        view.translationY = 0f
    }

    fun animateFocus(view: View, focused: Boolean, style: String, scale: Float = 1.08f, elevationDp: Float = 8f) {
        view.animate().cancel()
        ensureCameraDistance(view)
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
                view.pivotX = view.width / 2f
                view.pivotY = view.height / 2f
                view.animate()
                    .scaleX(if (focused) scale * 1.04f else 1f)
                    .scaleY(if (focused) scale * 1.04f else 1f)
                    .rotationX(0f).rotationY(0f)
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
                    .rotationX(0f)
                    .translationZ(tz)
                    .setDuration(dur).setInterpolator(interp).start()
            }
            else -> {
                view.pivotX = view.width / 2f
                view.pivotY = view.height / 2f
                view.animate()
                    .scaleX(if (focused) scale else 1f)
                    .scaleY(if (focused) scale else 1f)
                    .rotationX(0f).rotationY(0f)
                    .translationZ(tz)
                    .setDuration(dur).setInterpolator(interp).start()
            }
        }
    }
        fun playDemoPreview(panel: View, style: String) {
        resetTransform(panel)
        ensureCameraDistance(panel)
        val w = maxOf(panel.width, LauncherUtils.dp(panel.context, 280)).toFloat()
        val h = maxOf(panel.height, LauncherUtils.dp(panel.context, 420)).toFloat()
        val interp = getInterpolator(style)

        when (style) {
            STYLE_FLIP -> {
                panel.pivotX = w / 2f; panel.pivotY = h / 2f
                panel.rotationY = 24f; panel.scaleX = 0.94f; panel.scaleY = 0.94f
                panel.animate().rotationY(0f).scaleX(1f).scaleY(1f)
                    .setDuration(420L).setInterpolator(interp).start()
            }
            STYLE_QUANTUM -> {
                panel.pivotX = w / 2f; panel.pivotY = h / 2f
                panel.scaleX = 1.08f; panel.scaleY = 1.08f; panel.alpha = 0.75f
                panel.animate().scaleX(1f).scaleY(1f).alpha(1f)
                    .setDuration(360L).setInterpolator(interp).start()
            }
            STYLE_SWING -> {
                panel.pivotX = w; panel.pivotY = h / 2f
                panel.rotationY = -28f
                panel.animate().rotationY(0f)
                    .setDuration(420L).setInterpolator(interp).start()
            }
            STYLE_SILK -> {
                panel.translationX = LauncherUtils.dpF(panel.context, 36f); panel.alpha = 0.7f
                panel.animate().translationX(0f).alpha(1f)
                    .setDuration(320L).setInterpolator(SILK_EASE).start()
            }
            else -> {
                panel.translationX = LauncherUtils.dpF(panel.context, 48f)
                panel.animate().translationX(0f)
                    .setDuration(420L).setInterpolator(OVERSHOOT).start()
            }
        }
    }

    fun animatePanelEnter(panel: View, stageWidget: View?, widthPx: Int, style: String, onDone: () -> Unit) {
        resetTransform(panel)
        ensureCameraDistance(panel)
        stageWidget?.let {
            it.animate().cancel()
            ensureCameraDistance(it)
            it.pivotX = it.width / 2f
            it.pivotY = it.height / 2f
            it.animate()
                .alpha(0f)
                .scaleX(if (style == STYLE_QUANTUM) 0.86f else 0.94f)
                .scaleY(if (style == STYLE_QUANTUM) 0.86f else 0.94f)
                .rotationY(if (style == STYLE_FLIP) -20f else 0f)
                .setDuration(240L)
                .withEndAction {
                    it.visibility = View.GONE
                    resetTransform(it)
                }
                .start()
        }

        val interp = getInterpolator(style)
        val safeW = maxOf(widthPx, LauncherUtils.dp(panel.context, 250)).toFloat()
        val safeH = maxOf(panel.height, LauncherUtils.dp(panel.context, 420)).toFloat()

        when (style) {
            STYLE_FLIP -> {
                panel.pivotX = safeW / 2f; panel.pivotY = safeH / 2f
                panel.rotationY = 38f; panel.alpha = 0f; panel.scaleX = 0.92f; panel.scaleY = 0.92f
                panel.animate().rotationY(0f).alpha(1f).scaleX(1f).scaleY(1f)
                    .setDuration(400L).setInterpolator(interp).withEndAction(onDone).start()
            }
            STYLE_QUANTUM -> {
                panel.pivotX = safeW / 2f; panel.pivotY = safeH / 2f
                panel.scaleX = 1.10f; panel.scaleY = 1.10f; panel.alpha = 0f
                panel.animate().scaleX(1f).scaleY(1f).alpha(1f)
                    .setDuration(350L).setInterpolator(interp).withEndAction(onDone).start()
            }
            STYLE_SWING -> {
                panel.pivotX = safeW; panel.pivotY = safeH / 2f
                panel.rotationY = -42f; panel.alpha = 0f
                panel.animate().rotationY(0f).alpha(1f)
                    .setDuration(400L).setInterpolator(interp).withEndAction(onDone).start()
            }
            else -> {
                panel.translationX = safeW + 30f; panel.alpha = 0f
                panel.animate().translationX(0f).alpha(1f)
                    .setDuration(380L).setInterpolator(interp).withEndAction(onDone).start()
            }
        }
    }

    fun animatePanelExit(panel: View, stageWidget: View?, widthPx: Int, style: String, onDone: () -> Unit) {
        panel.animate().cancel()
        ensureCameraDistance(panel)
        stageWidget?.let {
            it.animate().cancel()
            resetTransform(it)
            ensureCameraDistance(it)
            it.visibility = View.VISIBLE
            it.alpha = 0f
            it.scaleX = 0.92f; it.scaleY = 0.92f
            it.rotationY = if (style == STYLE_FLIP) 20f else 0f
            it.animate().alpha(1f).scaleX(1f).scaleY(1f).rotationY(0f)
                .setDuration(360L).setInterpolator(getInterpolator(style)).start()
        }

        val safeW = maxOf(widthPx, LauncherUtils.dp(panel.context, 250)).toFloat()
        val finish = { resetTransform(panel); onDone() }

        when (style) {
            STYLE_FLIP -> {
                panel.pivotX = safeW / 2f
                panel.animate().rotationY(-38f).alpha(0f).scaleX(0.92f).scaleY(0.92f)
                    .setDuration(320L).withEndAction(finish).start()
            }
            STYLE_QUANTUM -> {
                panel.pivotX = safeW / 2f
                panel.animate().scaleX(0.88f).scaleY(0.88f).alpha(0f)
                    .setDuration(280L).withEndAction(finish).start()
            }
            STYLE_SWING -> {
                panel.pivotX = safeW
                panel.animate().rotationY(-42f).alpha(0f)
                    .setDuration(320L).withEndAction(finish).start()
            }
            else -> {
                panel.animate().translationX(safeW + 30f).alpha(0f)
                    .setDuration(340L).setInterpolator(getInterpolator(style)).withEndAction(finish).start()
            }
        }
    }
}
