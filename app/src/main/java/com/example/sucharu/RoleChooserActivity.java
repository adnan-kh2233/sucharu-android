package com.example.sucharu;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.card.MaterialCardView;

public class RoleChooserActivity extends AppCompatActivity {
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(Color.rgb(244, 247, 237));
        getWindow().getDecorView().setSystemUiVisibility(android.view.View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(Color.rgb(244, 247, 237));
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setGravity(Gravity.CENTER_HORIZONTAL);
        page.setPadding(dp(22), dp(28), dp(22), dp(30));
        scroll.addView(page);

        TextView logo = text("SUCHARU", 13, Color.rgb(24, 116, 94), true);
        logo.setLetterSpacing(.18f);
        page.addView(logo);
        TextView title = text("Welcome to Sucharu", 27, Color.rgb(23, 60, 52), true);
        title.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(-1, -2);
        titleParams.topMargin = dp(16);
        page.addView(title, titleParams);
        TextView subtitle = text("Choose how you want to continue", 15, Color.rgb(100, 118, 110), false);
        subtitle.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams subtitleParams = new LinearLayout.LayoutParams(-1, -2);
        subtitleParams.topMargin = dp(6);
        page.addView(subtitle, subtitleParams);

        page.addView(roleCard("Customer", "Browse home services, create an account and manage your bookings.", "Continue as customer", "customer"), cardParams(34));
        page.addView(roleCard("Admin", "Manage bookings, partners, services and customer support.", "Admin sign in", "admin"), cardParams(14));
        page.addView(roleCard("Partner", "See assigned work and update each service step.", "Partner sign in", "partner"), cardParams(14));

        TextView footer = text("Your sign-in is checked separately for each role.", 12, Color.rgb(100, 118, 110), false);
        footer.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams footerParams = new LinearLayout.LayoutParams(-1, -2);
        footerParams.topMargin = dp(24);
        page.addView(footer, footerParams);
        setContentView(scroll);
    }

    private MaterialCardView roleCard(String title, String description, String buttonText, String role) {
        MaterialCardView card = new MaterialCardView(this);
        card.setRadius(dp(18));
        card.setCardElevation(dp(1));
        card.setStrokeWidth(dp(1));
        card.setStrokeColor(Color.rgb(223, 231, 218));
        card.setCardBackgroundColor(Color.WHITE);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(18), dp(17), dp(18), dp(16));
        card.addView(content);
        TextView heading = text(title, 19, Color.rgb(23, 60, 52), true);
        content.addView(heading);
        TextView body = text(description, 13, Color.rgb(100, 118, 110), false);
        body.setLineSpacing(dp(2), 1f);
        LinearLayout.LayoutParams bodyParams = new LinearLayout.LayoutParams(-1, -2);
        bodyParams.topMargin = dp(5);
        content.addView(body, bodyParams);
        com.google.android.material.button.MaterialButton button = new com.google.android.material.button.MaterialButton(this);
        button.setText(buttonText);
        button.setAllCaps(false);
        button.setTextColor(Color.WHITE);
        button.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.rgb(24, 116, 94)));
        LinearLayout.LayoutParams buttonParams = new LinearLayout.LayoutParams(-1, -2);
        buttonParams.topMargin = dp(12);
        content.addView(button, buttonParams);
        button.setOnClickListener(v -> openRole(role));
        return card;
    }

    private LinearLayout.LayoutParams cardParams(int topMargin) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.topMargin = dp(topMargin);
        return params;
    }

    private void openRole(String role) {
        if ("customer".equals(role)) {
            Intent intent = new Intent(this, MainActivity.class);
            intent.putExtra(MainActivity.EXTRA_CUSTOMER_AUTH, !CustomerSession.isSignedIn(this));
            startActivity(intent);
        } else {
            startActivity(new Intent(this, StaffPortalActivity.class).putExtra(StaffPortalActivity.EXTRA_ROLE, role));
        }
    }

    private int dp(float value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    private TextView text(String value, float size, int color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        if (bold) view.setTypeface(view.getTypeface(), android.graphics.Typeface.BOLD);
        return view;
    }
}
