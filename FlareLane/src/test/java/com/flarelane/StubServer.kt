package com.flarelane

import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.ServerSocket
import java.net.Socket
import java.util.Collections
import java.util.concurrent.CountDownLatch

/**
 * Minimal HTTP server for E2E tests.
 *
 * Lets a test drive the real request path — [HTTPClient] → socket → HttpURLConnection → response
 * parsing → handler dispatch — without a network or a live project. Unit tests cover the queue in
 * isolation; this covers the wiring between an actual HTTP status and the SDK reacting to it, plus
 * the two properties that matter for data integrity: how many requests go out and in what order.
 *
 * Responses are scripted per path suffix and consumed in order, so a sequence like
 * `listOf(503, 200)` expresses "fails, then succeeds" for a retry policy.
 */
class StubServer {
    data class Response(val status: Int, val body: String) {
        companion object {
            fun ok(body: String = """{"data":{}}""") = Response(200, body)
            fun created(body: String) = Response(201, body)
            fun gone(message: String = "Project not found") =
                Response(410, """{"statusCode":410,"message":"$message","error":"Gone"}""")
            fun status(code: Int) =
                Response(code, """{"statusCode":$code,"message":"e2e","error":"e2e"}""")
        }
    }

    data class Recorded(val method: String, val path: String, val body: String)

    private val socket = ServerSocket(0)
    private val script = mutableMapOf<String, MutableList<Response>>()
    private val log = Collections.synchronizedList(mutableListOf<Recorded>())
    private var running = true

    /** Base URL to pass to [HTTPClient.sendRequestWithBody]. */
    val baseUrl: String get() = "http://127.0.0.1:${socket.localPort}/"

    val requests: List<Recorded> get() = synchronized(log) { log.toList() }

    fun countRequests(pathSuffix: String) = requests.count { it.path.endsWith(pathSuffix) }

    fun script(pathSuffix: String, responses: List<Response>) {
        synchronized(script) { script[pathSuffix] = responses.toMutableList() }
    }

    fun start() {
        Thread {
            while (running) {
                try {
                    val client = socket.accept()
                    // Each connection on its own thread: HttpURLConnection retries some statuses
                    // (408, and 503 with Retry-After) on a fresh connection, and a sequential accept
                    // loop would leave that retry waiting behind the reply it is retrying.
                    Thread { client.use { serve(it) } }.apply { isDaemon = true }.start()
                } catch (e: Exception) {
                    if (running) throw e
                }
            }
        }.apply { isDaemon = true }.start()
    }

    fun stop() {
        running = false
        socket.close()
    }

    private fun serve(client: Socket) {
        val reader = BufferedReader(InputStreamReader(client.getInputStream()))
        val requestLine = reader.readLine() ?: return
        val parts = requestLine.split(" ")
        val method = parts.getOrElse(0) { "" }
        val path = parts.getOrElse(1) { "" }

        var contentLength = 0
        while (true) {
            val header = reader.readLine() ?: break
            if (header.isEmpty()) break
            if (header.lowercase().startsWith("content-length:")) {
                contentLength = header.substringAfter(":").trim().toIntOrNull() ?: 0
            }
        }

        val body = if (contentLength > 0) {
            val buffer = CharArray(contentLength)
            var read = 0
            while (read < contentLength) {
                val n = reader.read(buffer, read, contentLength - read)
                if (n < 0) break
                read += n
            }
            String(buffer, 0, read)
        } else {
            ""
        }

        log.add(Recorded(method, path, body))

        val response = synchronized(script) {
            val key = script.keys.firstOrNull { path.endsWith(it) }
            val queued = key?.let { script[it] }
            when {
                queued.isNullOrEmpty() -> Response.ok()
                // Keep the last scripted response in place so repeated calls stay deterministic.
                queued.size == 1 -> queued.first()
                else -> queued.removeAt(0)
            }
        }

        val payload = response.body.toByteArray()
        client.getOutputStream().apply {
            write(
                ("HTTP/1.1 ${response.status} X\r\n" +
                    "Content-Type: application/json\r\n" +
                    "Content-Length: ${payload.size}\r\n" +
                    "Connection: close\r\n\r\n").toByteArray()
            )
            write(payload)
            flush()
        }
    }
}
