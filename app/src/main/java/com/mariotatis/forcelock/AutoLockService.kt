package com.mariotatis.forcelock

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.PixelFormat
import android.os.Handler
import android.os.HandlerThread
import android.os.PowerManager
import android.os.SystemClock
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import androidx.core.content.ContextCompat

/**
 * Watches for user inactivity and locks the device exactly like the power button does
 * ([GLOBAL_ACTION_LOCK_SCREEN]), regardless of which app or launcher is in front.
 *
 * Activity is detected without any extra setup:
 *  - Touches anywhere, through an invisible 1px overlay that watches outside touches.
 *  - Key events (all controller buttons), plus a few interaction events.
 * If DUMP has been granted over adb, the system's own activity clock ([SystemActivity]) is used
 * as well, which also sees analog sticks. Keys are then no longer filtered, so button presses
 * don't pay an extra hop when it isn't needed.
 *
 * The lock time comes from the app in front: its own [AppRule] if it has one, otherwise the main
 * setting. A rule can also hold the lock while the device is downloading ([DownloadMeter]).
 * Media playing in the app in front ([MediaPlayback]), like a YouTube video, counts as activity,
 * so the timer starts when it's paused or ends.
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
    private var touchWatcher: View? = null

    @Volatile
    private var appRules: Map<String, AppRule> = emptyMap()

    @Volatile
    private var foregroundPackage: String? = null
    private val activityLookups = HashMap<ComponentName, Boolean>()
    private val downloadMeter = DownloadMeter()
    private val mediaPlayback by lazy { MediaPlayback(this) }

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
        when (key) {
            AutoLockSettings.KEY_TIMEOUT_MS -> restartTimer()
            AutoLockSettings.KEY_APP_RULES -> {
                appRules = settings.appRules
                reschedule()
            }
        }
    }

    override fun onServiceConnected() {
        settings = AutoLockSettings(this)
        worker = HandlerThread("ForceLock").apply { start() }
        handler = Handler(worker.looper)
        appRules = settings.appRules

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
        addTouchWatcher()
        restartTimer()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            // Switching apps isn't activity by itself; it only changes which lock time applies.
            val app = foregroundAppOf(event) ?: return
            if (app != foregroundPackage) {
                foregroundPackage = app
                reschedule()
            }
            return
        }
        // With the system clock available these are redundant, and some (auto-scrolling
        // carousels) aren't really the user — so only trust them as a fallback.
        if (filteringKeys == true) markActivity()
    }

    /**
     * The app whose activity just came to the front, or null for windows that don't replace it:
     * dialogs, the notification shade, the keyboard. Only the event's package and class are used,
     * never what's on screen.
     */
    private fun foregroundAppOf(event: AccessibilityEvent): String? {
        val packageName = event.packageName?.toString() ?: return null
        val className = event.className?.toString() ?: return null
        if (packageName == SYSTEM_UI || packageName == currentKeyboardPackage()) return null
        val component = ComponentName(packageName, className)
        val isActivity = activityLookups.getOrPut(component) {
            try {
                packageManager.getActivityInfo(component, 0)
                true
            } catch (_: PackageManager.NameNotFoundException) {
                // Either a non-activity window, or an app Android hides from us. Hidden apps
                // can't have a rule anyway, so let them count and fall back to the main time.
                !isPackageVisible(packageName)
            }
        }
        return if (isActivity) packageName else null
    }

    private fun isPackageVisible(packageName: String) = try {
        packageManager.getPackageInfo(packageName, 0)
        true
    } catch (_: PackageManager.NameNotFoundException) {
        false
    }

    private fun currentKeyboardPackage(): String? =
        Settings.Secure.getString(contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD)
            ?.substringBefore('/')

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
        touchWatcher?.let { view ->
            runCatching { getSystemService(WindowManager::class.java).removeView(view) }
        }
        touchWatcher = null
        handler.removeCallbacksAndMessages(null)
        worker.quitSafely()
    }

    /**
     * A 1x1 transparent overlay with FLAG_WATCH_OUTSIDE_TOUCH: Android tells it about every touch
     * that starts anywhere else on screen, including inside games. Accessibility overlays need no
     * extra permission and are trusted, so the touch still reaches the app underneath.
     */
    @Suppress("ClickableViewAccessibility")
    private fun addTouchWatcher() {
        val view = View(this).apply {
            setOnTouchListener { _, _ ->
                markActivity()
                false
            }
        }
        val params = WindowManager.LayoutParams(
            1,
            1,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSPARENT,
        ).apply { gravity = Gravity.TOP or Gravity.START }
        runCatching { getSystemService(WindowManager::class.java).addView(view, params) }
            .onSuccess { touchWatcher = view }
            .onFailure { Log.w(TAG, "Touch watcher unavailable", it) }
    }

    private fun markActivity() {
        lastActivityUptime = SystemClock.uptimeMillis()
    }

    private fun restartTimer() {
        if (!::handler.isInitialized) return
        markActivity()
        reschedule()
    }

    /** Re-evaluates now, keeping the idle time so far. */
    private fun reschedule() {
        if (!::handler.isInitialized) return
        handler.removeCallbacks(checkRunnable)
        handler.post(checkRunnable)
    }

    private fun check() {
        val mainTimeout = settings.timeout ?: return
        val rule = foregroundPackage?.let { appRules[it] }
        // Never lock in this app; switching apps runs the check again.
        val timeout = if (rule != null) rule.timeout ?: return else mainTimeout
        val waitForDownloads = rule?.waitForDownloads == true
        val systemClockAvailable = SystemActivity.isAvailable(this)
        updateKeyFiltering(enabled = !systemClockAvailable)

        if (!isScreenInteractive()) return // resumes on SCREEN_ON
        if (isKeyguardLocked()) {
            // Poll instead of waiting for USER_PRESENT, so a missed broadcast can never stall us.
            handler.postDelayed(checkRunnable, KEYGUARD_POLL_MS)
            return
        }

        if (mediaPlayback.isPlayingIn(foregroundPackage)) {
            Log.d(TAG, "Media playing in $foregroundPackage, staying awake")
            markActivity()
            handler.postDelayed(checkRunnable, minOf(timeout.millis, MEDIA_POLL_MS))
            return
        }

        val systemLast = if (systemClockAvailable) SystemActivity.lastUserActivityUptime() else null
        val lastActivity = maxOf(lastActivityUptime, systemLast ?: 0L)
        val idle = SystemClock.uptimeMillis() - lastActivity

        // Sampled along the way, so a fresh rate is ready when the time is up.
        val downloading = if (waitForDownloads) downloadMeter.poll(SystemClock.uptimeMillis()) else false

        if (idle >= timeout.millis) {
            when (downloading) {
                true -> {
                    handler.postDelayed(checkRunnable, DownloadMeter.SAMPLE_INTERVAL_MS)
                    return
                }
                null -> {
                    handler.postDelayed(checkRunnable, DownloadMeter.MIN_SPAN_MS)
                    return
                }
                false -> Unit
            }
            Log.i(TAG, "Idle for ${idle}ms (limit ${timeout.millis}ms), locking")
            if (!performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN)) {
                Log.w(TAG, "Lock action was rejected, retrying shortly")
                handler.postDelayed(checkRunnable, RETRY_DELAY_MS)
            }
        } else {
            val remaining = timeout.millis - idle
            val delay = if (waitForDownloads) minOf(remaining, DownloadMeter.SAMPLE_INTERVAL_MS) else remaining
            handler.postDelayed(checkRunnable, delay)
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
        const val MEDIA_POLL_MS = 10_000L
        const val SYSTEM_UI = "com.android.systemui"
    }
}
