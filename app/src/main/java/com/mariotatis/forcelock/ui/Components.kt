package com.mariotatis.forcelock.ui

import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.mariotatis.forcelock.DeviceStatus
import com.mariotatis.forcelock.LockTimeout
import com.mariotatis.forcelock.R

val CardShape = RoundedCornerShape(24.dp)
val TileShape = RoundedCornerShape(18.dp)

@Composable
fun Header() {
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconBadge(
            icon = R.drawable.ic_lock,
            tint = MaterialTheme.colorScheme.primary,
            background = MaterialTheme.colorScheme.primaryContainer,
            size = 56,
        )
        Spacer(Modifier.width(16.dp))
        Column {
            Text("ForceLock", style = MaterialTheme.typography.headlineMedium)
            Text(
                "A real screen lock, whatever app is on screen.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
fun StatusCard(
    status: DeviceStatus,
    saved: LockTimeout?,
    onAllowAccess: () -> Unit,
    onOpenAppInfo: () -> Unit,
    onTurnOff: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    when {
        !status.lockServiceEnabled -> InfoCard(
            icon = null,
            accent = colors.tertiary,
            accentContainer = colors.tertiaryContainer,
            title = "Auto-lock permission needed",
            body = null,
        ) {
            var showHelp by rememberSaveable { mutableStateOf(false) }
            StepList(
                steps = listOf(
                    "Go to Settings › Accessibility",
                    "Choose ForceLock auto-lock",
                    "Turn on Use ForceLock auto-lock",
                    "Come back to ForceLock and set your lock time",
                ),
                accent = colors.tertiary,
                accentContainer = colors.tertiaryContainer,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(onClick = onAllowAccess) { Text("Open settings") }
                Spacer(Modifier.width(8.dp))
                TextButton(onClick = { showHelp = !showHelp }) { Text("Option greyed out?") }
            }
            AnimatedVisibility(visible = showHelp) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Android blocks this for apps installed outside the Play Store. Open " +
                            "App info, tap the ⋮ menu, choose \"Allow restricted settings\", " +
                            "then come back and try again.",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant,
                    )
                    TextButton(onClick = onOpenAppInfo) { Text("Open App info") }
                }
            }
        }

        saved == null -> InfoCard(
            icon = null,
            accent = colors.primary,
            accentContainer = colors.primaryContainer,
            title = "Ready when you are",
            body = "Pick how long your device can sit untouched.",
        )

        else -> InfoCard(
            icon = null,
            accent = colors.secondary,
            accentContainer = colors.secondaryContainer,
            title = "Auto-lock is on",
            body = listOf(
                "Locks after ${saved.spokenLabel} with no button press or touch.",
                "Works even when a game or launcher keeps the screen on.",
                "Apps in Per-app times use their own time.",
            ).joinToString("\n"),
        ) {
            OutlinedButton(onClick = onTurnOff) { Text("Turn off") }
        }
    }
}

@Composable
/** Tapping a time saves it right away; the outlined tile is the current setting. */
fun TimePicker(saved: LockTimeout?, onSelect: (LockTimeout) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionHeader("Lock after")
        LockTimeout.entries.chunked(COLUMNS).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { option ->
                    val (amount, unit) = option.amountAndUnit()
                    OptionTile(
                        amount = amount,
                        unit = unit,
                        spokenLabel = option.spokenLabel,
                        isSelected = option == saved,
                        isCurrent = false,
                        onClick = { onSelect(option) },
                        modifier = Modifier.weight(1f),
                    )
                }
                repeat(COLUMNS - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

/** A selectable tile showing an amount with a smaller unit, e.g. "5 min". */
@Composable
fun OptionTile(
    amount: String,
    unit: String,
    spokenLabel: String,
    isSelected: Boolean,
    isCurrent: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val outline by animateColorAsState(
        if (isSelected) colors.primary else Color.Transparent, label = "tile",
    )

    Surface(
        onClick = onClick,
        shape = TileShape,
        color = colors.surfaceContainerHigh,
        contentColor = if (isSelected) colors.primary else colors.onSurface,
        // The selection is an outline; D-pad focus gets a thicker, brighter ring on top.
        border = if (focused) BorderStroke(3.dp, colors.onSurface) else BorderStroke(2.dp, outline),
        interactionSource = interaction,
        modifier = modifier
            .height(40.dp)
            .semantics {
                role = Role.RadioButton
                selected = isSelected
                contentDescription = spokenLabel
            },
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                buildAnnotatedString {
                    withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append(amount) }
                    if (unit.isNotEmpty()) {
                        withStyle(SpanStyle(fontSize = MaterialTheme.typography.labelMedium.fontSize)) {
                            append(" $unit")
                        }
                    }
                },
                style = MaterialTheme.typography.titleSmall,
            )
            if (isCurrent) {
                Box(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 3.dp)
                        .size(5.dp)
                        .background(colors.secondary, CircleShape),
                )
            }
        }
    }
}

/** A section title with a compact action at the end of the same row. */
@Composable
fun SectionHeader(
    title: String,
    style: TextStyle = MaterialTheme.typography.titleLarge,
    action: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = style, modifier = Modifier.weight(1f))
        action?.invoke()
    }
}

@Composable
private fun StepList(steps: List<String>, accent: Color, accentContainer: Color) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        steps.forEachIndexed { index, step ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(20.dp)
                        .background(accentContainer, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "${index + 1}",
                        style = MaterialTheme.typography.labelMedium,
                        color = accent,
                    )
                }
                Spacer(Modifier.width(10.dp))
                Text(
                    step,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

@Composable
fun InfoCard(
    @DrawableRes icon: Int?,
    accent: Color,
    accentContainer: Color,
    title: String,
    body: String?,
    actions: (@Composable () -> Unit)? = null,
) {
    Surface(
        shape = CardShape,
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(),
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (icon != null) {
                    IconBadge(icon = icon, tint = accent, background = accentContainer, size = 40)
                    Spacer(Modifier.width(14.dp))
                }
                // Without an icon, the title carries the card's hierarchy on its own.
                Text(
                    title,
                    style = if (icon == null) {
                        MaterialTheme.typography.titleMedium
                    } else {
                        MaterialTheme.typography.titleMedium
                    },
                )
            }
            if (body != null) {
                Text(
                    body,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            actions?.invoke()
        }
    }
}

@Composable
fun IconBadge(@DrawableRes icon: Int, tint: Color, background: Color, size: Int) {
    Box(
        modifier = Modifier
            .size(size.dp)
            .background(background, RoundedCornerShape((size * 0.32f).dp)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painterResource(icon),
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size((size * 0.5f).dp),
        )
    }
}

fun LockTimeout.amountAndUnit(): Pair<String, String> = when {
    millis < 60_000 -> "${millis / 1000}" to "sec"
    millis < 3_600_000 -> "${millis / 60_000}" to "min"
    else -> "${millis / 3_600_000}" to "hr"
}

const val COLUMNS = 4
