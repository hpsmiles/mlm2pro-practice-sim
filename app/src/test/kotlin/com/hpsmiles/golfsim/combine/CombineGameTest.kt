package com.hpsmiles.golfsim.combine

import com.hpsmiles.golfsim.core.ble.BallData
import com.hpsmiles.golfsim.core.physics.ShotResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CombineGameTest {

    private val wedges = CombineCatalog.byId("WEDGES")!!
    private val approach = CombineCatalog.byId("APPROACH")!!

    /** Injects a synthetic rest position so the shot is deterministic on the JVM. */
    private fun gameWithRest(
        restX: Double = 0.0,
        restY: Double = 60.0,
    ): CombineGame {
        val game = CombineGame()
        game.simulator = { _ ->
            ShotResult(
                carryM = 60.0, rolloutM = 0.0, totalM = 60.0, sideM = restX,
                apexM = 20.0, flightTimeSec = 4.0,
                samples = emptyList(),
                restX = restX, restY = restY,
            )
        }
        return game
    }

    /** Valid decoded measurement (ball speed 44 m/s passes LaunchConditions validation). */
    private fun ball(speed: Double = 44.0) = BallData(33.0, speed, 0.0, 21.0, 0.0, 7500, 5, 10)

    @Test
    fun `add before start returns null and adds no shot`() {
        val game = gameWithRest()
        assertNull(game.add(ball()))
        assertTrue(game.shots.isEmpty())
        assertEquals(0, game.tick)
    }

    @Test
    fun `add after completion returns null`() {
        val game = gameWithRest()
        game.start(wedges, sessionId = 1L, baselineTaken = CombinePlan.SHOTS_TOTAL)
        assertTrue(game.complete)
        assertNull(game.add(ball()))
        assertTrue(game.shots.isEmpty())
    }

    @Test
    fun `guard-violating ball data is ignored`() {
        val game = gameWithRest()
        game.start(wedges, sessionId = 1L, baselineTaken = 0)
        // ball speed 0.0 m/s violates LaunchConditions require(0.5..100)
        assertNull(game.add(ball(speed = 0.0)))
        assertTrue(game.shots.isEmpty())
        assertEquals(0, game.tick)
    }

    @Test
    fun `accepted shot increments tick and stamps the clock`() {
        val game = gameWithRest()
        game.clockMs = { 123_456L }
        game.start(wedges, sessionId = 1L, baselineTaken = 0)
        val shot = game.add(ball())
        assertNotNull(shot)
        assertEquals(1, game.tick)
        assertEquals(123_456L, shot!!.display.timestampMs)
    }

    @Test
    fun `resume baseline drives taken count, index and station`() {
        val game = gameWithRest()
        game.start(approach, sessionId = 42L, baselineTaken = 6)
        assertEquals(6, game.takenCount)
        assertEquals(6, game.nextShotIndex)
        assertFalse(game.complete)

        val shot = game.add(ball())!!
        assertEquals(6, shot.index)
        // Round-robin sequence 0,0,1,1,2,2,... -> index 6 is station 0.
        assertEquals(0, CombinePlan.stationIndexFor(6))
        assertEquals(CombinePlan.stationIndexFor(6), shot.stationIndex)
        assertEquals(7, game.takenCount)
    }

    @Test
    fun `start resets session data but keeps in-memory shots cleared`() {
        val game = gameWithRest()
        game.start(wedges, sessionId = 1L, baselineTaken = 0)
        game.add(ball())
        game.record = CombineRecord.compare(null, 5.0)

        game.start(wedges, sessionId = 2L, baselineTaken = 0)
        assertTrue(game.shots.isEmpty())
        assertNull(game.record)
        assertEquals(0, game.tick)
        assertEquals(0, game.baselineTaken)
        assertEquals(2L, game.sessionId)
    }

    @Test
    fun `reset clears everything`() {
        val game = gameWithRest()
        game.start(wedges, sessionId = 1L, baselineTaken = 3)
        game.add(ball())

        game.reset()
        assertNull(game.entry)
        assertEquals(0L, game.sessionId)
        assertEquals(0, game.baselineTaken)
        assertTrue(game.shots.isEmpty())
        assertNull(game.record)
        assertEquals(0, game.tick)
        assertNull(game.add(ball()))
    }

    @Test
    fun `completes at eighteen shots`() {
        val game = gameWithRest()
        game.start(wedges, sessionId = 1L, baselineTaken = 17)
        assertFalse(game.complete)
        assertNotNull(game.add(ball()))
        assertTrue(game.complete)
        assertEquals(CombinePlan.SHOTS_TOTAL, game.takenCount)
        assertNull(game.add(ball()))
    }
}
