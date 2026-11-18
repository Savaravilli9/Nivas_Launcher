package com.shreenivas.tvhome.panels

import android.app.Activity
import android.app.ActivityManager
import android.app.AlertDialog
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
import android.text.InputType
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.widget.EditText
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
        drawerLayout.elevation = dp(22f).toFloat()
        scrim.setBackgroundColor(Color.argb(55, 4, 8, 20)); scrim.visibility = View.VISIBLE; drawerLayout.visibility = View.VISIBLE

        setupDrawerSkeleton()
        populateActivePanel(true)

        if (!wasOpen) {
            MotionEngine.resetTransform(drawerLayout)
            scrim.alpha = 0f; scrim.animate().alpha(1f).setDuration(360L).start()
            MotionEngine.animatePanelEnter(drawerLayout, stageWidget, dynamicDrawerW, lp.motionStyle) {}
        } else {
            activePanel?.let { MotionEngine.playDemoPreview(it, lp.motionStyle) }
        }
    }

    fun close() {
        if (!isOpen) return
        isOpen = false; currentMode = ""
        val out = maxOf(dp(340f), drawerLayout.width + dp(30f))

        scrim.animate().alpha(0f).setDuration(280L).start()

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
            setPadding(dp(4f), dp(2f), dp(2f), dp(10f))
        }

        val (icon, title) = when (currentMode) {
            MODE_CUSTOMIZE -> "✦  " to "Customize Launcher"
            MODE_WIFI -> "📶  " to "Network & Internet"
            MODE_ALERTS -> "🔔  " to "Alerts & Notifications"
            MODE_POWER -> "⏻  " to "Power & System"
            MODE_WALLPAPER_EDIT -> "🖼  " to "Wallpaper Studio"
            MODE_SUB_INPUTS -> "📺  " to "TV Inputs & HDMI"
            MODE_SUB_PICTURE -> "🎨  " to "Picture & Display"
            MODE_SUB_SOUND -> "🔊  " to "Sound & Audio Out"
            else -> "⚡  " to "Control Center"
        }
        topRow.addView(text("\$icon\$title", 13.5f, Color.WHITE, true))
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
            setPadding(dp(2f), dp(2f), dp(2f), dp(16f))
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
            else -> buildControlCenter(panel)
        }
        if (isInitialOpen) panel.post { panel.requestFocus() }
    }
        private fun buildControlCenter(panel: LinearLayout) {
        val sysRow = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        sysRow.addView(text("GOOGLE TV QUICK PANELS", 9f, lp.accentColor, true)); sysRow.addView(View(context), LinearLayout.LayoutParams(0, 1, 1f))
        val sysBtn = text("⚙ All Settings", 9.5f, Color.WHITE, true).apply {
            setPadding(dp(10f), dp(4f), dp(10f), dp(4f)); isFocusable = true; background = glassCardBg(false)
            setOnFocusChangeListener { v, f -> MotionEngine.animateFocus(v, f, lp.motionStyle, 1.04f, 3f); v.background = glassCardBg(f) }
            setOnClickListener { launch(Settings.ACTION_SETTINGS) }
        }
        sysRow.addView(sysBtn); panel.addView(sysRow, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(8f) })

        val gridRow1 = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL; clipChildren = false; clipToPadding = false }
        gridRow1.addView(makeGridTile("📺", "TV Inputs", "HDMI & Sources ›") { open(MODE_SUB_INPUTS) }, LinearLayout.LayoutParams(0, dp(58f), 1f).apply { rightMargin = dp(6f) })
        gridRow1.addView(makeGridTile("🎨", "Picture", "\$selectedPicturePreset ›") { open(MODE_SUB_PICTURE) }, LinearLayout.LayoutParams(0, dp(58f), 1f))
        panel.addView(gridRow1, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(6f) })

        val gridRow2 = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL; clipChildren = false; clipToPadding = false }
        gridRow2.addView(makeGridTile("🔊", "Audio Out", getActiveAudioDeviceName()) { open(MODE_SUB_SOUND) }, LinearLayout.LayoutParams(0, dp(58f), 1f).apply { rightMargin = dp(6f) })
        gridRow2.addView(makeGridTile("🖼", "Wallpaper", "Live & Gallery ›") { open(MODE_WALLPAPER_EDIT) }, LinearLayout.LayoutParams(0, dp(58f), 1f))
        panel.addView(gridRow2, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(8f) })

        addLabel(panel, "REAL-TIME BRIGHTNESS & VOLUME")
        addSliderCard(panel, "☀  Display Brightness", brightnessPct) { p ->
            brightnessPct = p
            applyRealBrightness(p)
        }

        val am = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        val maxVol = am?.getStreamMaxVolume(AudioManager.STREAM_MUSIC) ?: 15
        val curVol = am?.getStreamVolume(AudioManager.STREAM_MUSIC) ?: 10
        val volPct = ((curVol * 100f) / maxOf(1, maxVol)).toInt()
        addSliderCard(panel, "🔊  TV Audio Volume", volPct) { p ->
            am?.setStreamVolume(AudioManager.STREAM_MUSIC, ((p / 100f) * maxVol).toInt(), AudioManager.FLAG_SHOW_UI)
        }

        addLabel(panel, "SYSTEM & CONNECTIVITY")
        val ramSummary = getRamStatusText()
        addActionButton(panel, "🚀", "Boost Memory (RAM)", ramSummary) {
            Runtime.getRuntime().gc()
            Toast.makeText(context, "Memory optimized! \${getRamStatusText()}", Toast.LENGTH_SHORT).show()
            populateActivePanel(false)
        }
        addActionButton(panel, "📶", "Wi-Fi & Network", "Signal, IP & nearby networks ›") { open(MODE_WIFI) }
        addActionButton(panel, "🔵", "Bluetooth Devices", "Remotes, speakers & controllers") { launch(Settings.ACTION_BLUETOOTH_SETTINGS) }
        addActionButton(panel, "📱", "Installed Apps", "Storage, cache & permissions") { launch(Settings.ACTION_APPLICATION_SETTINGS) }
    }

    private fun getCurrentSystemBrightnessPct(): Int {
        return try {
            val raw = Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS, 200)
            ((raw / 255f) * 100f).toInt().coerceIn(10, 100)
        } catch (_: Exception) {
            85
        }
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
            addActionButton(panel, "🔌", "HDMI 1 / External Source", "Open TV Input Switcher") {
                launch("android.media.tv.action.SETUP_INPUTS")
            }
            addActionButton(panel, "📡", "Live Channels / TV", "Open Live TV tuner") {
                try { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("content://android.media.tv/channel"))) } catch (_: Exception) { launch(Settings.ACTION_SETTINGS) }
            }
        } else {
            for (inp in inputs) {
                val lbl = try { inp.loadLabel(context).toString() } catch (_: Exception) { inp.id }
                addActionButton(panel, "📺", lbl, "Tap to switch input source") {
                    try {
                        val i = inp.createSetupIntent() ?: Intent(Settings.ACTION_SETTINGS)
                        context.startActivity(i)
                    } catch (_: Exception) { launch(Settings.ACTION_SETTINGS) }
                }
            }
        }
        addBackSubButton(panel)
    }

    private fun buildPictureSubPanel(panel: LinearLayout) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.System.canWrite(context)) {
            addActionButton(panel, "🔓", "Grant System Brightness Access", "Allow launcher to control hardware backlight") {
                try {
                    val i = Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:\${context.packageName}"))
                    context.startActivity(i)
                } catch (_: Exception) { launch(Settings.ACTION_DISPLAY_SETTINGS) }
            }
        }

        addLabel(panel, "PICTURE PRESET MODES")
        val presets = listOf("Standard", "Cinema / Movie", "Vivid", "Night Eye-Care")
        val presetBrightness = listOf(80, 65, 100, 35)

        presets.forEachIndexed { idx, name ->
            val isSel = selectedPicturePreset == name
            val card = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(12f), dp(9f), dp(12f), dp(9f)); isFocusable = true; background = glassCardBg(isSel)
                setOnFocusChangeListener { v, f -> MotionEngine.animateFocus(v, f, lp.motionStyle, 1.02f, 3f); v.background = glassCardBg(f || isSel) }
                setOnClickListener {
                    selectedPicturePreset = name
                    brightnessPct = presetBrightness[idx]
                    applyRealBrightness(brightnessPct)
                    populateActivePanel(false)
                }
            }
            card.addView(text(if (isSel) "✓  \$name" else "○  \$name", 10.5f, if (isSel) lp.accentColor else Color.WHITE, true), LinearLayout.LayoutParams(0, -2, 1f))
            card.addView(text("\${presetBrightness[idx]}%", 9f, Color.argb(190, 220, 235, 255), true))
            panel.addView(card, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(6f) })
        }

        addLabel(panel, "SYSTEM DISPLAY MODES")
        val modes = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            (context as? Activity)?.windowManager?.defaultDisplay?.supportedModes?.take(3) ?: emptyList()
        } else emptyList()

        for (m in modes) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                addActionButton(panel, "🖥", "\${m.physicalWidth} × \${m.physicalHeight}", "\${m.refreshRate.toInt()} Hz Native Resolution") {
                    launch(Settings.ACTION_DISPLAY_SETTINGS)
                }
            }
        }
        addActionButton(panel, "⚙", "System Display Settings", "HDR, resolution & color space") { launch(Settings.ACTION_DISPLAY_SETTINGS) }
        addBackSubButton(panel)
    }

    private fun buildSoundSubPanel(panel: LinearLayout) {
        addLabel(panel, "ACTIVE AUDIO OUTPUT")
        val am = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        val devices = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            am?.getDevices(AudioManager.GET_DEVICES_OUTPUTS)?.toList() ?: emptyList()
        } else emptyList()

        if (devices.isEmpty()) {
            addActionButton(panel, "🔊", "TV Internal Speakers", "Default stereo output") { launch(Settings.ACTION_SOUND_SETTINGS) }
        } else {
            devices.distinctBy { it.type }.take(4).forEach { dev ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    val dName = mapAudioType(dev.type)
                    val pName = dev.productName?.toString()?.takeIf { it.isNotBlank() } ?: "Active Output"
                    addActionButton(panel, "🎧", dName, pName) { launch(Settings.ACTION_SOUND_SETTINGS) }
                }
            }
        }

        val isMuted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) am?.isStreamMute(AudioManager.STREAM_MUSIC) == true else false
        addActionButton(panel, if (isMuted) "🔇" else "🔈", if (isMuted) "Unmute TV Audio" else "Mute TV Audio", "Instant audio toggle") {
                        am?.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_TOGGLE_MUTE, AudioManager.FLAG_SHOW_UI)
            populateActivePanel(false)
        }
        addActionButton(panel, "⚙", "System Sound Settings", "Dolby, eARC & equalizer") { launch(Settings.ACTION_SOUND_SETTINGS) }
        addBackSubButton(panel)
    }

    private fun addBackSubButton(panel: LinearLayout) {
        val backBtn = TextView(context).apply {
            text = "‹  Back to Control Center"; textSize = 10.5f; typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE); gravity = Gravity.CENTER; isFocusable = true
            background = glassCardBg(true)
            setOnFocusChangeListener { v, f -> MotionEngine.animateFocus(v, f, lp.motionStyle, 1.03f, 3f) }
            setOnClickListener { open(MODE_QUICK) }
        }
        panel.addView(backBtn, LinearLayout.LayoutParams(-1, dp(40f)).apply { topMargin = dp(10f); bottomMargin = dp(20f) })
    }

    private fun getActiveAudioDeviceName(): String {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val am = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            val out = am?.getDevices(AudioManager.GET_DEVICES_OUTPUTS)?.lastOrNull()
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

    private fun getRamStatusText(): String {
        return try {
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val mi = ActivityManager.MemoryInfo()
            am.getMemoryInfo(mi)
            val freeMb = mi.availMem / (1024 * 1024)
            val totalMb = mi.totalMem / (1024 * 1024)
            "Free: ${freeMb}MB / ${totalMb}MB • Tap to boost"
        } catch (_: Exception) { "Optimize background memory" }
    }

    private fun buildRealAlertsPanel(panel: LinearLayout) {
        val hasPerm = TvNotificationService.isPermissionGranted(context)
        if (!hasPerm) {
            addLabel(panel, "PERMISSION REQUIRED")
            addActionButton(panel, "🔓", "Enable Notification Access", "Allow Shree TV Home to show real system & app alerts") {
                launch(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
            }
            return
        }

        val alerts = TvNotificationService.getAlerts()
        val topBar = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        topBar.addView(text("LIVE SYSTEM ALERTS (${alerts.size})", 9f, lp.accentColor, true))
        topBar.addView(View(context), LinearLayout.LayoutParams(0, 1, 1f))
        if (alerts.isNotEmpty()) {
            val clearAll = text("Clear All", 9f, Color.WHITE, true).apply {
                setPadding(dp(8f), dp(3f), dp(8f), dp(3f)); isFocusable = true; background = glassCardBg(false)
                setOnFocusChangeListener { v, f -> MotionEngine.animateFocus(v, f, lp.motionStyle, 1.03f, 3f); v.background = glassCardBg(f) }
                setOnClickListener { TvNotificationService.clearAllAlerts(); populateActivePanel(false) }
            }
            topBar.addView(clearAll)
        }
        panel.addView(topBar, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(8f) })

        if (alerts.isEmpty()) {
            val emptyTv = text("No active notifications right now", 10.5f, Color.argb(180, 210, 230, 250), false).apply {
                gravity = Gravity.CENTER; setPadding(0, dp(28f), 0, dp(28f))
            }
            panel.addView(emptyTv, LinearLayout.LayoutParams(-1, -2)); return
        }

        for (item in alerts) {
            val card = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL; setPadding(dp(11f), dp(9f), dp(11f), dp(9f)); isFocusable = true; background = glassCardBg(false)
                setOnFocusChangeListener { v, f -> MotionEngine.animateFocus(v, f, lp.motionStyle, 1.02f, 4f); v.background = glassCardBg(f) }
                setOnClickListener { TvNotificationService.dismissAlert(item.key); populateActivePanel(false) }
            }
            val row1 = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
            row1.addView(text("🔔  ${item.appName}", 9f, lp.accentColor, true), LinearLayout.LayoutParams(0, -2, 1f))
            row1.addView(text("${item.timeStr}  ✕", 8.5f, Color.argb(180, 255, 255, 255), true))
            card.addView(row1)
            card.addView(text(item.title, 10.5f, Color.WHITE, true).apply { isSingleLine = true; ellipsize = TextUtils.TruncateAt.END; setPadding(0, dp(3f), 0, dp(1f)) })
            card.addView(text(item.text, 9f, Color.argb(195, 215, 232, 250), false).apply { maxLines = 2; ellipsize = TextUtils.TruncateAt.END })
            panel.addView(card, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(7f) })
        }
    }

    private fun buildPowerPanel(panel: LinearLayout) {
        addLabel(panel, "DEVICE POWER & REBOOT")
        addActionButton(panel, "🌙", "Sleep / Screensaver", "Put TV display to rest") {
            try { context.startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)) } catch (_: Exception) {}
            close()
        }
        addActionButton(panel, "🔄", "Refresh Launcher", "Reload all installed TV apps") {
            AppGridManager.clearCache(); onLiveRefresh(); close()
        }
        addActionButton(panel, "⚙", "Android TV Settings", "Device preferences & reboot") { launch(Settings.ACTION_SETTINGS) }
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
        val box = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(20f), dp(14f), dp(20f), dp(10f)) }
        val nameInput = EditText(context).apply {
            isSingleLine = true; hint = "Profile Name (e.g. Shree)"; setText(lp.userName)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_WORDS
        }
        val phraseInput = EditText(context).apply {
            isSingleLine = true; hint = "Custom Greeting Phrase (Optional)"; setText(lp.greetingPhrase)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
        }
        val pickPicBtn = TextView(context).apply {
            text = "🖼  Choose Custom Profile Picture (3x Avatar)"
            textSize = 11f; typeface = Typeface.DEFAULT_BOLD; setTextColor(Color.WHITE)
            gravity = Gravity.CENTER; isFocusable = true; setPadding(dp(12f), dp(10f), dp(12f), dp(10f))
            background = glassCardBg(true)
            setOnClickListener { onPickProfilePic() }
        }
        box.addView(nameInput)
        box.addView(phraseInput, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(10f) })
        box.addView(pickPicBtn, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(14f) })

        AlertDialog.Builder(context).setTitle("Profile Name & Avatar").setView(box).setPositiveButton("Save") { _, _ ->
            lp.userName = nameInput.text.toString().trim()
            lp.greetingPhrase = phraseInput.text.toString().trim()
            onLiveRefresh()
            if (isOpen) populateActivePanel(false)
        }.setNegativeButton("Cancel", null).show()
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

    private fun launch(act: String) {
        try { context.startActivity(Intent(act)) } catch (_: Exception) {
            val fallback = when (act) {
                Settings.ACTION_DISPLAY_SETTINGS -> Intent("com.android.tv.settings.DISPLAY")
                Settings.ACTION_SOUND_SETTINGS -> Intent("com.android.tv.settings.SOUND")
                Settings.ACTION_APPLICATION_SETTINGS -> Intent("com.android.tv.settings.DEVICE_INFO")
                else -> Intent(Settings.ACTION_SETTINGS)
            }
            try { context.startActivity(fallback) } catch (_: Exception) {
                try { context.startActivity(Intent(Settings.ACTION_SETTINGS)) } catch (_: Exception) { Toast.makeText(context, "Settings unavailable on this TV", Toast.LENGTH_SHORT).show() }
            }
        }
    }

    private fun xBtnBg(foc: Boolean) = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(if (foc) Color.argb(165, 255, 75, 85) else Color.argb(55, 255, 255, 255)); setStroke(dp(1f), if (foc) Color.WHITE else Color.argb(85, 255, 255, 255)) }
    private fun glassCardBg(foc: Boolean) = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, if (foc) intArrayOf(Color.argb(115, 255, 255, 255), Color.argb(75, 255, 255, 255)) else intArrayOf(Color.argb(52, 255, 255, 255), Color.argb(26, 255, 255, 255))).apply { cornerRadius = LauncherUtils.dpF(context, 14f); setStroke(dp(if (foc) 2f else 1f), if (foc) lp.accentColor else Color.argb(65, 255, 255, 255)) }
    private fun text(v: String, sz: Float, col: Int, bld: Boolean) = TextView(context).apply { text = v; textSize = sz; setTextColor(col); if (bld) typeface = Typeface.DEFAULT_BOLD }
    private fun addLabel(panel: LinearLayout, lbl: String) = panel.addView(text(lbl.uppercase(Locale.ROOT), 9f, lp.accentColor, true).apply { letterSpacing = 0.09f }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8f); bottomMargin = dp(5f) })
    private fun dp(v: Float) = LauncherUtils.dpF(context, v).toInt()
}
