package com.aniket.tapshare.ui.home

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.dp
import com.aniket.tapshare.ui.theme.TapShareTheme

/** Smooth ease-in-out (no linear motion anywhere). */
private val Breath = CubicBezierEasing(0.42f, 0f, 0.58f, 1f)

/**
 * Two phones leaning in so their tops almost touch, with a soft glow where they meet.
 * When [active] the glow slowly "breathes"; otherwise it fades away and the phones dim.
 * Decorative only (no content description).
 */
@Composable
fun HeroIllustration(active: Boolean, modifier: Modifier = Modifier) {
    val colors = TapShareTheme.colors
    val activeAmount by animateFloatAsState(
        targetValue = if (active) 1f else 0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessVeryLow),
        label = "heroActive",
    )
    val breath by rememberInfiniteTransition(label = "heroBreath").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(durationMillis = 2600, easing = Breath), RepeatMode.Reverse),
        label = "breath",
    )

    val phoneFill = colors.cell
    val phoneStroke = colors.secondaryLabel.copy(alpha = 0.35f + 0.25f * activeAmount)
    val glowCore = Color.White
    val glowTint = colors.accent

    Canvas(modifier) {
        val unit = size.width / 200f // design grid: 200 × 140
        val phoneSize = Size(56f * unit, 100f * unit)
        val corner = CornerRadius(14f * unit)
        val tilt = 14f
        val baseY = size.height - 4f * unit
        // With 14° tilt and pivots 54 apart from centre, the inner top corners meet here.
        val meet = Offset(size.width / 2f, baseY - 90f * unit)

        // Glow behind the phones (drawn first so phones sit on top of it).
        if (activeAmount > 0.01f) {
            val radius = (46f + 10f * breath) * unit
            drawCircle(
                brush = Brush.radialGradient(
                    0f to glowCore.copy(alpha = 0.95f * activeAmount),
                    0.35f to glowTint.copy(alpha = 0.45f * activeAmount),
                    1f to glowTint.copy(alpha = 0f),
                    center = meet,
                    radius = radius,
                ),
                radius = radius,
                center = meet,
            )
        }

        drawPhone(
            pivot = Offset(size.width / 2f - 54f * unit, baseY),
            degrees = tilt,
            size = phoneSize,
            corner = corner,
            fill = phoneFill,
            stroke = phoneStroke,
            strokeWidth = 2f * unit,
            camera = colors.label.copy(alpha = 0.25f),
        )
        drawPhone(
            pivot = Offset(size.width / 2f + 54f * unit, baseY),
            degrees = -tilt,
            size = phoneSize,
            corner = corner,
            fill = phoneFill,
            stroke = phoneStroke,
            strokeWidth = 2f * unit,
            camera = colors.label.copy(alpha = 0.25f),
        )

        // Bright point exactly where the tops meet.
        if (activeAmount > 0.01f) {
            drawCircle(
                color = glowCore.copy(alpha = (0.75f + 0.25f * breath) * activeAmount),
                radius = (5f + 2f * breath) * unit,
                center = meet,
            )
        }
    }
}

/** A phone outline standing on [pivot] (bottom-centre), rotated by [degrees] around it. */
private fun DrawScope.drawPhone(
    pivot: Offset,
    degrees: Float,
    size: Size,
    corner: CornerRadius,
    fill: Color,
    stroke: Color,
    strokeWidth: Float,
    camera: Color,
) {
    val topLeft = Offset(pivot.x - size.width / 2f, pivot.y - size.height)
    rotate(degrees, pivot) {
        drawRoundRect(color = fill, topLeft = topLeft, size = size, cornerRadius = corner)
        drawRoundRect(color = stroke, topLeft = topLeft, size = size, cornerRadius = corner, style = Stroke(strokeWidth))
        // Front camera dot — the real glow will start from here in Phase 2.
        drawCircle(
            color = camera,
            radius = size.width * 0.05f,
            center = Offset(pivot.x, topLeft.y + size.width * 0.14f),
        )
    }
}

/** Size helper so callers keep the 200:140 design ratio. */
val HeroAspectRatio = 200f / 140f
val HeroMaxWidth = 220.dp
