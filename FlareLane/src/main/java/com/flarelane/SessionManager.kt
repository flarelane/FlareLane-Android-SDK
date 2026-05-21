package com.flarelane

import android.content.Context
import android.content.SharedPreferences

/**
 * Session tracking with engagement-time accumulation (Firebase Analytics style).
 *
 * Why engagement_time instead of a session_end event:
 *   - session_end events are unreliable: process kill (OS memory pressure, user task-swipe)
 *     happens between the last event and the would-be session_end fire, so the network call
 *     never lands and the server-side session length is wrong.
 *   - Instead, each event carries `sessionForegroundMs` — how long the user has been actively
 *     in the foreground in this session so far. The server takes max(sessionForegroundMs) per
 *     sessionId as the session length. As long as *any* event from the session reached the
 *     server, the length is accurate up to that event's emission point.
 *
 * Persistence model:
 *   - SessionManager owns its own SharedPreferences file (separate from BaseSharedPreferences,
 *     which holds SDK-wide identity state) to keep the session domain self-contained.
 *   - `sessionId` / `lastEventAt` / `sessionForegroundMs` are persisted: they need to survive
 *     a process restart so a quick relaunch within the timeout stays in the same session and so
 *     the accumulated foreground time isn't lost to an OS-initiated kill.
 *   - `foregroundStartedAt` is in-memory only: it represents an in-progress engagement segment
 *     that's only meaningful within one process lifetime. On process death the segment is lost,
 *     but it's a tiny tail (max ~the time between the last event and onStop, which is committed
 *     synchronously). The next process starts cleanly.
 */
/**
 * Pure decision functions for [SessionManager]. Kept as a separate `object` (same file) so JVM
 * unit tests can hit the truth table without loading SessionManager's SharedPreferences I/O.
 */
internal object SessionMath {

    /** True iff the inactivity gap exceeds [timeoutMs] — strict `>` so the boundary stays alive. */
    @JvmStatic
    fun shouldStartNewSession(now: Long, lastEventAt: Long, timeoutMs: Long): Boolean {
        if (lastEventAt == 0L) return true
        return now - lastEventAt > timeoutMs
    }

    /**
     * onBackground: fold the in-progress segment into the accumulator (0 if no segment).
     * Clamps the delta to >= 0 so a wall-clock rewind (NTP / user setting) can't drag the
     * accumulator backwards.
     */
    @JvmStatic
    fun commitInProgressSegment(prevAccumulated: Long, foregroundStartedAt: Long, now: Long): Long {
        if (foregroundStartedAt <= 0L) return prevAccumulated
        return prevAccumulated + (now - foregroundStartedAt).coerceAtLeast(0L)
    }

    /** Read-side snapshot: accumulated + any still-in-progress segment. */
    @JvmStatic
    fun foregroundSnapshot(accumulated: Long, foregroundStartedAt: Long, now: Long): Long {
        if (foregroundStartedAt <= 0L) return accumulated
        return accumulated + (now - foregroundStartedAt).coerceAtLeast(0L)
    }
}

internal object SessionManager {
    private const val INACTIVITY_TIMEOUT_MS = 5 * 60 * 1000L

    private const val PREFS_NAME = "com.flarelane.session"
    private const val KEY_SESSION_ID = "sessionId"
    private const val KEY_LAST_EVENT_AT = "lastEventAt"
    private const val KEY_SESSION_FOREGROUND_MS = "sessionForegroundMs"

    @Volatile private var foregroundStartedAt: Long = 0L

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /**
     * Called when the process enters the foreground. Starts a new session if the last event is
     * older than [INACTIVITY_TIMEOUT_MS], otherwise extends the existing session. Either way,
     * records the start of a new in-progress engagement segment.
     *
     * @return true if a new session was started.
     */
    @JvmStatic
    fun onForeground(context: Context): Boolean {
        val now = System.currentTimeMillis()
        val p = prefs(context)
        val lastEventAt = p.getLong(KEY_LAST_EVENT_AT, 0L)
        return if (SessionMath.shouldStartNewSession(now, lastEventAt, INACTIVITY_TIMEOUT_MS)) {
            p.edit()
                .putLong(KEY_SESSION_ID, now)
                .putLong(KEY_LAST_EVENT_AT, now)
                .putLong(KEY_SESSION_FOREGROUND_MS, 0L)
                .apply()
            foregroundStartedAt = now
            Logger.info("Session", "new session started", mapOf("sessionId" to now))
            true
        } else {
            p.edit().putLong(KEY_LAST_EVENT_AT, now).apply()
            foregroundStartedAt = now
            false
        }
    }

    /**
     * Called when the process enters the background. Commits the in-progress engagement segment
     * into the cumulative `sessionForegroundMs` so it survives subsequent process death. Uses
     * commit() rather than apply() so the write lands before a possible OS kill.
     */
    @JvmStatic
    fun onBackground(context: Context) {
        val startedAt = foregroundStartedAt
        if (startedAt == 0L) return
        val now = System.currentTimeMillis()
        val p = prefs(context)
        val accumulated = p.getLong(KEY_SESSION_FOREGROUND_MS, 0L)
        val next = SessionMath.commitInProgressSegment(accumulated, startedAt, now)
        p.edit().putLong(KEY_SESSION_FOREGROUND_MS, next).commit()
        foregroundStartedAt = 0L
    }

    /**
     * Returns the current sessionId, lazily initializing if [onForeground] hasn't fired yet
     * (rare — e.g., a push receiver in a separate process emits an event before the main process
     * is started).
     */
    @JvmStatic
    @Synchronized
    fun currentSessionId(context: Context): Long {
        val p = prefs(context)
        val sid = p.getLong(KEY_SESSION_ID, 0L)
        if (sid != 0L) return sid
        val now = System.currentTimeMillis()
        p.edit()
            .putLong(KEY_SESSION_ID, now)
            .putLong(KEY_LAST_EVENT_AT, now)
            .apply()
        return now
    }

    /**
     * Cumulative user-active foreground time within the current session, including any segment
     * currently in progress. Each event in a session carries this value so server-side can
     * compute the precise session length as `max(sessionForegroundMs)` over the session's events.
     */
    @JvmStatic
    fun currentSessionForegroundMs(context: Context): Long {
        val accumulated = prefs(context).getLong(KEY_SESSION_FOREGROUND_MS, 0L)
        return SessionMath.foregroundSnapshot(accumulated, foregroundStartedAt, System.currentTimeMillis())
    }

    /** Extends the current session — call when any event fires. */
    @JvmStatic
    fun touch(context: Context) {
        prefs(context).edit().putLong(KEY_LAST_EVENT_AT, System.currentTimeMillis()).apply()
    }

    /**
     * Wipe all session state. Called when the device identity is reset (projectId changed,
     * resetDevice). The next foreground entry will start a fresh session.
     */
    @JvmStatic
    fun reset(context: Context) {
        prefs(context).edit().clear().apply()
        foregroundStartedAt = 0L
    }
}
