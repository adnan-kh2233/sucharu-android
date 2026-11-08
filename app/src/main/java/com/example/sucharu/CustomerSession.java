package com.example.sucharu;

import android.content.Context;
import android.content.SharedPreferences;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.GetTokenResult;

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
        }
    }

    private static void saveProfile(Context context, JSONObject profile, String legacyToken) {
        SharedPreferences.Editor editor = prefs(context).edit()
                .putString("name", profile.optString("name", "Customer"))
                .putString("phone", profile.optString("phone", ""))
                .putString("email", profile.optString("email", ""));
        if (legacyToken == null) editor.remove("token"); else editor.putString("token", legacyToken);
        editor.apply();
    }

    public static void signOut(Context context) {
        FirebaseAuth.getInstance().signOut();
        prefs(context).edit().clear().apply();
    }
}
