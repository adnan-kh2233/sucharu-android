package com.example.sucharu;

import android.os.Handler;
import android.os.Looper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

public final class ApiClient {
    public interface Callback {
        void onComplete(int status, JSONObject object, JSONArray array, String error);
    }
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private ApiClient() {}
    public static void get(String path, String token, Callback callback) { request("GET", path, null, token, callback); }
    public static void post(String path, JSONObject body, String token, Callback callback) { request("POST", path, body, token, callback); }
    // Android's HttpURLConnection does not accept PATCH in setRequestMethod().
    // Send POST with an explicit override header; the API translates it before routing.
    public static void patch(String path, JSONObject body, String token, Callback callback) { request("POST", path, body, token, callback, true); }

    private static void request(String method, String path, JSONObject body, String token, Callback callback) {
        request(method, path, body, token, callback, false);
    }

    private static void request(String method, String path, JSONObject body, String token, Callback callback, boolean patchOverride) {
        new Thread(() -> {
            int status = 0;
            JSONObject object = null;
            JSONArray array = null;
            String error = null;
            HttpURLConnection connection = null;
            try {
                connection = (HttpURLConnection) new URL(BuildConfig.API_BASE_URL + path).openConnection();
                connection.setRequestMethod(method);
                connection.setConnectTimeout(15000);
                connection.setReadTimeout(15000);
                connection.setRequestProperty("Accept", "application/json");
                if (patchOverride) connection.setRequestProperty("X-HTTP-Method-Override", "PATCH");
                if (token != null && !token.isEmpty()) connection.setRequestProperty("Authorization", "Bearer " + token);
                if (body != null) {
                    connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
                    connection.setDoOutput(true);
                    try (OutputStream output = connection.getOutputStream()) {
                        output.write(body.toString().getBytes(StandardCharsets.UTF_8));
                    }
                }
                status = connection.getResponseCode();
                InputStream stream = status >= 200 && status < 400 ? connection.getInputStream() : connection.getErrorStream();
                if (stream != null) {
                    StringBuilder response = new StringBuilder();
                    try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
                        String line;
                        while ((line = reader.readLine()) != null) response.append(line);
                    }
                    String text = response.toString().trim();
                    if (text.startsWith("[")) array = new JSONArray(text);
                    else if (text.startsWith("{")) object = new JSONObject(text);
                }
                if (status < 200 || status >= 300) error = object == null ? "Request failed. Please try again." : object.optString("message", "Request failed. Please try again.");
            } catch (Exception exception) {
                error = "Could not connect to Sucharu. Check the internet connection and try again.";
            } finally {
                if (connection != null) connection.disconnect();
            }
            final int resultStatus = status;
            final JSONObject resultObject = object;
            final JSONArray resultArray = array;
            final String resultError = error;
            MAIN.post(() -> callback.onComplete(resultStatus, resultObject, resultArray, resultError));
        }, "sucharu-api").start();
    }
}
