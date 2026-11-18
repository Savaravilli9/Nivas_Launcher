package com.shreenivas.tvhome

import android.animation.TimeInterpolator
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator

object MotionEngine {
    const val STYLE_ELASTIC = "elastic"
    const val STYLE_JELLY = "jelly"
    const val STYLE_FLIP = "flip"
    const val STYLE_BOOK = "book"
    const val STYLE_FLOAT = "float"
    const val STYLE_SILK = "silk"
    const val STYLE_SWING = "swing"

    val NAMES = listOf("Elastic", "Jelly", "3D Flip", "Book Open", "Levitate", "Silk Glide", "Swing")
    val KEYS = listOf(STYLE_ELASTIC, STYLE_JELLY, STYLE_FLIP, STYLE_BOOK, STYLE_FLOAT, STYLE_SILK, STYLE_SWING)

    private val OVERSHOOT = OvershootInterpolator(1.55f)
    private val JELLY_POP = OvershootInterpolator(2.1f)
    private val SILK_EASE = DecelerateInterpolator(2.2f)

    fun getInterpolator(style: String): TimeInterpolator = when (style) {
        STYLE_JELLY -> JELLY_POP
        STYLE_SILK, STYLE_FLOAT -> SILK_EASE
        else -> OVERSHOOT
    }

    fun getDuration(style: String, focused: Boolean): Long = when (style) {
        STYLE_SILK -> if (focused) 280L else 190L
        STYLE_FLIP, STYLE_BOOK -> if (focused) 320L else 210L
        STYLE_JELLY -> if (focused) 340L else 200L
        else -> if (focused) 260L else 170L
    }

    private fun ensureCameraDistance(view: View) {
        val d = view.resources.displayMetrics.density
        view.cameraDistance = 16000f * d
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

    fun animateFocus(
        view: View,
        focused: Boolean,
        style: String,
        scale: Float = 1.08f,
        elevationDp: Float = 8f,
        dirX: Float = 0f,
        dirY: Float = 0f
    ) {
        view.animate().cancel()
        ensureCameraDistance(view)
        val interp = getInterpolator(style)
        val dur = getDuration(style, focused)
        val tz = if (focused) LauncherUtils.dpF(view.context, elevationDp) else 0f
        val d = view.resources.displayMetrics.density

        if (focused && (dirX != 0f || dirY != 0f)) {
            view.translationX = -dirX * 10f * d
            view.translationY = -dirY * 10f * d
        }

        when (style) {
            STYLE_JELLY -> {
                view.pivotX = view.width / 2f; view.pivotY = view.height / 2f
                if (focused) {
                    view.scaleX = 0.94f; view.scaleY = 1.06f
                }
                view.animate()
                    .scaleX(if (focused) scale else 1f)
                    .scaleY(if (focused) scale else 1f)
                    .translationX(0f).translationY(0f).rotationX(0f).rotationY(0f)
                    .translationZ(tz)
                    .setDuration(dur).setInterpolator(JELLY_POP).start()
            }
            STYLE_FLIP -> {
                view.pivotX = view.width / 2f; view.pivotY = view.height / 2f
                view.animate()
                    .scaleX(if (focused) scale else 1f)
                    .scaleY(if (focused) scale else 1f)
                    .rotationY(if (focused) 8f else 0f)
                    .rotationX(if (focused) -4f else 0f)
                    .translationX(0f).translationY(0f)
                    .translationZ(tz)
                    .setDuration(dur).setInterpolator(interp).start()
            }
            STYLE_BOOK -> {
                view.pivotX = 0f; view.pivotY = view.height / 2f
                view.animate()
                    .scaleX(if (focused) scale else 1f)
                    .scaleY(if (focused) scale else 1f)
                    .rotationY(if (focused) -9f else 0f)
                    .rotationX(0f).translationX(0f).translationY(0f)
                    .translationZ(tz)
                    .setDuration(dur).setInterpolator(interp).start()
            }
            STYLE_FLOAT -> {
                view.pivotX = view.width / 2f; view.pivotY = view.height / 2f
                view.animate()
                    .scaleX(if (focused) scale else 1f)
                    .scaleY(if (focused) scale else 1f)
                    .translationY(if (focused) -6f * d else 0f)
                    .translationX(0f).rotationX(0f).rotationY(0f)
                    .translationZ(tz)
                    .setDuration(dur).setInterpolator(SILK_EASE).start()
            }
            STYLE_SWING -> {
                view.pivotX = view.width * 0.5f; view.pivotY = 0f
                view.animate()
                    .scaleX(if (focused) scale else 1f)
                    .scaleY(if (focused) scale else 1f)
                    .rotationX(if (focused) 6f else 0f)
                    .rotationY(0f).translationX(0f).translationY(0f)
                    .translationZ(tz)
                    .setDuration(dur).setInterpolator(interp).start()
            }
            else -> {
                view.pivotX = view.width / 2f; view.pivotY = view.height / 2f
                view.animate()
                    .scaleX(if (focused) scale else 1f)
                    .scaleY(if (focused) scale else 1f)
                    .translationX(0f).translationY(0f).rotationX(0f).rotationY(0f)
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
        val d = panel.resources.displayMetrics.density

        when (style) {
            STYLE_JELLY -> {
                panel.pivotX = w / 2f; panel.pivotY = h / 2f
                panel.scaleX = 0.86f; panel.scaleY = 1.12f
                panel.animate().scaleX(1f).scaleY(1f)
                    .setDuration(760L).setInterpolator(JELLY_POP).start()
            }
            STYLE_FLIP -> {
                panel.pivotX = w / 2f; panel.pivotY = h / 2f
                panel.rotationY = 36f; panel.scaleX = 0.90f; panel.scaleY = 0.90f
                panel.animate().rotationY(0f).scaleX(1f).scaleY(1f)
                    .setDuration(760L).setInterpolator(OVERSHOOT).start()
            }
            STYLE_BOOK -> {
                panel.pivotX = 0f; panel.pivotY = h / 2f
                panel.rotationY = 34f; panel.alpha = 0.72f
                panel.animate().rotationY(0f).alpha(1f)
                    .setDuration(760L).setInterpolator(OVERSHOOT).start()
            }
            STYLE_FLOAT -> {
                panel.translationY = 44f * d; panel.alpha = 0.65f
                panel.animate().translationY(0f).alpha(1f)
                    .setDuration(740L).setInterpolator(SILK_EASE).start()
            }
            STYLE_SWING -> {
                panel.pivotX = w * 0.5f; panel.pivotY = 0f
                panel.rotationX = -22f; panel.scaleY = 0.92f
                panel.animate().rotationX(0f).scaleY(1f)
                    .setDuration(760L).setInterpolator(OVERSHOOT).start()
            }
            STYLE_SILK -> {
                panel.translationX = 65f * d; panel.alpha = 0.60f
                panel.animate().translationX(0f).alpha(1f)
                    .setDuration(720L).setInterpolator(SILK_EASE).start()
            }
            else -> {
                panel.translationX = 85f * d; panel.scaleX = 0.94f
                panel.animate().translationX(0f).scaleX(1f)
                    .setDuration(760L).setInterpolator(OVERSHOOT).start()
            }
        }
    }

    fun animatePanelEnter(panel: View, stageWidget: View?, widthPx: Int, style: String, onDone: () -> Unit) {
        resetTransform(panel)
        ensureCameraDistance(panel)
        stageWidget?.let {
            it.animate().cancel()
            ensureCameraDistance(it)
            it.pivotX = it.width / 2f; it.pivotY = it.height / 2f
            it.animate()
                .alpha(0f)
                .scaleX(0.90f).scaleY(0.90f)
                .rotationY(if (style == STYLE_FLIP) -24f else 0f)
                .setDuration(260L)
                .withEndAction {
                    it.visibility = View.GONE
                    resetTransform(it)
                }
                .start()
        }

        val interp = getInterpolator(style)
        val safeW = maxOf(widthPx, LauncherUtils.dp(panel.context, 250)).toFloat()
        val safeH = maxOf(panel.height, LauncherUtils.dp(panel.context, 420)).toFloat()
        val d = panel.resources.displayMetrics.density

        when (style) {
            STYLE_JELLY -> {
                panel.pivotX = safeW / 2f; panel.pivotY = safeH / 2f
                panel.scaleX = 0.85f; panel.scaleY = 1.12f; panel.alpha = 0f
                panel.animate().scaleX(1f).scaleY(1f).alpha(1f)
                    .setDuration(440L).setInterpolator(JELLY_POP).withEndAction(onDone).start()
            }
            STYLE_FLIP -> {
                panel.pivotX = safeW / 2f; panel.pivotY = safeH / 2f
                panel.rotationY = 34f; panel.alpha = 0f; panel.scaleX = 0.92f; panel.scaleY = 0.92f
                panel.animate().rotationY(0f).alpha(1f).scaleX(1f).scaleY(1f)
                    .setDuration(440L).setInterpolator(interp).withEndAction(onDone).start()
            }
            STYLE_BOOK -> {
                panel.pivotX = 0f; panel.pivotY = safeH / 2f
                panel.rotationY = 32f; panel.alpha = 0f
                panel.animate().rotationY(0f).alpha(1f)
                    .setDuration(440L).setInterpolator(interp).withEndAction(onDone).start()
            }
            STYLE_FLOAT -> {
                panel.translationY = 48f * d; panel.alpha = 0f
                panel.animate().translationY(0f).alpha(1f)
                    .setDuration(400L).setInterpolator(SILK_EASE).withEndAction(onDone).start()
            }
            STYLE_SWING -> {
                panel.pivotX = safeW * 0.5f; panel.pivotY = 0f
                panel.rotationX = -20f; panel.alpha = 0f
                panel.animate().rotationX(0f).alpha(1f)
                    .setDuration(440L).setInterpolator(interp).withEndAction(onDone).start()
            }
            else -> {
                panel.translationX = safeW + 30f; panel.alpha = 0f
                panel.animate().translationX(0f).alpha(1f)
                    .setDuration(400L).setInterpolator(interp).withEndAction(onDone).start()
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
            it.rotationY = if (style == STYLE_FLIP) 24f else 0f
            it.animate().alpha(1f).scaleX(1f).scaleY(1f).rotationY(0f)
                .setDuration(380L).setInterpolator(getInterpolator(style)).start()
        }

        val safeW = maxOf(widthPx, LauncherUtils.dp(panel.context, 250)).toFloat()
        val finish = { resetTransform(panel); onDone() }

        when (style) {
            STYLE_FLIP -> {
                panel.pivotX = safeW / 2f
                panel.animate().rotationY(-34f).alpha(0f).scaleX(0.92f).scaleY(0.92f)
                    .setDuration(320L).withEndAction(finish).start()
            }
            STYLE_BOOK -> {
                panel.pivotX = 0f
                panel.animate().rotationY(32f).alpha(0f)
                    .setDuration(320L).withEndAction(finish).start()
            }
            STYLE_FLOAT -> {
                panel.animate().translationY(42f).alpha(0f)
                    .setDuration(300L).withEndAction(finish).start()
            }
            STYLE_SWING -> {
                panel.pivotX = safeW * 0.5f; panel.pivotY = 0f
                panel.animate().rotationX(-18f).alpha(0f)
                    .setDuration(320L).withEndAction(finish).start()
            }
            else -> {
                panel.animate().translationX(safeW + 30f).alpha(0f)
                    .setDuration(340L).setInterpolator(getInterpolator(style)).withEndAction(finish).start()
            }
        }
    }
}
