package com.flarelane;

import android.content.Context;
import android.os.Build;

import androidx.annotation.Nullable;

import com.google.android.gms.ads.identifier.AdvertisingIdClient;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Locale;
import java.util.TimeZone;

class DeviceService {
    static JSONObject getSystemInfo() throws Exception {
        JSONObject data = new JSONObject();
        data.put("platform", "android");
        data.put("deviceModel", Build.MODEL);
        data.put("osVersion", String.valueOf(Build.VERSION.RELEASE));
        data.put("sdkVersion", FlareLane.SdkInfo.version);
        data.put("timeZone", TimeZone.getDefault().getID());
        data.put("languageCode", Locale.getDefault().getLanguage());
        data.put("countryCode", Locale.getDefault().getCountry());
        data.put("sdkType", FlareLane.SdkInfo.type.toString());

        return data;
    }

    static void register(Context context, String projectId, @Nullable ResponseHandler responseHandler) {
        try {
            JSONObject data = getSystemInfo();
            DeviceService.create(projectId, data, new ResponseHandler() {
                @Override
                public void onSuccess(Device device) {
                    BaseSharedPreferences.setDeviceId(context, device.id);
                    Logger.verbose("deviceId : " + device.id);

                    if (responseHandler != null)
                        responseHandler.onSuccess(device);
                }
            });
        } catch(Exception e) {
            BaseErrorHandler.handle(e);
        }
    }

    static void activate(Context context, @Nullable ResponseHandler responseHandler) {
        try {
            JSONObject data = getSystemInfo();
            data.put("lastActiveAt", Utils.getISO8601DateString());

            DeviceService.update(context, data, new ResponseHandler() {
                @Override
                public void onSuccess(Device device) {
                    if (responseHandler != null)
                        responseHandler.onSuccess(device);
                }
            });
        } catch (Exception e) {
            BaseErrorHandler.handle(e);
        }
    }

    static void getTags(String projectId, String deviceId, TagsResponseHandler handler) {
        HTTPClient.get("internal/v1/projects/" + projectId + "/devices/" + deviceId + "/tags", new HTTPClient.ResponseHandler() {
            @Override
            void onSuccess(int responseCode, JSONObject response) {
                super.onSuccess(responseCode, response);

                try {
                    JSONObject data = response.getJSONObject("data");
                    handler.onSuccess(data.getJSONObject("tags"));
                } catch (Exception e) {
                    BaseErrorHandler.handle(e);
                }
            }
        });
    }

    static void deleteTags(String projectId, String deviceId, ArrayList<String> keys) throws JSONException {
        JSONObject data = new JSONObject();
        data.put("keys", new JSONArray(keys));

        HTTPClient.delete("internal/v1/projects/" + projectId + "/devices/" + deviceId + "/tags", data, new HTTPClient.ResponseHandler());
    }

    static void create(String projectId, JSONObject data, @Nullable ResponseHandler handler) {
        HTTPClient.post("internal/v1/projects/" + projectId + "/devices", data, new HTTPClient.ResponseHandler() {
            @Override
            void onSuccess(int responseCode, JSONObject response) {
                super.onSuccess(responseCode, response);

                try {
                    JSONObject data = response.getJSONObject("data");
                    Device device = new Device(data.getString("id"), data.getBoolean("isSubscribed"), null);

                    if (handler != null) {
                        handler.onSuccess(device);
                    }
                } catch (Exception e) {
                    BaseErrorHandler.handle(e);
                }
            }
        });
    }

    static void update(Context context, JSONObject data, @Nullable ResponseHandler handler) throws Exception {
        String projectId = com.flarelane.BaseSharedPreferences.getProjectId(context, false);
        String deviceId = com.flarelane.BaseSharedPreferences.getDeviceId(context, false);

        HTTPClient.patch("internal/v1/projects/" + projectId + "/devices/" + deviceId, data, new HTTPClient.ResponseHandler() {
            @Override
            void onSuccess(int responseCode, JSONObject response) {
                super.onSuccess(responseCode, response);

                try {
                    JSONObject responseData = response.getJSONObject("data");
                    Device device = new Device(responseData.getString("id"), responseData.getBoolean("isSubscribed"), responseData.isNull("userId") ? null : responseData.getString("userId"));

                    BaseSharedPreferences.setUserId(context, device.userId);
                    BaseSharedPreferences.setIsSubscribed(context, device.isSubscribed);

                    if (handler != null) {
                        handler.onSuccess(device);
                    }
                } catch (Exception e) {
                    BaseErrorHandler.handle(e);
                }
            }
        });
    }

    protected interface ResponseHandler {
        void onSuccess(Device device);
    }

    protected interface TagsResponseHandler {
        void onSuccess(JSONObject tags);
    }

    //    https://stackoverflow.com/a/61157036
    private static String getAdvertisingId(Context context) {
        try {
            AdvertisingIdClient.Info advertisingIdInfo = AdvertisingIdClient.getAdvertisingIdInfo(context);

            if (!advertisingIdInfo.isLimitAdTrackingEnabled()) {
                String id = advertisingIdInfo.getId();
                return id;
            }
        } catch (Exception e) {
            BaseErrorHandler.handle(e);
        }

        return null;
    }
}
