package com.flarelane

import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * End-to-end coverage for the stop contract, in two halves that meet in the middle:
 *
 * 1. a real HTTP response travels through [HTTPClient] (socket, HttpURLConnection, response parsing)
 *    and arrives at a handler with the right status code
 * 2. [DeviceService.stopSdkIfGone] turns that status code into a stopped [TaskQueueManager]
 *
 * The base URL stays a final constant in production — the request under test is sent through the
 * package-private overload that takes one as a parameter, so nothing can redirect live traffic.
 *
 * Scenario ids match E2E_TESTING.md.
 */
class StopContractE2ETest {
    private lateinit var server: StubServer
    private lateinit var manager: TaskQueueManager
    private val devicesPath = "internal/v1/projects/00000000-0000-4000-8000-00000000e2e0/devices"

    private fun task(name: String) = object : NamedRunnable(name) {
        override fun run() {}
    }

    @Before
    fun setUp() {
        server = StubServer().apply { start() }
        manager = TaskQueueManager.getInstance()
        manager.reset()
    }

    @After
    fun tearDown() {
        server.stop()
        manager.reset()
    }

    private class Result {
        var success: Boolean? = null
        var code: Int? = null
        var body: JSONObject? = null
    }

    /** Sends one request at the stub and waits for the SDK's handler to be invoked. */
    private fun send(method: String, path: String = devicesPath): Result {
        val result = Result()
        val done = CountDownLatch(1)
        HTTPClient.sendRequestWithBody(
            server.baseUrl, method, path, JSONObject().put("platform", "android"),
            object : HTTPClient.ResponseHandler() {
                override fun onSuccess(responseCode: Int, response: JSONObject) {
                    result.success = true; result.code = responseCode; result.body = response
                    done.countDown()
                }

                override fun onFailure(responseCode: Int, response: JSONObject) {
                    result.success = false; result.code = responseCode; result.body = response
                    done.countDown()
                }
            })
        assertTrue("the SDK never invoked its response handler", done.await(5, TimeUnit.SECONDS))
        return result
    }

    // S2 — a real 410 arrives as a failure carrying the status code.
    @Test
    fun `a real 410 response reaches the handler as a failure`() {
        server.script("/devices", listOf(StubServer.Response.gone()))

        val result = send("POST")

        assertEquals(false, result.success)
        assertEquals(410, result.code)
        assertEquals("Gone", result.body?.optString("error"))
        assertTrue("410 must be recognised as gone", DeviceService.isGone(result.code!!))
    }

    // S2 — and that status code stops the queue.
    @Test
    fun `410 stops the queue and refuses further work`() {
        repeat(5) { manager.addTask(task("queued-$it")) }
        assertEquals(5, manager.queueSize())

        DeviceService.stopSdkIfGone(410, devicesPath)

        assertEquals("stop() must empty the queue", 0, manager.queueSize())
        assertFalse("a stopped SDK must refuse new work", manager.addTask(task("after-stop")))
    }

    // S3 — 404 is an ordinary failure.
    @Test
    fun `a real 404 does not stop the sdk`() {
        server.script("/devices", listOf(StubServer.Response.status(404)))

        val result = send("POST")
        assertEquals(404, result.code)
        DeviceService.stopSdkIfGone(result.code!!, devicesPath)

        assertTrue("404 must leave the SDK running", manager.addTask(task("after-404")))
    }

    /**
     * S4 — statuses a retry policy will treat as transient.
     *
     * Also locks down that the real status code survives the transport. HttpURLConnection throws a
     * plain IOException for these (only 404 and 410 get FileNotFoundException), and the SDK used to
     * report -1 for all of them, which would have left a retry policy blind.
     */
    @Test
    fun `transient failures keep their status code and do not stop the sdk`() {
        for (status in listOf(408, 429, 500, 502, 503)) {
            manager.reset()
            server.script("/devices", listOf(StubServer.Response.status(status)))

            val result = send("POST")
            assertEquals(status, result.code)
            DeviceService.stopSdkIfGone(result.code!!, devicesPath)

            assertTrue("status $status must not stop the SDK", manager.addTask(task("after-$status")))
        }
    }

    // S6 — reset lifts the stopped state so the next launch works.
    @Test
    fun `reset recovers after stop`() {
        DeviceService.stopSdkIfGone(410, devicesPath)
        assertFalse(manager.addTask(task("stopped")))

        manager.reset()

        assertTrue("reset must lift the stopped state", manager.addTask(task("after-reset")))
    }

    // D1 — one call must produce exactly one request, with the payload actually on the wire.
    @Test
    fun `one call sends exactly one request`() {
        server.script("/devices", listOf(StubServer.Response.created("""{"data":{"id":"d"}}""")))

        val result = send("POST")

        assertEquals(1, server.countRequests("/devices"))
        val request = server.requests.first()
        assertEquals("POST", request.method)
        assertTrue("the body the SDK sent must carry the payload", request.body.contains("platform"))
        assertEquals(true, result.success)
        assertNotNull(result.body)
    }

    // D2 — the queue is the ordering guarantee a retry policy must not break.
    @Test
    fun `queued tasks run in order`() {
        val order = Collections.synchronizedList(mutableListOf<Int>())
        val all = CountDownLatch(10)

        repeat(10) { i ->
            manager.addTask(object : NamedRunnable("ordered-$i") {
                override fun run() {
                    order.add(i)
                    all.countDown()
                    completeTask()
                }
            })
        }
        manager.onInitialized()

        assertTrue("tasks did not all run", all.await(10, TimeUnit.SECONDS))
        assertEquals("the queue must preserve FIFO order", (0 until 10).toList(), order.toList())
    }

    // D3 — the harness can express "fails, then succeeds", the shape a retry policy needs.
    @Test
    fun `harness supports per-call response sequences`() {
        server.script(
            "/devices",
            listOf(StubServer.Response.status(503), StubServer.Response.created("""{"data":{"id":"d"}}"""))
        )

        assertEquals(503, send("POST").code)
        assertEquals(true, send("POST").success)
        assertEquals(2, server.countRequests("/devices"))
    }

    // Malformed bodies must still reach the handler, or a task would hang until its timeout.
    @Test
    fun `a non-json body still reaches the handler as a failure`() {
        server.script("/devices", listOf(StubServer.Response(200, "not json at all")))

        val result = send("POST")

        assertEquals(false, result.success)
        assertEquals(200, result.code)
    }
}
