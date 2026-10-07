package com.mariotatis.forcelock

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.SystemClock
import java.util.concurrent.TimeUnit

/**
 * Reads the system's own "last user activity" timestamp from `dumpsys power`.
 *
 * PowerManagerService is told about every input event that reaches an app — taps, buttons,
 * analog sticks — even while the foreground app keeps the screen on. Reading it requires the
 * DUMP permission, which can only be granted over adb, so this is an optional upgrade.
 */
object SystemActivity {

    private val LAST_ACTIVITY = Regex("""(?i)lastUserActivityTime\w*=\s*(\d+)""")

    fun isAvailable(context: Context): Boolean =
        context.checkSelfPermission(Manifest.permission.DUMP) == PackageManager.PERMISSION_GRANTED

    /** Last user activity in [SystemClock.uptimeMillis] time, or null if it can't be read. */
    fun lastUserActivityUptime(): Long? {
        val output = try {
            val process = ProcessBuilder("dumpsys", "power").redirectErrorStream(true).start()
            val text = process.inputStream.bufferedReader().use { it.readText() }
            if (!process.waitFor(2, TimeUnit.SECONDS)) process.destroy()
            text
        } catch (_: Exception) {
            return null
        }
        return parseLastUserActivity(output, SystemClock.uptimeMillis())
    }

    /**
     * Picks the most recent `*LastUserActivityTime*=` value (older releases print one global
     * value, newer ones one per display group). Values from the future are ignored.
     */
    fun parseLastUserActivity(dumpsysOutput: String, nowUptime: Long): Long? =
        LAST_ACTIVITY.findAll(dumpsysOutput)
            .mapNotNull { it.groupValues[1].toLongOrNull() }
            .filter { it in 1..nowUptime }
            .maxOrNull()
}
