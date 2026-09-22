package com.flarelane

import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * Pins the retry contract end to end over a real socket: what the caller sees,
 * how many requests actually left the device, and that the handler still fires
 * exactly once now that a request can be sent more than once.
 */
class HTTPClientRetryTest {

    private lateinit var server: StubServer

    @After
    fun tearDown() {
        // The classification specs never start a server.
        if (::server.isInitialized) server.stop()
    }

    // MARK: - Classification specs (no network)

    @Test
    fun `transient failures are retried, rejections are not`() {
        // No response at all, server-side errors, timeout and rate limit are worth another try.
        for (code in intArrayOf(-1, 500, 502, 503, 504, 408, 429)) {
            assertTrue("$code should be retried", HTTPClient.shouldRetry(code, 1))
        }
        // Every other 4xx is a decision the server would simply repeat — 410 included,
        // because it is the stop signal and must not be delayed by a backoff.
        for (code in intArrayOf(400, 401, 403, 404, 409, 410, 422)) {
            assertFalse("$code should not be retried", HTTPClient.shouldRetry(code, 1))
        }
        // Success is never retried.
        for (code in intArrayOf(200, 201, 302)) {
            assertFalse("$code should not be retried", HTTPClient.shouldRetry(code, 1))
        }
    }

    @Test
    fun `the last attempt is never followed by another`() {
        assertTrue(HTTPClient.shouldRetry(500, HTTPClient.MAX_ATTEMPTS - 1))
        assertFalse(HTTPClient.shouldRetry(500, HTTPClient.MAX_ATTEMPTS))
        assertFalse(HTTPClient.shouldRetry(-1, HTTPClient.MAX_ATTEMPTS))
    }

    @Test
    fun `backoff stays inside the advertised bounds and is jittered`() {
        val first = (1..200).map { HTTPClient.delayMillis(1) }
        val second = (1..200).map { HTTPClient.delayMillis(2) }

        assertTrue("first delays out of range: ${first.filter { it !in 500..1000 }}", first.all { it in 500..1000 })
        assertTrue("second delays out of range: ${second.filter { it !in 1500..3000 }}", second.all { it in 1500..3000 })
        // The random share is what keeps devices that reconnect together from retrying as one burst.
        assertTrue("expected varied delays", first.toSet().size > 1)
    }

    // MARK: - Contracts over the wire

    @Test
    fun `a request that fails once succeeds on the retry`() {
        server = StubServer(
            StubServer.Reply(503),
            StubServer.Reply(200, """{"data":{"id":"device-1"}}""")
        ).also { it.start() }

        val outcome = post()

        assertEquals("caller should see success", 200, outcome.successCode)
        assertEquals("exactly one callback", 1, outcome.totalCallbacks)
        assertEquals("request should have gone out twice", 2, server.requests.size)
    }

    @Test
    fun `exhausted retries report the real status, not a placeholder`() {
        server = StubServer(StubServer.Reply(503, """{"message":"unavailable"}""")).also { it.start() }

        val outcome = post(watchMs = PAST_LAST_BACKOFF_MS)

        assertEquals("caller should see the server's status", 503, outcome.failureCode)
        assertEquals("exactly one callback", 1, outcome.totalCallbacks)
        assertEquals("there must be no fourth attempt", HTTPClient.MAX_ATTEMPTS, server.requests.size)
    }

    /** 410 is the stop signal; a backoff in front of it would delay the shutdown it orders. */
    @Test
    fun `a rejection is not retried`() {
        server = StubServer(StubServer.Reply(410, """{"message":"Gone"}""")).also { it.start() }

        val outcome = post(watchMs = PAST_FIRST_BACKOFF_MS)

        assertEquals(410, outcome.failureCode)
        assertEquals("410 must not be retried", 1, server.requests.size)
    }

    /**
     * A POST that reached the server but lost its response would be applied twice
     * if resent, so only calls that declared themselves idempotent may retry.
     */
    @Test
    fun `a non-idempotent request is never retried`() {
        server = StubServer(StubServer.Reply(503)).also { it.start() }

        val outcome = post(watchMs = PAST_FIRST_BACKOFF_MS, idempotent = false)

        assertEquals(503, outcome.failureCode)
        assertEquals("a non-idempotent request must fail on the first attempt", 1, server.requests.size)
    }

    /**
     * The airplane-mode shape end to end: the connection dies without any HTTP
     * response (status -1), the retry fires, and the next attempt delivers.
     */
    @Test
    fun `a dropped connection is retried and succeeds`() {
        server = StubServer(
            StubServer.Reply(StubServer.DROP_CONNECTION),
            StubServer.Reply(200)
        ).also { it.start() }

        val outcome = post()

        assertEquals("caller should see success", 200, outcome.successCode)
        assertEquals("exactly one callback", 1, outcome.totalCallbacks)
        assertEquals("request should have gone out twice", 2, server.requests.size)
    }

    @Test
    fun `a retry sends the exact same body`() {
        server = StubServer(StubServer.Reply(500), StubServer.Reply(200)).also { it.start() }
        val body = JSONObject().put("events", "[{\"id\":\"fixed-uuid\"}]").put("k", "참깨라면")

        post(body)

        assertEquals(2, server.requests.size)
        assertEquals(
            "the retried body must be byte-identical, or the backend cannot deduplicate it",
            server.requests[0].body,
            server.requests[1].body
        )
        assertEquals(body.toString(), server.requests[0].body)
    }

    /**
     * The key must survive a no-response retry: if the response was lost after the
     * server processed the request, the resend is only recognisable as a duplicate
     * because it carries the same key.
     */
    @Test
    fun `a no-response retry reuses the idempotency key`() {
        server = StubServer(
            StubServer.Reply(StubServer.DROP_CONNECTION),
            StubServer.Reply(200)
        ).also { it.start() }

        post()

        assertEquals(2, server.requests.size)
        assertNotNull("idempotent POSTs must carry a key", server.requests[0].idempotencyKey)
        assertEquals(
            "a retry after no response must reuse the key",
            server.requests[0].idempotencyKey,
            server.requests[1].idempotencyKey
        )
    }

    /**
     * A received error means the server may have reserved the key without completing
     * the request — retrying with a fresh key keeps that retry deliverable.
     */
    @Test
    fun `a received-error retry rotates the idempotency key`() {
        server = StubServer(StubServer.Reply(500), StubServer.Reply(200)).also { it.start() }

        post()

        assertEquals(2, server.requests.size)
        assertNotNull(server.requests[0].idempotencyKey)
        assertNotNull(server.requests[1].idempotencyKey)
        assertNotEquals(
            "a retry after a received error must use a fresh key",
            server.requests[0].idempotencyKey,
            server.requests[1].idempotencyKey
        )
    }

    /**
     * Only idempotent POSTs carry a key: nothing else has a harmful duplicate to
     * prevent. GET stands in for the value-based methods here because the JVM's
     * HttpURLConnection rejects PATCH (Android's OkHttp-backed engine allows it).
     */
    @Test
    fun `non-idempotent posts and value-based methods carry no idempotency key`() {
        server = StubServer(StubServer.Reply(200), StubServer.Reply(200)).also { it.start() }

        post(idempotent = false)

        val done = CountDownLatch(1)
        HTTPClient.send(server.baseUrl, "GET", "remote-params", null, true,
            object : HTTPClient.ResponseHandler() {
                override fun onSuccess(responseCode: Int, response: JSONObject) = done.countDown()
                override fun onFailure(responseCode: Int, response: JSONObject) = done.countDown()
            })
        assertTrue(done.await(TIMEOUT_SECONDS, TimeUnit.SECONDS))

        assertEquals(2, server.requests.size)
        assertNull("a non-idempotent POST must not carry a key", server.requests[0].idempotencyKey)
        assertNull("a value-based method must not carry a key", server.requests[1].idempotencyKey)
    }

    /** 409 (idempotency conflict) is terminal: one attempt, one callback, no stop. */
    @Test
    fun `a 409 fails once without retry`() {
        server = StubServer(StubServer.Reply(409, """{"message":"Idempotent request is already being processed"}"""))
            .also { it.start() }

        val outcome = post(watchMs = PAST_FIRST_BACKOFF_MS)

        assertEquals(409, outcome.failureCode)
        assertEquals("exactly one callback", 1, outcome.totalCallbacks)
        assertEquals("409 must not be retried", 1, server.requests.size)
    }

    // MARK: - Helpers

    private class Outcome {
        val successes = AtomicInteger()
        val failures = AtomicInteger()
        @Volatile var successCode: Int? = null
        @Volatile var failureCode: Int? = null
        val totalCallbacks: Int get() = successes.get() + failures.get()
    }

    /**
     * @param watchMs how long to keep watching after the callback. A test asserting
     *   that no retry happened has to outlast the backoff that retry would have used,
     *   or a scheduled-but-not-fired attempt slips past the assertion unseen.
     */
    private fun post(body: JSONObject = JSONObject(), watchMs: Long = SETTLE_MS, idempotent: Boolean = true): Outcome {
        val outcome = Outcome()
        val done = CountDownLatch(1)

        HTTPClient.send(server.baseUrl, "POST", "events", body, idempotent,
            object : HTTPClient.ResponseHandler() {
                override fun onSuccess(responseCode: Int, response: JSONObject) {
                    outcome.successes.incrementAndGet()
                    outcome.successCode = responseCode
                    done.countDown()
                }

                override fun onFailure(responseCode: Int, response: JSONObject) {
                    outcome.failures.incrementAndGet()
                    outcome.failureCode = responseCode
                    done.countDown()
                }
            })

        assertTrue("request did not finish in time", done.await(TIMEOUT_SECONDS, TimeUnit.SECONDS))
        Thread.sleep(watchMs)
        return outcome
    }

    private companion object {
        /** Comfortably longer than the full backoff chain so a slow CI machine does not flake. */
        const val TIMEOUT_SECONDS = 15L

        /** Window left open after the callback, to catch a duplicate dispatch. */
        const val SETTLE_MS = 300L

        /** Outlasts the first backoff (500-1000ms, pinned above), catching a wrongly scheduled retry. */
        const val PAST_FIRST_BACKOFF_MS = 1_200L

        /** Outlasts the second backoff (1500-3000ms), for asserting there is no extra attempt. */
        const val PAST_LAST_BACKOFF_MS = 3_500L
    }
}
