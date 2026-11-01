package com.aniket.tapshare.ui.session

import android.content.ActivityNotFoundException
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.net.wifi.WifiManager
import android.provider.ContactsContract
import android.text.format.Formatter
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aniket.tapshare.R
import com.aniket.tapshare.appContainer
import com.aniket.tapshare.data.MyCard
import com.aniket.tapshare.handshake.ContactCard
import com.aniket.tapshare.handshake.ItemInfo
import com.aniket.tapshare.handshake.ItemKind
import com.aniket.tapshare.handshake.describeItems
import com.aniket.tapshare.session.ContactChoice
import com.aniket.tapshare.session.FailReason
import com.aniket.tapshare.session.Outcome
import com.aniket.tapshare.session.PeerInfo
import com.aniket.tapshare.session.Progress
import com.aniket.tapshare.session.SendPhase
import com.aniket.tapshare.session.SessionState
import com.aniket.tapshare.ui.components.Avatar
import com.aniket.tapshare.ui.components.BreathingGlow
import com.aniket.tapshare.ui.components.ProgressRing
import com.aniket.tapshare.ui.components.rememberJpeg
import com.aniket.tapshare.ui.theme.TapShareTheme
import com.aniket.tapshare.util.TapLog

private val PosterGradient = Brush.verticalGradient(listOf(Color(0xFF2F8BFF), Color(0xFF5E3BEE), Color(0xFF2A1B6E)))

/**
 * Full-screen session UI on top of everything. Nothing is shown while Idle or during the glow
 * (the glow itself is drawn by TapRippleHost over the current screen).
 */
@Composable
fun SessionOverlay() {
    val container = LocalContext.current.appContainer
    val state by container.session.state.collectAsStateWithLifecycle()
    val card by container.myCard.card.collectAsStateWithLifecycle()
    val session = container.session

    AnimatedContent(
        targetState = state,
        contentKey = { it::class },
        transitionSpec = {
            (fadeIn(spring(stiffness = 260f)) + scaleIn(spring(dampingRatio = 0.82f, stiffness = 260f), initialScale = 0.92f)) togetherWith
                (fadeOut(spring(stiffness = 400f)) + scaleOut(spring(stiffness = 400f), targetScale = 1.04f))
        },
        label = "session",
    ) { s ->
        if (s == SessionState.Idle || s is SessionState.Touching) return@AnimatedContent
        // Swallow touches so nothing behind the session screen can be tapped by accident.
        Box(Modifier.fillMaxSize().pointerInput(Unit) { detectTapGestures { } }) {
            SessionScreen(s, card, session)
        }
    }
}

@Composable
private fun SessionScreen(s: SessionState, card: MyCard, session: com.aniket.tapshare.session.SessionManager) {
        when (s) {
            SessionState.Idle, is SessionState.Touching -> Unit
            is SessionState.ContactExchange -> ContactPoster(
                myCard = card,
                state = s,
                onShare = session::shareMyCard,
                onReceiveOnly = session::receiveOnly,
                onClose = session::cancel,
            )
            is SessionState.ContactReceived -> ReceivedCard(s.card, s.iSharedMine, onDone = session::dismiss)
            is SessionState.Sending -> SendingScreen(s, onCancel = session::cancel)
            is SessionState.Incoming -> IncomingScreen(
                s,
                onAccept = session::acceptIncoming,
                onDecline = session::declineIncoming,
                onCancel = session::cancel,
            )
            is SessionState.Finished -> FinishedScreen(s, onDone = session::dismiss)
            is SessionState.Failed -> FailedScreen(s, onDone = session::dismiss)
        }
}

// =========================================================================== contact poster

@Composable
private fun ContactPoster(
    myCard: MyCard,
    state: SessionState.ContactExchange,
    onShare: () -> Unit,
    onReceiveOnly: () -> Unit,
    onClose: () -> Unit,
) {
    BackHandler(onBack = onClose)
    val container = LocalContext.current.appContainer
    val photo by produceState<ImageBitmap?>(null, myCard.photoVersion) {
        value = container.myCard.photoBitmap()?.asImageBitmap()
    }
    val peerName = state.peer?.name
    PosterLayout(
        top = {
            PeerChip(state.peer, connecting = !state.connected || state.peer == null)
        },
        photo = photo,
        initials = myCard.initials,
        name = myCard.name.ifBlank { stringResource(R.string.home_my_card_setup) },
        details = listOfNotNull(
            myCard.phone.takeIf { myCard.sharePhone && it.isNotBlank() },
            myCard.email.takeIf { myCard.shareEmail && it.isNotBlank() },
        ),
        bottom = {
            AnimatedContent(targetState = state.myChoice, label = "choice") { choice ->
                if (choice == null) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        if (!myCard.hasName) {
                            Text(
                                stringResource(R.string.poster_need_name),
                                style = TapShareTheme.type.footnote,
                                color = Color.White.copy(alpha = 0.85f),
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(bottom = 12.dp),
                            )
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            PosterButton(stringResource(R.string.poster_receive_only), filled = false, onClick = onReceiveOnly, modifier = Modifier.weight(1f))
                            PosterButton(stringResource(R.string.poster_share), filled = true, enabled = myCard.hasName, onClick = onShare, modifier = Modifier.weight(1f))
                        }
                    }
                } else {
                    Text(
                        if (peerName != null) stringResource(R.string.poster_waiting, peerName) else stringResource(R.string.poster_waiting_other),
                        style = TapShareTheme.type.headline,
                        color = Color.White.copy(alpha = 0.9f),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 14.dp),
                    )
                }
            }
        },
    )
}

@Composable
private fun ReceivedCard(card: ContactCard, iSharedMine: Boolean, onDone: () -> Unit) {
    BackHandler(onBack = onDone)
    val context = LocalContext.current
    PosterLayout(
        top = {
            Text(
                if (iSharedMine) stringResource(R.string.poster_shared_both) else stringResource(R.string.poster_shared_their, card.name),
                style = TapShareTheme.type.subheadline.copy(fontWeight = FontWeight.SemiBold),
                color = Color.White.copy(alpha = 0.9f),
            )
        },
        photo = rememberJpeg(card.photoJpeg),
        initials = initialsOf(card.name),
        name = card.name,
        details = listOfNotNull(card.phone, card.email),
        bottom = {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                PosterButton(stringResource(R.string.poster_done), filled = false, onClick = onDone, modifier = Modifier.weight(1f))
                PosterButton(
                    stringResource(R.string.poster_save),
                    filled = true,
                    onClick = { saveContact(context, card); onDone() },
                    modifier = Modifier.weight(1f),
                )
            }
        },
    )
}

/** Poster look: deep gradient, big round photo, very large name (contact-poster style). */
@Composable
private fun PosterLayout(
    top: @Composable () -> Unit,
    photo: ImageBitmap?,
    initials: String,
    name: String,
    details: List<String>,
    bottom: @Composable () -> Unit,
) {
    Box(Modifier.fillMaxSize().background(PosterGradient)) {
        BreathingGlow(Color.White)
        Column(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(8.dp))
            top()
            Spacer(Modifier.weight(1f))
            Avatar(photo, initials, size = 196.dp, borderColor = Color.White.copy(alpha = 0.9f))
            Spacer(Modifier.height(28.dp))
            Text(
                name,
                color = Color.White,
                fontSize = 40.sp,
                lineHeight = 44.sp,
                style = TapShareTheme.type.largeTitle,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            details.forEach {
                Spacer(Modifier.height(6.dp))
                Text(it, style = TapShareTheme.type.body, color = Color.White.copy(alpha = 0.85f), textAlign = TextAlign.Center)
            }
            Spacer(Modifier.weight(1.2f))
            Box(Modifier.fillMaxWidth()) { bottom() }
        }
    }
}

/** "With Rahul" + their tiny photo, or "Connecting…" until the other phone says hello. */
@Composable
private fun PeerChip(peer: PeerInfo?, connecting: Boolean) {
    Row(
        Modifier
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.18f))
            .padding(start = 6.dp, end = 14.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (peer != null && !connecting) {
            Avatar(rememberJpeg(peer.avatarJpeg), initialsOf(peer.name), size = 26.dp)
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.poster_with, peer.name), style = TapShareTheme.type.subheadline.copy(fontWeight = FontWeight.SemiBold), color = Color.White)
        } else {
            ProgressRing(null, Color.White, Modifier.size(22.dp), strokeWidth = 2.5.dp)
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.poster_connecting), style = TapShareTheme.type.subheadline.copy(fontWeight = FontWeight.SemiBold), color = Color.White)
        }
    }
}

@Composable
private fun PosterButton(text: String, filled: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.96f else 1f, spring(dampingRatio = 0.6f, stiffness = 700f), label = "posterBtn")
    Box(
        modifier
            .height(54.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                alpha = if (enabled) 1f else 0.45f
            }
            .clip(RoundedCornerShape(27.dp))
            .background(if (filled) Color.White else Color.White.copy(alpha = 0.2f))
            .clickable(interaction, indication = null, enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            style = TapShareTheme.type.headline,
            color = if (filled) Color(0xFF2F5BEE) else Color.White,
        )
    }
}

// =========================================================================== send / receive

@Composable
private fun SendingScreen(s: SessionState.Sending, onCancel: () -> Unit) {
    BackHandler(onBack = onCancel)
    val name = s.peer?.name ?: stringResource(R.string.someone)
    val subtitle = when (s.phase) {
        SendPhase.CONNECTING -> stringResource(R.string.sending_connecting)
        SendPhase.WAITING_FOR_ACCEPT -> stringResource(R.string.sending_waiting, name)
        SendPhase.TRANSFERRING -> progressText(s.progress)
    }
    TransferLayout(
        peer = s.peer,
        progress = if (s.phase == SendPhase.TRANSFERRING) s.progress.fraction else null,
        title = if (s.peer == null) stringResource(R.string.sending_connecting) else stringResource(R.string.sending_to, name),
        subtitle = subtitle,
        items = s.items,
        showWifiHint = s.phase == SendPhase.TRANSFERRING,
        buttons = { SecondaryButton(stringResource(R.string.cancel), onCancel) },
    )
}

@Composable
private fun IncomingScreen(s: SessionState.Incoming, onAccept: () -> Unit, onDecline: () -> Unit, onCancel: () -> Unit) {
    BackHandler(onBack = if (s.accepted) onCancel else onDecline)
    val what = itemsLabel(s.items)
    TransferLayout(
        peer = s.peer,
        progress = if (s.accepted) s.progress.fraction else null,
        spinWhenNull = false,
        title = if (s.accepted) stringResource(R.string.receiving_from, s.peer.name) else s.peer.name,
        subtitle = if (s.accepted) progressText(s.progress) else stringResource(R.string.incoming_wants, what),
        items = s.items,
        preview = rememberJpeg(s.previewJpeg),
        showWifiHint = s.accepted,
        buttons = {
            if (s.accepted) {
                SecondaryButton(stringResource(R.string.cancel), onCancel)
            } else {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    SecondaryButton(stringResource(R.string.incoming_decline), onDecline, Modifier.weight(1f))
                    PrimaryAction(stringResource(R.string.incoming_accept), onAccept, Modifier.weight(1f))
                }
            }
        },
    )
}

@Composable
private fun TransferLayout(
    peer: PeerInfo?,
    progress: Float?,
    title: String,
    subtitle: String,
    items: List<ItemInfo>,
    showWifiHint: Boolean,
    buttons: @Composable () -> Unit,
    preview: ImageBitmap? = null,
    spinWhenNull: Boolean = true,
) {
    val colors = TapShareTheme.colors
    val type = TapShareTheme.type
    val context = LocalContext.current
    val wifiOff = remember(showWifiHint) {
        showWifiHint && context.getSystemService(WifiManager::class.java)?.isWifiEnabled == false
    }
    Box(Modifier.fillMaxSize().background(colors.groupedBackground)) {
        BreathingGlow(colors.accent)
        Column(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.weight(1f))
            Box(contentAlignment = Alignment.Center) {
                if (progress != null || spinWhenNull) {
                    ProgressRing(progress, colors.accent, Modifier.size(148.dp))
                }
                Avatar(rememberJpeg(peer?.avatarJpeg), initialsOf(peer?.name.orEmpty()), size = 124.dp)
            }
            Spacer(Modifier.height(24.dp))
            Text(title, style = type.title2, color = colors.label, textAlign = TextAlign.Center)
            Spacer(Modifier.height(6.dp))
            Text(subtitle, style = type.body, color = colors.secondaryLabel, textAlign = TextAlign.Center)
            if (preview != null) {
                Spacer(Modifier.height(20.dp))
                Image(
                    preview,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(140.dp).clip(RoundedCornerShape(18.dp)),
                )
            }
            Spacer(Modifier.height(16.dp))
            ItemNames(items)
            if (wifiOff) {
                Spacer(Modifier.height(16.dp))
                Text(stringResource(R.string.wifi_off_hint), style = type.footnote, color = colors.orange, textAlign = TextAlign.Center)
            }
            Spacer(Modifier.weight(1.2f))
            Box(Modifier.fillMaxWidth()) { buttons() }
        }
    }
}

@Composable
private fun ItemNames(items: List<ItemInfo>) {
    val colors = TapShareTheme.colors
    val shown = items.take(3)
    shown.forEach {
        Text(
            it.name,
            style = TapShareTheme.type.footnote,
            color = colors.secondaryLabel,
            maxLines = 1,
            overflow = TextOverflow.MiddleEllipsis,
        )
    }
    if (items.size > shown.size) {
        Text(stringResource(R.string.incoming_and_more, items.size - shown.size), style = TapShareTheme.type.footnote, color = colors.tertiaryLabel)
    }
}

// =========================================================================== finished / failed

@Composable
private fun FinishedScreen(s: SessionState.Finished, onDone: () -> Unit) {
    BackHandler(onBack = onDone)
    val context = LocalContext.current
    val colors = TapShareTheme.colors
    val name = s.peerName ?: stringResource(R.string.someone)
    val good = s.outcome in setOf(Outcome.SENT, Outcome.RECEIVED, Outcome.CARD_SENT)
    val title = when (s.outcome) {
        Outcome.SENT -> stringResource(R.string.done_sent, name)
        Outcome.RECEIVED -> stringResource(R.string.done_received, name)
        Outcome.CARD_SENT -> stringResource(R.string.done_card_sent)
        Outcome.NOTHING_SHARED -> stringResource(R.string.done_nothing)
        Outcome.DECLINED_BY_PEER -> stringResource(R.string.done_declined_by_peer, name)
        Outcome.DECLINED_BY_ME -> stringResource(R.string.done_declined_by_me)
        Outcome.CANCELLED_BY_PEER -> stringResource(R.string.done_cancelled, name)
    }
    val subtitle = when (s.outcome) {
        Outcome.SENT -> itemsLabel(s.items)
        Outcome.RECEIVED -> itemsLabel(s.items) + "\n" + stringResource(R.string.done_received_where)
        Outcome.NOTHING_SHARED -> stringResource(R.string.done_nothing_body)
        else -> ""
    }
    ResultLayout(
        iconRes = if (good) R.drawable.ic_check else R.drawable.ic_close,
        iconColor = if (good) colors.green else colors.gray,
        title = title,
        subtitle = subtitle,
    ) {
        if (s.outcome == Outcome.RECEIVED && s.savedUris.isNotEmpty()) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SecondaryButton(stringResource(R.string.done_done), onDone, Modifier.weight(1f))
                PrimaryAction(
                    stringResource(R.string.done_open),
                    { openReceived(context, Uri.parse(s.savedUris.first()), s.items.firstOrNull()?.mime) },
                    Modifier.weight(1f),
                )
            }
        } else {
            PrimaryAction(stringResource(R.string.done_done), onDone)
        }
    }
}

@Composable
private fun FailedScreen(s: SessionState.Failed, onDone: () -> Unit) {
    BackHandler(onBack = onDone)
    val message = when (s.reason) {
        FailReason.CONNECT_TIMEOUT -> stringResource(R.string.failed_timeout)
        FailReason.CONNECTION_LOST -> stringResource(R.string.failed_lost)
        FailReason.NEARBY_ERROR -> stringResource(R.string.failed_nearby, s.code)
        FailReason.NEARBY_UNAVAILABLE -> stringResource(R.string.failed_unavailable)
        FailReason.BOTH_SENDING -> stringResource(R.string.failed_both_sending)
        FailReason.VERSION_MISMATCH -> stringResource(R.string.failed_version)
        FailReason.READ_FAILED -> stringResource(R.string.failed_read)
        FailReason.SAVE_FAILED -> stringResource(R.string.failed_save)
        FailReason.NO_SPACE -> stringResource(R.string.failed_no_space)
    }
    ResultLayout(R.drawable.ic_close, TapShareTheme.colors.red, stringResource(R.string.failed_title), message) {
        PrimaryAction(stringResource(R.string.done_done), onDone)
    }
}

@Composable
private fun ResultLayout(iconRes: Int, iconColor: Color, title: String, subtitle: String, buttons: @Composable () -> Unit) {
    val colors = TapShareTheme.colors
    val pop by animateFloatAsState(1f, spring(dampingRatio = 0.5f, stiffness = 300f), label = "pop")
    Box(Modifier.fillMaxSize().background(colors.groupedBackground)) {
        Column(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.weight(1f))
            Box(
                Modifier
                    .size(108.dp)
                    .graphicsLayer {
                        scaleX = pop
                        scaleY = pop
                    }
                    .clip(CircleShape)
                    .background(iconColor),
                contentAlignment = Alignment.Center,
            ) {
                Icon(painterResource(iconRes), contentDescription = null, tint = Color.White, modifier = Modifier.size(56.dp))
            }
            Spacer(Modifier.height(24.dp))
            Text(title, style = TapShareTheme.type.title2, color = colors.label, textAlign = TextAlign.Center)
            if (subtitle.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Text(subtitle, style = TapShareTheme.type.body, color = colors.secondaryLabel, textAlign = TextAlign.Center)
            }
            Spacer(Modifier.weight(1.2f))
            Box(Modifier.fillMaxWidth()) { buttons() }
        }
    }
}

@Composable
private fun PrimaryAction(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    com.aniket.tapshare.ui.components.PrimaryButton(text, onClick, modifier)
}

@Composable
private fun SecondaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = TapShareTheme.colors
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    Box(
        modifier
            .fillMaxWidth()
            .height(50.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(colors.switchTrackOff.copy(alpha = if (pressed) 0.35f else 0.2f))
            .clickable(interaction, indication = null, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = TapShareTheme.type.headline, color = colors.accent)
    }
}

// =========================================================================== helpers

@Composable
private fun itemsLabel(items: List<ItemInfo>): String {
    val summary = describeItems(items)
    val res = when (summary.kind) {
        ItemKind.PHOTOS -> R.plurals.kind_photos
        ItemKind.VIDEOS -> R.plurals.kind_videos
        ItemKind.FILES -> R.plurals.kind_files
        ItemKind.ITEMS -> R.plurals.kind_items
    }
    val context = LocalContext.current
    val count = pluralStringResource(res, summary.count, summary.count)
    return if (summary.totalBytes > 0) "$count · ${Formatter.formatShortFileSize(context, summary.totalBytes)}" else count
}

@Composable
private fun progressText(p: Progress): String {
    val context = LocalContext.current
    return stringResource(
        R.string.transfer_progress,
        (p.filesDone + if (p.filesDone < p.filesTotal) 1 else 0).coerceAtMost(p.filesTotal),
        p.filesTotal,
        Formatter.formatShortFileSize(context, p.bytesDone),
        Formatter.formatShortFileSize(context, p.bytesTotal),
    )
}

fun initialsOf(name: String): String =
    name.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }.take(2).joinToString("") { it.first().uppercase() }

/** Opens the system "new contact" screen pre-filled — no Contacts permission needed. */
private fun saveContact(context: Context, card: ContactCard) {
    val intent = Intent(ContactsContract.Intents.Insert.ACTION).apply {
        type = ContactsContract.RawContacts.CONTENT_TYPE
        putExtra(ContactsContract.Intents.Insert.NAME, card.name)
        card.phone?.let { putExtra(ContactsContract.Intents.Insert.PHONE, it) }
        card.email?.let { putExtra(ContactsContract.Intents.Insert.EMAIL, it) }
        card.photoJpeg?.let { photo ->
            val row = ContentValues().apply {
                put(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.Photo.CONTENT_ITEM_TYPE)
                put(ContactsContract.CommonDataKinds.Photo.PHOTO, photo)
            }
            putParcelableArrayListExtra(ContactsContract.Intents.Insert.DATA, arrayListOf(row))
        }
    }
    try {
        context.startActivity(intent)
    } catch (e: ActivityNotFoundException) {
        TapLog.w("No contacts app to save the card", e)
    }
}

private fun openReceived(context: Context, uri: Uri, mime: String?) {
    val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, mime ?: context.contentResolver.getType(uri))
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    try {
        context.startActivity(intent)
    } catch (e: ActivityNotFoundException) {
        TapLog.w("No app to open $uri", e)
    }
}
