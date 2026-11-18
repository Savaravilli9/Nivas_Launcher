package com.shreenivas.tvhome.panels

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import com.shreenivas.tvhome.LauncherPreferences
import com.shreenivas.tvhome.LauncherUtils
import com.shreenivas.tvhome.ui.LauncherBackground

object WallpaperEditorPanel {
    private var lastFocusedTag = ""

    fun build(
        ctx: Context,
        panel: LinearLayout,
        lp: LauncherPreferences,
        onLiveUpdate: () -> Unit,
        onPickNewImage: () -> Unit,
        onDone: () -> Unit
    ) {
        panel.clipChildren = false
        panel.clipToPadding = false
        val hasCustomImage = lp.prefs.getString(LauncherPreferences.PREF_WALLPAPER, null) != null
        val curMode = lp.liveWallpaperMode

        addSection(ctx, panel, "SMART DYNAMIC MODES")
        val autoRow = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            clipChildren = false
            clipToPadding = false
        }
        autoRow.addView(
            makeVisualCard(
                ctx, lp, "wp_smart", "✨", "Smart Auto", "Time + Weather",
                intArrayOf(Color.rgb(52, 128, 225), Color.rgb(155, 72, 185)),
                !hasCustomImage && curMode == LauncherBackground.MODE_SMART
            ) {
                lastFocusedTag = "wp_smart"
                lp.prefs.edit().remove(LauncherPreferences.PREF_WALLPAPER).apply()
                lp.liveWallpaperMode = LauncherBackground.MODE_SMART
                onLiveUpdate()
                rebuild(ctx, panel, lp, onLiveUpdate, onPickNewImage, onDone)
            },
            LinearLayout.LayoutParams(0, dp(ctx, 54), 1f).apply { rightMargin = dp(ctx, 7) }
        )
        autoRow.addView(
            makeVisualCard(
                ctx, lp, "wp_auto", "🕒", "Time Flow", "Day to Night",
                intArrayOf(Color.rgb(235, 138, 72), Color.rgb(42, 62, 135)),
                !hasCustomImage && curMode == LauncherBackground.MODE_AUTO_TIME
            ) {
                lastFocusedTag = "wp_auto"
                lp.prefs.edit().remove(LauncherPreferences.PREF_WALLPAPER).apply()
                lp.liveWallpaperMode = LauncherBackground.MODE_AUTO_TIME
                onLiveUpdate()
                rebuild(ctx, panel, lp, onLiveUpdate, onPickNewImage, onDone)
            },
            LinearLayout.LayoutParams(0, dp(ctx, 54), 1f)
        )
        panel.addView(autoRow, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(ctx, 8) })

        addSection(ctx, panel, "LIVE CANVAS SCENES")
        val defaultSwatch = intArrayOf(Color.rgb(42, 68, 128), Color.rgb(22, 34, 72))

        for (i in LauncherBackground.MANUAL_NAMES.indices step 2) {
            val row = LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL
                clipChildren = false
                clipToPadding = false
            }
            for (j in i until minOf(i + 2, LauncherBackground.MANUAL_NAMES.size)) {
                val key = LauncherBackground.MANUAL_KEYS[j]
                val icon = LauncherBackground.MANUAL_ICONS.getOrElse(j) { "🎨" }
                val swatch = LauncherBackground.MANUAL_SWATCHES.getOrElse(j) { defaultSwatch }
                val tagStr = "wp_$key"
                val sel = !hasCustomImage && curMode == key
                val card = makeVisualCard(
                    ctx, lp, tagStr, icon, LauncherBackground.MANUAL_NAMES[j],
                    if (sel) "Active" else "Tap to set", swatch, sel
                ) {
                    lastFocusedTag = tagStr
                    lp.prefs.edit().remove(LauncherPreferences.PREF_WALLPAPER).apply()
                    lp.liveWallpaperMode = key
                    onLiveUpdate()
                    rebuild(ctx, panel, lp, onLiveUpdate, onPickNewImage, onDone)
                }
                val p = LinearLayout.LayoutParams(0, dp(ctx, 50), 1f).apply {
                    if (j == i) rightMargin = dp(ctx, 7)
                }
                row.addView(card, p)
            }
            panel.addView(row, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(ctx, 7) })
        }

        addSection(ctx, panel, "CUSTOM GALLERY PHOTO")
        val pickCard = makeVisualCard(
            ctx, lp, "wp_pick", "🖼", "Choose Photo from Storage",
            if (hasCustomImage) "Custom photo active • Tap to change" else "Select 4K / HD image",
            intArrayOf(Color.argb(85, 255, 255, 255), Color.argb(38, 255, 255, 255)),
            hasCustomImage
        ) {
            lastFocusedTag = "wp_pick"
            onPickNewImage()
        }
        panel.addView(pickCard, LinearLayout.LayoutParams(-1, dp(ctx, 50)).apply { bottomMargin = dp(ctx, 8) })
        if (hasCustomImage) {
            val auraOn = lp.photoWeatherAura
            val auraCard = makeVisualCard(
                ctx, lp, "wp_aura", if (auraOn) "🌦️" else "🚫",
                "Subtle Corner Weather Aura",
                if (auraOn) "Enabled • Soft edge glow (Tap to Off)" else "Disabled • Pure photo (Tap to On)",
                intArrayOf(Color.argb(80, 68, 165, 235), Color.argb(40, 32, 52, 95)),
                auraOn
            ) {
                lastFocusedTag = "wp_aura"
                lp.photoWeatherAura = !auraOn
                onLiveUpdate()
                rebuild(ctx, panel, lp, onLiveUpdate, onPickNewImage, onDone)
            }
            panel.addView(auraCard, LinearLayout.LayoutParams(-1, dp(ctx, 50)).apply { bottomMargin = dp(ctx, 8) })

            addSection(ctx, panel, "PHOTO FIT & ZOOM")
            val curScaleMode = lp.prefs.getString("wp_scale_mode", "fit") ?: "fit"
            addPillRow(ctx, panel, lp, "fit", listOf("Fit Screen", "Fill Screen"), listOf(curScaleMode == "fit", curScaleMode == "width")) { idx ->
                val modeVal = if (idx == 0) "fit" else "width"
                lp.prefs.edit().putString("wp_scale_mode", modeVal).apply()
                onLiveUpdate()
                rebuild(ctx, panel, lp, onLiveUpdate, onPickNewImage, onDone)
            }
            addPillRow(ctx, panel, lp, "zoom", listOf("Zoom -", "Reset", "Zoom +"), listOf(false, false, false)) { idx ->
                val sp = lp.prefs
                var z = sp.getFloat("wp_zoom", 1.0f)
                z = when (idx) { 0 -> maxOf(1.0f, z - 0.1f); 1 -> 1.08f; else -> minOf(2.2f, z + 0.1f) }
                sp.edit().putFloat("wp_zoom", z).apply()
                onLiveUpdate()
            }
            addPillRow(ctx, panel, lp, "pos", listOf("⬅", "⬆", "⬇", "➡"), listOf(false, false, false, false)) { idx ->
                val sp = lp.prefs
                var ox = sp.getInt("wp_offset_x", 0); var oy = sp.getInt("wp_offset_y", 0)
                val step = dp(ctx, 24)
                when (idx) { 0 -> ox -= step; 1 -> oy -= step; 2 -> oy += step; else -> ox += step }
                sp.edit().putInt("wp_offset_x", ox).putInt("wp_offset_y", oy).apply()
                onLiveUpdate()
            }
        }

        val donePill = TextView(ctx).apply {
            tag = "wp_done"
            text = "✓  Apply & Back"; textSize = 11f; typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE); gravity = Gravity.CENTER; isFocusable = true
            background = pillBg(ctx, false, true, lp.accentColor)
            setOnFocusChangeListener { v, f -> LauncherUtils.applyFocusSpring(v, f, 1.02f, 3f); v.background = pillBg(ctx, f, true, lp.accentColor) }
            setOnClickListener { lastFocusedTag = ""; onDone() }
        }
        panel.addView(donePill, LinearLayout.LayoutParams(-1, dp(ctx, 42)).apply { topMargin = dp(ctx, 10); bottomMargin = dp(ctx, 36) })

        if (lastFocusedTag.isNotEmpty()) {
            panel.post { panel.findViewWithTag<View>(lastFocusedTag)?.requestFocus() }
        }
    }

    private fun rebuild(ctx: Context, panel: LinearLayout, lp: LauncherPreferences, onLive: () -> Unit, onPick: () -> Unit, onDone: () -> Unit) {
        panel.removeAllViews()
        build(ctx, panel, lp, onLive, onPick, onDone)
    }

    private fun makeVisualCard(
        ctx: Context, lp: LauncherPreferences, tagStr: String, icon: String, title: String, sub: String,
        gradColors: IntArray, selected: Boolean, onClick: () -> Unit
    ) = LinearLayout(ctx).apply {
        tag = tagStr
        orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(ctx, 10), dp(ctx, 6), dp(ctx, 10), dp(ctx, 6)); isFocusable = true
        background = cardBg(ctx, false, selected, gradColors, lp.accentColor)
        setOnFocusChangeListener { v, f ->
            LauncherUtils.applyFocusSpring(v, f, 1.02f, 4f)
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

    private fun addPillRow(
        ctx: Context, panel: LinearLayout, lp: LauncherPreferences, prefix: String,
        labels: List<String>, selectedFlags: List<Boolean>, onClick: (Int) -> Unit
    ) {
        val row = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            clipChildren = false
            clipToPadding = false
        }
        labels.forEachIndexed { i, txt ->
            val tagStr = "wp_${prefix}_$i"
            val sel = selectedFlags.getOrElse(i) { false }
            val pill = TextView(ctx).apply {
                tag = tagStr
                text = txt; textSize = 9.5f; typeface = Typeface.DEFAULT_BOLD
                setTextColor(Color.WHITE); gravity = Gravity.CENTER; isFocusable = true
                background = pillBg(ctx, false, sel, lp.accentColor)
                setOnFocusChangeListener { v, f -> LauncherUtils.applyFocusSpring(v, f, 1.03f, 3f); v.background = pillBg(ctx, f, sel, lp.accentColor) }
                setOnClickListener { lastFocusedTag = tagStr; onClick(i) }
            }
            row.addView(pill, LinearLayout.LayoutParams(0, dp(ctx, 36), 1f).apply { if (i < labels.lastIndex) rightMargin = dp(ctx, 6) })
        }
        panel.addView(row, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(ctx, 7) })
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
        setColor(when { focused -> Color.argb(120, 255, 255, 255); selected -> LauncherUtils.withAlpha(accent, 130); else -> Color.argb(45, 255, 255, 255) })
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
