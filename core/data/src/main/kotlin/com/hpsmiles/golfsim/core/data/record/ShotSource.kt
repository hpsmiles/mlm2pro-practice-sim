package com.hpsmiles.golfsim.core.data.record

/** Origin of the shot data — a BLE callback is LIVE even if the demo toggle is on. */
enum class ShotSource(val code: Int) { LIVE(0), DEMO(1) }
