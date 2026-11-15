package com.shreenivas.tvhome

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.net.Uri
import android.view.View
import android.view.animation.LinearInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import kotlin.math.sin
import kotlin.random.Random

object LauncherBackground {
    private var activeWallpaperView: ImageView? = null
    private var activeAuroraView: RoyalCosmosView? = null
    private var baseZoom = 1.08f
    private var baseOffsetY = 0

    fun apply(ctx: Context, root: FrameLayout, lp: LauncherPreferences) {
        val sp = lp.prefs
        val wallpaper = sp.getString(LauncherPreferences.PREF_WALLPAPER, null)
        activeWallpaperView = null
        activeAuroraView = null

        if (wallpaper != null) {
            root.background = null
            val image = ImageView(ctx).apply {
                scaleType = when (sp.getString("wp_scale_mode", "fit")) {
                    "width" -> ImageView.ScaleType.FIT_XY
                    "height" -> ImageView.ScaleType.FIT_CENTER
                    else -> ImageView.ScaleType.CENTER_CROP
                }
                baseZoom = maxOf(1.08f, sp.getFloat("wp_zoom", 1.0f))
                baseOffsetY = sp.getInt("wp_offset_y", 0)
                scaleX = baseZoom
                scaleY = baseZoom
                translationX = sp.getInt("wp_offset_x", 0).toFloat()
                translationY = baseOffsetY.toFloat()
                try {
                    setImageURI(Uri.parse(wallpaper))
                } catch (_: Exception) {
                }
            }
            root.addView(image, 0, FrameLayout.LayoutParams(-1, -1))
            activeWallpaperView = image
        } else {
            root.background = null
            val cosmos = RoyalCosmosView(ctx, lp.tileColor, lp.accentColor)
            root.addView(cosmos, 0, FrameLayout.LayoutParams(-1, -1))
            activeAuroraView = cosmos
        }
    }

    fun updateThemeAndAccent(root: FrameLayout, lp: LauncherPreferences) {
        if (lp.prefs.getString(LauncherPreferences.PREF_WALLPAPER, null) == null) {
            activeAuroraView?.updateStyle(lp.tileColor, lp.accentColor)
        }
    }

    fun onScrollParallax(scrollY: Int) {
        val shift = minOf(24f, scrollY * 0.08f)
        activeWallpaperView?.translationY = baseOffsetY - shift
        activeAuroraView?.setParallaxShift(shift)
    }

    private class RoyalCosmosView(
        ctx: Context,
        private var themeKey: String,
        private var accentColor: Int
    ) : View(ctx) {

        private val skyPaint = Paint(Paint.DITHER_FLAG)
        private val topLeftGlow = Paint(Paint.ANTI_ALIAS_FLAG)
        private val centerVioletGlow = Paint(Paint.ANTI_ALIAS_FLAG)
        private val starPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        private var flowAnim: ValueAnimator? = null
        private var phase = 0f
        private var parallaxY = 0f

        private data class Star(val xRatio: Float, val yRatio: Float, val radiusPx: Float, val offset: Float)

        private val stars = List(48) { i ->
            val rnd = Random(i * 1013 + 11)
            val d = resources.displayMetrics.density
            Star(
                xRatio = rnd.nextFloat(),
                yRatio = rnd.nextFloat() * 0.78f,
                radiusPx = (0.6f + rnd.nextFloat() * 1.1f) * d,
                offset = rnd.nextFloat() * 6.28f
            )
        }

        fun updateStyle(newTheme: String, newAccent: Int) {
            themeKey = newTheme
            accentColor = newAccent
            buildShaders(width, height)
            invalidate()
        }

        fun setParallaxShift(shift: Float) {
            if (parallaxY != shift) {
                parallaxY = shift
                invalidate()
            }
        }

        override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
            super.onSizeChanged(w, h, oldw, oldh)
            if (w <= 0 || h <= 0) return
            buildShaders(w, h)

            flowAnim?.cancel()
            flowAnim = ValueAnimator.ofFloat(0f, 6.28318f).apply {
                duration = 26000L
                repeatCount = ValueAnimator.INFINITE
                interpolator = LinearInterpolator()
                addUpdateListener {
                    phase = it.animatedValue as Float
                    invalidate()
                }
                start()
            }
        }

        private fun buildShaders(w: Int, h: Int) {
            if (w <= 0 || h <= 0) return
            val baseColors = TileThemePalette.getWallpaperGradient(themeKey)

            skyPaint.shader = LinearGradient(
                0f, 0f, w.toFloat(), h.toFloat(),
                intArrayOf(baseColors, baseColors, baseColors),
                floatArrayOf(0f, 0.50f, 1f),
                Shader.TileMode.CLAMP
            )

            topLeftGlow.shader = RadialGradient(
                w * 0.22f, h * 0.25f, w * 0.55f,
                intArrayOf(
                    LauncherUtils.withAlpha(accentColor, 95),
                    Color.argb(40, 65, 130, 255),
                    Color.TRANSPARENT
                ),
                floatArrayOf(0f, 0.50f, 1f),
                Shader.TileMode.CLAMP
            )

            centerVioletGlow.shader = RadialGradient(
                w * 0.65f, h * 0.55f, w * 0.60f,
                intArrayOf(
                    Color.argb(85, 115, 65, 225),
                    Color.argb(35, 85, 45, 185),
                    Color.TRANSPARENT
                ),
                floatArrayOf(0f, 0.55f, 1f),
                Shader.TileMode.CLAMP
            )
        }

        override fun onDraw(canvas: Canvas) {
            val w = width.toFloat()
            val h = height.toFloat()
            if (w <= 0f || h <= 0f) return

            canvas.drawRect(0f, 0f, w, h, skyPaint)
            canvas.drawRect(0f, 0f, w, h, topLeftGlow)
            canvas.drawRect(0f, 0f, w, h, centerVioletGlow)

            for (s in stars) {
                val twinkle = 0.35f + 0.65f * sin(phase * 2f + s.offset)
                starPaint.color = Color.argb((twinkle * 185).toInt(), 215, 230, 255)
                canvas.drawCircle(s.xRatio * w, s.yRatio * h - parallaxY * 0.3f, s.radiusPx, starPaint)
            }
        }

        override fun onDetachedFromWindow() {
            super.onDetachedFromWindow()
            flowAnim?.cancel()
        }
    }
}
