package com.mariotatis.forcelock

import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings

/** Snapshot of what ForceLock is allowed to do right now. */
data class DeviceStatus(
    val lockServiceEnabled: Boolean,
    val systemActivityAvailable: Boolean,
) {
    companion object {
        fun read(context: Context) = DeviceStatus(
            lockServiceEnabled = isLockServiceEnabled(context),
            systemActivityAvailable = SystemActivity.isAvailable(context),
        )

        private fun isLockServiceEnabled(context: Context): Boolean {
            val enabled = Settings.Secure.getString(
                context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
            ) ?: return false
            val ours = serviceComponent(context)
            return enabled.split(':').any { ComponentName.unflattenFromString(it) == ours }
        }

        private fun serviceComponent(context: Context) =
            ComponentName(context, AutoLockService::class.java)

        /**
         * Opens the Accessibility settings list, or Settings itself if that's unavailable.
         * Deep-linking to ForceLock's own page needs a system-only permission on Android 13+.
         */
        fun openLockServiceSettings(context: Context) {
            context.tryStart(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) ||
                context.tryStart(Intent(Settings.ACTION_SETTINGS))
        }

        fun openAppInfo(context: Context) {
            context.tryStart(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                    .setData(Uri.fromParts("package", context.packageName, null)),
            )
        }

        private fun Context.tryStart(intent: Intent): Boolean = try {
            startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            true
        } catch (_: ActivityNotFoundException) {
            false
        } catch (_: SecurityException) {
            false
        }
    }
}
