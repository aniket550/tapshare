package com.aniket.tapshare.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.aniket.tapshare.R

/** Inter (free, OFL licence) — closest free match to the iOS system font's feel. */
val Inter = FontFamily(
    Font(R.font.inter_regular, FontWeight.Normal),
    Font(R.font.inter_medium, FontWeight.Medium),
    Font(R.font.inter_semibold, FontWeight.SemiBold),
    Font(R.font.inter_bold, FontWeight.Bold),
)

/** Sizes follow the iOS text-style ladder (Large Title 34 … Caption 12). Tight tracking like iOS. */
@Immutable
data class TapShareType(
    val largeTitle: TextStyle,
    val title2: TextStyle,
    val headline: TextStyle,
    val body: TextStyle,
    val subheadline: TextStyle,
    val footnote: TextStyle,
    val caption: TextStyle,
)

private fun style(size: Int, line: Int, weight: FontWeight, tracking: Double) = TextStyle(
    fontFamily = Inter,
    fontSize = size.sp,
    lineHeight = line.sp,
    fontWeight = weight,
    letterSpacing = tracking.em,
)

val DefaultType = TapShareType(
    largeTitle = style(34, 41, FontWeight.Bold, -0.022),
    title2 = style(22, 28, FontWeight.Bold, -0.019),
    headline = style(17, 22, FontWeight.SemiBold, -0.017),
    body = style(17, 22, FontWeight.Normal, -0.017),
    subheadline = style(15, 20, FontWeight.Normal, -0.013),
    footnote = style(13, 18, FontWeight.Normal, -0.006),
    caption = style(12, 16, FontWeight.Normal, 0.0),
)
