package com.aniket.tapshare.ui.session

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.aniket.tapshare.R
import com.aniket.tapshare.session.LinkStatus
import com.aniket.tapshare.ui.components.Avatar
import com.aniket.tapshare.ui.components.ProgressRing
import com.aniket.tapshare.ui.components.rememberJpeg
import com.aniket.tapshare.ui.theme.TapShareTheme

private val Green = Color(0xFF34C759)
private val Red = Color(0xFFFF453A)
private val Gray = Color(0xFF8E8E93)

/**
 * Black "island" pill at the top: Connecting… → Connected · Rahul → Disconnected / Connection lost.
 * It morphs (springy size change) between states, so you can always see if the phones are linked.
 */
@Composable
fun ConnectionPill(link: LinkStatus) {
    // Keep the last visible status while the pill animates away.
    val last = remember { mutableStateOf(link) }
    if (link != LinkStatus.None) last.value = link
    Box(
        Modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(top = 6.dp),
        contentAlignment = Alignment.TopCenter,
    ) {
        AnimatedVisibility(
            visible = link != LinkStatus.None,
            enter = fadeIn(spring(stiffness = 400f)) + scaleIn(spring(dampingRatio = 0.6f, stiffness = 400f), initialScale = 0.5f),
            exit = fadeOut(tween(250)) + scaleOut(tween(250), targetScale = 0.6f),
        ) {
            Box(
                Modifier
                    .clip(CircleShape)
                    .background(Color.Black)
                    .padding(horizontal = 14.dp)
                    .height(38.dp),
                contentAlignment = Alignment.Center,
            ) {
                AnimatedContent(
                    targetState = last.value,
                    contentKey = { it::class },
                    transitionSpec = {
                        (fadeIn(tween(180, delayMillis = 60)) togetherWith fadeOut(tween(120)))
                            .using(SizeTransform(clip = false) { _: IntSize, _: IntSize -> spring(dampingRatio = 0.7f, stiffness = 380f) })
                    },
                    label = "pill",
                ) { status -> PillContent(status) }
            }
        }
    }
}

@Composable
private fun PillContent(status: LinkStatus) {
    val type = TapShareTheme.type
    Row(verticalAlignment = Alignment.CenterVertically) {
        when (status) {
            LinkStatus.None -> Unit
            LinkStatus.Connecting -> {
                ProgressRing(null, Color.White, Modifier.size(16.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.pill_connecting), style = type.subheadline.copy(fontWeight = FontWeight.SemiBold), color = Color.White)
            }
            is LinkStatus.Connected -> {
                val peer = status.peer
                if (peer != null) {
                    Avatar(rememberJpeg(peer.avatarJpeg), initialsOf(peer.name), size = 24.dp)
                    Spacer(Modifier.width(8.dp))
                }
                Text(
                    if (peer != null) stringResource(R.string.pill_connected_to, peer.name) else stringResource(R.string.pill_connected),
                    style = type.subheadline.copy(fontWeight = FontWeight.SemiBold),
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.width(10.dp))
                PulsingDot(Green)
            }
            is LinkStatus.Disconnected -> {
                StaticDot(if (status.lost) Red else Gray)
                Spacer(Modifier.width(8.dp))
                Text(
                    stringResource(if (status.lost) R.string.pill_lost else R.string.pill_disconnected),
                    style = type.subheadline.copy(fontWeight = FontWeight.SemiBold),
                    color = if (status.lost) Red else Color.White.copy(alpha = 0.85f),
                )
            }
        }
    }
}

@Composable
private fun PulsingDot(color: Color) {
    val t by rememberInfiniteTransition(label = "dot").animateFloat(
        0.55f,
        1f,
        infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "dotAlpha",
    )
    Box(
        Modifier
            .size(9.dp)
            .graphicsLayer {
                alpha = t
                scaleX = 0.85f + 0.15f * t
                scaleY = 0.85f + 0.15f * t
            }
            .clip(CircleShape)
            .background(color),
    )
}

@Composable
private fun StaticDot(color: Color) {
    Box(Modifier.size(9.dp).clip(CircleShape).background(color))
}
