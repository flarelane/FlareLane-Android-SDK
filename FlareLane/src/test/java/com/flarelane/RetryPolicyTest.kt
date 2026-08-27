package com.flarelane

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Random

/**
 * Pins which failures are worth sending again. The distinction is the whole point of the retry
 * layer: retrying a rejection the server will simply repeat wastes battery and, for events, risks
 * duplicating data, while giving up on a dropped connection loses information for no reason.
 */
class RetryPolicyTest {

    @Test
    fun `a request that never got a response is retried`() {
        assertTrue(RetryPolicy.shouldRetry(RetryPolicy.NO_RESPONSE, 1))
    }

    @Test
    fun `server errors are retried`() {
        for (status in intArrayOf(500, 502, 503, 504)) {
            assertTrue("$status should be retried", RetryPolicy.shouldRetry(status, 1))
        }
    }

    @Test
    fun `timeout and rate limit are retried`() {
        assertTrue(RetryPolicy.shouldRetry(408, 1))
        assertTrue(RetryPolicy.shouldRetry(429, 1))
    }

    @Test
    fun `client errors are not retried`() {
        for (status in intArrayOf(400, 401, 403, 404, 409, 422)) {
            assertFalse("$status should not be retried", RetryPolicy.shouldRetry(status, 1))
        }
    }

    /**
     * 410 means the project or device is gone for the rest of this app run. Retrying would delay
     * the stop signal the SDK acts on by the length of the whole backoff chain.
     */
    @Test
    fun `gone is not retried`() {
        assertFalse(RetryPolicy.shouldRetry(410, 1))
    }

    @Test
    fun `success is never retried`() {
        for (status in intArrayOf(200, 201, 204, 302)) {
            assertFalse("$status should not be retried", RetryPolicy.shouldRetry(status, 1))
        }
    }

    @Test
    fun `the last attempt is never followed by another`() {
        assertTrue(RetryPolicy.shouldRetry(500, RetryPolicy.MAX_ATTEMPTS - 1))
        assertFalse(RetryPolicy.shouldRetry(500, RetryPolicy.MAX_ATTEMPTS))
        assertFalse(RetryPolicy.shouldRetry(RetryPolicy.NO_RESPONSE, RetryPolicy.MAX_ATTEMPTS))
    }

    @Test
    fun `backoff grows and stays within the advertised bounds`() {
        val random = Random(20260827)

        repeat(200) {
            val first = RetryPolicy.delayMillis(1, random)
            val second = RetryPolicy.delayMillis(2, random)

            assertTrue("first delay out of range: $first", first in 500..1000)
            assertTrue("second delay out of range: $second", second in 1500..3000)
        }
    }

    /** Out-of-range attempt numbers clamp to the nearest entry instead of throwing. */
    @Test
    fun `backoff tolerates attempt numbers outside the table`() {
        assertEquals(RetryPolicy.delayMillis(1, Random(1)), RetryPolicy.delayMillis(0, Random(1)))
        assertEquals(RetryPolicy.delayMillis(2, Random(1)), RetryPolicy.delayMillis(99, Random(1)))
    }

    /** The random share is what keeps devices that reconnect together from retrying as one burst. */
    @Test
    fun `backoff is jittered`() {
        val random = Random(7)
        val samples = (1..50).map { RetryPolicy.delayMillis(1, random) }.toSet()

        assertTrue("expected varied delays, got $samples", samples.size > 1)
    }
}
