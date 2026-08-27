package com.flarelane

import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Pins the queue against stale completions. A task's completion can arrive after the queue already
 * moved on — the task timed out and its HTTP callback landed later, or a task completed twice.
 * Acting on it used to cancel the running task's timeout and advance the queue past it, so tasks
 * could overlap or be skipped.
 */
class TaskQueueManagerTest {

    private val queue = TaskQueueManager.getInstance()

    @After
    fun tearDown() {
        queue.reset()
    }

    @Test
    fun `a stale completion does not advance the queue past the running task`() {
        queue.reset()
        queue.onInitialized()

        // Task A completes normally but keeps a handle so the test can fire its completion again
        // later — the same signal a late HTTP callback would produce after a timeout.
        val taskA = object : NamedRunnable("a") {
            override fun run() = completeTask()
            fun staleComplete() = completeTask()
        }

        val bStarted = CountDownLatch(1)
        val bRelease = CountDownLatch(1)
        val taskB = object : NamedRunnable("b") {
            override fun run() {
                bStarted.countDown()
                bRelease.await(5, TimeUnit.SECONDS)
                completeTask()
            }
        }

        val cStarted = AtomicBoolean()
        val cLatch = CountDownLatch(1)
        val taskC = object : NamedRunnable("c") {
            override fun run() {
                cStarted.set(true)
                cLatch.countDown()
                completeTask()
            }
        }

        queue.addTask(taskA)
        queue.addTask(taskB)
        queue.addTask(taskC)

        assertTrue("task B never started", bStarted.await(5, TimeUnit.SECONDS))

        // The stale signal arrives while B is still running.
        taskA.staleComplete()
        Thread.sleep(300)

        assertFalse("the stale completion must not start C while B is still running", cStarted.get())

        bRelease.countDown()
        assertTrue("C should start once B actually completes", cLatch.await(5, TimeUnit.SECONDS))
    }
}
