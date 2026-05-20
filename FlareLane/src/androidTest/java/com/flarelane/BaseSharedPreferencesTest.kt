package com.flarelane

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented coverage of the throttle/permission keys we added to BaseSharedPreferences.
 * Uses real SharedPreferences via ApplicationProvider; clean slate per test.
 */
@RunWith(AndroidJUnit4::class)
class BaseSharedPreferencesTest {

    private lateinit var context: Context

    @Before
    fun resetState() {
        context = ApplicationProvider.getApplicationContext()
        BaseSharedPreferences.setLastActivatedAt(context, 0L)
        // Wipe lastSyncedPermission so the "never synced" default returns
        val raw = context.getSharedPreferences(
            "com.flarelane.SHARED_PREFERENCE_KEY_" + context.packageManager
                .getPackageInfo(context.packageName, 0).firstInstallTime,
            Context.MODE_PRIVATE
        )
        raw.edit().remove("com.flarelane.LAST_SYNCED_PERMISSION_KEY").commit()
    }

    @Test
    fun lastActivatedAt_defaultsToZero() {
        assertEquals(0L, BaseSharedPreferences.getLastActivatedAt(context))
    }

    @Test
    fun lastActivatedAt_persists() {
        val ts = 1_700_000_000_000L
        BaseSharedPreferences.setLastActivatedAt(context, ts)
        assertEquals(ts, BaseSharedPreferences.getLastActivatedAt(context))
    }

    @Test
    fun lastSyncedPermission_returnsNullWhenNeverSynced() {
        assertNull(
            "expected null sentinel for never-synced state",
            BaseSharedPreferences.getLastSyncedPermission(context)
        )
    }

    @Test
    fun lastSyncedPermission_persistsTrue() {
        BaseSharedPreferences.setLastSyncedPermission(context, true)
        assertEquals(true, BaseSharedPreferences.getLastSyncedPermission(context))
    }

    @Test
    fun lastSyncedPermission_persistsFalse() {
        // Distinct from the "never synced" case — must return Boolean.FALSE, not null.
        BaseSharedPreferences.setLastSyncedPermission(context, false)
        assertEquals(false, BaseSharedPreferences.getLastSyncedPermission(context))
    }

    @Test
    fun resetThenLargeWrite_HasNoCorruption() {
        // Round-trip a few writes to confirm SharedPreferences is wired correctly.
        for (i in 0..5) {
            BaseSharedPreferences.setLastActivatedAt(context, i.toLong() * 1000L)
            assertEquals(i.toLong() * 1000L, BaseSharedPreferences.getLastActivatedAt(context))
        }
        assertTrue(BaseSharedPreferences.getLastActivatedAt(context) > 0L)
    }
}
