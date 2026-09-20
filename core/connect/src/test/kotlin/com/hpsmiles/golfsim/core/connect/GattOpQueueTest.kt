package com.hpsmiles.golfsim.core.connect

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * GattOpQueue is the pure one-op-at-a-time serializer that Mlm2proGattClient
 * drives from Android GATT callbacks (spec 60c775c §3a). Android allows only
 * one in-flight GATT operation; the queue enforces that ordering without any
 * Android types so it is JVM-testable.
 */
class GattOpQueueTest {

    private class FakeOp(val id: String) : GattOpQueue.Op {
        var started = false
        override fun start() {
            started = true
        }
    }

    @Test
    fun firstOperationStartsImmediately() {
        val queue = GattOpQueue()
        val op = FakeOp("a")
        queue.enqueue(op)
        assertTrue(op.started)
    }

    @Test
    fun secondOperationWaitsUntilFirstCompletes() {
        val queue = GattOpQueue()
        val first = FakeOp("a")
        val second = FakeOp("b")
        queue.enqueue(first)
        queue.enqueue(second)
        assertFalse(second.started)
        queue.onOperationComplete()
        assertTrue(second.started)
    }

    @Test
    fun completeWithoutPendingIsHarmless() {
        val queue = GattOpQueue()
        queue.onOperationComplete() // no throw
    }

    @Test
    fun queuedOperationsRunInFifoOrder() {
        val queue = GattOpQueue()
        val order = mutableListOf<String>()
        val a = object : GattOpQueue.Op {
            override fun start() {
                order.add("a")
            }
        }
        val b = object : GattOpQueue.Op {
            override fun start() {
                order.add("b")
            }
        }
        val c = object : GattOpQueue.Op {
            override fun start() {
                order.add("c")
            }
        }
        queue.enqueue(a)
        queue.enqueue(b)
        queue.enqueue(c)
        queue.onOperationComplete()
        queue.onOperationComplete()
        assertEquals(listOf("a", "b", "c"), order)
    }

    @Test
    fun currentOperationIsNullAfterCompletion() {
        val queue = GattOpQueue()
        queue.enqueue(FakeOp("a"))
        queue.onOperationComplete()
        // Internal state check via behavior: enqueue starts immediately when idle.
        val next = FakeOp("b")
        queue.enqueue(next)
        assertTrue(next.started)
    }
}
