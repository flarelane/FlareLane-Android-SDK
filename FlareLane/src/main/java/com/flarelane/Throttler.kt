package com.flarelane

import java.util.Date

/**
 * Throttler class to prevent rapid execution of actions
 * Similar to iOS SDK implementation
 */
class Throttler(private val interval: Long) {
    private var lastActionTime: Date = Date(0) // Equivalent to .distantPast in iOS

    /**
     * Execute action only if enough time has passed since last execution
     * @param action The action to execute if throttling allows
     */
    fun throttle(action: () -> Unit) {
        val now = Date()
        val distance = now.time - lastActionTime.time

        if (distance <= interval) {
            Logger.verbose("Throttler", "throttled")
            return
        }

        lastActionTime = now
        action()
    }
}
