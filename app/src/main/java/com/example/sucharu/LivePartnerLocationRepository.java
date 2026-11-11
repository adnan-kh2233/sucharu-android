package com.example.sucharu;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

public final class LivePartnerLocationRepository {
    public interface Listener {
        void onLocation(double latitude, double longitude, int accuracyMeters, long updatedAt);
        void onWaiting();
        void onError(String message);
    }

    public static final class Subscription {
        private final DatabaseReference reference;
        private final ValueEventListener listener;
        private Subscription(DatabaseReference reference, ValueEventListener listener) {
            this.reference = reference;
            this.listener = listener;
        }
        public void remove() { reference.removeEventListener(listener); }
    }

    private LivePartnerLocationRepository() { }

    public static boolean isConfigured() { return !BuildConfig.REALTIME_DATABASE_URL.trim().isEmpty(); }

    public static Subscription observe(String bookingId, Listener callback) {
        if (!isConfigured()) throw new IllegalStateException("Realtime Database URL is not configured.");
        DatabaseReference reference = FirebaseDatabase.getInstance(BuildConfig.REALTIME_DATABASE_URL)
                .getReference("livePartnerLocations").child(bookingId);
        ValueEventListener listener = new ValueEventListener() {
            @Override public void onDataChange(DataSnapshot snapshot) {
                if (!snapshot.exists()) { callback.onWaiting(); return; }
                Object latitudeValue = snapshot.child("latitude").getValue();
                Object longitudeValue = snapshot.child("longitude").getValue();
                Object accuracyValue = snapshot.child("accuracy").getValue();
                Long updatedAt = snapshot.child("updatedAt").getValue(Long.class);
                long timestamp = updatedAt == null ? 0 : updatedAt;
                if (!(latitudeValue instanceof Number) || !(longitudeValue instanceof Number) || timestamp == 0
                        || System.currentTimeMillis() - timestamp > 120_000L) {
                    callback.onWaiting();
                    return;
                }
                Number accuracy = accuracyValue instanceof Number ? (Number) accuracyValue : null;
                callback.onLocation(((Number) latitudeValue).doubleValue(), ((Number) longitudeValue).doubleValue(),
                        accuracy == null ? -1 : accuracy.intValue(), timestamp);
            }
            @Override public void onCancelled(DatabaseError error) { callback.onError(error.getMessage()); }
        };
        reference.addValueEventListener(listener);
        return new Subscription(reference, listener);
    }
}
