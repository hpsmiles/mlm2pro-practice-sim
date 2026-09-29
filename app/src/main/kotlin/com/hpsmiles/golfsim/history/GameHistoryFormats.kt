// app/src/main/kotlin/com/hpsmiles/golfsim/history/GameHistoryFormats.kt
package com.hpsmiles.golfsim.history

import com.hpsmiles.golfsim.core.data.entity.GameModes
import com.hpsmiles.golfsim.core.data.entity.GameResultEntity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Pure formatting for game result rows — unit-testable JVM, no Compose. */
object GameHistoryFormats {

    private val dateTimeFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)

    fun modeLabel(mode: String): String = when (mode) {
        GameModes.TARGET_PRACTICE -> "TARGET PRACTICE"
        GameModes.BREAK_PANE -> "BREAK THE PANE"
        else -> mode
    }

    fun difficultyLabel(difficulty: String?): String = difficulty ?: ""

    fun scoreLabel(result: GameResultEntity): String = when (result.mode) {
        GameModes.TARGET_PRACTICE -> "${result.score} / 125 pts"
        GameModes.BREAK_PANE -> "${result.score} shots"
        else -> "${result.score}"
    }

    fun targetDistanceM(distanceBin: Int): String = "$distanceBin m"

    fun dateTime(epochMs: Long): String = dateTimeFormat.format(Date(epochMs))
}
