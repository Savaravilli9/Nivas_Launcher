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
import com.shreenivas.tvhome.MotionEngine
import com.shreenivas.tvhome.ui.TileThemePalette
import java.util.Locale

object CustomizeMenuBuilder {

    private var activeSubMenuRow: LinearLayout? = null
    private var activeAnchorButton: View? = null

    fun isSubMenuOpen(): Boolean = activeSubMenuRow != null

    fun closeSubMenuWithAnimation() {
        val row = activeSubMenuRow ?: return
        val anchor = activeAnchorButton
        activeSubMenuRow = null
        activeAnchorButton = null
        row.animate().alpha(0f).scaleY(0.85f).setDuration(180L).withEndAction {
            (row.parent as? LinearLayout)?.removeView(row)
            anchor?.requestFocus()
        }.start()
    }

    fun closeSubMenuAndNavigate(moveDown: Boolean) {
        val row = activeSubMenuRow ?: return
        val anchor = activeAnchorButton
        activeSubMenuRow = null
        activeAnchorButton = null
        (row.parent as? LinearLayout)?.removeView(row)
        anchor?.requestFocus()
    }

    fun build(
        ctx: Context,
        panel: LinearLayout,
        lp: LauncherPreferences,
        onLiveRefresh: () -> Unit,
        onRefreshPanel: () -> Unit,
        onEditProfile: () -> Unit,
        onOpenWallpaper: () -> Unit,
        onOpenHidden: () -> Unit,
        onCloseDrawer: () -> Unit
    ) {
        activeSubMenuRow = null
        activeAnchorButton = null

        addHeader(ctx, panel, "ACCOUNT & VISIBILITY")
        val topRow = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL; clipChildren = false; clipToPadding = false }
        topRow.addView(makeActionTile(ctx, lp, "👤", lp.userName.ifBlank { "Shree" }, "Edit Profile & DP") {
            onEditProfile()
        }, LinearLayout.LayoutParams(0, dp(ctx, 52), 1f).apply { rightMargin = dp(ctx, 6) })

        val hiddenCount = lp.getHiddenApps().size
        topRow.addView(makeActionTile(ctx, lp, "👁", "Hidden Apps", "$hiddenCount hidden") {
            onCloseDrawer()
            onOpenHidden()
        }, LinearLayout.LayoutParams(0, dp(ctx, 52), 1f))
        panel.addView(topRow, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(ctx, 6) })

        addActionCard(ctx, panel, lp, "🖼", "Wallpaper & Live Effects", "Gallery photos, blur, dim & animations") {
            onOpenWallpaper()
        }

        // 1. TILE SIZE (In-Place Chip Highlight — Zero Rebuild!)
        addHeader(ctx, panel, "TILE SIZE")
        addInPlaceChipRow(
            ctx, panel, lp,
            options = listOf("small" to "Small", "medium" to "Medium", "large" to "Large"),
            getSelected = { lp.tileSize },
            onSelect = { key ->
                lp.tileSize = key
                onLiveRefresh()
            }
        )

        // 2. STAGE & PANEL WIDTH (In-Place Chip Highlight — Zero Rebuild!)
        addHeader(ctx, panel, "STAGE & PANEL WIDTH")
        addInPlaceChipRow(
            ctx, panel, lp,
            options = listOf("20" to "20%", "25" to "25%", "30" to "30%", "35" to "35%"),
            getSelected = { lp.panelWidthPct.toString() },
            onSelect = { key ->
                lp.panelWidthPct = key.toIntOrNull() ?: 25
                onLiveRefresh()
            }
        )

        // 3. ICON SHAPE (In-Place Chip Highlight — Zero Rebuild!)
        addHeader(ctx, panel, "ICON SHAPE")
        addInPlaceChipRow(
            ctx, panel, lp,
            options = listOf("squircle" to "◐ Squircle", "circle" to "● Circle", "heart" to "♥ Heart", "classic" to "■ Classic"),
            getSelected = { lp.tileShape },
            onSelect = { key ->
                lp.tileShape = key
                onLiveRefresh()
            }
        )

        // 4. GLOBAL MOTION STYLE (In-Place Chip Highlight — Zero Rebuild!)
        addHeader(ctx, panel, "GLOBAL MOTION STYLE (TAP TO PREVIEW)")
        val motionRow1 = listOf("elastic" to "Elastic", "jelly" to "Jelly", "flip3d" to "3D Flip", "book" to "Book Open")
        val motionRow2 = listOf("levitate" to "Levitate", "silk" to "Silk Slide", "swing" to "Swing")
        val allMotionButtons = ArrayList<Pair<String, TextView>>()

        val buildMotionRow = { items: List<Pair<String, String>> ->
            val r = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL; clipChildren = false; clipToPadding = false }
            items.forEachIndexed { idx, (key, label) ->
                val isSel = lp.motionStyle == key
                val btn = TextView(ctx).apply {
                    text = label; textSize = 9.5f; typeface = Typeface.DEFAULT_BOLD; setTextColor(Color.WHITE)
                    gravity = Gravity.CENTER; isFocusable = true; isClickable = true
                    background = chipBg(ctx, lp.accentColor, isSel)
                    setOnFocusChangeListener { v, f ->
                        MotionEngine.animateFocus(v, f, lp.motionStyle, 1.05f, 4f)
                        v.background = chipBg(ctx, lp.accentColor, f || lp.motionStyle == key)
                    }
                    setOnClickListener {
                        lp.motionStyle = key
                        allMotionButtons.forEach { (k, b) -> b.background = chipBg(ctx, lp.accentColor, b.hasFocus() || k == lp.motionStyle) }
                        MotionEngine.playDemoPreview(panel, key)
                    }
                }
                allMotionButtons.add(key to btn)
                r.addView(btn, LinearLayout.LayoutParams(0, dp(ctx, 34), 1f).apply {
                    if (idx < items.size - 1) rightMargin = dp(ctx, 5)
                })
            }
            panel.addView(r, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(ctx, 5) })
        }
        buildMotionRow(motionRow1)
        buildMotionRow(motionRow2)
                // 5. ACCENT AURA (In-Place Swatch Highlight — Zero Rebuild!)
        addHeader(ctx, panel, "ACCENT AURA")
        val accents = listOf(
            Color.rgb(92, 184, 255),
            Color.rgb(46, 232, 138),
            Color.rgb(255, 110, 145),
            Color.rgb(185, 135, 255),
            Color.rgb(255, 195, 75),
            Color.rgb(75, 235, 245)
        )
        val swatchRow = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL; clipChildren = false; clipToPadding = false }
        val swatchViews = ArrayList<Pair<Int, View>>()
        accents.forEachIndexed { idx, col ->
            val isSel = lp.accentColor == col
            val dot = View(ctx).apply {
                isFocusable = true; isClickable = true
                background = swatchBg(ctx, col, isSel)
                setOnFocusChangeListener { v, f ->
                    MotionEngine.animateFocus(v, f, lp.motionStyle, 1.12f, 5f)
                    v.background = swatchBg(ctx, col, f || lp.accentColor == col)
                }
                setOnClickListener {
                    lp.accentColor = col
                    swatchViews.forEach { (c, v) -> v.background = swatchBg(ctx, c, v.hasFocus() || c == lp.accentColor) }
                    onLiveRefresh()
                }
            }
            swatchViews.add(col to dot)
            swatchRow.addView(dot, LinearLayout.LayoutParams(0, dp(ctx, 32), 1f).apply {
                if (idx < accents.size - 1) rightMargin = dp(ctx, 6)
            })
        }
        panel.addView(swatchRow, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(ctx, 6) })

        // 6. TILE THEME PALETTE (In-Place Chip Highlight — Zero Rebuild!)
        addHeader(ctx, panel, "TILE GLASS PALETTE")
        val themes = TileThemePalette.THEMES.take(4)
        addInPlaceChipRow(
            ctx, panel, lp,
            options = themes.map { it.key to it.label },
            getSelected = { lp.tileColor },
            onSelect = { key ->
                lp.tileColor = key
                onLiveRefresh()
            }
        )

        // 7. FAVORITE BADGE STYLE (In-Place Chip Highlight — Zero Rebuild!)
        addHeader(ctx, panel, "FAVORITE BADGE")
        addInPlaceChipRow(
            ctx, panel, lp,
            options = listOf("accent" to "● Dot", "heart" to "♥ Heart", "star" to "★ Star", "none" to "Off"),
            getSelected = { lp.badgeMode },
            onSelect = { key ->
                lp.badgeMode = key
                onLiveRefresh()
            }
        )
    }

    private fun addInPlaceChipRow(
        ctx: Context,
        panel: LinearLayout,
        lp: LauncherPreferences,
        options: List<Pair<String, String>>,
        getSelected: () -> String,
        onSelect: (String) -> Unit
    ) {
        val row = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            clipChildren = false
            clipToPadding = false
        }
        val chips = ArrayList<Pair<String, TextView>>()
        options.forEachIndexed { idx, (key, label) ->
            val isSel = getSelected() == key
            val chip = TextView(ctx).apply {
                text = label
                textSize = 9.5f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(Color.WHITE)
                gravity = Gravity.CENTER
                isFocusable = true
                isClickable = true
                background = chipBg(ctx, lp.accentColor, isSel)
                setOnFocusChangeListener { v, f ->
                    MotionEngine.animateFocus(v, f, lp.motionStyle, 1.05f, 4f)
                    v.background = chipBg(ctx, lp.accentColor, f || getSelected() == key)
                }
                setOnClickListener {
                    onSelect(key)
                    // Update ONLY the chip backgrounds in-place — NEVER rebuild the panel!
                    chips.forEach { (k, cView) ->
                        cView.background = chipBg(ctx, lp.accentColor, cView.hasFocus() || getSelected() == k)
                    }
                }
            }
            chips.add(key to chip)
            row.addView(chip, LinearLayout.LayoutParams(0, dp(ctx, 34), 1f).apply {
                if (idx < options.size - 1) rightMargin = dp(ctx, 5)
            })
        }
        panel.addView(row, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(ctx, 4) })
    }

    private fun makeActionTile(ctx: Context, lp: LauncherPreferences, icon: String, title: String, sub: String, onClick: () -> Unit) =
        LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(ctx, 10), dp(ctx, 6), dp(ctx, 10), dp(ctx, 6)); isFocusable = true; isClickable = true
            background = chipBg(ctx, lp.accentColor, false)
            setOnFocusChangeListener { v, f ->
                MotionEngine.animateFocus(v, f, lp.motionStyle, 1.03f, 4f)
                v.background = chipBg(ctx, lp.accentColor, f)
            }
            setOnClickListener { onClick() }
            addView(TextView(ctx).apply { text = "$icon  $title"; textSize = 10f; typeface = Typeface.DEFAULT_BOLD; setTextColor(Color.WHITE); isSingleLine = true })
            addView(TextView(ctx).apply { text = sub; textSize = 8.2f; setTextColor(lp.accentColor); isSingleLine = true })
        }

    private fun addActionCard(ctx: Context, panel: LinearLayout, lp: LauncherPreferences, icon: String, title: String, sub: String, onClick: () -> Unit) {
        val card = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(ctx, 12), dp(ctx, 9), dp(ctx, 12), dp(ctx, 9)); isFocusable = true; isClickable = true
            background = chipBg(ctx, lp.accentColor, false)
            setOnFocusChangeListener { v, f ->
                MotionEngine.animateFocus(v, f, lp.motionStyle, 1.02f, 4f)
                v.background = chipBg(ctx, lp.accentColor, f)
            }
            setOnClickListener { onClick() }
        }
        card.addView(TextView(ctx).apply { text = icon; textSize = 14f; setTextColor(lp.accentColor); setPadding(0, 0, dp(ctx, 10), 0) })
        val mid = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
        mid.addView(TextView(ctx).apply { text = title; textSize = 10.5f; typeface = Typeface.DEFAULT_BOLD; setTextColor(Color.WHITE) })
        mid.addView(TextView(ctx).apply { text = sub; textSize = 8.5f; setTextColor(Color.argb(185, 210, 230, 245)) })
        card.addView(mid, LinearLayout.LayoutParams(0, -2, 1f))
        card.addView(TextView(ctx).apply { text = "›"; textSize = 15f; typeface = Typeface.DEFAULT_BOLD; setTextColor(Color.WHITE) })
        panel.addView(card, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(ctx, 4) })
    }

    private fun chipBg(ctx: Context, accent: Int, active: Boolean) = GradientDrawable(
        GradientDrawable.Orientation.TOP_BOTTOM,
        if (active) intArrayOf(LauncherUtils.withAlpha(accent, 165), LauncherUtils.withAlpha(accent, 95))
        else intArrayOf(Color.argb(52, 255, 255, 255), Color.argb(26, 255, 255, 255))
    ).apply {
        cornerRadius = LauncherUtils.dpF(ctx, 12f)
        setStroke(dp(ctx, if (active) 2 else 1), if (active) Color.WHITE else Color.argb(65, 255, 255, 255))
    }

    private fun swatchBg(ctx: Context, col: Int, active: Boolean) = GradientDrawable().apply {
        shape = GradientDrawable.OVAL
        setColor(col)
        setStroke(dp(ctx, if (active) 3 else 1), if (active) Color.WHITE else Color.argb(110, 255, 255, 255))
    }

    private fun addHeader(ctx: Context, panel: LinearLayout, label: String) {
        panel.addView(TextView(ctx).apply {
            text = label.uppercase(Locale.ROOT); textSize = 8.8f; typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.argb(210, 195, 220, 255)); letterSpacing = 0.08f
        }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(ctx, 8); bottomMargin = dp(ctx, 4) })
    }

    private fun dp(ctx: Context, v: Int) = LauncherUtils.dp(ctx, v)
}
