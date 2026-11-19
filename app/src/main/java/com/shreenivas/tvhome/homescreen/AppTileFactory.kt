package com.shreenivas.tvhome.homescreen

import android.animation.ValueAnimator
import android.content.Context
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.graphics.Bitmap
import android.graphics.Canvas
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
import com.shreenivas.tvhome.MotionEngine
import java.util.concurrent.ConcurrentHashMap

object AppTileFactory {

    private val iconCache = ConcurrentHashMap<String, Drawable>()
    private val labelCache = ConcurrentHashMap<String, String>()
    private val colorCache = ConcurrentHashMap<String, Int>()
    private var lastFocusedX = -1f
    private var lastFocusedY = -1f

    fun clearAssetCache() {
        iconCache.clear()
        labelCache.clear()
        colorCache.clear()
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

    private fun getAppDominantColor(pkg: String, drawable: Drawable, fallback: Int): Int {
        return colorCache.getOrPut(pkg) {
            extractVibrantColor(drawable, fallback)
        }
    }

    private fun extractVibrantColor(drawable: Drawable, fallback: Int): Int {
        return try {
            val bmp = Bitmap.createBitmap(24, 24, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bmp)
            drawable.setBounds(0, 0, 24, 24)
            drawable.draw(canvas)

            var bestColor = fallback
            var maxScore = -1f

            for (x in 2 until 22 step 2) {
                for (y in 2 until 22 step 2) {
                    val px = bmp.getPixel(x, y)
                    if (Color.alpha(px) < 160) continue
                    val hsv = FloatArray(3)
                    Color.colorToHSV(px, hsv)
                    val sat = hsv[1]
                    val valBright = hsv[2]
                    if (valBright < 0.25f || (sat < 0.18f && valBright > 0.85f)) continue
                    val score = (sat * 2.2f) + valBright
                    if (score > maxScore) {
                        maxScore = score
                        bestColor = px
                    }
                }
            }
            bmp.recycle()
            val outHsv = FloatArray(3)
            Color.colorToHSV(bestColor, outHsv)
            outHsv[1] = maxOf(outHsv[1], 0.55f)
            outHsv[2] = maxOf(outHsv[2], 0.88f)
            Color.HSVToColor(255, outHsv)
        } catch (_: Exception) {
            fallback
        }
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
        val appColor = getAppDominantColor(pkg, icon, lp.accentColor)

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

        val unitContainer = LinearLayout(ctx).apply {
            tag = "unit_container"
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            clipChildren = false
            clipToPadding = false
        }

        // Top: Double-Shaped Glossy Icon (Outer Theme Shape + Gap + Inner Cropped Icon)
        val iconBadgeSize = getIconBadgeSizePx(ctx, lp.tileSize)
        val innerCard = GlowTileLayout(ctx).apply {
            tag = "glow_card"
            isFocusable = false
            isClickable = false
            clipChildren = false
            setStyle(lp.accentColor, lp.tileShape, lp.tileColor, appColor)
        }

        val iv = ImageView(ctx).apply {
            tag = "tile_icon"
            setImageDrawable(icon)
            scaleType = ImageView.ScaleType.CENTER_CROP
            val pad = dp(ctx, 5)
            setPadding(pad, pad, pad, pad)
        }
        innerCard.addView(iv, FrameLayout.LayoutParams(-1, -1))
        unitContainer.addView(innerCard, LinearLayout.LayoutParams(iconBadgeSize, iconBadgeSize))
        // Bottom: Rounded Rectangle Glossy Nameplate that the Icon sits right above
        val namePlate = FrameLayout(ctx).apply {
            tag = "name_plate"
            outlineProvider = null
            setPadding(dp(ctx, 8), dp(ctx, 3), dp(ctx, 8), dp(ctx, 3))
            background = nameplateBg(ctx, false, appColor)
        }
        val tv = TextView(ctx).apply {
            tag = "tile_label"
            text = label
            textSize = if (lp.columns > 6) 8.8f else 10f
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
            isSingleLine = true
            ellipsize = TextUtils.TruncateAt.END
            gravity = Gravity.CENTER
        }
        namePlate.addView(tv, FrameLayout.LayoutParams(-1, -2, Gravity.CENTER))
        unitContainer.addView(namePlate, LinearLayout.LayoutParams(-1, -2).apply {
            leftMargin = dp(ctx, 6)
            rightMargin = dp(ctx, 6)
            topMargin = dp(ctx, 4)
        })

        wrapper.addView(unitContainer, FrameLayout.LayoutParams(-1, -1, Gravity.CENTER))

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
            topMargin = dp(ctx, 2); rightMargin = dp(ctx, 8)
        })
        applyBadgeState(badge, lp, isFav)

        wrapper.setOnFocusChangeListener { v, hasFocus ->
            val dx = if (lastFocusedX >= 0f) (v.x - lastFocusedX).coerceIn(-1f, 1f) else 0f
            val dy = if (lastFocusedY >= 0f) (v.y - lastFocusedY).coerceIn(-1f, 1f) else 0f
            if (hasFocus) {
                lastFocusedX = v.x
                lastFocusedY = v.y
            }

            innerCard.setFocusState(hasFocus)
            namePlate.background = nameplateBg(ctx, hasFocus, appColor)
            MotionEngine.animateFocus(unitContainer, hasFocus, lp.motionStyle, 1.11f, 12f, dx, dy)

            if (hasFocus) {
                tv.isSelected = true
                breathingAnim?.cancel()
                breathingAnim = ValueAnimator.ofFloat(0f, 1f).apply {
                    duration = 1150L
                    repeatMode = ValueAnimator.REVERSE
                    repeatCount = ValueAnimator.INFINITE
                    interpolator = AccelerateDecelerateInterpolator()
                    addUpdateListener { anim ->
                        val f = anim.animatedValue as Float
                        val pulseScale = 1.10f + (0.04f * f)
                        unitContainer.scaleX = pulseScale
                        unitContainer.scaleY = pulseScale
                        innerCard.updateGlowPulse(f)
                    }
                    start()
                }
            } else {
                tv.isSelected = false
                breathingAnim?.cancel()
                breathingAnim = null
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
        val rawTag = (wrapper.tag as? String)?.removePrefix("fav_")?.removePrefix("all_") ?: ""
        val appCol = colorCache[rawTag] ?: lp.accentColor
        wrapper.findViewWithTag<GlowTileLayout>("glow_card")?.let { card ->
            val badgeSize = getIconBadgeSizePx(ctx, lp.tileSize)
            if (card.layoutParams.width != badgeSize) {
                card.layoutParams.width = badgeSize
                card.layoutParams.height = badgeSize
                card.requestLayout()
            }
            card.setStyle(lp.accentColor, lp.tileShape, lp.tileColor, appCol)
        }
        wrapper.findViewWithTag<TextView>("tile_label")?.textSize = if (lp.columns > 6) 8.8f else 10f
        wrapper.findViewWithTag<TextView>("tile_badge")?.let { applyBadgeState(it, lp, isFav) }
    }

    private fun nameplateBg(ctx: Context, focused: Boolean, appColor: Int) = GradientDrawable(
        GradientDrawable.Orientation.TOP_BOTTOM,
        if (focused) intArrayOf(LauncherUtils.withAlpha(appColor, 145), Color.argb(165, 18, 26, 52))
        else intArrayOf(Color.argb(95, 255, 255, 255), Color.argb(55, 18, 26, 52))
    ).apply {
        cornerRadius = LauncherUtils.dpF(ctx, 10f)
        setStroke(dp(ctx, if (focused) 1 else 1), if (focused) appColor else Color.argb(85, 255, 255, 255))
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

    private fun getIconBadgeSizePx(ctx: Context, tileSize: String): Int = when (tileSize) {
        "large" -> dp(ctx, 68)
        "small" -> dp(ctx, 48)
        else -> dp(ctx, 58)
    }

    private fun dp(ctx: Context, v: Int) = LauncherUtils.dp(ctx, v)
}
