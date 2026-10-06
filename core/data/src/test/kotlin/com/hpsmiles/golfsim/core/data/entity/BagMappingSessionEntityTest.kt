package com.hpsmiles.golfsim.core.data.entity

import com.hpsmiles.golfsim.core.data.record.ClubType
import org.junit.Assert.assertEquals
import org.junit.Test

/** Golden cases for the M6 bag snapshot codec (spec §7). */
class BagMappingSessionEntityTest {

    private fun decode(clubList: String): List<Pair<String, ClubType>> =
        BagMappingSessionEntity(
            startedAtMs = 0,
            completedAtMs = null,
            status = BagMappingStatus.IN_PROGRESS,
            clubList = clubList,
        ).clubSnapshot()

    @Test
    fun `codec round-trips colons in club names`() {
        // Name "X:Y" encodes to "X:Y:IRON"; the codec must split at the LAST
        // colon so the name survives intact (and shotsForClub keeps matching).
        val bag = listOf("X:Y" to ClubType.IRON)
        val roundTripped = decode(BagMappingSessionEntity.encodeClubSnapshot(bag))
        assertEquals(listOf("X:Y" to ClubType.IRON), roundTripped)
    }
}
