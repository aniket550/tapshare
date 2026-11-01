package com.aniket.tapshare.ui.components

import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aniket.tapshare.share.loadThumbnail
import com.aniket.tapshare.ui.theme.TapShareTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Decodes a small JPEG (from the other phone) once per byte array. */
@Composable
fun rememberJpeg(bytes: ByteArray?): ImageBitmap? = remember(bytes) {
    bytes?.let { BitmapFactory.decodeByteArray(it, 0, it.size)?.asImageBitmap() }
}

/** System thumbnail of a content Uri, loaded off the main thread. */
@Composable
fun rememberThumbnail(uri: Uri, sizePx: Int = 300): ImageBitmap? {
    val context = LocalContext.current
    val image by produceState<ImageBitmap?>(null, uri) {
        value = withContext(Dispatchers.IO) { loadThumbnail(context, uri, sizePx)?.asImageBitmap() }
    }
    return image
}

/** Round photo, or initials on a gradient when there is no photo. */
@Composable
fun Avatar(
    image: ImageBitmap?,
    initials: String,
    size: Dp,
    modifier: Modifier = Modifier,
    borderColor: Color? = null,
) {
    val base = modifier
        .size(size)
        .clip(CircleShape)
        .let { if (borderColor != null) it.border(size * 0.03f, borderColor, CircleShape) else it }
    if (image != null) {
        Image(image, contentDescription = null, contentScale = ContentScale.Crop, modifier = base)
    } else {
        Box(
            base.background(Brush.linearGradient(listOf(Color(0xFFA1A1AA), Color(0xFF6B6B73)))),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                initials.ifEmpty { "?" },
                color = Color.White,
                fontSize = (size.value * 0.38f).sp,
                style = TapShareTheme.type.largeTitle,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/**
 * Ring around an avatar. [progress] null = waiting (a spinning arc); otherwise the arc grows
 * with a spring to the given fraction.
 */
@Composable
fun ProgressRing(progress: Float?, color: Color, modifier: Modifier = Modifier, strokeWidth: Dp = 5.dp) {
    val animated by animateFloatAsState(progress ?: 0f, spring(dampingRatio = 1f, stiffness = 120f), label = "ring")
    val spin by rememberInfiniteTransition(label = "ringSpin").animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(1100, easing = LinearEasing), RepeatMode.Restart),
        label = "spin",
    )
    val track = TapShareTheme.colors.switchTrackOff
    Canvas(modifier) {
        val stroke = strokeWidth.toPx()
        val inset = stroke / 2
        val arcSize = Size(size.width - stroke, size.height - stroke)
        val topLeft = Offset(inset, inset)
        drawArc(track, 0f, 360f, false, topLeft, arcSize, style = Stroke(stroke))
        if (progress == null) {
            drawArc(color, spin - 90f, 70f, false, topLeft, arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
        } else {
            drawArc(color, -90f, 360f * animated, false, topLeft, arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
        }
    }
}

/** Plain iOS-style text field that sits inside a [GroupedRow]-like cell. */
@Composable
fun IosTextFieldRow(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    keyboardType: KeyboardType = KeyboardType.Text,
    showDivider: Boolean = false,
) {
    val colors = TapShareTheme.colors
    val type = TapShareTheme.type
    Box(Modifier.fillMaxWidth().background(colors.cell)) {
        Box(Modifier.fillMaxWidth().heightIn(min = 44.dp).padding(horizontal = 16.dp, vertical = 11.dp)) {
            if (value.isEmpty()) Text(placeholder, style = type.body, color = colors.tertiaryLabel)
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = type.body.copy(color = colors.label),
                cursorBrush = SolidColor(colors.accent),
                keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (showDivider) {
            Box(
                Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 16.dp)
                    .fillMaxWidth()
                    .height(0.5.dp)
                    .background(colors.separator),
            )
        }
    }
}

/** Fills its parent with a soft, slowly breathing glow (behind posters). */
@Composable
fun BreathingGlow(color: Color, modifier: Modifier = Modifier) {
    val t by rememberInfiniteTransition(label = "glow").animateFloat(
        0f,
        1f,
        infiniteRepeatable(tween(2600), RepeatMode.Reverse),
        label = "glowT",
    )
    Canvas(modifier.fillMaxSize()) {
        val r = size.minDimension * (0.55f + 0.08f * t)
        drawCircle(
            Brush.radialGradient(
                0f to color.copy(alpha = 0.35f + 0.15f * t),
                1f to Color.Transparent,
                center = Offset(size.width / 2, size.height * 0.38f),
                radius = r,
            ),
            radius = r,
            center = Offset(size.width / 2, size.height * 0.38f),
        )
    }
}
