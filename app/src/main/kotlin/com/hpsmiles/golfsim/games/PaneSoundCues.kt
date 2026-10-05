package com.hpsmiles.golfsim.games

/**
 * Pure cue logic for Break-the-Pane sounds (2026-10-05 spec): the playback
 * loop asks whether a cue is due; GameAudio performs it. Event-based so
 * 1x-4x playback speed stays in sync; replays (speed change restarts the
 * loop) legitimately re-trigger.
 */
object PaneSoundCues {

    /** True when the shot's ball rests on the green (ding); false is a miss (fail). */
    fun restsOnGreen(kind: BreakOutcomeKind): Boolean =
        kind == BreakOutcomeKind.BROKE || kind == BreakOutcomeKind.GREEN_MISSED_PANE

    /** Fires once per playback at/after the pane-crossing reveal fraction; the caller gates this on the shot having crossed standing glass (`crossedUnbrokenCell`), and this function owns timing + the once-per-playback latch. */
    fun glassDue(playFraction: Float, revealFraction: Float, alreadyPlayed: Boolean): Boolean =
        !alreadyPlayed && playFraction >= revealFraction
}
