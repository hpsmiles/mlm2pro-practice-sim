package com.hpsmiles.golfsim.core.connect

/**
 * Pure handshake/arbitrary-session state machine for the MLM2PRO protocol.
 * Zero Android imports; all timing is driven by the caller-supplied
 * `nowMs` clock, so tests run on virtual time with no sleeping.
 *
 * State flow:
 * IDLE -> (subscriptions complete) AUTH_SENT -> (WRITE_RESPONSE 0x02)
 * TOKEN_WAIT -> (token fetched) CONFIG_WRITE_1 -> (+200 ms) READY
 * -> arm/disarm at will; heartbeat every 2 s while connected.
 */
class HandshakeSequencer(
    private val key: ByteArray,
    private val config: EnvironmentConfig,
) {
    var state: HandshakeState = HandshakeState.IDLE
        private set

    /** User id parsed from the WRITE_RESPONSE; -1 until authed. */
    var userId: Int = -1
        private set

    var lastNotificationAtMs: Long = Long.MIN_VALUE
        private set

    private var token = 0
    private var config1AtMs = Long.MIN_VALUE
    private var lastHeartbeatAtMs = Long.MIN_VALUE

    fun onSubscriptionsComplete(nowMs: Long): WriteCommand {
        state = HandshakeState.AUTH_SENT
        return CommandEncoder.authRequest(key)
    }

    /** Feed the decrypted WRITE_RESPONSE payload. Returns null (no write needed). */
    fun onWriteResponse(payload: ByteArray, nowMs: Long) {
        if (state != HandshakeState.AUTH_SENT) return
        if (payload.isEmpty() || payload[0] != 0x02.toByte()) {
            state = HandshakeState.FAULTED
            return
        }
        userId = (payload[2].toInt() and 0xFF) or
            ((payload[3].toInt() and 0xFF) shl 8) or
            ((payload[4].toInt() and 0xFF) shl 16) or
            ((payload[5].toInt() and 0xFF) shl 24)
        state = HandshakeState.TOKEN_WAIT
    }

    /** Token fetched from the Rapsodo API; returns the first CONFIGURE write. */
    fun onToken(token: Int, nowMs: Long): WriteCommand {
        check(state == HandshakeState.TOKEN_WAIT) { "token arrives in TOKEN_WAIT, was $state" }
        this.token = token
        state = HandshakeState.CONFIG_WRITE_1
        config1AtMs = nowMs
        return CommandEncoder.config(config, token, key)
    }

    fun onTokenFailed(nowMs: Long) {
        if (state == HandshakeState.TOKEN_WAIT) state = HandshakeState.FAULTED
    }

    /** Time-driven writes: CONFIGURE #2 after the 200 ms gap; heartbeats every 2 s. */
    fun poll(nowMs: Long): List<WriteCommand> {
        val out = mutableListOf<WriteCommand>()
        if (state == HandshakeState.CONFIG_WRITE_1 && nowMs - config1AtMs >= CONFIG_GAP_MS) {
            state = HandshakeState.READY
            lastHeartbeatAtMs = nowMs
            out += CommandEncoder.config(config, token, key)
        }
        if (state == HandshakeState.READY || state == HandshakeState.ARMED || state == HandshakeState.DISARMED) {
            if (nowMs - lastHeartbeatAtMs >= HEARTBEAT_PERIOD_MS) {
                lastHeartbeatAtMs = nowMs
                out += CommandEncoder.heartbeat(key)
            }
        }
        return out
    }

    fun arm(nowMs: Long): WriteCommand? {
        if (state != HandshakeState.READY && state != HandshakeState.DISARMED) return null
        state = HandshakeState.ARMED
        return CommandEncoder.arm(key)
    }

    fun disarm(nowMs: Long): WriteCommand? {
        if (state != HandshakeState.ARMED) return null
        state = HandshakeState.DISARMED
        return CommandEncoder.disarm(key)
    }

    /** The device stops notifying after ~20 s of missed resubscription. */
    fun isResubscribeDue(nowMs: Long): Boolean =
        lastNotificationAtMs != Long.MIN_VALUE && nowMs - lastNotificationAtMs >= RESUBSCRIBE_PERIOD_MS

    /** Call for EVERY notification received; refreshes the resubscribe window. */
    fun onNotification(nowMs: Long) {
        lastNotificationAtMs = nowMs
    }

    companion object {
        const val CONFIG_GAP_MS = 200L
        const val HEARTBEAT_PERIOD_MS = 2_000L
        const val RESUBSCRIBE_PERIOD_MS = 20_000L
    }
}

enum class HandshakeState {
    IDLE, AUTH_SENT, TOKEN_WAIT, CONFIG_WRITE_1, READY, ARMED, DISARMED, FAULTED
}
