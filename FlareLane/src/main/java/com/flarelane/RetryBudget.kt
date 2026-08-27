package com.flarelane

import java.util.concurrent.atomic.AtomicInteger

/**
 * Caps how many requests may be waiting out a backoff at the same time.
 *
 * Retries multiply outstanding work: during a long outage each failing call would park two more
 * attempts, so the pile grows several times faster than the calls arrive. Past the limit a request
 * is failed straight away instead of being parked.
 *
 * This bounds retries only. A host app that keeps calling while offline still queues its original
 * requests — bounding those means holding events somewhere durable, which this SDK deliberately
 * does not do.
 */
internal class RetryBudget(private val limit: Int) {

    private val waiting = AtomicInteger()

    /**
     * Takes a slot if one is free.
     *
     * @return true when a slot was taken, in which case the caller must [release] it once the retry
     *         starts; false when the budget is full and the request should give up now.
     */
    fun tryAcquire(): Boolean {
        // CAS rather than increment-then-check: the transient limit+1 the latter shows a racing
        // caller would refuse a slot that a concurrent release() had just freed.
        while (true) {
            val current = waiting.get()
            if (current >= limit) return false
            if (waiting.compareAndSet(current, current + 1)) return true
        }
    }

    fun release() {
        waiting.decrementAndGet()
    }

    /** Visible for tests. */
    fun waitingCount(): Int = waiting.get()
}
