package com.mariotatis.forcelock

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioManager
import android.service.media.MediaBrowserService
import java.util.concurrent.ConcurrentHashMap

/**
 * Whether the app in front is playing a video (or other media), so the device should stay awake.
 *
 * Two things must hold:
 *  - The app is a media app: it handles media buttons, offers a media browser, or calls itself a
 *    video or audio app (YouTube, Netflix, browsers, music players). Anything that calls itself a
 *    game never counts, and neither ES-DE nor any emulator declares media buttons.
 *  - A media player is running, not paused.
 * Android shares both without a permission. Players aren't tied to an app this way, which is why
 * the app in front has to be a media app.
 */
class MediaPlayback(private val context: Context) {

    private val mediaApps = ConcurrentHashMap<String, Boolean>()

    fun isPlayingIn(packageName: String?): Boolean =
        packageName != null && isMediaApp(packageName) && isAnyPlayerRunning()

    private fun isMediaApp(packageName: String): Boolean = mediaApps.getOrPut(packageName) {
        val pm = context.packageManager
        val info = try {
            pm.getApplicationInfo(packageName, 0)
        } catch (_: PackageManager.NameNotFoundException) {
            return@getOrPut false
        }
        @Suppress("DEPRECATION") // FLAG_IS_GAME is how older apps still mark themselves.
        val isGame = info.category == ApplicationInfo.CATEGORY_GAME ||
            info.flags and ApplicationInfo.FLAG_IS_GAME != 0
        when {
            isGame -> false
            info.category == ApplicationInfo.CATEGORY_VIDEO ||
                info.category == ApplicationInfo.CATEGORY_AUDIO -> true
            else -> handles(pm, packageName)
        }
    }

    private fun handles(pm: PackageManager, packageName: String): Boolean {
        val mediaButton = Intent(Intent.ACTION_MEDIA_BUTTON).setPackage(packageName)
        val browser = Intent(MediaBrowserService.SERVICE_INTERFACE).setPackage(packageName)
        @Suppress("DEPRECATION") // The flags overloads need API 33.
        return pm.queryBroadcastReceivers(mediaButton, 0).isNotEmpty() ||
            pm.queryIntentServices(browser, 0).isNotEmpty()
    }

    private fun isAnyPlayerRunning(): Boolean {
        val audio = context.getSystemService(AudioManager::class.java) ?: return false
        return audio.activePlaybackConfigurations.any { config ->
            config.audioAttributes.usage == AudioAttributes.USAGE_MEDIA &&
                // A paused video keeps its player, so its state matters. Android only exposes
                // it in the description; if that changes, fall back to "media is playing".
                (parsePlayerState(config.toString())?.let { it == "started" } ?: audio.isMusicActive)
        }
    }

    companion object {
        private val PLAYER_STATE = Regex("""\bstate:(\w+)""")

        fun parsePlayerState(description: String): String? =
            PLAYER_STATE.find(description)?.groupValues?.get(1)
    }
}
