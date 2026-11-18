package com.shreenivas.tvhome.ui

import android.graphics.Color

object TileThemePalette {
    val NAMES = listOf(
        "Royal Indigo", "Ocean Breeze", "Cyber Neon", "Sunset Glow",
        "Emerald Forest", "Slate Dark", "Frosted Crystal", "Golden Obsidian"
    )
    val SUBTITLES = listOf(
        "Glossy & Deep", "Cyan & Deep Sea", "Electric & Cyber", "Crimson & Amber",
        "Deep Jungle & Mint", "Graphite & Matte", "Vision Pure Glass", "Luxury Black & Gold"
    )
    val KEYS = listOf(
        "frost", "ocean", "cyber", "sunset",
        "emerald", "dark", "crystal", "obsidian"
    )

    fun getColors(themeKey: String?, accent: Int): Pair<IntArray, IntArray> {
        return when (themeKey) {
            "ocean" -> Pair(
                intArrayOf(Color.argb(150, 12, 58, 120), Color.argb(110, 6, 36, 82), Color.argb(135, 16, 86, 155)),
                intArrayOf(Color.argb(215, 22, 115, 215), Color.argb(165, 10, 65, 145), Color.argb(200, 32, 155, 245))
            )
            "cyber" -> Pair(
                intArrayOf(Color.argb(155, 58, 22, 115), Color.argb(115, 32, 12, 72), Color.argb(145, 88, 28, 155)),
                intArrayOf(Color.argb(220, 135, 52, 235), Color.argb(175, 78, 24, 165), Color.argb(215, 185, 72, 255))
            )
            "sunset" -> Pair(
                intArrayOf(Color.argb(155, 120, 32, 68), Color.argb(115, 78, 18, 46), Color.argb(145, 155, 48, 72)),
                intArrayOf(Color.argb(225, 225, 78, 108), Color.argb(175, 155, 38, 75), Color.argb(215, 255, 115, 92))
            )
            "emerald" -> Pair(
                intArrayOf(Color.argb(150, 14, 78, 62), Color.argb(110, 8, 48, 38), Color.argb(135, 22, 105, 82)),
                intArrayOf(Color.argb(220, 26, 158, 120), Color.argb(175, 14, 105, 78), Color.argb(215, 48, 205, 155))
            )
            "dark" -> Pair(
                intArrayOf(Color.argb(180, 18, 22, 34), Color.argb(160, 10, 14, 22), Color.argb(175, 26, 30, 44)),
                intArrayOf(Color.argb(230, 38, 46, 68), Color.argb(200, 22, 28, 44), Color.argb(225, 52, 62, 92))
            )
            "crystal" -> Pair(
                intArrayOf(Color.argb(95, 235, 245, 255), Color.argb(55, 185, 215, 250), Color.argb(85, 220, 240, 255)),
                intArrayOf(Color.argb(155, 255, 255, 255), Color.argb(105, 210, 235, 255), Color.argb(145, 240, 250, 255))
            )
            "obsidian" -> Pair(
                intArrayOf(Color.argb(195, 18, 16, 14), Color.argb(175, 10, 10, 12), Color.argb(190, 34, 28, 18)),
                intArrayOf(Color.argb(235, 68, 54, 26), Color.argb(210, 28, 24, 18), Color.argb(230, 115, 88, 36))
            )
            else -> Pair(
                intArrayOf(Color.argb(145, 26, 40, 82), Color.argb(100, 16, 26, 56), Color.argb(130, 38, 56, 108)),
                intArrayOf(Color.argb(210, 58, 92, 168), Color.argb(155, 32, 54, 112), Color.argb(195, 78, 118, 205))
            )
        }
    }
}
