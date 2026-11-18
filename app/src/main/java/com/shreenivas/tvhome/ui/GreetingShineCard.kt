package com.shreenivas.tvhome.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import android.view.Gravity
import android.view.animation.LinearInterpolator
import android.widget.LinearLayout
import android.widget.TextView
import com.shreenivas.tvhome.LauncherUtils
import java.util.Calendar

class GreetingShineCard(
    ctx: Context,
    private var userName: String,
    private var customPhrase: String,
    private var accentColor: Int
) : LinearLayout(ctx) {

    private val iconTv = TextView(ctx)
    private val titleTv = TextView(ctx)
    private var overlayBubble: TextView? = null
    private var shimmerAnim: ValueAnimator? = null
    private var shimmerX = -200f
    private val shimmerPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val shaderMatrix = Matrix()

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(14), dp(7), dp(16), dp(7))

        iconTv.apply {
            textSize = 15f
            setPadding(0, 0, dp(8), 0)
        }
        titleTv.apply {
            textSize = 12.5f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            isSingleLine = true
            ellipsize = TextUtils.TruncateAt.END
        }
        addView(iconTv)
        addView(titleTv)
        updateGreeting(userName, customPhrase)
        setAccentColor(accentColor)
    }

    fun attachOverlayBubble(bubble: TextView) {
        overlayBubble = bubble
    }

    fun setAccentColor(accent: Int) {
        accentColor = accent
        background = GradientDrawable(
            GradientDrawable.Orientation.LEFT_RIGHT,
            intArrayOf(LauncherUtils.withAlpha(accentColor, 115), Color.argb(85, 22, 34, 72))
        ).apply {
            cornerRadius = LauncherUtils.dpF(context, 20f)
            setStroke(dp(1), Color.argb(115, 255, 255, 255))
        }
    }

    fun updateGreeting(name: String, phrase: String) {
        userName = name
        customPhrase = phrase

        val hr = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val (emoji, autoGreet) = when (hr) {
            in 5..11 -> "☀️" to "Good Morning"
            in 12..16 -> "🌤️" to "Good Afternoon"
            in 17..20 -> "🌆" to "Good Evening"
            else -> "🌙" to "Good Night"
        }
        iconTv.text = emoji
        val cleanName = userName.trim()
        titleTv.text = when {
            customPhrase.isNotBlank() && cleanName.isNotEmpty() -> "$customPhrase, $cleanName"
            customPhrase.isNotBlank() -> customPhrase
            cleanName.isNotEmpty() -> "$autoGreet, $cleanName"
            else -> autoGreet
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w <= 0) return
        shimmerPaint.shader = LinearGradient(
            0f, 0f, w * 0.4f, 0f,
            intArrayOf(Color.TRANSPARENT, Color.argb(45, 255, 255, 255), Color.TRANSPARENT),
            floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP
        )
        shimmerAnim?.cancel()
        shimmerAnim = ValueAnimator.ofFloat(-w * 0.5f, w * 1.5f).apply {
            duration = 3200L; repeatCount = ValueAnimator.INFINITE; interpolator = LinearInterpolator()
            addUpdateListener { shimmerX = it.animatedValue as Float; invalidate() }
            start()
        }
    }

    override fun dispatchDraw(canvas: Canvas) {
        super.dispatchDraw(canvas)
        if (width > 0 && shimmerPaint.shader != null) {
            shaderMatrix.setTranslate(shimmerX, 0f)
            shimmerPaint.shader.setLocalMatrix(shaderMatrix)
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), shimmerPaint)
        }
    }

    override fun onDetachedFromWindow() { super.onDetachedFromWindow(); shimmerAnim?.cancel() }
    private fun dp(v: Int) = LauncherUtils.dp(context, v)
}
