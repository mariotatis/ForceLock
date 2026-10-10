package com.mariotatis.forcelock.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.mariotatis.forcelock.AppRule
import com.mariotatis.forcelock.AutoLockSettings
import com.mariotatis.forcelock.DeviceStatus
import com.mariotatis.forcelock.LockTimeout
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun ForceLockApp() {
    val context = LocalContext.current
    val settings = remember { AutoLockSettings(context) }
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }

    var saved by remember { mutableStateOf(settings.timeout) }
    var status by remember { mutableStateOf(DeviceStatus.read(context)) }
    var rules by remember { mutableStateOf(settings.appRules) }
    var picking by rememberSaveable { mutableStateOf(false) }
    var editing by rememberSaveable { mutableStateOf<String?>(null) }
    val apps by produceState<List<InstalledApp>?>(null) {
        value = withContext(Dispatchers.IO) { loadInstalledApps(context) }
    }

    fun labelOf(packageName: String) =
        apps?.firstOrNull { it.packageName == packageName }?.label ?: packageName

    // Permissions are granted in other screens (or over adb), so re-check whenever we come back.
    LifecycleResumeEffect(Unit) {
        status = DeviceStatus.read(context)
        onPauseOrDispose { }
    }

    fun persist(timeout: LockTimeout?) {
        settings.timeout = timeout
        saved = timeout
    }

    fun select(timeout: LockTimeout) {
        if (timeout == saved) return
        persist(timeout)
        val message = if (status.lockServiceEnabled) {
            "Lock time set to ${timeout.spokenLabel}."
        } else {
            "Lock time set to ${timeout.spokenLabel}. Allow access in Accessibility settings to start locking."
        }
        scope.launch {
            snackbar.currentSnackbarData?.dismiss()
            snackbar.showSnackbar(message)
        }
    }

    fun saveRule(packageName: String, rule: AppRule?) {
        val updated = if (rule == null) rules - packageName else rules + (packageName to rule)
        settings.appRules = updated
        rules = updated
        val label = labelOf(packageName)
        val message = when {
            rule == null -> "$label now uses your main lock time."
            rule.timeout == null -> "$label will never lock on its own."
            else -> "$label now locks after ${rule.timeout.spokenLabel}."
        }
        scope.launch {
            snackbar.currentSnackbarData?.dismiss()
            snackbar.showSnackbar(message)
        }
    }

    fun turnOff() {
        val previous = saved ?: return
        persist(null)
        scope.launch {
            snackbar.currentSnackbarData?.dismiss()
            val result = snackbar.showSnackbar(
                message = "Auto-lock is off. Your device will stay awake as before.",
                actionLabel = "Undo",
                duration = SnackbarDuration.Long,
            )
            if (result == SnackbarResult.ActionPerformed) persist(previous)
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentAlignment = Alignment.TopCenter,
        ) {
            val wide = maxWidth >= 700.dp && maxWidth > maxHeight

            val overview: @Composable () -> Unit = {
                Header()
                StatusCard(
                    status = status,
                    saved = saved,
                    onAllowAccess = { DeviceStatus.openLockServiceSettings(context) },
                    onOpenAppInfo = { DeviceStatus.openAppInfo(context) },
                    onTurnOff = ::turnOff,
                )
            }
            val picker: @Composable () -> Unit = {
                TimePicker(saved = saved, onSelect = ::select)
                AppRulesSection(
                    rules = rules,
                    labelOf = ::labelOf,
                    onAdd = { picking = true },
                    onEdit = { editing = it },
                )
            }

            if (wide) {
                Row(
                    modifier = Modifier
                        .widthIn(max = 1100.dp)
                        .fillMaxSize()
                        .padding(horizontal = 32.dp),
                    horizontalArrangement = Arrangement.spacedBy(32.dp),
                ) {
                    ScrollingColumn(Modifier.weight(1f)) { overview() }
                    ScrollingColumn(Modifier.weight(1f)) { picker() }
                }
            } else {
                ScrollingColumn(
                    Modifier
                        .widthIn(max = 560.dp)
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp),
                ) {
                    overview()
                    picker()
                }
            }
        }
    }

    if (picking) {
        AppPickerDialog(
            apps = apps,
            configured = rules.keys,
            onPick = {
                picking = false
                editing = it.packageName
            },
            onDismiss = { picking = false },
        )
    }

    editing?.let { packageName ->
        AppRuleDialog(
            packageName = packageName,
            label = labelOf(packageName),
            existing = rules[packageName],
            mainTimeout = saved,
            onSave = {
                editing = null
                saveRule(packageName, it)
            },
            onRemove = {
                editing = null
                saveRule(packageName, null)
            },
            onDismiss = { editing = null },
        )
    }
}

@Composable
private fun ScrollingColumn(modifier: Modifier, content: @Composable () -> Unit) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) { content() }
}

