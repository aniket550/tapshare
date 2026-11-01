package com.aniket.tapshare.ui.home

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.net.wifi.WifiManager
import android.provider.Settings
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aniket.tapshare.R
import com.aniket.tapshare.appContainer
import com.aniket.tapshare.ui.components.CapsuleButton
import com.aniket.tapshare.ui.components.GroupedRow
import com.aniket.tapshare.ui.components.GroupedSection
import com.aniket.tapshare.ui.components.IosSwitch
import com.aniket.tapshare.ui.theme.TapShareTheme
import com.aniket.tapshare.util.SystemScreens
import com.aniket.tapshare.util.TapLog

/** "Transfer speed" and "When TapShare is closed" sections on Home. */
@Composable
fun SpeedAndBackgroundSections() {
    val context = LocalContext.current
    val activity = LocalActivity.current
    val container = context.appContainer
    val colors = TapShareTheme.colors
    val settings = container.settings
    val fast by settings.fastTransfer.collectAsStateWithLifecycle()
    val smaller by settings.smallerPhotos.collectAsStateWithLifecycle()
    val background by settings.backgroundEnabled.collectAsStateWithLifecycle()
    val overlayAllowed by container.permissions.overlayAllowed.collectAsStateWithLifecycle()
    val bgLocation by container.permissions.backgroundLocationGranted.collectAsStateWithLifecycle()

    // Wi-Fi has no simple change callback we need here; re-read when the screen comes back.
    var wifiOn by remember { mutableStateOf(isWifiOn(context)) }
    LifecycleResumeEffect(Unit) {
        wifiOn = isWifiOn(context)
        onPauseOrDispose { }
    }

    val askBgLocation = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        container.permissions.refresh(activity)
        if (!granted) SystemScreens.appDetails(context) // Android 11 sends "all the time" to Settings anyway
    }

    // ---- Transfer speed ----
    Spacer(Modifier.height(28.dp))
    GroupedSection(
        header = stringResource(R.string.speed_section),
        footer = stringResource(R.string.speed_footer),
    ) {
        GroupedRow(
            title = stringResource(R.string.speed_fast),
            subtitle = stringResource(R.string.speed_fast_body),
            icon = painterResource(R.drawable.ic_bolt),
            iconBackground = colors.orange,
            showDivider = true,
        ) {
            IosSwitch(fast, settings::setFastTransfer)
        }
        if (fast && !wifiOn) {
            GroupedRow(
                title = stringResource(R.string.speed_wifi_off),
                titleColor = colors.orange,
                showDivider = true,
            ) {
                CapsuleButton(stringResource(R.string.speed_turn_on_wifi), onClick = { openWifiPanel(context) })
            }
        }
        GroupedRow(
            title = stringResource(R.string.speed_smaller),
            subtitle = stringResource(R.string.speed_smaller_body),
            icon = painterResource(R.drawable.ic_photo),
            iconBackground = colors.green,
        ) {
            IosSwitch(smaller, settings::setSmallerPhotos)
        }
    }

    // ---- When TapShare is closed ----
    Spacer(Modifier.height(28.dp))
    GroupedSection(
        header = stringResource(R.string.bg_section),
        footer = stringResource(R.string.bg_footer),
    ) {
        GroupedRow(
            title = stringResource(R.string.bg_toggle),
            icon = painterResource(R.drawable.ic_power),
            iconBackground = colors.accent,
            showDivider = background && (!overlayAllowed || !bgLocation),
        ) {
            IosSwitch(background, { on ->
                settings.setBackgroundEnabled(on)
                container.session.refreshTaps()
            })
        }
        if (background && !overlayAllowed) {
            GroupedRow(
                title = stringResource(R.string.bg_overlay_title),
                subtitle = stringResource(R.string.bg_overlay_body),
                showDivider = !bgLocation,
            ) {
                CapsuleButton(stringResource(R.string.permission_action_allow), onClick = { openOverlaySettings(context) })
            }
        }
        if (background && !bgLocation) {
            GroupedRow(
                title = stringResource(R.string.background_location_title),
                subtitle = stringResource(R.string.background_location_body),
            ) {
                CapsuleButton(stringResource(R.string.permission_action_allow), onClick = {
                    askBgLocation.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                })
            }
        }
    }
}

private fun isWifiOn(context: Context): Boolean =
    context.getSystemService(WifiManager::class.java)?.isWifiEnabled == true

/** Android 10+: a small Wi-Fi panel over the app (apps can't switch Wi-Fi on themselves). */
private fun openWifiPanel(context: Context) {
    try {
        context.startActivity(Intent(Settings.Panel.ACTION_WIFI))
    } catch (e: ActivityNotFoundException) {
        TapLog.w("No Wi-Fi panel", e)
        context.startActivity(Intent(Settings.ACTION_WIFI_SETTINGS))
    }
}

private fun openOverlaySettings(context: Context) {
    val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}"))
    try {
        context.startActivity(intent)
    } catch (e: ActivityNotFoundException) {
        TapLog.w("No overlay settings screen", e)
        SystemScreens.appDetails(context)
    }
}
