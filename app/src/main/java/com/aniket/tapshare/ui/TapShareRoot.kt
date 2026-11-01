package com.aniket.tapshare.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aniket.tapshare.appContainer
import com.aniket.tapshare.session.SessionState
import com.aniket.tapshare.ui.animation.TapRippleHost
import com.aniket.tapshare.ui.home.HomeRoute
import com.aniket.tapshare.ui.mycard.MyCardScreen
import com.aniket.tapshare.ui.nearby.NearbyRoute
import com.aniket.tapshare.ui.permissions.PermissionsRoute
import com.aniket.tapshare.ui.send.SendScreen
import com.aniket.tapshare.ui.session.SessionOverlay

private enum class Screen(val depth: Int) { PERMISSIONS(0), HOME(0), SEND(0), NEARBY(1), MY_CARD(1) }

/**
 * Permissions first; then Home ⇄ (My Card | Nearby Phones), or the Send screen while photos/files
 * are queued. The tap glow and the session screens are drawn on top of all of it.
 */
@Composable
fun TapShareRoot() {
    val container = LocalContext.current.appContainer
    val summary by container.permissions.summary.collectAsStateWithLifecycle()
    val outbox by container.outbox.items.collectAsStateWithLifecycle()
    val session by container.session.state.collectAsStateWithLifecycle()
    var detail by rememberSaveable { mutableStateOf<String?>(null) }

    val screen = when {
        !summary.allRequiredGranted -> Screen.PERMISSIONS
        detail == Screen.NEARBY.name -> Screen.NEARBY
        detail == Screen.MY_CARD.name -> Screen.MY_CARD
        outbox.isNotEmpty() -> Screen.SEND
        else -> Screen.HOME
    }
    val tapTrigger = (session as? SessionState.Touching)?.startedAtMs ?: 0L

    TapRippleHost(trigger = tapTrigger) {
        AnimatedContent(
            targetState = screen,
            transitionSpec = {
                val push = spring<IntOffset>(dampingRatio = 1f, stiffness = 380f)
                when {
                    // iOS push: new screen slides in from the right, old one moves a bit left.
                    targetState.depth > initialState.depth ->
                        slideInHorizontally(push) { it } togetherWith slideOutHorizontally(push) { -it / 3 }
                    // iOS pop: the reverse.
                    targetState.depth < initialState.depth ->
                        slideInHorizontally(push) { -it / 3 } togetherWith slideOutHorizontally(push) { it }
                    else ->
                        (fadeIn(spring(stiffness = 300f)) + scaleIn(spring(dampingRatio = 0.9f, stiffness = 300f), initialScale = 0.96f)) togetherWith
                            (fadeOut(spring(stiffness = 300f)) + scaleOut(spring(stiffness = 300f), targetScale = 1.04f))
                }
            },
            label = "root",
        ) { target ->
            when (target) {
                Screen.PERMISSIONS -> PermissionsRoute()
                Screen.HOME -> HomeRoute(
                    onOpenNearby = { detail = Screen.NEARBY.name },
                    onOpenMyCard = { detail = Screen.MY_CARD.name },
                )
                Screen.SEND -> SendScreen(outbox)
                Screen.NEARBY -> NearbyRoute(onBack = { detail = null })
                Screen.MY_CARD -> MyCardScreen(onBack = { detail = null })
            }
        }
        Box(Modifier.fillMaxSize()) { SessionOverlay() }
    }
}
