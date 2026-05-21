package com.flarelane;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationManagerCompat;
import androidx.lifecycle.DefaultLifecycleObserver;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.ProcessLifecycleOwner;

import org.json.JSONObject;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/* --- Note: This class deliberately doesn't hold a Handler/Looper field at the class level so
 * its `shouldActivate` decision function stays callable from a JVM unit test classloader without
 * triggering Android Looper init. Handler is created lazily inside registerObserver(). */

/**
 * Activate / session lifecycle controller. Holds all the cross-foreground-entry state and
 * decides when the SDK calls activate against the server.
 *
 * Responsibilities:
 *   - Observe process foreground/background via ProcessLifecycleOwner
 *   - Decide whether to invoke activate (delegates the truth table to ThrottleDecision)
 *   - Sync OS notification permission changes on foreground entry
 *   - Fire @session_start when SessionManager rolls a new session
 *   - Guard the activate HTTP call with an in-flight latch
 *
 * FlareLane.deviceRegisterOrActivate owns the actual register/activate HTTP call — this class
 * only orchestrates *when* to invoke it.
 */
class ActivateThrottle {

    private static final long INACTIVITY_THRESHOLD_MS = 5 * 60 * 1000L;
    private static final long MIN_ACTIVATE_INTERVAL_MS = 30 * 60 * 1000L;

    private static final AtomicBoolean isInFlight = new AtomicBoolean(false);
    private static long backgroundedAt = 0L;
    private static final AtomicBoolean observerRegistered = new AtomicBoolean(false);

    private ActivateThrottle() {}

    /**
     * Pure decision: should this foreground entry trigger activate?
     *
     * Policy: first launch fires unconditionally. Otherwise both (a) background gap exceeded
     * [inactivityThresholdMs] AND (b) elapsed since [lastActivated] exceeded [minIntervalMs]
     * must hold. `backgroundedAt == 0L` means this process never went to background — treat
     * as an infinite background gap.
     */
    static boolean shouldActivate(long now, long lastActivated, long backgroundedAt,
                                  long inactivityThresholdMs, long minIntervalMs) {
        long backgroundDuration = backgroundedAt == 0L ? Long.MAX_VALUE : now - backgroundedAt;
        long sinceLastActivate = lastActivated == 0L ? Long.MAX_VALUE : now - lastActivated;
        boolean firstLaunch = lastActivated == 0L;
        boolean longBackground = backgroundDuration >= inactivityThresholdMs
                && sinceLastActivate >= minIntervalMs;
        return firstLaunch || longBackground;
    }

    static void registerObserver(Context context) {
        if (!observerRegistered.compareAndSet(false, true)) return;
        final Context appContext = context.getApplicationContext();
        // ProcessLifecycleOwner.addObserver must run on the main thread. Handler is created
        // here (method-local) instead of as a class field so the class is loadable by a JVM
        // test runner — see the file header note.
        new Handler(Looper.getMainLooper()).post(() ->
            ProcessLifecycleOwner.get().getLifecycle().addObserver(new DefaultLifecycleObserver() {
                @Override
                public void onStart(@NonNull LifecycleOwner owner) {
                    handleAppStart(appContext);
                }

                @Override
                public void onStop(@NonNull LifecycleOwner owner) {
                    backgroundedAt = System.currentTimeMillis();
                    // Commit in-progress engagement segment so session length survives a kill.
                    SessionManager.onBackground(appContext);
                }
            })
        );
    }

    private static void handleAppStart(Context context) {
        try {
            long now = System.currentTimeMillis();
            long lastActivated = BaseSharedPreferences.getLastActivatedAt(context);
            boolean shouldFire = shouldActivate(
                    now, lastActivated, backgroundedAt, INACTIVITY_THRESHOLD_MS, MIN_ACTIVATE_INTERVAL_MS);

            if (shouldFire) {
                Map<String, Object> log = new HashMap<>();
                log.put("reason", lastActivated == 0L ? "firstLaunch" : "backgroundReturn");
                log.put("backgroundDurationMs", backgroundedAt == 0L ? Long.MAX_VALUE : now - backgroundedAt);
                log.put("sinceLastActivateMs", lastActivated == 0L ? Long.MAX_VALUE : now - lastActivated);
                Logger.info("Device", "activate trigger", log);
                FlareLane.deviceRegisterOrActivate(context, null);
            } else {
                // Throttle skipped activate — still want to catch OS permission toggles.
                syncPermissionIfChanged(context);
                Map<String, Object> log = new HashMap<>();
                log.put("backgroundDurationMs", now - backgroundedAt);
                log.put("sinceLastActivateMs", now - lastActivated);
                Logger.verbose("Device", "activate skipped", log);
            }

            // Session — fire @session_start whenever a new session begins. On first install
            // deviceId is still null here; the register success path calls fireSessionStartIfReady
            // again once it's available.
            if (SessionManager.onForeground(context)) {
                fireSessionStartIfReady(context);
            }
        } catch (Exception e) {
            BaseErrorHandler.handle(e);
        }
    }

    /**
     * Compare current OS notification permission against the last-synced value; if changed, send a
     * mini PATCH so the server sees toggles immediately even when activate is throttled.
     * Detection latency is bounded by foreground re-entry time (no OS-level broadcast exists).
     */
    static void syncPermissionIfChanged(Context context) {
        try {
            boolean current = NotificationManagerCompat.from(context).areNotificationsEnabled();
            Boolean lastSynced = BaseSharedPreferences.getLastSyncedPermission(context);
            if (lastSynced != null && lastSynced == current) return;

            String deviceId = BaseSharedPreferences.getDeviceId(context, true);
            if (deviceId == null) return; // not registered yet — first activate will sync

            JSONObject data = new JSONObject().put("notificationPermission", current);
            DeviceService.update(context, data, new DeviceService.ResponseHandler() {
                @Override
                public void onSuccess(Device device) {
                    BaseSharedPreferences.setLastSyncedPermission(context, current);
                    Logger.info("Device", "permission synced",
                            Collections.singletonMap("granted", current));
                }
            });
        } catch (Exception e) {
            BaseErrorHandler.handle(e);
        }
    }

    /**
     * In-flight latch — call before starting activate. Returns true if the caller acquired the
     * lock and should proceed; false if another activate is already running and the caller
     * should skip.
     */
    static boolean acquireInFlight() {
        return isInFlight.compareAndSet(false, true);
    }

    static void releaseInFlight() {
        isInFlight.set(false);
    }

    /**
     * Called by FlareLane.deviceRegisterOrActivate after a successful register/activate.
     * Updates persisted state (so the throttle ticks forward) and releases the in-flight latch.
     */
    static void onActivateSuccess(Context context) {
        BaseSharedPreferences.setLastActivatedAt(context, System.currentTimeMillis());
        boolean current = NotificationManagerCompat.from(context).areNotificationsEnabled();
        BaseSharedPreferences.setLastSyncedPermission(context, current);
        isInFlight.set(false);
    }

    /**
     * Fire @session_start if all the prerequisites are available. Called from both
     * handleAppStart (existing device path) and the register success callback (first install).
     * Idempotent against SessionManager — if a previous fire already happened within the
     * inactivity window, the next session check sees the recent lastEventAt and skips.
     */
    static void fireSessionStartIfReady(Context context) {
        try {
            String deviceId = BaseSharedPreferences.getDeviceId(context, true);
            String projectId = BaseSharedPreferences.getProjectId(context, true);
            String userId = BaseSharedPreferences.getUserId(context, true);
            if (deviceId != null && projectId != null) {
                EventService.trackEvent(projectId, deviceId, userId, "@session_start", null);
            }
        } catch (Exception e) {
            BaseErrorHandler.handle(e);
        }
    }
}
