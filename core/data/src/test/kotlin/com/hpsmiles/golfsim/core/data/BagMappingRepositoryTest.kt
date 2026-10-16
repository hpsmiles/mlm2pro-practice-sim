package com.hpsmiles.golfsim.core.data

import com.hpsmiles.golfsim.core.ble.BallData
import com.hpsmiles.golfsim.core.data.bag.BagMappingStats
import com.hpsmiles.golfsim.core.data.entity.BagMappingShotEntity
import com.hpsmiles.golfsim.core.data.entity.BagMappingStatus
import com.hpsmiles.golfsim.core.data.record.ClubRecord
import com.hpsmiles.golfsim.core.data.record.ClubType
import com.hpsmiles.golfsim.core.data.record.ShotSource
import com.hpsmiles.golfsim.core.physics.ShotResult
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * M6 repository contract (spec §7): start/append/complete lifecycle,
 * at-most-one-in-progress, active = latest completed, history ordering,
 * recompute-on-write consistency, range-session isolation.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31])
class BagMappingRepositoryTest {

    private fun repo() = SessionRepository.open(RuntimeEnvironment.getApplication())

    private fun ball(ballSpeed: Double) = BallData(
        clubHeadSpeed = 33.0, ballSpeed = ballSpeed, launchDirection = -1.0,
        launchAngle = 12.0, spinAxis = -4.0, totalSpin = 8000, unknown1 = 5, unknown2 = 10,
    )

    /** Richer helper for the misread-gate fixtures (custom CHS + spin). */
    private fun ballData(chs: Double, ballSpeed: Double, spin: Int = 8000) = BallData(
        clubHeadSpeed = chs, ballSpeed = ballSpeed, launchDirection = -1.0,
        launchAngle = 12.0, spinAxis = -4.0, totalSpin = spin, unknown1 = 5, unknown2 = 10,
    )

    private val bag = listOf(ClubRecord(1, "D", ClubType.DRIVER, false), ClubRecord(2, "7i", ClubType.IRON, false))

    @Test
    fun `start snapshots the bag - idempotent while in progress`() = runTest {
        val repo = repo()
        val id1 = repo.startBagMappingSession(bag, startedAtMs = 1000)!!
        val id2 = repo.startBagMappingSession(bag, startedAtMs = 2000)!!
        assertEquals(id1, id2) // at most one IN_PROGRESS (spec §4)
        val active = repo.bagMappingActive.first()!!
        assertEquals("D:DRIVER,7i:IRON", active.clubList)
        assertEquals(listOf("D" to ClubType.DRIVER, "7i" to ClubType.IRON), active.clubSnapshot())
    }

    @Test
    fun `complete lifecycle - active clears - latest completed - history newest first`() = runTest {
        val repo = repo()
        val id1 = repo.startBagMappingSession(bag, startedAtMs = 1000)!!
        repo.completeBagMappingSession(id1, completedAtMs = 2000)
        val id2 = repo.startBagMappingSession(bag, startedAtMs = 3000)!!
        assertTrue(id2 != id1)
        repo.completeBagMappingSession(id2, completedAtMs = 4000)
        assertNull(repo.bagMappingActive.first())
        assertEquals(id2, repo.bagMappingLatestCompleted.first()?.id) // latest wins
        assertEquals(listOf(id2, id1), repo.bagMappingHistory.first().map { it.id })
    }

    @Test
    fun `append recomputes the auto filter over the whole club set`() = runTest {
        val repo = repo()
        val id = repo.startBagMappingSession(bag, startedAtMs = 1000)!!
        // CHS 33 with ball 20 → smash 0.61, a hardware misread once the club
        // has 3 shots (the auto filter's misread gate catches it).
        repo.appendBagMappingShot(id, "7i", ClubType.IRON, ball(20.0), 140.0, 150.0, 1001)
        repo.appendBagMappingShot(id, "7i", ClubType.IRON, ball(48.0), 141.0, 151.0, 1002)
        // Still dormant at 2 shots — the early shot is kept so far.
        assertFalse(repo.observeBagMappingShots(id).first().single { it.timestampMs == 1001L }.filtered)
        repo.appendBagMappingShot(id, "7i", ClubType.IRON, ball(46.0), 139.0, 149.0, 1003)
        val shots = repo.observeBagMappingShots(id).first()
        assertTrue(shots.single { it.timestampMs == 1001L }.filtered)
        assertEquals(BagMappingStats.REASON_MISREAD_SMASH, shots.single { it.timestampMs == 1001L }.filterReason)
        assertFalse(shots.single { it.timestampMs == 1002L }.filtered)
        assertNull(shots.single { it.timestampMs == 1002L }.filterReason)
    }

    @Test
    fun `append recomputes retroactively - a late misread flips only itself`() = runTest {
        val repo = repo()
        val id = repo.startBagMappingSession(bag, startedAtMs = 1000)!!
        // Four normal 7i strikes first — nothing flags at any point.
        for (i in 0 until 4) {
            repo.appendBagMappingShot(id, "7i", ClubType.IRON, ballData(42.7, 62.0, 7000), 140.0, 150.0, 1001L + i)
        }
        assertTrue(repo.observeBagMappingShots(id).first().all { !it.filtered })
        // The known 5i-style misread lands last: CHS 35.7 → smash 1.636 (> 1.50)
        // and CHS outside the ±5% band — precedence reports MISREAD_SMASH. Only
        // this shot flips; the four normal strikes stay kept.
        repo.appendBagMappingShot(id, "7i", ClubType.IRON, ballData(35.7, 58.4, 7000), 130.0, 140.0, 1005)
        val shots = repo.observeBagMappingShots(id).first()
        assertEquals(5, shots.size)
        assertTrue(shots.single { it.timestampMs == 1005L }.filtered)
        assertEquals(BagMappingStats.REASON_MISREAD_SMASH, shots.single { it.timestampMs == 1005L }.filterReason)
        assertEquals(4, shots.count { !it.filtered })
    }

    @Test
    fun `include override - effective inclusion changes and appends never clobber it`() = runTest {
        val repo = repo()
        val id = repo.startBagMappingSession(bag, startedAtMs = 1000)!!
        // The known 5i-style misread: CHS 35.7, ball 58.4 → smash 1.636.
        repo.appendBagMappingShot(id, "7i", ClubType.IRON, ballData(42.7, 62.0, 7000), 140.0, 150.0, 1001)
        repo.appendBagMappingShot(id, "7i", ClubType.IRON, ballData(42.7, 62.0, 7000), 140.0, 150.0, 1002)
        repo.appendBagMappingShot(id, "7i", ClubType.IRON, ballData(42.7, 62.0, 7000), 140.0, 150.0, 1003)
        repo.appendBagMappingShot(id, "7i", ClubType.IRON, ballData(35.7, 58.4, 7000), 130.0, 140.0, 1004)
        val misread = repo.observeBagMappingShots(id).first().single { it.timestampMs == 1004L }
        // Auto verdict excludes it; the user ticks it back in.
        assertTrue(misread.filtered)
        assertFalse(misread.effectiveIncluded())
        assertNull(misread.includeOverride)
        repo.setBagMappingShotIncludeOverride(misread.id, true)
        assertTrue(repo.observeBagMappingShots(id).first().single { it.id == misread.id }.effectiveIncluded())

        // A passing shot can be ticked out (force exclude).
        val normal = repo.observeBagMappingShots(id).first().single { it.timestampMs == 1001L }
        assertTrue(normal.effectiveIncluded())
        repo.setBagMappingShotIncludeOverride(normal.id, false)
        assertFalse(repo.observeBagMappingShots(id).first().single { it.id == normal.id }.effectiveIncluded())

        // Appending another shot recomputes the AUTO verdict (filtered stays the
        // gate's call) but never clobbers the user override.
        repo.appendBagMappingShot(id, "7i", ClubType.IRON, ballData(42.7, 62.0, 7000), 140.0, 150.0, 1005)
        val after = repo.observeBagMappingShots(id).first()
        assertTrue(after.single { it.id == misread.id }.effectiveIncluded())   // still forced in
        assertFalse(after.single { it.id == normal.id }.effectiveIncluded())   // still forced out
        assertTrue(after.single { it.id == misread.id }.filtered)              // auto verdict unchanged
        assertEquals(false, after.single { it.id == normal.id }.includeOverride) // override survives the append
        assertEquals(BagMappingStats.REASON_MISREAD_SMASH, after.single { it.id == misread.id }.filterReason)
    }

    @Test
    fun `mapping shots never enter range sessions and vice versa`() = runTest {
        val repo = repo()
        val bagId = repo.startBagMappingSession(bag, startedAtMs = 1000)!!
        repo.appendBagMappingShot(bagId, "7i", ClubType.IRON, ball(48.0), 140.0, 150.0, 1001)
        // Range session list stays empty: bag writes never create range rows.
        assertTrue(repo.summaries.first().isEmpty())
        assertFalse(repo.hasOpenSession.first())
        // Range writes never create bag rows: the bag session stays at 1 shot.
        repo.appendShot(
            ballData = ball(48.0),
            result = ShotResult(carryM = 140.0, rolloutM = 10.0, totalM = 150.0, sideM = -3.0, apexM = 27.0, flightTimeSec = 6.0),
            source = ShotSource.LIVE,
            clubName = "7i",
            timestampMs = 2000,
        )
        assertEquals(1, repo.observeBagMappingShots(bagId).first().size)
        assertEquals(1, repo.summaries.first().single().shotCount)
    }

    /**
     * A pre-gate session row: written straight to the DAO with the entity
     * defaults (filtered=false, filterReason=null) — exactly what a session
     * collected before the 2026-10-16 misread gates shipped looks like.
     */
    private fun preGateShot(
        sessionId: Long,
        clubName: String,
        chs: Double,
        ballSpeed: Double,
        timestampMs: Long,
        launch: Double = 12.0,
        dir: Double = -1.0,
        axis: Double = -4.0,
        spin: Int = 8000,
    ) = BagMappingShotEntity(
        sessionId = sessionId,
        clubName = clubName,
        clubType = "IRON",
        timestampMs = timestampMs,
        clubHeadSpeedMps = chs,
        ballSpeedMps = ballSpeed,
        launchAngleDeg = launch,
        launchDirDeg = dir,
        spinAxisDeg = axis,
        totalSpinRpm = spin,
        carryM = ballSpeed * 2.2,
        totalM = ballSpeed * 2.35,
    )

    @Test
    fun `backfill applies the auto gates to a pre-gate session and preserves overrides`() = runTest {
        val repo = repo()
        val id = repo.startBagMappingSession(bag, startedAtMs = 1000)!!
        val dao = repo.bagMappingShotDao()

        // 2026-10-15-style session: 5 shots per club, ALL pre-gate (no
        // recompute ever ran). GW #15 spin outlier, 5i #43 smash > 1.50, D
        // #64 spin-axis outlier, D #65 CHS outlier — plus a clean 7i club.
        val gw = listOf(
            preGateShot(id, "GW", 30.0, 44.5, 2001, launch = 26.0, axis = -6.0, spin = 9843),
            preGateShot(id, "GW", 30.0, 44.5, 2002, launch = 26.0, axis = -6.0, spin = 10000),
            preGateShot(id, "GW", 30.0, 44.5, 2003, launch = 26.0, axis = -6.0, spin = 4502),
            preGateShot(id, "GW", 30.0, 44.5, 2004, launch = 26.0, axis = -6.0, spin = 9675),
            preGateShot(id, "GW", 30.0, 44.5, 2005, launch = 26.0, axis = -6.0, spin = 10827),
        )
        val fiveI = listOf(
            preGateShot(id, "5i", 42.7, 62.0, 4001, launch = 14.0, spin = 7000),
            preGateShot(id, "5i", 42.7, 62.0, 4002, launch = 14.0, spin = 7000),
            preGateShot(id, "5i", 35.7, 58.4, 4003, launch = 14.0, spin = 7000),
            preGateShot(id, "5i", 42.7, 62.0, 4004, launch = 14.0, spin = 7000),
            preGateShot(id, "5i", 42.7, 62.0, 4005, launch = 14.0, spin = 7000),
        )
        val driver = listOf(
            preGateShot(id, "D", 49.2, 72.2, 6001, launch = 10.0, dir = -2.0, axis = -11.0, spin = 2200),
            preGateShot(id, "D", 49.2, 72.2, 6002, launch = 11.0, dir = 0.0, axis = -6.0, spin = 2400),
            preGateShot(id, "D", 49.2, 72.2, 6003, launch = 12.0, dir = 1.0, axis = -1.0, spin = 2600),
            preGateShot(id, "D", 49.2, 72.2, 6004, launch = 11.0, dir = 0.0, axis = -27.0, spin = 2500),
            preGateShot(id, "D", 51.9, 76.1, 6005, launch = 13.0, dir = 3.0, axis = 1.0, spin = 2800),
        )
        val sevenI = listOf(
            preGateShot(id, "7i", 40.0, 58.0, 5001, launch = 18.0, spin = 7000),
            preGateShot(id, "7i", 40.0, 58.0, 5002, launch = 18.0, spin = 7000),
            preGateShot(id, "7i", 40.0, 58.0, 5003, launch = 18.0, spin = 7000),
            preGateShot(id, "7i", 40.0, 58.0, 5004, launch = 18.0, spin = 7000),
            preGateShot(id, "7i", 40.0, 58.0, 5005, launch = 18.0, spin = 7000),
        )
        (gw + fiveI + driver + sevenI).forEach { dao.insert(it) }

        // User overrides set BEFORE the backfill (must survive it): force the
        // 5i misread back in, force a clean 7i shot out.
        val shotsBefore = repo.observeBagMappingShots(id).first()
        val fiveIMisread = shotsBefore.single { it.clubName == "5i" && it.timestampMs == 4003L }
        val sevenIClean = shotsBefore.single { it.clubName == "7i" && it.timestampMs == 5001L }
        assertTrue(shotsBefore.all { !it.filtered }) // the pre-gate blind spot
        repo.setBagMappingShotIncludeOverride(fiveIMisread.id, true)
        repo.setBagMappingShotIncludeOverride(sevenIClean.id, false)

        // The backfill re-judges every shot exactly like a fresh append.
        repo.backfillBagMappingAutoFilter()

        val after = repo.observeBagMappingShots(id).first()
        val flagged = after.filter { it.filtered }
        val flaggedIds = flagged.map { it.clubName to it.timestampMs }.toSet()
        assertEquals(setOf("GW" to 2003L, "5i" to 4003L, "D" to 6004L, "D" to 6005L), flaggedIds)
        assertEquals(16, after.count { !it.filtered })
        assertEquals(
            BagMappingStats.REASON_MISREAD_SPIN,
            after.single { it.clubName == "GW" && it.timestampMs == 2003L }.filterReason,
        )
        assertEquals(
            BagMappingStats.REASON_MISREAD_SMASH,
            after.single { it.clubName == "5i" && it.timestampMs == 4003L }.filterReason,
        )
        assertEquals(
            BagMappingStats.REASON_MISREAD_DIRECTION,
            after.single { it.clubName == "D" && it.timestampMs == 6004L }.filterReason,
        )
        assertEquals(
            BagMappingStats.REASON_MISREAD_CHS,
            after.single { it.clubName == "D" && it.timestampMs == 6005L }.filterReason,
        )
        // The other 16 shots stay clean, with no stray reason.
        assertTrue(after.filter { !it.filtered }.all { it.filterReason == null })

        // Overrides survive the backfill and keep driving effective inclusion.
        val fiveIAfter = after.single { it.id == fiveIMisread.id }
        val sevenIAfter = after.single { it.id == sevenIClean.id }
        assertEquals(true, fiveIAfter.includeOverride)
        assertTrue(fiveIAfter.filtered)          // auto verdict still flags it
        assertTrue(fiveIAfter.effectiveIncluded()) // user force-in wins
        assertEquals(false, sevenIAfter.includeOverride)
        assertFalse(sevenIAfter.filtered)        // auto verdict still keeps it
        assertFalse(sevenIAfter.effectiveIncluded()) // user force-out wins
        assertEquals(2, after.count { it.includeOverride != null })

        // Idempotent: a second run is a no-op — same rows, same verdicts.
        repo.backfillBagMappingAutoFilter()
        val twice = repo.observeBagMappingShots(id).first()
        assertEquals(4, twice.count { it.filtered })
        assertEquals(flaggedIds, twice.filter { it.filtered }.map { it.clubName to it.timestampMs }.toSet())
        assertEquals(2, twice.count { it.includeOverride != null })
        assertEquals(true, twice.single { it.id == fiveIMisread.id }.effectiveIncluded())
        assertEquals(false, twice.single { it.id == sevenIClean.id }.effectiveIncluded())
    }
}
