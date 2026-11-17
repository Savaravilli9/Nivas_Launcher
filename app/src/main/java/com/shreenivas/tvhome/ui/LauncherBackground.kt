package com.shreenivas.tvhome.ui

import com.shreenivas.tvhome.LauncherPreferences
import com.shreenivas.tvhome.utils.LauncherUtils

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Shader
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
                scaleX = baseZoom; scaleY = baseZoom
                translationX = sp.getInt("wp_offset_x", 0).toFloat()
                translationY = baseOffsetY.toFloat()
                val bmp = decodeSampledBitmapFromUri(ctx, Uri.parse(wallpaper))
                if (bmp != null) {
                    setImageBitmap(bmp)
                } else {
                    try { setImageURI(Uri.parse(wallpaper)) } catch (_: Exception) {}
                }
            }
            root.addView(image, 0, FrameLayout.LayoutParams(-1, -1))
            activeWallpaperView = image
        } else {
            root.background = null
            val cosmos = RoyalMoonCosmosView(ctx, lp.accentColor)
            root.addView(cosmos, 0, FrameLayout.LayoutParams(-1, -1))
            activeCosmosView = cosmos
        }
    }

    private fun decodeSampledBitmapFromUri(ctx: Context, uri: Uri): Bitmap? {
        return try {
            val dm = ctx.resources.displayMetrics
            val reqW = maxOf(1280, dm.widthPixels)
            val reqH = maxOf(720, dm.heightPixels)

            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }

            var inSampleSize = 1
            if (options.outHeight > reqH || options.outWidth > reqW) {
                val halfH = options.outHeight / 2
                val halfW = options.outWidth / 2
                while (halfH / inSampleSize >= reqH && halfW / inSampleSize >= reqW) {
                    inSampleSize *= 2
                }
            }

            val decodeOpts = BitmapFactory.Options().apply {
                this.inSampleSize = inSampleSize
                inPreferredConfig = Bitmap.Config.RGB_565
            }
            ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, decodeOpts) }
        } catch (_: Exception) {
            null
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

    private class RoyalMoonCosmosView(ctx: Context, private var accentColor: Int) : View(ctx) {
        private val skyPaint = Paint(Paint.DITHER_FLAG)
        private val themeAuraPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val sparklePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
        private val naturalGlowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val moonDiscPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val craterPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(30, 165, 185, 215) }
        
        private val starPath = Path()
        private var flowAnim: ValueAnimator? = null
        private var phase = 0f
        private var parallaxY = 0f

        private data class SparkleStar(val xRatio: Float, val yRatio: Float, val sizePx: Float, val speed: Float, val offset: Float, val tint: Int)
        private val sparkles = List(45) { i ->
            val rnd = Random(i * 997 + 13)
            val d = resources.displayMetrics.density
            val tint = when (i % 4) {
                0 -> Color.rgb(255, 245, 205); 1 -> Color.rgb(185, 225, 255)
                2 -> Color.rgb(225, 200, 255); else -> Color.WHITE
            }
            SparkleStar(
                0.02f + rnd.nextFloat() * 0.96f, 0.02f + rnd.nextFloat() * 0.85f,
                (1.8f + rnd.nextFloat() * 4.5f) * d, 1.2f + rnd.nextFloat() * 3.5f,
                rnd.nextFloat() * 10f, tint
            )
        }

        fun updateAccent(newAccent: Int) {
            accentColor = newAccent; buildShaders(width, height); invalidate()
        }
        fun setParallaxShift(shift: Float) {
            if (parallaxY != shift) { parallaxY = shift; invalidate() }
        }

        override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
            super.onSizeChanged(w, h, oldw, oldh)
            if (w <= 0 || h <= 0) return
            buildShaders(w, h)
            flowAnim?.cancel()
            flowAnim = ValueAnimator.ofFloat(0f, 6.28318f).apply {
                duration = 18000L; repeatCount = ValueAnimator.INFINITE; interpolator = LinearInterpolator()
                addUpdateListener { phase = it.animatedValue as Float; invalidate() }; start()
            }
        }

        private fun buildShaders(w: Int, h: Int) {
            if (w <= 0 || h <= 0) return
            skyPaint.shader = LinearGradient(0f, 0f, w.toFloat(), h.toFloat(), intArrayOf(Color.rgb(10, 16, 36), Color.rgb(18, 28, 56), Color.rgb(8, 12, 28)), floatArrayOf(0f, 0.50f, 1f), Shader.TileMode.CLAMP)
            themeAuraPaint.shader = RadialGradient(w * 0.32f, h * 0.42f, w * 0.65f, intArrayOf(LauncherUtils.withAlpha(accentColor, 65), Color.TRANSPARENT), floatArrayOf(0f, 1f), Shader.TileMode.CLAMP)
            
            val moonX = w * 0.5f
            val moonY = h * 0.15f
            val moonR = 24f * resources.displayMetrics.density

            naturalGlowPaint.shader = RadialGradient(
                moonX, moonY, w * 0.5f,
                intArrayOf(Color.argb(220, 255, 245, 190), Color.argb(90, 255, 220, 130), Color.argb(25, 255, 200, 100), Color.TRANSPARENT),
                floatArrayOf(0f, 0.12f, 0.35f, 1f), Shader.TileMode.CLAMP
            )

            moonDiscPaint.shader = RadialGradient(moonX - moonR * 0.2f, moonY - moonR * 0.2f, moonR * 1.2f, intArrayOf(Color.rgb(255, 254, 245), Color.rgb(225, 236, 255)), null, Shader.TileMode.CLAMP)
        }

        override fun onDraw(canvas: Canvas) {
            val w = width.toFloat(); val h = height.toFloat()
            if (w <= 0f || h <= 0f) return

            canvas.drawRect(0f, 0f, w, h, skyPaint)
            canvas.drawRect(0f, 0f, w, h, themeAuraPaint)

            val moonX = w * 0.5f
            val moonY = h * 0.15f - parallaxY * 0.35f
            val moonR = 24f * resources.displayMetrics.density

            canvas.drawCircle(moonX, moonY, w * 0.5f, naturalGlowPaint)
            canvas.drawCircle(moonX, moonY, moonR, moonDiscPaint)
            canvas.drawCircle(moonX - moonR * 0.32f, moonY - moonR * 0.18f, moonR * 0.22f, craterPaint)
            canvas.drawCircle(moonX + moonR * 0.28f, moonY + moonR * 0.24f, moonR * 0.16f, craterPaint)

            for (s in sparkles) {
                val erraticWave = sin(phase * s.speed + s.offset) * cos(phase * s.speed * 0.6f + s.offset)
                val pulse = 0.3f + 0.7f * Math.abs(erraticWave)
                val sx = s.xRatio * w; val sy = s.yRatio * h - parallaxY * 0.25f
                val r = s.sizePx * (0.55f + 0.45f * pulse)
                sparklePaint.color = LauncherUtils.withAlpha(s.tint, (pulse * 245).toInt())
                
                starPath.reset(); val inner = r * 0.22f
                starPath.moveTo(sx, sy - r); starPath.lineTo(sx + inner, sy - inner); starPath.lineTo(sx + r, sy)
                starPath.lineTo(sx + inner, sy + inner); starPath.lineTo(sx, sy + r); starPath.lineTo(sx - inner, sy + inner)
                starPath.lineTo(sx - r, sy); starPath.lineTo(sx - inner, sy - inner); starPath.close()
                canvas.drawPath(starPath, sparklePaint)
            }
        }
        override fun onDetachedFromWindow() { super.onDetachedFromWindow(); flowAnim?.cancel() }
    }
}
