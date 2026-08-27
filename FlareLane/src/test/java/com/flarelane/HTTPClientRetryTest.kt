package com.flarelane

import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * Covers the retry loop end to end against a local server: what the caller ends up seeing, how many
 * times the request actually went out, and that the handler contract of exactly one callback still
 * holds once a request can be sent more than once.
 */
class HTTPClientRetryTest {

    private lateinit var server: StubServer

    @After
    fun tearDown() {
        server.stop()
    }

    @Test
    fun `a request that fails once succeeds on the retry`() {
        server = StubServer(
            StubServer.Reply(503),
            StubServer.Reply(200, """{"data":{"id":"device-1"}}""")
        ).also { it.start() }

        val outcome = post()

        assertEquals("caller should see success", 200, outcome.successCode)
        assertEquals("no failure should be reported", 0, outcome.failureCount)
        assertEquals("exactly one callback", 1, outcome.totalCallbacks)
        assertEquals("request should have gone out twice", 2, server.requests.size)
    }

    @Test
    fun `a request that keeps failing reports the real status, not a placeholder`() {
        server = StubServer(StubServer.Reply(503)).also { it.start() }

        val outcome = post(watchMs = PAST_LAST_BACKOFF_MS)

        assertEquals("caller should see the server's status", 503, outcome.failureCode)
        assertEquals("exactly one callback", 1, outcome.totalCallbacks)
        assertEquals(RetryPolicy.MAX_ATTEMPTS, server.requests.size)
    }

    /**
     * Regression guard. HttpURLConnection throws from getInputStream for every error status, and
     * treating that as a transport failure used to collapse 503 into "no response" — which hid both
     * the retryable statuses and the 410 stop signal.
     */
    @Test
    fun `an error status survives as itself`() {
        server = StubServer(StubServer.Reply(429, """{"message":"slow down"}""")).also { it.start() }

        val outcome = post()

        assertEquals(429, outcome.failureCode)
        assertEquals("slow down", outcome.failureBody?.optString("message"))
    }

    @Test
    fun `a rejection the server would repeat is not retried`() {
        server = StubServer(StubServer.Reply(404, """{"message":"Project not found"}""")).also { it.start() }

        val outcome = post(watchMs = PAST_FIRST_BACKOFF_MS)

        assertEquals(404, outcome.failureCode)
        assertEquals("404 must not be retried", 1, server.requests.size)
    }

    /** 410 stops the SDK for the rest of the run; the backoff chain must not delay that. */
    @Test
    fun `gone is delivered without a retry`() {
        server = StubServer(StubServer.Reply(410, """{"message":"Gone"}""")).also { it.start() }

        val outcome = post(watchMs = PAST_FIRST_BACKOFF_MS)

        assertEquals(410, outcome.failureCode)
        assertEquals("410 must not be retried", 1, server.requests.size)
    }

    @Test
    fun `a retry sends the exact same body`() {
        server = StubServer(StubServer.Reply(500), StubServer.Reply(200)).also { it.start() }

        post(JSONObject().put("events", "[{\"id\":\"fixed-uuid\"}]"))

        assertEquals(2, server.requests.size)
        assertEquals(
            "the retried body must be byte-identical, or the backend cannot deduplicate it",
            server.requests[0].body,
            server.requests[1].body
        )
    }

    /**
     * A 200 whose body cannot be parsed is not something a handler can act on: onSuccess would hand
     * it an empty object where it expects data, and the caller would fail silently. Repeating the
     * request would not produce a different answer, so it resolves as a failure without a retry.
     */
    @Test
    fun `a success status with an unusable body is reported as a failure`() {
        server = StubServer(StubServer.Reply(200, "<html>not json</html>")).also { it.start() }

        val outcome = post(watchMs = PAST_FIRST_BACKOFF_MS)

        assertEquals("must not be delivered as success", 0, outcome.successes.get())
        assertEquals(200, outcome.failureCode)
        assertEquals("exactly one callback", 1, outcome.totalCallbacks)
        assertEquals("an unparseable 2xx must not be retried", 1, server.requests.size)
    }

    /** 204 and 205 are defined to carry no body, so an empty one is the expected shape, not a fault. */
    @Test
    fun `a bodyless success status is reported as a success`() {
        server = StubServer(StubServer.Reply(204, "")).also { it.start() }

        val outcome = post(watchMs = PAST_FIRST_BACKOFF_MS)

        assertEquals(204, outcome.successCode)
        assertEquals(0, outcome.failureCount)
        assertEquals("exactly one callback", 1, outcome.totalCallbacks)
        assertEquals("a success must not be retried", 1, server.requests.size)
    }

    /** Content-Length counts bytes, so a multi-byte payload must survive the round trip intact. */
    @Test
    fun `a multi-byte body is sent and retried unchanged`() {
        server = StubServer(StubServer.Reply(500), StubServer.Reply(200)).also { it.start() }
        val body = JSONObject().put("type", "\uad6c\ub9e4\uc644\ub8cc").put("data", "\ud14c\uc2a4\ud2b8 \uc774\ubca4\ud2b8")

        post(body)

        assertEquals(2, server.requests.size)
        assertEquals(body.toString(), server.requests[0].body)
        assertEquals(server.requests[0].body, server.requests[1].body)
    }

    @Test
    fun `an exception thrown by the caller's handler does not reach the host app`() {
        server = StubServer(StubServer.Reply(200)).also { it.start() }

        // Watching the callback thread directly: the latch below is counted down before the throw,
        // so it alone would pass even if the exception escaped the SDK.
        val escaped = CopyOnWriteArrayList<Throwable>()
        val previousHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { _, throwable -> escaped.add(throwable) }

        try {
            val done = CountDownLatch(1)
            HTTPClient.send(server.baseUrl, "POST", "events", JSONObject(),
                object : HTTPClient.ResponseHandler() {
                    override fun onSuccess(responseCode: Int, response: JSONObject) {
                        done.countDown()
                        throw IllegalStateException("host app handler blew up")
                    }
                })

            assertTrue(done.await(TIMEOUT_SECONDS, TimeUnit.SECONDS))
            // A handler exception must not be mistaken for a transport failure and retried.
            Thread.sleep(PAST_FIRST_BACKOFF_MS)

            assertEquals("the exception must not escape the SDK", emptyList<Throwable>(), escaped)
            assertEquals(1, server.requests.size)
        } finally {
            Thread.setDefaultUncaughtExceptionHandler(previousHandler)
        }
    }

    /**
     * A handler is host-app code and may block indefinitely. Blocked handlers must not occupy the
     * transport pool, or a handful of them would stall every later request and scheduled retry.
     */
    @Test
    fun `a blocked handler does not stop later requests from going out`() {
        server = StubServer(StubServer.Reply(200)).also { it.start() }

        val blockedHandlersStarted = CountDownLatch(BLOCKING_HANDLERS)
        val unblock = CountDownLatch(1)

        repeat(BLOCKING_HANDLERS) {
            HTTPClient.send(server.baseUrl, "POST", "blocking", JSONObject(),
                object : HTTPClient.ResponseHandler() {
                    override fun onSuccess(responseCode: Int, response: JSONObject) {
                        blockedHandlersStarted.countDown()
                        unblock.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)
                    }
                })
        }
        assertTrue("handlers never ran", blockedHandlersStarted.await(TIMEOUT_SECONDS, TimeUnit.SECONDS))

        try {
            val outcome = post()
            assertEquals("a later request must still complete", 200, outcome.successCode)
        } finally {
            unblock.countDown()
        }
    }

    // MARK: - Helpers

    private class Outcome {
        val successes = AtomicInteger()
        val failures = AtomicInteger()

        @Volatile var successCode: Int? = null
        @Volatile var failureCode: Int? = null
        @Volatile var failureBody: JSONObject? = null

        val failureCount: Int get() = failures.get()
        val totalCallbacks: Int get() = successes.get() + failures.get()
    }

    /**
     * @param watchMs how long to keep watching after the callback. A test asserting that no retry
     *   happened has to outlast the backoff that retry would have used, or a scheduled-but-not-yet-
     *   fired attempt slips past the assertion and is hidden by `server.stop()`.
     */
    private fun post(body: JSONObject = JSONObject(), watchMs: Long = SETTLE_MS): Outcome {
        val outcome = Outcome()
        val done = CountDownLatch(1)

        HTTPClient.send(server.baseUrl, "POST", "events", body,
            object : HTTPClient.ResponseHandler() {
                override fun onSuccess(responseCode: Int, response: JSONObject) {
                    outcome.successes.incrementAndGet()
                    outcome.successCode = responseCode
                    done.countDown()
                }

                override fun onFailure(responseCode: Int, response: JSONObject) {
                    outcome.failures.incrementAndGet()
                    outcome.failureCode = responseCode
                    outcome.failureBody = response
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

        /**
         * Outlasts the first backoff (500-1000ms, pinned by RetryPolicyTest), so a request that was
         * wrongly scheduled for a second attempt is caught rather than missed.
         */
        const val PAST_FIRST_BACKOFF_MS = 1_200L

        /** Outlasts the second backoff (1500-3000ms), for asserting there is no fourth attempt. */
        const val PAST_LAST_BACKOFF_MS = 3_500L

        /** More than the transport pool holds, so a shared pool would deadlock the next request. */
        const val BLOCKING_HANDLERS = 6
    }
}
