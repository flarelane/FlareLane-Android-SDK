package com.flarelane

import android.content.Context

/**
 * Dedup helper for push notification lifecycle events (received + clicked).
 *
 * Any of `NotificationReceivedEvent.display()` or `NotificationClickedActivity` can
 * be invoked more than once for what the user perceives as a single event:
 *
 *   - FCM may redeliver `onMessageReceived` for the same message (best-effort
 *     transport, not exactly-once).
 *   - `event.display()` can be called multiple times by a foreground handler.
 *   - The same PendingIntent can re-fire on rapid re-tap, SINGLE_TOP edge cases,
 *     or cold start launch flows.
 *
 * Without dedup the SDK ends up POSTing the same `FOREGROUND_RECEIVED`,
 * `BACKGROUND_RECEIVED`, or `CLICKED` event N times — breaking statistical
 * integrity. Mirrors iOS's `NotificationClickProcessor` in intent, generalized
 * here to cover received events too. Distinct event types for the same
 * notification id remain independent (a receive followed by a click is two
 * legitimate events, not a dedup hit).
 *
 * Storage is hybrid:
 *   - In-memory `LinkedHashSet` for hot-path checks (FIFO, capped at [MAX_SIZE]).
 *   - SharedPreferences-backed comma-separated string so dedup survives process
 *     restart. This matches the best-class pattern in OneSignal Android, where
 *     the notification table persists an OPENED flag — adapted to
 *     SharedPreferences because FlareLane has no SQLite layer.
 *
 * The cache is loaded lazily on first call and reused for subsequent checks;
 * every accept also persists immediately so a force-stop right after the event
 * cannot lose the dedup state.
 *
 * Android `StringSet` is intentionally avoided: its iteration order isn't
 * guaranteed, so FIFO trimming would behave inconsistently.
 *
 * The [Storage] indirection is what lets JVM unit tests exercise the persistence
 * contract without needing Robolectric or instrumentation — the production code
 * path on a device still goes through [SharedPrefsStorage].
 */
internal object NotificationEventProcessor {
    /**
     * Maximum number of dedup keys retained. Picked to match the iOS bound
     * (`processedNotificationIds` Set with the same cap) so behavior stays
     * aligned across SDKs. The bound is across all event types combined,
     * which is fine because each (id, eventType) pair is a distinct key.
     */
    internal const val MAX_SIZE = 1000

    private const val PREFS_NAME = "com.flarelane.processedNotificationEventKeys"
    private const val PREFS_KEY = "keys"

    /** Abstracted IO so unit tests can substitute an in-memory implementation. */
    internal interface Storage {
        fun load(): String
        fun save(value: String)
    }

    private var storage: Storage? = null

    /** Null until the first call loads from storage. */
    private var cache: LinkedHashSet<String>? = null

    /**
     * Returns `true` the first time [eventType] for [notificationId] is observed,
     * `false` for every subsequent call within the retention window. The dedup
     * key is `notificationId#eventType` so the same notification can produce one
     * RECEIVED and one CLICKED — they live on different keys.
     *
     * Persists synchronously so subsequent process launches see the same answer.
     * Safe to call from any thread.
     */
    @Synchronized
    fun shouldProcess(context: Context, notificationId: String, eventType: String): Boolean {
        return shouldProcessWith(getOrCreateStorage(context), notificationId, eventType)
    }

    @Synchronized
    internal fun shouldProcessWith(storage: Storage, notificationId: String, eventType: String): Boolean {
        val key = compositeKey(notificationId, eventType)
        val keys = loadCache(storage)
        if (keys.contains(key)) return false
        keys.add(key)
        trimToMax(keys)
        storage.save(keys.joinToString(","))
        return true
    }

    private fun compositeKey(notificationId: String, eventType: String): String {
        // notificationId is a server-issued UUID and eventType is a fixed SDK
        // constant (`CLICKED` / `FOREGROUND_RECEIVED` / `BACKGROUND_RECEIVED`),
        // so neither can legitimately contain '#'. The require() gates exist as
        // a cheap defense in depth: if a future caller passes a tainted value,
        // we fail loudly instead of silently colliding keys (which would let a
        // duplicate event slip past dedup).
        require(!notificationId.contains("#")) { "notificationId must not contain '#' separator" }
        require(!eventType.contains("#")) { "eventType must not contain '#' separator" }
        return "$notificationId#$eventType"
    }

    /** Test-only: inject a fake storage and drop the in-memory cache. */
    @Synchronized
    internal fun setStorageForTesting(s: Storage?) {
        storage = s
        cache = null
    }

    /** Test-only: drop the in-memory cache so the next call re-reads from storage. */
    @Synchronized
    internal fun resetCacheForTesting() {
        cache = null
    }

    private fun getOrCreateStorage(context: Context): Storage {
        storage?.let { return it }
        val created = SharedPrefsStorage(context.applicationContext)
        storage = created
        return created
    }

    private fun loadCache(storage: Storage): LinkedHashSet<String> {
        cache?.let { return it }
        val loaded = LinkedHashSet<String>()
        val raw = storage.load()
        if (raw.isNotEmpty()) {
            // Filter out empty tokens so a malformed (e.g. leading/trailing comma)
            // stored value doesn't pollute the set with empty strings.
            raw.split(",").forEach { token ->
                if (token.isNotEmpty()) loaded.add(token)
            }
        }
        cache = loaded
        return loaded
    }

    private fun trimToMax(keys: LinkedHashSet<String>) {
        while (keys.size > MAX_SIZE) {
            val iter = keys.iterator()
            iter.next()
            iter.remove()
        }
    }

    private class SharedPrefsStorage(private val context: Context) : Storage {
        override fun load(): String {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            return prefs.getString(PREFS_KEY, "") ?: ""
        }

        override fun save(value: String) {
            // Use commit() (synchronous) rather than apply() (async): a force-stop
            // immediately after a click would otherwise lose the dedup state — the very
            // race this persistent layer exists to defend against. The payload is a
            // bounded comma-separated id list (cap MAX_SIZE=1000), so synchronous IO
            // here stays well under any ANR-relevant threshold even when called on the
            // main thread from NotificationClickedActivity.onCreate.
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val committed = prefs.edit().putString(PREFS_KEY, value).commit()
            if (!committed) {
                Logger.error("NotificationEventProcessor: failed to persist dedup keys")
            }
        }
    }
}
