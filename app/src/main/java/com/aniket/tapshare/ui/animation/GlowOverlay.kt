package com.aniket.tapshare.ui.animation

import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.provider.Settings
import android.view.WindowManager
import com.aniket.tapshare.util.TapLog

/**
 * Shows the rainbow edge glow over ANY app (TapShare closed) for a moment after a tap.
 * Needs "Display over other apps". The window never takes touches, and its alpha stays at 0.8 —
 * Android 12+ blocks touches that pass through more opaque windows of other apps.
 */
class GlowOverlay(private val context: Context) {

    private val windowManager = context.getSystemService(WindowManager::class.java)
    private var view: EdgeGlowView? = null

    fun isAllowed(): Boolean = Settings.canDrawOverlays(context)

    /** Returns true if the glow is now visible. Main thread only. */
    fun show(): Boolean {
        if (!isAllowed() || windowManager == null) return false
        hide()
        val glow = EdgeGlowView(context)
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSLUCENT,
        ).apply {
            alpha = 0.8f
            layoutInDisplayCutoutMode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            } else {
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
            title = "TapShare glow"
        }
        return try {
            windowManager.addView(glow, params)
            glow.onFinished = { hide() }
            glow.play()
            view = glow
            true
        } catch (e: RuntimeException) {
            // BadTokenException / permission revoked between the check and addView.
            TapLog.w("Overlay: couldn't show glow", e)
            false
        }
    }

    fun hide() {
        val v = view ?: return
        view = null
        runCatching { windowManager?.removeView(v) }
    }
}
