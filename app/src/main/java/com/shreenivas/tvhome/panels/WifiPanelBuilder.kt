package com.shreenivas.tvhome.panels

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.wifi.WifiManager
import android.os.Build
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.shreenivas.tvhome.LauncherPreferences
import com.shreenivas.tvhome.LauncherUtils
import com.shreenivas.tvhome.MotionEngine
import com.shreenivas.tvhome.ui.TileThemePalette
import java.util.Locale

object WifiPanelBuilder {

    @Suppress("DEPRECATION")
    fun build(ctx: Context, panel: LinearLayout, lp: LauncherPreferences, onRefreshPanel: () -> Unit) {
        val wm = ctx.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
        val isWifiOn = try { wm?.isWifiEnabled == true } catch (_: Exception) { true }

        val statusCard = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(ctx, 12), dp(ctx, 10), dp(ctx, 12), dp(ctx, 10)); isFocusable = true
            background = cardBg(ctx, lp.accentColor, isWifiOn)
            setOnFocusChangeListener { v, f ->
                MotionEngine.animateFocus(v, f, lp.motionStyle, 1.02f, 4f)
                v.background = cardBg(ctx, lp.accentColor, f || (try { wm?.isWifiEnabled == true } catch (_: Exception) { true }))
            }
        }

        val infoCol = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
        val connectedSsid = TileThemePalette.getConnectedSsid(ctx)
        val titleTv = TextView(ctx).apply {
            text = if (isWifiOn) connectedSsid else "Wi-Fi Turned Off"
            textSize = 12f; typeface = Typeface.DEFAULT_BOLD; setTextColor(Color.WHITE)
        }
        val subTv = TextView(ctx).apply {
            text = if (isWifiOn) "● Online • Tap to toggle Wi-Fi" else "○ Tap to turn on Wi-Fi"
            textSize = 9.5f; setTextColor(if (isWifiOn) Color.rgb(120, 245, 175) else Color.LTGRAY)
        }
        infoCol.addView(titleTv)
        infoCol.addView(subTv)
        statusCard.addView(infoCol, LinearLayout.LayoutParams(0, -2, 1f))

        val togglePill = TextView(ctx).apply {
            text = if (isWifiOn) "ON" else "OFF"
            textSize = 10f; typeface = Typeface.DEFAULT_BOLD; setTextColor(Color.WHITE)
            setPadding(dp(ctx, 12), dp(ctx, 5), dp(ctx, 12), dp(ctx, 5))
            background = GradientDrawable().apply {
                setColor(if (isWifiOn) Color.rgb(34, 185, 110) else Color.argb(85, 255, 255, 255))
                cornerRadius = LauncherUtils.dpF(ctx, 14f)
            }
        }
        statusCard.addView(togglePill)
        panel.addView(statusCard, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(ctx, 8) })

        // Dynamic container for Speed/IP card + Nearby Networks (updated in-place without rebuilding panel!)
        val dynamicListContainer = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            clipChildren = false
            clipToPadding = false
        }

        val renderDynamicWifiContent = {
            dynamicListContainer.removeAllViews()
            val curOn = try { wm?.isWifiEnabled == true } catch (_: Exception) { true }
            val curSsid = TileThemePalette.getConnectedSsid(ctx)

            titleTv.text = if (curOn) curSsid else "Wi-Fi Turned Off"
            subTv.text = if (curOn) "● Online • Tap to toggle Wi-Fi" else "○ Tap to turn on Wi-Fi"
            subTv.setTextColor(if (curOn) Color.rgb(120, 245, 175) else Color.LTGRAY)
            togglePill.text = if (curOn) "ON" else "OFF"
            togglePill.background = GradientDrawable().apply {
                setColor(if (curOn) Color.rgb(34, 185, 110) else Color.argb(85, 255, 255, 255))
                cornerRadius = LauncherUtils.dpF(ctx, 14f)
            }

            if (curOn && wm != null) {
                val info = try { wm.connectionInfo } catch (_: Exception) { null }
                val ipInt = info?.ipAddress ?: 0
                val ipStr = if (ipInt != 0) String.format(
                    Locale.US, "%d.%d.%d.%d",
                    ipInt and 0xff, ipInt shr 8 and 0xff, ipInt shr 16 and 0xff, ipInt shr 24 and 0xff
                ) else "Connected"
                val speed = info?.linkSpeed ?: 0

                val metricCard = LinearLayout(ctx).apply {
                    orientation = LinearLayout.HORIZONTAL; setPadding(dp(ctx, 12), dp(ctx, 8), dp(ctx, 12), dp(ctx, 8))
                    background = cardBg(ctx, lp.accentColor, false)
                }
                val col1 = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
                col1.addView(TextView(ctx).apply { text = "LINK SPEED"; textSize = 8f; setTextColor(Color.LTGRAY) })
                col1.addView(TextView(ctx).apply { text = if (speed > 0) "$speed Mbps" else "Active"; textSize = 11f; typeface = Typeface.DEFAULT_BOLD; setTextColor(Color.WHITE) })
                metricCard.addView(col1, LinearLayout.LayoutParams(0, -2, 1f))

                val col2 = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
                col2.addView(TextView(ctx).apply { text = "IP ADDRESS"; textSize = 8f; setTextColor(Color.LTGRAY) })
                col2.addView(TextView(ctx).apply { text = ipStr; textSize = 11f; typeface = Typeface.DEFAULT_BOLD; setTextColor(Color.WHITE) })
                metricCard.addView(col2, LinearLayout.LayoutParams(0, -2, 1f))
                dynamicListContainer.addView(metricCard, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(ctx, 10) })
            }

            dynamicListContainer.addView(TextView(ctx).apply {
                text = "NEARBY AVAILABLE NETWORKS"; textSize = 9f; typeface = Typeface.DEFAULT_BOLD
                setTextColor(lp.accentColor); letterSpacing = 0.08f
            }, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(ctx, 6) })

            val hasLocPerm = Build.VERSION.SDK_INT < Build.VERSION_CODES.M ||
                ctx.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

            if (!hasLocPerm) {
                val permBtn = makeGlassBtn(ctx, lp, "📍  Enable Location to Scan Networks") {
                    try { ctx.startActivity(Intent(Settings.ACTION_WIFI_SETTINGS)) } catch (_: Exception) {}
                }
                dynamicListContainer.addView(permBtn, LinearLayout.LayoutParams(-1, dp(ctx, 40)))
            } else {
                @SuppressLint("MissingPermission")
                val results = try { wm?.scanResults ?: emptyList() } catch (_: Exception) { emptyList() }
                val unique = results.filter { !it.SSID.isNullOrBlank() }.distinctBy { it.SSID }.sortedByDescending { it.level }

                if (unique.isEmpty()) {
                    dynamicListContainer.addView(TextView(ctx).apply {
                        text = "No nearby networks found. Tap Refresh Scan or TV Wi-Fi Settings."
                        textSize = 9.5f; setTextColor(Color.argb(190, 210, 230, 250))
                        setPadding(dp(ctx, 4), dp(ctx, 8), dp(ctx, 4), dp(ctx, 8))
                    })
                } else {
                    for (scan in unique.take(8)) {
                        val isCurrent = scan.SSID == curSsid
                        val netRow = LinearLayout(ctx).apply {
                            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
                            setPadding(dp(ctx, 12), dp(ctx, 9), dp(ctx, 12), dp(ctx, 9)); isFocusable = true
                            background = cardBg(ctx, lp.accentColor, isCurrent)
                            setOnFocusChangeListener { v, f ->
                                MotionEngine.animateFocus(v, f, lp.motionStyle, 1.02f, 4f)
                                v.background = cardBg(ctx, lp.accentColor, f || isCurrent)
                            }
                            setOnClickListener {
                                try { ctx.startActivity(Intent(Settings.ACTION_WIFI_SETTINGS)) } catch (_: Exception) {}
                            }
                        }
                        netRow.addView(TextView(ctx).apply {
                            text = if (isCurrent) "✓  ${scan.SSID}" else "📶  ${scan.SSID}"
                            textSize = 10.5f; typeface = Typeface.DEFAULT_BOLD
                            setTextColor(if (isCurrent) Color.rgb(120, 245, 175) else Color.WHITE)
                        }, LinearLayout.LayoutParams(0, -2, 1f))
                        netRow.addView(TextView(ctx).apply {
                            text = "›"; textSize = 14f; typeface = Typeface.DEFAULT_BOLD; setTextColor(Color.LTGRAY)
                        })
                        dynamicListContainer.addView(netRow, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(ctx, 6) })
                    }
                }
            }
        }

        // In-place Wi-Fi On/Off toggle (focus stays locked on statusCard!)
        statusCard.setOnClickListener {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                try {
                    ctx.startActivity(Intent(Settings.Panel.ACTION_INTERNET_CONNECTIVITY))
                } catch (_: Exception) {
                    try { ctx.startActivity(Intent(Settings.ACTION_WIFI_SETTINGS)) } catch (_: Exception) {}
                }
            } else {
                try {
                    wm?.isWifiEnabled = !isWifiOn
                    renderDynamicWifiContent()
                } catch (_: Exception) {
                                        try { ctx.startActivity(Intent(Settings.ACTION_WIFI_SETTINGS)) } catch (_: Exception) {}
                }
            }
        }

        // Action Row: Refresh Scan & TV Wi-Fi Settings (In-Place Refresh — Focus NEVER jumps!)
        val actionRow = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL; clipChildren = false; clipToPadding = false
        }
        actionRow.addView(
            makeGlassBtn(ctx, lp, "🔄  Refresh Scan") {
                try { wm?.startScan() } catch (_: Exception) {}
                renderDynamicWifiContent()
                Toast.makeText(ctx, "Wi-Fi list refreshed", Toast.LENGTH_SHORT).show()
            },
            LinearLayout.LayoutParams(0, dp(ctx, 38), 1f).apply { rightMargin = dp(ctx, 6) }
        )
        actionRow.addView(
            makeGlassBtn(ctx, lp, "⚙  TV Wi-Fi Settings") {
                try { ctx.startActivity(Intent(Settings.ACTION_WIFI_SETTINGS)) } catch (_: Exception) {}
            },
            LinearLayout.LayoutParams(0, dp(ctx, 38), 1f)
        )
        panel.addView(actionRow, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(ctx, 10) })

        panel.addView(dynamicListContainer, LinearLayout.LayoutParams(-1, -2))
        renderDynamicWifiContent()
    }

    private fun makeGlassBtn(ctx: Context, lp: LauncherPreferences, label: String, onClick: () -> Unit) = TextView(ctx).apply {
        text = label; textSize = 9.5f; typeface = Typeface.DEFAULT_BOLD; setTextColor(Color.WHITE)
        gravity = Gravity.CENTER; isFocusable = true; isClickable = true
        background = cardBg(ctx, lp.accentColor, false)
        setOnFocusChangeListener { v, f ->
            MotionEngine.animateFocus(v, f, lp.motionStyle, 1.03f, 4f)
            v.background = cardBg(ctx, lp.accentColor, f)
        }
        setOnClickListener { onClick() }
    }

    private fun cardBg(ctx: Context, accent: Int, active: Boolean) = GradientDrawable(
        GradientDrawable.Orientation.TOP_BOTTOM,
        if (active) intArrayOf(LauncherUtils.withAlpha(accent, 115), Color.argb(65, 255, 255, 255))
        else intArrayOf(Color.argb(52, 255, 255, 255), Color.argb(26, 255, 255, 255))
    ).apply {
        cornerRadius = LauncherUtils.dpF(ctx, 14f)
        setStroke(dp(ctx, if (active) 2 else 1), if (active) accent else Color.argb(65, 255, 255, 255))
    }

    private fun dp(ctx: Context, v: Int) = LauncherUtils.dp(ctx, v)
}
