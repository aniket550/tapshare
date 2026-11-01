package com.aniket.tapshare.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
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

    override fun onStart() {
        super.onStart()
        // App visible → fast adverts/scans so the numbers on screen are live.
        appContainer.discovery.setForeground(true)
    }

    override fun onStop() {
        appContainer.discovery.setForeground(false)
        super.onStop()
    }

    override fun onResume() {
        super.onResume()
        // Permissions can change in Settings while we were away; there is no callback for that.
        appContainer.permissions.refresh(this)
    }
}
