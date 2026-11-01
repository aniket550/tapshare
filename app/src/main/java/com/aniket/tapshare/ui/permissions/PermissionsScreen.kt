package com.aniket.tapshare.ui.permissions

import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aniket.tapshare.R
import com.aniket.tapshare.appContainer
import com.aniket.tapshare.ui.components.CapsuleButton
import com.aniket.tapshare.ui.components.GroupedRow
import com.aniket.tapshare.ui.components.GroupedSection
import com.aniket.tapshare.ui.components.PrimaryButton
import com.aniket.tapshare.ui.theme.TapShareTheme
import com.aniket.tapshare.util.AppPermission
import com.aniket.tapshare.util.GrantState
import com.aniket.tapshare.util.GroupStatus
import com.aniket.tapshare.util.PermissionGroup
import com.aniket.tapshare.util.PermissionSummary
import com.aniket.tapshare.util.SystemScreens

/** Wires the screen to the real permission system. */
@Composable
fun PermissionsRoute() {
    val context = LocalContext.current
    val activity = LocalActivity.current
    val repo = context.appContainer.permissions
    val summary by repo.summary.collectAsStateWithLifecycle()

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        repo.refresh(activity)
    }
    val request: (List<AppPermission>) -> Unit = { perms ->
        if (perms.isNotEmpty()) {
            repo.markRequested(perms)
            launcher.launch(perms.map { it.manifestName }.toTypedArray())
        }
    }

    PermissionsScreen(
        summary = summary,
        sdk = repo.sdk,
        onRequest = request,
        onOpenSettings = { SystemScreens.appDetails(context) },
    )
}

@Composable
fun PermissionsScreen(
    summary: PermissionSummary,
    sdk: Int,
    onRequest: (List<AppPermission>) -> Unit,
    onOpenSettings: () -> Unit,
) {
    val colors = TapShareTheme.colors
    val type = TapShareTheme.type
    val required = summary.onboardingGroups.filter { it.group.required }
    val optional = summary.onboardingGroups.filterNot { it.group.required }

    Box(Modifier.fillMaxSize().background(colors.groupedBackground)) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(bottom = 120.dp), // room for the pinned button
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(40.dp))
            AppMark()
            Spacer(Modifier.height(20.dp))
            Text(
                stringResource(R.string.permissions_title),
                style = type.largeTitle,
                color = colors.label,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 32.dp),
            )
            Spacer(Modifier.height(10.dp))
            Text(
                stringResource(R.string.permissions_subtitle),
                style = type.body,
                color = colors.secondaryLabel,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 32.dp),
            )
            Spacer(Modifier.height(32.dp))

            GroupedSection(
                header = stringResource(R.string.permissions_section_required),
                footer = if (summary.anyRequiredBlocked) {
                    stringResource(R.string.permissions_blocked_hint)
                } else {
                    stringResource(R.string.permissions_privacy_footer)
                },
            ) {
                required.forEachIndexed { i, status ->
                    PermissionRow(status, sdk, showDivider = i < required.lastIndex, onRequest, onOpenSettings)
                }
            }

            if (optional.isNotEmpty()) {
                Spacer(Modifier.height(28.dp))
                GroupedSection(header = stringResource(R.string.permissions_section_optional)) {
                    optional.forEachIndexed { i, status ->
                        PermissionRow(status, sdk, showDivider = i < optional.lastIndex, onRequest, onOpenSettings)
                    }
                }
            }
        }

        BottomBar(summary, onRequest, onOpenSettings, Modifier.align(Alignment.BottomCenter))
    }
}

@Composable
private fun PermissionRow(
    status: GroupStatus,
    sdk: Int,
    showDivider: Boolean,
    onRequest: (List<AppPermission>) -> Unit,
    onOpenSettings: () -> Unit,
) {
    val colors = TapShareTheme.colors
    val (title, body, icon, tint) = when (status.group) {
        PermissionGroup.NEARBY_DEVICES -> RowContent(
            stringResource(R.string.permission_nearby_title),
            stringResource(R.string.permission_nearby_body),
            R.drawable.ic_nearby,
            colors.accent,
        )
        PermissionGroup.LOCATION -> RowContent(
            stringResource(R.string.permission_location_title),
            stringResource(
                if (sdk <= 30) R.string.permission_location_body_android10 else R.string.permission_location_body_android12,
            ),
            R.drawable.ic_location,
            colors.accent,
        )
        // Never shown here (asked from Home → "When TapShare is closed"); kept for a complete `when`.
        PermissionGroup.BACKGROUND_LOCATION -> RowContent(
            stringResource(R.string.background_location_title),
            stringResource(R.string.background_location_body),
            R.drawable.ic_location,
            colors.accent,
        )
        PermissionGroup.NOTIFICATIONS -> RowContent(
            stringResource(R.string.permission_notifications_title),
            stringResource(R.string.permission_notifications_body),
            R.drawable.ic_bell,
            colors.red,
        )
    }
    GroupedRow(
        title = title,
        subtitle = body,
        icon = painterResource(icon),
        iconBackground = tint,
        showDivider = showDivider,
    ) {
        // Crossfade + small pop when a permission becomes granted.
        AnimatedContent(
            targetState = status.state,
            transitionSpec = {
                (fadeIn(spring(stiffness = 400f)) + scaleIn(spring(dampingRatio = 0.6f, stiffness = 400f), initialScale = 0.6f)) togetherWith
                    fadeOut(spring(stiffness = 400f))
            },
            label = "grantState",
        ) { state ->
            when (state) {
                GrantState.GRANTED -> Icon(
                    painterResource(R.drawable.ic_check),
                    contentDescription = stringResource(R.string.permission_granted),
                    tint = colors.green,
                    modifier = Modifier.size(22.dp),
                )
                GrantState.CAN_REQUEST -> CapsuleButton(stringResource(R.string.permission_action_allow), onClick = { onRequest(status.missing) })
                GrantState.BLOCKED -> CapsuleButton(stringResource(R.string.permission_action_settings), onClick = onOpenSettings)
            }
        }
    }
}

private data class RowContent(val title: String, val body: String, val icon: Int, val tint: Color)

@Composable
private fun BottomBar(
    summary: PermissionSummary,
    onRequest: (List<AppPermission>) -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = TapShareTheme.colors
    val requestable = summary.requestable
    Column(
        modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    0f to colors.groupedBackground.copy(alpha = 0f),
                    0.25f to colors.groupedBackground,
                ),
            )
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(start = 20.dp, end = 20.dp, top = 28.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.Bottom,
    ) {
        if (requestable.isNotEmpty()) {
            PrimaryButton(stringResource(R.string.permissions_continue), onClick = { onRequest(requestable) })
        } else {
            // Only blocked items are left: the system dialog won't show, Settings is the only way.
            PrimaryButton(stringResource(R.string.permissions_open_settings), onClick = onOpenSettings)
        }
    }
}

/** The app's mark: same gradient + waves as the launcher icon. */
@Composable
private fun AppMark() {
    Box(
        Modifier
            .size(88.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(Brush.linearGradient(listOf(Color(0xFF2F8BFF), Color(0xFF5E3BEE)))),
        contentAlignment = Alignment.Center,
    ) {
        Icon(painterResource(R.drawable.ic_nearby), contentDescription = null, tint = Color.White, modifier = Modifier.size(48.dp))
    }
}
