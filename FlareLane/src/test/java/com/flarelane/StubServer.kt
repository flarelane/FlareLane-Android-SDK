package com.flarelane

import java.io.InputStream
import java.net.ServerSocket
import java.net.Socket
import java.util.Collections

/**
 * Minimal HTTP server on localhost, so [HTTPClient] is exercised over a real socket. Status
 * handling, error-stream reading and the retry loop all behave differently against a mock, and the
 * two properties this suite cares about — how many requests actually went out, and whether a retry
 * put the same bytes on the wire — only exist at this level.
 *
 * Replies are consumed one per request. Past the end of the script the last entry repeats, so a
 * test only spells out the replies it cares about.
 */
// Callers always script at least one reply — an empty stub server has no meaning,
// so the constructor stays free of defensive checks on purpose.
class StubServer(private vararg val scripted: Reply) {

    /** [DROP_CONNECTION] as status slams the socket shut without an HTTP response — the
     *  airplane-mode shape: the client sees an IOException and no status code at all. */
    data class Reply(val status: Int, val body: String = """{"data":{}}""", val delayMs: Long = 0)

    companion object {
        const val DROP_CONNECTION = 0
    }

    data class Recorded(val method: String, val path: String, val body: String, val idempotencyKey: String?)

    private val socket = ServerSocket(0)
    private val log = Collections.synchronizedList(mutableListOf<Recorded>())

    @Volatile
    private var running = true

    /** Base URL in the shape [HTTPClient] expects: an origin with a trailing slash. */
    val baseUrl: String get() = "http://127.0.0.1:${socket.localPort}/"

    val requests: List<Recorded> get() = synchronized(log) { log.toList() }

    fun start() {
        thread {
            while (running) {
                try {
                    val client = socket.accept()
                    // Each connection on its own thread: HttpURLConnection reconnects for some
                    // statuses, and a sequential accept loop would leave that second connection
                    // waiting behind the reply it is reacting to.
                    thread { client.use { serve(it) } }
                } catch (e: Exception) {
                    if (running) throw e
                }
            }
        }
    }

    fun stop() {
        running = false
        socket.close()
    }

    private fun serve(client: Socket) {
        // Read the request as bytes throughout: Content-Length counts bytes, and decoding the
        // headers through a Reader first would make a multi-byte body come up short.
        val input = client.getInputStream().buffered()
        val requestLine = readLine(input) ?: return
        val parts = requestLine.split(" ")

        var contentLength = 0
        var idempotencyKey: String? = null
        while (true) {
            val header = readLine(input) ?: break
            if (header.isEmpty()) break
            if (header.lowercase().startsWith("content-length:")) {
                contentLength = header.substringAfter(":").trim().toIntOrNull() ?: 0
            }
            if (header.lowercase().startsWith("idempotency-key:")) {
                idempotencyKey = header.substringAfter(":").trim()
            }
        }

        val body = readBody(input, contentLength)
        val index: Int
        synchronized(log) {
            index = log.size
            log.add(Recorded(parts.getOrElse(0) { "" }, parts.getOrElse(1) { "" }, body, idempotencyKey))
        }

        val reply = scripted.getOrElse(index) { scripted.last() }
        if (reply.delayMs > 0) {
            Thread.sleep(reply.delayMs)
        }

        if (reply.status == DROP_CONNECTION) {
            client.close()
            return
        }

        val payload = reply.body.toByteArray()
        client.getOutputStream().apply {
            write(
                ("HTTP/1.1 ${reply.status} X\r\n" +
                    "Content-Type: application/json\r\n" +
                    "Content-Length: ${payload.size}\r\n" +
                    "Connection: close\r\n\r\n").toByteArray()
            )
            write(payload)
            flush()
        }
    }

    /** Reads one CRLF-terminated header line without buffering past it into the body. */
    private fun readLine(input: InputStream): String? {
        val line = StringBuilder()
        while (true) {
            val byte = input.read()
            if (byte < 0) return if (line.isEmpty()) null else line.toString()
            if (byte == '\n'.code) return line.toString().removeSuffix("\r")
            line.append(byte.toChar())
        }
    }

    private fun readBody(input: InputStream, contentLength: Int): String {
        if (contentLength <= 0) return ""

        val buffer = ByteArray(contentLength)
        var read = 0
        while (read < contentLength) {
            val n = input.read(buffer, read, contentLength - read)
            if (n < 0) break
            read += n
        }
        return String(buffer, 0, read, Charsets.UTF_8)
    }

    private fun thread(block: () -> Unit) =
        Thread(block).apply { isDaemon = true }.start()
}
