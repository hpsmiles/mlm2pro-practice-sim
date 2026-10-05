package com.hpsmiles.golfsim.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import com.hpsmiles.golfsim.R

/**
 * Thin SoundPool wrapper for the three Break-the-Pane cues (2026-10-05 spec).
 * No-ops when disabled or until clips finish loading (SoundPool.play is a
 * silent no-op before load completes, so nothing blocks playback).
 */
class GameAudio(context: Context) {

    var enabled: Boolean = true

    private val pool: SoundPool = SoundPool.Builder()
        .setMaxStreams(3)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build(),
        )
        .build()

    private val glassId: Int = pool.load(context, R.raw.pane_glass_break, 1)
    private val dingId: Int = pool.load(context, R.raw.pane_success_ding, 1)
    private val failId: Int = pool.load(context, R.raw.pane_fail, 1)

    fun glassBreak() = play(glassId)
    fun success() = play(dingId)
    fun fail() = play(failId)

    private fun play(id: Int) {
        if (!enabled) return
        pool.play(id, 0.9f, 0.9f, 1, 0, 1f)
    }

    fun release() {
        pool.release()
    }
}
