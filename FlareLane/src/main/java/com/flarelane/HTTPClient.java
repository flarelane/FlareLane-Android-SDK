package com.flarelane;

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

    public static void get(String path, ResponseHandler responseHandler) {
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
                } finally {
                    if (conn != null)
                        conn.disconnect();
                }
            }
        }).start();
    }

    public static void post(String path, JSONObject body, ResponseHandler responseHandler) {
        sendRequestWithBody("POST", path, body, responseHandler);
    }

    public static void patch(String path, JSONObject body, ResponseHandler responseHandler) {
        sendRequestWithBody("PATCH", path, body, responseHandler);
    }

    public static void delete(String path, JSONObject body, ResponseHandler responseHandler) {
        sendRequestWithBody("DELETE", path, body, responseHandler);
    }

    private static void sendRequestWithBody(String method, String path, JSONObject body, ResponseHandler responseHandler) {
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
                } finally {
                    if (conn != null)
                        conn.disconnect();
                }
            }
        }).start();
    }

    private static void handleResponse(HttpURLConnection conn, ResponseHandler responseHandler) throws Exception {
        int responseCode = conn.getResponseCode();
        InputStream in = null;

        try {
            in = new BufferedInputStream(conn.getInputStream());
        } catch (FileNotFoundException e) {
            in = new BufferedInputStream(conn.getErrorStream());
        }

        String response = convertStreamToString(in);
        JSONObject json = new JSONObject(response);

        if (responseCode >= 200 && responseCode < 400) {
            responseHandler.onSuccess(responseCode, json);
        } else {
            responseHandler.onFailure(responseCode, json);
        }
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

