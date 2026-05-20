package com.flarelane

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.experimental.runners.Enclosed
import org.junit.runner.RunWith

/**
 * # EventDataMerger spec
 *
 * 모든 outgoing event payload에 session metadata (`sessionId`, `sessionForegroundMs`)를 자동
 * attach. 핵심 룰: **호스트 앱이 직접 셋팅한 키는 절대 덮어쓰지 않는다.** — 고객사가 의도적으로
 * 임의 값을 보내고 싶을 때 SDK가 끼어들지 않도록.
 */
@RunWith(Enclosed::class)
class EventDataMergerTest {

    /** 호스트가 session 키들을 안 썼을 때 — SDK가 attach한다. */
    class `호스트가 session 키를 안 썼을 때` {

        @Test fun `빈 data — sessionId와 sessionForegroundMs 둘 다 attach`() {
            val data = JSONObject()

            EventDataMerger.mergeSessionMetadata(data, sessionId = 100L, sessionForegroundMs = 200L)

            assertEquals(100L, data.getLong("sessionId"))
            assertEquals(200L, data.getLong("sessionForegroundMs"))
        }

        @Test fun `호스트 키가 다른 이름이면 그대로 보존하고 session 키를 attach`() {
            val data = JSONObject().put("foo", "bar").put("count", 7)

            EventDataMerger.mergeSessionMetadata(data, sessionId = 1L, sessionForegroundMs = 2L)

            assertEquals("bar", data.getString("foo"))
            assertEquals(7, data.getInt("count"))
            assertEquals(1L, data.getLong("sessionId"))
            assertEquals(2L, data.getLong("sessionForegroundMs"))
        }
    }

    /** 호스트가 이미 같은 키를 썼을 때 — SDK는 끼어들지 않는다. */
    class `호스트가 session 키를 이미 썼을 때` {

        @Test fun `호스트의 sessionId가 우선이고 SDK 값은 무시됨`() {
            val data = JSONObject().put("sessionId", "host-custom")

            EventDataMerger.mergeSessionMetadata(data, sessionId = 99L, sessionForegroundMs = 0L)

            assertEquals("host-custom", data.getString("sessionId"))
            // 호스트가 안 쓴 sessionForegroundMs는 attach됨
            assertEquals(0L, data.getLong("sessionForegroundMs"))
        }

        @Test fun `호스트의 sessionForegroundMs가 우선이고 SDK 값은 무시됨`() {
            val data = JSONObject().put("sessionForegroundMs", 1L)

            EventDataMerger.mergeSessionMetadata(data, sessionId = 42L, sessionForegroundMs = 9999L)

            assertEquals(42L, data.getLong("sessionId"))
            assertEquals(1L, data.getLong("sessionForegroundMs"))
        }
    }
}
