package com.flarelane

import android.app.Application
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * Pins the stop-and-bound contract: a 410 from a device endpoint shuts the SDK
 * down for the rest of the process, no other outcome does, and the pending
 * queue can never grow without limit.
 */
class StopAndQueueTest {

    private val queue = TaskQueueManager.getInstance()

    @After
    fun tearDown() {
        queue.reset()
    }

    private fun countingTask(ran: AtomicInteger, latch: CountDownLatch? = null) =
        object : NamedRunnable("probe") {
            override fun run() {
                ran.incrementAndGet()
                latch?.countDown()
                completeTask()
            }
        }

    // MARK: - 410 mapping

    @Test
    fun `only 410 stops the sdk`() {
        queue.reset()
        val ran = AtomicInteger()

        for (code in intArrayOf(-1, 400, 404, 408, 429, 500, 503)) {
            DeviceService.stopSdkIfGone(code)
        }
        val latch = CountDownLatch(1)
        queue.addTask(countingTask(ran, latch))
        queue.onInitialized()

        assertTrue("queue must still be alive after non-410 failures", latch.await(5, TimeUnit.SECONDS))
        assertEquals(1, ran.get())
    }

    @Test
    fun `410 drops pending tasks and refuses new ones`() {
        queue.reset()
        val ran = AtomicInteger()
        queue.addTask(countingTask(ran)) // queued while the queue is still closed

        DeviceService.stopSdkIfGone(410)

        queue.addTask(countingTask(ran)) // refused outright
        queue.onInitialized()
        Thread.sleep(300)

        assertEquals("neither the pending nor the new task may run after 410", 0, ran.get())
    }

    @Test
    fun `reset lifts the stop so the next launch starts clean`() {
        queue.reset()
        DeviceService.stopSdkIfGone(410)
        queue.reset()

        val ran = AtomicInteger()
        val latch = CountDownLatch(1)
        queue.addTask(countingTask(ran, latch))
        queue.onInitialized()

        assertTrue(latch.await(5, TimeUnit.SECONDS))
        assertEquals(1, ran.get())
    }

    // MARK: - Queue bound

    @Test
    fun `the pending queue never grows past its cap`() {
        queue.reset()
        val ran = AtomicInteger()
        val all = CountDownLatch(TaskQueueManager.MAX_PENDING_TASKS)

        repeat(TaskQueueManager.MAX_PENDING_TASKS + 20) {
            queue.addTask(object : NamedRunnable("bulk") {
                override fun run() {
                    ran.incrementAndGet()
                    all.countDown()
                    completeTask()
                }
            })
        }
        queue.onInitialized()

        assertTrue("capped tasks should all run", all.await(15, TimeUnit.SECONDS))
        Thread.sleep(300)
        assertEquals(
            "tasks beyond the cap must have been refused at add time",
            TaskQueueManager.MAX_PENDING_TASKS, ran.get()
        )
    }

    // MARK: - A dead task must not stall the queue

    /**
     * trackEvent with no usable Context dies inside its own catch. Without the
     * completeTask there, the queue would sit out its full 10s timeout per dead
     * task — the "one event costs ten seconds" behavior from the field report.
     */
    @Test
    fun `a task that dies advances the queue immediately`() {
        queue.reset()
        queue.onInitialized()

        // Application() under returnDefaultValues gives null prefs → the task throws inside run().
        FlareLane.trackEvent(Application(), "probe_event", null)

        val ran = AtomicInteger()
        val latch = CountDownLatch(1)
        queue.addTask(countingTask(ran, latch))

        assertTrue(
            "the next task should start immediately, not after the 10s task timeout",
            latch.await(3, TimeUnit.SECONDS)
        )
    }
}
