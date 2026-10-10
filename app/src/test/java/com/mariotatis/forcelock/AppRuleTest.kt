package com.mariotatis.forcelock

import org.junit.Assert.assertEquals
import org.junit.Test

class AppRuleTest {

    @Test
    fun roundTripsRules() {
        val rules = mapOf(
            "org.es_de.frontend" to AppRule(LockTimeout.M5, waitForDownloads = true),
            "com.retroarch" to AppRule(LockTimeout.M20),
            "com.video.player" to AppRule(timeout = null),
        )

        assertEquals(rules, AppRule.decode(AppRule.encode(rules)))
    }

    @Test
    fun skipsUnreadableEntries() {
        val decoded = AppRule.decode(
            setOf("ok.app|60000|0", "bad.time|abc|0", "missing.parts|60000", "|60000|0"),
        )

        assertEquals(mapOf("ok.app" to AppRule(LockTimeout.M1)), decoded)
    }

    @Test
    fun retiredTimesMoveToNearestOption() {
        val decoded = AppRule.decode(setOf("old.short|10000|0", "old.long|3600000|1"))

        assertEquals(
            mapOf(
                "old.short" to AppRule(LockTimeout.S15),
                "old.long" to AppRule(LockTimeout.M20, waitForDownloads = true),
            ),
            decoded,
        )
    }
}
