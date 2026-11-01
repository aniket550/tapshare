package com.aniket.tapshare.ui.animation

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.BlurMaskFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PathMeasure
import android.graphics.RectF
import android.graphics.SweepGradient
import android.view.View
import android.view.animation.LinearInterpolator
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalDensity
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlin.math.max
import kotlin.math.min

/**
 * Siri/Apple-Intelligence-style rainbow glow around the screen edges (our own drawing, no Apple assets).
 *
 * Layers: a wide blurred bloom, a medium blurred band and a thin bright line, all stroked along a
 * rounded rectangle that follows the screen corners and filled with a rotating sweep gradient.
 * The glow first runs out of the camera (top centre) along both edges ([reveal] 0→1), then rotates.
 */
class EdgeGlowPainter(density: Float) {

    private val colors = intArrayOf(
        0xFF3A7BFF.toInt(), // blue
        0xFFA15BFF.toInt(), // violet
        0xFFFF5FA8.toInt(), // pink
        0xFFFFA24A.toInt(), // orange
        0xFF3EDCFF.toInt(), // cyan
        0xFF3A7BFF.toInt(), // back to blue (seamless loop)
    )
    private val cornerRadius = 44f * density
    private val bloomWidth = 34f * density
    private val bandWidth = 12f * density
    private val lineWidth = 3f * density

    private val bloom = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = bloomWidth
        maskFilter = BlurMaskFilter(26f * density, BlurMaskFilter.Blur.NORMAL)
    }
    private val band = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = bandWidth
        maskFilter = BlurMaskFilter(8f * density, BlurMaskFilter.Blur.NORMAL)
    }
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = lineWidth
        strokeCap = Paint.Cap.ROUND
    }

    private val outline = Path()
    private val segment = Path()
    private val measure = PathMeasure()
    private val rect = RectF()
    private val matrix = Matrix()
    private var cachedW = -1f
    private var cachedH = -1f
    private var length = 0f
    private var gradient: SweepGradient? = null

    /**
     * @param reveal 0..1 how far the glow has travelled from the camera around the edges.
     * @param intensity 0..1.5 overall brightness (above 1 = "connected" pulse).
     * @param rotationDeg colour rotation.
     */
    fun draw(canvas: android.graphics.Canvas, width: Float, height: Float, reveal: Float, intensity: Float, rotationDeg: Float) {
        if (intensity <= 0.01f || reveal <= 0.001f) return
        ensurePath(width, height)
        val g = gradient ?: return
        matrix.setRotate(rotationDeg, width / 2f, height / 2f)
        g.setLocalMatrix(matrix)

        // Path starts at the top centre (camera) and runs clockwise; show both ends growing.
        segment.reset()
        val half = (length / 2f) * reveal.coerceIn(0f, 1f)
        measure.getSegment(0f, half, segment, true)
        measure.getSegment(length - half, length, segment, true)

        val a = intensity.coerceIn(0f, 1.5f)
        bloom.alpha = (min(1f, a) * 150).toInt()
        band.alpha = (min(1f, a) * 210).toInt()
        line.alpha = (min(1f, a * 1.1f) * 255).toInt()
        bloom.strokeWidth = bloomWidth * (0.8f + 0.4f * a)
        canvas.drawPath(segment, bloom)
        canvas.drawPath(segment, band)
        canvas.drawPath(segment, line)
    }

    private fun ensurePath(width: Float, height: Float) {
        if (width == cachedW && height == cachedH) return
        cachedW = width
        cachedH = height
        val inset = lineWidth / 2f
        rect.set(inset, inset, width - inset, height - inset)
        val r = min(cornerRadius, min(width, height) / 2f)
        outline.reset()
        // Start at top centre, go clockwise around the rounded rectangle.
        outline.moveTo(width / 2f, rect.top)
        outline.lineTo(rect.right - r, rect.top)
        outline.arcTo(RectF(rect.right - 2 * r, rect.top, rect.right, rect.top + 2 * r), -90f, 90f)
        outline.lineTo(rect.right, rect.bottom - r)
        outline.arcTo(RectF(rect.right - 2 * r, rect.bottom - 2 * r, rect.right, rect.bottom), 0f, 90f)
        outline.lineTo(rect.left + r, rect.bottom)
        outline.arcTo(RectF(rect.left, rect.bottom - 2 * r, rect.left + 2 * r, rect.bottom), 90f, 90f)
        outline.lineTo(rect.left, rect.top + r)
        outline.arcTo(RectF(rect.left, rect.top, rect.left + 2 * r, rect.top + 2 * r), 180f, 90f)
        outline.close()
        measure.setPath(outline, true)
        length = measure.length
        gradient = SweepGradient(width / 2f, height / 2f, colors, null).also {
            bloom.shader = it
            band.shader = it
            line.shader = it
        }
    }
}

/** What the glow should be doing right now. */
enum class GlowMode {
    OFF,

    /** Phones just touched: run out of the camera, bright. */
    TAP,

    /** Connecting / waiting / transferring: softer, keeps rotating. */
    WORKING,
}

/**
 * Compose host for the edge glow, drawn above everything in the app.
 * [pulseKey] changes → one bright "connected" pulse.
 */
@Composable
fun EdgeGlowHost(mode: GlowMode, pulseKey: Long) {
    val density = LocalDensity.current.density
    val painter = remember(density) { EdgeGlowPainter(density) }
    val reveal = remember { Animatable(0f) }
    val intensity = remember { Animatable(0f) }
    val pulse = remember { Animatable(0f) }

    LaunchedEffect(mode) {
        when (mode) {
            GlowMode.TAP -> {
                if (reveal.value < 0.99f) {
                    reveal.snapTo(0f)
                    intensity.snapTo(0f)
                }
                // Both at once: travel out of the camera while getting bright.
                coroutineScope {
                    launch { intensity.animateTo(1f, tween(260, easing = FastOutSlowInEasing)) }
                    reveal.animateTo(1f, tween(720, easing = FastOutSlowInEasing))
                }
            }
            GlowMode.WORKING -> {
                reveal.animateTo(1f, tween(400, easing = FastOutSlowInEasing))
                intensity.animateTo(0.6f, spring(dampingRatio = 1f, stiffness = 120f))
            }
            GlowMode.OFF -> {
                intensity.animateTo(0f, tween(450, easing = FastOutSlowInEasing))
                reveal.snapTo(0f)
            }
        }
    }
    LaunchedEffect(pulseKey) {
        if (pulseKey == 0L) return@LaunchedEffect
        pulse.snapTo(0f)
        pulse.animateTo(1f, tween(180, easing = FastOutSlowInEasing))
        pulse.animateTo(0f, tween(700, easing = FastOutSlowInEasing))
    }
    val rotation by rememberInfiniteTransition(label = "glowSpin").animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(3000, easing = LinearEasing), RepeatMode.Restart),
        label = "rotation",
    )
    if (intensity.value <= 0.01f && pulse.value <= 0.01f) return
    Canvas(Modifier.fillMaxSize()) {
        drawIntoCanvas { canvas ->
            painter.draw(
                canvas.nativeCanvas,
                size.width,
                size.height,
                reveal.value,
                intensity.value + 0.6f * pulse.value,
                rotation,
            )
        }
    }
}

/**
 * Plain View version of the glow for the "app closed" overlay window (no Compose there).
 * Plays: run out of the camera, rotate, fade out after [visibleMs].
 */
class EdgeGlowView(context: Context, private val visibleMs: Long = 1_600) : View(context) {
    private val painter = EdgeGlowPainter(resources.displayMetrics.density)
    private var t = 0f // seconds since start
    private val animator = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = visibleMs + FADE_MS
        interpolator = LinearInterpolator()
        addUpdateListener {
            t = it.animatedFraction * (visibleMs + FADE_MS) / 1000f
            invalidate()
        }
    }

    var onFinished: (() -> Unit)? = null

    fun play() {
        animator.removeAllListeners()
        animator.addListener(object : android.animation.AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: android.animation.Animator) {
                onFinished?.invoke()
            }
        })
        animator.start()
    }

    override fun onDetachedFromWindow() {
        animator.cancel()
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: android.graphics.Canvas) {
        val visible = visibleMs / 1000f
        val reveal = easeOut(min(1f, t / 0.72f))
        val fadeIn = easeOut(min(1f, t / 0.26f))
        val fadeOut = if (t <= visible) 1f else max(0f, 1f - (t - visible) / (FADE_MS / 1000f))
        painter.draw(canvas, width.toFloat(), height.toFloat(), reveal, fadeIn * fadeOut, (t / 3f) * 360f)
    }

    private fun easeOut(x: Float) = 1f - (1f - x) * (1f - x) * (1f - x)

    private companion object {
        const val FADE_MS = 450L
    }
}
