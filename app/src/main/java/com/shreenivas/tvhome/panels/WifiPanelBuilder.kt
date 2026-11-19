package com.shreenivas.tvhome.panels

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.provider.Settings
import android.text.TextUtils
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.shreenivas.tvhome.LauncherPreferences
import com.shreenivas.tvhome.LauncherUtils
import java.net.Inet4Address
import java.net.NetworkInterface

object WifiPanelBuilder {

    fun build(
        ctx: Context,
        panel: LinearLayout,
        lp: LauncherPreferences,
        onRefreshPanel: () -> Unit
    ) {
        panel.clipChildren = false
        panel.clipToPadding = false
        val wm = ctx.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
        val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        val isWifiEnabled = wm?.isWifiEnabled ?: false

        val caps = cm?.getNetworkCapabilities(cm.activeNetwork)
        val isConnectedWifi = caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
        val isConnectedEth = caps?.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) == true

        @Suppress("DEPRECATION")
        val info = wm?.connectionInfo
        val rawSsid = info?.ssid?.replace("\"", "") ?: ""
        val ssidName = when {
            isConnectedEth -> "Wired Ethernet"
            isConnectedWifi && rawSsid.isNotEmpty() && rawSsid != "<unknown ssid>" -> rawSsid
            isConnectedWifi -> "Connected Wi-Fi"
            isWifiEnabled -> "Not Connected"
            else -> "Wi-Fi Turned Off"
        }

        val rssi = info?.rssi ?: -100
        val signalPct = if (isConnectedWifi) WifiManager.calculateSignalLevel(rssi, 100) else 0
        val signalLabel = when {
            isConnectedEth -> "Gigabit Wired"
            !isConnectedWifi -> "Offline"
            signalPct >= 75 -> "Excellent ($signalPct%)"
            signalPct >= 50 -> "Good ($signalPct%)"
            else -> "Fair ($signalPct%)"
        }
        val linkSpeed = if (isConnectedWifi && (info?.linkSpeed ?: 0) > 0) "${info?.linkSpeed} Mbps" else "—"
        val ipAddr = getLocalIpAddress() ?: "No IP"

        val heroCard = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(ctx, 13), dp(ctx, 12), dp(ctx, 13), dp(ctx, 12))
            background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                if (isConnectedWifi || isConnectedEth)
                    intArrayOf(LauncherUtils.withAlpha(lp.accentColor, 130), Color.argb(145, 18, 28, 64))
                else
                    intArrayOf(Color.argb(85, 45, 52, 78), Color.argb(120, 22, 26, 44))
            ).apply {
                cornerRadius = LauncherUtils.dpF(ctx, 16f)
                setStroke(dp(ctx, 1), Color.argb(115, 255, 255, 255))
            }
        }

        val topRow = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        topRow.addView(TextView(ctx).apply {
            text = if (isConnectedEth) "🔌" else "📶"
            textSize = 18f
            setPadding(0, 0, dp(ctx, 9), 0)
        })
        val nameBox = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
        nameBox.addView(TextView(ctx).apply {
            text = ssidName; textSize = 12f; typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE); isSingleLine = true; ellipsize = TextUtils.TruncateAt.END
        })
        nameBox.addView(TextView(ctx).apply {
            text = if (isConnectedWifi || isConnectedEth) "● Online • $signalLabel" else "○ $signalLabel"
            textSize = 9f; typeface = Typeface.DEFAULT_BOLD
            setTextColor(if (isConnectedWifi || isConnectedEth) Color.rgb(110, 245, 175) else Color.argb(190, 220, 230, 245))
        })
        topRow.addView(nameBox, LinearLayout.LayoutParams(0, -2, 1f))
        heroCard.addView(topRow)

        val statsRow = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(ctx, 10), 0, 0)
        }
        statsRow.addView(makeStatChip(ctx, "SPEED", linkSpeed), LinearLayout.LayoutParams(0, -2, 1f).apply { rightMargin = dp(ctx, 6) })
        statsRow.addView(makeStatChip(ctx, "IP ADDRESS", ipAddr), LinearLayout.LayoutParams(0, -2, 1f))
        heroCard.addView(statsRow)
        panel.addView(heroCard, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(ctx, 10) })
        val actionRow = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            clipChildren = false
            clipToPadding = false
        }
        actionRow.addView(
            makeGlassBtn(ctx, lp, "🔄  Refresh Scan") {
                @Suppress("DEPRECATION")
                try { wm?.startScan() } catch (_: Exception) {}
                onRefreshPanel()
                panel.post { panel.findViewWithTag<android.view.View>("btn_wifi_refresh")?.requestFocus() }
            }.apply { tag = "btn_wifi_refresh" },
            LinearLayout.LayoutParams(0, dp(ctx, 38), 1f).apply { rightMargin = dp(6) }
        )
        actionRow.addView(
            makeGlassBtn(ctx, lp, "⚙  TV Wi-Fi Settings") {
                openWifiSettings(ctx)
            },
            LinearLayout.LayoutParams(0, dp(ctx, 38), 1f)
        )
        panel.addView(actionRow, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(ctx, 10) })

        addHeader(ctx, panel, "NEARBY AVAILABLE NETWORKS")
        val hasLocPerm = ctx.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        @Suppress("DEPRECATION")
        val scanResults = if (hasLocPerm) {
            try {
                wm?.scanResults
                    ?.filter { !it.SSID.isNullOrBlank() }
                    ?.distinctBy { it.SSID }
                    ?.sortedByDescending { it.level }
                    ?.take(6) ?: emptyList()
            } catch (_: Exception) { emptyList() }
        } else emptyList()

        if (scanResults.isEmpty()) {
            val msg = if (!hasLocPerm) "Grant Location permission or tap TV Wi-Fi Settings to browse networks."
            else "No nearby networks cached. Tap TV Wi-Fi Settings to connect."
            panel.addView(TextView(ctx).apply {
                text = msg; textSize = 9.5f; setTextColor(Color.argb(195, 210, 228, 248))
                setPadding(dp(ctx, 6), dp(ctx, 10), dp(ctx, 6), dp(ctx, 12))
            })
        } else {
            for (net in scanResults) {
                val lvl = WifiManager.calculateSignalLevel(net.level, 4)
                val bars = when (lvl) { 3 -> "▂▄▆█"; 2 -> "▂▄▆"; 1 -> "▂▄"; else -> "▂" }
                val isSecured = net.capabilities.contains("WEP") || net.capabilities.contains("PSK") || net.capabilities.contains("EAP")
                val isCurrent = net.SSID == rawSsid

                val itemCard = LinearLayout(ctx).apply {
                    orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
                    setPadding(dp(ctx, 11), dp(ctx, 8), dp(ctx, 11), dp(ctx, 8)); isFocusable = true
                    background = cardBg(ctx, false, isCurrent, lp.accentColor)
                    setOnFocusChangeListener { v, f ->
                        LauncherUtils.applyFocusSpring(v, f, 1.02f, 3f)
                        v.background = cardBg(ctx, f, isCurrent, lp.accentColor)
                    }
                    setOnClickListener { openWifiSettings(ctx) }
                }
                val left = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
                left.addView(TextView(ctx).apply {
                    text = net.SSID; textSize = 10.5f; typeface = Typeface.DEFAULT_BOLD
                    setTextColor(Color.WHITE); isSingleLine = true; ellipsize = TextUtils.TruncateAt.END
                })
                left.addView(TextView(ctx).apply {
                    text = if (isCurrent) "Connected" else if (isSecured) "Secured • Tap to connect" else "Open Network"
                    textSize = 8.2f; setTextColor(if (isCurrent) lp.accentColor else Color.argb(185, 205, 225, 245))
                })
                itemCard.addView(left, LinearLayout.LayoutParams(0, -2, 1f))
                itemCard.addView(TextView(ctx).apply {
                    text = if (isSecured) "🔒 $bars" else bars
                    textSize = 9.5f; typeface = Typeface.DEFAULT_BOLD; setTextColor(Color.WHITE)
                })
                panel.addView(itemCard, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(ctx, 6) })
            }
        }
    }

    private fun openWifiSettings(ctx: Context) {
        val intents = listOf(Intent(Settings.ACTION_WIFI_SETTINGS), Intent(Settings.ACTION_WIRELESS_SETTINGS), Intent(Settings.ACTION_SETTINGS))
        for (i in intents) {
            try { ctx.startActivity(i); return } catch (_: Exception) {}
        }
        Toast.makeText(ctx, "Network settings unavailable", Toast.LENGTH_SHORT).show()
    }

    private fun getLocalIpAddress(): String? {
        return try {
            NetworkInterface.getNetworkInterfaces()?.toList()?.flatMap { it.inetAddresses.toList() }
                ?.firstOrNull { !it.isLoopbackAddress && it is Inet4Address }?.hostAddress
        } catch (_: Exception) { null }
    }

    private fun makeStatChip(ctx: Context, label: String, value: String) = LinearLayout(ctx).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(ctx, 8), dp(ctx, 5), dp(ctx, 8), dp(ctx, 5))
        background = GradientDrawable().apply { setColor(Color.argb(45, 0, 0, 0)); cornerRadius = LauncherUtils.dpF(ctx, 9f) }
        addView(TextView(ctx).apply { text = label; textSize = 7.5f; typeface = Typeface.DEFAULT_BOLD; setTextColor(Color.argb(175, 210, 230, 250)) })
        addView(TextView(ctx).apply { text = value; textSize = 9.5f; typeface = Typeface.DEFAULT_BOLD; setTextColor(Color.WHITE); isSingleLine = true })
    }

    private fun makeGlassBtn(ctx: Context, lp: LauncherPreferences, label: String, onClick: () -> Unit) = TextView(ctx).apply {
        text = label; textSize = 9.5f; typeface = Typeface.DEFAULT_BOLD; setTextColor(Color.WHITE)
        gravity = Gravity.CENTER; isFocusable = true; background = cardBg(ctx, false, false, lp.accentColor)
        setOnFocusChangeListener { v, f -> LauncherUtils.applyFocusSpring(v, f, 1.03f, 3f); v.background = cardBg(ctx, f, false, lp.accentColor) }
        setOnClickListener { onClick() }
    }

    private fun cardBg(ctx: Context, focused: Boolean, selected: Boolean, accent: Int) = GradientDrawable().apply {
        setColor(when { focused -> Color.argb(115, 255, 255, 255); selected -> LauncherUtils.withAlpha(accent, 110); else -> Color.argb(42, 255, 255, 255) })
        cornerRadius = LauncherUtils.dpF(ctx, 12f)
        setStroke(dp(ctx, if (focused || selected) 2 else 1), if (focused) Color.WHITE else if (selected) accent else Color.argb(60, 255, 255, 255))
    }

    private fun addHeader(ctx: Context, panel: LinearLayout, txt: String) = panel.addView(
        TextView(ctx).apply { text = txt; textSize = 8.5f; typeface = Typeface.DEFAULT_BOLD; letterSpacing = 0.08f; setTextColor(Color.argb(200, 195, 222, 250)) },
        LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(ctx, 4); bottomMargin = dp(ctx, 6) }
    )

    private fun dp(ctx: Context, v: Int) = LauncherUtils.dp(ctx, v)
}
