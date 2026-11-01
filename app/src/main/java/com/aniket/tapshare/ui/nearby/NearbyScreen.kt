package com.aniket.tapshare.ui.nearby

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aniket.tapshare.R
import com.aniket.tapshare.appContainer
import com.aniket.tapshare.discovery.DiscoveryProtocol
import com.aniket.tapshare.discovery.DiscoveryState
import com.aniket.tapshare.discovery.PeerSnapshot
import com.aniket.tapshare.discovery.RadioFailure
import com.aniket.tapshare.discovery.RadioStatus
import com.aniket.tapshare.discovery.TxPower
import com.aniket.tapshare.service.BluetoothStatus
import com.aniket.tapshare.service.Readiness
import com.aniket.tapshare.service.ReadinessPolicy
import com.aniket.tapshare.service.text
import com.aniket.tapshare.ui.components.GroupedRow
import com.aniket.tapshare.ui.components.GroupedSection
import com.aniket.tapshare.ui.components.SegmentedControl
import com.aniket.tapshare.ui.components.TrailingValue
import com.aniket.tapshare.ui.theme.TapShareTheme
import kotlin.math.roundToInt

/** Wires the screen to the live discovery engine. */
@Composable
fun NearbyRoute(onBack: () -> Unit) {
    val container = LocalContext.current.appContainer
    val state by container.discovery.state.collectAsStateWithLifecycle()
    val conditions by container.conditions.conditions.collectAsStateWithLifecycle()
    BackHandler(onBack = onBack)
    NearbyScreen(
        state = state,
        readiness = ReadinessPolicy.evaluate(conditions),
        bluetooth = conditions.bluetooth,
        onBack = onBack,
        onTxPower = container.discovery::setTxPower,
        onReset = container.discovery::resetStats,
    )
}

@Composable
fun NearbyScreen(
    state: DiscoveryState,
    readiness: Readiness,
    bluetooth: BluetoothStatus,
    onBack: () -> Unit,
    onTxPower: (TxPower) -> Unit,
    onReset: () -> Unit,
) {
    val colors = TapShareTheme.colors
    val type = TapShareTheme.type

    Column(
        Modifier
            .fillMaxSize()
            .background(colors.groupedBackground)
            .windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        BackButton(onBack)
        Column(Modifier.verticalScroll(rememberScrollState()).padding(bottom = 32.dp)) {
            Text(
                stringResource(R.string.nearby_title),
                style = type.largeTitle,
                color = colors.label,
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 16.dp),
            )

            // ---- This phone ----
            val advertNotSupported = (state.advertising as? RadioStatus.Failed)?.reason == RadioFailure.NOT_SUPPORTED
            GroupedSection(
                header = stringResource(R.string.nearby_section_this_phone),
                footer = stringResource(
                    if (advertNotSupported) R.string.nearby_not_supported_footer else R.string.nearby_my_id_footer,
                ),
            ) {
                GroupedRow(title = stringResource(R.string.nearby_my_id), showDivider = true) {
                    Text(
                        DiscoveryProtocol.shortLabel(state.myId),
                        style = type.body.copy(fontFamily = FontFamily.Monospace),
                        color = colors.secondaryLabel,
                    )
                }
                GroupedRow(title = stringResource(R.string.nearby_sending), showDivider = true) {
                    RadioValue(state.advertising, state.fast)
                }
                GroupedRow(title = stringResource(R.string.nearby_listening)) {
                    RadioValue(state.scanning, state.fast)
                }
            }

            // ---- TX power ----
            Spacer(Modifier.height(28.dp))
            GroupedSection(
                header = stringResource(R.string.nearby_section_power),
                footer = stringResource(R.string.nearby_power_footer),
            ) {
                val labels = listOf(
                    stringResource(R.string.tx_ultra_low),
                    stringResource(R.string.tx_low),
                    stringResource(R.string.tx_medium),
                    stringResource(R.string.tx_high),
                )
                Box(Modifier.padding(12.dp)) {
                    SegmentedControl(
                        options = labels,
                        selectedIndex = state.txPower.ordinal,
                        onSelect = { onTxPower(TxPower.entries[it]) },
                    )
                }
            }

            // ---- Found phones ----
            Spacer(Modifier.height(28.dp))
            GroupedSection(
                header = stringResource(R.string.nearby_section_found),
                footer = stringResource(R.string.nearby_found_footer),
                modifier = Modifier.animateContentSize(spring(dampingRatio = 0.9f, stiffness = 400f)),
            ) {
                when {
                    !state.running -> {
                        val why = readiness.text(bluetooth)
                        GroupedRow(
                            title = stringResource(R.string.nearby_not_running_title),
                            subtitle = stringResource(why.title) + " — " + stringResource(why.body),
                        )
                    }
                    state.peers.isEmpty() -> GroupedRow(
                        title = stringResource(R.string.nearby_searching_title),
                        subtitle = stringResource(R.string.nearby_searching_body),
                    )
                    else -> state.peers.forEachIndexed { i, peer ->
                        PeerRow(peer, showDivider = i < state.peers.lastIndex)
                    }
                }
            }

            Spacer(Modifier.height(28.dp))
            GroupedSection {
                GroupedRow(title = stringResource(R.string.nearby_reset), titleColor = colors.accent, onClick = onReset)
            }
        }
    }
}

@Composable
private fun PeerRow(peer: PeerSnapshot, showDivider: Boolean) {
    val colors = TapShareTheme.colors
    val type = TapShareTheme.type
    val rssi = peer.smoothedRssi.roundToInt()
    val strengthColor = when {
        rssi >= -50 -> colors.green
        rssi >= -70 -> colors.orange
        else -> colors.gray
    }
    GroupedRow(
        title = stringResource(R.string.nearby_peer_title, DiscoveryProtocol.shortLabel(peer.id)),
        subtitle = stringResource(
            R.string.nearby_peer_stats,
            peer.avgRssi.roundToInt(),
            peer.minRssi,
            peer.maxRssi,
            peer.samplesPerSecond,
        ) + "\n" + stringResource(R.string.nearby_peer_seen, peer.msSinceLastSeen / 1000.0),
        showDivider = showDivider,
    ) {
        Column(horizontalAlignment = Alignment.End) {
            Text(
                stringResource(R.string.nearby_dbm, rssi),
                style = type.headline.copy(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.SemiBold),
                color = strengthColor,
            )
            Spacer(Modifier.height(6.dp))
            SignalBar(rssi = rssi, color = strengthColor)
        }
    }
}

/** Bar that fills from −100 dBm (empty) to −30 dBm (full), springing as the number changes. */
@Composable
private fun SignalBar(rssi: Int, color: Color) {
    val fraction by animateFloatAsState(
        targetValue = ((rssi + 100) / 70f).coerceIn(0.02f, 1f),
        animationSpec = spring(dampingRatio = 0.85f, stiffness = 300f),
        label = "signalBar",
    )
    Box(
        Modifier
            .width(72.dp)
            .height(6.dp)
            .clip(CircleShape)
            .background(TapShareTheme.colors.switchTrackOff),
    ) {
        Box(
            Modifier
                .fillMaxWidth(fraction)
                .height(6.dp)
                .clip(CircleShape)
                .background(color),
        )
    }
}

@Composable
private fun RadioValue(status: RadioStatus, fast: Boolean) {
    val colors = TapShareTheme.colors
    val (label, bad) = when (status) {
        RadioStatus.Off -> stringResource(R.string.radio_off) to false
        RadioStatus.Starting -> stringResource(R.string.radio_starting) to false
        RadioStatus.On -> stringResource(if (fast) R.string.nearby_mode_fast else R.string.nearby_mode_low_power) to false
        is RadioStatus.Failed -> stringResource(
            when (status.reason) {
                RadioFailure.NOT_SUPPORTED -> R.string.radio_not_supported
                RadioFailure.BLUETOOTH_OFF -> R.string.radio_bluetooth_off
                RadioFailure.NO_PERMISSION -> R.string.radio_no_permission
                RadioFailure.TOO_MANY_ADVERTISERS -> R.string.radio_busy
                RadioFailure.TOO_FREQUENT -> R.string.radio_too_frequent
                RadioFailure.OUT_OF_RESOURCES -> R.string.radio_busy
                RadioFailure.INTERNAL -> R.string.radio_error
            },
        ) to true
    }
    TrailingValue(label, color = if (bad) colors.red else colors.secondaryLabel)
}

/** iOS-style "‹ TapShare" back button at the top left. */
@Composable
private fun BackButton(onBack: () -> Unit) {
    val colors = TapShareTheme.colors
    Row(
        Modifier
            .padding(start = 8.dp, top = 4.dp)
            .clip(RoundedCornerShape(8.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                role = Role.Button,
                onClick = onBack,
            )
            .padding(horizontal = 8.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painterResource(R.drawable.ic_chevron_right),
            contentDescription = stringResource(R.string.back),
            tint = colors.accent,
            modifier = Modifier.size(20.dp).rotate(180f),
        )
        Spacer(Modifier.width(2.dp))
        Text(stringResource(R.string.app_name), style = TapShareTheme.type.body, color = colors.accent)
    }
}
