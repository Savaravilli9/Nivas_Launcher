package com.shreenivas.tvhome

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
        ctx: Context,
        pm: PackageManager,
        lp: LauncherPreferences,
        info: ResolveInfo,
        isFavSection: Boolean,
        isFavorite: Boolean,
        tileWidth: Int,
        tileHeight: Int,
        actions: TileActions
    ): View {
        val pkg = info.activityInfo.packageName
        val label = info.loadLabel(pm).toString()
        val shapeMode = lp.tileShape
        val themeKey = lp.tileColor
        val badgeMode = lp.badgeMode

        val tile = GlowTileLayout(ctx).apply {
            tag = if (isFavSection) "fav_$pkg" else "all_$pkg"
            setPadding(dp(ctx, 10), dp(ctx, 8), dp(ctx, 10), dp(ctx, 8))
            isFocusable = true
            isClickable = true
            background = buildTileDrawable(ctx, shapeMode, themeKey, false, lp.accentColor, tileHeight)
        }

        val innerBox = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
        }
        tile.addView(innerBox, FrameLayout.LayoutParams(-1, -1))

        val iconWrap = FrameLayout(ctx)
        val iconPx = (tileHeight * if (shapeMode == "circle" || shapeMode == "heart") 0.36f else 0.42f).toInt()
        val icon = ImageView(ctx).apply {
            setImageDrawable(info.loadIcon(pm))
            scaleType = ImageView.ScaleType.FIT_CENTER
        }
        iconWrap.addView(icon, FrameLayout.LayoutParams(iconPx, iconPx, Gravity.CENTER))

        val badgeInfo = resolveBadgeVector(badgeMode, isFavorite)
        if (badgeInfo != null) {
            val badgeSize = dp(ctx, 20)
            val badgeContainer = FrameLayout(ctx).apply {
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(Color.argb(190, 14, 22, 52))
                    setStroke(dp(ctx, 1), badgeInfo.second)
                }
            }
            badgeContainer.addView(
                VectorIconView(ctx, badgeInfo.first, badgeInfo.second),
                FrameLayout.LayoutParams(dp(ctx, 11), dp(ctx, 11), Gravity.CENTER)
            )
            iconWrap.addView(
                badgeContainer,
                FrameLayout.LayoutParams(badgeSize, badgeSize, Gravity.TOP or Gravity.END).apply {
                    topMargin = dp(ctx, 2)
                    rightMargin = dp(ctx, 2)
                }
            )
        }
        innerBox.addView(iconWrap, LinearLayout.LayoutParams(-1, 0, 1f))

        val title = TextView(ctx).apply {
            text = label
            textSize = if (shapeMode == "circle" || shapeMode == "heart") 11f else 12.5f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            isSingleLine = true
            ellipsize = TextUtils.TruncateAt.END
            setShadowLayer(4f, 0f, 1.5f, Color.argb(175, 0, 0, 0))
            setPadding(dp(ctx, 4), dp(ctx, 4), dp(ctx, 4), dp(ctx, 2))
        }
        innerBox.addView(title, LinearLayout.LayoutParams(-1, -2))

        tile.setOnFocusChangeListener { v, focused ->
            LauncherUtils.applyFocusSpring(v, focused, 1.08f, 12f)
            v.background = buildTileDrawable(ctx, shapeMode, themeKey, focused, lp.accentColor, tileHeight)
        }

        tile.setOnClickListener {
            val launch = pm.getLaunchIntentForPackage(pkg)
            if (launch != null) {
                ctx.startActivity(launch)
            } else {
                Toast.makeText(ctx, "Cannot open $label", Toast.LENGTH_SHORT).show()
            }
        }

        tile.setOnLongClickListener {
            AppActionDialog.show(
                ctx,
                label,
                pkg,
                isFavSection,
                isFavorite,
                true,
                true,
                lp.accentColor,
                { actions.onToggleFavorite(pkg, label) },
                { delta -> actions.onMoveFavorite(pkg, delta) },
                { actions.onHideApp(pkg, label) }
            )
            true
        }

        tile.setOnKeyListener { _, keyCode, event ->
            if (event.action == KeyEvent.ACTION_DOWN && keyCode == KeyEvent.KEYCODE_MENU) {
                tile.performLongClick()
                true
            } else {
                false
            }
        }

        return tile
    }

    private fun resolveBadgeVector(badgeMode: String, isFav: Boolean): Pair<String, Int>? {
        return when (badgeMode) {
            "none" -> null
            "heart" -> "heart" to if (isFav) Color.rgb(255, 85, 130) else Color.argb(130, 255, 255, 255)
            "blue" -> "sparkle" to if (isFav) Color.rgb(75, 195, 255) else Color.argb(130, 255, 255, 255)
            else -> "sparkle" to if (isFav) Color.rgb(255, 215, 65) else Color.argb(130, 255, 255, 255)
        }
    }

    private fun buildTileDrawable(
        ctx: Context,
        shape: String,
        themeKey: String,
        focused: Boolean,
        accentColor: Int,
        tileHeight: Int
    ): GradientDrawable {
        val colors = TileThemePalette.getTopBottomColors(themeKey, focused)
        return GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, colors).apply {
            cornerRadius = when (shape) {
                "circle", "heart" -> tileHeight / 2f
                "classic" -> LauncherUtils.dpF(ctx, 12f)
                else -> LauncherUtils.dpF(ctx, 22f)
            }
            setStroke(
                dp(ctx, if (focused) 2 else 1),
                if (focused) accentColor else Color.argb(95, 255, 255, 255)
            )
        }
    }

    private fun dp(ctx: Context, value: Int) = LauncherUtils.dp(ctx, value)
}
