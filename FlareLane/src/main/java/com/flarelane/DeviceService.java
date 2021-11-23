package com.flarelane;

import android.content.Context;
import android.os.AsyncTask;
import android.os.Build;

import com.google.android.gms.ads.identifier.AdvertisingIdClient;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Locale;
import java.util.TimeZone;

import static com.flarelane.FlareLane.sdkType;
import static com.flarelane.FlareLane.sdkVersion;

class DeviceService {
    static JSONObject getSystemInfo() throws Exception {
        JSONObject data = new JSONObject();
        data.put("platform", "android");
        data.put("deviceModel", Build.MODEL);
        data.put("osVersion", String.valueOf(Build.VERSION.RELEASE));
        data.put("sdkVersion", sdkVersion);
        data.put("timeZone", TimeZone.getDefault().getID());
        data.put("languageCode", Locale.getDefault().getLanguage());
        data.put("countryCode", Locale.getDefault().getCountry());
        data.put("sdkType", sdkType.toString());

        return data;
    }

    static void register(Context context, String projectId, String pushToken) {
        AsyncTask.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    String gaid = getAdvertisingId(context);
                    JSONObject data = getSystemInfo();
                    data.put("pushToken", pushToken);
                    data.put("isSubscribed", true);
                    data.put("gaid", gaid);


                    DeviceService.create(projectId, data, new ResponseHandler() {
                        @Override
                        public void onSuccess(Device device) {
                            BaseSharedPreferences.setDeviceId(context, device.id);
                            BaseSharedPreferences.setProjectId(context, projectId);
                            Logger.verbose("deviceId : " + device.id);
                        }
                    });
                } catch(Exception e) {
                    BaseErrorHandler.handle(e);
                }
            }
        });



    }

    static void activate(Context context, String projectId, String deviceId, String pushToken) {
        AsyncTask.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    JSONObject data = getSystemInfo();
                    data.put("pushToken", pushToken);
                    data.put("lastActiveAt", Utils.getISO8601DateString());

                    DeviceService.update(projectId, deviceId, data, new ResponseHandler() {
                        @Override
                        public void onSuccess(Device device) {

                        }
                    });
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

    static void create(String projectId, JSONObject data, ResponseHandler handler) {
        HTTPClient.post("internal/v1/projects/" + projectId + "/devices", data, new HTTPClient.ResponseHandler() {
            @Override
            void onSuccess(int responseCode, JSONObject response) {
                super.onSuccess(responseCode, response);

                try {
                    Device device = new Device(response.getJSONObject("data").getString("id"));
                    handler.onSuccess(device);
                } catch (Exception e) {
                    BaseErrorHandler.handle(e);
                }
            }
        });
    }

    static void update(String projectId, String deviceId, JSONObject data, ResponseHandler handler) {
        HTTPClient.patch("internal/v1/projects/" + projectId + "/devices/" + deviceId, data, new HTTPClient.ResponseHandler() {
            @Override
            void onSuccess(int responseCode, JSONObject response) {
                super.onSuccess(responseCode, response);

                try {
                    Device device = new Device(response.getJSONObject("data").getString("id"));
                    handler.onSuccess(device);
                } catch (Exception e) {
                    BaseErrorHandler.handle(e);
                }
            }
        });
    }

    protected interface ResponseHandler {
        void onSuccess(Device device);
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
