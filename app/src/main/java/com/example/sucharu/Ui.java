package com.example.sucharu;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.google.android.material.card.MaterialCardView;

public final class Ui {
    public static final int GREEN = Color.rgb(23, 107, 85);
    public static final int GREEN_DARK = Color.rgb(16, 63, 53);
    public static final int INK = Color.rgb(23, 59, 53);
    public static final int LIME = Color.rgb(181, 214, 108);
    public static final int CREAM = Color.rgb(248, 247, 240);
    public static final int MUTED = Color.rgb(102, 117, 110);
    public static final int LINE = Color.rgb(226, 233, 225);
    public static final int SOFT_GREEN = Color.rgb(239, 245, 232);
    private Ui() {}
    public static int dp(Context context, float value) { return (int) (value * context.getResources().getDisplayMetrics().density + 0.5f); }

    public static TextView text(Context context, String value, float size, int color, boolean bold) {
        TextView view = new TextView(context);
        view.setText(value);
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, size);
        view.setTextColor(color);
        if (bold) view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return view;
    }

    public static MaterialCardView card(Context context) {
        MaterialCardView card = new MaterialCardView(context);
        card.setCardBackgroundColor(Color.WHITE);
        card.setRadius(dp(context, 16));
        card.setCardElevation(0);
        card.setStrokeColor(LINE);
        card.setStrokeWidth(dp(context, 1));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.bottomMargin = dp(context, 12);
        card.setLayoutParams(params);
        return card;
    }

    public static GradientDrawable pill(int color, int radiusPx) {
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(color);
        shape.setCornerRadius(radiusPx);
        return shape;
    }

    public static View space(Context context, int heightDp) {
        View view = new View(context);
        view.setLayoutParams(new LinearLayout.LayoutParams(1, dp(context, heightDp)));
        return view;
    }
}
