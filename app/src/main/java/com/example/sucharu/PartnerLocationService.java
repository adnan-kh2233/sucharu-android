package com.example.sucharu;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Looper;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

import org.json.JSONObject;

public class PartnerLocationService extends Service implements LocationListener {
    public static final String EXTRA_BOOKING_ID = "booking_id";
    public static final String EXTRA_TOKEN = "partner_token";
    public static final String ACTION_STOP = "com.example.sucharu.STOP_PARTNER_LOCATION";
    private static final String CHANNEL_ID = "partner_location_tracking";
    private static final int NOTIFICATION_ID = 7301;
    private LocationManager locationManager;
    private String bookingId, token;
    private long lastSentAt;

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null || ACTION_STOP.equals(intent.getAction())) {
            stopTracking(true);
            return START_NOT_STICKY;
        }
        String newBookingId = intent.getStringExtra(EXTRA_BOOKING_ID);
        String newToken = intent.getStringExtra(EXTRA_TOKEN);
        if (newBookingId == null || newBookingId.isEmpty() || newToken == null || newToken.isEmpty()) {
            stopSelf();
            return START_NOT_STICKY;
        }
        bookingId = newBookingId;
        token = newToken;
        createChannel();
        if (Build.VERSION.SDK_INT >= 29) startForeground(NOTIFICATION_ID, buildNotification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION);
        else startForeground(NOTIFICATION_ID, buildNotification());
        if (!hasLocationPermission()) {
            stopTracking(false);
            return START_NOT_STICKY;
        }
        locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);
        try {
            boolean requested = false;
            if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 10000L, 10f, this, Looper.getMainLooper());
                Location last = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
                if (last != null && System.currentTimeMillis() - last.getTime() < 120000L) onLocationChanged(last);
                requested = true;
            }
            if (locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 10000L, 10f, this, Looper.getMainLooper());
                Location last = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
                if (!requested && last != null && System.currentTimeMillis() - last.getTime() < 120000L) onLocationChanged(last);
                requested = true;
            }
            if (!requested) {
                stopTracking(false);
                openLocationSettingsNotification();
                return START_NOT_STICKY;
            }
            getSharedPreferences("partner_location", MODE_PRIVATE).edit().putString("active_booking", bookingId).apply();
        } catch (SecurityException error) {
            stopTracking(false);
        }
        return START_NOT_STICKY;
    }

    @Override public void onLocationChanged(Location location) {
        if (location == null || bookingId == null || token == null || !location.hasAccuracy() || location.getAccuracy() > 10000f) return;
        long now = System.currentTimeMillis();
        if (now - lastSentAt < 9000L) return;
        lastSentAt = now;
        try {
            JSONObject body = new JSONObject();
            body.put("latitude", location.getLatitude());
            body.put("longitude", location.getLongitude());
            body.put("accuracy", location.getAccuracy());
            ApiClient.post("/partner/jobs/" + java.net.URLEncoder.encode(bookingId, "UTF-8") + "/location", body, token,
                    (status, response, rows, error) -> {
                        if (status == 409 || status == 404) stopTracking(false);
                    });
        } catch (Exception ignored) { }
    }

    private boolean hasLocationPermission() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                || ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    private Notification buildNotification() {
        Intent stop = new Intent(this, PartnerLocationService.class).setAction(ACTION_STOP);
        PendingIntent stopPending = PendingIntent.getService(this, 7302, stop,
                PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0));
        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_menu_mylocation)
                .setContentTitle("Sucharu · sharing trip location")
                .setContentText("Booking " + bookingId + " · tap Stop to pause sharing")
                .setCategory(NotificationCompat.CATEGORY_SERVICE)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .addAction(android.R.drawable.ic_media_pause, "Stop sharing", stopPending)
                .build();
    }

    private void createChannel() {
        if (Build.VERSION.SDK_INT < 26) return;
        NotificationChannel channel = new NotificationChannel(CHANNEL_ID, "Partner trip location", NotificationManager.IMPORTANCE_LOW);
        channel.setDescription("Visible while a partner voluntarily shares location for an active booking.");
        ((NotificationManager) getSystemService(NOTIFICATION_SERVICE)).createNotificationChannel(channel);
    }

    private void openLocationSettingsNotification() {
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_menu_mylocation)
                .setContentTitle("Turn on device location")
                .setContentText("Enable Location and tap Start location sharing on the booking again.")
                .setAutoCancel(true);
        ((NotificationManager) getSystemService(NOTIFICATION_SERVICE)).notify(NOTIFICATION_ID, builder.build());
    }

    private void stopTracking(boolean notifyServer) {
        if (notifyServer && bookingId != null && token != null) {
            ApiClient.post("/partner/jobs/" + bookingId + "/location/stop", new JSONObject(), token, (status, response, rows, error) -> { });
        }
        if (locationManager != null) {
            try { locationManager.removeUpdates(this); } catch (SecurityException ignored) { }
        }
        getSharedPreferences("partner_location", MODE_PRIVATE).edit().remove("active_booking").apply();
        token = null;
        bookingId = null;
        stopForeground(true);
        stopSelf();
    }

    @Override public void onDestroy() {
        if (locationManager != null) {
            try { locationManager.removeUpdates(this); } catch (SecurityException ignored) { }
        }
        super.onDestroy();
    }

    @Nullable @Override public IBinder onBind(Intent intent) { return null; }
    @Override public void onStatusChanged(String provider, int status, Bundle extras) { }
    @Override public void onProviderEnabled(String provider) { }
    @Override public void onProviderDisabled(String provider) { }
}
