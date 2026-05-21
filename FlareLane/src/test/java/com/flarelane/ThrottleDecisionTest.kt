package com.flarelane

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.experimental.runners.Enclosed
import org.junit.runner.RunWith

/**
 * # ThrottleDecision spec
 *
 * Activate API를 언제 호출할지 결정하는 truth table.
 *
 * 정책: 첫 launch는 무조건 fire. 그 외에는 (background ≥ inactivity threshold) AND
 * (마지막 activate ≥ min interval) 두 조건을 모두 만족해야만 fire — 즉 사용자가 잠깐 home 눌렀다
 * 돌아오거나 Activity 전환만 한 경우엔 server를 건드리지 않는다.
 */
@RunWith(Enclosed::class)
class ThrottleDecisionTest {

    /** 첫 launch — lastActivated가 0이면 다른 조건 무시하고 무조건 fire. */
    class `첫 launch` {

        @Test fun `lastActivated가 0이면 무조건 fire`() {
            assertActivates(lastActivated = 0L, backgroundedAt = 0L)
        }
    }

    /** Background 정도 + 마지막 activate 시각의 조합. */
    class `재진입 시 throttle 판정` {

        private val now = 1_700_000_000_000L

        @Test fun `짧은 background 후 복귀 — skip`() {
            assertSkips(lastActivated = now - minutes(10), backgroundedAt = now - seconds(30))
        }

        @Test fun `오래 background했지만 마지막 activate가 30분 미달 — skip`() {
            assertSkips(lastActivated = now - minutes(20), backgroundedAt = now - minutes(10))
        }

        @Test fun `오래 background + 마지막 activate 30분 초과 — fire`() {
            assertActivates(lastActivated = now - minutes(31), backgroundedAt = now - minutes(10))
        }
    }

    /** Cold start: 이 process에서 한 번도 background 적 없음 (backgroundedAt == 0). */
    class `cold start` {

        private val now = 1_700_000_000_000L

        @Test fun `마지막 activate가 오래됐으면 fire`() {
            // backgroundedAt=0은 무한 background gap으로 간주, min-interval만 결정.
            assertActivates(lastActivated = now - hours(1), backgroundedAt = 0L)
        }

        @Test fun `마지막 activate가 30분 안쪽이면 skip`() {
            assertSkips(lastActivated = now - minutes(10), backgroundedAt = 0L)
        }
    }

    /** Inactivity threshold 경계 — `>=`인가 `>`인가. */
    class `inactivity 5분 경계값` {

        private val now = 1_700_000_000_000L

        @Test fun `정확히 5분 — fire (경계 포함)`() {
            assertActivates(lastActivated = now - minutes(30), backgroundedAt = now - minutes(5))
        }

        @Test fun `5분에서 1ms 모자람 — skip`() {
            assertSkips(lastActivated = now - minutes(30), backgroundedAt = now - minutes(5) + 1L)
        }
    }
}

// ---- shared helpers (top-level so all nested classes can use them) ----

private val INACTIVITY_5_MIN = 5 * 60_000L
private val MIN_INTERVAL_30_MIN = 30 * 60_000L

private fun assertActivates(lastActivated: Long, backgroundedAt: Long) {
    // ActivateThrottle.shouldActivate(now, lastActivated, backgroundedAt, inactivity, minInterval)
    assertTrue(
        ActivateThrottle.shouldActivate(
            1_700_000_000_000L, lastActivated, backgroundedAt, INACTIVITY_5_MIN, MIN_INTERVAL_30_MIN
        )
    )
}

private fun assertSkips(lastActivated: Long, backgroundedAt: Long) {
    assertFalse(
        ActivateThrottle.shouldActivate(
            1_700_000_000_000L, lastActivated, backgroundedAt, INACTIVITY_5_MIN, MIN_INTERVAL_30_MIN
        )
    )
}

private fun seconds(n: Int): Long = n * 1_000L
private fun minutes(n: Int): Long = n * 60_000L
private fun hours(n: Int): Long = n * 60 * 60_000L
