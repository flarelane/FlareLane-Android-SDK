package com.flarelane

import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.experimental.runners.Enclosed
import org.junit.runner.RunWith

/**
 * # EventDeduplicator spec
 *
 * Push received/clicked event가 같은 process 안에서 중복 발화되는 걸 막는 in-memory dedup.
 * Cap(200) 초과 시 set 자체를 비우는 단순 정책 — cross-process / cross-restart는 server-side
 * dedup의 책임.
 */
@RunWith(Enclosed::class)
class EventDeduplicatorTest {

    /** 기본 mark + check 동작. */
    class `중복 판정` : Purge() {

        @Test fun `처음 본 이벤트 — false (처리해도 됨)`() {
            assertFalse(EventDeduplicator.markAndCheckDuplicate("CLICKED", "n1"))
        }

        @Test fun `두 번째 같은 이벤트 — true (중복이라 skip)`() {
            EventDeduplicator.markAndCheckDuplicate("CLICKED", "n2")
            assertTrue(EventDeduplicator.markAndCheckDuplicate("CLICKED", "n2"))
        }

        @Test fun `notificationId 같아도 eventType 다르면 별개`() {
            // RECEIVED와 CLICKED는 같은 알림에서 각각 한 번씩 발화돼야 함.
            assertFalse(EventDeduplicator.markAndCheckDuplicate("CLICKED", "n3"))
            assertFalse(EventDeduplicator.markAndCheckDuplicate("RECEIVED", "n3"))
            assertTrue(EventDeduplicator.markAndCheckDuplicate("CLICKED", "n3"))
            assertTrue(EventDeduplicator.markAndCheckDuplicate("RECEIVED", "n3"))
        }
    }

    /** Cap overflow 시 자동 wipe — 메모리 무한 증가 방지. */
    class `cap 초과 시 자동 wipe` : Purge() {

        @Test fun `cap 200을 넘기면 set이 비워져서 같은 키도 다시 첫 관찰로 처리됨`() {
            EventDeduplicator.markAndCheckDuplicate("CLICKED", "early")
            assertTrue(
                "방금 mark했으니 일단은 중복으로 잡혀야 함",
                EventDeduplicator.markAndCheckDuplicate("CLICKED", "early")
            )

            // 220개의 새 키를 넣어 cap(200)을 초과시킴 → 자동 wipe
            repeat(220) { EventDeduplicator.markAndCheckDuplicate("FILL", "$it") }

            assertFalse(
                "wipe된 뒤엔 다시 첫 관찰로 처리돼야 함",
                EventDeduplicator.markAndCheckDuplicate("CLICKED", "early")
            )
        }
    }

    /**
     * EventDeduplicator는 process-lifetime singleton이라 test 간 state가 새는 걸 막아야 한다.
     * @After에서 cap을 두 번 넘겨서 set을 확실히 비운다. inner 클래스들이 이걸 상속해서 공통 사용.
     */
    abstract class Purge {
        @After fun purgeBetweenTests() {
            repeat(201) { EventDeduplicator.markAndCheckDuplicate("__purge1__", "$it") }
            repeat(201) { EventDeduplicator.markAndCheckDuplicate("__purge2__", "$it") }
        }
    }
}
