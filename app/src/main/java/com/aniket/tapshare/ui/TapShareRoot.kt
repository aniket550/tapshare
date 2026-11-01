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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aniket.tapshare.appContainer
import com.aniket.tapshare.ui.home.HomeRoute
import com.aniket.tapshare.ui.nearby.NearbyRoute
import com.aniket.tapshare.ui.permissions.PermissionsRoute

private enum class Screen { PERMISSIONS, HOME, NEARBY }

/** Permissions until everything required is granted, then Home ⇄ Nearby Phones. */
@Composable
fun TapShareRoot() {
    val summary by LocalContext.current.appContainer.permissions.summary.collectAsStateWithLifecycle()
    var showNearby by rememberSaveable { mutableStateOf(false) }
    val screen = when {
        !summary.allRequiredGranted -> Screen.PERMISSIONS
        showNearby -> Screen.NEARBY
        else -> Screen.HOME
    }

    AnimatedContent(
        targetState = screen,
        transitionSpec = {
            val push = spring<IntOffset>(dampingRatio = 1f, stiffness = 380f)
            when {
                // iOS push: new screen slides in from the right, old one moves a bit left.
                initialState == Screen.HOME && targetState == Screen.NEARBY ->
                    slideInHorizontally(push) { it } togetherWith slideOutHorizontally(push) { -it / 3 }
                // iOS pop: the reverse.
                initialState == Screen.NEARBY && targetState == Screen.HOME ->
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
            Screen.HOME -> HomeRoute(onOpenNearby = { showNearby = true })
            Screen.NEARBY -> NearbyRoute(onBack = { showNearby = false })
        }
    }
}
