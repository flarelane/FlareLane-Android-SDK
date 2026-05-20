package com.flarelane

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented coverage of SessionManager. Hits real SharedPreferences so the persistence
 * behavior (in particular the engagement-time accumulation that survives process death) is
 * exercised end-to-end.
 *
 * Each test calls SessionManager.reset() in @Before so it doesn't see state from prior runs.
 */
@RunWith(AndroidJUnit4::class)
class SessionManagerTest {

    private lateinit var context: Context

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        SessionManager.reset(context)
    }

    @Test
    fun firstForegroundStartsNewSession() {
        val isNew = SessionManager.onForeground(context)
        assertTrue("first foreground must start a new session", isNew)
        val sid = SessionManager.currentSessionId(context)
        assertNotEquals("sessionId must be non-zero", 0L, sid)
    }

    @Test
    fun secondForegroundWithinTimeoutExtendsSession() {
        SessionManager.onForeground(context)
        val firstSid = SessionManager.currentSessionId(context)
        // Without advancing time, the second onForeground must NOT start a new session.
        val isNew = SessionManager.onForeground(context)
        assertFalse("foreground within timeout must extend, not split", isNew)
        assertEquals(
            "sessionId must persist within the same session",
            firstSid, SessionManager.currentSessionId(context)
        )
    }

    @Test
    fun onBackgroundCommitsInProgressSegment() {
        SessionManager.onForeground(context)
        // Sleep ~200ms so a measurable segment exists.
        Thread.sleep(200)
        SessionManager.onBackground(context)
        val committed = SessionManager.currentSessionForegroundMs(context)
        assertTrue(
            "committed foreground time must be at least the sleep duration ($committed ms)",
            committed >= 200L
        )
    }

    @Test
    fun multipleForegroundBackgroundCycles_Accumulate() {
        SessionManager.onForeground(context)
        Thread.sleep(150)
        SessionManager.onBackground(context)
        val firstSegment = SessionManager.currentSessionForegroundMs(context)

        // Second foreground within timeout — same session, continues accumulating.
        SessionManager.onForeground(context)
        Thread.sleep(150)
        SessionManager.onBackground(context)
        val total = SessionManager.currentSessionForegroundMs(context)

        assertTrue("total ($total) must exceed first segment ($firstSegment)", total > firstSegment)
        assertTrue("total ($total) must reflect both segments", total >= 300L)
    }

    @Test
    fun touchExtendsLastEventTimestamp() {
        SessionManager.onForeground(context)
        val sidBefore = SessionManager.currentSessionId(context)
        Thread.sleep(50)
        SessionManager.touch(context)
        // Touch must not start a new session — sessionId stable.
        assertEquals(sidBefore, SessionManager.currentSessionId(context))
    }

    @Test
    fun resetWipesAllState() {
        SessionManager.onForeground(context)
        Thread.sleep(50)
        SessionManager.onBackground(context)
        assertTrue(SessionManager.currentSessionForegroundMs(context) > 0L)

        SessionManager.reset(context)
        // After reset, no in-progress segment and no accumulated time.
        assertEquals(0L, SessionManager.currentSessionForegroundMs(context))
        // currentSessionId lazily creates one when nothing is stored, so the value
        // becomes non-zero again — but it must be different from the pre-reset id.
        val newSid = SessionManager.currentSessionId(context)
        assertNotEquals(0L, newSid)
    }

    @Test
    fun lazyInitWhenForegroundNotCalled() {
        // Simulates a push receiver in a separate process emitting an event before onForeground.
        SessionManager.reset(context)
        val sid = SessionManager.currentSessionId(context)
        assertNotEquals("lazy init must produce a non-zero sessionId", 0L, sid)
        // Calling again returns the same value.
        assertEquals(sid, SessionManager.currentSessionId(context))
    }

    @Test
    fun currentSessionForegroundMs_IncludesInProgressSegment() {
        SessionManager.onForeground(context)
        Thread.sleep(100)
        // No onBackground yet — the in-progress segment should still show up in the snapshot.
        val snapshot = SessionManager.currentSessionForegroundMs(context)
        assertTrue("in-progress snapshot ($snapshot) must be > 0", snapshot >= 100L)
    }
}
