package com.hpsmiles.golfsim.core.data.record

import com.hpsmiles.golfsim.core.ble.BallData
import com.hpsmiles.golfsim.core.data.entity.ShotEntity
import com.hpsmiles.golfsim.core.physics.ShotResult

/** Raw-block view of a stored shot (source of truth, unknowns included). */
fun ShotEntity.toBallData(): BallData = BallData(
    clubHeadSpeed = clubHeadSpeedMps,
    ballSpeed = ballSpeedMps,
    launchDirection = launchDirDeg,
    launchAngle = launchAngleDeg,
    spinAxis = spinAxisDeg,
    totalSpin = spinRpm,
    unknown1 = unknown1,
    unknown2 = unknown2,
)

fun ShotEntity.toRecord(): ShotRecord = ShotRecord(
    id = id, sessionId = sessionId, seq = seq, timestampMs = timestampMs,
    source = if (source == ShotSource.DEMO.code) ShotSource.DEMO else ShotSource.LIVE,
    clubName = clubName, ballData = toBallData(),
    carryM = carryM, totalM = totalM, sideM = sideM,
    apexM = apexM, flightTimeSec = flightTimeSec,
    excluded = excluded, clubWasTemp = clubWasTemp,
)

/**
 * Insert-ready entity: raw BallData (source of truth) + the physics scalars
 * cached at capture time (hybrid storage decision D8). rolloutM is derived
 * on restore (totalM - carryM) and never persisted.
 */
fun makeShotEntity(
    sessionId: Long,
    seq: Int,
    timestampMs: Long,
    source: ShotSource,
    clubName: String?,
    ballData: BallData,
    result: ShotResult,
    clubWasTemp: Boolean = false,
): ShotEntity = ShotEntity(
    sessionId = sessionId, seq = seq, timestampMs = timestampMs,
    source = source.code, clubName = clubName,
    clubHeadSpeedMps = ballData.clubHeadSpeed, ballSpeedMps = ballData.ballSpeed,
    launchDirDeg = ballData.launchDirection, launchAngleDeg = ballData.launchAngle,
    spinAxisDeg = ballData.spinAxis, spinRpm = ballData.totalSpin,
    unknown1 = ballData.unknown1, unknown2 = ballData.unknown2,
    carryM = result.carryM, totalM = result.totalM, sideM = result.sideM,
    apexM = result.apexM, flightTimeSec = result.flightTimeSec,
    excluded = false, clubWasTemp = clubWasTemp,
)
