package com.shreenivas.tvhome.panels

import android.app.Activity
import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.tv.TvInputManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import com.shreenivas.tvhome.LauncherPreferences
import com.shreenivas.tvhome.LauncherUtils
import com.shreenivas.tvhome.MotionEngine
import com.shreenivas.tvhome.homescreen.AppGridManager
import com.shreenivas.tvhome.services.TvNotificationService
import java.util.Locale

class SideControlDrawer(
    private val context: Context,
    private val drawerLayout: LinearLayout,
    private val scrim: View,
    private val mainPage: View,
    private val lp: LauncherPreferences,
    private val onLiveRefresh: () -> Unit,
    private val onWallpaperRefresh: () -> Unit,
    private val onPickWallpaper: () -> Unit,
    private val onPickProfilePic: () -> Unit,
    private val onOpenHiddenRibbon: () -> Unit
) {
    companion object {
        const val MODE_CUSTOMIZE = "customize"
        const val MODE_QUICK = "quick"
        const val MODE_WIFI = "wifi"
        const val MODE_ALERTS = "alerts"
        const val MODE_POWER = "power"
        const val MODE_WALLPAPER_EDIT = "wp_edit"
        const val MODE_SUB_INPUTS = "sub_inputs"
        const val MODE_SUB_PICTURE = "sub_picture"
        const val MODE_SUB_SOUND = "sub_sound"
        const val MODE_SUB_BLUETOOTH = "sub_bluetooth"
        const val MODE_SUB_STORAGE = "sub_storage"
    }

    var isOpen = false
        private set
    private var currentMode = ""
    private var activeScroll: ScrollView? = null
    private var activePanel: LinearLayout? = null
    private var brightnessPct = getCurrentSystemBrightnessPct()
    private var selectedPicturePreset = "Standard"
    private var stageWidget: View? = null

    fun attachStageWidget(widget: View) {
        stageWidget = widget
    }

    fun getParentNavTag(): String = when (currentMode) {
        MODE_WIFI -> "nav_wifi"
        MODE_ALERTS -> "nav_alerts"
        MODE_CUSTOMIZE, MODE_WALLPAPER_EDIT -> "nav_customize"
        MODE_POWER -> "nav_power"
        else -> "nav_quick"
    }

    fun toggle(mode: String) {
        if (isOpen && mode == currentMode) close() else open(mode)
    }

    fun open(mode: String) {
        val wasOpen = isOpen
        currentMode = mode
        isOpen = true

        val screenW = context.resources.displayMetrics.widthPixels
        val dynamicDrawerW = maxOf(LauncherUtils.dp(context, 250), (screenW * (lp.panelWidthPct / 100f)).toInt())
        drawerLayout.layoutParams.width = dynamicDrawerW
        drawerLayout.clipChildren = false
        drawerLayout.clipToPadding = false
        drawerLayout.requestLayout()

        drawerLayout.background = GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            intArrayOf(Color.argb(238, 22, 30, 68), Color.argb(244, 15, 22, 52), Color.argb(248, 10, 15, 38))
        ).apply { cornerRadius = LauncherUtils.dpF(context, 24f); setStroke(dp(1.5f), Color.argb(145, 255, 255, 255)) }
        drawerLayout.outlineProvider = null

        scrim.setBackgroundColor(Color.TRANSPARENT)
        scrim.visibility = View.VISIBLE
        drawerLayout.visibility = View.VISIBLE

        setupDrawerSkeleton()
        populateActivePanel(true)

        if (!wasOpen) {
            MotionEngine.resetTransform(drawerLayout)
            MotionEngine.animatePanelEnter(drawerLayout, stageWidget, dynamicDrawerW, lp.motionStyle) {}
        } else {
            activePanel?.let { MotionEngine.playDemoPreview(it, lp.motionStyle) }
        }
    }

    fun close() {
        if (!isOpen) return
        isOpen = false; currentMode = ""
        val out = maxOf(dp(340f), drawerLayout.width + dp(30f))

        MotionEngine.animatePanelExit(drawerLayout, stageWidget, out, lp.motionStyle) {
            scrim.visibility = View.GONE; drawerLayout.visibility = View.GONE
        }
        drawerLayout.post { onLiveRefresh() }
    }

    private fun setupDrawerSkeleton() {
        drawerLayout.removeAllViews()
        drawerLayout.setPadding(dp(14f), dp(14f), dp(14f), dp(14f))

        val topRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            clipChildren = false; clipToPadding = false
            setPadding(dp(6f), dp(4f), dp(6f), dp(10f))
        }

        val (icon, title) = when (currentMode) {
            MODE_CUSTOMIZE -> "✦  " to "Customize Launcher"
            MODE_WIFI -> "📶  " to "Network & Internet"
            MODE_ALERTS -> "🔔  " to "Alerts & Notifications"
            MODE_POWER -> "⏻  " to "Power & System"
            MODE_WALLPAPER_EDIT -> "🖼  " to "Wallpaper Studio"
            MODE_SUB_INPUTS -> "📺  " to "TV Inputs & HDMI"
            MODE_SUB_PICTURE -> "☀  " to "Display & Sleep Timer"
            MODE_SUB_SOUND -> "🔊  " to "Sound & Audio Out"
            MODE_SUB_BLUETOOTH -> "🔵  " to "Bluetooth & Remotes"
            MODE_SUB_STORAGE -> "💾  " to "Storage & Installed Apps"
            else -> "⚡  " to "Control Center"
        }
        topRow.addView(text("$icon$title", 13.5f, Color.WHITE, true))
        topRow.addView(View(context), LinearLayout.LayoutParams(0, 1, 1f))

        val closeX = TextView(context).apply {
            text = "✕"; textSize = 12f; typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE); gravity = Gravity.CENTER; isFocusable = true; isClickable = true
            background = xBtnBg(false)
            setOnFocusChangeListener { v, f -> MotionEngine.animateFocus(v, f, lp.motionStyle, 1.12f, 4f); v.background = xBtnBg(f) }
            setOnClickListener { close() }
        }
        topRow.addView(closeX, LinearLayout.LayoutParams(dp(28f), dp(28f)))
        drawerLayout.addView(topRow)

        val divider = View(context).apply { setBackgroundColor(Color.argb(60, 255, 255, 255)) }
        drawerLayout.addView(divider, LinearLayout.LayoutParams(-1, dp(1f)).apply { bottomMargin = dp(8f) })

        activeScroll = ScrollView(context).apply {
            isVerticalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
            isFillViewport = true
            clipChildren = false
            clipToPadding = false
        }
        activePanel = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            clipChildren = false
            clipToPadding = false
            setPadding(dp(6f), dp(6f), dp(6f), dp(20f))
        }
        drawerLayout.addView(activeScroll, LinearLayout.LayoutParams(-1, 0, 1f))
        activeScroll?.addView(activePanel)
    }

    private fun populateActivePanel(isInitialOpen: Boolean) {
        val panel = activePanel ?: return
        panel.removeAllViews()

        when (currentMode) {
            MODE_CUSTOMIZE -> CustomizeMenuBuilder.build(
                context, panel, lp, onLiveRefresh, { populateActivePanel(false) },
                ::showProfileEditDialog,
                { open(MODE_WALLPAPER_EDIT) },
                onOpenHiddenRibbon,
                ::close
            )
            MODE_WIFI -> WifiPanelBuilder.build(context, panel, lp) { populateActivePanel(false) }
            MODE_ALERTS -> buildRealAlertsPanel(panel)
            MODE_POWER -> buildPowerPanel(panel)
            MODE_WALLPAPER_EDIT -> WallpaperEditorPanel.build(context, panel, lp, onWallpaperRefresh, onPickWallpaper) { open(MODE_CUSTOMIZE) }
            MODE_SUB_INPUTS -> buildInputsSubPanel(panel)
            MODE_SUB_PICTURE -> buildPictureSubPanel(panel)
            MODE_SUB_SOUND -> buildSoundSubPanel(panel)
            MODE_SUB_BLUETOOTH -> SystemSubPanels.buildBluetoothPanel(context, panel, lp) { open(MODE_QUICK) }
            MODE_SUB_STORAGE -> SystemSubPanels.buildStorageAndAppsPanel(context, panel, lp) { open(MODE_QUICK) }
            else -> buildControlCenter(panel)
        }
        if (isInitialOpen) panel.post { panel.requestFocus() }
    }
        private fun buildControlCenter(panel: LinearLayout) {
        addLabel(panel, "SYSTEM CONTROLS (IN-APP GLOSSY)")

        val r1 = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL; clipChildren = false; clipToPadding = false }
        r1.addView(makeGridTile("☀", "Display", "$selectedPicturePreset • $brightnessPct%") { open(MODE_SUB_PICTURE) }, LinearLayout.LayoutParams(0, dp(56f), 1f).apply { rightMargin = dp(6f) })
        r1.addView(makeGridTile("🔊", "Sound", getActiveAudioDeviceName().removeSuffix(" ›")) { open(MODE_SUB_SOUND) }, LinearLayout.LayoutParams(0, dp(56f), 1f))
        panel.addView(r1, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(6f) })

        val r2 = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL; clipChildren = false; clipToPadding = false }
        r2.addView(makeGridTile("📶", "Wi-Fi", "Manage & Connect") { open(MODE_WIFI) }, LinearLayout.LayoutParams(0, dp(56f), 1f).apply { rightMargin = dp(6f) })
        r2.addView(makeGridTile("🔵", "Bluetooth", "Remotes & Audio") { open(MODE_SUB_BLUETOOTH) }, LinearLayout.LayoutParams(0, dp(56f), 1f))
        panel.addView(r2, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(6f) })

        val r3 = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL; clipChildren = false; clipToPadding = false }
        r3.addView(makeGridTile("📺", "TV Inputs", "HDMI & Live TV") { open(MODE_SUB_INPUTS) }, LinearLayout.LayoutParams(0, dp(56f), 1f).apply { rightMargin = dp(6f) })
        r3.addView(makeGridTile("💾", "Storage & Apps", "Space & App Info") { open(MODE_SUB_STORAGE) }, LinearLayout.LayoutParams(0, dp(56f), 1f))
        panel.addView(r3, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(8f) })

        addLabel(panel, "LIVE HARDWARE SLIDERS")
        addSliderCard(panel, "☀  Screen Brightness", brightnessPct) { p ->
            brightnessPct = p
            applyRealBrightness(p)
        }

        val am = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        val maxVol = am?.getStreamMaxVolume(AudioManager.STREAM_MUSIC) ?: 15
        val curVol = am?.getStreamVolume(AudioManager.STREAM_MUSIC) ?: 10
        val volPct = ((curVol * 100f) / maxOf(1, maxVol)).toInt()
        addSliderCard(panel, "🔊  TV Master Volume", volPct) { p ->
            am?.setStreamVolume(AudioManager.STREAM_MUSIC, ((p / 100f) * maxVol).toInt(), AudioManager.FLAG_SHOW_UI)
        }

        val ramCard = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12f), dp(9f), dp(12f), dp(9f)); isFocusable = true; background = glassCardBg(false)
            setOnFocusChangeListener { v, f -> MotionEngine.animateFocus(v, f, lp.motionStyle, 1.02f, 4f); v.background = glassCardBg(f) }
        }
        ramCard.addView(text("🚀", 14f, lp.accentColor, true).apply { setPadding(0, 0, dp(10f), 0) })
        val ramMid = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        ramMid.addView(text("Boost Memory (RAM)", 10.5f, Color.WHITE, true))
        val ramSubTv = text(getRamStatusText(), 8.5f, Color.argb(185, 210, 230, 245), false)
        ramMid.addView(ramSubTv)
        ramCard.addView(ramMid, LinearLayout.LayoutParams(0, -2, 1f))
        ramCard.addView(text("›", 15f, Color.argb(180, 255, 255, 255), true))
        ramCard.setOnClickListener {
            Runtime.getRuntime().gc()
            ramSubTv.text = getRamStatusText()
            Toast.makeText(context, "Memory optimized!", Toast.LENGTH_SHORT).show()
        }
        panel.addView(ramCard, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(4f); bottomMargin = dp(6f) })
    }

    private fun getCurrentSystemBrightnessPct(): Int {
        return try {
            val raw = Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS, 200)
            ((raw / 255f) * 100f).toInt().coerceIn(10, 100)
        } catch (_: Exception) { 85 }
    }

    private fun applyRealBrightness(pct: Int) {
        val clamped = pct.coerceIn(10, 100)
        (context as? Activity)?.window?.let { win ->
            val attr = win.attributes
            attr.screenBrightness = clamped / 100f
            win.attributes = attr
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && Settings.System.canWrite(context)) {
            try {
                Settings.System.putInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS_MODE, Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL)
                Settings.System.putInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS, ((clamped / 100f) * 255).toInt())
            } catch (_: Exception) {}
        }
    }

    private fun buildInputsSubPanel(panel: LinearLayout) {
        addLabel(panel, "DETECTED TV & HDMI INPUTS")
        val tvMgr = context.getSystemService(Context.TV_INPUT_SERVICE) as? TvInputManager
        val inputs = try { tvMgr?.tvInputList ?: emptyList() } catch (_: Exception) { emptyList() }
        if (inputs.isEmpty()) {
            panel.addView(text("No external HDMI tuner inputs reported by TV hardware.", 9.5f, Color.LTGRAY, false).apply {
                setPadding(dp(4f), dp(8f), dp(4f), dp(12f))
            })
        } else {
            for (inp in inputs) {
                val lbl = try { inp.loadLabel(context).toString() } catch (_: Exception) { inp.id }
                addActionButton(panel, "📺", lbl, "Tap to switch input source") {
                    try { inp.createSetupIntent()?.let { context.startActivity(it) } } catch (_: Exception) {}
                }
            }
        }
        addBackSubButton(panel)
    }

    private fun buildPictureSubPanel(panel: LinearLayout) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.System.canWrite(context)) {
            addActionButton(panel, "🔓", "Enable Hardware Backlight Control", "One-time permission to write system brightness") {
                try { context.startActivity(Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:${context.packageName}"))) } catch (_: Exception) {}
            }
        }
        addLabel(panel, "PICTURE PRESET MODES")
        val presets = listOf("Standard", "Cinema / Movie", "Vivid", "Night Eye-Care")
        val presetBrightness = listOf(80, 65, 100, 35)
        val presetCards = ArrayList<Pair<LinearLayout, TextView>>()

        presets.forEachIndexed { idx, name ->
            val isSel = selectedPicturePreset == name
            val titleTv = text(if (isSel) "✓  $name" else "○  $name", 10.5f, if (isSel) lp.accentColor else Color.WHITE, true)
            val card = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(12f), dp(9f), dp(12f), dp(9f)); isFocusable = true; background = glassCardBg(isSel)
                setOnFocusChangeListener { v, f -> MotionEngine.animateFocus(v, f, lp.motionStyle, 1.02f, 3f); v.background = glassCardBg(f || selectedPicturePreset == name) }
                setOnClickListener {
                    selectedPicturePreset = name
                    brightnessPct = presetBrightness[idx]
                    applyRealBrightness(brightnessPct)
                    presetCards.forEachIndexed { i, (cView, tView) ->
                        val active = presets[i] == selectedPicturePreset
                        tView.text = if (active) "✓  ${presets[i]}" else "○  ${presets[i]}"
                        tView.setTextColor(if (active) lp.accentColor else Color.WHITE)
                        cView.background = glassCardBg(cView.hasFocus() || active)
                    }
                }
            }
            card.addView(titleTv, LinearLayout.LayoutParams(0, -2, 1f))
            card.addView(text("${presetBrightness[idx]}%", 9f, Color.argb(190, 220, 235, 255), true))
            presetCards.add(card to titleTv)
            panel.addView(card, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(6f) })
        }

        SystemSubPanels.buildDisplayTimeoutAndNightPanel(context, panel, lp) { open(MODE_QUICK) }
    }

    private fun buildSoundSubPanel(panel: LinearLayout) {
        addLabel(panel, "ACTIVE AUDIO OUTPUT")
        val am = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        val devices = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) am?.getDevices(AudioManager.GET_DEVICES_OUTPUTS)?.toList() ?: emptyList() else emptyList()
        if (devices.isEmpty()) {
            addActionButton(panel, "🔊", "TV Internal Speakers", "Default stereo output") {}
        } else {
            devices.distinctBy { it.type }.take(4).forEach { dev ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    addActionButton(panel, "🎧", mapAudioType(dev.type), dev.productName?.toString()?.takeIf { it.isNotBlank() } ?: "Connected Output") {}
                }
            }
        }

        val muteCard = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12f), dp(9f), dp(12f), dp(9f)); isFocusable = true; background = glassCardBg(false)
            setOnFocusChangeListener { v, f -> MotionEngine.animateFocus(v, f, lp.motionStyle, 1.02f, 4f); v.background = glassCardBg(f) }
        }
        val initialMuted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) am?.isStreamMute(AudioManager.STREAM_MUSIC) == true else false
        val muteIconTv = text(if (initialMuted) "🔇" else "🔈", 14f, lp.accentColor, true).apply { setPadding(0, 0, dp(10f), 0) }
        val muteTitleTv = text(if (initialMuted) "Unmute TV Audio" else "Mute TV Audio", 10.5f, Color.WHITE, true)
        val muteMid = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
                muteMid.addView(muteTitleTv)
        muteMid.addView(text("Instant audio toggle", 8.5f, Color.argb(185, 210, 230, 245), false))
        muteCard.addView(muteIconTv)
        muteCard.addView(muteMid, LinearLayout.LayoutParams(0, -2, 1f))
        muteCard.addView(text("›", 15f, Color.argb(180, 255, 255, 255), true))
        muteCard.setOnClickListener {
            am?.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_TOGGLE_MUTE, AudioManager.FLAG_SHOW_UI)
            val nowMuted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) am?.isStreamMute(AudioManager.STREAM_MUSIC) == true else !initialMuted
            muteIconTv.text = if (nowMuted) "🔇" else "🔈"
            muteTitleTv.text = if (nowMuted) "Unmute TV Audio" else "Mute TV Audio"
        }
        panel.addView(muteCard, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(6f) })
        addBackSubButton(panel)
    }

    private fun addBackSubButton(panel: LinearLayout) {
        val backBtn = TextView(context).apply {
            text = "‹  Back to Control Center"; textSize = 10.5f; typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE); gravity = Gravity.CENTER; isFocusable = true; background = glassCardBg(true)
            setOnFocusChangeListener { v, f -> MotionEngine.animateFocus(v, f, lp.motionStyle, 1.03f, 3f) }
            setOnClickListener { open(MODE_QUICK) }
        }
        panel.addView(backBtn, LinearLayout.LayoutParams(-1, dp(40f)).apply { topMargin = dp(10f); bottomMargin = dp(20f) })
    }

    private fun getActiveAudioDeviceName(): String {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val out = (context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager)?.getDevices(AudioManager.GET_DEVICES_OUTPUTS)?.lastOrNull()
            if (out != null) return "${mapAudioType(out.type)} ›"
        }
        return "TV Speakers ›"
    }

    private fun mapAudioType(type: Int): String = when (type) {
        AudioDeviceInfo.TYPE_BLUETOOTH_A2DP, AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> "Bluetooth Audio"
        AudioDeviceInfo.TYPE_HDMI, AudioDeviceInfo.TYPE_HDMI_ARC -> "HDMI ARC"
        AudioDeviceInfo.TYPE_USB_DEVICE, AudioDeviceInfo.TYPE_USB_HEADSET -> "USB Audio"
        AudioDeviceInfo.TYPE_WIRED_HEADSET, AudioDeviceInfo.TYPE_WIRED_HEADPHONES -> "Wired Audio"
        else -> "TV Speakers"
    }

    private fun getRamStatusText(): String = try {
        val mi = ActivityManager.MemoryInfo()
        (context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).getMemoryInfo(mi)
        "Free: ${mi.availMem / (1024 * 1024)}MB / ${mi.totalMem / (1024 * 1024)}MB • Tap to boost"
    } catch (_: Exception) { "Optimize background memory" }

    private fun buildRealAlertsPanel(panel: LinearLayout) {
        if (!TvNotificationService.isPermissionGranted(context)) {
            addLabel(panel, "PERMISSION REQUIRED")
            addActionButton(panel, "🔓", "Enable Notification Access", "Allow Shree TV Home to show real system & app alerts") {
                try { context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) } catch (_: Exception) {}
            }
            return
        }
        val alerts = TvNotificationService.getAlerts()
        val topBar = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val headerCountTv = text("LIVE SYSTEM ALERTS (${alerts.size})", 9f, lp.accentColor, true)
        topBar.addView(headerCountTv)
        topBar.addView(View(context), LinearLayout.LayoutParams(0, 1, 1f))

        val listHolder = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL; clipChildren = false; clipToPadding = false }
        if (alerts.isNotEmpty()) {
            val clearAllBtn = text("Clear All", 9f, Color.WHITE, true).apply {
                setPadding(dp(8f), dp(3f), dp(8f), dp(3f)); isFocusable = true; background = glassCardBg(false)
                setOnFocusChangeListener { v, f -> MotionEngine.animateFocus(v, f, lp.motionStyle, 1.03f, 3f); v.background = glassCardBg(f) }
                setOnClickListener {
                    TvNotificationService.clearAllAlerts()
                    listHolder.removeAllViews()
                    headerCountTv.text = "LIVE SYSTEM ALERTS (0)"
                    listHolder.addView(text("No active notifications right now", 10.5f, Color.argb(180, 210, 230, 250), false).apply { gravity = Gravity.CENTER; setPadding(0, dp(28f), 0, dp(28f)) })
                }
            }
            topBar.addView(clearAllBtn)
        }
        panel.addView(topBar, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(8f) })
        panel.addView(listHolder, LinearLayout.LayoutParams(-1, -2))

        if (alerts.isEmpty()) {
            listHolder.addView(text("No active notifications right now", 10.5f, Color.argb(180, 210, 230, 250), false).apply { gravity = Gravity.CENTER; setPadding(0, dp(28f), 0, dp(28f)) })
            return
        }
        for (item in alerts) {
            val card = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL; setPadding(dp(11f), dp(9f), dp(11f), dp(9f)); isFocusable = true; background = glassCardBg(false)
                setOnFocusChangeListener { v, f -> MotionEngine.animateFocus(v, f, lp.motionStyle, 1.02f, 4f); v.background = glassCardBg(f) }
            }
            card.setOnClickListener {
                TvNotificationService.dismissAlert(item.key)
                listHolder.removeView(card)
                val rem = TvNotificationService.getAlerts().size
                headerCountTv.text = "LIVE SYSTEM ALERTS ($rem)"
                if (rem == 0) listHolder.addView(text("No active notifications right now", 10.5f, Color.argb(180, 210, 230, 250), false).apply { gravity = Gravity.CENTER; setPadding(0, dp(28f), 0, dp(28f)) })
            }
            val row1 = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
            row1.addView(text("🔔  ${item.appName}", 9f, lp.accentColor, true), LinearLayout.LayoutParams(0, -2, 1f))
            row1.addView(text("${item.timeStr}  ✕", 8.5f, Color.argb(180, 255, 255, 255), true))
            card.addView(row1)
            card.addView(text(item.title, 10.5f, Color.WHITE, true).apply { isSingleLine = true; ellipsize = TextUtils.TruncateAt.END; setPadding(0, dp(3f), 0, dp(1f)) })
            card.addView(text(item.text, 9f, Color.argb(195, 215, 232, 250), false).apply { maxLines = 2; ellipsize = TextUtils.TruncateAt.END })
            listHolder.addView(card, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(7f) })
        }
    }

    private fun buildPowerPanel(panel: LinearLayout) {
        addLabel(panel, "DEVICE POWER & LAUNCHER")
        addActionButton(panel, "🌙", "Sleep / Lock Display", "Turn off screen / enter standby") {
            try { context.startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)) } catch (_: Exception) {}
            close()
        }
        addActionButton(panel, "🔄", "Refresh All Apps", "Reload app icons and grid") {
            AppGridManager.clearCache(); onLiveRefresh(); close()
        }
    }

    private fun makeGridTile(icon: String, title: String, sub: String, onClick: () -> Unit) = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_VERTICAL; setPadding(dp(10f), dp(6f), dp(10f), dp(6f)); isFocusable = true; background = glassCardBg(false)
        setOnFocusChangeListener { v, f -> MotionEngine.animateFocus(v, f, lp.motionStyle, 1.03f, 4f); v.background = glassCardBg(f) }
        setOnClickListener { onClick() }
        addView(text("$icon  $title", 10f, Color.WHITE, true))
        addView(text(sub, 8.2f, lp.accentColor, false).apply { isSingleLine = true; ellipsize = TextUtils.TruncateAt.END; setPadding(0, dp(2f), 0, 0) })
    }

    private fun addSliderCard(panel: LinearLayout, label: String, initialPct: Int, onChange: (Int) -> Unit) {
        val card = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(12f), dp(9f), dp(12f), dp(9f)); background = glassCardBg(false) }
        val top = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
        val pct = text("$initialPct%", 10.5f, lp.accentColor, true)
        top.addView(text(label, 10.5f, Color.WHITE, true)); top.addView(View(context), LinearLayout.LayoutParams(0, 1, 1f)); top.addView(pct)
        card.addView(top)
        val sb = SeekBar(context).apply {
            max = 100; progress = initialPct; isFocusable = true
            setOnFocusChangeListener { _, f -> card.background = glassCardBg(f) }
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(s: SeekBar?, p: Int, user: Boolean) { pct.text = "$p%"; if (user) onChange(p) }
                override fun onStartTrackingTouch(s: SeekBar?) {}
                override fun onStopTrackingTouch(s: SeekBar?) {}
            })
        }
        card.addView(sb, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(5f) }); panel.addView(card, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(7f) })
    }

    fun showProfileEditDialog() {
        ProfileCropDialog.show(context, lp, onPickProfilePic) {
            onLiveRefresh()
            if (isOpen) populateActivePanel(false)
        }
    }

    private fun addActionButton(panel: LinearLayout, icon: String, title: String, sub: String, act: () -> Unit) {
        val card = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(dp(12f), dp(9f), dp(12f), dp(9f)); isFocusable = true; background = glassCardBg(false)
            setOnFocusChangeListener { v, f -> MotionEngine.animateFocus(v, f, lp.motionStyle, 1.02f, 4f); v.background = glassCardBg(f) }
                        }
            card.setOnClickListener {
                TvNotificationService.dismissAlert(item.key)
                listHolder.removeView(card)
                val rem = TvNotificationService.getAlerts().size
                headerCountTv.text = "LIVE SYSTEM ALERTS ($rem)"
                if (rem == 0) listHolder.addView(text("No active notifications right now", 10.5f, Color.argb(180, 210, 230, 250), false).apply { gravity = Gravity.CENTER; setPadding(0, dp(28f), 0, dp(28f)) })
            }
            val row1 = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
            row1.addView(text("🔔  ${item.appName}", 9f, lp.accentColor, true), LinearLayout.LayoutParams(0, -2, 1f))
            row1.addView(text("${item.timeStr}  ✕", 8.5f, Color.argb(180, 255, 255, 255), true))
            card.addView(row1)
            card.addView(text(item.title, 10.5f, Color.WHITE, true).apply { isSingleLine = true; ellipsize = TextUtils.TruncateAt.END; setPadding(0, dp(3f), 0, dp(1f)) })
            card.addView(text(item.text, 9f, Color.argb(195, 215, 232, 250), false).apply { maxLines = 2; ellipsize = TextUtils.TruncateAt.END })
            listHolder.addView(card, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(7f) })
        }
    }

    private fun buildPowerPanel(panel: LinearLayout) {
        addLabel(panel, "DEVICE POWER & LAUNCHER")
        addActionButton(panel, "🌙", "Sleep / Lock Display", "Turn off screen / enter standby") {
            try { context.startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)) } catch (_: Exception) {}
            close()
        }
        addActionButton(panel, "🔄", "Refresh All Apps", "Reload app icons and grid") {
            AppGridManager.clearCache(); onLiveRefresh(); close()
        }
    }

    private fun makeGridTile(icon: String, title: String, sub: String, onClick: () -> Unit) = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_VERTICAL; setPadding(dp(10f), dp(6f), dp(10f), dp(6f)); isFocusable = true; background = glassCardBg(false)
        setOnFocusChangeListener { v, f -> MotionEngine.animateFocus(v, f, lp.motionStyle, 1.03f, 4f); v.background = glassCardBg(f) }
        setOnClickListener { onClick() }
        addView(text("$icon  $title", 10f, Color.WHITE, true))
        addView(text(sub, 8.2f, lp.accentColor, false).apply { isSingleLine = true; ellipsize = TextUtils.TruncateAt.END; setPadding(0, dp(2f), 0, 0) })
    }

    private fun addSliderCard(panel: LinearLayout, label: String, initialPct: Int, onChange: (Int) -> Unit) {
        val card = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(12f), dp(9f), dp(12f), dp(9f)); background = glassCardBg(false) }
        val top = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
        val pct = text("$initialPct%", 10.5f, lp.accentColor, true)
        top.addView(text(label, 10.5f, Color.WHITE, true)); top.addView(View(context), LinearLayout.LayoutParams(0, 1, 1f)); top.addView(pct)
        card.addView(top)
        val sb = SeekBar(context).apply {
            max = 100; progress = initialPct; isFocusable = true
            setOnFocusChangeListener { _, f -> card.background = glassCardBg(f) }
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(s: SeekBar?, p: Int, user: Boolean) { pct.text = "$p%"; if (user) onChange(p) }
                override fun onStartTrackingTouch(s: SeekBar?) {}
                override fun onStopTrackingTouch(s: SeekBar?) {}
            })
        }
        card.addView(sb, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(5f) }); panel.addView(card, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(7f) })
    }

    fun showProfileEditDialog() {
        ProfileCropDialog.show(context, lp, onPickProfilePic) {
            onLiveRefresh()
            if (isOpen) populateActivePanel(false)
        }
    }

    private fun addActionButton(panel: LinearLayout, icon: String, title: String, sub: String, act: () -> Unit) {
        val card = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(dp(12f), dp(9f), dp(12f), dp(9f)); isFocusable = true; background = glassCardBg(false)
            setOnFocusChangeListener { v, f -> MotionEngine.animateFocus(v, f, lp.motionStyle, 1.02f, 4f); v.background = glassCardBg(f) }
            setOnClickListener { act() }
        }
        card.addView(text(icon, 14f, lp.accentColor, true).apply { setPadding(0, 0, dp(10f), 0) })
        val mid = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        mid.addView(text(title, 10.5f, Color.WHITE, true)); mid.addView(text(sub, 8.5f, Color.argb(185, 210, 230, 245), false))
        card.addView(mid, LinearLayout.LayoutParams(0, -2, 1f)); card.addView(text("›", 15f, Color.argb(180, 255, 255, 255), true))
        panel.addView(card, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(6f) })
    }

    private fun xBtnBg(foc: Boolean) = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(if (foc) Color.argb(165, 255, 75, 85) else Color.argb(55, 255, 255, 255)); setStroke(dp(1f), if (foc) Color.WHITE else Color.argb(85, 255, 255, 255)) }
    private fun glassCardBg(foc: Boolean) = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, if (foc) intArrayOf(Color.argb(115, 255, 255, 255), Color.argb(75, 255, 255, 255)) else intArrayOf(Color.argb(52, 255, 255, 255), Color.argb(26, 255, 255, 255))).apply { cornerRadius = LauncherUtils.dpF(context, 14f); setStroke(dp(if (foc) 2f else 1f), if (foc) lp.accentColor else Color.argb(65, 255, 255, 255)) }
    private fun text(v: String, sz: Float, col: Int, bld: Boolean) = TextView(context).apply { text = v; textSize = sz; setTextColor(col); if (bld) typeface = Typeface.DEFAULT_BOLD }
    private fun addLabel(panel: LinearLayout, lbl: String) = panel.addView(text(lbl.uppercase(Locale.ROOT), 9f, lp.accentColor, true).apply { letterSpacing = 0.09f }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8f); bottomMargin = dp(5f) })
    private fun dp(v: Float) = LauncherUtils.dpF(context, v).toInt()
}
