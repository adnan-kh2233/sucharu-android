package com.example.sucharu;

import android.content.Context;
import android.content.SharedPreferences;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.messaging.FirebaseMessaging;

import org.json.JSONObject;

public final class CustomerSession {
    public interface TokenCallback { void complete(String token, String error); }
    public interface SaveCallback { void complete(String error); }

    private static final String PREFS = "sucharu_customer";
    private CustomerSession() {}

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static boolean isSignedIn(Context context) {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        return user != null || !prefs(context).getString("token", "").isEmpty();
    }

    public static String getName(Context context) { return prefs(context).getString("name", ""); }
    public static String getPhone(Context context) { return prefs(context).getString("phone", ""); }
    public static String getEmail(Context context) { return prefs(context).getString("email", ""); }
    public static String getProfileImageUrl(Context context) { return prefs(context).getString("profileImageUrl", ""); }
    public static JSONObject getDefaultAddress(Context context) {
        try { String value = prefs(context).getString("defaultAddress", ""); return value.isEmpty() ? null : new JSONObject(value); }
        catch (Exception ignored) { return null; }
    }
    public static void saveDefaultAddress(Context context, JSONObject address) {
        if (address == null) prefs(context).edit().remove("defaultAddress").apply();
        else prefs(context).edit().putString("defaultAddress", address.toString()).apply();
    }
    public static void saveCustomerProfile(Context context, JSONObject profile) {
        prefs(context).edit()
                .putString("name", profile.optString("name", "Customer"))
                .putString("phone", profile.optString("phone", ""))
                .putString("email", profile.optString("email", ""))
                .putString("profileImageUrl", profile.optString("profileImageUrl", prefs(context).getString("profileImageUrl", "")))
                .apply();
    }

    public static void withToken(Context context, TokenCallback callback) {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            String token = prefs(context).getString("token", "");
            callback.complete(token.isEmpty() ? null : token, token.isEmpty() ? "Please sign in first." : null);
            return;
        }
        user.getIdToken(false).addOnCompleteListener(task -> {
            if (!task.isSuccessful() || task.getResult() == null || task.getResult().getToken() == null) {
                callback.complete(null, "Could not refresh your customer session. Please sign in again.");
                return;
            }
            callback.complete(task.getResult().getToken(), null);
        });
    }

    public static void saveAuthResponse(Context context, JSONObject response, SaveCallback callback) {
        JSONObject profile = response.optJSONObject("customer");
        if (profile == null) { callback.complete("The server did not return a customer profile."); return; }
        String customToken = response.optString("firebaseCustomToken", "");
        if (!customToken.isEmpty()) {
            FirebaseAuth.getInstance().signInWithCustomToken(customToken).addOnCompleteListener(signInTask -> {
                if (!signInTask.isSuccessful() || signInTask.getResult() == null || signInTask.getResult().getUser() == null) {
                    callback.complete("Firebase sign-in failed. Please try again.");
                    return;
                }
                signInTask.getResult().getUser().getIdToken(true).addOnCompleteListener(tokenTask -> {
                    if (!tokenTask.isSuccessful() || tokenTask.getResult() == null) {
                        callback.complete("Could not finish the Firebase session. Please sign in again.");
                        return;
                    }
                    saveProfile(context, profile, null);
                    callback.complete(null);
                });
            });
        } else {
            String token = response.optString("token", "");
            if (token.isEmpty()) { callback.complete("The server did not return a customer session."); return; }
            saveProfile(context, profile, token);
            callback.complete(null);
            // Keep sign-in fast and preserve compatibility with the current website/API JWT.
            // Once the protected exchange route is deployed, upgrade this Android session
            // asynchronously so Firebase Auth can scope Firebase-backed features to this user.
            ensureFirebaseCustomerSession(context, ignored -> { });
        }
    }

    public static void ensureFirebaseCustomerSession(Context context, SaveCallback callback) {
        if (FirebaseAuth.getInstance().getCurrentUser() != null) { callback.complete(null); return; }
        withToken(context, (apiToken, tokenError) -> {
            if (tokenError != null || apiToken == null || apiToken.isEmpty()) { callback.complete(tokenError == null ? "Please sign in first." : tokenError); return; }
            ApiClient.post("/auth/customer/firebase-session", new JSONObject(), apiToken, (status, response, array, error) -> {
                String customToken = response == null ? "" : response.optString("firebaseCustomToken", "");
                if (error != null || status < 200 || status >= 300 || customToken.isEmpty()) {
                    callback.complete(error == null ? "Firebase sign-in is not available yet." : error);
                    return;
                }
                FirebaseAuth.getInstance().signInWithCustomToken(customToken).addOnCompleteListener(task -> {
                    if (!task.isSuccessful() || task.getResult() == null || task.getResult().getUser() == null) {
                        callback.complete("Firebase sign-in failed. Please sign in again.");
                        return;
                    }
                    prefs(context).edit().remove("token").apply();
                    callback.complete(null);
                });
            });
        });
    }

    private static void saveProfile(Context context, JSONObject profile, String legacyToken) {
        SharedPreferences.Editor editor = prefs(context).edit()
                .putString("name", profile.optString("name", "Customer"))
                .putString("phone", profile.optString("phone", ""))
                .putString("email", profile.optString("email", ""));
        if (profile.has("profileImageUrl")) editor.putString("profileImageUrl", profile.optString("profileImageUrl", ""));
        if (legacyToken == null) editor.remove("token"); else editor.putString("token", legacyToken);
        editor.apply();
    }

    public static void signOut(Context context) {
        FirebaseAuth.getInstance().signOut();
        prefs(context).edit().clear().apply();
    }

    public static void syncNotificationToken(Context context) {
        FirebaseMessaging.getInstance().getToken().addOnSuccessListener(token -> {
            if (token == null || token.isEmpty()) return;
            withToken(context, (authToken, error) -> {
                if (error != null || authToken == null) return;
                try {
                    JSONObject body = new JSONObject(); body.put("token", token); body.put("platform", "android");
                    ApiClient.post("/customers/device-tokens", body, authToken, (status, object, array, requestError) -> { });
                } catch (Exception ignored) { }
            });
        });
    }

    public static void removeNotificationToken(Context context, String authToken) {
        FirebaseMessaging.getInstance().getToken().addOnSuccessListener(token -> {
            if (token == null || token.isEmpty() || authToken == null || authToken.isEmpty()) return;
            try {
                JSONObject body = new JSONObject(); body.put("token", token); body.put("action", "delete");
                ApiClient.post("/customers/device-tokens", body, authToken, (status, object, array, error) -> { });
            } catch (Exception ignored) { }
        });
    }
}
