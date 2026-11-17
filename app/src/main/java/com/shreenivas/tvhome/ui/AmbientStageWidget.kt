package com.shreenivas.tvhome.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.location.Geocoder
import android.location.LocationManager
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import com.shreenivas.tvhome.LauncherPreferences
import com.shreenivas.tvhome.LauncherUtils
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.concurrent.thread
import kotlin.math.cos
import kotlin.math.sin

class AmbientStageWidget(
    ctx: Context,
    private val lp: LauncherPreferences
) : LinearLayout(ctx) {

    private val analogView = ColorfulAnalogClockView(ctx)
    private val digitalTimeTv = TextView(ctx)
    private val amPmTv = TextView(ctx)
    private val dateTv = TextView(ctx)
    private val weatherIconTv = TextView(ctx)
    private val tempTv = TextView(ctx)
    private val condTv = TextView(ctx)
    private val cityTv = TextView(ctx)
    private val weatherCard = LinearLayout(ctx)

    private val uiHandler = Handler(Looper.getMainLooper())
    private var lastWeatherFetchMs = 0L

    private val clockTicker = object : Runnable {
        override fun run() {
            updateTimeDisplay()
            analogView.invalidate()
            uiHandler.postDelayed(this, 1000L)
        }
    }

    init {
        orientation = VERTICAL
        gravity = Gravity.CENTER_HORIZONTAL
        setPadding(dp(14), dp(16), dp(14), dp(16))

        background = GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            intArrayOf(Color.argb(135, 22, 32, 72), Color.argb(175, 12, 18, 44))
        ).apply {
            cornerRadius = LauncherUtils.dpF(ctx, 24f)
            setStroke(dp(1), Color.argb(95, 255, 255, 255))
        }

        addView(analogView, LayoutParams(dp(148), dp(148)).apply { bottomMargin = dp(10) })

        val timeRow = LinearLayout(ctx).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_HORIZONTAL or Gravity.BOTTOM
        }
        digitalTimeTv.apply {
            textSize = 26f
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            setTextColor(Color.WHITE)
            setShadowLayer(6f, 0f, 2f, Color.argb(140, 0, 0, 0))
        }
        amPmTv.apply {
            textSize = 11f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(lp.accentColor)
            setPadding(dp(5), 0, 0, dp(4))
        }
        timeRow.addView(digitalTimeTv)
        timeRow.addView(amPmTv)
        addView(timeRow)

        dateTv.apply {
            textSize = 11.5f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.argb(215, 210, 232, 255))
            gravity = Gravity.CENTER
            setPadding(0, dp(2), 0, dp(14))
        }
        addView(dateTv)

        weatherCard.apply {
            orientation = VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(12), dp(12), dp(12), dp(12))
            background = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(Color.argb(75, 255, 255, 255), Color.argb(32, 255, 255, 255))
            ).apply {
                cornerRadius = LauncherUtils.dpF(ctx, 18f)
                setStroke(dp(1), Color.argb(80, 255, 255, 255))
            }
        }

        val wTop = LinearLayout(ctx).apply { orientation = HORIZONTAL; gravity = Gravity.CENTER }
        weatherIconTv.apply { text = "⛅"; textSize = 26f; setPadding(0, 0, dp(8), 0) }
        tempTv.apply {
            text = lp.prefs.getString("cached_temp", "28°C") ?: "28°C"
            textSize = 22f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
        }
        wTop.addView(weatherIconTv)
        wTop.addView(tempTv)
        weatherCard.addView(wTop)

        condTv.apply {
            text = lp.prefs.getString("cached_cond", "Pleasant Sky") ?: "Pleasant Sky"
            textSize = 10.5f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.rgb(145, 225, 255))
            gravity = Gravity.CENTER
            setPadding(0, dp(3), 0, dp(2))
        }
        weatherCard.addView(condTv)

        cityTv.apply {
            text = "📍 ${lp.prefs.getString("cached_city", lp.weatherCity.ifBlank { "Local Weather" })}"
            textSize = 9.5f
            setTextColor(Color.argb(195, 220, 235, 255))
            gravity = Gravity.CENTER
        }
        weatherCard.addView(cityTv)
        addView(weatherCard, LayoutParams(-1, -2))

        refreshStyle()
        updateTimeDisplay()
    }
        fun refreshStyle() {
        val accent = lp.accentColor
        amPmTv.setTextColor(accent)
        analogView.updateAccent(accent)

        val isNarrow = lp.panelWidthPct <= 22
        val dialSize = if (isNarrow) dp(112) else dp(148)
        analogView.layoutParams.width = dialSize
        analogView.layoutParams.height = dialSize
        digitalTimeTv.textSize = if (isNarrow) 21f else 26f
        analogView.requestLayout()
    }

    fun triggerWeatherRefresh(force: Boolean = false) {
        val now = System.currentTimeMillis()
        if (!force && now - lastWeatherFetchMs < 30 * 60 * 1000L) return
        lastWeatherFetchMs = now

        thread {
            try {
                var lat = 17.6868
                var lon = 83.2185
                var cityName = lp.weatherCity.trim()

                if (cityName.isNotEmpty()) {
                    val geoUrl = "https://open-meteo.com{URLEncoder.encode(cityName, "UTF-8")}&count=1"
                    val geoJson = JSONObject(fetchUrl(geoUrl))
                    val results = geoJson.optJSONArray("results")
                    if (results != null && results.length() > 0) {
                        val first = results.getJSONObject(0)
                        lat = first.getDouble("latitude")
                        lon = first.getDouble("longitude")
                        cityName = first.optString("name", cityName)
                    }
                } else if (context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                    val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
                    val loc = lm?.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
                        ?: lm?.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                    if (loc != null) {
                        lat = loc.latitude
                        lon = loc.longitude
                        @Suppress("DEPRECATION")
                        val list = Geocoder(context, Locale.getDefault()).getFromLocation(lat, lon, 1)
                        cityName = list?.firstOrNull()?.locality ?: "Your Location"
                    }
                }

                if (cityName.isEmpty()) cityName = "Visakhapatnam"

                val wUrl = "https://open-meteo.com"
                val wJson = JSONObject(fetchUrl(wUrl))
                val cw = wJson.getJSONObject("current_weather")
                val temp = "${cw.getDouble("temperature").toInt()}°C"
                val code = cw.getInt("weathercode")
                val (emoji, desc) = decodeWeatherCode(code)

                lp.prefs.edit()
                    .putString("cached_temp", temp)
                    .putString("cached_cond", desc)
                    .putString("cached_city", cityName)
                    .apply()

                uiHandler.post {
                    weatherIconTv.text = emoji
                    tempTv.text = temp
                    condTv.text = desc
                    cityTv.text = "📍 $cityName"
                }
            } catch (_: Exception) {
            }
        }
    }

    private fun fetchUrl(urlStr: String): String {
        val conn = URL(urlStr).openConnection() as HttpURLConnection
        conn.connectTimeout = 6000
        conn.readTimeout = 6000
        return conn.inputStream.bufferedReader().use { it.readText() }
    }

    private fun decodeWeatherCode(code: Int): Pair<String, String> = when (code) {
        0 -> "☀️" to "Clear Sky"
        1, 2 -> "🌤️" to "Partly Cloudy"
        3 -> "☁️" to "Overcast"
        45, 48 -> "🌫️" to "Misty Fog"
        in 51..67 -> "🌧️" to "Light Rain"
        in 80..82 -> "🌦️" to "Rain Showers"
        in 95..99 -> "⛈️" to "Thunderstorm"
        else -> "⛅" to "Pleasant Weather"
    }

    private fun updateTimeDisplay() {
        val now = Date()
        digitalTimeTv.text = SimpleDateFormat("hh:mm", Locale.getDefault()).format(now)
        amPmTv.text = SimpleDateFormat("a", Locale.getDefault()).format(now).uppercase(Locale.ROOT)
        dateTv.text = SimpleDateFormat("EEEE, d MMM", Locale.getDefault()).format(now)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        uiHandler.post(clockTicker)
        triggerWeatherRefresh(false)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        uiHandler.removeCallbacks(clockTicker)
    }

    private fun dp(v: Int) = LauncherUtils.dp(context, v)

    private class ColorfulAnalogClockView(ctx: Context) : View(ctx) {
        private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
        private val dialPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
        private val tickPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeCap = Paint.Cap.ROUND }
        private val handPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeCap = Paint.Cap.ROUND }
        private var accent = Color.rgb(92, 184, 255)

        fun updateAccent(col: Int) {
            accent = col
            invalidate()
        }

        override fun onDraw(canvas: Canvas) {
            val w = width.toFloat(); val h = height.toFloat()
            if (w <= 0f || h <= 0f) return
            val cx = w / 2f; val cy = h / 2f
            val r = minOf(cx, cy) - LauncherUtils.dpF(context, 4f)

            dialPaint.shader = LinearGradient(0f, 0f, w, h, Color.argb(190, 28, 42, 92), Color.argb(220, 12, 18, 42), Shader.TileMode.CLAMP)
            canvas.drawCircle(cx, cy, r, dialPaint)

            ringPaint.strokeWidth = LauncherUtils.dpF(context, 2.8f)
            ringPaint.shader = LinearGradient(0f, 0f, w, h, accent, Color.rgb(255, 95, 165), Shader.TileMode.CLAMP)
            canvas.drawCircle(cx, cy, r, ringPaint)

            for (i in 0 until 12) {
                val ang = Math.toRadians((i * 30 - 90).toDouble())
                val isMajor = i % 3 == 0
                tickPaint.strokeWidth = LauncherUtils.dpF(context, if (isMajor) 2.6f else 1.4f)
                tickPaint.color = if (isMajor) accent else Color.argb(150, 220, 235, 255)
                val inner = r * if (isMajor) 0.78f else 0.85f
                val outer = r * 0.92f
                canvas.drawLine(
                    (cx + cos(ang) * inner).toFloat(), (cy + sin(ang) * inner).toFloat(),
                    (cx + cos(ang) * outer).toFloat(), (cy + sin(ang) * outer).toFloat(), tickPaint
                )
            }

            val cal = Calendar.getInstance()
            val sec = cal.get(Calendar.SECOND)
            val min = cal.get(Calendar.MINUTE) + sec / 60f
            val hr = (cal.get(Calendar.HOUR) % 12) + min / 60f

            drawHand(canvas, cx, cy, (hr * 30f - 90f), r * 0.50f, 4.2f, Color.WHITE)
            drawHand(canvas, cx, cy, (min * 6f - 90f), r * 0.70f, 2.8f, Color.rgb(185, 230, 255))
            drawHand(canvas, cx, cy, (sec * 6f - 90f), r * 0.78f, 1.6f, Color.rgb(255, 95, 145))

            handPaint.style = Paint.Style.FILL
            handPaint.color = accent
            canvas.drawCircle(cx, cy, LauncherUtils.dpF(context, 4f), handPaint)
        }

        private fun drawHand(canvas: Canvas, cx: Float, cy: Float, deg: Float, len: Float, strokeDp: Float, col: Int) {
            val rad = Math.toRadians(deg.toDouble())
            handPaint.style = Paint.Style.STROKE
            handPaint.strokeWidth = LauncherUtils.dpF(context, strokeDp)
            handPaint.color = col
            canvas.drawLine(cx, cy, (cx + cos(rad) * len).toFloat(), (cy + sin(rad) * len).toFloat(), handPaint)
        }
    }
}
