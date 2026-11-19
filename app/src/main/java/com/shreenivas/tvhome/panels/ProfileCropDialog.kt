package com.shreenivas.tvhome.panels

import android.app.Dialog
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.Window
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.shreenivas.tvhome.LauncherPreferences
import com.shreenivas.tvhome.LauncherUtils
import com.shreenivas.tvhome.MotionEngine

object ProfileCropDialog {

    fun show(
        ctx: Context,
        lp: LauncherPreferences,
        onPickPhoto: () -> Unit,
        onSaved: () -> Unit
    ) {
        val dialog = Dialog(ctx)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)

        var zoom = lp.prefs.getFloat("profile_pic_zoom", 1.0f)
        var panX = lp.prefs.getFloat("profile_pic_pan_x", 0f)
        var panY = lp.prefs.getFloat("profile_pic_pan_y", 0f)
        var rawBitmap: Bitmap? = loadUserBitmap(ctx, lp.prefs.getString("profile_pic_uri", null))

        val rootGlass = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(ctx, 24), dp(ctx, 20), dp(ctx, 24), dp(ctx, 20))
            background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(Color.argb(242, 24, 36, 78), Color.argb(248, 12, 18, 44))
            ).apply {
                cornerRadius = LauncherUtils.dpF(ctx, 24f)
                setStroke(dp(ctx, 1), Color.argb(140, 255, 255, 255))
            }
        }

        // Title
        rootGlass.addView(TextView(ctx).apply {
            text = "✦  Profile & Avatar Studio"
            textSize = 16f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            setPadding(0, 0, 0, dp(ctx, 14))
        })

        val contentRow = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        // Left: Live WhatsApp/Insta Circular Crop Preview (150dp)
        val previewSize = dp(ctx, 150)
        val cropPreview = object : View(ctx) {
            private val circlePath = Path()
            private val rimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
            private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                color = Color.argb(70, 255, 255, 255)
                strokeWidth = 1.5f
            }
            private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
            private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                textAlign = Paint.Align.CENTER
                typeface = Typeface.DEFAULT_BOLD
            }
            private val drawMatrix = Matrix()

            override fun onDraw(canvas: Canvas) {
                val cx = width / 2f
                val cy = height / 2f
                val r = minOf(cx, cy) - dp(ctx, 4)

                circlePath.reset()
                circlePath.addCircle(cx, cy, r, Path.Direction.CW)

                canvas.save()
                canvas.clipPath(circlePath)

                val bmp = rawBitmap
                if (bmp != null) {
                    val baseScale = maxOf((r * 2f) / bmp.width, (r * 2f) / bmp.height)
                    val finalScale = baseScale * zoom
                    drawMatrix.reset()
                    drawMatrix.postTranslate(-bmp.width / 2f, -bmp.height / 2f)
                    drawMatrix.postScale(finalScale, finalScale)
                    drawMatrix.postTranslate(cx + (panX * r), cy + (panY * r))
                    canvas.drawBitmap(bmp, drawMatrix, fillPaint)

                    // Subtle WhatsApp/Insta alignment crosshair guides
                    canvas.drawLine(cx - r, cy, cx + r, cy, gridPaint)
                    canvas.drawLine(cx, cy - r, cx, cy + r, gridPaint)
                } else {
                    fillPaint.color = lp.accentColor
                    canvas.drawCircle(cx, cy, r, fillPaint)
                    textPaint.textSize = r * 0.65f
                    val initial = lp.userName.ifBlank { "S" }.trim().first().uppercaseChar().toString()
                    val ty = cy - ((textPaint.descent() + textPaint.ascent()) / 2f)
                    canvas.drawText(initial, cx, ty, textPaint)
                }
                canvas.restore()

                rimPaint.strokeWidth = LauncherUtils.dpF(ctx, 2.5f)
                rimPaint.color = lp.accentColor
                canvas.drawCircle(cx, cy, r, rimPaint)
            }
        }
        contentRow.addView(cropPreview, LinearLayout.LayoutParams(previewSize, previewSize).apply {
            rightMargin = dp(ctx, 20)
        })

        // Right: Name Input + Crop/Zoom/Position Controls
        val rightCol = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
        }

        val nameInput = EditText(ctx).apply {
            isSingleLine = true
            hint = "Enter Profile Name"
            setText(lp.userName)
            setTextColor(Color.WHITE)
            setHintTextColor(Color.argb(150, 200, 220, 245))
            setPadding(dp(ctx, 12), dp(ctx, 9), dp(ctx, 12), dp(ctx, 9))
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_WORDS
            background = GradientDrawable().apply {
                setColor(Color.argb(55, 255, 255, 255))
                cornerRadius = LauncherUtils.dpF(ctx, 12f)
                setStroke(dp(ctx, 1), lp.accentColor)
            }
        }
        rightCol.addView(nameInput, LinearLayout.LayoutParams(dp(ctx, 260), -2))

        // Choose Photo & Remove Row
        val photoRow = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL }
        photoRow.addView(makeGlossBtn(ctx, lp, "🖼  Choose Photo", true) {
            dialog.dismiss()
            onPickPhoto()
        }, LinearLayout.LayoutParams(0, -2, 1.4f).apply { rightMargin = dp(ctx, 6) })

        photoRow.addView(makeGlossBtn(ctx, lp, "✕ Remove", false) {
            rawBitmap = null
            zoom = 1.0f; panX = 0f; panY = 0f
            lp.prefs.edit().remove("profile_pic_uri").putFloat("profile_pic_zoom", 1f)
                .putFloat("profile_pic_pan_x", 0f).putFloat("profile_pic_pan_y", 0f).apply()
            cropPreview.invalidate()
            onSaved()
        }, LinearLayout.LayoutParams(0, -2, 1f))
        rightCol.addView(photoRow, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(ctx, 10) })

        // Zoom + / - / Reset Row
        val zoomRow = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL }
        zoomRow.addView(makeGlossBtn(ctx, lp, "🔍 +", false) {
            zoom = (zoom + 0.15f).coerceAtMost(2.8f)
            cropPreview.invalidate()
        }, LinearLayout.LayoutParams(0, -2, 1f).apply { rightMargin = dp(ctx, 5) })
        zoomRow.addView(makeGlossBtn(ctx, lp, "🔍 −", false) {
            zoom = (zoom - 0.15f).coerceAtLeast(1.0f)
            cropPreview.invalidate()
        }, LinearLayout.LayoutParams(0, -2, 1f).apply { rightMargin = dp(ctx, 5) })
        zoomRow.addView(makeGlossBtn(ctx, lp, "↺ Reset", false) {
            zoom = 1.0f; panX = 0f; panY = 0f
            cropPreview.invalidate()
        }, LinearLayout.LayoutParams(0, -2, 1f))
        rightCol.addView(zoomRow, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(ctx, 8) })

        // Pan / Position Arrows Row (Left, Right, Up, Down) for TV Remote D-Pad
        val panRow = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL }
        panRow.addView(makeGlossBtn(ctx, lp, "◀", false) {
            panX = (panX - 0.10f).coerceIn(-0.85f, 0.85f); cropPreview.invalidate()
        }, LinearLayout.LayoutParams(0, -2, 1f).apply { rightMargin = dp(ctx, 4) })
        panRow.addView(makeGlossBtn(ctx, lp, "▶", false) {
            panX = (panX + 0.10f).coerceIn(-0.85f, 0.85f); cropPreview.invalidate()
        }, LinearLayout.LayoutParams(0, -2, 1f).apply { rightMargin = dp(ctx, 4) })
        panRow.addView(makeGlossBtn(ctx, lp, "▲", false) {
            panY = (panY - 0.10f).coerceIn(-0.85f, 0.85f); cropPreview.invalidate()
        }, LinearLayout.LayoutParams(0, -2, 1f).apply { rightMargin = dp(ctx, 4) })
        panRow.addView(makeGlossBtn(ctx, lp, "▼", false) {
            panY = (panY + 0.10f).coerceIn(-0.85f, 0.85f); cropPreview.invalidate()
        }, LinearLayout.LayoutParams(0, -2, 1f))
        rightCol.addView(panRow, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(ctx, 8) })

        contentRow.addView(rightCol)
        rootGlass.addView(contentRow)

        // Bottom Save & Cancel Row
        val actionRow = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL }
        actionRow.addView(makeGlossBtn(ctx, lp, "Cancel", false) {
            dialog.dismiss()
        }, LinearLayout.LayoutParams(0, -2, 1f).apply { rightMargin = dp(ctx, 8) })

        actionRow.addView(makeGlossBtn(ctx, lp, "✓  Save Profile", true) {
            lp.userName = nameInput.text.toString().trim()
            lp.prefs.edit()
                .putFloat("profile_pic_zoom", zoom)
                .putFloat("profile_pic_pan_x", panX)
                .putFloat("profile_pic_pan_y", panY)
                .apply()
            onSaved()
            Toast.makeText(ctx, "Profile saved!", Toast.LENGTH_SHORT).show()
            dialog.dismiss()
        }, LinearLayout.LayoutParams(0, -2, 1.3f))
        rootGlass.addView(actionRow, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(ctx, 16) })
                dialog.setContentView(rootGlass)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.show()
    }

    private fun loadUserBitmap(ctx: Context, uriStr: String?): Bitmap? {
        if (uriStr.isNullOrEmpty()) return null
        return try {
            val opts = BitmapFactory.Options().apply { inSampleSize = 2 }
            ctx.contentResolver.openInputStream(Uri.parse(uriStr))?.use {
                BitmapFactory.decodeStream(it, null, opts)
            }
        } catch (_: Exception) { null }
    }

    private fun makeGlossBtn(ctx: Context, lp: LauncherPreferences, label: String, highlight: Boolean, onClick: () -> Unit) =
        TextView(ctx).apply {
            text = label
            textSize = 10.5f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            isFocusable = true
            isClickable = true
            setPadding(dp(ctx, 8), dp(ctx, 8), dp(ctx, 8), dp(ctx, 8))
            background = btnBg(ctx, lp.accentColor, highlight)
            setOnFocusChangeListener { v, f ->
                MotionEngine.animateFocus(v, f, lp.motionStyle, 1.04f, 4f)
                v.background = btnBg(ctx, lp.accentColor, f || highlight)
            }
            setOnClickListener { onClick() }
        }

    private fun btnBg(ctx: Context, accent: Int, active: Boolean) = GradientDrawable(
        GradientDrawable.Orientation.TOP_BOTTOM,
        if (active) intArrayOf(LauncherUtils.withAlpha(accent, 185), LauncherUtils.withAlpha(accent, 110))
        else intArrayOf(Color.argb(65, 255, 255, 255), Color.argb(30, 255, 255, 255))
    ).apply {
        cornerRadius = LauncherUtils.dpF(ctx, 12f)
        setStroke(dp(ctx, 1), if (active) Color.WHITE else Color.argb(80, 255, 255, 255))
    }

    private fun dp(ctx: Context, v: Int) = LauncherUtils.dp(ctx, v)
}
