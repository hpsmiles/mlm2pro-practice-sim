package com.hpsmiles.golfsim.range

/**
 * Display-only translation of connection-failure reasons into short
 * uppercase status-strip phrases (spec 2026-09-30 §4). Raw reasons are
 * load-bearing in core (`ConnectionState.Faulted.reason`), logcat and
 * capture exports — this layer never rewrites them.
 */
object ConnectFailureHints {
    /** Wiring sentinel: the 10 s scan budget expired with no advertisement. */
    const val SCAN_TIMEOUT = "scan-timeout"

    /**
     * Map a raw failure reason (`Faulted.reason` or [SCAN_TIMEOUT]) to a
     * strip phrase. Unknown input falls back to `CONNECT FAILED: <raw>`;
     * blank input to bare `CONNECT FAILED`.
     */
    fun phrase(raw: String): String = when {
        raw == SCAN_TIMEOUT -> "NO MONITOR FOUND"
        raw == "gatt 8" -> "MONITOR NOT RESPONDING (GATT 8)"
        raw == "gatt 19" -> "MONITOR CLOSED LINK (GATT 19)"
        raw == "gatt 133" -> "CONNECT REJECTED (GATT 133)"
        raw.startsWith("gatt ") -> "LINK ERROR (${raw.uppercase()})"
        raw.startsWith("service discovery ") || raw == "discoverServices rejected" ->
            "SERVICE DISCOVERY FAILED"
        raw == "connectGatt returned null" -> "BLUETOOTH STACK REFUSED"
        raw.startsWith("token fetch: ") -> "TOKEN FETCH FAILED: ${raw.removePrefix("token fetch: ")}"
        raw == "SecurityException" -> "BLUETOOTH PERMISSION MISSING"
        raw.isBlank() -> "CONNECT FAILED"
        else -> "CONNECT FAILED: $raw"
    }
}
