package com.mariotatis.forcelock

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

/**
 * The persisted settings: how long the device may sit idle before it's locked, plus optional
 * per-app overrides. A `null` [timeout] means auto-lock is off, overrides included. The UI and
 * [AutoLockService] share one process, so the service picks up changes through a
 * [SharedPreferences] listener.
 */
class AutoLockSettings(context: Context) {

    val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var timeout: LockTimeout?
        get() = LockTimeout.fromMillis(prefs.getLong(KEY_TIMEOUT_MS, 0L))
        set(value) = prefs.edit { putLong(KEY_TIMEOUT_MS, value?.millis ?: 0L) }

    /** Per-app overrides, keyed by package name. */
    var appRules: Map<String, AppRule>
        get() = AppRule.decode(prefs.getStringSet(KEY_APP_RULES, null).orEmpty())
        set(value) = prefs.edit { putStringSet(KEY_APP_RULES, AppRule.encode(value)) }

    companion object {
        const val KEY_TIMEOUT_MS = "timeout_ms"
        const val KEY_APP_RULES = "app_rules"
        private const val PREFS_NAME = "auto_lock"
    }
}
