package com.shreenivas.tvhome.homescreen

import com.shreenivas.tvhome.MotionEngine

import android.animation.ValueAnimator
import android.content.Context
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.Drawable
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
import com.shreenivas.tvhome.LauncherPreferences
import com.shreenivas.tvhome.LauncherUtils
import java.util.concurrent.ConcurrentHashMap

object AppTileFactory {

    private val iconCache = ConcurrentHashMap<String, Drawable>()
    private val labelCache = ConcurrentHashMap<String, String>()
    private var lastFocusedX = -1f
    private var lastFocusedY = -1f

    fun clearAssetCache() {
        iconCache.clear()
        labelCache.clear()
    }

    interface TileActions {
        fun onToggleFavorite(pkg: String, label: String)
        fun onMoveFavorite(pkg: String, delta: Int)
        fun onHideApp(pkg: String, label: String)
    }

    fun getCachedLabel(pm: PackageManager, info: ResolveInfo): String {
        val pkg = info.activityInfo.packageName
        return labelCache.getOrPut(pkg) { info.loadLabel(pm).toString() }
    }

    private fun getCachedIcon(pm: PackageManager, info: ResolveInfo): Drawable {
        val pkg = info.activityInfo.packageName
        return iconCache.getOrPut(pkg) { info.loadIcon(pm) }
    }

    fun create(
        ctx: Context, pm: PackageManager, lp: LauncherPreferences,
        info: ResolveInfo, isFavRow: Boolean, isFav: Boolean,
        canMoveLeft: Boolean, canMoveRight: Boolean,
        w: Int, h: Int, actions: TileActions
    ): View {
        val pkg = info.activityInfo.packageName
        val label = getCachedLabel(pm, info)
        val icon = getCachedIcon(pm, info)

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
            tag = "glow_card"
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

        val imgSize = getIconSizePx(ctx, lp.tileSize)
        val iv = ImageView(ctx).apply {
            tag = "tile_icon"
            setImageDrawable(icon)
            layoutParams = LinearLayout.LayoutParams(imgSize, imgSize).apply {
                bottomMargin = dp(ctx, 4)
            }
        }
        contentBox.addView(iv)

        val tv = TextView(ctx).apply {
            tag = "tile_label"
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
        val badge = TextView(ctx).apply {
            tag = "tile_badge"
            textSize = 9.5f
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.argb(220, 15, 20, 35))
            }
            gravity = Gravity.CENTER
        }
        wrapper.addView(badge, FrameLayout.LayoutParams(dp(ctx, 18), dp(ctx, 18), Gravity.TOP or Gravity.END).apply {
            topMargin = dp(ctx, 4); rightMargin = dp(ctx, 4)
        })
        applyBadgeState(badge, lp, isFav)

        wrapper.setOnFocusChangeListener { v, hasFocus ->
            val dx = if (lastFocusedX >= 0f) (v.x - lastFocusedX).coerceIn(-1f, 1f) else 0f
            val dy = if (lastFocusedY >= 0f) (v.y - lastFocusedY).coerceIn(-1f, 1f) else 0f
            if (hasFocus) {
                lastFocusedX = v.x
                lastFocusedY = v.y
            }

            MotionEngine.animateFocus(innerCard, hasFocus, lp.motionStyle, 1.13f, 16f, dx, dy)

            if (hasFocus) {
                innerCard.elevation = LauncherUtils.dpF(ctx, 16f)
                tv.isSelected = true
                breathingAnim?.cancel()
                breathingAnim = ValueAnimator.ofFloat(0f, 1f).apply {
                    duration = 1100L
                    repeatMode = ValueAnimator.REVERSE
                    repeatCount = ValueAnimator.INFINITE
                    interpolator = AccelerateDecelerateInterpolator()
                    addUpdateListener { anim ->
                        val f = anim.animatedValue as Float
                        val pulseScale = 1.12f + (0.045f * f)
                        innerCard.scaleX = pulseScale
                        innerCard.scaleY = pulseScale
                        innerCard.alpha = 0.88f + (0.12f * f)
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
                isHiddenMode = false,
                onToggleFav = { actions.onToggleFavorite(pkg, label) },
                onMoveFav = { delta -> actions.onMoveFavorite(pkg, delta) },
                onHideOrUnhideApp = { actions.onHideApp(pkg, label) }
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

    fun updateTileAppearance(ctx: Context, wrapper: View, lp: LauncherPreferences, isFav: Boolean) {
        wrapper.findViewWithTag<GlowTileLayout>("glow_card")?.setStyle(lp.accentColor, lp.tileShape, lp.tileColor)
        wrapper.findViewWithTag<ImageView>("tile_icon")?.let { iv ->
            val size = getIconSizePx(ctx, lp.tileSize)
            if (iv.layoutParams.width != size) {
                iv.layoutParams.width = size
                iv.layoutParams.height = size
                iv.requestLayout()
            }
        }
        wrapper.findViewWithTag<TextView>("tile_label")?.textSize = if (lp.columns > 6) 9f else 10.5f
        wrapper.findViewWithTag<TextView>("tile_badge")?.let { applyBadgeState(it, lp, isFav) }
    }

    private fun applyBadgeState(badge: TextView, lp: LauncherPreferences, isFav: Boolean) {
        if (isFav && lp.badgeMode != "none") {
            badge.visibility = View.VISIBLE
            badge.text = when (lp.badgeMode) { "heart" -> "♥"; "star" -> "★"; else -> "●" }
            badge.setTextColor(if (lp.badgeMode == "blue") Color.CYAN else lp.accentColor)
        } else {
            badge.visibility = View.GONE
        }
    }

    private fun getIconSizePx(ctx: Context, tileSize: String): Int = when (tileSize) {
        "large" -> dp(ctx, 52)
        "small" -> dp(ctx, 34)
        else -> dp(ctx, 42)
    }

    private fun dp(ctx: Context, v: Int) = LauncherUtils.dp(ctx, v)
}
