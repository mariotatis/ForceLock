package com.mariotatis.forcelock

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DownloadMeterTest {

    private var bytes = 0L
    private val meter = DownloadMeter { bytes }

    @Test
    fun needsTwoSamplesSpacedApart() {
        assertNull(meter.poll(nowUptime = 100_000))
        bytes += 1_000_000
        assertNull(meter.poll(nowUptime = 102_000)) // Too soon to judge.
        assertEquals(true, meter.poll(nowUptime = 110_000))
    }

    @Test
    fun steadyDownloadKeepsItAwake() {
        meter.poll(nowUptime = 0)
        bytes += 30 * 50_000 // 50 KB/s, like scraping.
        assertEquals(true, meter.poll(nowUptime = 30_000))
    }

    @Test
    fun backgroundTrickleDoesNot() {
        meter.poll(nowUptime = 0)
        bytes += 30 * 200 // 200 B/s of background chatter.
        assertEquals(false, meter.poll(nowUptime = 30_000))
    }

    @Test
    fun staleSampleStartsOver() {
        meter.poll(nowUptime = 0)
        bytes += 10_000_000
        assertNull(meter.poll(nowUptime = 10 * 60_000))
    }

    @Test
    fun unsupportedCounterMeansNotDownloading() {
        bytes = -1
        assertEquals(false, meter.poll(nowUptime = 0))
    }
}
