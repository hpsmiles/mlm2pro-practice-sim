package com.hpsmiles.golfsim.core.connect

/**
 * Pure one-operation-at-a-time queue (spec 60c775c §3a).
 *
 * Android's BluetoothGatt allows exactly one in-flight operation; issuing a
 * second write before the first callback silently drops it. [Mlm2proGattClient]
 * enqueues every GATT operation here and calls [onOperationComplete] from
 * `onDescriptorWrite` / `onCharacteristicWrite` callbacks to advance the queue.
 *
 * Zero Android dependencies — JVM-testable (see GattOpQueueTest).
 */
class GattOpQueue {

    /** A single GATT operation, started on the queue's thread when it is its turn. */
    fun interface Op {
        fun start()
    }

    private val pending = ArrayDeque<Op>()
    private var running: Op? = null

    /**
     * Adds [op]. Starts it immediately when the queue is idle; otherwise it
     * waits until every earlier operation has completed.
     */
    @Synchronized
    fun enqueue(op: Op) {
        val current = running
        if (current == null) {
            running = op
            op.start()
        } else {
            pending.addLast(op)
        }
    }

    /**
     * Marks the current operation finished and starts the next one (if any).
     * Call from GATT callbacks. Calling when idle is harmless.
     */
    @Synchronized
    fun onOperationComplete() {
        val next = pending.removeFirstOrNull()
        if (next == null) {
            running = null
        } else {
            running = next
            next.start()
        }
    }
}
