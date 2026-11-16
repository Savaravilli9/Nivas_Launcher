package com.shreenivas.tvhome

import android.animation.ValueAnimator
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
    private var lastAppSignature = ""
    private var lastFavSignature = ""

    fun clearCache() { cachedApps = null; lastAppSignature = ""; lastFavSignature = "" }

    fun createHeader(ctx: Context, title: String, isFav: Boolean, lp: LauncherPreferences? = null, onSortChanged: (() -> Unit)? = null): LinearLayout {
        return LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(ctx, 2), dp(ctx, 4), dp(ctx, 2), dp(ctx, 8))
            addView(View(ctx).apply { background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(if (isFav) Color.rgb(255, 85, 130) else Color.rgb(88, 175, 255)) } }, LinearLayout.LayoutParams(dp(ctx, 8), dp(ctx, 8)).apply { rightMargin = dp(ctx, 8) })
            addView(TextView(ctx).apply { text = title; textSize = 11.5f; typeface = Typeface.DEFAULT_BOLD; letterSpacing = 0.08f; setTextColor(Color.argb(235, 225, 238, 255)) })
            addView(View(ctx), LinearLayout.LayoutParams(0, 1, 1f))
            if (!isFav && lp != null && onSortChanged != null) {
                val sortPill = TextView(ctx).apply {
                    text = if (lp.sortOrder == "za") "⇅  Z-A ORDER" else "⇅  A-Z ORDER"
                    textSize = 10f; typeface = Typeface.DEFAULT_BOLD; setTextColor(Color.WHITE)
                    setPadding(dp(ctx, 11), dp(ctx, 4), dp(ctx, 11), dp(ctx, 4)); isFocusable = true; background = badgeBg(ctx, false)
                    setOnFocusChangeListener { v, f -> LauncherUtils.applyFocusSpring(v, f, 1.05f, 4f); v.background = badgeBg(ctx, f) }
                    setOnClickListener { lp.sortOrder = if (lp.sortOrder == "za") "az" else "za"; text = if (lp.sortOrder == "za") "⇅  Z-A ORDER" else "⇅  A-Z ORDER"; onSortChanged() }
                }
                addView(sortPill, LinearLayout.LayoutParams(-2, -2).apply { rightMargin = dp(ctx, 8) })
            }
            addView(TextView(ctx).apply { tag = "count_tv"; text = "0 Apps"; textSize = 10f; typeface = Typeface.DEFAULT_BOLD; setTextColor(Color.argb(220, 215, 235, 255)); setPadding(dp(ctx, 11), dp(ctx, 4), dp(ctx, 11), dp(ctx, 4)); background = badgeBg(ctx, false) })
        }
    }

    fun populate(
        ctx: Context, lp: LauncherPreferences, favSection: LinearLayout, favRow: LinearLayout, favCount: TextView,
        appGrid: LinearLayout, allCount: TextView, shouldRequestFocus: Boolean, restorePkg: String?, isDrawerOpen: Boolean, actions: AppTileFactory.TileActions
    ) {
        val pm = ctx.packageManager
        val visibleApps = getInstalledApps(pm).filter { !lp.getHiddenApps().contains(it.activityInfo.packageName) }
        val byPkg = visibleApps.associateBy { it.activityInfo.packageName }
        val favOrder = lp.getOrderedFavorites()
        val favApps = favOrder.mapNotNull { byPkg[it] }

        val sortedAll = if (lp.sortOrder == "za") visibleApps.sortedByDescending { it.loadLabel(pm).toString().lowercase(Locale.ROOT) } else visibleApps.sortedBy { it.loadLabel(pm).toString().lowercase(Locale.ROOT) }

        val screenW = ctx.resources.displayMetrics.widthPixels
        val sidePad = dp(ctx, 64)
        val gap = dp(ctx, 12)
        val drawerW = if (isDrawerOpen) maxOf(dp(ctx, 230), (screenW * (lp.panelWidthPct / 100f)).toInt()) else 0
        val currentAvailableW = screenW - sidePad - drawerW

        val baseAvailableW = screenW - sidePad
        val baseCellW = (baseAvailableW - (gap * (lp.columns - 1))) / lp.columns
        val scale = when (lp.tileSize) { "small" -> 0.75f; "large" -> 0.96f; else -> 0.85f }
        val tileW = (baseCellW * scale).toInt()
        val tileH = (tileW * 0.85f).toInt()
        
        var targetCols = lp.columns
        if (isDrawerOpen) {
             val maxFittingCols = (currentAvailableW + gap) / (baseCellW + gap)
             targetCols = minOf(lp.columns, maxOf(3, maxFittingCols))
        }
        val sideMargin = maxOf(0, ((currentAvailableW - (gap * (targetCols - 1))) / targetCols - tileW) / 2)

        val currentFavSig = favApps.joinToString { it.activityInfo.packageName }
        if (favApps.isEmpty()) favSection.visibility = View.GONE
        else {
            favSection.visibility = View.VISIBLE
            favCount.text = "${favApps.size} Apps"
            if (currentFavSig != lastFavSignature) {
                favRow.removeAllViews()
                favApps.forEach { info -> favRow.addView(AppTileFactory.create(ctx, pm, lp, info, true, true, tileW, tileH, actions), LinearLayout.LayoutParams(tileW, tileH).apply { leftMargin = sideMargin; rightMargin = sideMargin }) }
                lastFavSignature = currentFavSig
            } else {
                for (i in 0 until favRow.childCount) {
                    val child = favRow.getChildAt(i)
                    animateLayoutParams(child, tileW, tileH, sideMargin, sideMargin)
                }
            }
        }

        allCount.text = "${sortedAll.size} Apps"
        val currentAppSig = sortedAll.joinToString { it.activityInfo.packageName }
        var fluidGrid = appGrid.findViewWithTag<FluidAppGrid>("fluid_engine")

        if (fluidGrid == null || currentAppSig != lastAppSignature) {
            appGrid.removeAllViews()
            fluidGrid = FluidAppGrid(ctx).apply { tag = "fluid_engine" }
            appGrid.addView(fluidGrid, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { leftMargin = sideMargin; rightMargin = sideMargin })
            val favSet = favOrder.toSet()
            sortedAll.forEach { info -> fluidGrid.addView(AppTileFactory.create(ctx, pm, lp, info, false, favSet.contains(info.activityInfo.packageName), tileW, tileH, actions)) }
            lastAppSignature = currentAppSig
            fluidGrid.updateGridState(targetCols, tileW, tileH, gap, animate = false)
        } else {
            // THE MAGIC: Just animate existing tiles elastically!
            (fluidGrid.layoutParams as? LinearLayout.LayoutParams)?.let { p ->
                ValueAnimator.ofInt(p.leftMargin, sideMargin).apply {
                    duration = 320L; interpolator = LauncherUtils.FLUID_SPRING
                    addUpdateListener { p.leftMargin = it.animatedValue as Int; p.rightMargin = it.animatedValue as Int; fluidGrid.requestLayout() }; start()
                }
            }
            fluidGrid.updateGridState(targetCols, tileW, tileH, gap, animate = true)
        }

        if (shouldRequestFocus) {
            val restoreView = fluidGrid.findViewWithTag<View>("all_$restorePkg") ?: fluidGrid.getChildAt(0)
            restoreView?.post { restoreView.requestFocus() }
        }
    }

    private fun animateLayoutParams(view: View, w: Int, h: Int, lMargin: Int, rMargin: Int) {
        val p = view.layoutParams as? LinearLayout.LayoutParams ?: return
        val startW = p.width; val startH = p.height; val startL = p.leftMargin; val startR = p.rightMargin
        if (startW == w && startL == lMargin) return
        ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 320L; interpolator = LauncherUtils.FLUID_SPRING
            addUpdateListener {
                val f = it.animatedValue as Float
                p.width = (startW + (w - startW) * f).toInt(); p.height = (startH + (h - startH) * f).toInt()
                p.leftMargin = (startL + (lMargin - startL) * f).toInt(); p.rightMargin = (startR + (rMargin - startR) * f).toInt()
                view.requestLayout()
            }; start()
        }
    }

    private fun getInstalledApps(pm: PackageManager): List<ResolveInfo> {
        cachedApps?.let { return it }; val map = LinkedHashMap<String, ResolveInfo>()
        LauncherUtils.addApps(pm, "android.intent.category.LEANBACK_LAUNCHER", map); LauncherUtils.addApps(pm, "android.intent.category.LAUNCHER", map)
        val result = map.values.toList(); cachedApps = result; return result
    }

    private fun badgeBg(ctx: Context, focused: Boolean) = GradientDrawable().apply { setColor(if (focused) Color.argb(95, 255, 255, 255) else Color.argb(48, 22, 34, 74)); setStroke(dp(ctx, 1), Color.argb(if (focused) 180 else 80, 255, 255, 255)); cornerRadius = LauncherUtils.dpF(ctx, 14f) }
    private fun dp(ctx: Context, v: Int) = LauncherUtils.dp(ctx, v)
}
