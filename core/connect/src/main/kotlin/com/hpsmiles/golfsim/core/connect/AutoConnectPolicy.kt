// core/connect/src/main/kotlin/com/hpsmiles/golfsim/core/connect/AutoConnectPolicy.kt
package com.hpsmiles.golfsim.core.connect

/**
 * Pure decision machine for the auto-connect-on-boot cycle (spec
 * docs/superpowers/specs/2026-09-30-auto-connect-boot-design.md §2). No
 * Android imports; every event returns the single [Action] the caller should
 * perform, or null when the event must be ignored (stopped machine, state
 * guard). Deterministic: same event sequence → same action sequence.
 *
 * Cycle: an immediate first attempt ([Action.StartAttempt]), then up to
 * [maxRetries] retries with growing backoff ([Action.WaitThenAttempt]). A
 * newly established link emits [Action.Arm] and resets the retry budget, so
 * a later link drop starts a fresh cycle. DISCONNECT stops the machine until
 * the next manual connect. Failure sequence: attempt 1 (immediate), then
 * waits 5 s → 10 s → 20 s before retries 1–3; the 4th failure gives up.
 */
class AutoConnectPolicy(
    val maxRetries: Int = DEFAULT_MAX_RETRIES,
    private val backoffMs: List<Long> = DEFAULT_BACKOFF_MS,
) {
    sealed interface Action {
        /** Run one scan+connect attempt immediately. */
        data object StartAttempt : Action

        /** Wait [delayMs], then run attempt [retryNumber] (1-based). */
        data class WaitThenAttempt(val retryNumber: Int, val delayMs: Long) : Action

        /** Link established — arm the device (client auto-arms too; belt). */
        data object Arm : Action

        /** Retry budget exhausted — surface BLE DISCONNECTED, stop cycling. */
        data object GiveUp : Action

        /** User disconnect — no further retries until a connect event. */
        data object Stop : Action
    }

    /** True after [userDisconnect] until the next connect/start event. */
    var stopped = false
        private set

    private var retriesLeft = maxRetries
    private var established = false
    private var givenUp = false

    /** Boot or app-open trigger. */
    fun start(): Action = begin()

    /** Manual CONNECT chip / permission-grant trigger. Fresh cycle. */
    fun manualConnect(): Action = begin()

    /** Scan timeout, connect fault or handshake fault before establishment. */
    fun attemptFailed(): Action? {
        if (stopped || established || givenUp) return null
        return retryOrGiveUp()
    }

    /** Handshake completed (client reached Armed/Disarmed). */
    fun linkEstablished(): Action? {
        if (stopped || established) return null
        established = true
        givenUp = false
        retriesLeft = maxRetries
        return Action.Arm
    }

    /** A previously-established link was lost (drop or fault). */
    fun linkDropped(): Action? {
        if (stopped || !established) return null
        established = false
        retriesLeft = maxRetries
        return retryOrGiveUp()
    }

    /** DISCONNECT chip — cancel cycling; manualConnect() restarts. */
    fun userDisconnect(): Action {
        stopped = true
        established = false
        return Action.Stop
    }

    private fun begin(): Action {
        stopped = false
        established = false
        givenUp = false
        retriesLeft = maxRetries
        return Action.StartAttempt
    }

    private fun retryOrGiveUp(): Action? {
        if (retriesLeft <= 0) {
            givenUp = true
            return Action.GiveUp
        }
        val n = maxRetries - retriesLeft + 1
        retriesLeft--
        val delayMs = backoffMs[(n - 1).coerceAtMost(backoffMs.lastIndex)]
        return Action.WaitThenAttempt(n, delayMs)
    }

    companion object {
        const val DEFAULT_MAX_RETRIES = 3
        val DEFAULT_BACKOFF_MS = listOf(5_000L, 10_000L, 20_000L)
    }
}
