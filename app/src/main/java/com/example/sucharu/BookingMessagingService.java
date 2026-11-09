package com.example.sucharu;

import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;

import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

import java.util.Map;

public class BookingMessagingService extends FirebaseMessagingService {
    public static final String CHANNEL_ID = "sucharu_booking_updates";

    @Override public void onNewToken(String token) {
        super.onNewToken(token);
        if (CustomerSession.isSignedIn(this)) CustomerSession.syncNotificationToken(this);
    }

    @Override public void onMessageReceived(RemoteMessage message) {
        super.onMessageReceived(message);
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return;
        Map<String, String> data = message.getData();
        String bookingId = data.getOrDefault("bookingId", "");
        RemoteMessage.Notification payload = message.getNotification();
        String title = payload == null ? "Sucharu booking update" : payload.getTitle();
        String body = payload == null ? "Open Sucharu to see the latest update." : payload.getBody();
        showNotification(title, body, bookingId);
    }

    private void showNotification(String title, String body, String bookingId) {
        NotificationManager manager = getSystemService(NotificationManager.class);
        if (manager == null) return;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, "Booking updates", NotificationManager.IMPORTANCE_DEFAULT);
            channel.setDescription("Updates about your Sucharu service requests");
            manager.createNotificationChannel(channel);
        }
        Intent open = new Intent(this, MainActivity.class).putExtra("notification_booking_id", bookingId)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent pending = PendingIntent.getActivity(this, bookingId.hashCode(), open,
                PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0));
        NotificationCompat.Builder notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(title == null || title.isEmpty() ? "Sucharu booking update" : title)
                .setContentText(body == null ? "Open Sucharu to see the latest update." : body)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(body == null ? "Open Sucharu to see the latest update." : body))
                .setContentIntent(pending).setAutoCancel(true).setPriority(NotificationCompat.PRIORITY_DEFAULT);
        NotificationManagerCompat.from(this).notify(bookingId.hashCode(), notification.build());
    }
}
