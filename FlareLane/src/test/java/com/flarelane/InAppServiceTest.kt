package com.flarelane

import com.flarelane.model.ModelInAppMessage
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Specs for the InAppService callback contract: every exit path in `getMessage` must invoke
 * the caller-supplied callback exactly once, and `parseFirstMessage` must never throw. Both
 * guarantees are what keep the FlareLane TaskQueueManager from stalling for TIMEOUT_MS (10s)
 * when a fetch fails or the response body is unexpected.
 */
class InAppServiceTest {

    @After
    fun tearDown() {
        // Reset shared state so tests do not leak into each other.
        InAppService.isDisplaying = false
    }

    @Test
    fun `getMessage invokes callback with null when isDisplaying guard fires`() {
        InAppService.isDisplaying = true

        var callCount = 0
        var received: ModelInAppMessage? = ModelInAppMessage(id = "sentinel", htmlString = "sentinel")
        InAppService.getMessage("p", "d", "g", JSONObject()) { model ->
            callCount++
            received = model
        }

        // Guard path must invoke exactly once with null; the flag stays true because the
        // in-flight request that set it is still expected to reset it on completion.
        assertEquals(1, callCount)
        assertNull(received)
    }

    @Test
    fun `parseFirstMessage returns model for well-formed response`() {
        val response = JSONObject().put(
            "data", JSONArray().put(
                JSONObject()
                    .put("id", "msg-1")
                    .put("htmlString", "<html></html>")
            )
        )

        val model = InAppService.parseFirstMessage(response)

        assertNotNull(model)
        assertEquals("msg-1", model!!.id)
        assertEquals("<html></html>", model.htmlString)
    }

    @Test
    fun `parseFirstMessage returns null when data array is empty`() {
        val response = JSONObject().put("data", JSONArray())

        assertNull(InAppService.parseFirstMessage(response))
    }

    @Test
    fun `parseFirstMessage returns null when data field is missing`() {
        // No `data` key — previously threw JSONException that was caught silently without
        // notifying the caller. Now returns null so the caller can complete its task.
        val response = JSONObject().put("unrelated", "value")

        assertNull(InAppService.parseFirstMessage(response))
    }

    @Test
    fun `parseFirstMessage returns null when first entry is missing required fields`() {
        val response = JSONObject().put(
            "data", JSONArray().put(JSONObject().put("id", "msg-1"))
            // missing "htmlString"
        )

        assertNull(InAppService.parseFirstMessage(response))
    }

    @Test
    fun `parseFirstMessage returns null when data is not an array`() {
        val response = JSONObject().put("data", "not-an-array")

        assertNull(InAppService.parseFirstMessage(response))
    }
}
