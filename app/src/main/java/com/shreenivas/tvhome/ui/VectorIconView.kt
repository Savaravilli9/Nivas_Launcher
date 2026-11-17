package com.shreenivas.tvhome.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.view.View

class VectorIconView(
    ctx: Context,
    private var iconType: String,
    private var tintColor: Int = Color.WHITE
) : View(ctx) {

    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        color = tintColor
    }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = tintColor
    }
    private val path = Path()
    private val rect = RectF()

    fun setIcon(type: String, color: Int = tintColor) {
        iconType = type
        tintColor = color
        strokePaint.color = color
        fillPaint.color = color
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        val cx = w / 2f
        val cy = h / 2f
        val r = minOf(w, h) * 0.34f
        strokePaint.strokeWidth = maxOf(2f, minOf(w, h) * 0.085f)
        strokePaint.color = tintColor
        fillPaint.color = tintColor

        when (iconType) {
            "mic" -> {
                rect.set(cx - r * 0.34f, cy - r * 0.85f, cx + r * 0.34f, cy + r * 0.15f)
                canvas.drawRoundRect(rect, r * 0.34f, r * 0.34f, strokePaint)
                rect.set(cx - r * 0.68f, cy - r * 0.45f, cx + r * 0.68f, cy + r * 0.52f)
                canvas.drawArc(rect, 0f, 180f, false, strokePaint)
                canvas.drawLine(cx, cy + r * 0.52f, cx, cy + r * 0.90f, strokePaint)
                canvas.drawLine(cx - r * 0.38f, cy + r * 0.90f, cx + r * 0.38f, cy + r * 0.90f, strokePaint)
            }
            "wifi" -> {
                canvas.drawCircle(cx, cy + r * 0.62f, strokePaint.strokeWidth * 0.75f, fillPaint)
                for (i in 1..3) {
                    val rad = r * (0.38f * i)
                    rect.set(cx - rad, cy + r * 0.62f - rad, cx + rad, cy + r * 0.62f + rad)
                    canvas.drawArc(rect, 225f, 90f, false, strokePaint)
                }
            }
            "bell" -> {
                path.reset()
                path.moveTo(cx - r * 0.68f, cy + r * 0.42f)
                path.lineTo(cx + r * 0.68f, cy + r * 0.42f)
                path.cubicTo(cx + r * 0.48f, cy + r * 0.18f, cx + r * 0.52f, cy - r * 0.68f, cx, cy - r * 0.75f)
                path.cubicTo(cx - r * 0.52f, cy - r * 0.68f, cx - r * 0.48f, cy + r * 0.18f, cx - r * 0.68f, cy + r * 0.42f)
                canvas.drawPath(path, strokePaint)
                rect.set(cx - r * 0.22f, cy + r * 0.48f, cx + r * 0.22f, cy + r * 0.82f)
                canvas.drawArc(rect, 0f, 180f, false, strokePaint)
            }
            "sparkle" -> {
                path.reset()
                path.moveTo(cx, cy - r * 0.95f)
                path.quadTo(cx, cy, cx + r * 0.95f, cy)
                path.quadTo(cx, cy, cx, cy + r * 0.95f)
                path.quadTo(cx, cy, cx - r * 0.95f, cy)
                path.quadTo(cx, cy, cx, cy - r * 0.95f)
                path.close()
                canvas.drawPath(path, fillPaint)
            }

            "gear" -> {
                canvas.drawCircle(cx, cy, r * 0.42f, strokePaint)
                for (i in 0 until 8) {
                    val ang = Math.toRadians(i * 45.0)
                    val x1 = (cx + Math.cos(ang) * r * 0.62f).toFloat()
                    val y1 = (cy + Math.sin(ang) * r * 0.62f).toFloat()
                    val x2 = (cx + Math.cos(ang) * r * 0.92f).toFloat()
                    val y2 = (cy + Math.sin(ang) * r * 0.92f).toFloat()
                    canvas.drawLine(x1, y1, x2, y2, strokePaint)
                }
            }
            "power" -> {
                rect.set(cx - r * 0.72f, cy - r * 0.72f, cx + r * 0.72f, cy + r * 0.72f)
                canvas.drawArc(rect, -55f, 290f, false, strokePaint)
                canvas.drawLine(cx, cy - r * 0.92f, cx, cy - r * 0.12f, strokePaint)
            }
            "bolt" -> {
                path.reset()
                path.moveTo(cx + r * 0.12f, cy - r * 0.92f)
                path.lineTo(cx - r * 0.55f, cy + r * 0.08f)
                path.lineTo(cx + r * 0.05f, cy + r * 0.08f)
                path.lineTo(cx - r * 0.15f, cy + r * 0.92f)
                path.lineTo(cx + r * 0.55f, cy - r * 0.08f)
                path.lineTo(cx - r * 0.05f, cy - r * 0.08f)
                path.close()
                canvas.drawPath(path, fillPaint)
            }
            else -> drawExtraIcons(canvas, cx, cy, r)
        }
    }
    private fun drawExtraIcons(canvas: Canvas, cx: Float, cy: Float, r: Float) {
        when (iconType) {
            "user" -> {
                canvas.drawCircle(cx, cy - r * 0.32f, r * 0.36f, strokePaint)
                rect.set(cx - r * 0.68f, cy + r * 0.22f, cx + r * 0.68f, cy + r * 1.15f)
                canvas.drawArc(rect, 180f, 180f, false, strokePaint)
            }
            "grid" -> {
                val gap = r * 0.16f
                val box = r * 0.44f
                canvas.drawRoundRect(cx - box - gap, cy - box - gap, cx - gap, cy - gap, 4f, 4f, strokePaint)
                canvas.drawRoundRect(cx + gap, cy - box - gap, cx + box + gap, cy - gap, 4f, 4f, strokePaint)
                canvas.drawRoundRect(cx - box - gap, cy + gap, cx - gap, cy + box + gap, 4f, 4f, strokePaint)
                canvas.drawRoundRect(cx + gap, cy + gap, cx + box + gap, cy + box + gap, 4f, 4f, strokePaint)
            }
            "heart" -> {
                path.reset()
                path.moveTo(cx, cy + r * 0.78f)
                path.cubicTo(cx - r * 1.1f, cy + r * 0.1f, cx - r * 0.85f, cy - r * 0.85f, cx, cy - r * 0.32f)
                path.cubicTo(cx + r * 0.85f, cy - r * 0.85f, cx + r * 1.1f, cy + r * 0.1f, cx, cy + r * 0.78f)
                canvas.drawPath(path, fillPaint)
            }
            "image" -> {
                rect.set(cx - r * 0.82f, cy - r * 0.65f, cx + r * 0.82f, cy + r * 0.65f)
                canvas.drawRoundRect(rect, 6f, 6f, strokePaint)
                canvas.drawCircle(cx - r * 0.35f, cy - r * 0.22f, r * 0.14f, fillPaint)
                path.reset()
                path.moveTo(cx - r * 0.65f, cy + r * 0.55f)
                path.lineTo(cx - r * 0.12f, cy + r * 0.05f)
                path.lineTo(cx + r * 0.22f, cy + r * 0.35f)
                path.lineTo(cx + r * 0.45f, cy + r * 0.12f)
                path.lineTo(cx + r * 0.72f, cy + r * 0.55f)
                canvas.drawPath(path, strokePaint)
            }
            "moon" -> {
                path.reset()
                path.moveTo(cx + r * 0.25f, cy - r * 0.78f)
                path.cubicTo(cx - r * 0.65f, cy - r * 0.65f, cx - r * 0.65f, cy + r * 0.65f, cx + r * 0.25f, cy + r * 0.78f)
                path.cubicTo(cx - r * 0.20f, cy + r * 0.45f, cx - r * 0.20f, cy - r * 0.45f, cx + r * 0.25f, cy - r * 0.78f)
                canvas.drawPath(path, fillPaint)
            }
            else -> {
                rect.set(cx - r * 0.72f, cy - r * 0.72f, cx + r * 0.72f, cy + r * 0.72f)
                canvas.drawRoundRect(rect, r * 0.35f, r * 0.35f, strokePaint)
            }
        }
    }
}
