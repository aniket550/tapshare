package com.aniket.tapshare.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.content.IntentCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.aniket.tapshare.R
import com.aniket.tapshare.appContainer
import com.aniket.tapshare.service.ReadinessPolicy
import com.aniket.tapshare.service.ServiceController
import com.aniket.tapshare.ui.theme.TapShareTheme
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge() // draw behind status/nav bars; status-bar icon colour follows light/dark
        setContent {
            TapShareTheme {
                TapShareRoot()
            }
        }
        // Opened from the share sheet ("Share → TapShare"). Not on rotation (savedInstanceState != null).
        if (savedInstanceState == null) handleShareIntent(intent)

        // While we're visible (the only time Android 12+ lets us start a foreground service),
        // keep the service matching "sharing on + permissions granted + BLE supported".
        val container = appContainer
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                combine(
                    container.conditions.conditions,
                    container.serviceRunning,
                ) { c, running ->
                    ReadinessPolicy.serviceShouldRun(c.sharingEnabled, c.permissionsGranted, c.bluetooth) to running
                }
                    .distinctUntilChanged()
                    .collect { (shouldRun, _) -> ServiceController.sync(this@MainActivity, shouldRun) }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleShareIntent(intent)
    }

    override fun onStart() {
        super.onStart()
        // App visible → fast adverts/scans (live numbers) and taps can start a share.
        appContainer.discovery.setForeground(true)
        appContainer.session.setForeground(true)
    }

    override fun onStop() {
        appContainer.session.setForeground(false)
        appContainer.discovery.setForeground(false)
        super.onStop()
    }

    override fun onResume() {
        super.onResume()
        // Permissions can change in Settings while we were away; there is no callback for that.
        appContainer.permissions.refresh(this)
    }

    /** Photos/files shared to us from Gallery, Files, etc. go into the outbox. */
    private fun handleShareIntent(intent: Intent?) {
        val uris: List<Uri> = when (intent?.action) {
            Intent.ACTION_SEND ->
                listOfNotNull(IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java))
            Intent.ACTION_SEND_MULTIPLE ->
                IntentCompat.getParcelableArrayListExtra(intent, Intent.EXTRA_STREAM, Uri::class.java).orEmpty()
            else -> return
        }
        if (uris.isEmpty()) {
            // e.g. a shared link or plain text — not supported in v1.
            Toast.makeText(this, R.string.share_nothing_to_send, Toast.LENGTH_LONG).show()
            return
        }
        lifecycleScope.launch { appContainer.outbox.set(uris) }
    }
}
