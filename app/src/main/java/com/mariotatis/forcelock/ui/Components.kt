package com.mariotatis.forcelock.ui

import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.mariotatis.forcelock.DeviceStatus
import com.mariotatis.forcelock.LockTimeout
import com.mariotatis.forcelock.R

private val CardShape = RoundedCornerShape(24.dp)
private val TileShape = RoundedCornerShape(18.dp)

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
                Button(onClick = onAllowAccess) { Text("Open settings") }
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
            body = "Choose how long your device can sit untouched, then tap Save.",
        )

        else -> InfoCard(
            icon = R.drawable.ic_check_circle,
            accent = colors.secondary,
            accentContainer = colors.secondaryContainer,
            title = "Auto-lock is on",
            body = "Your device locks after ${saved.spokenLabel} without a button press " +
                "or touch, even if the launcher or a game tries to keep it awake.",
        ) {
            TextButton(onClick = onTurnOff) { Text("Turn off") }
        }
    }
}

@Composable
fun TimePicker(selected: LockTimeout?, saved: LockTimeout?, onSelect: (LockTimeout) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Column(Modifier.padding(horizontal = 4.dp)) {
            Text("Lock after", style = MaterialTheme.typography.titleLarge)
            Text(
                "How long your device can sit idle before it locks.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        LockTimeout.entries.chunked(COLUMNS).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEach { option ->
                    TimeTile(
                        option = option,
                        isSelected = option == selected,
                        isCurrent = option == saved,
                        onClick = { onSelect(option) },
                        modifier = Modifier.weight(1f),
                    )
                }
                repeat(COLUMNS - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun TimeTile(
    option: LockTimeout,
    isSelected: Boolean,
    isCurrent: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val container by animateColorAsState(
        if (isSelected) colors.primary else colors.surfaceContainerHigh, label = "tile",
    )
    val content = if (isSelected) colors.onPrimary else colors.onSurface
    val (amount, unit) = option.amountAndUnit()

    Surface(
        onClick = onClick,
        shape = TileShape,
        color = container,
        contentColor = content,
        // A clear ring for D-pad navigation on handhelds.
        border = if (focused) BorderStroke(3.dp, colors.onSurface) else null,
        interactionSource = interaction,
        modifier = modifier
            .height(76.dp)
            .semantics {
                role = Role.RadioButton
                selected = isSelected
                contentDescription = option.spokenLabel
            },
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                buildAnnotatedString {
                    withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append(amount) }
                    withStyle(SpanStyle(fontSize = MaterialTheme.typography.labelLarge.fontSize)) {
                        append(" $unit")
                    }
                },
                style = MaterialTheme.typography.titleLarge,
            )
            if (isCurrent) {
                Box(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 10.dp)
                        .size(8.dp)
                        .background(colors.secondary, CircleShape)
                        // Keeps the green dot crisp on the light selected tile.
                        .border(1.5.dp, if (isSelected) colors.onPrimary else Color.Transparent, CircleShape),
                )
            }
        }
    }
}

@Composable
fun SaveButton(selected: LockTimeout?, saved: LockTimeout?, onSave: () -> Unit) {
    val alreadySaved = selected != null && selected == saved
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Button(
            onClick = onSave,
            enabled = selected != null && !alreadySaved,
            shape = RoundedCornerShape(18.dp),
            colors = ButtonDefaults.buttonColors(
                disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            ),
            modifier = Modifier
                .fillMaxWidth()
                .height(58.dp),
        ) {
            if (alreadySaved) {
                Icon(
                    painterResource(R.drawable.ic_check_circle),
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(8.dp))
            }
            Text(
                when {
                    selected == null -> "Choose a time"
                    alreadySaved -> "Saved"
                    else -> "Save"
                },
            )
        }
        if (saved != null) {
            Row(
                modifier = Modifier.padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .size(6.dp)
                        .background(MaterialTheme.colorScheme.secondary, CircleShape),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "Current setting",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
fun ConfirmDialog(
    timeout: LockTimeout,
    previous: LockTimeout?,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(painterResource(R.drawable.ic_lock), contentDescription = null) },
        title = { Text(if (previous == null) "Turn on auto-lock?" else "Change auto-lock time?") },
        text = {
            Text(
                buildAnnotatedString {
                    append("Your device will lock after ")
                    withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) {
                        append(timeout.spokenLabel)
                    }
                    if (previous != null) append(" (instead of ${previous.spokenLabel})")
                    append(
                        " without any activity, no matter which app or launcher is open. " +
                            "Unlock it the usual way.",
                    )
                },
            )
        },
        confirmButton = {
            Button(onClick = onConfirm) { Text(if (previous == null) "Turn on" else "Change") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        shape = CardShape,
    )
}

@Composable
private fun StepList(steps: List<String>, accent: Color, accentContainer: Color) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        steps.forEachIndexed { index, step ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .background(accentContainer, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "${index + 1}",
                        style = MaterialTheme.typography.labelLarge,
                        color = accent,
                    )
                }
                Spacer(Modifier.width(12.dp))
                Text(
                    step,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

@Composable
private fun InfoCard(
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
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (icon != null) {
                    IconBadge(icon = icon, tint = accent, background = accentContainer, size = 40)
                    Spacer(Modifier.width(14.dp))
                }
                // Without an icon, the title carries the card's hierarchy on its own.
                Text(
                    title,
                    style = if (icon == null) {
                        MaterialTheme.typography.titleLarge
                    } else {
                        MaterialTheme.typography.titleMedium
                    },
                )
            }
            if (body != null) {
                Text(
                    body,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            actions?.invoke()
        }
    }
}

@Composable
private fun IconBadge(@DrawableRes icon: Int, tint: Color, background: Color, size: Int) {
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

private fun LockTimeout.amountAndUnit(): Pair<String, String> = when {
    millis < 60_000 -> "${millis / 1000}" to "sec"
    millis < 3_600_000 -> "${millis / 60_000}" to "min"
    else -> "${millis / 3_600_000}" to "hr"
}

private const val COLUMNS = 4
