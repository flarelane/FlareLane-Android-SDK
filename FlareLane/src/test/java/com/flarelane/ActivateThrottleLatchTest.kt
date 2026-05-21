package com.flarelane

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.experimental.runners.Enclosed
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * # ActivateThrottle in-flight latch spec
 *
 * `acquireInFlight` / `releaseInFlight`는 register/activate HTTP 호출이 process 안에서 동시
 * 두 번 진행되는 것을 막는다. 예를 들어 lifecycle observer가 fire되는 순간 `initWithContext`의
 * register path가 진행 중이면, 두 번째 acquire는 false를 받아 즉시 return해야 한다.
 *
 * (참고: ActivateThrottle은 Handler/Looper를 method-local로만 사용해서 acquireInFlight
 *  자체는 JVM 클래스 로드만으로 안전하게 호출 가능. 따라서 unit test로 cover.)
 */
@RunWith(Enclosed::class)
class ActivateThrottleLatchTest {

    /** Sequential acquire / release semantics. */
    class `순차 호출 동작` : ResetLatch() {

        @Test fun `처음 acquire는 true`() {
            assertTrue(ActivateThrottle.acquireInFlight())
        }

        @Test fun `이미 acquire한 상태에서 또 acquire하면 false`() {
            ActivateThrottle.acquireInFlight()
            assertFalse(ActivateThrottle.acquireInFlight())
        }

        @Test fun `release 후엔 다시 acquire 가능`() {
            ActivateThrottle.acquireInFlight()
            ActivateThrottle.releaseInFlight()
            assertTrue(ActivateThrottle.acquireInFlight())
        }

        @Test fun `release를 두 번 호출해도 안전 (idempotent)`() {
            ActivateThrottle.acquireInFlight()
            ActivateThrottle.releaseInFlight()
            ActivateThrottle.releaseInFlight() // 두 번째 — no-op
            assertTrue(ActivateThrottle.acquireInFlight())
        }
    }

    /** Concurrent acquire — 정확히 한 명만 진입한다. */
    class `동시 호출 시 단일 진입자만 통과` : ResetLatch() {

        @Test fun `여러 thread가 동시에 acquire해도 정확히 한 번만 true`() {
            val threadCount = 16
            val startGate = CountDownLatch(1)
            val finishGate = CountDownLatch(threadCount)
            val successCount = AtomicInteger(0)
            val exec = Executors.newFixedThreadPool(threadCount)

            repeat(threadCount) {
                exec.submit {
                    try {
                        startGate.await()                    // 모든 thread를 한 번에 풀어줌
                        if (ActivateThrottle.acquireInFlight()) {
                            successCount.incrementAndGet()
                        }
                    } finally {
                        finishGate.countDown()
                    }
                }
            }
            try {
                startGate.countDown()
                assertTrue(
                    "concurrent acquire의 동시 완료를 5초 안에 끝내야 함",
                    finishGate.await(5, TimeUnit.SECONDS)
                )
            } finally {
                exec.shutdownNow()
            }

            assertEquals(
                "동시 acquire 시 정확히 한 thread만 통과해야 함",
                1, successCount.get()
            )
        }
    }

    /**
     * Latch state는 static field라 test 간 누설을 막으려면 @After에서 release.
     * inner 클래스들이 상속해서 공통 사용.
     */
    abstract class ResetLatch {
        @After fun resetLatch() {
            ActivateThrottle.releaseInFlight()
        }
    }
}
