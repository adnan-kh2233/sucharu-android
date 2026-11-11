package com.example.sucharu;

import android.app.Application;

public final class SucharuApplication extends Application {
    @Override public void onCreate() { super.onCreate(); ConnectivityMonitor.initialize(this); }
}
