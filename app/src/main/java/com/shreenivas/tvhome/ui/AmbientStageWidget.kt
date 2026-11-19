package com.shreenivas.tvhome.ui

import android.app.AlertDialog
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import com.shreenivas.tvhome.LauncherPreferences
import com.shreenivas.tvhome.LauncherUtils
import com.shreenivas.tvhome.MotionEngine
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

    private val analogView = ClassicLuxuryClockView(ctx)
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
        setPadding(dp(14), dp(14), dp(14), dp(14))

        background = GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            intArrayOf(Color.argb(145, 20, 30, 68), Color.argb(185, 10, 16, 40))
        ).apply {
            cornerRadius = LauncherUtils.dpF(ctx, 24f)
            setStroke(dp(1), Color.argb(105, 255, 255, 255))
        }

        addView(analogView, LayoutParams(dp(166), dp(166)).apply { bottomMargin = dp(8) })

        val timeRow = LinearLayout(ctx).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_HORIZONTAL or Gravity.BOTTOM
        }
        digitalTimeTv.apply {
            textSize = 28f
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            setTextColor(Color.WHITE)
            setShadowLayer(6f, 0f, 2f, Color.argb(150, 0, 0, 0))
        }
        amPmTv.apply {
            textSize = 12f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(lp.accentColor)
            setPadding(dp(5), 0, 0, dp(4))
        }
        timeRow.addView(digitalTimeTv)
        timeRow.addView(amPmTv)
        addView(timeRow)

        dateTv.apply {
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
            letterSpacing = 0.03f
            setTextColor(Color.rgb(225, 240, 255))
            gravity = Gravity.CENTER
            setPadding(0, dp(3), 0, dp(12))
        }
        addView(dateTv)

        weatherCard.apply {
            orientation = VERTICAL
            gravity = Gravity.CENTER
            isFocusable = true
            isClickable = true
            setPadding(dp(12), dp(11), dp(12), dp(11))
            background = weatherCardBg(false)
            setOnFocusChangeListener { v, f ->
                MotionEngine.animateFocus(v, f, lp.motionStyle, 1.04f, 6f)
                v.background = weatherCardBg(f)
            }
            setOnClickListener { showLiveCitySearchDialog() }
        }

        val wTop = LinearLayout(ctx).apply { orientation = HORIZONTAL; gravity = Gravity.CENTER }
        weatherIconTv.apply { text = "⛅"; textSize = 28f; setPadding(0, 0, dp(8), 0) }
        tempTv.apply {
            text = lp.prefs.getString("cached_temp", "28°C") ?: "28°C"
            textSize = 23f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
        }
        wTop.addView(weatherIconTv)
        wTop.addView(tempTv)
        weatherCard.addView(wTop)

        condTv.apply {
            text = lp.prefs.getString("cached_cond", "Pleasant Sky") ?: "Pleasant Sky"
            textSize = 11f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.rgb(145, 225, 255))
            gravity = Gravity.CENTER
            setPadding(0, dp(3), 0, dp(3))
        }
        weatherCard.addView(condTv)

        val savedCity = lp.prefs.getString("cached_city", lp.weatherCity.ifBlank { "Tap to Select City" })
        cityTv.apply {
            text = "📍 $savedCity  ▾"
            textSize = 10.5f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
        }
        weatherCard.addView(cityTv)
        addView(weatherCard, LayoutParams(-1, -2))

        refreshStyle()
        updateTimeDisplay()
    }

    private fun weatherCardBg(focused: Boolean) = GradientDrawable(
        GradientDrawable.Orientation.TOP_BOTTOM,
        if (focused) intArrayOf(Color.argb(125, 255, 255, 255), Color.argb(75, 255, 255, 255))
        else intArrayOf(Color.argb(75, 255, 255, 255), Color.argb(32, 255, 255, 255))
    ).apply {
        cornerRadius = LauncherUtils.dpF(context, 18f)
        setStroke(dp(if (focused) 2 else 1), if (focused) lp.accentColor else Color.argb(80, 255, 255, 255))
    }
    private data class CitySuggestion(
        val name: String,
        val admin: String,
        val country: String,
        val lat: Double,
        val lon: Double
    )

    private fun showLiveCitySearchDialog() {
        val dialog = android.app.Dialog(context)
        dialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE)

        val box = LinearLayout(context).apply {
            orientation = VERTICAL
            setPadding(dp(22), dp(16), dp(22), dp(16))
            background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(Color.rgb(20, 32, 62), Color.rgb(12, 18, 38))
            ).apply {
                cornerRadius = LauncherUtils.dpF(context, 20f)
                setStroke(dp(1), Color.argb(120, 255, 255, 255))
            }
        }

        val headerTv = TextView(context).apply {
            text = "Search Your City (Live Weather)"
            textSize = 15f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            setPadding(0, 0, 0, dp(10))
        }
        box.addView(headerTv)

        val input = EditText(context).apply {
            isSingleLine = true
            isFocusable = true
            isFocusableInTouchMode = true
            hint = "Type city (e.g. Vizianagaram, Hyderabad...)"
            setText(lp.weatherCity)
            setTextColor(Color.WHITE)
            setHintTextColor(Color.argb(150, 200, 220, 245))
            setPadding(dp(12), dp(10), dp(12), dp(10))
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_WORDS
            background = GradientDrawable().apply {
                setColor(Color.argb(55, 255, 255, 255))
                cornerRadius = LauncherUtils.dpF(context, 12f)
                setStroke(dp(1), lp.accentColor)
            }
        }
        box.addView(input, LayoutParams(dp(340), -2))

        val statusTv = TextView(context).apply {
            text = "Type at least 2 letters for live suggestions..."
            textSize = 10.5f
            setTextColor(Color.rgb(145, 215, 255))
            setPadding(dp(4), dp(8), dp(4), dp(6))
        }
        box.addView(statusTv)

        val suggestionsContainer = LinearLayout(context).apply {
            orientation = VERTICAL
        }
        box.addView(suggestionsContainer)

        dialog.setContentView(box)
        dialog.window?.apply {
            setBackgroundDrawableResource(android.R.color.transparent)
            clearFlags(android.view.WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or android.view.WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM)
            setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE)
        }

        var searchRunnable: Runnable? = null

        val triggerSearch: (String) -> Unit = { q ->
            searchRunnable?.let { uiHandler.removeCallbacks(it) }
            if (q.length < 2) {
                suggestionsContainer.removeAllViews()
                statusTv.text = "Type at least 2 characters..."
            } else {
                statusTv.text = "Searching '$q'..."
                searchRunnable = Runnable {
                    thread {
                        val (list, errMsg) = fetchCitySuggestionsWithStatus(q)
                        uiHandler.post {
                            suggestionsContainer.removeAllViews()
                            if (errMsg != null) {
                                statusTv.text = "Network error: $errMsg"
                            } else if (list.isEmpty()) {
                                statusTv.text = "No cities found for '$q'"
                            } else {
                                statusTv.text = "Select a city below:"
                                for (item in list) {
                                    val sub = listOf(item.admin, item.country).filter { it.isNotBlank() }.joinToString(", ")
                                    val rowBtn = LinearLayout(context).apply {
                                        orientation = VERTICAL
                                        isFocusable = true
                                        isClickable = true
                                        setPadding(dp(12), dp(8), dp(12), dp(8))
                                        background = GradientDrawable().apply {
                                            setColor(Color.argb(42, 255, 255, 255))
                                            cornerRadius = LauncherUtils.dpF(context, 10f)
                                            setStroke(dp(1), Color.argb(60, 255, 255, 255))
                                        }
                                        setOnFocusChangeListener { v, f ->
                                            v.background = GradientDrawable().apply {
                                                setColor(if (f) LauncherUtils.withAlpha(lp.accentColor, 140) else Color.argb(42, 255, 255, 255))
                                                cornerRadius = LauncherUtils.dpF(context, 10f)
                                                setStroke(dp(if (f) 2 else 1), if (f) Color.WHITE else Color.argb(60, 255, 255, 255))
                                            }
                                        }
                                        setOnClickListener {
                                            lp.weatherCity = item.name
                                            lp.prefs.edit()
                                                .putString("cached_city", item.name)
                                                .putFloat("saved_lat", item.lat.toFloat())
                                                .putFloat("saved_lon", item.lon.toFloat())
                                                .apply()
                                            cityTv.text = "📍 ${item.name}  ▾"
                                            dialog.dismiss()
                                            triggerWeatherRefresh(true)
                                        }
                                    }
                                    rowBtn.addView(TextView(context).apply {
                                        text = "📍 ${item.name}"
                                        textSize = 12f
                                        typeface = Typeface.DEFAULT_BOLD
                                        setTextColor(Color.WHITE)
                                    })
                                    if (sub.isNotEmpty()) {
                                        rowBtn.addView(TextView(context).apply {
                                            text = sub
                                            textSize = 9.5f
                                            setTextColor(Color.argb(200, 210, 230, 255))
                                        })
                                    }
                                    suggestionsContainer.addView(rowBtn, LayoutParams(-1, -2).apply { bottomMargin = dp(6) })
                                }
                            }
                        }
                    }
                }
                uiHandler.postDelayed(searchRunnable!!, 300L)
            }
        }

        input.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                triggerSearch(s?.toString()?.trim().orEmpty())
            }
        })

        dialog.show()
        input.requestFocus()
        if (input.text.toString().trim().length >= 2) {
            triggerSearch(input.text.toString().trim())
        }
    }

    private fun fetchCitySuggestions(query: String): List<CitySuggestion> =
        fetchCitySuggestionsWithStatus(query).first

    private fun fetchCitySuggestionsWithStatus(query: String): Pair<List<CitySuggestion>, String?> {
        return try {
            val encoded = URLEncoder.encode(query, "UTF-8")
            val url = "https://geocoding-api." + "://open-meteo.com" + encoded + "&count=5&language=en&format=json"
            val rawResponse = fetchUrl(url)
            val json = JSONObject(rawResponse)
            val arr = json.optJSONArray("results") ?: return emptyList<CitySuggestion>() to null
            val out = ArrayList<CitySuggestion>()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                out.add(
                    CitySuggestion(
                        name = obj.optString("name"),
                        admin = obj.optString("admin1"),
                        country = obj.optString("country"),
                        lat = obj.getDouble("latitude"),
                        lon = obj.getDouble("longitude")
                    )
                )
            }
            out to null
        } catch (e: Exception) {
            emptyList<CitySuggestion>() to (e.message ?: e.javaClass.simpleName)
        }
    }

    fun refreshStyle() {
        val accent = lp.accentColor
        amPmTv.setTextColor(accent)
        analogView.updateAccent(accent)
        weatherCard.background = weatherCardBg(weatherCard.hasFocus())

        val isNarrow = lp.panelWidthPct <= 22
        val dialSize = if (isNarrow) dp(126) else dp(166)
        analogView.layoutParams.width = dialSize
        analogView.layoutParams.height = dialSize
        digitalTimeTv.textSize = if (isNarrow) 22f else 28f
        dateTv.textSize = if (isNarrow) 12f else 14f
        analogView.requestLayout()
    }

    fun triggerWeatherRefresh(force: Boolean = false) {
        val now = System.currentTimeMillis()
        if (!force && now - lastWeatherFetchMs < 20 * 60 * 1000L) return
        lastWeatherFetchMs = now

        thread {
            try {
                var lat = lp.prefs.getFloat("saved_lat", 0f).toDouble()
                var lon = lp.prefs.getFloat("saved_lon", 0f).toDouble()
                var cityName = lp.weatherCity.trim()

                if (cityName.isNotEmpty() && (lat == 0.0 || lon == 0.0)) {
                    val found = fetchCitySuggestions(cityName).firstOrNull()
                    if (found != null) {
                        lat = found.lat
                        lon = found.lon
                        cityName = found.name
                        lp.prefs.edit()
                            .putFloat("saved_lat", lat.toFloat())
                            .putFloat("saved_lon", lon.toFloat())
                            .apply()
                    }
                }

                if (lat == 0.0 && lon == 0.0) {
                    try {
                        val ipJson = JSONObject(fetchUrl("http://ip-api.com"))
                        if (ipJson.optString("status") == "success") {
                            lat = ipJson.getDouble("lat")
                            lon = ipJson.getDouble("lon")
                            if (cityName.isEmpty()) cityName = ipJson.optString("city", "Visakhapatnam")
                        }
                    } catch (_: Exception) {}
                }

                if (lat == 0.0 && lon == 0.0) {
                    lat = 17.6868; lon = 83.2185
                    if (cityName.isEmpty()) cityName = "Visakhapatnam"
                }

                val wUrl = "https://api." + "://open-meteo.com" + lat + "&longitude=" + lon + "&current_weather=true"
                val wJson = JSONObject(fetchUrl(wUrl))
                val cw = wJson.getJSONObject("current_weather")
                val temp = "${cw.getDouble("temperature").toInt()}°C"
                val code = cw.getInt("weathercode")
                val (emoji, desc) = decodeWeatherCode(code)

                lp.prefs.edit()
                    .putString("cached_temp", temp)
                    .putString("cached_cond", desc)
                    .putString("cached_city", cityName)
                    .putInt("cached_weather_code", code)
                    .apply()

                uiHandler.post {
                    weatherIconTv.text = emoji
                    tempTv.text = temp
                    condTv.text = desc
                    cityTv.text = "📍 $cityName  ▾"
                }
            } catch (_: Exception) {
            }
        }
    }

    private fun fetchUrl(urlStr: String): String {
        val conn = URL(urlStr).openConnection() as HttpURLConnection
        conn.requestMethod = "GET"
        conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Android TV; ShreeTVLauncher)")
        conn.setRequestProperty("Accept", "application/json")
        conn.connectTimeout = 8000
        conn.readTimeout = 8000
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
        dateTv.text = SimpleDateFormat("EEEE • d MMM", Locale.getDefault()).format(now)
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

    private class ClassicLuxuryClockView(ctx: Context) : View(ctx) {
        private val bezelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
        private val innerRingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
        private val dialPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
        private val tickPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeCap = Paint.Cap.ROUND }
        private val numPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create("serif", Typeface.BOLD)
        }
        private val handPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeCap = Paint.Cap.ROUND }
        private var accent = Color.rgb(92, 184, 255)

        fun updateAccent(col: Int) { accent = col; invalidate() }

        override fun onDraw(canvas: Canvas) {
            val w = width.toFloat(); val h = height.toFloat()
            if (w <= 0f || h <= 0f) return
            val cx = w / 2f; val cy = h / 2f
            val r = minOf(cx, cy) - LauncherUtils.dpF(context, 4f)

            dialPaint.shader = RadialGradient(
                cx, cy * 0.85f, r,
                intArrayOf(Color.rgb(34, 48, 96), Color.rgb(16, 24, 54), Color.rgb(8, 12, 28)),
                floatArrayOf(0f, 0.72f, 1f),
                Shader.TileMode.CLAMP
            )
            canvas.drawCircle(cx, cy, r, dialPaint)

            bezelPaint.strokeWidth = LauncherUtils.dpF(context, 3.2f)
            bezelPaint.shader = LinearGradient(0f, 0f, w, h, accent, Color.rgb(225, 238, 255), Shader.TileMode.CLAMP)
            canvas.drawCircle(cx, cy, r, bezelPaint)

            innerRingPaint.strokeWidth = LauncherUtils.dpF(context, 1f)
            innerRingPaint.color = Color.argb(75, 255, 255, 255)
            canvas.drawCircle(cx, cy, r * 0.88f, innerRingPaint)

            for (i in 0 until 60) {
                val ang = Math.toRadians((i * 6 - 90).toDouble())
                val isHour = i % 5 == 0
                tickPaint.strokeWidth = LauncherUtils.dpF(context, if (isHour) 2.2f else 1f)
                tickPaint.color = if (isHour) accent else Color.argb(90, 210, 230, 255)
                val inner = r * if (isHour) 0.83f else 0.86f
                val outer = r * 0.89f
                canvas.drawLine(
                    (cx + cos(ang) * inner).toFloat(), (cy + sin(ang) * inner).toFloat(),
                    (cx + cos(ang) * outer).toFloat(), (cy + sin(ang) * outer).toFloat(), tickPaint
                )
            }

            for (hrNum in 1..12) {
                val ang = Math.toRadians((hrNum * 30 - 90).toDouble())
                val isCardinal = hrNum % 3 == 0
                numPaint.textSize = r * if (isCardinal) 0.19f else 0.145f
                numPaint.color = if (isCardinal) Color.WHITE else Color.argb(215, 205, 225, 250)
                val numDist = r * 0.67f
                val nx = (cx + cos(ang) * numDist).toFloat()
                val ny = (cy + sin(ang) * numDist).toFloat() - ((numPaint.descent() + numPaint.ascent()) / 2f)
                canvas.drawText(hrNum.toString(), nx, ny, numPaint)
            }

            val cal = Calendar.getInstance()
            val sec = cal.get(Calendar.SECOND)
            val min = cal.get(Calendar.MINUTE) + sec / 60f
            val hr = (cal.get(Calendar.HOUR) % 12) + min / 60f

            drawHand(canvas, cx, cy, (hr * 30f - 90f), r * 0.45f, 4.4f, Color.WHITE)
            drawHand(canvas, cx, cy, (min * 6f - 90f), r * 0.66f, 2.8f, Color.rgb(185, 230, 255))
            drawHand(canvas, cx, cy, (sec * 6f - 90f), r * 0.76f, 1.5f, Color.rgb(255, 95, 135))

            handPaint.style = Paint.Style.FILL
            handPaint.color = accent
            canvas.drawCircle(cx, cy, LauncherUtils.dpF(context, 5f), handPaint)
            handPaint.color = Color.WHITE
            canvas.drawCircle(cx, cy, LauncherUtils.dpF(context, 2f), handPaint)
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
