package com.shreenivas.tvhome.panels

import android.animation.LayoutTransition
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.shreenivas.tvhome.LauncherPreferences
import com.shreenivas.tvhome.LauncherUtils
import com.shreenivas.tvhome.MotionEngine
import com.shreenivas.tvhome.ui.TileThemePalette

object CustomizeMenuBuilder {
    private var lastFocusedTag = ""
    private var activeCloseDrawerRef: (() -> Unit)? = null

    fun isSubMenuOpen(): Boolean = false

    fun closeSubMenuWithAnimation(onClosedCallback: (() -> Unit)? = null) {
        onClosedCallback?.invoke()
    }

    fun closeSubMenuAndNavigate(moveDown: Boolean) {}

    fun build(
        ctx: Context, panel: LinearLayout, lp: LauncherPreferences, onLiveRefresh: () -> Unit,
        onRebuildPanel: () -> Unit, onEditProfile: () -> Unit, onOpenWallpaper: () -> Unit,
        onOpenHiddenRibbon: () -> Unit, onBackToApps: () -> Unit
    ) {
        activeCloseDrawerRef = onBackToApps
        (panel.findFocus()?.tag as? String)?.let { lastFocusedTag = it }

        panel.clipChildren = false
        panel.clipToPadding = false
        panel.layoutTransition = LayoutTransition().apply {
            setDuration(220L)
            enableTransitionType(LayoutTransition.CHANGING)
            setInterpolator(LayoutTransition.CHANGING, LauncherUtils.FLUID_SPRING)
        }
        panel.removeAllViews()

        addHeader(ctx, panel, "ACCOUNT & VISIBILITY")
        val topRow = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            clipChildren = false; clipToPadding = false
        }
        val profCard = makeActionChip(ctx, "👤", lp.userName, "Edit Account & City", Color.rgb(68, 138, 255), lp.accentColor, "chip_profile") {
            lastFocusedTag = "chip_profile"; onEditProfile()
        }
        val hiddenCount = lp.getHiddenApps().size
        val hidSub = if (hiddenCount == 0) "None Hidden" else "$hiddenCount Hidden"
        val hidCard = makeActionChip(ctx, "👁", "Hidden Apps", hidSub, Color.rgb(255, 105, 130), lp.accentColor, "chip_hidden") {
            lastFocusedTag = "chip_hidden"
            if (hiddenCount == 0) {
                Toast.makeText(ctx, "No hidden apps right now", Toast.LENGTH_SHORT).show()
            } else {
                onBackToApps()
                onOpenHiddenRibbon()
            }
        }
        topRow.addView(profCard, LinearLayout.LayoutParams(0, dp(ctx, 50), 1f).apply { rightMargin = dp(ctx, 6) })
        topRow.addView(hidCard, LinearLayout.LayoutParams(0, dp(ctx, 50), 1f))
        panel.addView(topRow, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(ctx, 8) })

        addHeader(ctx, panel, "TILE SIZE")
        addSegmentedRow(
            ctx, panel, lp, "size",
            listOf("Small", "Medium", "Large"),
            listOf("small", "medium", "large"),
            lp.tileSize,
            onSelect = { v -> lp.tileSize = v; onLiveRefresh(); onRebuildPanel() }
        )

        addHeader(ctx, panel, "STAGE & PANEL WIDTH")
        addSegmentedRow(
            ctx, panel, lp, "width",
            listOf("20%", "25%", "30%", "35%", "40%"),
            listOf("20", "25", "30", "35", "40"),
            lp.panelWidthPct.toString(),
            onSelect = { v -> lp.panelWidthPct = v.toInt(); onLiveRefresh(); onRebuildPanel() }
        )

        addHeader(ctx, panel, "TILE SHAPE")
        addSegmentedRow(
            ctx, panel, lp, "shape",
            listOf("▢ Squircle", "◯ Circle", "♥ Heart", "▪ Classic"),
            listOf("squircle", "circle", "heart", "classic"),
            lp.tileShape,
            onSelect = { v -> lp.tileShape = v; onLiveRefresh(); onRebuildPanel() }
        )
                addHeader(ctx, panel, "GLOBAL MOTION STYLE (TAP TO PREVIEW)")
        val motionTopNames = MotionEngine.NAMES.take(4)
        val motionTopKeys = MotionEngine.KEYS.take(4)
        val motionBotNames = MotionEngine.NAMES.drop(4)
        val motionBotKeys = MotionEngine.KEYS.drop(4)

        val onMotionPicked: (String) -> Unit = { v ->
            lp.motionStyle = v
            onRebuildPanel()
            val drawerContainer = (panel.parent?.parent as? View) ?: panel
            drawerContainer.post { MotionEngine.playDemoPreview(drawerContainer, v) }
        }

        addSegmentedRow(ctx, panel, lp, "motion", motionTopNames, motionTopKeys, lp.motionStyle, isMotionRow = true, onSelect = onMotionPicked)
        if (motionBotNames.isNotEmpty()) {
            addSegmentedRow(ctx, panel, lp, "motion", motionBotNames, motionBotKeys, lp.motionStyle, isMotionRow = true, onSelect = onMotionPicked)
        }

        addHeader(ctx, panel, "ACCENT AURA")
        val accentKeys = LauncherPreferences.ACCENT_KEYS
        val accentLabels = LauncherPreferences.ACCENT_LABELS
        val accentRow = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            clipChildren = false; clipToPadding = false
        }
        accentKeys.forEachIndexed { idx, key ->
            val col = lp.getAccentColorValue(key)
            val isSel = lp.accentColor == col
            val tagStr = "acc_$key"
            val swatch = LinearLayout(ctx).apply {
                tag = tagStr
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setPadding(dp(ctx, 2), dp(ctx, 6), dp(ctx, 2), dp(ctx, 6))
                isFocusable = true
                background = pillBg(ctx, isSel, false, col)
                setOnFocusChangeListener { v, f ->
                    LauncherUtils.applyFocusSpring(v, f, 1.05f, 3f)
                    v.background = pillBg(ctx, isSel, f, col)
                }
                setOnClickListener {
                    lastFocusedTag = tagStr
                    lp.accentColor = col
                    lp.accentName = cap(key)
                    onLiveRefresh()
                    onRebuildPanel()
                }
            }
            val dot = View(ctx).apply {
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(col)
                    setStroke(dp(ctx, 1), Color.WHITE)
                }
            }
            swatch.addView(dot, LinearLayout.LayoutParams(dp(ctx, 14), dp(ctx, 14)).apply { bottomMargin = dp(ctx, 3) })
            swatch.addView(TextView(ctx).apply {
                text = accentLabels.getOrElse(idx) { cap(key) }; textSize = 8f; typeface = Typeface.DEFAULT_BOLD
                setTextColor(Color.WHITE); gravity = Gravity.CENTER; isSingleLine = true
            })
            accentRow.addView(swatch, LinearLayout.LayoutParams(0, dp(ctx, 44), 1f).apply {
                if (idx < accentKeys.lastIndex) rightMargin = dp(ctx, 4)
            })
        }
        panel.addView(accentRow, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(ctx, 8) })

        addHeader(ctx, panel, "LAUNCHER THEMES")
        val curTheme = lp.tileColor

        for (i in TileThemePalette.NAMES.indices step 2) {
            val row = LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL
                clipChildren = false; clipToPadding = false
            }
            for (j in i until minOf(i + 2, TileThemePalette.NAMES.size)) {
                val key = TileThemePalette.KEYS[j]
                val sel = key == curTheme
                val tagStr = "theme_$key"
                val gradColors = TileThemePalette.getColors(key, lp.accentColor).first
                val card = LinearLayout(ctx).apply {
                    tag = tagStr
                    orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
                    setPadding(dp(ctx, 9), dp(ctx, 6), dp(ctx, 9), dp(ctx, 6)); isFocusable = true
                    background = themeCardBg(ctx, sel, false, gradColors, lp.accentColor)
                    setOnFocusChangeListener { v, f ->
                        LauncherUtils.applyFocusSpring(v, f, 1.03f, 3f)
                        v.background = themeCardBg(ctx, sel, f, gradColors, lp.accentColor)
                    }
                    setOnClickListener {
                        lastFocusedTag = tagStr; lp.tileColor = key; onLiveRefresh(); onRebuildPanel()
                    }
                }
                val mid = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
                mid.addView(TextView(ctx).apply { text = TileThemePalette.NAMES[j]; textSize = 9.5f; typeface = Typeface.DEFAULT_BOLD; setTextColor(Color.WHITE); isSingleLine = true })
                mid.addView(TextView(ctx).apply { text = TileThemePalette.SUBTITLES[j]; textSize = 7.5f; setTextColor(Color.argb(200, 225, 240, 255)); isSingleLine = true })
                card.addView(mid, LinearLayout.LayoutParams(0, -2, 1f))
                if (sel) card.addView(TextView(ctx).apply { text = "✓"; textSize = 11f; typeface = Typeface.DEFAULT_BOLD; setTextColor(lp.accentColor) })

                row.addView(card, LinearLayout.LayoutParams(0, dp(ctx, 44), 1f).apply {
                    if (j == i) rightMargin = dp(ctx, 6)
                })
            }
            panel.addView(row, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(ctx, 6) })
        }
                addHeader(ctx, panel, "WALLPAPER & AMBIENT STAGE")
        val wpCard = makeActionChip(ctx, "🖼", "Wallpaper Studio", "Dynamic weather, time & gallery", Color.rgb(46, 196, 182), lp.accentColor, "card_wallpaper") {
            lastFocusedTag = "card_wallpaper"; onOpenWallpaper()
        }
        panel.addView(wpCard, LinearLayout.LayoutParams(-1, dp(ctx, 50)).apply { bottomMargin = dp(ctx, 10) })

        val backBtn = TextView(ctx).apply {
            tag = "btn_back_apps"; text = "✓  Done & Close"; textSize = 11f; typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE); gravity = Gravity.CENTER; isFocusable = true
            background = pillBg(ctx, false, true, lp.accentColor)
            setOnFocusChangeListener { v, f -> LauncherUtils.applyFocusSpring(v, f, 1.03f, 3f); v.background = pillBg(ctx, f, true, lp.accentColor) }
            setOnClickListener { activeCloseDrawerRef?.invoke() }
        }
        panel.addView(backBtn, LinearLayout.LayoutParams(-1, dp(ctx, 40)).apply { bottomMargin = dp(ctx, 32) })

        if (lastFocusedTag.isNotEmpty()) panel.findViewWithTag<View>(lastFocusedTag)?.requestFocus()
    }

    private fun addSegmentedRow(
        ctx: Context, panel: LinearLayout, lp: LauncherPreferences, groupKey: String,
        labels: List<String>, values: List<String>, currentVal: String,
        isMotionRow: Boolean = false, onSelect: (String) -> Unit
    ) {
        val bar = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            clipChildren = false; clipToPadding = false
            setPadding(dp(ctx, 3), dp(ctx, 3), dp(ctx, 3), dp(ctx, 3))
            background = GradientDrawable().apply {
                setColor(Color.argb(42, 10, 16, 38))
                cornerRadius = LauncherUtils.dpF(ctx, 12f)
                setStroke(dp(ctx, 1), Color.argb(55, 255, 255, 255))
            }
        }
        labels.forEachIndexed { idx, lbl ->
            val v = values[idx]
            val isSel = v == currentVal
            val tagStr = "seg_${groupKey}_$v"
            val pill = TextView(ctx).apply {
                tag = tagStr
                text = lbl
                textSize = if (labels.size >= 5) 8.2f else 8.8f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(Color.WHITE)
                gravity = Gravity.CENTER
                isSingleLine = true
                ellipsize = TextUtils.TruncateAt.END
                setPadding(dp(ctx, 2), dp(ctx, 7), dp(ctx, 2), dp(ctx, 7))
                isFocusable = true
                background = pillBg(ctx, isSel, false, lp.accentColor)
                setOnFocusChangeListener { vw, f ->
                    MotionEngine.animateFocus(vw, f, if (isMotionRow) v else lp.motionStyle, 1.04f, 3f)
                    vw.background = pillBg(ctx, isSel, f, lp.accentColor)
                }
                setOnClickListener {
                    lastFocusedTag = tagStr
                    onSelect(v)
                }
            }
            bar.addView(pill, LinearLayout.LayoutParams(0, -2, 1f).apply {
                if (idx < labels.lastIndex) rightMargin = dp(ctx, 3)
            })
        }
        panel.addView(bar, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(ctx, 7) })
    }

    private fun makeActionChip(
        ctx: Context, icon: String, title: String, sub: String,
        iconTint: Int, accent: Int, tagStr: String, onClick: () -> Unit
    ) = LinearLayout(ctx).apply {
        tag = tagStr
        orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(ctx, 8), dp(ctx, 6), dp(ctx, 8), dp(ctx, 6)); isFocusable = true
        background = pillBg(ctx, false, false, accent)
        setOnFocusChangeListener { v, f ->
            LauncherUtils.applyFocusSpring(v, f, 1.03f, 3f)
            v.background = pillBg(ctx, false, f, accent)
        }
        setOnClickListener { onClick() }

        val badge = TextView(ctx).apply {
            text = icon; textSize = 12f; gravity = Gravity.CENTER; setTextColor(Color.WHITE)
            background = GradientDrawable().apply {
                setColor(LauncherUtils.withAlpha(iconTint, 145))
                cornerRadius = LauncherUtils.dpF(ctx, 9f)
            }
        }
        addView(badge, LinearLayout.LayoutParams(dp(ctx, 28), dp(ctx, 28)).apply { rightMargin = dp(ctx, 7) })
        val mid = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
        mid.addView(TextView(ctx).apply { text = title; textSize = 9.5f; typeface = Typeface.DEFAULT_BOLD; setTextColor(Color.WHITE); isSingleLine = true; ellipsize = TextUtils.TruncateAt.END })
        mid.addView(TextView(ctx).apply { text = sub; textSize = 7.8f; setTextColor(Color.argb(195, 210, 230, 250)); isSingleLine = true; ellipsize = TextUtils.TruncateAt.END })
        addView(mid, LinearLayout.LayoutParams(0, -2, 1f))
    }

    private fun pillBg(ctx: Context, sel: Boolean, foc: Boolean, accent: Int) = GradientDrawable().apply {
        setColor(when {
            foc -> Color.argb(125, 255, 255, 255)
            sel -> LauncherUtils.withAlpha(accent, 130)
            else -> Color.argb(32, 255, 255, 255)
        })
        cornerRadius = LauncherUtils.dpF(ctx, 10f)
        setStroke(dp(ctx, if (foc || sel) 2 else 1), when {
            foc -> Color.WHITE
            sel -> accent
            else -> Color.argb(50, 255, 255, 255)
        })
    }

    private fun themeCardBg(ctx: Context, sel: Boolean, foc: Boolean, grad: IntArray, accent: Int) = GradientDrawable(
        GradientDrawable.Orientation.TL_BR, grad
    ).apply {
        cornerRadius = LauncherUtils.dpF(ctx, 12f)
        setStroke(dp(ctx, if (foc || sel) 2 else 1), when {
            foc -> Color.WHITE
            sel -> accent
            else -> Color.argb(65, 255, 255, 255)
        })
    }

    private fun addHeader(ctx: Context, panel: LinearLayout, txt: String) = panel.addView(
        TextView(ctx).apply {
            text = txt; textSize = 8.5f; typeface = Typeface.DEFAULT_BOLD; letterSpacing = 0.08f
            setTextColor(Color.argb(200, 195, 222, 250))
        },
        LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(ctx, 6); bottomMargin = dp(ctx, 4) }
    )

    private fun cap(s: String): String = s.replaceFirstChar { it.uppercase() }
    private fun dp(ctx: Context, v: Int): Int = LauncherUtils.dp(ctx, v)
}
