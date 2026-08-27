package com.flarelane

import org.json.JSONException
import org.json.JSONObject
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.util.Random
import java.util.concurrent.Executors
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.ThreadFactory
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

/**
 * The SDK's only HTTP entry point.
 *
 * A request that fails for a transient reason — the connection dropped, the server answered 5xx —
 * is sent again a couple of times before the caller is told it failed. Callers see no difference:
 * they still get exactly one onSuccess or onFailure, only now after the retries are spent.
 */
internal object HTTPClient {

    open class ResponseHandler {
        open fun onSuccess(responseCode: Int, response: JSONObject) {
            Logger.verbose("HTTPClient.ResponseHandler.onSuccess: $response")
        }

        open fun onFailure(responseCode: Int, response: JSONObject) {
            Logger.error("HTTPClient.ResponseHandler.onFailure: $response")
        }
    }

    private const val BASE_URL = "https://service-api.flarelane.com/"

    /**
     * HttpURLConnection defaults to waiting forever. On a mobile network that means a request can
     * hang for as long as the process lives, holding its slot in the task queue with it.
     */
    private const val CONNECT_TIMEOUT_MS = 10_000
    private const val READ_TIMEOUT_MS = 10_000

    /** See [RetryBudget] for what this does and does not bound. */
    private val retryBudget = RetryBudget(20)

    /**
     * One shared pool instead of a thread per request.
     *
     * Four threads: the SDK issues few requests at once — the task queue serialises most of them —
     * and a request waiting out its backoff sits on the scheduler's timer rather than occupying a
     * thread. The pool size is also the concurrency ceiling, since a ScheduledThreadPoolExecutor
     * queues past its core size rather than growing, which is the point: an outage can no longer
     * spawn one stalled thread per call the way a thread-per-request client did.
     */
    private val executor = Executors.newScheduledThreadPool(4, daemonThreadFactory("FlareLane-HTTP"))

    /**
     * Handlers run here, never on [executor].
     *
     * A handler is host-app code and may block for as long as it likes. Running it on a transport
     * thread would let a handful of slow handlers occupy the whole pool, stalling new requests and
     * scheduled retries. Sixteen workers bound what pathological handlers can cost (the old
     * thread-per-request client had no bound at all); past that, callbacks queue and arrive late
     * rather than being dropped, so "exactly one callback" still holds. Idle workers time out.
     */
    private val callbackExecutor = ThreadPoolExecutor(
        16, 16, 30, TimeUnit.SECONDS, LinkedBlockingQueue(), daemonThreadFactory("FlareLane-HTTP-callback")
    ).apply { allowCoreThreadTimeOut(true) }

    private val random = Random()

    // MARK: - Entry points

    @JvmStatic
    fun get(path: String, responseHandler: ResponseHandler?) =
        send(BASE_URL, "GET", path, null, idempotent = true, responseHandler = responseHandler)

    /**
     * POSTs are only retried when the caller marks them [idempotent] — a POST that reached the
     * server but lost its response would otherwise be applied twice. Callers may opt in when the
     * request is a read in POST clothing, or when its body carries a deduplication id the backend
     * can recognise. GET/PATCH/DELETE are idempotent by contract here: PATCH bodies are absolute
     * values (last-writer-wins), never increments.
     */
    @JvmStatic
    @JvmOverloads
    fun post(path: String, body: JSONObject?, responseHandler: ResponseHandler?, idempotent: Boolean = false) =
        send(BASE_URL, "POST", path, body, idempotent, responseHandler)

    @JvmStatic
    fun patch(path: String, body: JSONObject?, responseHandler: ResponseHandler?) =
        send(BASE_URL, "PATCH", path, body, idempotent = true, responseHandler = responseHandler)

    @JvmStatic
    fun delete(path: String, body: JSONObject?, responseHandler: ResponseHandler?) =
        send(BASE_URL, "DELETE", path, body, idempotent = true, responseHandler = responseHandler)

    /**
     * The base URL is a parameter rather than a field: the entry points above always pass the final
     * [BASE_URL], so live traffic cannot be redirected, while tests can aim one request at a local
     * stub server and exercise the real socket and retry path.
     *
     * The body is serialised here, not per attempt. The caller still owns the JSONObject it passed —
     * nested event data comes straight from the host app — and a retry that re-read a mutated object
     * would put different bytes on the wire under the same idempotency key.
     */
    @JvmStatic
    fun send(baseUrl: String, method: String, path: String, body: JSONObject?, idempotent: Boolean, responseHandler: ResponseHandler?) {
        val call = Call(baseUrl, method, path, body?.toString(), responseHandler, idempotent)
        try {
            executor.execute { attempt(call, 1) }
        } catch (e: RejectedExecutionException) {
            BaseErrorHandler.handle(e)
            deliver(call, Result.noResponse())
        }
    }

    // MARK: - Attempt loop

    /**
     * Runs one attempt and either schedules the next or hands the outcome to the caller. Every path
     * out of here reaches [deliver], so a caller waiting on the handler is never left hanging — the
     * task queue would otherwise stall until its own timeout.
     */
    private fun attempt(call: Call, attemptNo: Int) {
        val result = execute(call)

        if (result.isSuccess || !call.idempotent || !RetryPolicy.shouldRetry(result.code, attemptNo)) {
            deliver(call, result)
            return
        }

        scheduleRetry(call, attemptNo, result)
    }

    private fun scheduleRetry(call: Call, attemptNo: Int, lastResult: Result) {
        if (!retryBudget.tryAcquire()) {
            Logger.error("Retry budget full, giving up on ${call.describe()} with status ${lastResult.code}")
            deliver(call, lastResult)
            return
        }

        val delayMs = RetryPolicy.delayMillis(attemptNo, random)
        Logger.verbose(
            "Retrying ${call.describe()} in ${delayMs}ms" +
                " (attempt ${attemptNo + 1}/${RetryPolicy.MAX_ATTEMPTS}, status ${lastResult.code})"
        )

        try {
            executor.schedule({
                retryBudget.release()
                attempt(call, attemptNo + 1)
            }, delayMs, TimeUnit.MILLISECONDS)
        } catch (e: RejectedExecutionException) {
            retryBudget.release()
            BaseErrorHandler.handle(e)
            deliver(call, lastResult)
        }
    }

    // MARK: - One attempt

    /** Runs a single attempt end to end. Never throws: every failure comes back as a [Result]. */
    private fun execute(call: Call): Result {
        var conn: HttpURLConnection? = null

        return try {
            conn = open(call)
            writeBody(conn, call)
            readResult(conn)
        } catch (e: Exception) {
            BaseErrorHandler.handle(e)
            Result.noResponse()
        } finally {
            conn?.disconnect()
        }
    }

    private fun open(call: Call): HttpURLConnection {
        val conn = URL(call.baseUrl + call.path).openConnection() as HttpURLConnection

        conn.connectTimeout = CONNECT_TIMEOUT_MS
        conn.readTimeout = READ_TIMEOUT_MS
        conn.useCaches = false
        conn.doInput = true
        // Only opened for output when there is something to write: with doOutput on and no body,
        // HttpURLConnection would wait on an output stream that never gets one.
        conn.doOutput = call.body != null
        conn.requestMethod = call.method

        conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8")
        conn.setRequestProperty("Accept", "application/json")
        conn.setRequestProperty("x-flarelane-sdk-info", "${FlareLane.SdkInfo.type}-${FlareLane.SdkInfo.version}")

        return conn
    }

    private fun writeBody(conn: HttpURLConnection, call: Call) {
        val body = call.body ?: return

        // HttpURLConnection exposes no write timeout — setReadTimeout only bounds waiting for the
        // response — so in principle a server that never drains the request body could park this
        // write. It cannot happen here: every request this SDK sends is a few hundred bytes, far
        // under the socket send buffer, so write() hands off to the kernel and returns without
        // waiting on the peer. Bounding it properly would mean a transport with a write timeout
        // (a new dependency), which is not worth it for payloads this size.
        val bytes = body.toByteArray(Charsets.UTF_8)
        conn.setFixedLengthStreamingMode(bytes.size)
        conn.outputStream.use { it.write(bytes) }

        // Deliberate: the body is logged in full, identifiers included. Diagnosing an integration
        // means seeing exactly what was sent, so verbose logging carries whole HTTP parameters by
        // design across all four FlareLane SDKs. Apps that cannot keep logs call setLogLevel with
        // LOG_LEVEL_NONE, which suppresses every line the SDK emits. Do not reduce this to metadata
        // without changing that contract on all four platforms at once.
        Logger.verbose("HTTP ${call.method} body: $body")
    }

    /**
     * Reads the status line first, then the body.
     *
     * Order matters. HttpURLConnection throws from getInputStream for every error status, so
     * treating that throw as a transport failure would erase the status the retry decision is
     * based on — a 503 would be indistinguishable from an unplugged cable. The exception only
     * means "the body is on the error stream".
     */
    private fun readResult(conn: HttpURLConnection): Result {
        val responseCode = conn.responseCode

        // 204/205 are defined to carry no body, so there is nothing to read or parse. Falling
        // through would hand an empty string to the JSON parser and turn a success into a failure.
        if (responseCode == HttpURLConnection.HTTP_NO_CONTENT || responseCode == HttpURLConnection.HTTP_RESET) {
            return Result(responseCode, JSONObject(), isSuccess = true)
        }

        val stream = try {
            conn.inputStream
        } catch (e: IOException) {
            conn.errorStream ?: run {
                Logger.error("No response body for status $responseCode")
                return Result(responseCode, JSONObject(), isSuccess = false)
            }
        }

        // A read that dies part-way is allowed to throw: [execute] turns it into "no response", so a
        // connection dropped mid-body is retried instead of surfacing as a truncated success.
        val raw = readAll(stream)

        val body = try {
            JSONObject(raw)
        } catch (e: JSONException) {
            // The status is trustworthy, the body is not. Repeating the request would not produce a
            // different answer, so this resolves as a failure the caller can see. The unparseable
            // body is logged in full on purpose — see the note on the request-body log above.
            Logger.error("Failed to parse response JSON: $e, body: $raw")
            return Result(responseCode, JSONObject(), isSuccess = false)
        }

        // 2xx only: a 3xx here means redirect following did not finish the request, and the
        // response is not the resource the caller asked for. Matches the iOS SDK's contract.
        return Result(responseCode, body, isSuccess = responseCode in 200..299)
    }

    private fun readAll(stream: InputStream): String {
        val builder = StringBuilder()
        try {
            BufferedReader(InputStreamReader(stream, Charsets.UTF_8))
                .forEachLine { builder.append(it).append('\n') }
        } finally {
            try {
                stream.close()
            } catch (e: IOException) {
                Logger.error("Failed to close the response stream", e)
            }
        }
        return builder.toString()
    }

    // MARK: - Delivery

    /**
     * The single place a handler is called, so "exactly one of onSuccess/onFailure" holds whichever
     * path finished the request. Exceptions thrown by host-app handler code are contained here
     * instead of unwinding into the attempt loop, where they would look like a transport failure
     * and trigger a second dispatch.
     */
    private fun deliver(call: Call, result: Result) {
        val handler = call.responseHandler ?: return

        try {
            callbackExecutor.execute {
                try {
                    if (result.isSuccess) handler.onSuccess(result.code, result.body)
                    else handler.onFailure(result.code, result.body)
                } catch (e: Exception) {
                    BaseErrorHandler.handle(e)
                }
            }
        } catch (e: RejectedExecutionException) {
            BaseErrorHandler.handle(e)
        }
    }

    /** Daemon threads only: the SDK must never be the reason the host app's process stays alive. */
    private fun daemonThreadFactory(name: String) = ThreadFactory { runnable ->
        Thread(runnable, name).apply { isDaemon = true }
    }

    // MARK: - Value types

    /** One request, held immutable so every retry puts the exact same bytes on the wire. */
    private class Call(
        val baseUrl: String,
        val method: String,
        val path: String,
        /** Already serialised; null for a request without a body. */
        val body: String?,
        val responseHandler: ResponseHandler?,
        /** Whether a lost-response resend is safe. Only idempotent calls enter the retry loop. */
        val idempotent: Boolean
    ) {
        fun describe() = "$method $path"
    }

    /**
     * Outcome of a single attempt.
     *
     * Success is recorded rather than derived from the status: a 2xx whose body could not be parsed
     * is not something a caller can act on, and reporting it as success would hand handlers an
     * empty object where they expect data.
     */
    private class Result(val code: Int, val body: JSONObject, val isSuccess: Boolean) {
        companion object {
            /** Nothing came back at all, so there is no status to reason about. */
            fun noResponse() = Result(RetryPolicy.NO_RESPONSE, JSONObject(), isSuccess = false)
        }
    }
}
