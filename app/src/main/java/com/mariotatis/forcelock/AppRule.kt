package com.mariotatis.forcelock

/**
 * A per-app override of the main lock time, applied while that app is in front.
 * [timeout] `null` means never lock in this app. With [waitForDownloads], the lock is held off
 * while the device is steadily downloading (ES-DE scraping, for example).
 */
data class AppRule(val timeout: LockTimeout?, val waitForDownloads: Boolean = false) {

    companion object {
        // Package names can only contain letters, digits, '_' and '.', so this never collides.
        private const val SEPARATOR = '|'

        fun encode(rules: Map<String, AppRule>): Set<String> =
            rules.mapTo(HashSet()) { (packageName, rule) ->
                listOf(
                    packageName,
                    rule.timeout?.millis ?: 0L,
                    if (rule.waitForDownloads) 1 else 0,
                ).joinToString(SEPARATOR.toString())
            }

        /** Skips entries it can't read. Retired lock times move to the nearest one offered. */
        fun decode(entries: Set<String>): Map<String, AppRule> =
            entries.mapNotNull { entry ->
                val parts = entry.split(SEPARATOR)
                if (parts.size != 3 || parts[0].isEmpty()) return@mapNotNull null
                val millis = parts[1].toLongOrNull() ?: return@mapNotNull null
                val timeout = if (millis == 0L) null else LockTimeout.fromMillis(millis) ?: return@mapNotNull null
                parts[0] to AppRule(timeout, waitForDownloads = parts[2] == "1")
            }.toMap()
    }
}
