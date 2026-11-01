package com.aniket.tapshare.ui.animation

import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalView
import com.aniket.tapshare.util.TapLog
import kotlin.math.exp

/**
 * The "phones touched" effect, drawn over the whole app:
 *  - a bright white glow that blooms out of the front camera,
 *  - a ring of light that travels down the screen,
 *  - (Android 13+) the screen content itself warps like water under that ring (AGSL shader).
 *
 * Timing follows what is described for iOS: glow + haptic at once, ~1 s of ripple, then the
 * poster. The exact curve still needs tuning against a real iPhone video (Phase 4.5).
 *
 * [trigger] changes value (e.g. a timestamp) each time a new tap happens; 0 = never.
 */
@Composable
fun TapRippleHost(trigger: Long, content: @Composable BoxScope.() -> Unit) {
    var time by remember { mutableFloatStateOf(DONE) }
    LaunchedEffect(trigger) {
        if (trigger == 0L) return@LaunchedEffect
        val start = withFrameNanos { it }
        while (true) {
            val t = withFrameNanos { (it - start) / 1_000_000_000f }
            time = t
            if (t >= DURATION_S) break
        }
        time = DONE
    }

    val view = LocalView.current
    val origin = remember(view, trigger) { cameraOrigin(view) }
    val shader = remember { if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) RippleShader.createOrNull() else null }
    val active = time < DURATION_S

    Box(
        Modifier
            .graphicsLayer {
                if (active && shader != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    val o = origin(size.width)
                    renderEffect = RippleShader.effect(shader, o, time, size.height).asComposeRenderEffect()
                } else {
                    renderEffect = null
                }
            }
            .drawWithContent {
                drawContent()
                if (!active) return@drawWithContent
                val o = origin(size.width)
                val t = time
                // Bloom at the camera: fast in (~120 ms), slow out.
                val bloom = (t / 0.12f).coerceAtMost(1f) * exp(-t * 2.2f)
                val bloomRadius = size.width * (0.18f + 0.55f * (1f - exp(-t * 3f)))
                drawCircle(
                    brush = Brush.radialGradient(
                        0f to Color.White.copy(alpha = 0.95f * bloom),
                        0.35f to Color(0xFFEAF2FF).copy(alpha = 0.55f * bloom),
                        1f to Color.Transparent,
                        center = o,
                        radius = bloomRadius,
                    ),
                    radius = bloomRadius,
                    center = o,
                )
                // Travelling ring of light (also on Android 10–12, where there is no warp).
                val front = ringFront(t, size.height)
                val ringFade = exp(-t * 1.8f)
                val ringWidth = 60f + 160f * t
                drawCircle(
                    brush = Brush.radialGradient(
                        0f to Color.Transparent,
                        ((front - ringWidth).coerceAtLeast(0f) / (front + ringWidth)) to Color.Transparent,
                        (front / (front + ringWidth)) to Color.White.copy(alpha = 0.45f * ringFade),
                        1f to Color.Transparent,
                        center = o,
                        radius = front + ringWidth,
                    ),
                    radius = front + ringWidth,
                    center = o,
                )
            },
        content = content,
    )
}

private const val DURATION_S = 1.4f
private const val DONE = 99f

/** Ease-out travel of the ring: quick at first, slowing down (never linear). */
private fun ringFront(t: Float, height: Float): Float = height * 0.95f * (1f - exp(-t * 2.4f))

/**
 * Where the front camera is: centre of the top display cutout (punch-hole, notch, waterdrop),
 * or top-centre if the phone has none. Returns a function of the drawing width so it also works
 * before layout.
 */
private fun cameraOrigin(view: android.view.View): (Float) -> Offset {
    val rect = view.rootWindowInsets?.displayCutout?.boundingRects
        ?.filter { it.top <= 0 || it.top < it.height() }
        ?.minByOrNull { it.top }
    if (rect != null) {
        val point = Offset(rect.exactCenterX(), rect.exactCenterY())
        return { _: Float -> point }
    }
    return { width: Float -> Offset(width / 2f, 0f) }
}

/** AGSL water-ripple that warps the app's own pixels (Android 13+). */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
private object RippleShader {
    private const val SOURCE = """
        uniform shader content;
        uniform float2 uOrigin;
        uniform float uTime;
        uniform float uHeight;

        half4 main(float2 p) {
            float2 d = p - uOrigin;
            float dist = length(d);
            float front = uHeight * 0.95 * (1.0 - exp(-uTime * 2.4));
            float width = 60.0 + 160.0 * uTime;
            float x = (dist - front) / width;
            float band = exp(-x * x * 3.0);
            float fade = exp(-uTime * 1.8);
            float2 dir = dist > 0.001 ? d / dist : float2(0.0, 0.0);
            float disp = sin(x * 6.2831) * band * 26.0 * fade;
            half4 c = content.eval(p - dir * disp);
            float near = exp(-(dist * dist) / (2.0 * 140.0 * 140.0));
            float glow = band * fade * 0.35 + near * fade * 0.5;
            half g = half(glow);
            c.rgb = clamp(c.rgb + half3(g * 0.96, g * 0.98, g), half3(0.0), half3(1.0));
            return c;
        }
    """

    /** Null if this phone's GPU driver rejects the shader — we then just skip the warp. */
    fun createOrNull(): RuntimeShader? = try {
        RuntimeShader(SOURCE)
    } catch (e: IllegalArgumentException) {
        TapLog.w("Ripple shader not supported on this phone", e)
        null
    }

    fun effect(shader: RuntimeShader, origin: Offset, time: Float, height: Float): RenderEffect {
        shader.setFloatUniform("uOrigin", origin.x, origin.y)
        shader.setFloatUniform("uTime", time)
        shader.setFloatUniform("uHeight", height)
        return RenderEffect.createRuntimeShaderEffect(shader, "content")
    }
}
