package com.flarelane

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * Specs for the notification-event dedup helper. The contract guarded here is:
 *
 *   1. Same (notification id, event type) pair never returns `true` more than once.
 *   2. Different notification ids are independent within the same event type.
 *   3. Different event types for the same notification id are independent — a
 *      received event followed by a clicked event are both legitimate first-time wins.
 *   4. Persistence layer ([Storage]) is consulted on cold cache so the dedup
 *      decision survives process restart.
 *   5. Once [MAX_SIZE] is exceeded, the *oldest* key is the one that drops out
 *      (FIFO), letting that key re-fire after eviction. The bound is across all
 *      event types combined.
 *   6. Concurrent callers race-safe: exactly one wins for any given key.
 *   7. Malformed persisted values (empty tokens / leading/trailing commas) parse
 *      cleanly without polluting the set with empty strings.
 *
 * All tests inject a FakeStorage so the SharedPreferences path stays out of the
 * JVM unit-test surface (covered separately by the on-device sample run).
 */
class NotificationEventProcessorTest {

    /** In-memory stand-in for SharedPreferences — captures the most recently saved value. */
    private class FakeStorage(initial: String = "") : NotificationEventProcessor.Storage {
        var stored: String = initial
        override fun load(): String = stored
        override fun save(value: String) {
            stored = value
        }
    }

    @Before
    fun setUp() {
        NotificationEventProcessor.setStorageForTesting(null)
        NotificationEventProcessor.resetCacheForTesting()
    }

    @After
    fun tearDown() {
        NotificationEventProcessor.setStorageForTesting(null)
        NotificationEventProcessor.resetCacheForTesting()
    }

    @Test
    fun `first call returns true and a repeat for the same id+eventType returns false`() {
        val storage = FakeStorage()
        NotificationEventProcessor.setStorageForTesting(storage)

        assertTrue(NotificationEventProcessor.shouldProcessWith(storage, "n1", EventType.Clicked))
        assertFalse(NotificationEventProcessor.shouldProcessWith(storage, "n1", EventType.Clicked))
    }

    @Test
    fun `different ids each get one true independently within the same event type`() {
        val storage = FakeStorage()
        NotificationEventProcessor.setStorageForTesting(storage)

        assertTrue(NotificationEventProcessor.shouldProcessWith(storage, "n1", EventType.Clicked))
        assertTrue(NotificationEventProcessor.shouldProcessWith(storage, "n2", EventType.Clicked))
        assertFalse(NotificationEventProcessor.shouldProcessWith(storage, "n1", EventType.Clicked))
        assertFalse(NotificationEventProcessor.shouldProcessWith(storage, "n2", EventType.Clicked))
    }

    @Test
    fun `same id but different event types are independent (received and clicked both fire)`() {
        // The core reason we generalized the dedup key: receiving a notification
        // and then clicking it are two legitimate events for the SAME id. Each
        // event type must get one first-time win independently.
        val storage = FakeStorage()
        NotificationEventProcessor.setStorageForTesting(storage)

        assertTrue(
            NotificationEventProcessor.shouldProcessWith(storage, "n1", EventType.ForegroundReceived)
        )
        assertTrue(NotificationEventProcessor.shouldProcessWith(storage, "n1", EventType.Clicked))
        // But each one still dedups within its own type.
        assertFalse(
            NotificationEventProcessor.shouldProcessWith(storage, "n1", EventType.ForegroundReceived)
        )
        assertFalse(NotificationEventProcessor.shouldProcessWith(storage, "n1", EventType.Clicked))
    }

    @Test
    fun `BACKGROUND_RECEIVED and FOREGROUND_RECEIVED for same id are independent`() {
        // A push delivered while backgrounded followed by the user pulling the
        // app forward could fire both lifecycles for one notification id —
        // unusual, but the dedup must not collapse them.
        val storage = FakeStorage()
        NotificationEventProcessor.setStorageForTesting(storage)

        assertTrue(
            NotificationEventProcessor.shouldProcessWith(storage, "n1", EventType.BackgroundReceived)
        )
        assertTrue(
            NotificationEventProcessor.shouldProcessWith(storage, "n1", EventType.ForegroundReceived)
        )
    }

    @Test
    fun `accept persists the new key immediately`() {
        val storage = FakeStorage()
        NotificationEventProcessor.setStorageForTesting(storage)

        NotificationEventProcessor.shouldProcessWith(storage, "n1", EventType.Clicked)
        assertEquals("n1#CLICKED", storage.stored)
    }

    @Test
    fun `process restart restores dedup from persisted storage`() {
        val storage = FakeStorage(initial = "n1#CLICKED,n1#FOREGROUND_RECEIVED")
        NotificationEventProcessor.setStorageForTesting(storage)
        NotificationEventProcessor.resetCacheForTesting()

        // Both keys land in the cache after the cold load.
        assertFalse(NotificationEventProcessor.shouldProcessWith(storage, "n1", EventType.Clicked))
        assertFalse(
            NotificationEventProcessor.shouldProcessWith(storage, "n1", EventType.ForegroundReceived)
        )
        // BACKGROUND_RECEIVED for the same id was not persisted, so it still fires.
        assertTrue(
            NotificationEventProcessor.shouldProcessWith(storage, "n1", EventType.BackgroundReceived)
        )
    }

    @Test
    fun `oldest key is evicted FIFO once MAX_SIZE is exceeded`() {
        val storage = FakeStorage()
        NotificationEventProcessor.setStorageForTesting(storage)

        for (i in 0 until NotificationEventProcessor.MAX_SIZE) {
            assertTrue(
                "fill #$i must be accepted",
                NotificationEventProcessor.shouldProcessWith(storage, "id-$i", EventType.Clicked)
            )
        }
        // One more push triggers FIFO eviction of "id-0#CLICKED".
        assertTrue(
            NotificationEventProcessor.shouldProcessWith(storage, "overflow", EventType.Clicked)
        )
        // The second-oldest is still resident (check before any further inserts).
        assertFalse(NotificationEventProcessor.shouldProcessWith(storage, "id-1", EventType.Clicked))
        // The oldest key was evicted, so it is accepted again.
        assertTrue(NotificationEventProcessor.shouldProcessWith(storage, "id-0", EventType.Clicked))
    }

    @Test
    fun `concurrent callers for the same key see exactly one true`() {
        val storage = FakeStorage()
        NotificationEventProcessor.setStorageForTesting(storage)

        val threadCount = 20
        val winners = AtomicInteger(0)
        val gate = CountDownLatch(1)
        val done = CountDownLatch(threadCount)
        val pool = Executors.newFixedThreadPool(threadCount)
        try {
            repeat(threadCount) {
                pool.submit {
                    gate.await()
                    if (NotificationEventProcessor.shouldProcessWith(storage, "race", EventType.Clicked)) {
                        winners.incrementAndGet()
                    }
                    done.countDown()
                }
            }
            gate.countDown()
            assertTrue(done.await(5, TimeUnit.SECONDS))
        } finally {
            pool.shutdownNow()
        }
        assertEquals(1, winners.get())
    }

    @Test
    fun `malformed persisted value (empty tokens, leading and trailing commas) parses without empty entries`() {
        val storage = FakeStorage(initial = ",,n1#CLICKED,,n2#FOREGROUND_RECEIVED,")
        NotificationEventProcessor.setStorageForTesting(storage)
        NotificationEventProcessor.resetCacheForTesting()

        assertFalse(NotificationEventProcessor.shouldProcessWith(storage, "n1", EventType.Clicked))
        assertFalse(
            NotificationEventProcessor.shouldProcessWith(storage, "n2", EventType.ForegroundReceived)
        )
        // A genuinely new key is still accepted.
        assertTrue(NotificationEventProcessor.shouldProcessWith(storage, "n3", EventType.Clicked))
        // The save side rewrites the value into a clean canonical form (no empty tokens).
        assertEquals("n1#CLICKED,n2#FOREGROUND_RECEIVED,n3#CLICKED", storage.stored)
    }

    @Test
    fun `empty persisted value yields an empty set`() {
        val storage = FakeStorage(initial = "")
        NotificationEventProcessor.setStorageForTesting(storage)
        NotificationEventProcessor.resetCacheForTesting()

        assertTrue(NotificationEventProcessor.shouldProcessWith(storage, "n1", EventType.Clicked))
        assertNotNull(storage.stored)
        assertEquals("n1#CLICKED", storage.stored)
    }
}
