package com.flarelane

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 410 is the server's only directive to stop the SDK. The decision is made on the status code
 * alone so the SDK never has to parse a response body.
 */
class DeviceServiceTest {
    @Test
    fun `410 means the project or device is gone`() {
        assertTrue(DeviceService.isGone(410))
    }

    @Test
    fun `no other status stops the SDK`() {
        listOf(200, 201, 400, 401, 403, 404, 409, 429, 500, 503).forEach {
            assertFalse("status $it must not stop the SDK", DeviceService.isGone(it))
        }
    }
}
