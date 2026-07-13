package com.flarelane;

import androidx.annotation.Nullable;

import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

// REF: https://pythonq.com/so/java/491072
// REF: https://www.tutorialspoint.com/android/android_json_parser.htm/a/p
class HTTPClient {
    protected static class ResponseHandler {
        void onSuccess(int responseCode, JSONObject response) {
            com.flarelane.Logger.verbose("HTTPClient.ResponseHandler.onSuccess: " + response.toString());
        }
        void onFailure(int responseCode, JSONObject response) {
            com.flarelane.Logger.error("HTTPClient.ResponseHandler.onFailure: " + response.toString());
        }
    }

    private static final String BASE_URL = "https://service-api.flarelane.com/";

    public static void get(String path, @Nullable ResponseHandler responseHandler) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                HttpURLConnection conn = null;

                try {
                    URL url = new URL(BASE_URL + path);
                    conn = (HttpURLConnection) url.openConnection();
                    conn.setRequestMethod("GET");
                    conn.setRequestProperty("Content-Type", "application/json;charset=UTF-8");
                    conn.setRequestProperty("Accept","application/json");
                    conn.setRequestProperty("x-flarelane-sdk-info",FlareLane.SdkInfo.type + "-" + FlareLane.SdkInfo.version);
                    conn.setUseCaches(false);

                    handleResponse(conn, responseHandler);
                } catch (Exception e) {
                    com.flarelane.BaseErrorHandler.handle(e);
                    notifyFailure(responseHandler);
                } finally {
                    if (conn != null)
                        conn.disconnect();
                }
            }
        }).start();
    }

    public static void post(String path, JSONObject body, @Nullable ResponseHandler responseHandler) {
        sendRequestWithBody("POST", path, body, responseHandler);
    }

    public static void patch(String path, JSONObject body, @Nullable ResponseHandler responseHandler) {
        sendRequestWithBody("PATCH", path, body, responseHandler);
    }

    public static void delete(String path, JSONObject body, @Nullable ResponseHandler responseHandler) {
        sendRequestWithBody("DELETE", path, body, responseHandler);
    }

    private static void sendRequestWithBody(String method, String path, JSONObject body, @Nullable ResponseHandler responseHandler) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                HttpURLConnection conn = null;

                try {
                    URL url = new URL(BASE_URL + path);
                    conn = (HttpURLConnection) url.openConnection();

                    conn.setUseCaches(false);
                    conn.setDoOutput(true);
                    conn.setDoInput(true);

                    conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
                    conn.setRequestProperty("Accept","application/json");
                    conn.setRequestProperty("x-flarelane-sdk-info",FlareLane.SdkInfo.type + "-" + FlareLane.SdkInfo.version);
                    conn.setRequestMethod(method);

                    if (body != null) {
                        String strJsonBody = body.toString();

                        byte[] sendBytes = strJsonBody.getBytes("UTF-8");
                        conn.setFixedLengthStreamingMode(sendBytes.length);

                        OutputStream outputStream = conn.getOutputStream();
                        outputStream.write(sendBytes);
                        com.flarelane.Logger.verbose("HTTP " + method + " body: " + body.toString());
                    }

                    handleResponse(conn, responseHandler);
                } catch (Exception e) {
                    com.flarelane.BaseErrorHandler.handle(e);
                    notifyFailure(responseHandler);
                } finally {
                    if (conn != null)
                        conn.disconnect();
                }
            }
        }).start();
    }

    // Called from the outer catch blocks so every network-layer exception (URL
    // build, connect, IO, or a bad handleResponse) still resolves the caller's
    // handler. Without this, InAppService.getMessage would never see onFailure
    // for these paths and the TaskQueueManager would stall until TIMEOUT_MS.
    private static void notifyFailure(@Nullable ResponseHandler responseHandler) {
        invokeSafely(responseHandler, false, -1, new JSONObject());
    }

    // Single dispatch site for handler callbacks. Swallowing exceptions here
    // keeps them from bubbling into `sendRequestWithBody`'s outer catch, which
    // would otherwise call `notifyFailure` and dispatch a second time. The
    // handler contract is "exactly one of onSuccess/onFailure" — a partial
    // dispatch that threw counts as the one call.
    private static void invokeSafely(@Nullable ResponseHandler responseHandler, boolean success, int responseCode, JSONObject body) {
        if (responseHandler == null) return;
        try {
            if (success) {
                responseHandler.onSuccess(responseCode, body);
            } else {
                responseHandler.onFailure(responseCode, body);
            }
        } catch (Exception e) {
            com.flarelane.BaseErrorHandler.handle(e);
        }
    }

    private static void handleResponse(HttpURLConnection conn, @Nullable ResponseHandler responseHandler) throws Exception {
        int responseCode = conn.getResponseCode();
        InputStream in = null;

        try {
            in = new BufferedInputStream(conn.getInputStream());
        } catch (FileNotFoundException e) {
            in = new BufferedInputStream(conn.getErrorStream());
        }

        String response = convertStreamToString(in);
        // Defensive: a malformed/empty server response would otherwise bubble a
        // JSONException up to the outer `catch (Exception)`, which silently
        // swallows it without invoking the responseHandler — leaving the
        // FlareLaneTaskManager waiting until its timeout. Catch here so callers
        // always observe an onFailure for HTTP-level success with bad body.
        JSONObject json;
        try {
            json = new JSONObject(response);
        } catch (org.json.JSONException e) {
            com.flarelane.Logger.error("Failed to parse response JSON: " + e + ", body: " + response);
            invokeSafely(responseHandler, false, responseCode, new JSONObject());
            return;
        }

        boolean isSuccess = (responseCode >= 200 && responseCode < 400);
        invokeSafely(responseHandler, isSuccess, responseCode, json);
    }

    private static String convertStreamToString(InputStream is) {
        BufferedReader reader = new BufferedReader(new InputStreamReader(is));
        StringBuilder sb = new StringBuilder();

        String line;
        try {
            while ((line = reader.readLine()) != null) {
                sb.append(line).append('\n');
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try {
                is.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        return sb.toString();
    }
}

