package com.aniket.tapshare.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aniket.tapshare.appContainer
import com.aniket.tapshare.ui.home.HomeRoute
import com.aniket.tapshare.ui.permissions.PermissionsRoute

/** Shows the permission screen until every required permission is granted, then Home. */
@Composable
fun TapShareRoot() {
    val summary by LocalContext.current.appContainer.permissions.summary.collectAsStateWithLifecycle()
    AnimatedContent(
        targetState = summary.allRequiredGranted,
        transitionSpec = {
            (fadeIn(spring(stiffness = 300f)) + scaleIn(spring(dampingRatio = 0.9f, stiffness = 300f), initialScale = 0.96f)) togetherWith
                (fadeOut(spring(stiffness = 300f)) + scaleOut(spring(stiffness = 300f), targetScale = 1.04f))
        },
        label = "root",
    ) { ready ->
        if (ready) HomeRoute() else PermissionsRoute()
    }
}
