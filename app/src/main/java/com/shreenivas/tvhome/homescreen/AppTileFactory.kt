package com.shreenivas.tvhome.homescreen

import android.animation.ValueAnimator
import android.content.Context
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.os.Build
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

    private val fullBleedCache = ConcurrentHashMap<String, Bitmap>()
    private val labelCache = ConcurrentHashMap<String, String>()
    private val colorCache = ConcurrentHashMap<String, Int>()
    private var lastFocusedX = -1f
    private var lastFocusedY = -1f

    fun clearAssetCache() {
        fullBleedCache.clear()
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

    private fun getFullBleedBitmap(pm: PackageManager, info: ResolveInfo): Bitmap {
        val pkg = info.activityInfo.packageName
        return fullBleedCache.getOrPut(pkg) {
            renderFullBleedIcon(info.loadIcon(pm))
        }
    }

    private fun renderFullBleedIcon(drawable: Drawable): Bitmap {
        val size = 144
        val out = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && drawable is AdaptiveIconDrawable) {
            // Adaptive icons have a 108dp canvas with an 18dp safe-zone inset on each side.
            // Expanding bounds by 22% crops out the rounded-square mask so the true background fills 100% edge-to-edge!
            val inset = (size * 0.22f).toInt()
            drawable.background?.let { bg ->
                bg.setBounds(-inset, -inset, size + inset, size + inset)
                bg.draw(canvas)
            }
            drawable.foreground?.let { fg ->
                val fgInset = (size * 0.14f).toInt()
                fg.setBounds(-fgInset, -fgInset, size + fgInset, size + fgInset)
                fg.draw(canvas)
            }
        } else {
            drawable.setBounds(0, 0, size, size)
            drawable.draw(canvas)
        }
        return out
    }

    private fun getAppDominantColor(pkg: String, bmp: Bitmap, fallback: Int): Int {
        return colorCache.getOrPut(pkg) {
            extractVibrantColor(bmp, fallback)
        }
    }

    private fun extractVibrantColor(bmp: Bitmap, fallback: Int): Int {
        return try {
            var bestColor = fallback
            var maxScore = -1f
            val step = maxOf(1, bmp.width / 16)

            for (x in step until bmp.width - step step step) {
                for (y in step until bmp.height - step step step) {
                    val px = bmp.getPixel(x, y)
                    if (Color.alpha(px) < 180) continue
                    val hsv = FloatArray(3)
                    Color.colorToHSV(px, hsv)
                    val sat = hsv.get(1)
                    val valBright = hsv.get(2)
                    if (valBright < 0.25f || (sat < 0.18f && valBright > 0.85f)) continue
                    val score = (sat * 2.2f) + valBright
                    if (score > maxScore) {
                        maxScore = score
                        bestColor = px
                    }
                }
            }
            val outHsv = FloatArray(3)
            Color.colorToHSV(bestColor, outHsv)
            outHsv.set(1, maxOf(outHsv.get(1), 0.55f))
            outHsv.set(2, maxOf(outHsv.get(2), 0.88f))
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
        val fullBmp = getFullBleedBitmap(pm, info)
        val appColor = getAppDominantColor(pkg, fullBmp, lp.accentColor)

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

        // Top: Large Double-Shaped Glossy Icon (Outer Theme Palette + Gap + Edge-to-Edge Cropped App Icon)
        val iconBadgeSize = getIconBadgeSizePx(ctx, lp.tileSize)
        val innerCard = GlowTileLayout(ctx).apply {
            tag = "glow_card"
            isFocusable = false
            isClickable = false
            clipChildren = false
            setStyle(lp.accentColor, lp.tileShape, lp.tileColor, appColor)
        }

        // Zero padding so the icon fills 100% of the inner Heart / Circle / Squircle shape!
        val iv = ImageView(ctx).apply {
            tag = "tile_icon"
            setImageBitmap(fullBmp)
            scaleType = ImageView.ScaleType.CENTER_CROP
        }
        innerCard.addView(iv, FrameLayout.LayoutParams(-1, -1))
        unitContainer.addView(innerCard, LinearLayout.LayoutParams(iconBadgeSize, iconBadgeSize))

        // Bottom: Rounded Rectangle Glossy Nameplate that the Icon sits right on top of
        val namePlate = FrameLayout(ctx).apply {
            tag = "name_plate"
            outlineProvider = null
            setPadding(dp(ctx, 8), dp(ctx, 3), dp(ctx, 8), dp(ctx, 3))
            background = nameplateBg(ctx, false, appColor)
        }
        val tv = TextView(ctx).apply {
            tag = "tile_label"
            text = label
            textSize = if (lp.columns > 6) 9f else 10.2f
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
            topMargin = dp(ctx, 3)
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
        wrapper.findViewWithTag<TextView>("tile_label")?.textSize = if (lp.columns > 6) 9f else 10.2f
        wrapper.findViewWithTag<TextView>("tile_badge")?.let { applyBadgeState(it, lp, isFav) }
    }

    private fun nameplateBg(ctx: Context, focused: Boolean, accentColor: Int) = GradientDrawable(
        GradientDrawable.Orientation.LEFT_RIGHT,
        if (focused) intArrayOf(LauncherUtils.withAlpha(accentColor, 225), LauncherUtils.withAlpha(accentColor, 145))
        else intArrayOf(LauncherUtils.withAlpha(accentColor, 115), Color.argb(125, 18, 26, 54))
    ).apply {
        cornerRadius = LauncherUtils.dpF(ctx, 10f)
        setStroke(dp(ctx, 1), if (focused) Color.WHITE else LauncherUtils.withAlpha(accentColor, 185))
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
        "large" -> dp(ctx, 94)
        "small" -> dp(ctx, 64)
        else -> dp(ctx, 78)
    }

    private fun dp(ctx: Context, v: Int) = LauncherUtils.dp(ctx, v)
}
