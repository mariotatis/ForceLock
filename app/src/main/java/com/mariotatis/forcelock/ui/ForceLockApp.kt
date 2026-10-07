package com.mariotatis.forcelock.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.mariotatis.forcelock.AutoLockSettings
import com.mariotatis.forcelock.DeviceStatus
import com.mariotatis.forcelock.LockTimeout
import kotlinx.coroutines.launch

@Composable
fun ForceLockApp() {
    val context = LocalContext.current
    val settings = remember { AutoLockSettings(context) }
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }

    var saved by remember { mutableStateOf(settings.timeout) }
    var selected by rememberSaveable { mutableStateOf(saved) }
    var pendingConfirm by rememberSaveable { mutableStateOf<LockTimeout?>(null) }
    var status by remember { mutableStateOf(DeviceStatus.read(context)) }

    // Permissions are granted in other screens (or over adb), so re-check whenever we come back.
    LifecycleResumeEffect(Unit) {
        status = DeviceStatus.read(context)
        onPauseOrDispose { }
    }

    fun persist(timeout: LockTimeout?) {
        settings.timeout = timeout
        saved = timeout
        selected = timeout
    }

    fun confirmSave(timeout: LockTimeout) {
        persist(timeout)
        val message = if (status.lockServiceEnabled) {
            "All set. Your device will lock after ${timeout.spokenLabel} of inactivity."
        } else {
            "Saved. Allow ForceLock in Accessibility settings to start locking."
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

    fun copyAdbCommand() {
        val clipboard = context.getSystemService(ClipboardManager::class.java)
        clipboard.setPrimaryClip(ClipData.newPlainText("adb command", adbCommand(context)))
        // Android 13+ shows its own "Copied" confirmation.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            scope.launch { snackbar.showSnackbar("Command copied.") }
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
                if (status.lockServiceEnabled) {
                    DetectionCard(
                        fullDetection = status.systemActivityAvailable,
                        adbCommand = adbCommand(context),
                        onCopy = ::copyAdbCommand,
                    )
                }
            }
            val picker: @Composable () -> Unit = {
                TimePicker(
                    selected = selected,
                    saved = saved,
                    onSelect = { selected = it },
                )
                SaveButton(
                    selected = selected,
                    saved = saved,
                    onSave = { pendingConfirm = selected },
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

    pendingConfirm?.let { timeout ->
        ConfirmDialog(
            timeout = timeout,
            previous = saved,
            onConfirm = {
                pendingConfirm = null
                confirmSave(timeout)
            },
            onDismiss = { pendingConfirm = null },
        )
    }
}

@Composable
private fun ScrollingColumn(modifier: Modifier, content: @Composable () -> Unit) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) { content() }
}

private fun adbCommand(context: Context) =
    "adb shell pm grant ${context.packageName} android.permission.DUMP"
