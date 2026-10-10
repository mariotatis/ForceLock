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
 * Whether a video (or other media) is playing, so the device should stay awake.
 *
 * Any running player tagged as a movie counts, whatever app it's in: that tag means video, and
 * games and ES-DE don't use it. Players tagged otherwise (YouTube tags its videos as music) count
 * when two things hold:
 *  - The app is a media app: it handles media buttons, offers a media browser, calls itself a
 *    video or audio app, or has a screen that supports picture-in-picture (Netflix declares only
 *    that). Anything that calls itself a game never counts, even with picture-in-picture, and
 *    neither ES-DE nor the emulators declare any of these.
 *  - A media player is running, not paused.
 * Android shares both without a permission. Players aren't tied to an app this way, which is why
 * the app in front has to be a media app.
 */
class MediaPlayback(private val context: Context) {

    private val mediaApps = ConcurrentHashMap<String, Boolean>()

    fun isPlayingIn(packageName: String?): Boolean {
        val players = runningMediaPlayers()
        if (players.any { it.contentType == AudioAttributes.CONTENT_TYPE_MOVIE }) return true
        return players.isNotEmpty() && packageName != null && isMediaApp(packageName)
    }

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
            else -> handles(pm, packageName) || supportsPictureInPicture(pm, packageName)
        }
    }

    private fun supportsPictureInPicture(pm: PackageManager, packageName: String): Boolean {
        val activities = try {
            @Suppress("DEPRECATION") // The flags overload needs API 33.
            pm.getPackageInfo(packageName, PackageManager.GET_ACTIVITIES).activities
        } catch (_: PackageManager.NameNotFoundException) {
            null
        }
        return activities.orEmpty().any { it.flags and FLAG_SUPPORTS_PICTURE_IN_PICTURE != 0 }
    }

    private fun handles(pm: PackageManager, packageName: String): Boolean {
        val mediaButton = Intent(Intent.ACTION_MEDIA_BUTTON).setPackage(packageName)
        val browser = Intent(MediaBrowserService.SERVICE_INTERFACE).setPackage(packageName)
        @Suppress("DEPRECATION") // The flags overloads need API 33.
        return pm.queryBroadcastReceivers(mediaButton, 0).isNotEmpty() ||
            pm.queryIntentServices(browser, 0).isNotEmpty()
    }

    /** Attributes of the media players that are playing right now, not paused. */
    private fun runningMediaPlayers(): List<AudioAttributes> {
        val audio = context.getSystemService(AudioManager::class.java) ?: return emptyList()
        return audio.activePlaybackConfigurations
            .filter { config ->
                config.audioAttributes.usage == AudioAttributes.USAGE_MEDIA &&
                    // A paused video keeps its player, so its state matters. Android only exposes
                    // it in the description; if that changes, fall back to "media is playing".
                    (parsePlayerState(config.toString())?.let { it == "started" } ?: audio.isMusicActive)
            }
            .map { it.audioAttributes }
    }

    companion object {
        /**
         * ActivityInfo.FLAG_SUPPORTS_PICTURE_IN_PICTURE: hidden from the SDK, but the bit has
         * meant picture-in-picture since Android 7, and the flags field itself is public.
         */
        private const val FLAG_SUPPORTS_PICTURE_IN_PICTURE = 0x400000

        private val PLAYER_STATE = Regex("""\bstate:(\w+)""")

        fun parsePlayerState(description: String): String? =
            PLAYER_STATE.find(description)?.groupValues?.get(1)
    }
}
