package com.shreenivas.tvhome.panels

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
import com.shreenivas.tvhome.LauncherPreferences
import com.shreenivas.tvhome.LauncherUtils
import com.shreenivas.tvhome.ui.LauncherBackground

object WallpaperEditorPanel {

    fun build(
        ctx: Context,
        panel: LinearLayout,
        lp: LauncherPreferences,
        onLiveUpdate: () -> Unit,
        onPickNewImage: () -> Unit,
        onDone: () -> Unit
    ) {
        val hasCustomImage = lp.prefs.getString(LauncherPreferences.PREF_WALLPAPER, null) != null
        val curMode = lp.liveWallpaperMode

        addSection(ctx, panel, "SMART DYNAMIC MODES")
        val autoRow = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL }
        autoRow.addView(
            makeVisualCard(
                ctx, lp, "✨", "Smart Auto", "Time + Weather",
                intArrayOf(Color.rgb(52, 128, 225), Color.rgb(155, 72, 185)),
                !hasCustomImage && curMode == LauncherBackground.MODE_SMART
            ) {
                lp.prefs.edit().remove(LauncherPreferences.PREF_WALLPAPER).apply()
                lp.liveWallpaperMode = LauncherBackground.MODE_SMART
                onLiveUpdate()
                rebuild(ctx, panel, lp, onLiveUpdate, onPickNewImage, onDone)
            },
            LinearLayout.LayoutParams(0, dp(ctx, 56), 1f).apply { rightMargin = dp(ctx, 7) }
        )
        autoRow.addView(
            makeVisualCard(
                ctx, lp, "🕒", "Time Flow", "Day to Night",
                intArrayOf(Color.rgb(235, 138, 72), Color.rgb(42, 62, 135)),
                !hasCustomImage && curMode == LauncherBackground.MODE_AUTO_TIME
            ) {
                lp.prefs.edit().remove(LauncherPreferences.PREF_WALLPAPER).apply()
                lp.liveWallpaperMode = LauncherBackground.MODE_AUTO_TIME
                onLiveUpdate()
                rebuild(ctx, panel, lp, onLiveUpdate, onPickNewImage, onDone)
            },
            LinearLayout.LayoutParams(0, dp(ctx, 56), 1f)
        )
        panel.addView(autoRow, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(ctx, 8) })

        addSection(ctx, panel, "LIVE CANVAS SCENES")
        val sceneSwatches = listOf(
            intArrayOf(Color.rgb(125, 195, 255), Color.rgb(245, 198, 165)),
            intArrayOf(Color.rgb(185, 165, 245), Color.rgb(155, 225, 205)),
            intArrayOf(Color.rgb(195, 68, 118), Color.rgb(255, 152, 78)),
            intArrayOf(Color.rgb(12, 115, 152), Color.rgb(38, 192, 172)),
            intArrayOf(Color.rgb(24, 58, 92), Color.rgb(65, 135, 175)),
            intArrayOf(Color.rgb(16, 24, 54), Color.rgb(48, 64, 125))
        )
        val sceneIcons = listOf("☀️", "🌸", "🌇", "🌊", "🌧️", "🌙")

        for (i in LauncherBackground.MANUAL_NAMES.indices step 2) {
            val row = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL }
            for (j in i until minOf(i + 2, LauncherBackground.MANUAL_NAMES.size)) {
                val key = LauncherBackground.MANUAL_KEYS[j]
                val sel = !hasCustomImage && curMode == key
                val card = makeVisualCard(
                    ctx, lp, sceneIcons[j], LauncherBackground.MANUAL_NAMES[j],
                    if (sel) "Active" else "Tap to set", sceneSwatches[j], sel
                ) {
                    lp.prefs.edit().remove(LauncherPreferences.PREF_WALLPAPER).apply()
                    lp.liveWallpaperMode = key
                    onLiveUpdate()
                    rebuild(ctx, panel, lp, onLiveUpdate, onPickNewImage, onDone)
                }
                val p = LinearLayout.LayoutParams(0, dp(ctx, 52), 1f).apply {
                    if (j == i) rightMargin = dp(ctx, 7)
                }
                row.addView(card, p)
            }
            panel.addView(row, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(ctx, 7) })
        }

        addSection(ctx, panel, "CUSTOM GALLERY PHOTO")
        val pickCard = makeVisualCard(
            ctx, lp, "🖼", "Choose Photo from Storage",
            if (hasCustomImage) "Custom photo active • Tap to change" else "Select 4K / HD image",
            intArrayOf(Color.argb(85, 255, 255, 255), Color.argb(38, 255, 255, 255)),
            hasCustomImage
        ) { onPickNewImage() }
        panel.addView(pickCard, LinearLayout.LayoutParams(-1, dp(ctx, 52)).apply { bottomMargin = dp(ctx, 8) })
                if (hasCustomImage) {
            val auraOn = lp.photoWeatherAura
            val auraCard = makeVisualCard(
                ctx, lp, if (auraOn) "🌦️" else "🚫",
                "Subtle Corner Weather Aura",
                if (auraOn) "Enabled • Soft edge glow (Tap to turn Off)" else "Disabled • Pure photo (Tap to turn On)",
                intArrayOf(Color.argb(80, 68, 165, 235), Color.argb(40, 32, 52, 95)),
                auraOn
            ) {
                lp.photoWeatherAura = !auraOn
                onLiveUpdate()
                rebuild(ctx, panel, lp, onLiveUpdate, onPickNewImage, onDone)
            }
            panel.addView(auraCard, LinearLayout.LayoutParams(-1, dp(ctx, 52)).apply { bottomMargin = dp(ctx, 8) })

            addSection(ctx, panel, "PHOTO FIT, ZOOM & POSITION")
            addPillRow(ctx, panel, lp, listOf("Fit", "Fill W", "Fill H")) { idx ->
                val modes = listOf("fit", "width", "height")
                lp.prefs.edit().putString("wp_scale_mode", modes[idx]).apply()
                onLiveUpdate()
            }
            addPillRow(ctx, panel, lp, listOf("Zoom -", "Reset", "Zoom +")) { idx ->
                val sp = lp.prefs
                var z = sp.getFloat("wp_zoom", 1.0f)
                z = when (idx) { 0 -> maxOf(1.0f, z - 0.1f); 1 -> 1.08f; else -> minOf(2.2f, z + 0.1f) }
                sp.edit().putFloat("wp_zoom", z).apply()
                onLiveUpdate()
            }
            addPillRow(ctx, panel, lp, listOf("⬅", "⬆", "⬇", "➡")) { idx ->
                val sp = lp.prefs
                var ox = sp.getInt("wp_offset_x", 0); var oy = sp.getInt("wp_offset_y", 0)
                val step = dp(ctx, 24)
                when (idx) { 0 -> ox -= step; 1 -> oy -= step; 2 -> oy += step; else -> ox += step }
                sp.edit().putInt("wp_offset_x", ox).putInt("wp_offset_y", oy).apply()
                onLiveUpdate()
            }
        }

        val donePill = TextView(ctx).apply {
            text = "✓  Apply & Back"; textSize = 11f; typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE); gravity = Gravity.CENTER; isFocusable = true
            background = pillBg(ctx, false, true, lp.accentColor)
            setOnFocusChangeListener { v, f -> LauncherUtils.applyFocusSpring(v, f, 1.03f, 3f); v.background = pillBg(ctx, f, true, lp.accentColor) }
            setOnClickListener { onDone() }
        }
        panel.addView(donePill, LinearLayout.LayoutParams(-1, dp(ctx, 42)).apply { topMargin = dp(ctx, 10); bottomMargin = dp(ctx, 24) })
    }

    private fun rebuild(ctx: Context, panel: LinearLayout, lp: LauncherPreferences, onLive: () -> Unit, onPick: () -> Unit, onDone: () -> Unit) {
        panel.removeAllViews()
        build(ctx, panel, lp, onLive, onPick, onDone)
    }

    private fun makeVisualCard(
        ctx: Context, lp: LauncherPreferences, icon: String, title: String, sub: String,
        gradColors: IntArray, selected: Boolean, onClick: () -> Unit
    ) = LinearLayout(ctx).apply {
        orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(ctx, 10), dp(ctx, 6), dp(ctx, 10), dp(ctx, 6)); isFocusable = true
        background = cardBg(ctx, false, selected, gradColors, lp.accentColor)
        setOnFocusChangeListener { v, f ->
            LauncherUtils.applyFocusSpring(v, f, 1.03f, 4f)
            v.background = cardBg(ctx, f, selected, gradColors, lp.accentColor)
        }
        setOnClickListener { onClick() }

        addView(TextView(ctx).apply { text = icon; textSize = 15f; setPadding(0, 0, dp(ctx, 8), 0) })
        val mid = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
        mid.addView(TextView(ctx).apply { text = title; textSize = 9.5f; typeface = Typeface.DEFAULT_BOLD; setTextColor(Color.WHITE); isSingleLine = true })
        mid.addView(TextView(ctx).apply { text = sub; textSize = 7.8f; setTextColor(Color.argb(210, 235, 245, 255)); isSingleLine = true })
        addView(mid, LinearLayout.LayoutParams(0, -2, 1f))
        if (selected) addView(TextView(ctx).apply { text = "✓"; textSize = 12f; typeface = Typeface.DEFAULT_BOLD; setTextColor(Color.WHITE) })
    }

    private fun addPillRow(ctx: Context, panel: LinearLayout, lp: LauncherPreferences, labels: List<String>, onClick: (Int) -> Unit) {
        val row = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL }
        labels.forEachIndexed { i, txt ->
            val pill = TextView(ctx).apply {
                text = txt; textSize = 9.5f; typeface = Typeface.DEFAULT_BOLD
                setTextColor(Color.WHITE); gravity = Gravity.CENTER; isFocusable = true
                background = pillBg(ctx, false, false, lp.accentColor)
                setOnFocusChangeListener { v, f -> LauncherUtils.applyFocusSpring(v, f, 1.04f, 3f); v.background = pillBg(ctx, f, false, lp.accentColor) }
                setOnClickListener { onClick(i) }
            }
            row.addView(pill, LinearLayout.LayoutParams(0, dp(ctx, 34), 1f).apply { if (i < labels.lastIndex) rightMargin = dp(ctx, 5) })
        }
        panel.addView(row, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(ctx, 6) })
    }

    private fun cardBg(ctx: Context, focused: Boolean, selected: Boolean, colors: IntArray, accent: Int) = GradientDrawable(
        GradientDrawable.Orientation.TL_BR, colors
    ).apply {
        cornerRadius = LauncherUtils.dpF(ctx, 14f)
        setStroke(dp(ctx, if (focused || selected) 2 else 1), when {
            focused -> Color.WHITE
            selected -> accent
            else -> Color.argb(85, 255, 255, 255)
        })
    }

    private fun pillBg(ctx: Context, focused: Boolean, selected: Boolean, accent: Int) = GradientDrawable().apply {
        setColor(when { focused -> Color.argb(120, 255, 255, 255); selected -> Color.argb(85, 92, 168, 255); else -> Color.argb(45, 255, 255, 255) })
        cornerRadius = LauncherUtils.dpF(ctx, 12f)
        setStroke(dp(ctx, if (focused || selected) 2 else 1), if (focused) Color.WHITE else accent)
    }

    private fun addSection(ctx: Context, panel: LinearLayout, label: String) {
        val t = TextView(ctx).apply {
            text = label; textSize = 8.5f; typeface = Typeface.DEFAULT_BOLD; letterSpacing = 0.08f
            setTextColor(Color.rgb(110, 215, 255))
        }
        panel.addView(t, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(ctx, 8); bottomMargin = dp(ctx, 5) })
    }

    private fun dp(ctx: Context, v: Int) = LauncherUtils.dp(ctx, v)
}
