package com.shreenivas.tvhome.homescreen

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.widget.FrameLayout
import com.shreenivas.tvhome.LauncherUtils
import com.shreenivas.tvhome.ui.TileThemePalette

class GlowTileLayout(context: Context) : FrameLayout(context) {
    private val outerCardPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val outerSheenPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val spotlightPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val outerBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val innerRimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val outerHaloPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val deepAuraPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }

    private val rectF = RectF()
    private val sheenRect = RectF()
    private val outerPath = Path()
    private val innerIconPath = Path()

    private var tileFocused = false
    private var glowIntensity = 0f
    private var accentColor = Color.rgb(92, 184, 255)
    private var appDominantColor = Color.rgb(92, 184, 255)
    private var iconShape = "squircle"
    private var tileKey = "frost"

    init {
        setWillNotDraw(false)
        clipChildren = false
        clipToPadding = false
        outlineProvider = null
    }

    fun setStyle(color: Int, shape: String?, themeKey: String?, dominantColor: Int = color) {
        accentColor = color
        appDominantColor = dominantColor
        iconShape = shape ?: "squircle"
        tileKey = themeKey ?: "frost"
        if (width > 0 && height > 0) {
            buildDoublePaths(width.toFloat(), height.toFloat())
            updateShaders(width.toFloat(), height.toFloat())
        }
        invalidate()
    }

    fun setFocusState(focused: Boolean) {
        if (tileFocused == focused) return
        tileFocused = focused
        if (!focused) glowIntensity = 0f
        if (width > 0 && height > 0) {
            updateShaders(width.toFloat(), height.toFloat())
        }
        invalidate()
    }

    fun updateGlowPulse(intensity: Float) {
        glowIntensity = intensity.coerceIn(0f, 1f)
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        val wf = w.toFloat()
        val hf = h.toFloat()
        buildDoublePaths(wf, hf)
        updateShaders(wf, hf)
    }

    private fun updateShaders(w: Float, h: Float) {
        if (w <= 0f || h <= 0f) return

        val colors = TileThemePalette.getTopBottomColors(tileKey, tileFocused)
        outerCardPaint.shader = LinearGradient(0f, 0f, 0f, h, colors, null, Shader.TileMode.CLAMP)

        sheenRect.set(0f, 0f, w, h * 0.50f)
        outerSheenPaint.shader = LinearGradient(
            0f, 0f, 0f, h * 0.50f,
            Color.argb(if (tileFocused) 135 else 78, 255, 255, 255),
            Color.argb(8, 255, 255, 255),
            Shader.TileMode.CLAMP
        )

        if (tileFocused) {
            val centerGlow = LauncherUtils.withAlpha(appDominantColor, 175)
            spotlightPaint.shader = RadialGradient(
                w / 2f, h * 0.45f, maxOf(w, h) * 0.70f,
                centerGlow, Color.TRANSPARENT, Shader.TileMode.CLAMP
            )
            outerBorderPaint.shader = null
        } else {
            outerBorderPaint.shader = LinearGradient(
                0f, 0f, 0f, h,
                Color.argb(150, 255, 255, 255),
                Color.argb(40, 255, 255, 255),
                Shader.TileMode.CLAMP
            )
        }
    }

    private fun buildDoublePaths(w: Float, h: Float) {
        val outerInset = dp(1.5f)
        val visibleGap = dp(7.5f) // Visible gap between Outer Theme Shape and Inner Icon Shape
        buildShapeIntoPath(outerPath, w, h, outerInset)
        buildShapeIntoPath(innerIconPath, w, h, outerInset + visibleGap)
    }

    private fun buildShapeIntoPath(target: Path, w: Float, h: Float, inset: Float) {
        target.reset()
        val left = inset
        val top = inset
        val right = w - inset
        val bottom = h - inset
        val sw = right - left
        val sh = bottom - top
        if (sw <= 0f || sh <= 0f) return

        when (iconShape) {
            "heart" -> {
                val cx = left + sw / 2f
                val topY = top + sh * 0.22f
                val botY = bottom - dp(1f)
                val lx = left + sw * 0.08f
                val rx = right - sw * 0.08f
                target.moveTo(cx, botY)
                target.cubicTo(lx - sw * 0.08f, top + sh * 0.64f, lx, top + sh * 0.10f, cx, topY + sh * 0.07f)
                target.cubicTo(rx, top + sh * 0.10f, rx + sw * 0.08f, top + sh * 0.64f, cx, botY)
                target.close()
            }
            "circle" -> {
                val r = minOf(sw, sh) / 2f
                target.addCircle(left + sw / 2f, top + sh / 2f, r, Path.Direction.CW)
            }
            else -> {
                val radius = if (iconShape == "classic") dp(6f) else dp(18f)
                rectF.set(left, top, right, bottom)
                target.addRoundRect(rectF, radius, radius, Path.Direction.CW)
            }
        }
    }

    override fun dispatchDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        // 1. Ambient App-Based Glow when focused
        if (tileFocused) {
            deepAuraPaint.strokeWidth = dp(10f) + (dp(5f) * glowIntensity)
            deepAuraPaint.color = LauncherUtils.withAlpha(appDominantColor, (50 + 80 * glowIntensity).toInt())
            canvas.drawPath(outerPath, deepAuraPaint)

            outerHaloPaint.strokeWidth = dp(4.5f) + (dp(2.5f) * glowIntensity)
            outerHaloPaint.color = LauncherUtils.withAlpha(appDominantColor, (130 + 115 * glowIntensity).toInt())
            canvas.drawPath(outerPath, outerHaloPaint)
        }

        // 2. Outer Shape (Theme Palette + Glossy Sheen)
        canvas.drawPath(outerPath, outerCardPaint)
        canvas.save()
        canvas.clipPath(outerPath)
        canvas.drawRect(sheenRect, outerSheenPaint)
        if (tileFocused) {
            spotlightPaint.alpha = (180 + 75 * glowIntensity).toInt()
            canvas.drawRect(0f, 0f, w, h, spotlightPaint)
        }
        canvas.restore()

        // 3. Outer Glossy Edge Border
        if (tileFocused) {
            outerBorderPaint.strokeWidth = dp(2.2f)
            outerBorderPaint.color = appDominantColor
            canvas.drawPath(outerPath, outerBorderPaint)
        } else {
            outerBorderPaint.strokeWidth = dp(1.2f)
            canvas.drawPath(outerPath, outerBorderPaint)
        }

        // 4. Inner Shape: Crop & Fill the App Icon inside innerIconPath (leaving visible gap)
        canvas.save()
        canvas.clipPath(innerIconPath)
        super.dispatchDraw(canvas)
        canvas.restore()

        // 5. Crisp Inner Glossy Rim around the cropped icon
        innerRimPaint.strokeWidth = dp(1.2f)
        innerRimPaint.color = if (tileFocused) Color.argb(215, 255, 255, 255) else Color.argb(115, 255, 255, 255)
        canvas.drawPath(innerIconPath, innerRimPaint)
    }

    private fun dp(value: Float) = LauncherUtils.dpF(context, value)
}
