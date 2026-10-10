package com.mariotatis.forcelock.ui

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.Image
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.graphics.drawable.toBitmap
import com.mariotatis.forcelock.AppRule
import com.mariotatis.forcelock.LockTimeout
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** An app that can get its own lock time: anything in the app drawer, plus home apps like ES-DE. */
data class InstalledApp(val packageName: String, val label: String)

fun loadInstalledApps(context: Context): List<InstalledApp> {
    val pm = context.packageManager
    return listOf(Intent.CATEGORY_LAUNCHER, Intent.CATEGORY_HOME)
        .flatMap { category ->
            @Suppress("DEPRECATION") // The flags overload needs API 33.
            pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(category), 0)
        }
        .map { it.activityInfo.applicationInfo }
        .distinctBy { it.packageName }
        .filter { it.packageName != context.packageName }
        .map { InstalledApp(it.packageName, it.loadLabel(pm).toString()) }
        .sortedBy { it.label.lowercase() }
}

@Composable
fun AppRulesSection(
    rules: Map<String, AppRule>,
    labelOf: (String) -> String,
    onAdd: () -> Unit,
    onEdit: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        HorizontalDivider(
            modifier = Modifier.padding(bottom = 4.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHighest,
        )
        if (rules.isEmpty()) {
            SectionHeader("Per-app times", style = MaterialTheme.typography.titleMedium)
            Surface(
                shape = CardShape,
                color = MaterialTheme.colorScheme.surfaceContainer,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "Give a game more time for cutscenes, or keep ES-DE awake while it scrapes.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(12.dp))
                    FilledTonalButton(onClick = onAdd) { Text("Add app") }
                }
            }
        } else {
            SectionHeader("Per-app times", style = MaterialTheme.typography.titleMedium) {
                FilledTonalButton(onClick = onAdd) { Text("Add app") }
            }
            // Two apps per row keeps the list short on landscape handhelds.
            rules.entries.sortedBy { labelOf(it.key).lowercase() }.chunked(2).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { (pkg, rule) ->
                        Surface(
                            shape = TileShape,
                            color = MaterialTheme.colorScheme.surfaceContainer,
                            modifier = Modifier.weight(1f),
                        ) {
                            AppRow(
                                packageName = pkg,
                                label = labelOf(pkg),
                                detail = rule.summary(),
                                onClick = { onEdit(pkg) },
                                compact = true,
                            )
                        }
                    }
                    if (row.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
fun AppPickerDialog(
    apps: List<InstalledApp>?,
    configured: Set<String>,
    onPick: (InstalledApp) -> Unit,
    onDismiss: () -> Unit,
) {
    LargeDialog(onDismiss) {
        Text(
            "Choose an app",
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 24.dp, bottom = 8.dp),
        )
        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
            if (apps == null) {
                CircularProgressIndicator()
            } else {
                LazyColumn(Modifier.fillMaxSize()) {
                    items(apps, key = { it.packageName }) { app ->
                        AppRow(
                            packageName = app.packageName,
                            label = app.label,
                            detail = if (app.packageName in configured) "Has its own time" else null,
                            onClick = { onPick(app) },
                        )
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    }
}

@Composable
fun AppRuleDialog(
    packageName: String,
    label: String,
    existing: AppRule?,
    mainTimeout: LockTimeout?,
    onSave: (AppRule) -> Unit,
    onRemove: () -> Unit,
    onDismiss: () -> Unit,
) {
    // A new app starts from the main time, so it's one tap to adjust.
    var choice by rememberSaveable { mutableStateOf(if (existing != null) existing.timeout else mainTimeout) }
    var waitForDownloads by rememberSaveable { mutableStateOf(existing?.waitForDownloads == true) }
    val colors = MaterialTheme.colorScheme

    LargeDialog(onDismiss, maxWidth = 900.dp) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(top = 20.dp, bottom = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 24.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AppIcon(packageName, size = 40.dp)
                Spacer(Modifier.width(14.dp))
                Text(label, style = MaterialTheme.typography.titleLarge)
            }

            // One scrolling row keeps the dialog short enough for landscape handhelds.
            val options: List<LockTimeout?> = listOf(null) + LockTimeout.entries
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Spacer(Modifier.width(16.dp))
                options.forEach { option ->
                    val (amount, unit) = option?.amountAndUnit() ?: ("Never" to "")
                    OptionTile(
                        amount = amount,
                        unit = unit,
                        spokenLabel = option?.spokenLabel ?: "Never lock",
                        isSelected = option == choice,
                        isCurrent = existing != null && option == existing.timeout,
                        onClick = { choice = option },
                        modifier = Modifier.width(88.dp),
                    )
                }
                Spacer(Modifier.width(16.dp))
            }

            Surface(
                shape = TileShape,
                color = colors.surfaceContainerHigh,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
            ) {
                Row(
                    modifier = Modifier
                        .toggleable(
                            value = waitForDownloads && choice != null,
                            enabled = choice != null,
                            role = Role.Switch,
                            onValueChange = { waitForDownloads = it },
                        )
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Stay awake while downloading", style = MaterialTheme.typography.titleSmall)
                        Text(
                            "Waits for downloads to finish, like ES-DE scraping.",
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Switch(
                        checked = waitForDownloads && choice != null,
                        onCheckedChange = null,
                        enabled = choice != null,
                    )
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (existing != null) {
                TextButton(
                    onClick = onRemove,
                    colors = ButtonDefaults.textButtonColors(contentColor = colors.error),
                ) { Text("Remove") }
            }
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onDismiss) { Text("Cancel") }
            Spacer(Modifier.width(8.dp))
            Button(
                onClick = {
                    onSave(AppRule(choice, waitForDownloads = waitForDownloads && choice != null))
                },
            ) { Text("Save") }
        }
    }
}

/** Short enough for a half-width tile, e.g. "10 sec", or "5 min · Waits" when it waits for downloads. */
fun AppRule.summary(): String {
    val time = timeout?.amountAndUnit()?.let { (amount, unit) -> "$amount $unit" } ?: "Never"
    return if (waitForDownloads) "$time · Waits" else time
}

@Composable
private fun AppRow(
    packageName: String,
    label: String,
    detail: String?,
    onClick: () -> Unit,
    compact: Boolean = false,
) {
    Surface(onClick = onClick, color = Color.Transparent) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    horizontal = if (compact) 12.dp else 20.dp,
                    vertical = if (compact) 10.dp else 12.dp,
                ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AppIcon(packageName, size = if (compact) 32.dp else 36.dp)
            Spacer(Modifier.width(if (compact) 10.dp else 14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    label,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (detail != null) {
                    Text(
                        detail,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun AppIcon(packageName: String, size: Dp) {
    val context = LocalContext.current
    val icon by produceState<ImageBitmap?>(null, packageName) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                context.packageManager.getApplicationIcon(packageName).toBitmap().asImageBitmap()
            }.getOrNull()
        }
    }
    Box(Modifier.size(size)) {
        icon?.let { Image(it, contentDescription = null, modifier = Modifier.fillMaxSize()) }
    }
}

/** A tall dialog that fits both phones and landscape handhelds. */
@Composable
private fun LargeDialog(
    onDismiss: () -> Unit,
    maxWidth: Dp = 640.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            shape = CardShape,
            color = MaterialTheme.colorScheme.surfaceContainer,
            modifier = Modifier
                .widthIn(max = maxWidth)
                .fillMaxWidth(0.92f)
                .fillMaxHeight(0.9f),
        ) {
            Column(content = content)
        }
    }
}
