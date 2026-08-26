package com.flarelane

import android.util.Log
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the log level contract: `LOG_LEVEL_NONE` must sit above every android.util.Log priority so
 * both of [Logger]'s gates fail, and the legacy android.util.Log values callers already pass must
 * keep working. The constants are compile-time inlined into consumers (including the React Native
 * and Flutter plugins), so their meaning cannot be changed later without breaking those builds.
 */
class LogLevelTest {
    @Test
    fun `none suppresses both verbose and error`() {
        FlareLane.setLogLevel(FlareLane.LOG_LEVEL_NONE)

        assertEquals(FlareLane.LOG_LEVEL_NONE, Logger.logLevel)
        assertFalse("verbose must be gated off", Logger.logLevel <= Log.VERBOSE)
        assertFalse("error must be gated off", Logger.logLevel <= Log.ERROR)
    }

    @Test
    fun `error passes failures only`() {
        FlareLane.setLogLevel(FlareLane.LOG_LEVEL_ERROR)

        assertTrue(Logger.logLevel <= Log.ERROR)
        assertFalse(Logger.logLevel <= Log.VERBOSE)
    }

    @Test
    fun `verbose passes everything`() {
        FlareLane.setLogLevel(FlareLane.LOG_LEVEL_VERBOSE)

        assertTrue(Logger.logLevel <= Log.VERBOSE)
        assertTrue(Logger.logLevel <= Log.ERROR)
    }

    @Test
    fun `legacy android util Log values keep their meaning`() {
        FlareLane.setLogLevel(Log.VERBOSE)
        assertTrue(Logger.logLevel <= Log.VERBOSE)

        FlareLane.setLogLevel(Log.ERROR)
        assertTrue(Logger.logLevel <= Log.ERROR)
        assertFalse(Logger.logLevel <= Log.VERBOSE)
    }
}
