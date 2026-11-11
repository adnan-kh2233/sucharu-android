package com.example.sucharu;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.os.Handler;
import android.os.Looper;

import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

public final class ConnectivityMonitor {
    private static final AtomicBoolean connected = new AtomicBoolean(false);
    private static final CopyOnWriteArrayList<Listener> listeners = new CopyOnWriteArrayList<>();
    private static final Handler mainHandler = new Handler(Looper.getMainLooper());
    private static volatile boolean initialized;
    private ConnectivityMonitor() {}

    public static synchronized void initialize(Context context) {
        if (initialized) return;
        ConnectivityManager manager = (ConnectivityManager) context.getApplicationContext().getSystemService(Context.CONNECTIVITY_SERVICE);
        if (manager == null) { connected.set(false); initialized = true; return; }
        Network active = manager.getActiveNetwork();
        NetworkCapabilities caps = active == null ? null : manager.getNetworkCapabilities(active);
        connected.set(hasValidatedInternet(caps));
        try {
            manager.registerNetworkCallback(new NetworkRequest.Builder().addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET).build(), new ConnectivityManager.NetworkCallback() {
                @Override public void onAvailable(Network network) {
                    update(hasValidatedInternet(manager.getNetworkCapabilities(network)));
                }
                @Override public void onCapabilitiesChanged(Network network, NetworkCapabilities capabilities) {
                    update(hasValidatedInternet(capabilities));
                }
                @Override public void onLost(Network network) {
                    Network current = manager.getActiveNetwork();
                    NetworkCapabilities currentCaps = current == null ? null : manager.getNetworkCapabilities(current);
                    update(hasValidatedInternet(currentCaps));
                }
            });
        } catch (RuntimeException ignored) { }
        initialized = true;
    }

    public static boolean isConnected() { return connected.get(); }

    public interface Listener { void onConnectivityChanged(boolean available); }

    public static void addListener(Listener listener) {
        if (listener == null) return;
        listeners.addIfAbsent(listener);
        boolean current = isConnected();
        mainHandler.post(() -> { if (listeners.contains(listener)) listener.onConnectivityChanged(current); });
    }

    public static void removeListener(Listener listener) { listeners.remove(listener); }

    private static void update(boolean available) {
        boolean changed = connected.getAndSet(available) != available;
        if (changed) mainHandler.post(() -> {
            for (Listener listener : listeners) listener.onConnectivityChanged(available);
        });
    }

    private static boolean hasValidatedInternet(NetworkCapabilities capabilities) {
        return capabilities != null
                && capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                && capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED);
    }
}
