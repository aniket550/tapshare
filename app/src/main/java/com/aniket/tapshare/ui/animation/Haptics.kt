package com.aniket.tapshare.ui.animation

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/**
 * The few haptics TapShare uses, built from Android's haptic "primitives" when the phone supports
 * them (Android 11+, good actuators), otherwise a short one-shot buzz. Unsupported primitives
 * would play NOTHING, so we always check first.
 */
class Haptics(context: Context) {

    private val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        context.getSystemService(VibratorManager::class.java)?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Vibrator::class.java)
    }

    /** Phones touched: one strong, deep thump (with the glow). */
    fun tap() = play(
        primitives = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            listOf(VibrationEffect.Composition.PRIMITIVE_THUD to 1f, VibrationEffect.Composition.PRIMITIVE_CLICK to 0.6f)
        } else {
            listOf(VibrationEffect.Composition.PRIMITIVE_CLICK to 1f)
        },
        fallbackMs = 45,
    )

    /** Connected / the other card arrived: light tick. */
    fun connected() = play(listOf(VibrationEffect.Composition.PRIMITIVE_TICK to 0.8f), fallbackMs = 15)

    /** Transfer finished: rising then click (feels like "done"). */
    fun success() = play(
        listOf(VibrationEffect.Composition.PRIMITIVE_QUICK_RISE to 0.7f, VibrationEffect.Composition.PRIMITIVE_CLICK to 1f),
        fallbackMs = 30,
    )

    /** Something failed: two short knocks. */
    fun error() = play(
        listOf(VibrationEffect.Composition.PRIMITIVE_CLICK to 0.8f, VibrationEffect.Composition.PRIMITIVE_CLICK to 0.8f),
        fallbackMs = 60,
    )

    private fun play(primitives: List<Pair<Int, Float>>, fallbackMs: Long) {
        val v = vibrator ?: return
        if (!v.hasVibrator()) return
        val ids = primitives.map { it.first }.toIntArray()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && v.areAllPrimitivesSupported(*ids)) {
            val composition = VibrationEffect.startComposition()
            primitives.forEachIndexed { i, (id, scale) -> composition.addPrimitive(id, scale, if (i == 0) 0 else 40) }
            v.vibrate(composition.compose())
        } else {
            v.vibrate(VibrationEffect.createOneShot(fallbackMs, VibrationEffect.DEFAULT_AMPLITUDE))
        }
    }
}
