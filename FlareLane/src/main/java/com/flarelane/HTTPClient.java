package com.flarelane;

import androidx.annotation.Nullable;

import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ThreadLocalRandom;

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

    // HttpURLConnection defaults to waiting forever; on a mobile network that
    // can hold a request (and its task-queue slot) for the life of the process.
    private static final int CONNECT_TIMEOUT_MS = 10000;
    private static final int READ_TIMEOUT_MS = 10000;

    // Transient failures are retried before the handler hears about them, so a
    // few seconds without network no longer loses the request outright.
    static final int MAX_ATTEMPTS = 3;

    // Total budget for one call, retries included. Kept under the
    // TaskQueueManager timeout (10s) so a retrying request cannot outlive the
    // task slot that is waiting on it.
    static final long RETRY_DEADLINE_MS = 8000;

    /**
     * A transient failure is worth another attempt; a rejection the server
     * would simply repeat is not. -1 means no HTTP response ever arrived.
     * Every other 4xx fails immediately, 410 (gone) included — it is the
     * stop signal and must not be delayed by a backoff.
     */
    static boolean shouldRetry(int responseCode, int attempt) {
        if (attempt >= MAX_ATTEMPTS) return false;
        if (responseCode == -1 || responseCode >= 500) return true;
        return responseCode == 408 || responseCode == 429;
    }

    /**
     * Half the base delay (1s, then 3s) plus a random share of the other half,
     * so devices that lost connectivity together do not retry as one burst.
     */
    static long delayMillis(int attempt) {
        long half = (attempt <= 1 ? 1000L : 3000L) / 2;
        return half + ThreadLocalRandom.current().nextLong(half + 1);
    }

    public static void get(String path, @Nullable ResponseHandler responseHandler) {
        send(BASE_URL, "GET", path, null, true, responseHandler);
    }

    public static void post(String path, JSONObject body, @Nullable ResponseHandler responseHandler) {
        send(BASE_URL, "POST", path, body, false, responseHandler);
    }

    /**
     * POSTs are only retried when the caller marks them idempotent — a POST
     * that reached the server but lost its response would otherwise be applied
     * twice. Opt in only when the request is a read in POST clothing, or when
     * its body carries an id the backend can deduplicate on. GET/PATCH/DELETE
     * are idempotent by contract here: PATCH bodies are absolute values
     * (last-writer-wins), never increments.
     */
    public static void post(String path, JSONObject body, @Nullable ResponseHandler responseHandler, boolean idempotent) {
        send(BASE_URL, "POST", path, body, idempotent, responseHandler);
    }

    public static void patch(String path, JSONObject body, @Nullable ResponseHandler responseHandler) {
        send(BASE_URL, "PATCH", path, body, true, responseHandler);
    }

    public static void delete(String path, JSONObject body, @Nullable ResponseHandler responseHandler) {
        send(BASE_URL, "DELETE", path, body, true, responseHandler);
    }

    /**
     * The base URL is a parameter, not a field: the entry points above always
     * pass the final {@link #BASE_URL}, so live traffic cannot be redirected,
     * while tests can aim one request at a local stub server and exercise the
     * real socket, parsing and retry path.
     */
    static void send(String baseUrl, String method, String path, @Nullable JSONObject body,
                     boolean idempotent, @Nullable ResponseHandler responseHandler) {
        // Serialised once so every attempt puts the same bytes on the wire —
        // the event id inside stays stable, which is what lets the backend
        // recognise a resend whose response was lost and count it once.
        byte[] sendBytes;
        try {
            sendBytes = body == null ? null : body.toString().getBytes("UTF-8");
        } catch (Exception e) {
            com.flarelane.BaseErrorHandler.handle(e);
            invokeSafely(responseHandler, false, -1, new JSONObject());
            return;
        }

        new Thread(new Runnable() {
            @Override
            public void run() {
                long deadline = System.currentTimeMillis() + RETRY_DEADLINE_MS;

                for (int attempt = 1; ; attempt++) {
                    int responseCode = -1;
                    JSONObject responseJson = null;
                    HttpURLConnection conn = null;

                    try {
                        conn = openConnection(baseUrl, method, path, sendBytes, deadline);
                        if (sendBytes != null) {
                            OutputStream outputStream = conn.getOutputStream();
                            outputStream.write(sendBytes);
                            // Deliberate: the body is logged in full, identifiers included.
                            // Diagnosing an integration means seeing exactly what was sent, so
                            // verbose logging carries whole HTTP parameters by design across all
                            // four FlareLane SDKs. Apps that cannot keep logs call setLogLevel
                            // with LOG_LEVEL_NONE, which suppresses every line the SDK emits.
                            com.flarelane.Logger.verbose("HTTP " + method + " body: " + body.toString());
                        }
                        // Re-clamped after connect: the connect phase may have spent part of
                        // the budget, and the read timeout set before it still held the full
                        // remainder. The 1s floor can overshoot the deadline by at most a second
                        // — a floor of 0 would mean "wait forever" on this API.
                        conn.setReadTimeout((int) Math.min(READ_TIMEOUT_MS, Math.max(1000, deadline - System.currentTimeMillis())));
                        responseCode = conn.getResponseCode();
                        responseJson = readBody(conn, responseCode);
                    } catch (Exception e) {
                        com.flarelane.BaseErrorHandler.handle(e);
                    } finally {
                        if (conn != null)
                            conn.disconnect();
                    }

                    // A 2xx whose body is missing or unparseable resolves as a failure, as it
                    // always has on this SDK — every FlareLane endpoint answers success with a
                    // JSON body, so a bodyless success here means something upstream is wrong.
                    boolean isSuccess = responseCode >= 200 && responseCode < 400 && responseJson != null;
                    if (isSuccess) {
                        invokeSafely(responseHandler, true, responseCode, responseJson);
                        return;
                    }

                    long delay = delayMillis(attempt);
                    if (!idempotent || !shouldRetry(responseCode, attempt)
                            || System.currentTimeMillis() + delay > deadline) {
                        invokeSafely(responseHandler, false, responseCode,
                                responseJson != null ? responseJson : new JSONObject());
                        return;
                    }

                    // A stop that lands mid-backoff does not cancel this retry on purpose:
                    // at most two more requests reach a dead project and are refused there.
                    // Coupling the HTTP layer to the queue's stopped state is not worth that.
                    com.flarelane.Logger.verbose("Retrying " + method + " " + path + " in " + delay + "ms"
                            + " (attempt " + (attempt + 1) + "/" + MAX_ATTEMPTS + ", status " + responseCode + ")");
                    try {
                        // Sleeping here costs nothing extra: each request already owns this thread.
                        Thread.sleep(delay);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        invokeSafely(responseHandler, false, responseCode, new JSONObject());
                        return;
                    }
                }
            }
        }).start();
    }

    private static HttpURLConnection openConnection(String baseUrl, String method, String path,
                                                    @Nullable byte[] sendBytes, long deadline) throws IOException {
        URL url = new URL(baseUrl + path);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();

        // Clamped to what is left of the call's deadline (floored so a nearly
        // spent budget does not degenerate into instant spurious failures), so
        // a single blocked connect or read cannot spend more than the whole
        // call was given.
        int budgetMs = (int) Math.max(1000, deadline - System.currentTimeMillis());
        conn.setConnectTimeout(Math.min(CONNECT_TIMEOUT_MS, budgetMs));
        conn.setReadTimeout(Math.min(READ_TIMEOUT_MS, budgetMs));
        conn.setUseCaches(false);

        if (sendBytes != null) {
            conn.setDoOutput(true);
            conn.setDoInput(true);
            conn.setFixedLengthStreamingMode(sendBytes.length);
        }

        conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
        conn.setRequestProperty("Accept", "application/json");
        conn.setRequestProperty("x-flarelane-sdk-info", FlareLane.SdkInfo.type + "-" + FlareLane.SdkInfo.version);
        conn.setRequestMethod(method);

        return conn;
    }

    // Single dispatch site for handler callbacks, so the handler contract stays
    // "exactly one of onSuccess/onFailure" per call — retries included. An
    // exception thrown by handler code is contained here instead of unwinding
    // into the retry loop, where it would look like a failed attempt.
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

    /**
     * Reads the body from whichever stream carries it, or null when there is
     * nothing usable. Every error status throws from getInputStream — with two
     * different exception types (FileNotFoundException for 404/410 only, plain
     * IOException otherwise) — so catching IOException here is what keeps the
     * real status code: a 503 must stay distinguishable from a dead connection
     * for the retry decision, and a 410 for the stop signal.
     */
    private static @Nullable JSONObject readBody(HttpURLConnection conn, int responseCode) {
        InputStream in;
        try {
            in = new BufferedInputStream(conn.getInputStream());
        } catch (IOException e) {
            InputStream errorStream = conn.getErrorStream();
            if (errorStream == null) {
                com.flarelane.Logger.error("No response body for status " + responseCode);
                return null;
            }
            in = new BufferedInputStream(errorStream);
        }

        String response = convertStreamToString(in);
        try {
            return new JSONObject(response);
        } catch (org.json.JSONException e) {
            com.flarelane.Logger.error("Failed to parse response JSON: " + e + ", body: " + response);
            return null;
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
            com.flarelane.Logger.error("Failed to read the response stream", e);
        } finally {
            try {
                is.close();
            } catch (IOException e) {
                com.flarelane.Logger.error("Failed to close the response stream", e);
            }
        }

        return sb.toString();
    }
}
