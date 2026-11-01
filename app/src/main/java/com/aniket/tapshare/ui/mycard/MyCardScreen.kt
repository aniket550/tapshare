package com.aniket.tapshare.ui.mycard

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aniket.tapshare.R
import com.aniket.tapshare.appContainer
import com.aniket.tapshare.ui.components.Avatar
import com.aniket.tapshare.ui.components.BackBar
import com.aniket.tapshare.ui.components.GroupedRow
import com.aniket.tapshare.ui.components.GroupedSection
import com.aniket.tapshare.ui.components.IosSwitch
import com.aniket.tapshare.ui.components.IosTextFieldRow
import com.aniket.tapshare.ui.theme.TapShareTheme
import kotlinx.coroutines.launch

/** Name, photo and which details "Share" sends. Saved as you type. */
@Composable
fun MyCardScreen(onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    val context = LocalContext.current
    val store = context.appContainer.myCard
    val card by store.card.collectAsStateWithLifecycle()
    val colors = TapShareTheme.colors
    val type = TapShareTheme.type
    val scope = rememberCoroutineScope()
    val photo by produceState<ImageBitmap?>(null, card.photoVersion) { value = store.photoBitmap()?.asImageBitmap() }

    val pickPhoto = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            scope.launch {
                if (!store.setPhoto(uri)) Toast.makeText(context, R.string.my_card_photo_failed, Toast.LENGTH_SHORT).show()
            }
        }
    }
    val choosePhoto = { pickPhoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }

    Column(
        Modifier
            .fillMaxSize()
            .background(colors.groupedBackground)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .imePadding(),
    ) {
        BackBar(onBack)
        Column(Modifier.verticalScroll(rememberScrollState()).padding(bottom = 32.dp)) {
            Text(
                stringResource(R.string.my_card_title),
                style = type.largeTitle,
                color = colors.label,
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 16.dp),
            )
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Avatar(
                    photo,
                    card.initials,
                    size = 132.dp,
                    modifier = Modifier.clickable(
                        remember { MutableInteractionSource() },
                        indication = null,
                        role = Role.Button,
                        onClick = choosePhoto,
                    ),
                )
                Spacer(Modifier.height(10.dp))
                Row {
                    TextAction(stringResource(if (card.hasPhoto) R.string.my_card_change_photo else R.string.my_card_add_photo), choosePhoto)
                    if (card.hasPhoto) {
                        Spacer(Modifier.width(20.dp))
                        TextAction(stringResource(R.string.my_card_remove_photo), store::removePhoto, color = colors.red)
                    }
                }
            }

            Spacer(Modifier.height(24.dp))
            GroupedSection(header = stringResource(R.string.my_card_section_name)) {
                IosTextFieldRow(card.name, { v -> store.update { it.copy(name = v.take(60)) } }, stringResource(R.string.my_card_name_hint))
            }
            Spacer(Modifier.height(28.dp))
            GroupedSection(header = stringResource(R.string.my_card_section_contact)) {
                IosTextFieldRow(
                    card.phone,
                    { v -> store.update { it.copy(phone = v.take(30)) } },
                    stringResource(R.string.my_card_phone_hint),
                    KeyboardType.Phone,
                    showDivider = true,
                )
                IosTextFieldRow(
                    card.email,
                    { v -> store.update { it.copy(email = v.take(80)) } },
                    stringResource(R.string.my_card_email_hint),
                    KeyboardType.Email,
                )
            }
            Spacer(Modifier.height(28.dp))
            GroupedSection(
                header = stringResource(R.string.my_card_section_share),
                footer = stringResource(R.string.my_card_share_footer),
            ) {
                GroupedRow(title = stringResource(R.string.my_card_share_phone), showDivider = true) {
                    IosSwitch(card.sharePhone, { on -> store.update { it.copy(sharePhone = on) } })
                }
                GroupedRow(title = stringResource(R.string.my_card_share_email)) {
                    IosSwitch(card.shareEmail, { on -> store.update { it.copy(shareEmail = on) } })
                }
            }
        }
    }
}

@Composable
private fun TextAction(text: String, onClick: () -> Unit, color: androidx.compose.ui.graphics.Color = TapShareTheme.colors.accent) {
    Text(
        text,
        style = TapShareTheme.type.body,
        color = color,
        modifier = Modifier.clickable(remember { MutableInteractionSource() }, indication = null, role = Role.Button, onClick = onClick),
    )
}
