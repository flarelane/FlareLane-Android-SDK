package com.flarelane

import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/**
 * Tasks wait in the queue until the device is registered. When the project is deleted that never
 * happens, so the SDK stops instead of letting the queue grow inside the host app.
 */
class TaskQueueManagerTest {
    private lateinit var manager: TaskQueueManager

    private fun task(name: String) = object : NamedRunnable(name) {
        override fun run() {}
    }

    @Before
    fun setUp() {
        manager = TaskQueueManager.getInstance()
        manager.reset()
    }

    @Test
    fun `stop clears pending tasks and refuses new ones`() {
        repeat(5) { manager.addTask(task("task-$it")) }
        assertEquals(5, manager.queueSize())

        manager.stop()
        assertEquals(0, manager.queueSize())

        manager.addTask(task("after-stop"))
        assertEquals(0, manager.queueSize())
    }

    @Test
    fun `reset lifts the stopped state so the next launch works again`() {
        manager.stop()
        manager.reset()
        manager.addTask(task("after-reset"))

        assertEquals(1, manager.queueSize())
    }
}
