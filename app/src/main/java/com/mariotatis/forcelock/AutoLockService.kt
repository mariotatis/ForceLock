package com.mariotatis.forcelock

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.os.Handler
import android.os.HandlerThread
import android.os.PowerManager
import android.os.SystemClock
import android.util.Log
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import androidx.core.content.ContextCompat

/**
 * Watches for user inactivity and locks the device exactly like the power button does
 * ([GLOBAL_ACTION_LOCK_SCREEN]), regardless of which app or launcher is in front.
 *
 * Activity comes from one of two sources:
 *  - The system's own activity clock ([SystemActivity]) when DUMP has been granted. It sees every
 *    input, including touches inside games and analog sticks.
 *  - Otherwise, key events (all controller buttons) plus a few interaction events. Keys are only
 *    filtered in this mode so button presses don't pay an extra hop when it isn't needed.
 *
 * Timing is lazy: input just records a timestamp, and a single check runs when the timeout could
 * have elapsed, then reschedules itself for the remaining time.
 */
class AutoLockService : AccessibilityService() {

    private lateinit var settings: AutoLockSettings
    private lateinit var worker: HandlerThread
    private lateinit var handler: Handler

    @Volatile
    private var lastActivityUptime = SystemClock.uptimeMillis()
    private var filteringKeys: Boolean? = null

    private val checkRunnable = Runnable { check() }

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_SCREEN_OFF -> handler.removeCallbacks(checkRunnable)
                Intent.ACTION_SCREEN_ON, Intent.ACTION_USER_PRESENT -> restartTimer()
            }
        }
    }

    private val prefsListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == AutoLockSettings.KEY_TIMEOUT_MS) restartTimer()
    }

    override fun onServiceConnected() {
        settings = AutoLockSettings(this)
        worker = HandlerThread("ForceLock").apply { start() }
        handler = Handler(worker.looper)

        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_USER_PRESENT)
        }
        // Must be exported: USER_PRESENT is sent by SystemUI, not the system server, and a
        // non-exported receiver silently misses it. All three actions are protected broadcasts
        // that only the platform can send.
        ContextCompat.registerReceiver(
            this, screenReceiver, filter, null, handler, ContextCompat.RECEIVER_EXPORTED,
        )
        settings.prefs.registerOnSharedPreferenceChangeListener(prefsListener)
        restartTimer()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        // With the system clock available these are redundant, and some (auto-scrolling
        // carousels) aren't really the user — so only trust them as a fallback.
        if (filteringKeys == true) markActivity()
    }

    override fun onKeyEvent(event: KeyEvent): Boolean {
        markActivity()
        return false // never consume: the key still reaches the app
    }

    override fun onInterrupt() = Unit

    override fun onUnbind(intent: Intent?): Boolean {
        teardown()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        teardown()
        super.onDestroy()
    }

    private fun teardown() {
        if (!::worker.isInitialized || !worker.isAlive) return
        settings.prefs.unregisterOnSharedPreferenceChangeListener(prefsListener)
        runCatching { unregisterReceiver(screenReceiver) }
        handler.removeCallbacksAndMessages(null)
        worker.quitSafely()
    }

    private fun markActivity() {
        lastActivityUptime = SystemClock.uptimeMillis()
    }

    private fun restartTimer() {
        if (!::handler.isInitialized) return
        markActivity()
        handler.removeCallbacks(checkRunnable)
        handler.post(checkRunnable)
    }

    private fun check() {
        val timeout = settings.timeout ?: return
        val systemClockAvailable = SystemActivity.isAvailable(this)
        updateKeyFiltering(enabled = !systemClockAvailable)

        if (!isScreenInteractive()) return // resumes on SCREEN_ON
        if (isKeyguardLocked()) {
            // Poll instead of waiting for USER_PRESENT, so a missed broadcast can never stall us.
            handler.postDelayed(checkRunnable, KEYGUARD_POLL_MS)
            return
        }

        val systemLast = if (systemClockAvailable) SystemActivity.lastUserActivityUptime() else null
        val lastActivity = maxOf(lastActivityUptime, systemLast ?: 0L)
        val idle = SystemClock.uptimeMillis() - lastActivity

        if (idle >= timeout.millis) {
            Log.i(TAG, "Idle for ${idle}ms (limit ${timeout.millis}ms), locking")
            if (!performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN)) {
                Log.w(TAG, "Lock action was rejected, retrying shortly")
                handler.postDelayed(checkRunnable, RETRY_DELAY_MS)
            }
        } else {
            handler.postDelayed(checkRunnable, timeout.millis - idle)
        }
    }

    private fun updateKeyFiltering(enabled: Boolean) {
        if (filteringKeys == enabled) return
        val info = serviceInfo ?: return
        info.flags = if (enabled) {
            info.flags or AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS
        } else {
            info.flags and AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS.inv()
        }
        serviceInfo = info
        filteringKeys = enabled
    }

    private fun isScreenInteractive() = getSystemService(PowerManager::class.java).isInteractive

    private fun isKeyguardLocked() = getSystemService(KeyguardManager::class.java).isKeyguardLocked

    private companion object {
        const val TAG = "ForceLock"
        const val RETRY_DELAY_MS = 5_000L
        const val KEYGUARD_POLL_MS = 2_000L
    }
}
