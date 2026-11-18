package com.shreenivas.tvhome.ui

import com.shreenivas.tvhome.LauncherPreferences
import com.shreenivas.tvhome.LauncherUtils

import android.animation.ValueAnimator
import android.app.ActivityManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import kotlin.math.roundToInt

class TopNavBar(
    context: Context,
    private val lp: LauncherPreferences,
    private val callbacks: NavCallbacks
) : LinearLayout(context) {

    interface NavCallbacks {
        fun onVoiceSearch()
        fun onWifiClick()
        fun onAlertsClick()
        fun onCustomizeClick()
        fun onQuickSettingsClick()
        fun onPowerClick()
    }

    private lateinit var dateTv: TextView
    private lateinit var timeTv: TextView
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
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        clipChildren = false
        clipToPadding = false
        setPadding(0, dp(4), 0, dp(16))

        val accountPill = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            isFocusable = true
            isClickable = true
            setPadding(dp(8), dp(5), dp(14), dp(5))
            background = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(Color.argb(85, 255, 255, 255), Color.argb(35, 255, 255, 255))
            ).apply {
                cornerRadius = LauncherUtils.dpF(context, 20f)
                setStroke(dp(1), Color.argb(95, 255, 255, 255))
            }
            setOnFocusChangeListener { v, f -> LauncherUtils.applyFocusSpring(v, f, 1.05f, 4f) }
            setOnClickListener { callbacks.onCustomizeClick() }
        }
        val avatarCircle = TextView(context).apply {
            val initial = lp.userName.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "U"
            text = initial
            textSize = 12f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(lp.accentColor)
                setStroke(dp(1), Color.WHITE)
            }
        }
        accountPill.addView(avatarCircle, LayoutParams(dp(26), dp(26)).apply { rightMargin = dp(8) })

        val accTextCol = LinearLayout(context).apply { orientation = VERTICAL }
        dateTv = makeText(lp.userName.ifBlank { "Home Profile" }, 11f, Color.WHITE, true)
        val detectedEmail = lp.userEmail.ifBlank { detectDeviceEmail(context) }
        timeTv = makeText(detectedEmail, 8.8f, Color.argb(210, 215, 235, 255), false)
        accTextCol.addView(dateTv)
        accTextCol.addView(timeTv)
        accountPill.addView(accTextCol)
        addView(accountPill)

        addView(
            View(context).apply { setBackgroundColor(Color.argb(65, 255, 255, 255)) },
            LayoutParams(dp(1), dp(28)).apply { leftMargin = dp(14); rightMargin = dp(14) }
        )

        greetingCard = GreetingShineCard(context, lp.userName, lp.greetingPhrase, lp.accentColor)
        addView(greetingCard, LayoutParams(-2, dp(40)))

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

        addView(View(context), LayoutParams(0, 1, 1f))

        val voicePill = makeVectorPill("mic", "Voice Search", Color.rgb(120, 195, 255)).apply {
            setOnClickListener { callbacks.onVoiceSearch() }
        }
        addView(voicePill, LayoutParams(-2, dp(38)))
                ramTv = makeText("RAM --%", 12.5f, Color.WHITE, true)
        val ramPill = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(dp(13), 0, dp(13), 0)
            isFocusable = true
            isClickable = true
            addView(VectorIconView(context, "bolt", Color.rgb(255, 215, 95)), LayoutParams(dp(15), dp(15)).apply { rightMargin = dp(6) })
            addView(ramTv)
            background = pillBg(false, lp.accentColor)
            setOnFocusChangeListener { v, f ->
                LauncherUtils.applyFocusSpring(v, f, 1.06f, 6f)
                v.background = pillBg(f, lp.accentColor)
            }
            setOnClickListener {
                System.gc()
                updateRamInfo()
                Toast.makeText(context, "Memory refreshed • ${ramTv.text}", Toast.LENGTH_SHORT).show()
            }
        }
        addView(ramPill, LayoutParams(-2, dp(38)).apply { leftMargin = dp(8) })

        val wifiWrapper = FrameLayout(context).apply {
            clipChildren = false
            clipToPadding = false
        }
        wifiIconView = VectorIconView(context, "wifi", Color.WHITE)
        wifiLabelTv = makeText("WI-FI", 13f, Color.WHITE, true)
        wifiPill = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(dp(14), 0, dp(14), 0)
            isFocusable = true
            isClickable = true
            addView(wifiIconView, LayoutParams(dp(16), dp(16)).apply { rightMargin = dp(6) })
            addView(wifiLabelTv)
            background = pillBg(false, lp.accentColor)
            setOnFocusChangeListener { v, f ->
                LauncherUtils.applyFocusSpring(v, f, 1.07f, 8f)
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

        wifiWrapper.addView(wifiPill, FrameLayout.LayoutParams(-2, dp(38), Gravity.TOP or Gravity.CENTER_HORIZONTAL))

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
        addView(wifiWrapper, LayoutParams(-2, dp(38)).apply { leftMargin = dp(8) })

        addView(makeVectorCircleButton("bell", Color.rgb(255, 210, 95)) { callbacks.onAlertsClick() }, LayoutParams(dp(38), dp(38)).apply { leftMargin = dp(8) })
        addView(makeVectorCircleButton("sparkle", Color.rgb(175, 145, 255)) { callbacks.onCustomizeClick() }, LayoutParams(dp(38), dp(38)).apply { leftMargin = dp(8) })
        addView(makeVectorCircleButton("gear", Color.WHITE) { callbacks.onQuickSettingsClick() }, LayoutParams(dp(38), dp(38)).apply { leftMargin = dp(8) })
        addView(makeVectorCircleButton("power", Color.rgb(255, 115, 130)) { callbacks.onPowerClick() }, LayoutParams(dp(38), dp(38)).apply { leftMargin = dp(8) })

        updateClockRamAndWifi()
    }

    fun createFloatingNudgeView(): TextView = nudgeOverlayBubble

    private fun updateClockRamAndWifi() {
        dateTv.text = lp.userName.ifBlank { "Home Profile" }
        timeTv.text = lp.userEmail.ifBlank { detectDeviceEmail(context) }
        refreshGreeting()
        updateRamInfo()
        updateWifiVisualState()
    }

    private fun detectDeviceEmail(ctx: Context): String {
        return try {
            val am = android.accounts.AccountManager.get(ctx)
            am.accounts.firstOrNull { it.name.contains("@") }?.name ?: "Signed-in Account"
        } catch (_: Exception) {
            "Signed-in Account"
        }
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
        private fun makeVectorPill(icon: String, label: String, iconTint: Int) = LinearLayout(context).apply {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER
        setPadding(dp(14), 0, dp(15), 0)
        isFocusable = true
        isClickable = true
        addView(VectorIconView(context, icon, iconTint), LayoutParams(dp(16), dp(16)).apply { rightMargin = dp(7) })
        addView(makeText(label, 13f, Color.WHITE, true))
        background = pillBg(false, lp.accentColor)
        setOnFocusChangeListener { v, f ->
            LauncherUtils.applyFocusSpring(v, f, 1.07f, 8f)
            v.background = pillBg(f, lp.accentColor)
        }
    }

    private fun makeVectorCircleButton(icon: String, tint: Int, onClick: () -> Unit) = FrameLayout(context).apply {
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
            LauncherUtils.applyFocusSpring(v, f, 1.15f, 8f)
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
