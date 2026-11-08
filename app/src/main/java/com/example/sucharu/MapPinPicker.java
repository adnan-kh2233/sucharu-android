package com.example.sucharu;

import android.app.Activity;
import android.app.Dialog;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;

import com.google.android.material.button.MaterialButton;

import org.json.JSONObject;

public final class MapPinPicker {
    public interface Listener { void onPin(double latitude, double longitude); }
    private MapPinPicker() {}

    public static void show(Activity activity, @Nullable Double latitude, @Nullable Double longitude, Listener listener) {
        Dialog dialog = new Dialog(activity);
        LinearLayout root = new LinearLayout(activity);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackground(roundBackground(activity));
        root.setClipToOutline(true);

        LinearLayout header = new LinearLayout(activity);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(Ui.dp(activity, 18), Ui.dp(activity, 12), Ui.dp(activity, 10), Ui.dp(activity, 8));
        LinearLayout heading = new LinearLayout(activity);
        heading.setOrientation(LinearLayout.VERTICAL);
        TextView title = Ui.text(activity, "Pin your service address", 18, Color.rgb(23, 59, 53), true);
        TextView hint = Ui.text(activity, "Tap the map or drag the marker to your entrance.", 12, Color.rgb(102, 117, 110), false);
        heading.addView(title);
        heading.addView(hint);
        header.addView(heading, new LinearLayout.LayoutParams(0, -2, 1));
        TextView close = Ui.text(activity, "×", 30, Color.rgb(23, 59, 53), false);
        close.setGravity(Gravity.CENTER);
        header.addView(close, new LinearLayout.LayoutParams(Ui.dp(activity, 42), Ui.dp(activity, 46)));
        root.addView(header, new LinearLayout.LayoutParams(-1, -2));

        WebView map = new WebView(activity);
        map.setBackgroundColor(Color.rgb(237, 243, 237));
        map.getSettings().setJavaScriptEnabled(true);
        map.getSettings().setDomStorageEnabled(true);
        map.getSettings().setUserAgentString("Sucharu/1.0 (OpenStreetMap address picker)");
        map.setWebViewClient(new WebViewClient() {
            @Override public void onPageFinished(WebView view, String url) {
                String lat = latitude == null ? "null" : latitude.toString();
                String lon = longitude == null ? "null" : longitude.toString();
                view.evaluateJavascript("window.setInitialPin(" + lat + "," + lon + ");setTimeout(()=>window.invalidateMap(),200);", null);
            }
        });
        root.addView(map, new LinearLayout.LayoutParams(-1, 0, 1));

        LinearLayout footer = new LinearLayout(activity);
        footer.setGravity(Gravity.CENTER_VERTICAL);
        footer.setPadding(Ui.dp(activity, 14), Ui.dp(activity, 9), Ui.dp(activity, 14), Ui.dp(activity, 10));
        footer.setBackgroundColor(Color.WHITE);
        MaterialButton cancel = new MaterialButton(activity);
        cancel.setText("Cancel");
        cancel.setAllCaps(false);
        MaterialButton usePin = new MaterialButton(activity);
        usePin.setText("Use this pin");
        usePin.setAllCaps(false);
        LinearLayout.LayoutParams buttonParams = new LinearLayout.LayoutParams(0, Ui.dp(activity, 48), 1);
        buttonParams.setMarginEnd(Ui.dp(activity, 8));
        footer.addView(cancel, buttonParams);
        LinearLayout.LayoutParams useParams = new LinearLayout.LayoutParams(0, Ui.dp(activity, 48), 1);
        footer.addView(usePin, useParams);
        root.addView(footer, new LinearLayout.LayoutParams(-1, -2));

        dialog.setContentView(root);
        dialog.setOnShowListener(ignored -> {
            Window window = dialog.getWindow();
            if (window != null) {
                window.setBackgroundDrawableResource(android.R.color.transparent);
                window.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT);
                window.setDimAmount(.28f);
            }
        });
        close.setOnClickListener(v -> dialog.dismiss());
        cancel.setOnClickListener(v -> dialog.dismiss());
        usePin.setOnClickListener(v -> map.evaluateJavascript("selectedPin()", result -> {
            try {
                String json = result == null ? "" : String.valueOf(new org.json.JSONTokener(result).nextValue());
                JSONObject point = new JSONObject(json);
                listener.onPin(point.getDouble("latitude"), point.getDouble("longitude"));
                dialog.dismiss();
            } catch (Exception ignoredError) {
                android.widget.Toast.makeText(activity, "Map is still loading. Check your connection and try again.", android.widget.Toast.LENGTH_LONG).show();
            }
        }));
        dialog.show();
        map.loadUrl("file:///android_asset/map_picker.html");
    }

    private static GradientDrawable roundBackground(Activity activity) {
        GradientDrawable background = new GradientDrawable();
        background.setColor(Color.WHITE);
        background.setCornerRadius(Ui.dp(activity, 16));
        return background;
    }
}
