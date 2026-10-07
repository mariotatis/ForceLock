package com.mariotatis.forcelock

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

/**
 * The single persisted setting: how long the device may sit idle before it's locked.
 * `null` means auto-lock is off. The UI and [AutoLockService] share one process, so the service
 * picks up changes through a [SharedPreferences] listener.
 */
class AutoLockSettings(context: Context) {

    val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var timeout: LockTimeout?
        get() = LockTimeout.fromMillis(prefs.getLong(KEY_TIMEOUT_MS, 0L))
        set(value) = prefs.edit { putLong(KEY_TIMEOUT_MS, value?.millis ?: 0L) }

    companion object {
        const val KEY_TIMEOUT_MS = "timeout_ms"
        private const val PREFS_NAME = "auto_lock"
    }
}
