package com.flarelane

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Coverage for the device-state lifecycle hooks (resetDevice, projectId switch) — the points
 * where activate-throttle state must be wiped so a stale device doesn't bleed statistics
 * into a new identity.
 *
 * These are instrumented tests because they exercise the real SharedPreferences via
 * BaseSharedPreferences. The actual register/activate HTTP calls are not exercised here
 * (network-dependent).
 */
@RunWith(AndroidJUnit4::class)
class DeviceLifecycleTest {

    private lateinit var context: Context

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        // Establish a known state for each test: stale activation present.
        BaseSharedPreferences.setLastActivatedAt(context, 1_700_000_000_000L)
        BaseSharedPreferences.setLastSyncedPermission(context, true)
    }

    @Test
    fun resetDevice_WipesLastActivatedAt() {
        // Pre-condition
        assertTrue(BaseSharedPreferences.getLastActivatedAt(context) > 0L)
        FlareLane.resetDevice(context)
        assertEquals(
            "resetDevice must clear the activate throttle so the next launch fires a fresh activate",
            0L, BaseSharedPreferences.getLastActivatedAt(context)
        )
    }

    @Test
    fun resetDevice_WipesDeviceIdentity() {
        // Set some identity, then reset.
        BaseSharedPreferences.setDeviceId(context, "fake-device-id")
        BaseSharedPreferences.setUserId(context, "user-1")
        BaseSharedPreferences.setProjectId(context, "proj-1")
        BaseSharedPreferences.setPushToken(context, "token-xyz")
        BaseSharedPreferences.setIsSubscribed(context, true)

        FlareLane.resetDevice(context)

        // All identity fields cleared. Use nullable=true to read without throwing.
        assertNull(BaseSharedPreferences.getDeviceId(context, true))
        assertNull(BaseSharedPreferences.getUserId(context, true))
        assertNull(BaseSharedPreferences.getProjectId(context, true))
        assertNull(BaseSharedPreferences.getPushToken(context, true))
    }

    @Test
    fun initWithContext_DifferentProjectId_ResetsThrottle() {
        // Simulate an existing project's persisted state.
        BaseSharedPreferences.setProjectId(context, "old-project-id")
        BaseSharedPreferences.setDeviceId(context, "old-device-id")
        BaseSharedPreferences.setLastActivatedAt(context, 1_700_000_000_000L)

        // Switch to a different projectId via initWithContext.
        FlareLane.initWithContext(context, "new-project-id", false)
        // Initialize is async (lifecycle observer is registered via mainHandler.post), but the
        // reset of stale state happens synchronously in the initWithContext call itself.
        assertEquals(
            "switching projectId must wipe the previous device's activate throttle",
            0L, BaseSharedPreferences.getLastActivatedAt(context)
        )
        // deviceId must be cleared so the next foreground triggers a fresh register
        // (nullable=true to read without throwing).
        assertNull(
            "switching projectId must wipe deviceId",
            BaseSharedPreferences.getDeviceId(context, true)
        )
    }

    @Test
    fun initWithContext_SameProjectId_PreservesIdentity() {
        BaseSharedPreferences.setProjectId(context, "stable-project-id")
        BaseSharedPreferences.setDeviceId(context, "device-stays")
        BaseSharedPreferences.setLastActivatedAt(context, 1_700_000_000_000L)

        FlareLane.initWithContext(context, "stable-project-id", false)

        assertEquals(
            "deviceId must persist when projectId is unchanged",
            "device-stays", BaseSharedPreferences.getDeviceId(context, true)
        )
        assertTrue(
            "lastActivatedAt must persist when projectId is unchanged",
            BaseSharedPreferences.getLastActivatedAt(context) > 0L
        )
    }

    @Test
    fun initWithContext_NullProjectId_NoOps() {
        // Host app misconfiguration (typo, env-var fallthrough) shouldn't silently corrupt
        // persisted state. Without the guard the null projectId would overwrite SharedPreferences
        // and every subsequent SDK call would no-op against a null projectId.
        BaseSharedPreferences.setProjectId(context, "existing-project")
        BaseSharedPreferences.setDeviceId(context, "existing-device")

        FlareLane.initWithContext(context, null, false)

        assertEquals(
            "null projectId must not overwrite an existing valid projectId",
            "existing-project", BaseSharedPreferences.getProjectId(context, true)
        )
        assertEquals(
            "null projectId must not wipe deviceId",
            "existing-device", BaseSharedPreferences.getDeviceId(context, true)
        )
    }

    @Test
    fun initWithContext_BlankProjectId_NoOps() {
        // Same protection for "" or "  " (trimmed empty) — common mistake.
        BaseSharedPreferences.setProjectId(context, "existing-project")
        BaseSharedPreferences.setDeviceId(context, "existing-device")

        FlareLane.initWithContext(context, "   ", false)

        assertEquals(
            "blank projectId must not overwrite an existing valid projectId",
            "existing-project", BaseSharedPreferences.getProjectId(context, true)
        )
        assertEquals(
            "blank projectId must not wipe deviceId",
            "existing-device", BaseSharedPreferences.getDeviceId(context, true)
        )
    }
}
