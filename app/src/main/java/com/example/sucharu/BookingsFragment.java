package com.example.sucharu;

import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ScrollView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.google.android.material.card.MaterialCardView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.json.JSONArray;
import org.json.JSONObject;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

public class BookingsFragment extends Fragment {
    private static final String[] PROGRESS_STATUSES = {
            "booking_received", "confirmed", "assigned", "on_the_way", "service_started", "completed"
    };
    private static final String[] PROGRESS_LABELS = {
            "Request received", "Confirmed", "Partner assigned", "Partner on the way", "Service started", "Completed"
    };
    private TextView status, trackingResult;
    private LinearLayout list;
    private EditText bookingId, phone;
    private ScrollView bookingsScroll;

    @Nullable @Override public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_bookings, container, false);
    }

    @Override public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        bookingsScroll = view.findViewById(R.id.bookings_scroll);
        status = view.findViewById(R.id.bookings_status);
        trackingResult = view.findViewById(R.id.track_result);
        list = view.findViewById(R.id.bookings_list);
        bookingId = view.findViewById(R.id.track_booking_id);
        phone = view.findViewById(R.id.track_phone);
        view.findViewById(R.id.refresh_bookings).setOnClickListener(v -> loadBookings());
        view.findViewById(R.id.track_button).setOnClickListener(v -> trackBooking());
        loadBookings();
    }

    public void scrollToTracker() {
        if (bookingsScroll == null) return;
        View target = bookingsScroll.findViewById(R.id.track_booking_card);
        if (target != null) bookingsScroll.post(() -> bookingsScroll.smoothScrollTo(0, target.getTop()));
    }

    private void loadBookings() {
        if (!isAdded()) return;
        if (!CustomerSession.isSignedIn(requireContext())) {
            status.setText("Sign in to see your booking history. You can still track a booking below with its ID and phone number.");
            list.removeAllViews();
            Button signIn = new Button(requireContext());
            signIn.setText("Sign in / Create account");
            signIn.setOnClickListener(v -> ((MainActivity) requireActivity()).showTab(R.id.nav_account));
            list.addView(signIn);
            return;
        }
        status.setText("Loading your bookings…");
        CustomerSession.withToken(requireContext(), (token, tokenError) -> {
            if (!isAdded()) return;
            if (tokenError != null) { status.setText(tokenError); return; }
            ApiClient.get("/customers/bookings", token, (code, object, array, error) -> {
                if (!isAdded()) return;
                list.removeAllViews();
                if (error != null) { status.setText(error); return; }
                JSONArray rows = array == null ? new JSONArray() : array;
                if (rows.length() == 0) { status.setText("No bookings yet. Your service requests will appear here."); return; }
                status.setText("Showing your recent service requests.");
                for (int i = 0; i < rows.length(); i++) {
                    JSONObject row = rows.optJSONObject(i);
                    if (row != null) list.addView(bookingCard(row));
                }
            });
        });
    }

    private View bookingCard(JSONObject row) {
        MaterialCardView card = Ui.card(requireContext());
        LinearLayout content = new LinearLayout(requireContext());
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(Ui.dp(requireContext(), 15), Ui.dp(requireContext(), 13), Ui.dp(requireContext(), 15), Ui.dp(requireContext(), 13));
        card.addView(content);
        JSONObject service = row.optJSONObject("service");
        JSONObject schedule = row.optJSONObject("schedule");
        content.addView(Ui.text(requireContext(), (service == null ? "Service" : service.optString("name", "Service")), 17, Ui.INK, true));
        content.addView(Ui.text(requireContext(), "BOOKING  ·  " + row.optString("bookingId"), 11, Ui.GREEN, true), topMargin(5));
        String bookingStatus = row.optString("status", "booking_received");
        TextView statusBadge = Ui.text(requireContext(), statusLabel(bookingStatus), 12,
                "cancelled".equals(bookingStatus) ? Color.rgb(164, 55, 48) : Ui.GREEN, true);
        statusBadge.setPadding(Ui.dp(requireContext(), 10), Ui.dp(requireContext(), 6), Ui.dp(requireContext(), 10), Ui.dp(requireContext(), 6));
        statusBadge.setBackground(Ui.pill("cancelled".equals(bookingStatus) ? Color.rgb(252, 237, 234) : Ui.SOFT_GREEN, Ui.dp(requireContext(), 18)));
        LinearLayout.LayoutParams badgeParams = new LinearLayout.LayoutParams(-2, -2);
        badgeParams.topMargin = Ui.dp(requireContext(), 9);
        content.addView(statusBadge, badgeParams);
        if (!"cancelled".equals(bookingStatus)) content.addView(progressSummary(bookingStatus), topMargin(10));
        String date = schedule == null ? "" : schedule.optString("date", "");
        String time = schedule == null ? "" : schedule.optString("time", "");
        content.addView(Ui.text(requireContext(), date + (time.isEmpty() ? "" : " · " + time), 13, Ui.MUTED, false), topMargin(5));
        content.addView(Ui.text(requireContext(), row.optString("area", ""), 13, Ui.MUTED, false), topMargin(3));
        TextView details = Ui.text(requireContext(), "View booking progress  ›", 13, Ui.GREEN, true);
        details.setPadding(0, Ui.dp(requireContext(), 10), 0, Ui.dp(requireContext(), 2));
        details.setOnClickListener(v -> showProgressTimeline(row));
        content.addView(details);
        return card;
    }

    private View progressSummary(String bookingStatus) {
        int step = statusIndex(bookingStatus);
        LinearLayout summary = new LinearLayout(requireContext());
        summary.setOrientation(LinearLayout.VERTICAL);

        LinearLayout heading = new LinearLayout(requireContext());
        heading.setOrientation(LinearLayout.HORIZONTAL);
        TextView current = Ui.text(requireContext(), statusLabel(bookingStatus), 13, Ui.INK, true);
        heading.addView(current, new LinearLayout.LayoutParams(0, -2, 1));
        TextView stepCount = Ui.text(requireContext(), "STEP " + (step + 1) + " OF " + PROGRESS_STATUSES.length, 10, Ui.MUTED, true);
        heading.addView(stepCount);
        summary.addView(heading);

        ProgressBar progress = new ProgressBar(requireContext(), null, android.R.attr.progressBarStyleHorizontal);
        progress.setMax(PROGRESS_STATUSES.length * 100);
        progress.setProgress((step + 1) * 100);
        progress.setProgressTintList(android.content.res.ColorStateList.valueOf(Ui.GREEN));
        progress.setProgressBackgroundTintList(android.content.res.ColorStateList.valueOf(Ui.LINE));
        LinearLayout.LayoutParams progressParams = new LinearLayout.LayoutParams(-1, Ui.dp(requireContext(), 6));
        progressParams.topMargin = Ui.dp(requireContext(), 8);
        summary.addView(progress, progressParams);
        return summary;
    }

    private void showProgressTimeline(JSONObject row) {
        String bookingStatus = row.optString("status", "booking_received");
        LinearLayout timeline = new LinearLayout(requireContext());
        timeline.setOrientation(LinearLayout.VERTICAL);
        timeline.setPadding(Ui.dp(requireContext(), 8), Ui.dp(requireContext(), 4), Ui.dp(requireContext(), 8), Ui.dp(requireContext(), 4));

        if ("cancelled".equals(bookingStatus)) {
            timeline.addView(Ui.text(requireContext(), "This request was cancelled. Contact Sucharu if you need help or want to arrange another service.", 14, Ui.MUTED, false));
        } else {
            int activeStep = statusIndex(bookingStatus);
            for (int i = 0; i < PROGRESS_LABELS.length; i++) {
                LinearLayout rowLayout = new LinearLayout(requireContext());
                rowLayout.setOrientation(LinearLayout.HORIZONTAL);
                rowLayout.setGravity(android.view.Gravity.TOP);

                LinearLayout markerColumn = new LinearLayout(requireContext());
                markerColumn.setOrientation(LinearLayout.VERTICAL);
                markerColumn.setGravity(android.view.Gravity.CENTER_HORIZONTAL);
                TextView marker = Ui.text(requireContext(), i <= activeStep ? "●" : "○", 16,
                        i <= activeStep ? Ui.GREEN : Ui.LINE, true);
                marker.setGravity(android.view.Gravity.CENTER);
                markerColumn.addView(marker, new LinearLayout.LayoutParams(Ui.dp(requireContext(), 24), Ui.dp(requireContext(), 24)));
                if (i < PROGRESS_LABELS.length - 1) {
                    View connector = new View(requireContext());
                    connector.setBackgroundColor(i < activeStep ? Ui.GREEN : Ui.LINE);
                    LinearLayout.LayoutParams connectorParams = new LinearLayout.LayoutParams(Ui.dp(requireContext(), 2), Ui.dp(requireContext(), 22));
                    markerColumn.addView(connector, connectorParams);
                }
                rowLayout.addView(markerColumn);

                LinearLayout labelColumn = new LinearLayout(requireContext());
                labelColumn.setOrientation(LinearLayout.VERTICAL);
                labelColumn.setPadding(Ui.dp(requireContext(), 10), Ui.dp(requireContext(), 2), 0, Ui.dp(requireContext(), 14));
                labelColumn.addView(Ui.text(requireContext(), PROGRESS_LABELS[i], 14,
                        i <= activeStep ? Ui.INK : Ui.MUTED, i == activeStep));
                if (i == activeStep) labelColumn.addView(Ui.text(requireContext(), "Current update", 11, Ui.GREEN, false));
                rowLayout.addView(labelColumn, new LinearLayout.LayoutParams(0, -2, 1));
                timeline.addView(rowLayout);
            }
            String partnerName = row.optString("partnerName", "");
            JSONObject partner = row.optJSONObject("partner");
            if (partnerName.isEmpty() && partner != null) partnerName = partner.optString("name", "");
            if (!partnerName.isEmpty()) timeline.addView(Ui.text(requireContext(), "Service partner · " + partnerName, 13, Ui.MUTED, false));
        }

        ScrollView scroll = new ScrollView(requireContext());
        scroll.addView(timeline);
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Booking progress · " + row.optString("bookingId"))
                .setView(scroll)
                .setPositiveButton("Done", null)
                .show();
    }

    private int statusIndex(String bookingStatus) {
        for (int i = 0; i < PROGRESS_STATUSES.length; i++) if (PROGRESS_STATUSES[i].equals(bookingStatus)) return i;
        return 0;
    }

    private LinearLayout.LayoutParams topMargin(int value) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.topMargin = Ui.dp(requireContext(), value);
        return params;
    }

    private void trackBooking() {
        String id = bookingId.getText().toString().trim().toUpperCase();
        String number = phone.getText().toString().trim();
        if (id.isEmpty() || number.isEmpty()) { trackingResult.setText("Enter both the booking ID and booking phone."); return; }
        trackingResult.setText("Checking booking status…");
        try {
            String query = "?bookingId=" + URLEncoder.encode(id, StandardCharsets.UTF_8.name()) + "&phone=" + URLEncoder.encode(number, StandardCharsets.UTF_8.name());
            ApiClient.get("/bookings/track" + query, null, (code, object, array, error) -> {
                if (!isAdded()) return;
                if (error != null || object == null) { trackingResult.setText(error == null ? "Booking could not be found." : error); return; }
                StringBuilder result = new StringBuilder();
                result.append(object.optString("service", "Service")).append(" · ").append(statusLabel(object.optString("status")));
                JSONObject schedule = object.optJSONObject("schedule");
                if (schedule != null) result.append("\n").append(schedule.optString("date", "")).append(" · ").append(schedule.optString("time", ""));
                result.append("\n").append(object.optString("area", ""));
                String partner = object.optString("partnerName", "");
                if (!partner.isEmpty()) result.append("\nPartner: ").append(partner);
                trackingResult.setText(result + "\n\nTap here to view the full progress timeline.");
                trackingResult.setOnClickListener(v -> showProgressTimeline(object));
            });
        } catch (Exception exception) {
            Toast.makeText(requireContext(), "Could not prepare the booking lookup.", Toast.LENGTH_LONG).show();
        }
    }

    private String statusLabel(String status) {
        switch (status) {
            case "booking_received": return "Request received";
            case "confirmed": return "Confirmed";
            case "assigned": return "Partner assigned";
            case "on_the_way": return "Partner on the way";
            case "service_started": return "Service started";
            case "completed": return "Completed";
            case "cancelled": return "Cancelled";
            default: return status.replace('_', ' ');
        }
    }
}
