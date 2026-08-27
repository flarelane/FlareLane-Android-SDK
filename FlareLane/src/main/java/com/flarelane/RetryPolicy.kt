package com.flarelane

import java.util.Random

/**
 * Decides whether a failed request is worth sending again, and how long to wait first.
 *
 * Deliberately free of Android and networking types: the retry contract is the part most worth
 * pinning down with tests, and this way it can be exercised on the JVM without a device or a socket.
 */
internal object RetryPolicy {

    /** Status reported when a request never produced an HTTP response (DNS, connect, read, timeout). */
    const val NO_RESPONSE = -1

    /** Total attempts allowed per request, the first one included. */
    const val MAX_ATTEMPTS = 3

    /** Base wait before attempt N+1, indexed by `attempt - 1`. */
    private val BASE_DELAYS_MS = longArrayOf(1000L, 3000L)

    /**
     * A transient failure is worth another attempt; a rejection the server would simply repeat is not.
     *
     * @param responseCode HTTP status, or [NO_RESPONSE] when the request never got one
     * @param attempt 1-based number of the attempt that just failed
     */
    @JvmStatic
    fun shouldRetry(responseCode: Int, attempt: Int): Boolean = when {
        attempt >= MAX_ATTEMPTS -> false
        responseCode == NO_RESPONSE -> true // the connection never completed — the next one still might
        responseCode >= 500 -> true // server-side and typically short-lived
        // Every other 4xx is a decision the server will reach again, 410 (gone) included.
        else -> responseCode == 408 || responseCode == 429
    }

    /**
     * Half the base delay plus a random share of the other half.
     *
     * The random part matters: devices that lost connectivity together would otherwise come back
     * in lockstep and retry as one burst.
     */
    @JvmStatic
    fun delayMillis(attempt: Int, random: Random): Long {
        val half = BASE_DELAYS_MS[attempt.coerceIn(1, BASE_DELAYS_MS.size) - 1] / 2
        return half + random.nextInt(half.toInt() + 1)
    }
}
