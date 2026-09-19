package com.hpsmiles.golfsim.core.ble

/**
 * Landing zone for the MLM2PRO BLE byte-decoder (ROADMAP milestone M1).
 *
 * This module is a pure JVM library: no Android dependencies allowed, so the
 * decoder stays unit-testable without a device or emulator. M1 implements
 * raw-byte -> shot-metric decoding here, validated against golden captures.
 */
object Mlm2proBle
