package com.shreenivas.tvhome

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Shader
import android.graphics.SweepGradient
import android.net.Uri
import android.view.View
import android.view.animation.LinearInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

object LauncherBackground {
    private var activeWallpaperView: ImageView? = null
    private var activeCosmosView: RoyalMoonCosmosView? = null
    private var baseZoom = 1.08f
    private var baseOffsetY = 0

    fun apply(ctx: Context, root: FrameLayout, lp: LauncherPreferences) {
        val sp = lp.prefs
        val wallpaper = sp.getString(LauncherPreferences.PREF_WALLPAPER, null)
        activeWallpaperView = null
        activeCosmosView = null

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
                try { setImageURI(Uri.parse(wallpaper)) } catch (_: Exception) {}
            }
            root.addView(image, 0, FrameLayout.LayoutParams(-1, -1))
            activeWallpaperView = image
        } else {
            root.background = null
            // We pass accentColor for the interactive aura
            val cosmos = RoyalMoonCosmosView(ctx, lp.accentColor)
            root.addView(cosmos, 0, FrameLayout.LayoutParams(-1, -1))
            activeCosmosView = cosmos
        }
    }

    fun updateThemeAndAccent(root: FrameLayout, lp: LauncherPreferences) {
        if (lp.prefs.getString(LauncherPreferences.PREF_WALLPAPER, null) == null) {
            activeCosmosView?.updateAccent(lp.accentColor)
        }
    }

    fun onScrollParallax(scrollY: Int) {
        val shift = minOf(22f, scrollY * 0.07f)
        activeWallpaperView?.translationY = baseOffsetY - shift
        activeCosmosView?.setParallaxShift(shift)
    }

    private class RoyalMoonCosmosView(
        ctx: Context,
        private var accentColor: Int
    ) : View(ctx) {

        private val skyPaint = Paint(Paint.DITHER_FLAG)
        private val themeAuraPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val sparklePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
        private val moonHaloOuter = Paint(Paint.ANTI_ALIAS_FLAG)
        private val moonHaloInner = Paint(Paint.ANTI_ALIAS_FLAG)
        private val moonDiscPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val craterPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(26, 145, 165, 205) }
        
        // Added Golden Rays Paint
        private val goldenRayPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        private var moonR = 0f
        
        private val starPath = Path()
        private var flowAnim: ValueAnimator? = null
        private var phase = 0f
        private var parallaxY = 0f
                private data class SparkleStar(
            val xRatio: Float,
            val yRatio: Float,
            val sizePx: Float,
            val speed: Float,
            val offset: Float,
            val tint: Int
        )

        private val sparkles = List(50) { i ->
            val rnd = Random(i * 997 + 13)
            val d = resources.displayMetrics.density
            val tint = when (i % 4) {
                0 -> Color.rgb(255, 245, 205)
                1 -> Color.rgb(185, 225, 255)
                2 -> Color.rgb(225, 200, 255)
                else -> Color.WHITE
            }
            SparkleStar(
                xRatio = 0.02f + rnd.nextFloat() * 0.96f,
                yRatio = 0.02f + rnd.nextFloat() * 0.85f,
                sizePx = (1.8f + rnd.nextFloat() * 4.5f) * d,
                speed = 1.2f + rnd.nextFloat() * 3.5f,
                offset = rnd.nextFloat() * 10f,
                tint = tint
            )
        }

        fun updateAccent(newAccent: Int) {
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
                duration = 18000L
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
            
            // Fixed Beautiful Deep Space Sky (Independent of Tile Theme)
            val fixedSky = intArrayOf(Color.rgb(10, 16, 36), Color.rgb(18, 28, 56), Color.rgb(8, 12, 28))
            skyPaint.shader = LinearGradient(
                0f, 0f, w.toFloat(), h.toFloat(),
                fixedSky,
                floatArrayOf(0f, 0.50f, 1f),
                Shader.TileMode.CLAMP
            )

            // Dynamic Aura based on Accent
            themeAuraPaint.shader = RadialGradient(
                w * 0.32f, h * 0.42f, w * 0.65f,
                intArrayOf(LauncherUtils.withAlpha(accentColor, 75), Color.TRANSPARENT),
                floatArrayOf(0f, 1f),
                Shader.TileMode.CLAMP
            )

            // Golden Rays Sweep Shader
            goldenRayPaint.shader = SweepGradient(0f, 0f, 
                intArrayOf(
                    Color.TRANSPARENT, Color.argb(45, 255, 215, 80), 
                    Color.TRANSPARENT, Color.argb(30, 255, 195, 60),
                    Color.TRANSPARENT, Color.argb(45, 255, 215, 80), 
                    Color.TRANSPARENT
                ), null
            )
            moonR = 20f * resources.displayMetrics.density
        }

        override fun onDraw(canvas: Canvas) {
            val w = width.toFloat()
            val h = height.toFloat()
            if (w <= 0f || h <= 0f) return

            canvas.drawRect(0f, 0f, w, h, skyPaint)
            canvas.drawRect(0f, 0f, w, h, themeAuraPaint)

            val moonX = w * 0.89f
            val moonY = h * 0.22f - parallaxY * 0.35f

            // Golden Sweep Rays (Rotating)
            canvas.save()
            canvas.translate(moonX, moonY)
            canvas.rotate((phase * 45f) % 360f)
            canvas.drawCircle(0f, 0f, moonR * 7f, goldenRayPaint)
            canvas.restore()

            moonHaloOuter.shader = RadialGradient(
                moonX, moonY, moonR * 9.5f,
                intArrayOf(Color.argb(68, 210, 232, 255), Color.argb(26, 165, 205, 255), Color.TRANSPARENT),
                floatArrayOf(0f, 0.42f, 1f), Shader.TileMode.CLAMP
            )
            canvas.drawCircle(moonX, moonY, moonR * 9.5f, moonHaloOuter)

            moonHaloInner.shader = RadialGradient(
                moonX, moonY, moonR * 3.0f,
                intArrayOf(Color.argb(155, 255, 252, 240), Color.argb(55, 215, 235, 255), Color.TRANSPARENT),
                floatArrayOf(0f, 0.45f, 1f), Shader.TileMode.CLAMP
            )
            canvas.drawCircle(moonX, moonY, moonR * 3.0f, moonHaloInner)

            moonDiscPaint.shader = RadialGradient(
                moonX - moonR * 0.25f, moonY - moonR * 0.25f, moonR * 1.2f,
                intArrayOf(Color.rgb(255, 254, 245), Color.rgb(225, 236, 255)),
                null, Shader.TileMode.CLAMP
            )
            canvas.drawCircle(moonX, moonY, moonR, moonDiscPaint)
            canvas.drawCircle(moonX - moonR * 0.32f, moonY - moonR * 0.18f, moonR * 0.22f, craterPaint)
            canvas.drawCircle(moonX + moonR * 0.28f, moonY + moonR * 0.24f, moonR * 0.16f, craterPaint)

            for (s in sparkles) {
                // Chaotic Twinkling (Combining two Sine waves)
                val erraticWave = sin(phase * s.speed + s.offset) * cos(phase * s.speed * 0.6f + s.offset)
                val pulse = 0.3f + 0.7f * Math.abs(erraticWave)
                
                val sx = s.xRatio * w
                val sy = s.yRatio * h - parallaxY * 0.25f
                val r = s.sizePx * (0.55f + 0.45f * pulse)

                sparklePaint.color = LauncherUtils.withAlpha(s.tint, (pulse * 245).toInt())
                drawFourPointSparkle(canvas, sx, sy, r)
            }
        }

        private fun drawFourPointSparkle(canvas: Canvas, cx: Float, cy: Float, r: Float) {
            val inner = r * 0.22f
            starPath.reset()
            starPath.moveTo(cx, cy - r)
            starPath.lineTo(cx + inner, cy - inner)
            starPath.lineTo(cx + r, cy)
            starPath.lineTo(cx + inner, cy + inner)
            starPath.lineTo(cx, cy + r)
            starPath.lineTo(cx - inner, cy + inner)
            starPath.lineTo(cx - r, cy)
            starPath.lineTo(cx - inner, cy - inner)
            starPath.close()
            canvas.drawPath(starPath, sparklePaint)
        }

        override fun onDetachedFromWindow() {
            super.onDetachedFromWindow()
            flowAnim?.cancel()
        }
    }
}
