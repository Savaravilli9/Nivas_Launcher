package com.shreenivas.tvhome

import android.animation.ValueAnimator
import android.content.Context
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

object AppTileFactory {

    interface TileActions {
        fun onToggleFavorite(pkg: String, label: String)
        fun onMoveFavorite(pkg: String, delta: Int)
        fun onHideApp(pkg: String, label: String)
    }

    fun create(
        ctx: Context, pm: PackageManager, lp: LauncherPreferences,
        info: ResolveInfo, isFavRow: Boolean, isFav: Boolean,
        canMoveLeft: Boolean, canMoveRight: Boolean,
        w: Int, h: Int, actions: TileActions
    ): View {
        val pkg = info.activityInfo.packageName
        val label = info.loadLabel(pm).toString()
        val icon = info.loadIcon(pm)

        var breathingAnim: ValueAnimator? = null

        val wrapper = object : FrameLayout(ctx) {
            override fun onDetachedFromWindow() {
                super.onDetachedFromWindow()
                breathingAnim?.cancel()
                breathingAnim = null
            }
        }.apply {
            layoutParams = LinearLayout.LayoutParams(w, h)
            clipChildren = false
            clipToPadding = false
            isFocusable = true
            isClickable = true
            tag = if (isFavRow) "fav_$pkg" else "all_$pkg"
        }

        val safeMargin = LauncherUtils.dp(ctx, 8)
        val innerCard = GlowTileLayout(ctx).apply {
            isFocusable = false
            isClickable = false
            clipChildren = false
            setStyle(lp.accentColor, lp.tileShape, lp.tileColor)
        }

        val contentBox = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            isFocusable = false
            isClickable = false
        }

        val imgSize = when (lp.tileSize) {
            "large" -> dp(ctx, 52)
            "small" -> dp(ctx, 34)
            else -> dp(ctx, 42)
        }
        val iv = ImageView(ctx).apply {
            setImageDrawable(icon)
            layoutParams = LinearLayout.LayoutParams(imgSize, imgSize).apply {
                bottomMargin = dp(ctx, 4)
            }
        }
        contentBox.addView(iv)

        val tv = TextView(ctx).apply {
            text = label
            textSize = if (lp.columns > 6) 9f else 10.5f
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
            isSingleLine = true
            ellipsize = TextUtils.TruncateAt.END
            gravity = Gravity.CENTER
            setPadding(dp(ctx, 6), 0, dp(ctx, 6), 0)
        }
        contentBox.addView(tv)
        innerCard.addView(contentBox, FrameLayout.LayoutParams(-1, -1))

        val cardParams = FrameLayout.LayoutParams(-1, -1).apply {
            setMargins(safeMargin, safeMargin, safeMargin, safeMargin)
        }
        wrapper.addView(innerCard, cardParams)

        if (lp.badgeMode != "none" && isFav) {
            val badge = TextView(ctx).apply {
                text = when (lp.badgeMode) { "heart" -> "♥"; "star" -> "★"; else -> "●" }
                textSize = 9.5f
                setTextColor(if (lp.badgeMode == "blue") Color.CYAN else lp.accentColor)
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(Color.argb(220, 15, 20, 35))
                }
                gravity = Gravity.CENTER
            }
            wrapper.addView(badge, FrameLayout.LayoutParams(dp(ctx, 18), dp(ctx, 18), Gravity.TOP or Gravity.END).apply {
                topMargin = dp(ctx, 4); rightMargin = dp(ctx, 4)
            })
        }

        wrapper.setOnFocusChangeListener { _, hasFocus ->
            val targetScale = if (hasFocus) 1.14f else 1.0f

            innerCard.animate().cancel()
            innerCard.animate()
                .scaleX(targetScale).scaleY(targetScale)
                .setDuration(220L)
                .setInterpolator(LauncherUtils.FLUID_SPRING)
                .start()

            if (hasFocus) {
                innerCard.elevation = LauncherUtils.dpF(ctx, 14f)
                tv.isSelected = true
                breathingAnim?.cancel()
                breathingAnim = ValueAnimator.ofFloat(0.96f, 1.0f).apply {
                    duration = 850L
                    repeatMode = ValueAnimator.REVERSE
                    repeatCount = ValueAnimator.INFINITE
                    interpolator = AccelerateDecelerateInterpolator()
                    addUpdateListener {
                        innerCard.alpha = it.animatedValue as Float
                    }
                    start()
                }
            } else {
                tv.isSelected = false
                innerCard.elevation = 0f
                breathingAnim?.cancel()
                breathingAnim = null
                innerCard.alpha = 1.0f
            }
        }

        val openDialog = {
            AppActionDialog.show(
                ctx = ctx,
                pkg = pkg,
                appName = label,
                isFav = isFav,
                canMoveLeft = isFavRow && canMoveLeft,
                canMoveRight = isFavRow && canMoveRight,
                accent = lp.accentColor,
                onToggleFav = { actions.onToggleFavorite(pkg, label) },
                onMoveFav = { delta -> actions.onMoveFavorite(pkg, delta) },
                onHideApp = { actions.onHideApp(pkg, label) }
            )
        }

        wrapper.setOnClickListener {
            try {
                val launchIntent = pm.getLaunchIntentForPackage(pkg)
                if (launchIntent != null) ctx.startActivity(launchIntent)
                else Toast.makeText(ctx, "App cannot be launched", Toast.LENGTH_SHORT).show()
            } catch (_: Exception) {
                Toast.makeText(ctx, "App not found", Toast.LENGTH_SHORT).show()
            }
        }

        wrapper.setOnKeyListener { _, keyCode, event ->
            if (event.action == KeyEvent.ACTION_DOWN && (keyCode == KeyEvent.KEYCODE_MENU || (keyCode == KeyEvent.KEYCODE_DPAD_CENTER && event.isLongPress))) {
                openDialog()
                true
            } else false
        }

        wrapper.setOnLongClickListener {
            openDialog()
            true
        }

        return wrapper
    }

    private fun dp(ctx: Context, v: Int) = LauncherUtils.dp(ctx, v)
}
