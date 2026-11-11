package com.example.sucharu;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public final class LowBatteryReceiver extends BroadcastReceiver {
    private final SystemEventCallbacks callbacks;
    public LowBatteryReceiver(SystemEventCallbacks callbacks) { this.callbacks = callbacks; }

    @Override public void onReceive(Context context, Intent intent) {
        if (intent == null) return;
        if (Intent.ACTION_BATTERY_LOW.equals(intent.getAction())) callbacks.onBatteryLow(true);
        else if (Intent.ACTION_BATTERY_OKAY.equals(intent.getAction())) callbacks.onBatteryLow(false);
    }
}
