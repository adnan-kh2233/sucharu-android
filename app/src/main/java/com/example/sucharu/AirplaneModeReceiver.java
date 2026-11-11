package com.example.sucharu;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public final class AirplaneModeReceiver extends BroadcastReceiver {
    private final SystemEventCallbacks callbacks;
    public AirplaneModeReceiver(SystemEventCallbacks callbacks) { this.callbacks = callbacks; }

    @Override public void onReceive(Context context, Intent intent) {
        if (intent != null && Intent.ACTION_AIRPLANE_MODE_CHANGED.equals(intent.getAction())) {
            callbacks.onAirplaneModeChanged(intent.getBooleanExtra("state", false));
        }
    }
}
