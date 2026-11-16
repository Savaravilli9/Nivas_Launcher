package com.shreenivas.tvhome

import android.animation.ValueAnimator
import android.content.Context
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

object AppTileFactory {

    interface TileActions {
        fun onToggleFavorite(pkg: String, label: String)
        fun onMoveFavorite(pkg: String, delta: Int)
        fun onHideApp(pkg: String, label: String)
    }

    fun create(
        ctx: Context, pm: PackageManager, lp: LauncherPreferences,
        info: ResolveInfo, isFavRow: Boolean, isFav: Boolean,
        w: Int, h: Int, actions: TileActions
    ): View {
        val pkg = info.activityInfo.packageName
        val label = info.loadLabel(pm).toString()
        val icon = info.loadIcon(pm)

        // 1. ఔటర్ కంటైనర్ (ఇది కట్ అవ్వకుండా సేఫ్ బౌండరీ ఇస్తుంది)
        val wrapper = FrameLayout(ctx).apply {
            layoutParams = LinearLayout.LayoutParams(w, h)
            clipChildren = false
            clipToPadding = false
            isFocusable = true
            isClickable = true
            tag = if (isFavRow) "fav_$pkg" else "all_$pkg"
        }

        // 2. ఇన్నర్ కార్డ్ (ఇది స్కేల్ అవుతుంది మరియు గ్లో అవుతుంది)
        val safeMargin = LauncherUtils.dp(ctx, 10) // 10dp సేఫ్ జోన్ 
        val innerCard = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            isFocusable = false
            isClickable = false
            clipChildren = false
        }
        
        val cardParams = FrameLayout.LayoutParams(-1, -1).apply {
            setMargins(safeMargin, safeMargin, safeMargin, safeMargin)
        }

        // డైనమిక్ బ్యాక్‌గ్రౌండ్ & షేప్
        val bg = GradientDrawable().apply {
            shape = if (lp.tileShape == "circle") GradientDrawable.OVAL else GradientDrawable.RECTANGLE
            if (lp.tileShape != "circle") {
                cornerRadius = when(lp.tileShape) {
                    "squircle" -> LauncherUtils.dpF(ctx, 22f)
                    "heart" -> LauncherUtils.dpF(ctx, 18f)
                    else -> LauncherUtils.dpF(ctx, 12f)
                }
            }
            // గ్లాసీ ఎఫెక్ట్
            orientation = GradientDrawable.Orientation.TL_BR
            colors = intArrayOf(Color.argb(140, 32, 42, 75), Color.argb(80, 20, 28, 55))
            setStroke(LauncherUtils.dp(ctx, 1), Color.argb(40, 255, 255, 255))
        }
        innerCard.background = bg

        // యాప్ ఐకాన్ సైజింగ్
        val imgSize = when (lp.tileSize) {
            "large" -> dp(ctx, 52)
            "small" -> dp(ctx, 34)
            else -> dp(ctx, 42)
        }
        val iv = ImageView(ctx).apply {
            setImageDrawable(icon)
            layoutParams = LinearLayout.LayoutParams(imgSize, imgSize).apply {
                bottomMargin = dp(ctx, 4)
            }
        }
        innerCard.addView(iv)

        // యాప్ టైటిల్
        val tv = TextView(ctx).apply {
            text = label
            textSize = if (lp.columns > 6) 9f else 10.5f
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
            isSingleLine = true
            ellipsize = TextUtils.TruncateAt.END
            gravity = Gravity.CENTER
            setPadding(dp(ctx, 6), 0, dp(ctx, 6), 0)
        }
        innerCard.addView(tv)
        wrapper.addView(innerCard, cardParams)

        // ఐకాన్ బ్యాడ్జ్
        if (lp.badgeMode != "none") {
            val badge = TextView(ctx).apply {
                text = when(lp.badgeMode) { "heart" -> "♥"; "star" -> "★"; else -> "●" }
                textSize = 9.5f
                setTextColor(if(lp.badgeMode == "blue") Color.CYAN else lp.accentColor)
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(Color.argb(220, 15, 20, 35))
                }
                gravity = Gravity.CENTER
            }
            wrapper.addView(badge, FrameLayout.LayoutParams(dp(ctx,18), dp(ctx,18), Gravity.TOP or Gravity.END).apply {
                topMargin = dp(ctx, 4); rightMargin = dp(ctx, 4)
            })
        }

        // ==========================================
        // YOUTUBE-STYLE BREATHING GLOW ANIMATION
        // ==========================================
        var breathingAnim: ValueAnimator? = null

        wrapper.setOnFocusChangeListener { _, hasFocus ->
            val targetScale = if (hasFocus) 1.15f else 1.0f
            
            // బటర్-స్మూత్ స్కేల్ ఎఫెక్ట్
            innerCard.animate()
                .scaleX(targetScale).scaleY(targetScale)
                .setDuration(220L)
                .setInterpolator(LauncherUtils.FLUID_SPRING)
                .start()

            if (hasFocus) {
                innerCard.elevation = LauncherUtils.dpF(ctx, 14f)
                tv.isSelected = true // లాంగ్ టెక్స్ట్ ఉంటే స్క్రోల్ అవ్వడానికి (Marquee)
                
                // నియాన్ బ్రీతింగ్ (In & Out Glow)
                breathingAnim = ValueAnimator.ofInt(40, 255).apply {
                    duration = 850L // శ్వాస తీసుకున్నంత స్లోగా
                    repeatMode = ValueAnimator.REVERSE
                    repeatCount = ValueAnimator.INFINITE
                    interpolator = AccelerateDecelerateInterpolator()
                    addUpdateListener {
                        val alpha = it.animatedValue as Int
                        // యాసెంట్ కలర్ తో స్ట్రోక్ బ్లింక్ అవుతుంది
                        bg.setStroke(LauncherUtils.dp(ctx, 2), LauncherUtils.withAlpha(lp.accentColor, alpha))
                        innerCard.background = bg 
                    }
                    start()
                }
            } else {
                tv.isSelected = false
                innerCard.elevation = 0f
                breathingAnim?.cancel() // ఫోకస్ పోగానే యానిమేషన్ ఆగిపోతుంది
                bg.setStroke(LauncherUtils.dp(ctx, 1), Color.argb(40, 255, 255, 255))
                innerCard.background = bg
            }
        }

        // క్లిక్ మరియు లాంగ్ క్లిక్ యాక్షన్స్
        wrapper.setOnClickListener {
            try {
                ctx.startActivity(pm.getLaunchIntentForPackage(pkg))
            } catch (e: Exception) {
                Toast.makeText(ctx, "App not found", Toast.LENGTH_SHORT).show()
            }
        }

        wrapper.setOnKeyListener { _, keyCode, event ->
            if (event.action == KeyEvent.ACTION_DOWN && (keyCode == KeyEvent.KEYCODE_MENU || (keyCode == KeyEvent.KEYCODE_DPAD_CENTER && event.isLongPress))) {
                actions.onToggleFavorite(pkg, label)
                true
            } else false
        }

        wrapper.setOnLongClickListener {
            actions.onToggleFavorite(pkg, label)
            true
        }

        return wrapper
    }

    private fun dp(ctx: Context, v: Int) = LauncherUtils.dp(ctx, v)
}
