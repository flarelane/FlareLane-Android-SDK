package com.flarelane

import android.content.Context
import android.util.Log
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * # FlareLane public API spec
 *
 * 고객사 개발자가 직접 호출하는 진입점들. 호환성이 깨지면 통합한 모든 호스트 앱이 영향을 받기
 * 때문에 한 줄도 변경되지 않도록 spec으로 박아둔다. Spec 시나리오 중심.
 *
 * 그룹은 메서드명 prefix(`isSubscribed_`, `setLogLevel_` 등)로 표시한다. (Android DEX 040
 * 미만은 SimpleName에 공백/dash/괄호를 못 받기 때문에 underscore + 한글로만 구성.)
 */
@RunWith(AndroidJUnit4::class)
class FlareLanePublicApiTest {

    private lateinit var context: Context

    @Before fun setup() {
        context = ApplicationProvider.getApplicationContext()
        EventService.unhandledClickedNotification = null
        FlareLane.notificationClickedHandler = null
    }

    @After fun restoreLogLevel() {
        // 다른 spec이 영향받지 않도록 default(VERBOSE)로 복원
        FlareLane.setLogLevel(Log.VERBOSE)
    }

    // ============================================================
    // getDeviceId / getUserId / getProjectId — null safety + 읽기
    // ============================================================
    // 고객사가 init 직후 / reset 직후에 부르는 read-side wrapper. 저장값이 없어도 절대 throw하지
    // 않고 null을 반환해야 한다 — BaseSharedPreferences는 non-nullable getter에서 throw하지만
    // public wrapper는 swallow + graceful null.

    @Test fun getDeviceId_저장된_값_없으면_null() {
        BaseSharedPreferences.setDeviceId(context, null)
        assertNull(FlareLane.getDeviceId(context))
    }

    @Test fun getUserId_저장된_값_없으면_null() {
        BaseSharedPreferences.setUserId(context, null)
        assertNull(FlareLane.getUserId(context))
    }

    @Test fun getProjectId_저장된_값_없으면_null() {
        BaseSharedPreferences.setProjectId(context, null)
        assertNull(FlareLane.getProjectId(context))
    }

    @Test fun getDeviceId_저장된_값이_있으면_그대로_반환() {
        BaseSharedPreferences.setDeviceId(context, "device-abc")
        assertEquals("device-abc", FlareLane.getDeviceId(context))
    }

    @Test fun getUserId_저장된_값이_있으면_그대로_반환() {
        BaseSharedPreferences.setUserId(context, "user@example.com")
        assertEquals("user@example.com", FlareLane.getUserId(context))
    }

    @Test fun getProjectId_저장된_값이_있으면_그대로_반환() {
        BaseSharedPreferences.setProjectId(context, "proj-xyz")
        assertEquals("proj-xyz", FlareLane.getProjectId(context))
    }

    // ============================================================
    // setLogLevel — Logger 영향
    // ============================================================
    // 고객사가 production에서 로그를 줄이거나 debug에서 verbose로 켜고 싶을 때 부르는 setter.

    @Test fun setLogLevel_ERROR_적용() {
        FlareLane.setLogLevel(Log.ERROR)
        assertEquals(Log.ERROR, Logger.logLevel)
    }

    @Test fun setLogLevel_INFO_적용() {
        FlareLane.setLogLevel(Log.INFO)
        assertEquals(Log.INFO, Logger.logLevel)
    }

    @Test fun setLogLevel_VERBOSE로_복원_가능() {
        FlareLane.setLogLevel(Log.ERROR)
        FlareLane.setLogLevel(Log.VERBOSE)
        assertEquals(Log.VERBOSE, Logger.logLevel)
    }

    // ============================================================
    // setNotificationClickedHandler — 보류된 클릭 flush
    // ============================================================
    // 알림 클릭이 일어났는데 호스트 앱이 아직 handler를 등록 안 한 상태라면 SDK는 그 click을
    // EventService.unhandledClickedNotification에 보류한다. 나중에 handler가 등록되는 순간 즉시
    // callback이 호출돼야 사용자 클릭이 유실되지 않는다.

    @Test fun setNotificationClickedHandler_보류된_클릭이_있으면_즉시_호출하고_보류는_비움() {
        val pending = Notification(
            id = "pending-id",
            body = "body",
            data = null,
            title = null,
            url = null,
            imageUrl = null,
            buttons = null,
            clickedButtonIdx = null
        )
        EventService.unhandledClickedNotification = pending

        var captured: Notification? = null
        FlareLane.setNotificationClickedHandler(NotificationClickedHandler { captured = it })

        assertSame("보류된 알림이 그대로 handler에 전달돼야 함", pending, captured)
        assertNull(
            "flush 후엔 보류 슬롯이 비워져야 함 (같은 클릭 두 번 처리 방지)",
            EventService.unhandledClickedNotification
        )
    }

    @Test fun setNotificationClickedHandler_보류된_클릭이_없으면_등록_시_호출_안_함() {
        EventService.unhandledClickedNotification = null
        var called = false
        FlareLane.setNotificationClickedHandler(NotificationClickedHandler { called = true })
        assertFalse(called)
    }

    // ============================================================
    // isSubscribed — OS 권한 AND 저장된 isSubscribed=="true" 둘 다 봐야 함
    // ============================================================
    // OS 권한이 꺼지면 SharedPreferences에 true가 남아 있어도 false 반환해야 함.

    @Test fun isSubscribed_저장값이_false면_권한과_무관하게_false() {
        BaseSharedPreferences.setIsSubscribed(context, false)
        assertFalse(FlareLane.isSubscribed(context))
    }

    @Test fun isSubscribed_저장값이_없으면_false() {
        val prefsKey = "com.flarelane.SHARED_PREFERENCE_KEY_" +
                context.packageManager.getPackageInfo(context.packageName, 0).firstInstallTime
        context.getSharedPreferences(prefsKey, Context.MODE_PRIVATE)
            .edit().remove("com.flarelane.IS_SUBSCRIBED_KEY").commit()
        assertFalse(FlareLane.isSubscribed(context))
    }

    @Test fun isSubscribed_저장값이_true면_OS_권한과_동일() {
        // 권한을 test 시점에 결정적으로 조작하기 어려우니 현재 권한 상태와 동일 결과만 검증.
        BaseSharedPreferences.setIsSubscribed(context, true)
        val osGranted = androidx.core.app.NotificationManagerCompat.from(context).areNotificationsEnabled()
        assertEquals(
            "saved=true일 때 isSubscribed는 OS 권한과 같아야 함",
            osGranted, FlareLane.isSubscribed(context)
        )
    }

    // ============================================================
    // setUserId / getUserId round-trip
    // ============================================================
    // setUserId public API는 비동기 + server 의존이라 emulator에서 측정 불안정. 대신
    // BaseSharedPreferences round-trip (server PATCH 성공 경로가 결국 호출하는 것)이 public read
    // API와 호환되는지 검증.

    @Test fun setUserId_BaseSharedPreferences_round_trip이_getUserId로_노출됨() {
        BaseSharedPreferences.setUserId(context, "alice@flarelane.com")
        assertEquals("alice@flarelane.com", FlareLane.getUserId(context))
    }

    @Test fun setUserId_null로_재설정하면_getUserId도_null() {
        BaseSharedPreferences.setUserId(context, "bob")
        assertNotNull(FlareLane.getUserId(context))
        BaseSharedPreferences.setUserId(context, null)
        assertNull(FlareLane.getUserId(context))
    }

    // ============================================================
    // resetDevice — public read API에 즉시 반영
    // ============================================================
    // DeviceLifecycleTest가 SharedPreferences-level wipe를 cover. 여기는 reset 직후 public read
    // API들이 즉시 null/false를 반환해서 host app이 stale 값을 보지 않는지 검증.

    @Test fun resetDevice_getDeviceId가_null() {
        BaseSharedPreferences.setDeviceId(context, "before-reset")
        FlareLane.resetDevice(context)
        assertNull(FlareLane.getDeviceId(context))
    }

    @Test fun resetDevice_getUserId가_null() {
        BaseSharedPreferences.setUserId(context, "before-reset-user")
        FlareLane.resetDevice(context)
        assertNull(FlareLane.getUserId(context))
    }

    @Test fun resetDevice_getProjectId가_null() {
        BaseSharedPreferences.setProjectId(context, "before-reset-project")
        FlareLane.resetDevice(context)
        assertNull(FlareLane.getProjectId(context))
    }

    @Test fun resetDevice_isSubscribed가_false() {
        BaseSharedPreferences.setIsSubscribed(context, true)
        FlareLane.resetDevice(context)
        assertFalse(FlareLane.isSubscribed(context))
    }
}
