package com.flarelane

/**
 * Process-lifetime in-memory dedup for SDK-auto events (CLICKED, FOREGROUND_RECEIVED,
 * BACKGROUND_RECEIVED). Keyed by "<eventType>:<notificationId>". When the in-memory cap is
 * reached the entire set is cleared.
 *
 * Cross-process / cross-restart retries (FCM redeliver after process kill) are not covered by
 * this layer and are expected to be handled by server-side deduplication.
 */
internal object EventDeduplicator {
    private val seen = mutableSetOf<String>()
    private const val CAP = 200

    /**
     * Returns true if (eventType, notificationId) has been observed before in this process.
     * Otherwise records it and returns false. Caller should early-return on true.
     */
    @Synchronized
    fun markAndCheckDuplicate(eventType: String, notificationId: String): Boolean {
        val key = "$eventType:$notificationId"
        if (seen.contains(key)) return true
        if (seen.size >= CAP) seen.clear()
        seen.add(key)
        return false
    }
}
