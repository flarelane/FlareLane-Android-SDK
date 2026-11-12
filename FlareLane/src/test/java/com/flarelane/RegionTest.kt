package com.flarelane

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Pins the region contract: which values are accepted, which host each one reaches,
 * and what initWithContext saves. Every region is a separate deployment that only
 * knows its own projects, so a value that slipped through to the wrong host would
 * send a project's data to a region that has never heard of it.
 */
class RegionTest {

    private val queue = TaskQueueManager.getInstance()

    @After
    fun tearDown() {
        // resetDevice touches the shared queue; leave it as the other specs expect.
        queue.reset()
    }

    /** In-memory SharedPreferences — enough for BaseSharedPreferences' get/put/remove/commit. */
    private class FakePreferences : SharedPreferences {
        val values = HashMap<String?, Any?>()

        override fun getAll(): MutableMap<String, *> =
            values.entries.filter { it.key != null }.associate { it.key!! to it.value }.toMutableMap()
        override fun getString(key: String?, defValue: String?): String? = values[key] as String? ?: defValue
        @Suppress("UNCHECKED_CAST")
        override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? =
            values[key] as MutableSet<String>? ?: defValues
        override fun getInt(key: String?, defValue: Int): Int = values[key] as Int? ?: defValue
        override fun getLong(key: String?, defValue: Long): Long = values[key] as Long? ?: defValue
        override fun getFloat(key: String?, defValue: Float): Float = values[key] as Float? ?: defValue
        override fun getBoolean(key: String?, defValue: Boolean): Boolean = values[key] as Boolean? ?: defValue
        override fun contains(key: String?): Boolean = values.containsKey(key)
        override fun edit(): SharedPreferences.Editor = FakeEditor()
        override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}
        override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}

        // Writes land immediately; commit just reports success, as the SDK only checks that.
        inner class FakeEditor : SharedPreferences.Editor {
            override fun putString(key: String?, value: String?): SharedPreferences.Editor = put(key, value)
            override fun putStringSet(key: String?, value: MutableSet<String>?): SharedPreferences.Editor = put(key, value)
            override fun putInt(key: String?, value: Int): SharedPreferences.Editor = put(key, value)
            override fun putLong(key: String?, value: Long): SharedPreferences.Editor = put(key, value)
            override fun putFloat(key: String?, value: Float): SharedPreferences.Editor = put(key, value)
            override fun putBoolean(key: String?, value: Boolean): SharedPreferences.Editor = put(key, value)
            override fun remove(key: String?): SharedPreferences.Editor {
                values.remove(key)
                return this
            }
            override fun clear(): SharedPreferences.Editor {
                values.clear()
                return this
            }
            override fun commit(): Boolean = true
            override fun apply() {}

            private fun put(key: String?, value: Any?): SharedPreferences.Editor {
                values[key] = value
                return this
            }
        }
    }

    // Application() under returnDefaultValues is an inert Context: no system services (so
    // init stops at the device registration step, before any request) and no package
    // manager — only the preferences are real.
    private fun contextWith(prefs: SharedPreferences): Context = object : Application() {
        override fun getSharedPreferences(name: String?, mode: Int): SharedPreferences = prefs
    }

    // MARK: - Accepted values

    @Test
    fun `no region means the default region`() {
        assertEquals("us", HTTPClient.normalizeRegion(null))
    }

    @Test
    fun `region codes are trimmed and lowercased`() {
        assertEquals("us", HTTPClient.normalizeRegion("us"))
        assertEquals("kr", HTTPClient.normalizeRegion("kr"))
        assertEquals("kr", HTTPClient.normalizeRegion(" KR "))
        assertEquals("kr", HTTPClient.normalizeRegion("Kr"))
        assertEquals("a".repeat(16), HTTPClient.normalizeRegion("a".repeat(16)))
    }

    @Test
    fun `malformed region codes are rejected, not routed to the default`() {
        for (value in listOf("", "   ", "k r", "1kr", "kr-1", "kr_", "kr.", "한국", "a".repeat(17))) {
            assertNull("\"$value\" must be rejected", HTTPClient.normalizeRegion(value))
        }
    }

    // MARK: - Hosts

    @Test
    fun `default region keeps the existing host byte for byte`() {
        assertEquals("https://service-api.flarelane.com/", HTTPClient.baseUrlFor(null))
        assertEquals("https://service-api.flarelane.com/", HTTPClient.baseUrlFor("us"))
    }

    @Test
    fun `other regions resolve to their own subdomain`() {
        assertEquals("https://service-api.kr.flarelane.com/", HTTPClient.baseUrlFor("kr"))
    }

    // MARK: - What init saves

    @Test
    fun `init saves the normalized region next to the projectId`() {
        val prefs = FakePreferences()
        val context = contextWith(prefs)

        FlareLane.initWithContext(context, "p", " KR ", false)

        assertEquals("p", BaseSharedPreferences.getProjectId(context, true))
        assertEquals("kr", BaseSharedPreferences.getRegion(context))
        assertEquals("kr", FlareLane.getRegion(context))
    }

    @Test
    fun `an invalid region aborts init without saving anything`() {
        val fresh = contextWith(FakePreferences())
        FlareLane.initWithContext(fresh, "p", "", false)
        assertNull(BaseSharedPreferences.getProjectId(fresh, true))
        assertNull(BaseSharedPreferences.getRegion(fresh))

        // An app that was already set up keeps its previous state untouched.
        val prefs = FakePreferences()
        val context = contextWith(prefs)
        BaseSharedPreferences.setProjectId(context, "p")
        BaseSharedPreferences.setRegion(context, "kr")
        BaseSharedPreferences.setDeviceId(context, "d1")

        FlareLane.initWithContext(context, "p2", "k r", false)

        assertEquals("p", BaseSharedPreferences.getProjectId(context, true))
        assertEquals("kr", BaseSharedPreferences.getRegion(context))
        assertEquals("d1", BaseSharedPreferences.getDeviceId(context, true))
    }

    @Test
    fun `changing only the region keeps the registered device`() {
        // A project lives in one region, so a region change alone is a misconfiguration:
        // the release that fixes it must resume the same device, not register a second one.
        val prefs = FakePreferences()
        val context = contextWith(prefs)
        BaseSharedPreferences.setProjectId(context, "p")
        BaseSharedPreferences.setDeviceId(context, "d1")
        BaseSharedPreferences.setIsSubscribed(context, true)

        FlareLane.initWithContext(context, "p", "kr", false)

        assertEquals("d1", BaseSharedPreferences.getDeviceId(context, true))
        assertEquals("true", BaseSharedPreferences.getIsSubscribed(context, true))
        assertEquals("kr", BaseSharedPreferences.getRegion(context))
        assertEquals("p", BaseSharedPreferences.getProjectId(context, true))

        FlareLane.initWithContext(context, "p", false)

        assertEquals("d1", BaseSharedPreferences.getDeviceId(context, true))
        assertEquals("us", BaseSharedPreferences.getRegion(context))

        // A projectId change still drops the device.
        FlareLane.initWithContext(context, "p2", "kr", false)

        assertNull(BaseSharedPreferences.getDeviceId(context, true))
        assertEquals("p2", BaseSharedPreferences.getProjectId(context, true))
    }

    @Test
    fun `upgrading without a region keeps the registered device`() {
        // An install from before the option: projectId and device saved, no region.
        val prefs = FakePreferences()
        val context = contextWith(prefs)
        BaseSharedPreferences.setProjectId(context, "p")
        BaseSharedPreferences.setDeviceId(context, "d1")

        FlareLane.initWithContext(context, "p", false)
        assertEquals("d1", BaseSharedPreferences.getDeviceId(context, true))

        FlareLane.initWithContext(context, "p", "us", false)
        assertEquals("d1", BaseSharedPreferences.getDeviceId(context, true))
        assertEquals("us", FlareLane.getRegion(context))
    }

    // MARK: - Processes that never ran init

    /**
     * A process the OS starts for a notification may never call initWithContext. Every
     * request path reads the region from the same preferences as the projectId it sends,
     * so such a process still reaches the region init saved earlier.
     */
    @Test
    fun `a process that never ran init sends to the saved region`() {
        val saved = contextWith(FakePreferences())
        BaseSharedPreferences.setRegion(saved, "kr")
        assertEquals("https://service-api.kr.flarelane.com/", HTTPClient.baseUrlFor(BaseSharedPreferences.getRegion(saved)))

        val nothingSaved = contextWith(FakePreferences())
        assertEquals("https://service-api.flarelane.com/", HTTPClient.baseUrlFor(BaseSharedPreferences.getRegion(nothingSaved)))
    }

    // MARK: - Accessor and reset

    @Test
    fun `getRegion reports the default region before any init`() {
        assertEquals("us", FlareLane.getRegion(contextWith(FakePreferences())))
    }

    @Test
    fun `resetDevice forgets the region along with the projectId`() {
        val prefs = FakePreferences()
        val context = contextWith(prefs)
        FlareLane.initWithContext(context, "p", "kr", false)

        FlareLane.resetDevice(context)

        assertNull(BaseSharedPreferences.getProjectId(context, true))
        assertNull(BaseSharedPreferences.getRegion(context))
        assertEquals("us", FlareLane.getRegion(context))
    }
}
