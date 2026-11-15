package com.shreenivas.tvhome

import android.graphics.Color

object TileThemePalette {
    val NAMES = listOf(
        "Royal Indigo", "Ocean Breeze", "Cyber Neon",
        "Sunset Glow", "Emerald Forest", "Slate Dark"
    )
    val SUBTITLES = listOf(
        "Glossy & Deep", "Cyan & Deep Sea", "Electric & Cyber",
        "Crimson & Amber", "Deep Jungle & Mint", "Graphite & Matte"
    )
    val KEYS = listOf(
        "frost", "sapphire", "amethyst", "rose", "emerald", "gold"
    )

    fun getTopBottomColors(key: String, focused: Boolean): IntArray {
        val aTop = if (focused) 235 else 165
        val aBot = if (focused) 250 else 195
        return when (key) {
            "sapphire" -> intArrayOf(Color.argb(aTop, 38, 128, 228), Color.argb(aBot, 20, 62, 136))
            "emerald" -> intArrayOf(Color.argb(aTop, 32, 158, 122), Color.argb(aBot, 14, 78, 62))
            "rose" -> intArrayOf(Color.argb(aTop, 198, 68, 118), Color.argb(aBot, 112, 32, 68))
            "amethyst" -> intArrayOf(Color.argb(aTop, 138, 76, 224), Color.argb(aBot, 72, 36, 134))
            "gold" -> intArrayOf(Color.argb(aTop, 58, 68, 92), Color.argb(aBot, 32, 38, 54))
            else -> intArrayOf(Color.argb(aTop, 64, 82, 176), Color.argb(aBot, 42, 52, 126))
        }
    }

    fun getWallpaperGradient(key: String): IntArray {
        return when (key) {
            "sapphire" -> intArrayOf(Color.rgb(16, 52, 118), Color.rgb(24, 82, 156), Color.rgb(12, 32, 76))
            "emerald" -> intArrayOf(Color.rgb(14, 62, 56), Color.rgb(22, 92, 78), Color.rgb(10, 38, 34))
            "rose" -> intArrayOf(Color.rgb(72, 22, 56), Color.rgb(104, 32, 74), Color.rgb(40, 12, 32))
            "amethyst" -> intArrayOf(Color.rgb(44, 26, 104), Color.rgb(68, 38, 142), Color.rgb(24, 16, 62))
            "gold" -> intArrayOf(Color.rgb(24, 28, 40), Color.rgb(38, 44, 60), Color.rgb(16, 18, 28))
            else -> intArrayOf(Color.rgb(26, 38, 112), Color.rgb(46, 36, 124), Color.rgb(20, 26, 78))
        }
    }
}
