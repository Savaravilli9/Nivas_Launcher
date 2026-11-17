package com.shreenivas.tvhome

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.ScanResult
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.provider.Settings
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

object WifiPanelBuilder {

    @Suppress("DEPRECATION")
    fun build(ctx: Context, panel: LinearLayout, lp: LauncherPreferences, onRefresh: () -> Unit) {
        val wm = ctx.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
        val isWifiOn = try { wm?.isWifiEnabled ?: true } catch (_: Exception) { true }
        val connectedSsid = getConnectedSsid(ctx, wm)
        val info = try { wm?.connectionInfo } catch (_: Exception) { null }
        val speedMbps = info?.linkSpeed ?: -1
        val freq = info?.frequency ?: 0

        addSectionLabel(ctx, panel, "CONNECTION TELEMETRY")
        val teleRow = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL }
        val statusVal = when {
            !isWifiOn -> "Disabled"
            connectedSsid != null -> "Active"
            else -> "Ready"
        }
        val speedVal = when {
            !isWifiOn -> "0 Mbps"
            speedMbps > 0 -> "$speedMbps Mbps"
            freq > 4900 -> "5.0 GHz"
            freq > 2400 -> "2.4 GHz"
            else -> "Standby"
        }
        teleRow.addView(
            makeTelemetryCard(ctx, "📶", "Status", statusVal, if (connectedSsid != null) Color.rgb(46, 232, 138) else lp.accentColor),
            LinearLayout.LayoutParams(0, -2, 1f).apply { rightMargin = dp(ctx, 8) }
        )
        teleRow.addView(
            makeTelemetryCard(ctx, "⚡", "Speed", speedVal, lp.accentColor),
            LinearLayout.LayoutParams(0, -2, 1f)
        )
        panel.addView(teleRow, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(ctx, 10) })

        val toggleRow = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(ctx, 12), dp(ctx, 10), dp(ctx, 12), dp(ctx, 10))
            isFocusable = true
            isClickable = true
            background = glassCardBg(ctx, false, lp.accentColor)
            setOnFocusChangeListener { v, f ->
                LauncherUtils.applyFocusSpring(v, f, 1.02f, 4f)
                v.background = glassCardBg(ctx, f, lp.accentColor)
            }
            setOnClickListener {
                val changed = try { wm?.setWifiEnabled(!isWifiOn) ?: false } catch (_: Exception) { false }
                if (changed) {
                    panel.postDelayed({ onRefresh() }, 500L)
                } else {
                    openSystemWifi(ctx)
                }
            }
        }
        val wMid = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
        wMid.addView(makeText(ctx, "Wi-Fi", 12f, Color.WHITE, true))
        wMid.addView(makeText(ctx, if (isWifiOn) "On" else "Off", 9f, Color.argb(185, 205, 225, 250), false))
        toggleRow.addView(wMid, LinearLayout.LayoutParams(0, -2, 1f))

        val switchTrack = LinearLayout(ctx).apply {
            gravity = if (isWifiOn) Gravity.END or Gravity.CENTER_VERTICAL else Gravity.START or Gravity.CENTER_VERTICAL
            setPadding(dp(ctx, 3), dp(ctx, 3), dp(ctx, 3), dp(ctx, 3))
            background = GradientDrawable().apply {
                setColor(if (isWifiOn) lp.accentColor else Color.argb(75, 255, 255, 255))
                cornerRadius = LauncherUtils.dpF(ctx, 14f)
            }
        }
        val switchThumb = View(ctx).apply {
            background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(Color.WHITE) }
        }
        switchTrack.addView(switchThumb, LinearLayout.LayoutParams(dp(ctx, 16), dp(ctx, 16)))
        toggleRow.addView(switchTrack, LinearLayout.LayoutParams(dp(ctx, 40), dp(ctx, 22)))
        panel.addView(toggleRow, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(ctx, 10) })

        if (isWifiOn && connectedSsid != null) {
            addSectionLabel(ctx, panel, "CONNECTED")
            val channel = if (freq > 5000) ((freq - 5000) / 5) else if (freq > 2400) ((freq - 2407) / 5) else 36
            val bandStr = if (freq > 4900) "5.0 GHz • Ch $channel" else "2.4 GHz • Ch $channel"

            val connCard = LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(ctx, 12), dp(ctx, 10), dp(ctx, 10), dp(ctx, 10))
                isFocusable = true
                background = GradientDrawable(
                    GradientDrawable.Orientation.TOP_BOTTOM,
                    intArrayOf(Color.argb(85, 32, 152, 104), Color.argb(55, 18, 88, 62))
                ).apply {
                    cornerRadius = LauncherUtils.dpF(ctx, 14f)
                    setStroke(dp(ctx, 1), Color.rgb(46, 232, 138))
                }
                setOnFocusChangeListener { v, f -> LauncherUtils.applyFocusSpring(v, f, 1.02f, 4f) }
                setOnClickListener { openSystemWifi(ctx) }
            }
            connCard.addView(makeText(ctx, "📶", 13f, Color.rgb(46, 232, 138), true).apply { setPadding(0, 0, dp(ctx, 8), 0) })
            val cMid = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
            cMid.addView(makeText(ctx, connectedSsid, 11f, Color.WHITE, true))
            cMid.addView(makeText(ctx, "Encrypted • $bandStr", 8f, Color.rgb(165, 245, 205), false))
            connCard.addView(cMid, LinearLayout.LayoutParams(0, -2, 1f))

            val discBtn = makeText(ctx, "Disconnect", 8.5f, Color.WHITE, true).apply {
                setPadding(dp(ctx, 8), dp(ctx, 5), dp(ctx, 8), dp(ctx, 5))
                background = GradientDrawable().apply {
                    setColor(Color.argb(95, 255, 85, 105))
                    setStroke(dp(ctx, 1), Color.argb(160, 255, 130, 145))
                    cornerRadius = LauncherUtils.dpF(ctx, 8f)
                }
            }
            connCard.addView(discBtn)
            panel.addView(connCard, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(ctx, 10) })
        }

        if (isWifiOn) {
            val headRow = LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(ctx, 2), dp(ctx, 2), dp(ctx, 2), dp(ctx, 6))
            }
            headRow.addView(makeText(ctx, "SAVED NETWORKS", 9f, Color.argb(195, 200, 220, 250), true))
            headRow.addView(View(ctx), LinearLayout.LayoutParams(0, 1, 1f))
            val refBtn = makeText(ctx, "↻ Scan", 9f, lp.accentColor, true).apply {
                isFocusable = true
                setPadding(dp(ctx, 7), dp(ctx, 3), dp(ctx, 7), dp(ctx, 3))
                background = glassCardBg(ctx, false, lp.accentColor)
                setOnFocusChangeListener { v, f ->
                    LauncherUtils.applyFocusSpring(v, f, 1.05f, 3f)
                    v.background = glassCardBg(ctx, f, lp.accentColor)
                }
                setOnClickListener {
                    Toast.makeText(ctx, "Scanning Wi-Fi...", Toast.LENGTH_SHORT).show()
                    onRefresh()
                }
            }
            headRow.addView(refBtn)
            panel.addView(headRow)

            loadRealWifi(ctx, panel, wm, connectedSsid, lp.accentColor)
        }

        val moreBtn = makeText(ctx, "⚙  System Network Settings ›", 10.5f, lp.accentColor, true).apply {
            gravity = Gravity.CENTER
            isFocusable = true
            setPadding(dp(ctx, 10), dp(ctx, 10), dp(ctx, 10), dp(ctx, 10))
            background = glassCardBg(ctx, false, lp.accentColor)
            setOnFocusChangeListener { v, f ->
                LauncherUtils.applyFocusSpring(v, f, 1.03f, 4f)
                v.background = glassCardBg(ctx, f, lp.accentColor)
            }
            setOnClickListener { openSystemWifi(ctx) }
        }
        panel.addView(moreBtn, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(ctx, 8); bottomMargin = dp(ctx, 6) })
    }
    @Suppress("DEPRECATION")
    fun getConnectedSsid(ctx: Context, wm: WifiManager?): String? {
        try {
            val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            val net = cm?.activeNetwork ?: return null
            val caps = cm.getNetworkCapabilities(net) ?: return null
            if (!caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) &&
                !caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
            ) return null

            if (caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) return "Wired Ethernet"

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val wifiInfo = caps.transportInfo as? WifiInfo
                val ssidQ = wifiInfo?.ssid?.replace("\"", "")
                if (!ssidQ.isNullOrEmpty() && ssidQ != "<unknown ssid>") return ssidQ
            }

            val legacySsid = wm?.connectionInfo?.ssid?.replace("\"", "")
            if (!legacySsid.isNullOrEmpty() && legacySsid != "<unknown ssid>") return legacySsid

            return "Wi-Fi Connected"
        } catch (_: Exception) {
            return null
        }
    }

    @Suppress("DEPRECATION")
    private fun loadRealWifi(
        ctx: Context, panel: LinearLayout, wm: WifiManager?,
        connectedSsid: String?, accent: Int
    ) {
        // Run scan on background thread to prevent UI freezing
        Thread {
            try {
                wm?.startScan()
                val results: List<ScanResult> = wm?.scanResults ?: emptyList()
                
                // Update UI on main thread safely
                panel.post {
                    val seen = HashSet<String>()
                    if (connectedSsid != null) seen.add(connectedSsid)

                    var count = 0
                    for (sr in results) {
                        val ssid = sr.SSID ?: continue
                        if (ssid.isEmpty() || !seen.add(ssid)) continue
                        val band = if (sr.frequency > 4900) "5.0 GHz" else "2.4 GHz"
                        val locked = sr.capabilities?.contains("WPA") == true || sr.capabilities?.contains("WEP") == true
                        val level = WifiManager.calculateSignalLevel(sr.level, 5)
                        addWifiRow(ctx, panel, ssid, "Saved • $band • Signal $level/4", locked, accent)
                        if (++count >= 5) break
                    }

                    if (count == 0) {
                        addWifiRow(ctx, panel, "Manage Saved Networks", "Tap to open Android TV Wi-Fi list", true, accent)
                    }
                }
            } catch (_: SecurityException) {
                panel.post {
                    addWifiRow(ctx, panel, "Location Permission Needed", "Grant Location to scan Wi-Fi list", false, accent)
                }
            }
        }.start()
    }

    private fun makeTelemetryCard(ctx: Context, icon: String, title: String, value: String, accent: Int) = LinearLayout(ctx).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(ctx, 10), dp(ctx, 8), dp(ctx, 10), dp(ctx, 8))
        background = glassCardBg(ctx, false, accent)
        addView(makeText(ctx, "$icon  $title", 8.5f, Color.argb(185, 200, 220, 250), true))
        addView(makeText(ctx, value, 11.5f, Color.WHITE, true).apply { setPadding(0, dp(ctx, 3), 0, 0) })
    }

    private fun addWifiRow(ctx: Context, panel: LinearLayout, ssid: String, sub: String, locked: Boolean, accent: Int) {
        val card = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(ctx, 10), dp(ctx, 8), dp(ctx, 10), dp(ctx, 8))
            isFocusable = true
            background = glassCardBg(ctx, false, accent)
            setOnFocusChangeListener { v, f ->
                LauncherUtils.applyFocusSpring(v, f, 1.02f, 4f)
                v.background = glassCardBg(ctx, f, accent)
            }
            setOnClickListener { openSystemWifi(ctx) }
        }
        card.addView(makeText(ctx, "📶", 12.5f, accent, true).apply { setPadding(0, 0, dp(ctx, 8), 0) })
        val mid = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
        mid.addView(makeText(ctx, ssid, 10.5f, Color.WHITE, true))
        mid.addView(makeText(ctx, sub, 8f, Color.argb(180, 205, 225, 250), false))
        card.addView(mid, LinearLayout.LayoutParams(0, -2, 1f))
        if (locked) card.addView(makeText(ctx, "🔒", 10f, Color.WHITE, false))
        panel.addView(card, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(ctx, 6) })
    }

    private fun openSystemWifi(ctx: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                ctx.startActivity(Intent(Settings.Panel.ACTION_INTERNET_CONNECTIVITY))
                return
            } catch (_: Exception) {
            }
        }
        try {
            ctx.startActivity(Intent(Settings.ACTION_WIFI_SETTINGS))
        } catch (_: Exception) {
        }
    }

    private fun glassCardBg(ctx: Context, foc: Boolean, accent: Int) = GradientDrawable(
        GradientDrawable.Orientation.TOP_BOTTOM,
        if (foc) intArrayOf(Color.argb(120, 255, 255, 255), Color.argb(80, 255, 255, 255))
        else intArrayOf(Color.argb(56, 255, 255, 255), Color.argb(28, 255, 255, 255))
    ).apply {
        cornerRadius = LauncherUtils.dpF(ctx, 13f)
        setStroke(dp(ctx, if (foc) 2 else 1), if (foc) accent else Color.argb(70, 255, 255, 255))
    }

    private fun addSectionLabel(ctx: Context, panel: LinearLayout, txt: String) = panel.addView(
        makeText(ctx, txt, 8.5f, Color.argb(195, 195, 218, 250), true).apply { letterSpacing = 0.07f },
        LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(ctx, 2); bottomMargin = dp(ctx, 5) }
    )

    private fun makeText(ctx: Context, txt: String, sz: Float, col: Int, bold: Boolean) = TextView(ctx).apply {
        text = txt
        textSize = sz
        setTextColor(col)
        isSingleLine = true
        ellipsize = TextUtils.TruncateAt.END
        if (bold) typeface = Typeface.DEFAULT_BOLD
    }

    private fun dp(ctx: Context, v: Int) = LauncherUtils.dp(ctx, v)
}
