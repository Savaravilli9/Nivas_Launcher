package com.shreenivas.tvhome

import android.animation.LayoutTransition
import android.content.Context
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import java.util.Locale

object AppGridManager {

    private var cachedApps: List<ResolveInfo>? = null

    fun clearCache() { cachedApps = null }

    fun createHeader(
        ctx: Context, title: String, isFav: Boolean, lp: LauncherPreferences? = null, onSortChanged: (() -> Unit)? = null
    ): LinearLayout {
        return LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(ctx, 2), dp(ctx, 4), dp(ctx, 2), dp(ctx, 8))

            addView(View(ctx).apply {
                background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(if (isFav) Color.rgb(255, 85, 130) else Color.rgb(88, 175, 255)) }
            }, LinearLayout.LayoutParams(dp(ctx, 8), dp(ctx, 8)).apply { rightMargin = dp(ctx, 8) })

            addView(TextView(ctx).apply {
                text = title; textSize = 11.5f; typeface = Typeface.DEFAULT_BOLD
                letterSpacing = 0.08f; setTextColor(Color.argb(235, 225, 238, 255))
            })

            addView(View(ctx), LinearLayout.LayoutParams(0, 1, 1f))

            if (!isFav && lp != null && onSortChanged != null) {
                val sortPill = TextView(ctx).apply {
                    text = if (lp.sortOrder == "za") "⇅  Z-A ORDER" else "⇅  A-Z ORDER"
                    textSize = 10f; typeface = Typeface.DEFAULT_BOLD; setTextColor(Color.WHITE)
                    setPadding(dp(ctx, 11), dp(ctx, 4), dp(ctx, 11), dp(ctx, 4)); isFocusable = true; background = badgeBg(ctx, false)
                    setOnFocusChangeListener { v, f -> LauncherUtils.applyFocusSpring(v, f, 1.05f, 4f); v.background = badgeBg(ctx, f) }
                    setOnClickListener {
                        lp.sortOrder = if (lp.sortOrder == "za") "az" else "za"
                        text = if (lp.sortOrder == "za") "⇅  Z-A ORDER" else "⇅  A-Z ORDER"
                        onSortChanged()
                    }
                }
                addView(sortPill, LinearLayout.LayoutParams(-2, -2).apply { rightMargin = dp(ctx, 8) })
            }

            addView(TextView(ctx).apply {
                tag = "count_tv"
                text = "0 Apps"; textSize = 10f; typeface = Typeface.DEFAULT_BOLD
                setTextColor(Color.argb(220, 215, 235, 255)); setPadding(dp(ctx, 11), dp(ctx, 4), dp(ctx, 11), dp(ctx, 4))
                background = badgeBg(ctx, false)
            })
        }
    }

    fun populate(
        ctx: Context, lp: LauncherPreferences, favSection: LinearLayout, favRow: LinearLayout,
        favCount: TextView, appGrid: LinearLayout, allCount: TextView,
        shouldRequestFocus: Boolean, restorePkg: String?, isDrawerOpen: Boolean,
        actions: AppTileFactory.TileActions
    ) {
        val pm = ctx.packageManager
        val visibleApps = getInstalledApps(pm).filter { !lp.getHiddenApps().contains(it.activityInfo.packageName) }
        val byPkg = visibleApps.associateBy { it.activityInfo.packageName }
        val favOrder = lp.getOrderedFavorites()
        val favApps = favOrder.mapNotNull { byPkg[it] }

        val sortedAll = if (lp.sortOrder == "za") visibleApps.sortedByDescending { it.loadLabel(pm).toString().lowercase(Locale.ROOT) }
        else visibleApps.sortedBy { it.loadLabel(pm).toString().lowercase(Locale.ROOT) }

        // Layout Transitions for Butter Smooth Column Rewrapping
        val transition = LayoutTransition().apply {
            setDuration(220L)
            enableTransitionType(LayoutTransition.CHANGING)
            setInterpolator(LayoutTransition.CHANGING, LauncherUtils.FLUID_SPRING)
        }
        appGrid.layoutTransition = transition
        favRow.layoutTransition = transition

        favRow.removeAllViews(); appGrid.removeAllViews()

        // PERF Math: Prevent tiny dots and calculate exact wraps
        val screenW = ctx.resources.displayMetrics.widthPixels
        val sidePad = dp(ctx, 64)
        val drawerW = if (isDrawerOpen) maxOf(dp(ctx, 230), (screenW * (lp.panelWidthPct / 100f)).toInt()) else 0
        val availableW = screenW - sidePad - drawerW

        val gap = dp(ctx, 12)
        val minTileW = dp(ctx, 95) // Prevents shrinking to dots!
        
        var targetCols = lp.columns
        if (isDrawerOpen) {
            val maxFittingCols = (availableW + gap) / (minTileW + gap)
            targetCols = minOf(lp.columns, maxOf(3, maxFittingCols))
        }

        val totalGapW = gap * (targetCols - 1)
        val tileW = ((availableW - totalGapW) / targetCols).coerceAtLeast(minTileW)
        
        val heightFactor = when (lp.tileSize) { "small" -> 0.72f; "large" -> 0.94f; else -> 0.82f }
        val tileH = (tileW * heightFactor).toInt()

        var focusTarget: View? = null

        if (favApps.isEmpty()) favSection.visibility = View.GONE
        else {
            favSection.visibility = View.VISIBLE
            favCount.text = "${favApps.size} Apps"
            favApps.forEachIndexed { idx, info ->
                val pkg = info.activityInfo.packageName
                val tile = AppTileFactory.create(ctx, pm, lp, info, true, true, tileW, tileH, actions)
                if (restorePkg == pkg || (restorePkg == null && idx == 0)) focusTarget = tile
                favRow.addView(tile, LinearLayout.LayoutParams(tileW, tileH).apply { if (idx < favApps.lastIndex) rightMargin = gap })
            }
        }

        allCount.text = "${sortedAll.size} Apps"
        var rowLayout: LinearLayout? = null
        val favSet = favOrder.toSet()

        sortedAll.forEachIndexed { idx, info ->
            if (idx % targetCols == 0) {
                rowLayout = LinearLayout(ctx).apply { 
                    orientation = LinearLayout.HORIZONTAL; clipChildren = false
                    layoutTransition = transition 
                }
                appGrid.addView(rowLayout, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = gap })
            }
            val pkg = info.activityInfo.packageName
            val tile = AppTileFactory.create(ctx, pm, lp, info, false, favSet.contains(pkg), tileW, tileH, actions)
            if (focusTarget == null && (restorePkg == pkg || (restorePkg == null && idx == 0))) focusTarget = tile
            
            rowLayout?.addView(tile, LinearLayout.LayoutParams(tileW, tileH).apply {
                if ((idx % targetCols) < targetCols - 1) rightMargin = gap
            })
        }

        if (shouldRequestFocus) focusTarget?.post { focusTarget?.requestFocus() }
    }

    private fun getInstalledApps(pm: PackageManager): List<ResolveInfo> {
        cachedApps?.let { return it }
        val map = LinkedHashMap<String, ResolveInfo>()
        LauncherUtils.addApps(pm, "android.intent.category.LEANBACK_LAUNCHER", map)
        LauncherUtils.addApps(pm, "android.intent.category.LAUNCHER", map)
        val result = map.values.toList()
        cachedApps = result; return result
    }

    private fun badgeBg(ctx: Context, focused: Boolean) = GradientDrawable().apply {
        setColor(if (focused) Color.argb(95, 255, 255, 255) else Color.argb(48, 22, 34, 74))
        setStroke(dp(ctx, 1), Color.argb(if (focused) 180 else 80, 255, 255, 255))
        cornerRadius = LauncherUtils.dpF(ctx, 14f)
    }
    private fun dp(ctx: Context, v: Int) = LauncherUtils.dp(ctx, v)
}
