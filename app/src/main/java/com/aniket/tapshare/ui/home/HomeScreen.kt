package com.aniket.tapshare.ui.home

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aniket.tapshare.BuildConfig
import com.aniket.tapshare.R
import com.aniket.tapshare.appContainer
import com.aniket.tapshare.service.BluetoothStatus
import com.aniket.tapshare.service.DeviceConditions
import com.aniket.tapshare.service.Problem
import com.aniket.tapshare.service.Readiness
import com.aniket.tapshare.service.ReadinessPolicy
import com.aniket.tapshare.service.text
import com.aniket.tapshare.ui.components.GroupedRow
import com.aniket.tapshare.ui.components.GroupedSection
import com.aniket.tapshare.ui.components.IosSwitch
import com.aniket.tapshare.ui.components.PrimaryButton
import com.aniket.tapshare.ui.components.TrailingValue
import com.aniket.tapshare.ui.theme.TapShareTheme
import com.aniket.tapshare.util.SystemScreens
import com.aniket.tapshare.util.TapLog

/** Wires Home to live state and system actions. */
@Composable
fun HomeRoute() {
    val context = LocalContext.current
    val container = context.appContainer
    val conditions by container.conditions.conditions.collectAsStateWithLifecycle()
    val serviceRunning by container.serviceRunning.collectAsStateWithLifecycle()
    val notificationsOn by container.permissions.notificationsEnabled.collectAsStateWithLifecycle()

    val enableBluetooth = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        // Nothing to do: the Bluetooth state receiver updates the screen by itself.
    }
    val onTurnOnBluetooth: () -> Unit = {
        // ACTION_REQUEST_ENABLE needs BLUETOOTH_CONNECT on Android 12+; Home is only shown once it's granted,
        // but check anyway and fall back to the Bluetooth settings page.
        val canAsk = Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
        if (canAsk) {
            try {
                enableBluetooth.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
            } catch (e: ActivityNotFoundException) {
                TapLog.w("No Bluetooth enable dialog on this phone", e)
                SystemScreens.bluetooth(context)
            }
        } else {
            SystemScreens.bluetooth(context)
        }
    }

    HomeScreen(
        conditions = conditions,
        serviceRunning = serviceRunning,
        notificationsOn = notificationsOn,
        onSharingChange = container.settings::setSharingEnabled,
        onTurnOnBluetooth = onTurnOnBluetooth,
        onOpenLocationSettings = { SystemScreens.location(context) },
        onOpenNotificationSettings = { SystemScreens.appNotifications(context) },
    )
}

@Composable
fun HomeScreen(
    conditions: DeviceConditions,
    serviceRunning: Boolean,
    notificationsOn: Boolean,
    onSharingChange: (Boolean) -> Unit,
    onTurnOnBluetooth: () -> Unit,
    onOpenLocationSettings: () -> Unit,
    onOpenNotificationSettings: () -> Unit,
) {
    val colors = TapShareTheme.colors
    val type = TapShareTheme.type
    val readiness = ReadinessPolicy.evaluate(conditions)

    Column(
        Modifier
            .fillMaxSize()
            .background(colors.groupedBackground)
            .verticalScroll(rememberScrollState())
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(bottom = 32.dp),
    ) {
        Text(
            stringResource(R.string.app_name),
            style = type.largeTitle,
            color = colors.label,
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 12.dp),
        )

        StatusCard(
            readiness = readiness,
            bluetooth = conditions.bluetooth,
            onTurnOnBluetooth = onTurnOnBluetooth,
            onOpenLocationSettings = onOpenLocationSettings,
        )

        Spacer(Modifier.height(28.dp))
        GroupedSection(
            header = stringResource(R.string.section_sharing),
            footer = stringResource(R.string.sharing_footer),
        ) {
            GroupedRow(
                title = stringResource(R.string.sharing_toggle),
                icon = painterResource(R.drawable.ic_nearby),
                iconBackground = colors.accent,
            ) {
                IosSwitch(checked = conditions.sharingEnabled, onCheckedChange = onSharingChange)
            }
        }

        Spacer(Modifier.height(28.dp))
        GroupedSection(header = stringResource(R.string.section_status)) {
            GroupedRow(
                title = stringResource(R.string.status_bluetooth),
                icon = painterResource(R.drawable.ic_bluetooth),
                iconBackground = colors.accent,
                showDivider = true,
                onClick = if (conditions.bluetooth == BluetoothStatus.OFF) onTurnOnBluetooth else null,
            ) {
                val (label, good) = when (conditions.bluetooth) {
                    BluetoothStatus.ON -> stringResource(R.string.status_on) to true
                    BluetoothStatus.OFF -> stringResource(R.string.status_off) to false
                    BluetoothStatus.TURNING_ON -> stringResource(R.string.status_turning_on) to true
                    BluetoothStatus.TURNING_OFF -> stringResource(R.string.status_turning_off) to false
                    BluetoothStatus.UNSUPPORTED -> stringResource(R.string.status_not_available) to false
                }
                TrailingValue(label, showChevron = conditions.bluetooth == BluetoothStatus.OFF, color = if (good) colors.secondaryLabel else colors.red)
            }
            if (conditions.locationServicesNeeded) {
                GroupedRow(
                    title = stringResource(R.string.status_location),
                    icon = painterResource(R.drawable.ic_location),
                    iconBackground = colors.accent,
                    showDivider = true,
                    onClick = onOpenLocationSettings,
                ) {
                    TrailingValue(
                        stringResource(if (conditions.locationServicesOn) R.string.status_on else R.string.status_off),
                        showChevron = true,
                        color = if (conditions.locationServicesOn) colors.secondaryLabel else colors.red,
                    )
                }
            }
            GroupedRow(
                title = stringResource(R.string.status_notifications),
                icon = painterResource(R.drawable.ic_bell),
                iconBackground = colors.red,
                showDivider = true,
                onClick = onOpenNotificationSettings,
            ) {
                TrailingValue(stringResource(if (notificationsOn) R.string.status_on else R.string.status_off), showChevron = true)
            }
            GroupedRow(
                title = stringResource(R.string.status_service),
                icon = painterResource(R.drawable.ic_power),
                iconBackground = colors.green,
            ) {
                TrailingValue(stringResource(if (serviceRunning) R.string.status_running else R.string.status_stopped))
            }
        }

        Spacer(Modifier.height(28.dp))
        GroupedSection(header = stringResource(R.string.section_about)) {
            GroupedRow(
                title = stringResource(R.string.about_version),
                icon = painterResource(R.drawable.ic_phone),
                iconBackground = colors.gray,
            ) {
                TrailingValue(BuildConfig.VERSION_NAME)
            }
        }
    }
}

/** Big card at the top: illustration + what's going on + the one action that fixes it. */
@Composable
private fun StatusCard(
    readiness: Readiness,
    bluetooth: BluetoothStatus,
    onTurnOnBluetooth: () -> Unit,
    onOpenLocationSettings: () -> Unit,
) {
    val colors = TapShareTheme.colors
    val type = TapShareTheme.type
    val text = readiness.text(bluetooth)

    Column(
        Modifier
            .padding(horizontal = 16.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(colors.cell)
            .padding(horizontal = 20.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        HeroIllustration(
            active = readiness == Readiness.Ready,
            modifier = Modifier
                .widthIn(max = HeroMaxWidth)
                .fillMaxWidth()
                .aspectRatio(HeroAspectRatio),
        )
        Spacer(Modifier.height(20.dp))
        AnimatedContent(
            targetState = text,
            transitionSpec = { fadeIn(spring(stiffness = 300f)) togetherWith fadeOut(spring(stiffness = 300f)) },
            label = "statusText",
        ) { t ->
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(stringResource(t.title), style = type.title2, color = colors.label, textAlign = TextAlign.Center)
                Spacer(Modifier.height(6.dp))
                Text(stringResource(t.body), style = type.subheadline, color = colors.secondaryLabel, textAlign = TextAlign.Center)
            }
        }

        val action: Pair<String, () -> Unit>? = when ((readiness as? Readiness.Blocked)?.problem) {
            Problem.BLUETOOTH_OFF ->
                if (bluetooth == BluetoothStatus.OFF) stringResource(R.string.bluetooth_turn_on) to onTurnOnBluetooth else null
            Problem.LOCATION_SERVICES_OFF -> stringResource(R.string.location_turn_on) to onOpenLocationSettings
            else -> null
        }
        // Keep showing the last button while it animates out (otherwise it would vanish instantly).
        val lastAction = remember { mutableStateOf(action) }
        if (action != null) lastAction.value = action
        AnimatedVisibility(
            visible = action != null,
            enter = fadeIn(spring(stiffness = 300f)) + expandVertically(spring(dampingRatio = 0.85f, stiffness = 300f)),
            exit = fadeOut(spring(stiffness = 300f)) + shrinkVertically(spring(dampingRatio = 1f, stiffness = 300f)),
        ) {
            Column {
                Spacer(Modifier.height(20.dp))
                lastAction.value?.let { (label, onClick) -> PrimaryButton(label, onClick = onClick) }
            }
        }
    }
}
