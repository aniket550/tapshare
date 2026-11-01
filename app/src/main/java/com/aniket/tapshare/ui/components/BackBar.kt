package com.aniket.tapshare.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.aniket.tapshare.R
import com.aniket.tapshare.ui.theme.TapShareTheme

/** iOS-style "‹ TapShare" back button. */
@Composable
fun BackBar(onBack: () -> Unit, label: String = stringResource(R.string.app_name)) {
    val colors = TapShareTheme.colors
    Row(
        Modifier
            .padding(start = 8.dp, top = 4.dp)
            .clip(RoundedCornerShape(8.dp))
            .clickable(remember { MutableInteractionSource() }, indication = null, role = Role.Button, onClick = onBack)
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
        Text(label, style = TapShareTheme.type.body, color = colors.accent)
    }
}
