package com.flarelane

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Pins the shape of the body that register / activate send to the server. The
 * notificationPermission field is new (Android previously omitted it; iOS already had it) — this
 * test guards against accidentally dropping it in a future refactor.
 */
@RunWith(AndroidJUnit4::class)
class DeviceServiceSystemInfoTest {

    private lateinit var context: Context

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
    }

    @Test
    fun systemInfo_ContainsRequiredFields() {
        val info = DeviceService.getSystemInfo(context)
        // Identity fields the server consumes for routing & analytics
        assertEquals("android", info.getString("platform"))
        assertNotNull(info.getString("deviceModel"))
        assertNotNull(info.getString("osVersion"))
        assertNotNull(info.getString("sdkVersion"))
        assertNotNull(info.getString("timeZone"))
        assertNotNull(info.getString("languageCode"))
        assertNotNull(info.getString("countryCode"))
        assertNotNull(info.getString("sdkType"))
    }

    @Test
    fun systemInfo_ContainsNotificationPermissionField() {
        // The presence of this field is the contract — value depends on runtime permission state.
        // (Default for instrumented test on Android 13+ is false since POST_NOTIFICATIONS is not
        // granted; on older versions it defaults to true. We assert presence + boolean type.)
        val info = DeviceService.getSystemInfo(context)
        assertTrue(
            "notificationPermission field must be present in register/activate body",
            info.has("notificationPermission")
        )
        // getBoolean throws if missing/non-bool, so this both validates type and records the value.
        info.getBoolean("notificationPermission")
    }

    @Test
    fun systemInfo_SdkVersionMatchesConstant() {
        val info = DeviceService.getSystemInfo(context)
        assertEquals(FlareLane.SdkInfo.version, info.getString("sdkVersion"))
    }
}
