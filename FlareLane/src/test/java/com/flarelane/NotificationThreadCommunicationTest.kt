package com.flarelane

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit specs for the 1.11.0 payload additions: threadId (notification grouping key) and
 * communication (chat-style sender). JVM-only — no Android framework calls.
 */
class NotificationThreadCommunicationTest {

    private fun makePayload(threadId: String? = null, communicationJson: String? = null): JSONObject {
        val payload = JSONObject()
            .put("notificationId", "notif-1")
            .put("body", "hello")
            .put("data", "{}")
        if (threadId != null) payload.put("threadId", threadId)
        if (communicationJson != null) payload.put("communication", communicationJson)
        return payload
    }

    // MARK: threadId

    @Test
    fun `threadId is parsed from payload and survives the clicked copy`() {
        val notification = Notification(makePayload(threadId = "promo"))

        assertEquals("promo", notification.threadId)
        assertEquals("promo", notification.withClickedButtonIndex(0).threadId)
        assertEquals("promo", notification.toHashMap()["threadId"])
    }

    @Test
    fun `threadId absent or empty is null`() {
        assertNull(Notification(makePayload()).threadId)
        assertNull(Notification(makePayload(threadId = "")).threadId)
    }

    // MARK: communication

    @Test
    fun `communicationData parses a well-formed payload`() {
        val notification = Notification(
            makePayload(communicationJson = """{"senderName":"Kim","senderImageUrl":"https://x/a.png"}""")
        )

        val communication = notification.communicationData
        assertNotNull(communication)
        assertEquals("Kim", communication?.senderName)
        assertEquals("https://x/a.png", communication?.senderImageUrl)
    }

    @Test
    fun `communicationData requires both senderName and senderImageUrl`() {
        assertNull(Notification(makePayload(communicationJson = """{"senderName":"Kim"}""")).communicationData)
        assertNull(Notification(makePayload(communicationJson = """{"senderImageUrl":"https://x/a.png"}""")).communicationData)
        assertNull(Notification(makePayload(communicationJson = """{"senderName":"","senderImageUrl":"https://x/a.png"}""")).communicationData)
    }

    @Test
    fun `communicationData tolerates malformed JSON without throwing`() {
        assertNull(Notification(makePayload(communicationJson = "not json")).communicationData)
        assertNull(Notification(makePayload()).communicationData)
    }

    @Test
    fun `communication round-trips through the bridge map`() {
        val notification = Notification(
            makePayload(communicationJson = """{"senderName":"Kim","senderImageUrl":"https://x/a.png"}""")
        )

        @Suppress("UNCHECKED_CAST")
        val bridged = notification.toHashMap()["communication"] as? HashMap<String, Any?>
        assertEquals("Kim", bridged?.get("senderName"))
        assertEquals("https://x/a.png", bridged?.get("senderImageUrl"))
    }

    // MARK: group summary decisions (pure logic of NotificationGroupManager)

    @Test
    fun `pre 1_11 eight-arg constructor still maps clickedButtonIndex`() {
        // Binary-compat shim: the old (…, clickedButtonIndex) signature must keep resolving.
        val notification = Notification("id", "b", "{}", null, null, null, null, 1)

        assertEquals(1, notification.clickedButtonIndex)
        assertNull(notification.threadId)
        assertNull(notification.communicationData)
    }

    @Test
    fun `summary shows from two children`() {
        assertFalse(NotificationGroupManager.shouldShowSummary(0))
        assertFalse(NotificationGroupManager.shouldShowSummary(1))
        assertTrue(NotificationGroupManager.shouldShowSummary(2))
        assertTrue(NotificationGroupManager.shouldShowSummary(25))
    }

    @Test
    fun `summary lines join title and text, skip empty entries, cap at five`() {
        val lines = NotificationGroupManager.buildSummaryLines(
            listOf(
                Pair("Title", "Body"),
                Pair(null, "Body only"),
                Pair("Title only", null),
                Pair(null, null),
                Pair("", ""),
                Pair("A", "1"),
                Pair("B", "2"),
                Pair("C", "3")
            )
        )

        // 8 entries -> first 5 considered -> 3 valid lines among them
        assertEquals(listOf("Title Body", "Body only", "Title only"), lines.map { it.toString() })
    }
}
