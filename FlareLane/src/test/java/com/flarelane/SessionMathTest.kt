package com.flarelane

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.experimental.runners.Enclosed
import org.junit.runner.RunWith

/**
 * # SessionMath spec
 *
 * Session lifecycle의 순수 결정 함수. SessionManager는 이 함수들을 SharedPreferences I/O 위에
 * wiring할 뿐, "언제 새 session인가 / engagement time을 어떻게 누적하는가"는 모두 여기서 결정.
 *
 * 각 inner 클래스 = 한 기능의 spec. 각 @Test = 그 spec의 한 줄짜리 룰.
 */
@RunWith(Enclosed::class)
class SessionMathTest {

    /**
     * ## `shouldStartNewSession(now, lastEventAt, timeoutMs)`
     *
     * 마지막 이벤트 발화 시점 이후 [timeoutMs] 이상 지났을 때만 새 session으로 간주.
     */
    class `새 session 시작 여부` {

        private val now = 1_700_000_000_000L
        private val timeout = 5 * 60_000L

        @Test fun `lastEventAt이 0이면 (이벤트 처음) — 새 session`() {
            assertTrue(SessionMath.shouldStartNewSession(now, lastEventAt = 0L, timeout))
        }

        @Test fun `lastEventAt이 timeout보다 오래 전 — 새 session`() {
            assertTrue(SessionMath.shouldStartNewSession(now, lastEventAt = now - timeout - 1, timeout))
        }

        @Test fun `lastEventAt이 timeout 안쪽 — 같은 session 유지`() {
            assertFalse(SessionMath.shouldStartNewSession(now, lastEventAt = now - 30_000, timeout))
        }

        @Test fun `lastEventAt이 정확히 timeout 만큼 전 — 같은 session 유지 (경계는 still alive)`() {
            assertFalse(SessionMath.shouldStartNewSession(now, lastEventAt = now - timeout, timeout))
        }
    }

    /**
     * ## `commitInProgressSegment(prevAccumulated, foregroundStartedAt, now)`
     *
     * onBackground 시 진행 중이던 foreground 구간을 누적값에 더한다. 진행 중 구간이 없으면
     * 누적값을 그대로 반환.
     */
    class `background 진입 시 누적` {

        private val now = 1_700_000_000_000L

        @Test fun `진행 중 segment가 있으면 누적값에 더해진다`() {
            // prev 2000ms + 진행 중 1500ms = 3500ms
            val merged = SessionMath.commitInProgressSegment(
                prevAccumulated = 2_000L,
                foregroundStartedAt = now - 1_500L,
                now = now
            )
            assertEquals(3_500L, merged)
        }

        @Test fun `foreground 아니었으면 누적값 변경 없음`() {
            val merged = SessionMath.commitInProgressSegment(
                prevAccumulated = 4_000L,
                foregroundStartedAt = 0L,
                now = now
            )
            assertEquals(4_000L, merged)
        }
    }

    /**
     * ## `foregroundSnapshot(accumulated, foregroundStartedAt, now)`
     *
     * 매 event 발화 시 `data.sessionForegroundMs`에 들어가는 값. 현재 진행 중인 segment까지
     * 포함해 server-side에서 max(sessionForegroundMs)로 정확한 session 길이를 계산할 수 있게.
     */
    class `event 발화 시 foreground 스냅샷` {

        private val now = 1_700_000_000_000L

        @Test fun `foreground 중이면 누적값 + 진행 중 segment`() {
            val snapshot = SessionMath.foregroundSnapshot(
                accumulated = 1_200L,
                foregroundStartedAt = now - 800L,
                now = now
            )
            assertEquals(2_000L, snapshot)
        }

        @Test fun `background 중이면 누적값만`() {
            val snapshot = SessionMath.foregroundSnapshot(
                accumulated = 5_000L,
                foregroundStartedAt = 0L,
                now = now
            )
            assertEquals(5_000L, snapshot)
        }

        @Test fun `lazy init된 빈 session — 0`() {
            val snapshot = SessionMath.foregroundSnapshot(
                accumulated = 0L,
                foregroundStartedAt = 0L,
                now = now
            )
            assertEquals(0L, snapshot)
        }
    }
}
