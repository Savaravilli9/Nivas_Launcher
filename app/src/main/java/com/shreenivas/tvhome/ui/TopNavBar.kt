package com.shreenivas.tvhome.ui

import android.animation.ValueAnimator
import android.app.ActivityManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.text.TextUtils
import android.view.Gravity
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
import kotlin.math.roundToInt

class TopNavBar(
    context: Context,
    private val lp: LauncherPreferences,
    private val callbacks: NavCallbacks
) : FrameLayout(context) {

    interface NavCallbacks {
        fun onProfileClick()
        fun onVoiceSearch()
        fun onWifiClick()
        fun onAlertsClick()
        fun onCustomizeClick()
        fun onQuickSettingsClick()
        fun onPowerClick()
    }

    private lateinit var avatarImageView: ImageView
    private lateinit var avatarInitialTv: TextView
    private lateinit var greetingCard: GreetingShineCard
    private lateinit var nudgeOverlayBubble: TextView
    private lateinit var ramTv: TextView
    private lateinit var wifiPill: LinearLayout
    private lateinit var wifiIconView: VectorIconView
    private lateinit var wifiLabelTv: TextView
    private lateinit var wifiBubbleTv: TextView

    private var receiverRegistered = false
    private val handler = Handler(Looper.getMainLooper())
    private var swingAnim: ValueAnimator? = null
    private var breatheAnim: ValueAnimator? = null
    private var wifiStateMode = -1
    private var showConnectingAlt = false

    private val statusReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            updateClockRamAndWifi()
        }
    }

    private val cycleRunnable = object : Runnable {
        override fun run() {
            if (!::wifiLabelTv.isInitialized) return
            if (wifiStateMode == 1) {
                showConnectingAlt = !showConnectingAlt
                wifiLabelTv.animate().alpha(0f).setDuration(180L).withEndAction {
                    wifiLabelTv.text = if (showConnectingAlt) "Connecting..." else "WI-FI"
                    wifiLabelTv.animate().alpha(1f).setDuration(220L).start()
                }.start()
                handler.postDelayed(this, 1600L)
            } else if (wifiStateMode == 0) {
                triggerMistBubbleCycle()
                handler.postDelayed(this, 10_000L)
            }
        }
    }

    init {
        clipChildren = false
        clipToPadding = false
        setPadding(dp(4), dp(8), dp(6), dp(14))
)

        val barHeight = dp(38)
        val avatarSize = dp(92) // Large 2.5x Hero Profile Avatar centered on TopNavBar centerline

        // 1. RIGHT-ALIGNED FIXED CONTROLS (Pinned to right edge so they NEVER go off-screen)
        val rightControls = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            clipChildren = false
            clipToPadding = false
        }

        val voicePill = makeVectorPill("mic", "Voice Search", Color.rgb(120, 195, 255), "nav_voice").apply {
            setOnClickListener { callbacks.onVoiceSearch() }
        }
        rightControls.addView(voicePill, LinearLayout.LayoutParams(-2, barHeight))

        ramTv = makeText("RAM --%", 12.5f, Color.WHITE, true)
        val ramPill = LinearLayout(context).apply {
            tag = "nav_ram"
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(dp(13), 0, dp(13), 0)
            isFocusable = true
            isClickable = true
            addView(VectorIconView(context, "bolt", Color.rgb(255, 215, 95)), LinearLayout.LayoutParams(dp(15), dp(15)).apply { rightMargin = dp(6) })
            addView(ramTv)
            background = pillBg(false, lp.accentColor)
            setOnFocusChangeListener { v, f ->
                MotionEngine.animateFocus(v, f, lp.motionStyle, 1.06f, 6f)
                v.background = pillBg(f, lp.accentColor)
            }
            setOnClickListener {
                System.gc()
                updateRamInfo()
                Toast.makeText(context, "Memory refreshed • ${ramTv.text}", Toast.LENGTH_SHORT).show()
            }
        }
        rightControls.addView(ramPill, LinearLayout.LayoutParams(-2, barHeight).apply { leftMargin = dp(8) })

        val wifiWrapper = FrameLayout(context).apply {
            clipChildren = false
            clipToPadding = false
        }
        wifiIconView = VectorIconView(context, "wifi", Color.WHITE)
        wifiLabelTv = makeText("WI-FI", 13f, Color.WHITE, true)
        wifiPill = LinearLayout(context).apply {
            tag = "nav_wifi"
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(dp(14), 0, dp(14), 0)
            isFocusable = true
            isClickable = true
            addView(wifiIconView, LinearLayout.LayoutParams(dp(16), dp(16)).apply { rightMargin = dp(6) })
            addView(wifiLabelTv)
            background = pillBg(false, lp.accentColor)
            setOnFocusChangeListener { v, f ->
                MotionEngine.animateFocus(v, f, lp.motionStyle, 1.07f, 8f)
                if (wifiStateMode == 2) {
                    val green = Color.rgb(46, 232, 138)
                    v.background = GradientDrawable().apply {
                        setColor(if (f) Color.argb(125, 34, 130, 86) else Color.argb(75, 24, 98, 64))
                        setStroke(dp(if (f) 2 else 1), if (f) Color.WHITE else green)
                        cornerRadius = LauncherUtils.dpF(context, 22f)
                    }
                } else {
                    v.background = pillBg(f, lp.accentColor)
                }
            }
            setOnClickListener { callbacks.onWifiClick() }
        }

        wifiWrapper.addView(wifiPill, FrameLayout.LayoutParams(-2, barHeight, Gravity.TOP or Gravity.CENTER_HORIZONTAL))

        wifiBubbleTv = TextView(context).apply {
            textSize = 9.5f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            setPadding(dp(9), dp(2), dp(9), dp(2))
            maxWidth = dp(135)
            ellipsize = TextUtils.TruncateAt.END
            isSingleLine = true
            visibility = View.GONE
        }
        wifiWrapper.addView(
            wifiBubbleTv,
            FrameLayout.LayoutParams(-2, -2, Gravity.TOP or Gravity.CENTER_HORIZONTAL).apply { topMargin = dp(41) }
        )
        rightControls.addView(wifiWrapper, LinearLayout.LayoutParams(-2, barHeight).apply { leftMargin = dp(8) })

        rightControls.addView(makeVectorCircleButton("bell", Color.rgb(255, 210, 95), "nav_alerts") { callbacks.onAlertsClick() }, LinearLayout.LayoutParams(barHeight, barHeight).apply { leftMargin = dp(8) })
        rightControls.addView(makeVectorCircleButton("sparkle", Color.rgb(175, 145, 255), "nav_customize") { callbacks.onCustomizeClick() }, LinearLayout.LayoutParams(barHeight, barHeight).apply { leftMargin = dp(8) })
        rightControls.addView(makeVectorCircleButton("gear", Color.WHITE, "nav_quick") { callbacks.onQuickSettingsClick() }, LinearLayout.LayoutParams(barHeight, barHeight).apply { leftMargin = dp(8) })
        rightControls.addView(makeVectorCircleButton("power", Color.rgb(255, 115, 130), "nav_power") { callbacks.onPowerClick() }, LinearLayout.LayoutParams(barHeight, barHeight).apply { leftMargin = dp(8) })
        // 2. LEFT-ALIGNED HERO AVATAR + ATTACHED GREETING CARD
        val profileHeroGroup = FrameLayout(context).apply {
            clipChildren = false
            clipToPadding = false
        }

        greetingCard = GreetingShineCard(context, lp.userName, lp.greetingPhrase, lp.accentColor).apply {
            setPadding(avatarSize - dp(6), dp(7), dp(16), dp(7))
        }
        profileHeroGroup.addView(
            greetingCard,
            FrameLayout.LayoutParams(-2, dp(40), Gravity.START or Gravity.CENTER_VERTICAL).apply {
                leftMargin = dp(20)
            }
        )

        val avatarFrame = FrameLayout(context).apply {
            tag = "nav_profile"
            isFocusable = true
            isClickable = true
            outlineProvider = null
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(lp.accentColor)
                setStroke(dp(2), Color.WHITE)
            }
            setOnFocusChangeListener { v, f ->
                MotionEngine.animateFocus(v, f, lp.motionStyle, 1.08f, 12f)
                v.background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(lp.accentColor)
                    setStroke(dp(if (f) 3 else 2), if (f) lp.accentColor else Color.WHITE)
                }
            }
            setOnClickListener { callbacks.onProfileClick() }
        }
        avatarInitialTv = TextView(context).apply {
            textSize = 30f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
        }
        avatarImageView = ImageView(context).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            visibility = View.GONE
        }
        avatarFrame.addView(avatarInitialTv, FrameLayout.LayoutParams(-1, -1))
        avatarFrame.addView(avatarImageView, FrameLayout.LayoutParams(-1, -1))

        profileHeroGroup.addView(
            avatarFrame,
            FrameLayout.LayoutParams(avatarSize, avatarSize, Gravity.START or Gravity.CENTER_VERTICAL)
        )

        nudgeOverlayBubble = TextView(context).apply {
            textSize = 10.5f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.argb(250, 255, 246, 225))
            setPadding(dp(12), dp(4), dp(14), dp(4))
            isSingleLine = true
            elevation = LauncherUtils.dpF(context, 10f)
            visibility = View.GONE
        }
        greetingCard.attachOverlayBubble(nudgeOverlayBubble)

        addView(rightControls, FrameLayout.LayoutParams(-2, avatarSize, Gravity.END or Gravity.CENTER_VERTICAL))
        addView(profileHeroGroup, FrameLayout.LayoutParams(-2, avatarSize, Gravity.START or Gravity.CENTER_VERTICAL))

        post {
            val availableForLeft = width - rightControls.width - dp(16)
            if (availableForLeft > avatarSize + dp(40)) {
                greetingCard.layoutParams.width = minOf(greetingCard.measuredWidth, availableForLeft - dp(20))
                greetingCard.requestLayout()
            }
        }

        refreshProfileAvatar()
        updateClockRamAndWifi()
    }

    fun createFloatingNudgeView(): TextView = nudgeOverlayBubble

    fun refreshProfileAvatar() {
        val cleanName = lp.userName.ifBlank { "Shree" }
        val initial = cleanName.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "S"
        avatarInitialTv.text = initial

        val picUriStr = lp.prefs.getString("profile_pic_uri", null)
        if (!picUriStr.isNullOrEmpty()) {
            try {
                val uri = Uri.parse(picUriStr)
                val opts = BitmapFactory.Options().apply { inSampleSize = 2 }
                val rawBmp = context.contentResolver.openInputStream(uri)?.use {
                    BitmapFactory.decodeStream(it, null, opts)
                }
                if (rawBmp != null) {
                val zoom = lp.prefs.getFloat("profile_pic_zoom", 1.0f)
                    avatarImageView.setImageBitmap(makeCircularBitmap(rawBmp))
                    avatarImageView.scaleX = zoom
                    avatarImageView.scaleY = zoom
                    avatarImageView.visibility = View.VISIBLE
                    avatarInitialTv.visibility = View.GONE
                return
                }
            } catch (_: Exception) {}
        }
        avatarImageView.visibility = View.GONE
        avatarInitialTv.visibility = View.VISIBLE
    }

    private fun makeCircularBitmap(src: Bitmap): Bitmap {
        val size = minOf(src.width, src.height)
        val x = (src.width - size) / 2
        val y = (src.height - size) / 2
        val squared = Bitmap.createBitmap(src, x, y, size, size)
        val output = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.shader = BitmapShader(squared, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
        val r = size / 2f
        canvas.drawCircle(r, r, r, paint)
        return output
    }

    private fun updateClockRamAndWifi() {
        refreshProfileAvatar()
        refreshGreeting()
        updateRamInfo()
        updateWifiVisualState()
    }

    private fun updateRamInfo() {
        try {
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager ?: return
            val memInfo = ActivityManager.MemoryInfo()
            am.getMemoryInfo(memInfo)
            val total = memInfo.totalMem.coerceAtLeast(1L)
            val used = (total - memInfo.availMem).coerceAtLeast(0L)
            val pct = ((used.toDouble() / total.toDouble()) * 100.0).roundToInt().coerceIn(1, 99)
            ramTv.text = "RAM $pct%"
        } catch (_: Exception) {
            ramTv.text = "RAM"
        }
    }

    @Suppress("DEPRECATION")
    private var lastShownSsid = ""
    private fun updateWifiVisualState() {
        val wm = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
        val connectedSsid = TileThemePalette.getConnectedSsid(context).takeIf { it != "Wi-Fi" }
        val isWifiEnabling = try { wm?.wifiState == WifiManager.WIFI_STATE_ENABLING } catch (_: Exception) { false }

        val newMode = when {
            connectedSsid != null -> 2
            isWifiEnabling -> 1
            else -> 0
        }

        if (newMode == wifiStateMode && (newMode != 2 || connectedSsid == lastShownSsid)) return
        wifiStateMode = newMode
        lastShownSsid = connectedSsid ?: ""

        handler.removeCallbacks(cycleRunnable)
        swingAnim?.cancel()
        breatheAnim?.cancel()
        wifiPill.scaleX = 1f
        wifiPill.scaleY = 1f
        wifiPill.alpha = 1f

        when (newMode) {
            2 -> {
                val green = Color.rgb(46, 232, 138)
                wifiIconView.setIcon("wifi", green)
                wifiLabelTv.text = "WI-FI"
                wifiPill.background = GradientDrawable().apply {
                    setColor(Color.argb(75, 24, 98, 64))
                    setStroke(dp(2), green)
                    cornerRadius = LauncherUtils.dpF(context, 22f)
                }
                wifiBubbleTv.text = " $connectedSsid"
                wifiBubbleTv.alpha = 1f
                wifiBubbleTv.translationY = 0f
                wifiBubbleTv.scaleX = 1f
                wifiBubbleTv.scaleY = 1f
                wifiBubbleTv.background = GradientDrawable().apply {
                    setColor(Color.argb(205, 18, 72, 48))
                    setStroke(dp(1), green)
                    cornerRadius = LauncherUtils.dpF(context, 12f)
                }
                wifiBubbleTv.visibility = View.VISIBLE
                startPendulumSwing()
            }
            1 -> {
                wifiIconView.setIcon("wifi", Color.rgb(92, 184, 255))
                wifiBubbleTv.visibility = View.GONE
                wifiPill.background = pillBg(true, Color.rgb(92, 184, 255))
                breatheAnim = ValueAnimator.ofFloat(0.96f, 1.04f).apply {
                    duration = 950L
                    repeatMode = ValueAnimator.REVERSE
                    repeatCount = ValueAnimator.INFINITE
                    interpolator = AccelerateDecelerateInterpolator()
                    addUpdateListener {
                        val s = it.animatedValue as Float
                        wifiPill.scaleX = s
                        wifiPill.scaleY = s
                    }
                    start()
                }
                handler.post(cycleRunnable)
            }
            else -> {
                wifiIconView.setIcon("wifi", Color.WHITE)
                wifiLabelTv.text = "WI-FI"
                wifiPill.background = pillBg(false, lp.accentColor)
                wifiBubbleTv.rotation = 0f
                handler.post(cycleRunnable)
            }
        }
    }

    private fun startPendulumSwing() {
        wifiBubbleTv.post {
            wifiBubbleTv.pivotX = wifiBubbleTv.width / 2f
            wifiBubbleTv.pivotY = 0f
            swingAnim = ValueAnimator.ofFloat(-4f, 4f).apply {
                duration = 2100L
                repeatMode = ValueAnimator.REVERSE
                repeatCount = ValueAnimator.INFINITE
                interpolator = AccelerateDecelerateInterpolator()
                addUpdateListener { wifiBubbleTv.rotation = it.animatedValue as Float }
                start()
            }
        }
    }

    private fun triggerMistBubbleCycle() {
        wifiBubbleTv.animate().cancel()
        wifiBubbleTv.text = "○ No Internet"
        wifiBubbleTv.background = GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(Color.argb(165, 95, 145, 210), Color.argb(120, 55, 90, 150))
        ).apply {
            setStroke(dp(1), Color.argb(160, 185, 225, 255))
            cornerRadius = LauncherUtils.dpF(context, 12f)
        }

        wifiBubbleTv.visibility = View.VISIBLE
        wifiBubbleTv.alpha = 0f
        wifiBubbleTv.translationY = -dp(10).toFloat()
        wifiBubbleTv.scaleX = 0.88f
        wifiBubbleTv.scaleY = 0.88f
        wifiBubbleTv.animate()
            .alpha(1f).translationY(0f).scaleX(1f).scaleY(1f)
            .setDuration(420L)
            .setInterpolator(LauncherUtils.FLUID_SPRING)
            .start()

        handler.postDelayed({
            if (wifiStateMode == 0 && wifiBubbleTv.visibility == View.VISIBLE) {
                wifiBubbleTv.animate()
                    .alpha(0f)
                    .translationY(-dp(12).toFloat())
                    .scaleX(1.16f)
                    .scaleY(0.80f)
                    .setDuration(650L)
                    .setInterpolator(LauncherUtils.SILKY_EASE)
                    .withEndAction { wifiBubbleTv.visibility = View.GONE }
                    .start()
            }
        }, 4200L)
    }

    fun refreshGreeting() {
        greetingCard.updateGreeting(lp.userName, lp.greetingPhrase)
        greetingCard.setAccentColor(lp.accentColor)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (!receiverRegistered) {
            val filter = IntentFilter().apply {
                addAction(Intent.ACTION_TIME_TICK)
                addAction(Intent.ACTION_TIME_CHANGED)
                addAction(WifiManager.NETWORK_STATE_CHANGED_ACTION)
                addAction(WifiManager.WIFI_STATE_CHANGED_ACTION)
            }
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    context.registerReceiver(statusReceiver, filter, Context.RECEIVER_EXPORTED)
                } else {
                    context.registerReceiver(statusReceiver, filter)
                }
                receiverRegistered = true
            } catch (_: Exception) {
            }
            updateClockRamAndWifi()
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        handler.removeCallbacksAndMessages(null)
        swingAnim?.cancel()
        breatheAnim?.cancel()
        if (receiverRegistered) {
            try {
                context.unregisterReceiver(statusReceiver)
            } catch (_: Exception) {
            }
            receiverRegistered = false
        }
    }

    private fun makeVectorPill(icon: String, label: String, iconTint: Int, tagStr: String) = LinearLayout(context).apply {
        tag = tagStr
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER
        setPadding(dp(14), 0, dp(15), 0)
        isFocusable = true
        isClickable = true
        addView(VectorIconView(context, icon, iconTint), LinearLayout.LayoutParams(dp(16), dp(16)).apply { rightMargin = dp(7) })
        addView(makeText(label, 13f, Color.WHITE, true))
        background = pillBg(false, lp.accentColor)
        setOnFocusChangeListener { v, f ->
            MotionEngine.animateFocus(v, f, lp.motionStyle, 1.07f, 8f)
            v.background = pillBg(f, lp.accentColor)
        }
    }

    private fun makeVectorCircleButton(icon: String, tint: Int, tagStr: String, onClick: () -> Unit) = FrameLayout(context).apply {
        tag = tagStr
        isFocusable = true
        isClickable = true
        background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(Color.argb(62, 22, 32, 68))
            setStroke(dp(1), Color.argb(105, 255, 255, 255))
        }
        val iv = VectorIconView(context, icon, tint)
        addView(iv, FrameLayout.LayoutParams(dp(18), dp(18), Gravity.CENTER))
        setOnClickListener { onClick() }
        setOnFocusChangeListener { v, f ->
            MotionEngine.animateFocus(v, f, lp.motionStyle, 1.15f, 8f)
            v.background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(if (f) Color.argb(120, 255, 255, 255) else Color.argb(62, 22, 32, 68))
                setStroke(dp(if (f) 2 else 1), if (f) lp.accentColor else Color.argb(105, 255, 255, 255))
            }
            when (icon) {
                "gear" -> iv.animate().rotation(if (f) 60f else 0f).setDuration(240).start()
                "bell" -> iv.animate().rotation(if (f) 18f else 0f).setDuration(200).start()
                "sparkle" -> iv.animate().rotation(if (f) 45f else 0f).setDuration(240).start()
            }
        }
    }

    private fun pillBg(focused: Boolean, accent: Int) = GradientDrawable().apply {
        setColor(if (focused) Color.argb(110, 255, 255, 255) else Color.argb(54, 22, 32, 68))
        setStroke(dp(if (focused) 2 else 1), if (focused) accent else Color.argb(95, 255, 255, 255))
        cornerRadius = LauncherUtils.dpF(context, 22f)
    }

    private fun makeText(value: String, size: Float, color: Int, bold: Boolean) = TextView(context).apply {
        text = value
        textSize = size
        setTextColor(color)
        if (bold) typeface = Typeface.DEFAULT_BOLD
    }

    private fun dp(value: Int) = LauncherUtils.dp(context, value)
}
