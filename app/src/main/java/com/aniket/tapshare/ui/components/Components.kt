package com.aniket.tapshare.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.aniket.tapshare.R
import com.aniket.tapshare.ui.theme.TapShareTheme

private val SectionShape = RoundedCornerShape(12.dp)
private const val ICON_TILE_DP = 29

/** iOS "inset grouped" section: optional header, rounded white card, optional footer. */
@Composable
fun GroupedSection(
    modifier: Modifier = Modifier,
    header: String? = null,
    footer: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = TapShareTheme.colors
    val type = TapShareTheme.type
    Column(modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        if (header != null) {
            Text(
                text = header.uppercase(),
                style = type.footnote,
                color = colors.secondaryLabel,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 7.dp),
            )
        }
        Column(
            Modifier
                .fillMaxWidth()
                .clip(SectionShape)
                .background(colors.cell),
            content = content,
        )
        if (footer != null) {
            Text(
                text = footer,
                style = type.footnote,
                color = colors.secondaryLabel,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 7.dp),
            )
        }
    }
}

/**
 * One row inside a [GroupedSection]. Pressing shows the iOS grey highlight (instant on press,
 * fades out on release) instead of an Android ripple.
 */
@Composable
fun GroupedRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    icon: Painter? = null,
    iconBackground: Color = TapShareTheme.colors.accent,
    showDivider: Boolean = false,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable RowScope.() -> Unit)? = null,
) {
    val colors = TapShareTheme.colors
    val type = TapShareTheme.type
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val background by animateColorAsState(
        targetValue = if (pressed && onClick != null) colors.cellPressed else colors.cell,
        animationSpec = tween(durationMillis = if (pressed) 0 else 280),
        label = "rowHighlight",
    )
    val clickModifier = if (onClick != null) {
        Modifier.clickable(interactionSource = interaction, indication = null, role = Role.Button, onClick = onClick)
    } else {
        Modifier
    }

    Column(modifier.fillMaxWidth().background(background).then(clickModifier)) {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 44.dp)
                .padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (icon != null) {
                IconTile(icon, iconBackground)
                Spacer(Modifier.width(12.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(title, style = type.body, color = colors.label)
                if (subtitle != null) {
                    Text(
                        subtitle,
                        style = type.footnote,
                        color = colors.secondaryLabel,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }
            if (trailing != null) {
                Spacer(Modifier.width(12.dp))
                trailing()
            }
        }
        if (showDivider) {
            // iOS separators start where the text starts, not at the edge.
            val inset = if (icon != null) (16 + ICON_TILE_DP + 12).dp else 16.dp
            Box(
                Modifier
                    .padding(start = inset)
                    .fillMaxWidth()
                    .height(0.5.dp)
                    .background(colors.separator),
            )
        }
    }
}

/** Small rounded coloured square with a white glyph (Settings-style). */
@Composable
fun IconTile(icon: Painter, background: Color, modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(ICON_TILE_DP.dp)
            .clip(RoundedCornerShape(7.dp))
            .background(background),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
    }
}

/** Secondary text at the end of a row, optionally with a chevron (means "tap to open"). */
@Composable
fun TrailingValue(text: String, showChevron: Boolean = false, color: Color = TapShareTheme.colors.secondaryLabel) {
    Text(text, style = TapShareTheme.type.body, color = color)
    if (showChevron) {
        Spacer(Modifier.width(6.dp))
        Icon(
            painterResource(R.drawable.ic_chevron_right),
            contentDescription = null,
            tint = TapShareTheme.colors.tertiaryLabel,
            modifier = Modifier.size(14.dp),
        )
    }
}

/** iOS-style switch: 51×31 track, springy thumb, green when on, haptic tick on change. */
@Composable
fun IosSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = TapShareTheme.colors
    val haptics = LocalHapticFeedback.current
    val progress by animateFloatAsState(
        targetValue = if (checked) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.72f, stiffness = 520f),
        label = "switchThumb",
    )
    Box(
        modifier
            .size(width = 51.dp, height = 31.dp)
            .clip(CircleShape)
            .background(lerp(colors.switchTrackOff, colors.green, progress))
            .toggleable(
                value = checked,
                interactionSource = null,
                indication = null,
                role = Role.Switch,
                onValueChange = { on ->
                    haptics.performHapticFeedback(if (on) HapticFeedbackType.ToggleOn else HapticFeedbackType.ToggleOff)
                    onCheckedChange(on)
                },
            )
            .padding(2.dp),
    ) {
        Box(
            Modifier
                .offset(x = 20.dp * progress)
                .size(27.dp)
                .shadow(elevation = 2.dp, shape = CircleShape)
                .background(Color.White, CircleShape),
        )
    }
}

/** Big filled button (iOS "large" button). Shrinks slightly with a spring while pressed. */
@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val colors = TapShareTheme.colors
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.97f else 1f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 700f),
        label = "buttonScale",
    )
    Box(
        modifier
            .fillMaxWidth()
            .height(50.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                alpha = if (pressed) 0.85f else 1f
            }
            .clip(RoundedCornerShape(14.dp))
            .background(if (enabled) colors.accent else colors.gray.copy(alpha = 0.35f))
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                role = Role.Button,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = TapShareTheme.type.headline, color = Color.White, textAlign = TextAlign.Center)
    }
}

/** Small tinted capsule button used inside rows ("Allow", "Settings"). */
@Composable
fun CapsuleButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = TapShareTheme.colors
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    Box(
        modifier
            .height(30.dp)
            .clip(CircleShape)
            .background(colors.accent.copy(alpha = if (pressed) 0.22f else 0.12f))
            .clickable(interactionSource = interaction, indication = null, role = Role.Button, onClick = onClick)
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = TapShareTheme.type.subheadline.copy(fontWeight = TapShareTheme.type.headline.fontWeight), color = colors.accent)
    }
}
