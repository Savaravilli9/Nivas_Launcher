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
        val aTop = if (focused) 235 else 155
        val aBot = if (focused) 250 else 185
        return when (key) {
            "sapphire" -> intArrayOf(Color.argb(aTop, 32, 138, 235), Color.argb(aBot, 16, 66, 145))
            "emerald" -> intArrayOf(Color.argb(aTop, 28, 168, 124), Color.argb(aBot, 12, 82, 62))
            "rose" -> intArrayOf(Color.argb(aTop, 224, 72, 118), Color.argb(aBot, 128, 32, 68))
            "amethyst" -> intArrayOf(Color.argb(aTop, 148, 72, 235), Color.argb(aBot, 76, 32, 142))
            "gold" -> intArrayOf(Color.argb(aTop, 56, 64, 86), Color.argb(aBot, 30, 35, 50))
            else -> intArrayOf(Color.argb(aTop, 62, 84, 182), Color.argb(aBot, 38, 52, 128))
        }
    }

    fun getWallpaperGradient(key: String): IntArray {
        return when (key) {
            "sapphire" -> intArrayOf(Color.rgb(8, 36, 92), Color.rgb(18, 78, 152), Color.rgb(10, 42, 98))
            "emerald" -> intArrayOf(Color.rgb(8, 46, 44), Color.rgb(18, 92, 76), Color.rgb(10, 48, 42))
            "rose" -> intArrayOf(Color.rgb(58, 16, 48), Color.rgb(112, 32, 74), Color.rgb(48, 14, 42))
            "amethyst" -> intArrayOf(Color.rgb(32, 18, 86), Color.rgb(72, 34, 148), Color.rgb(28, 16, 72))
            "gold" -> intArrayOf(Color.rgb(18, 22, 32), Color.rgb(34, 40, 54), Color.rgb(16, 18, 26))
            else -> intArrayOf(Color.rgb(18, 28, 94), Color.rgb(44, 38, 132), Color.rgb(22, 28, 88))
        }
    }
}
