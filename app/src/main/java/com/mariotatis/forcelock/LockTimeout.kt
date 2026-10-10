package com.mariotatis.forcelock

/** The auto-lock delays offered in the UI. */
enum class LockTimeout(val millis: Long, val shortLabel: String, val spokenLabel: String) {
    S15(15_000, "15s", "15 seconds"),
    S30(30_000, "30s", "30 seconds"),
    M1(60_000, "1m", "1 minute"),
    M3(3 * 60_000, "3m", "3 minutes"),
    M5(5 * 60_000, "5m", "5 minutes"),
    M10(10 * 60_000, "10m", "10 minutes"),
    M15(15 * 60_000, "15m", "15 minutes"),
    M20(20 * 60_000, "20m", "20 minutes");

    companion object {
        /**
         * The offered delay closest to [millis], or null for 0 (off). Saved values from older
         * versions, such as 10 seconds or 1 hour, move to the nearest option instead of
         * silently turning auto-lock off.
         */
        fun fromMillis(millis: Long): LockTimeout? {
            if (millis <= 0L) return null
            return entries.minBy { kotlin.math.abs(it.millis - millis) }
        }
    }
}
