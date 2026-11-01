package com.aniket.tapshare.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/**
 * iOS-style semantic colours (values match the look of iOS system colours; written by us,
 * no Apple assets). Names follow what each colour is FOR, not what it looks like.
 */
@Immutable
data class TapShareColors(
    val isDark: Boolean,
    val groupedBackground: Color,
    val cell: Color,
    val cellPressed: Color,
    val label: Color,
    val secondaryLabel: Color,
    val tertiaryLabel: Color,
    val separator: Color,
    val accent: Color,
    val green: Color,
    val red: Color,
    val orange: Color,
    val gray: Color,
    val switchTrackOff: Color,
)

val LightColors = TapShareColors(
    isDark = false,
    groupedBackground = Color(0xFFF2F2F7),
    cell = Color(0xFFFFFFFF),
    cellPressed = Color(0xFFD1D1D6),
    label = Color(0xFF000000),
    secondaryLabel = Color(0x993C3C43),
    tertiaryLabel = Color(0x4D3C3C43),
    separator = Color(0x4A3C3C43),
    accent = Color(0xFF007AFF),
    green = Color(0xFF34C759),
    red = Color(0xFFFF3B30),
    orange = Color(0xFFFF9500),
    gray = Color(0xFF8E8E93),
    switchTrackOff = Color(0x29787880),
)

val DarkColors = TapShareColors(
    isDark = true,
    groupedBackground = Color(0xFF000000),
    cell = Color(0xFF1C1C1E),
    cellPressed = Color(0xFF3A3A3C),
    label = Color(0xFFFFFFFF),
    secondaryLabel = Color(0x99EBEBF5),
    tertiaryLabel = Color(0x4DEBEBF5),
    separator = Color(0x99545458),
    accent = Color(0xFF0A84FF),
    green = Color(0xFF30D158),
    red = Color(0xFFFF453A),
    orange = Color(0xFFFF9F0A),
    gray = Color(0xFF8E8E93),
    switchTrackOff = Color(0x52787880),
)
