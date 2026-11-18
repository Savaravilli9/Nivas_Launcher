package com.shreenivas.tvhome.homescreen

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Shader
import android.graphics.RectF
import android.widget.FrameLayout
import com.shreenivas.tvhome.LauncherUtils
import com.shreenivas.tvhome.ui.TileThemePalette

class GlowTileLayout(context: Context) : FrameLayout(context) {
    private val cardPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val glassSheenPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val spotlightPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val outerHaloPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val deepAuraPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }

    private val rectF = RectF()
    private val sheenRect = RectF()
    private val shapePath = Path()

    private var tileFocused = false
    private var glowIntensity = 0f
    private var accentColor = Color.rgb(92, 184, 255)
    private var appDominantColor = Color.rgb(92, 184, 255)
    private var tileShape = "squircle"
    private var tileKey = "frost"

    init {
        setWillNotDraw(false)
        clipChildren = false
        clipToPadding = false
    }

    fun setStyle(color: Int, shape: String?, themeKey: String?, dominantColor: Int = color) {
        accentColor = color
        appDominantColor = dominantColor
        tileShape = shape ?: "squircle"
        tileKey = themeKey ?: "frost"
        if (width > 0 && height > 0) {
            buildShapePath(width.toFloat(), height.toFloat())
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
        buildShapePath(wf, hf)
        updateShaders(wf, hf)
    }

    private fun updateShaders(w: Float, h: Float) {
        if (w <= 0f || h <= 0f) return

        val colors = TileThemePalette.getTopBottomColors(tileKey, tileFocused)
        cardPaint.shader = LinearGradient(0f, 0f, 0f, h, colors, null, Shader.TileMode.CLAMP)

        sheenRect.set(0f, 0f, w, h * 0.50f)
        glassSheenPaint.shader = LinearGradient(
            0f, 0f, 0f, h * 0.50f,
            Color.argb(if (tileFocused) 125 else 68, 255, 255, 255),
            Color.argb(6, 255, 255, 255),
            Shader.TileMode.CLAMP
        )

        if (tileFocused) {
            val centerGlow = LauncherUtils.withAlpha(appDominantColor, 165)
            spotlightPaint.shader = RadialGradient(
                w / 2f, h * 0.45f, maxOf(w, h) * 0.68f,
                centerGlow, Color.TRANSPARENT, Shader.TileMode.CLAMP
            )
            borderPaint.shader = null
        } else {
            borderPaint.shader = LinearGradient(
                0f, 0f, 0f, h,
                Color.argb(120, 255, 255, 255),
                Color.argb(30, 255, 255, 255),
                Shader.TileMode.CLAMP
            )
        }
    }

    private fun buildShapePath(w: Float, h: Float) {
        shapePath.reset()
        val pad = dp(2.5f)
        when (tileShape) {
            "heart" -> {
                val cx = w / 2f
                val topY = h * 0.22f
                val botY = h - pad * 2f
                val leftX = w * 0.12f
                val rightX = w * 0.88f
                shapePath.moveTo(cx, botY)
                shapePath.cubicTo(leftX - w * 0.08f, h * 0.65f, leftX, h * 0.12f, cx, topY + h * 0.08f)
                shapePath.cubicTo(rightX, h * 0.12f, rightX + w * 0.08f, h * 0.65f, cx, botY)
                shapePath.close()
            }
            "circle" -> {
                val r = (minOf(w, h) / 2f) - pad
                shapePath.addCircle(w / 2f, h / 2f, r, Path.Direction.CW)
            }
            else -> {
                val radius = if (tileShape == "classic") dp(6f) else dp(20f)
                rectF.set(pad, pad, w - pad, h - pad)
                shapePath.addRoundRect(rectF, radius, radius, Path.Direction.CW)
            }
        }
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        if (tileFocused) {
            // Outer soft ambient aura using App's Dominant Color
            deepAuraPaint.strokeWidth = dp(11f) + (dp(5f) * glowIntensity)
            deepAuraPaint.color = LauncherUtils.withAlpha(appDominantColor, (45 + 75 * glowIntensity).toInt())
            canvas.drawPath(shapePath, deepAuraPaint)

            outerHaloPaint.strokeWidth = dp(5f) + (dp(2.5f) * glowIntensity)
            outerHaloPaint.color = LauncherUtils.withAlpha(appDominantColor, (120 + 115 * glowIntensity).toInt())
            canvas.drawPath(shapePath, outerHaloPaint)
        }

        canvas.drawPath(shapePath, cardPaint)

        canvas.save()
        canvas.clipPath(shapePath)
        canvas.drawRect(sheenRect, glassSheenPaint)

        if (tileFocused) {
            spotlightPaint.alpha = (180 + 75 * glowIntensity).toInt()
            canvas.drawRect(0f, 0f, w, h, spotlightPaint)
        }
        canvas.restore()

        if (tileFocused) {
            borderPaint.strokeWidth = dp(2.4f)
            borderPaint.color = appDominantColor
            canvas.drawPath(shapePath, borderPaint)
        } else {
            borderPaint.strokeWidth = dp(1f)
            canvas.drawPath(shapePath, borderPaint)
        }

        super.onDraw(canvas)
    }

    private fun dp(value: Float) = LauncherUtils.dpF(context, value)
}
