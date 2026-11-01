package com.aniket.tapshare.ui.send

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aniket.tapshare.R
import com.aniket.tapshare.appContainer
import com.aniket.tapshare.share.OutgoingItem
import com.aniket.tapshare.ui.components.PrimaryButton
import com.aniket.tapshare.ui.components.rememberThumbnail
import com.aniket.tapshare.ui.home.HeroAspectRatio
import com.aniket.tapshare.ui.home.HeroIllustration
import com.aniket.tapshare.ui.home.HeroMaxWidth
import com.aniket.tapshare.ui.theme.TapShareTheme

/** Items are queued (share sheet / pickers): "hold your phone near the other phone". */
@Composable
fun SendScreen(items: List<OutgoingItem>) {
    val container = LocalContext.current.appContainer
    val discovery by container.discovery.state.collectAsStateWithLifecycle()
    val colors = TapShareTheme.colors
    val type = TapShareTheme.type
    BackHandler { container.outbox.clear() }

    val allPhotos = items.all { it.mime.startsWith("image/") }
    val title = when {
        items.size == 1 -> stringResource(R.string.send_title_one)
        allPhotos -> stringResource(R.string.send_title_photos, items.size)
        else -> stringResource(R.string.send_title_items, items.size)
    }
    val hint = when {
        discovery.touchingPeer != null -> stringResource(R.string.home_peer_touching)
        discovery.peers.isNotEmpty() -> stringResource(R.string.home_peer_nearby, discovery.peers.size)
        else -> stringResource(R.string.send_body)
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(colors.groupedBackground)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(24.dp))
        ThumbStrip(items)
        Spacer(Modifier.weight(1f))
        HeroIllustration(
            active = discovery.running,
            modifier = Modifier.widthIn(max = HeroMaxWidth).fillMaxWidth().aspectRatio(HeroAspectRatio),
        )
        Spacer(Modifier.height(24.dp))
        Text(title, style = type.title2, color = colors.label, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(hint, style = type.body, color = colors.secondaryLabel, textAlign = TextAlign.Center)
        Spacer(Modifier.weight(1.2f))
        PrimaryButton(stringResource(R.string.send_cancel), onClick = container.outbox::clear)
    }
}

@Composable
private fun ThumbStrip(items: List<OutgoingItem>) {
    val shown = items.take(4)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        shown.forEachIndexed { i, item ->
            val extra = if (i == shown.lastIndex) items.size - shown.size else 0
            Thumb(item, extra)
        }
    }
}

@Composable
private fun Thumb(item: OutgoingItem, extra: Int) {
    val colors = TapShareTheme.colors
    val image = rememberThumbnail(item.uri)
    Box(
        Modifier
            .size(76.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(colors.cell),
        contentAlignment = Alignment.Center,
    ) {
        if (image != null) {
            Image(image, contentDescription = item.name, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        } else {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(6.dp)) {
                Icon(painterResource(R.drawable.ic_file), contentDescription = null, tint = colors.accent, modifier = Modifier.size(26.dp))
                Text(item.name, style = TapShareTheme.type.caption, color = colors.secondaryLabel, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        if (extra > 0) {
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f)), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.send_more, extra), style = TapShareTheme.type.headline, color = Color.White)
            }
        }
    }
}
