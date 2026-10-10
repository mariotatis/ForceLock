package com.mariotatis.forcelock

import android.net.TrafficStats

/**
 * Tells whether the device is steadily downloading, from the total bytes it has received.
 * [TrafficStats] needs no permission, so ForceLock still never touches the network itself.
 *
 * Each call compares against the previous sample, so calling it every [SAMPLE_INTERVAL_MS]
 * measures the rate over roughly that window.
 */
class DownloadMeter(private val readTotalBytes: () -> Long = TrafficStats::getTotalRxBytes) {

    private var sampleBytes = -1L
    private var sampleUptime = 0L

    /**
     * `true` if at least [MIN_BYTES_PER_SECOND] arrived since the last sample. `null` when there
     * isn't a usable sample yet: ask again after [MIN_SPAN_MS].
     */
    fun poll(nowUptime: Long): Boolean? {
        val bytes = readTotalBytes()
        if (bytes < 0) return false // Not supported on this device.

        val span = nowUptime - sampleUptime
        if (sampleBytes < 0 || span > MAX_SPAN_MS || bytes < sampleBytes) {
            // First sample, a stale one, or the counters were reset.
            sampleBytes = bytes
            sampleUptime = nowUptime
            return null
        }
        if (span < MIN_SPAN_MS) return null

        val bytesPerSecond = (bytes - sampleBytes) * 1000 / span
        sampleBytes = bytes
        sampleUptime = nowUptime
        return bytesPerSecond >= MIN_BYTES_PER_SECOND
    }

    companion object {
        const val SAMPLE_INTERVAL_MS = 30_000L
        const val MIN_SPAN_MS = 5_000L
        private const val MAX_SPAN_MS = 2 * SAMPLE_INTERVAL_MS

        // About 60 KB a minute. Scraping images and videos is far above this; a device sitting
        // on a menu, with only background chatter, stays well below it.
        const val MIN_BYTES_PER_SECOND = 1024L
    }
}
