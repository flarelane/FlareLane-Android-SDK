package com.flarelane

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit specs for the action-button feature: JSON parsing, click-target resolution, and the
 * malformed-entry tolerance contract. These run on the JVM only — no Android framework calls.
 */
class NotificationButtonTest {

    private fun makePayload(buttonsJson: String?): JSONObject {
        val payload = JSONObject()
            .put("notificationId", "notif-1")
            .put("body", "hello")
            .put("data", "{}")
            .put("title", "Title")
            .put("url", "https://example.com/body")
        if (buttonsJson != null) payload.put("buttons", buttonsJson)
        return payload
    }

    @Test
    fun `buttonList parses well-formed JSON array`() {
        val buttons = JSONArray()
            .put(JSONObject().put("label", "Open").put("link", "https://example.com/a"))
            .put(JSONObject().put("label", "Share"))
            .toString()

        val notification = Notification(makePayload(buttons))

        assertEquals(2, notification.buttonList.size)
        assertEquals("Open", notification.buttonList[0].label)
        assertEquals("https://example.com/a", notification.buttonList[0].link)
        assertEquals("Share", notification.buttonList[1].label)
        assertNull(notification.buttonList[1].link)
    }

    @Test
    fun `buttonList is empty when buttons field is absent`() {
        val notification = Notification(makePayload(null))
        assertTrue(notification.buttonList.isEmpty())
    }

    @Test
    fun `buttonList skips entries with empty label rather than failing entire list`() {
        val buttons = JSONArray()
            .put(JSONObject().put("label", "Good").put("link", "https://example.com/ok"))
            .put(JSONObject().put("label", "")) // empty label — should be skipped
            .put(JSONObject().put("link", "https://example.com/no-label")) // no label key
            .put(JSONObject().put("label", "AlsoGood"))
            .toString()

        val notification = Notification(makePayload(buttons))

        assertEquals(2, notification.buttonList.size)
        assertEquals("Good", notification.buttonList[0].label)
        assertEquals("AlsoGood", notification.buttonList[1].label)
    }

    @Test
    fun `buttonList returns empty list for malformed JSON instead of throwing`() {
        val notification = Notification(makePayload("not a json array"))
        assertTrue(notification.buttonList.isEmpty())
    }

    @Test
    fun `clickedButton resolves index via withClickedButtonIndex`() {
        val buttons = JSONArray()
            .put(JSONObject().put("label", "First").put("link", "https://example.com/1"))
            .put(JSONObject().put("label", "Second").put("link", "https://example.com/2"))
            .toString()

        val notification = Notification(makePayload(buttons)).withClickedButtonIndex(1)

        assertNotNull(notification.clickedButton)
        assertEquals("Second", notification.clickedButton!!.label)
        assertEquals("https://example.com/2", notification.clickedUrl)
    }

    @Test
    fun `clickedUrl is the body url for body clicks`() {
        val notification = Notification(makePayload(null))
        // Body click branch: `clickedUrl` resolves to the notification body's `url`.
        assertNull(notification.clickedButton)
        assertEquals("https://example.com/body", notification.clickedUrl)
    }

    @Test
    fun `clickedUrl is null on a button click whose button has no link`() {
        // Button click with a link → uses button.link.
        val buttonsWithLink = JSONArray()
            .put(JSONObject().put("label", "Only").put("link", "https://example.com/only"))
            .toString()
        val viaButton = Notification(makePayload(buttonsWithLink)).withClickedButtonIndex(0)
        assertEquals("https://example.com/only", viaButton.clickedUrl)

        // Out-of-range index is still a button click — but with no resolvable link, this
        // returns null. Critically, it does NOT fall through to the body's url:
        // button and body URLs are distinct destinations and must not be conflated.
        val outOfRange = Notification(makePayload(buttonsWithLink)).withClickedButtonIndex(5)
        assertNull(outOfRange.clickedButton)
        assertNull(outOfRange.clickedUrl)
        // Sanity: the body still has its own url; we're just not surfacing it via clickedUrl.
        assertEquals("https://example.com/body", outOfRange.url)
    }
}
