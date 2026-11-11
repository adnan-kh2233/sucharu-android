package com.example.sucharu;

/** Callbacks keep system broadcasts scoped to the visible home screen. */
public interface SystemEventCallbacks {
    void onAirplaneModeChanged(boolean enabled);
    void onBatteryLow(boolean low);
}
