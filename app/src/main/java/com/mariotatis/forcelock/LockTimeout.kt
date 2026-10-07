package com.mariotatis.forcelock

/** The auto-lock delays offered in the UI. */
enum class LockTimeout(val millis: Long, val shortLabel: String, val spokenLabel: String) {
    S10(10_000, "10s", "10 seconds"),
    S30(30_000, "30s", "30 seconds"),
    M1(60_000, "1m", "1 minute"),
    M5(5 * 60_000, "5m", "5 minutes"),
    M10(10 * 60_000, "10m", "10 minutes"),
    M15(15 * 60_000, "15m", "15 minutes"),
    M30(30 * 60_000, "30m", "30 minutes"),
    H1(60 * 60_000, "1h", "1 hour");

    companion object {
        fun fromMillis(millis: Long): LockTimeout? = entries.firstOrNull { it.millis == millis }
    }
}
