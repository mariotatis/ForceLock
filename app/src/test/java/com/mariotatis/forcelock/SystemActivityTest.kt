package com.mariotatis.forcelock

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SystemActivityTest {

    @Test
    fun picksMostRecentActivityAcrossFormats() {
        val output = """
            Power Manager State:
              mLastUserActivityTime=905000 (12s 100ms ago)
              mLastUserActivityTimeNoChangeLights=0 (--)
              mUserActivityTimeoutOverrideFromWindowManager=-1
            PowerGroup 0:
              lastUserActivityTime=910250 (6s 850ms ago)
        """.trimIndent()

        assertEquals(910_250L, SystemActivity.parseLastUserActivity(output, nowUptime = 917_100))
    }

    @Test
    fun readsAndroid13PowerGroupFormat() {
        // Captured from an AYN Odin 2 Portal (Android 13).
        val output = """
             mLastUserActivityTime(excludingAttention)=212838245
              lastUserActivityTime=212838245 (24653 ms ago)
              lastUserActivityTimeNoChangeLights=212825024 (37874 ms ago)
        """.trimIndent()

        assertEquals(212_838_245L, SystemActivity.parseLastUserActivity(output, nowUptime = 212_862_898))
    }

    @Test
    fun ignoresTimeoutsAndFutureValues() {
        val output = """
              mUserActivityTimeoutOverrideFromWindowManager=60000
              mLastUserActivityTime=999999
        """.trimIndent()

        assertNull(SystemActivity.parseLastUserActivity(output, nowUptime = 5_000))
    }

    @Test
    fun returnsNullWhenPermissionDenied() {
        val output = "Permission Denial: can't dump PowerManager from pid=1, uid=10200"

        assertNull(SystemActivity.parseLastUserActivity(output, nowUptime = 5_000))
    }
}
