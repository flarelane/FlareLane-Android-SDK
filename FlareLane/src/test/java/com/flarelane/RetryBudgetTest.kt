package com.flarelane

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * The budget is the SDK's guard against retries snowballing during a long outage, and it is reached
 * from several threads at once, so both the limit and its accounting are pinned here.
 */
class RetryBudgetTest {

    @Test
    fun `slots are handed out up to the limit`() {
        val budget = RetryBudget(3)

        repeat(3) { assertTrue(budget.tryAcquire()) }

        assertFalse("the fourth request must be refused", budget.tryAcquire())
        assertEquals(3, budget.waitingCount())
    }

    @Test
    fun `a released slot can be taken again`() {
        val budget = RetryBudget(1)

        assertTrue(budget.tryAcquire())
        assertFalse(budget.tryAcquire())

        budget.release()

        assertEquals(0, budget.waitingCount())
        assertTrue(budget.tryAcquire())
    }

    /** A refused acquire must not consume a slot, or the budget would leak down to zero. */
    @Test
    fun `a refused acquire leaves the count untouched`() {
        val budget = RetryBudget(1)
        budget.tryAcquire()

        repeat(10) { budget.tryAcquire() }

        assertEquals(1, budget.waitingCount())
    }

    @Test
    fun `concurrent callers never exceed the limit`() {
        val limit = 8
        val budget = RetryBudget(limit)
        val granted = AtomicInteger()
        val start = CountDownLatch(1)
        val done = CountDownLatch(64)
        val pool = Executors.newFixedThreadPool(16)

        repeat(64) {
            pool.execute {
                start.await()
                if (budget.tryAcquire()) granted.incrementAndGet()
                done.countDown()
            }
        }
        start.countDown()

        assertTrue(done.await(10, TimeUnit.SECONDS))
        pool.shutdown()

        assertEquals(limit, granted.get())
        assertEquals(limit, budget.waitingCount())
    }
}
