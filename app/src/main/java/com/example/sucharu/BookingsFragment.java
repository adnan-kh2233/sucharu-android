package com.example.sucharu;

import android.graphics.Color;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.RatingBar;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ScrollView;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.app.Dialog;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.google.android.material.card.MaterialCardView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.json.JSONArray;
import org.json.JSONObject;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class BookingsFragment extends Fragment {
    private static final String[] PROGRESS_STATUSES = {
            "booking_received", "confirmed", "assigned", "on_the_way", "service_started", "awaiting_customer_confirmation", "completed"
    };
    private static final String[] PROGRESS_LABELS = {
            "Request received", "Confirmed", "Partner assigned", "Partner on the way", "Service started", "Waiting for your confirmation", "Completed"
    };
    private TextView status, trackingResult;
    private LinearLayout list;
    private EditText bookingId, phone;
    private ScrollView bookingsScroll;
    private boolean bookingUpdateReceiverRegistered;
    private final BroadcastReceiver bookingUpdateReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) { if (isAdded()) loadBookings(); }
    };

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
        view.findViewById(R.id.open_notifications).setOnClickListener(v -> loadNotifications());
        view.findViewById(R.id.track_button).setOnClickListener(v -> trackBooking());
        loadBookings();
    }

    @Override public void onStart() {
        super.onStart();
        if (bookingUpdateReceiverRegistered) return;
        IntentFilter filter = new IntentFilter(BookingMessagingService.ACTION_CUSTOMER_BOOKING_UPDATED);
        if (Build.VERSION.SDK_INT >= 33) requireContext().registerReceiver(bookingUpdateReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        else requireContext().registerReceiver(bookingUpdateReceiver, filter);
        bookingUpdateReceiverRegistered = true;
    }

    @Override public void onStop() {
        if (bookingUpdateReceiverRegistered && getContext() != null) {
            try { requireContext().unregisterReceiver(bookingUpdateReceiver); } catch (IllegalArgumentException ignored) { }
            bookingUpdateReceiverRegistered = false;
        }
        super.onStop();
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
        JSONObject partner = row.optJSONObject("partner");
        if (partner != null && !partner.optString("name").isEmpty()) {
            content.addView(Ui.text(requireContext(), "Service partner · " + partner.optString("name"), 13, Ui.GREEN, true), topMargin(7));
        }
        String date = schedule == null ? "" : schedule.optString("date", "");
        String time = schedule == null ? "" : schedule.optString("time", "");
        content.addView(Ui.text(requireContext(), date + (time.isEmpty() ? "" : " · " + time), 13, Ui.MUTED, false), topMargin(5));
        content.addView(Ui.text(requireContext(), row.optString("area", ""), 13, Ui.MUTED, false), topMargin(3));
        TextView details = Ui.text(requireContext(), "View booking progress  ›", 13, Ui.GREEN, true);
        details.setPadding(0, Ui.dp(requireContext(), 10), 0, Ui.dp(requireContext(), 2));
        details.setOnClickListener(v -> showProgressTimeline(row));
        content.addView(details);
        if ("on_the_way".equals(bookingStatus) && partner != null && !partner.optString("name").isEmpty()) {
            Button trackPartner = new Button(requireContext());
            trackPartner.setText("Track partner live");
            trackPartner.setAllCaps(false);
            trackPartner.setOnClickListener(v -> showPartnerTracker(row.optString("bookingId")));
            content.addView(trackPartner, topMargin(5));
            Button startCode = new Button(requireContext());
            startCode.setText("Show service start code");
            startCode.setAllCaps(false);
            startCode.setOnClickListener(v -> requestServiceStartCode(row.optString("bookingId")));
            content.addView(startCode, topMargin(3));
        }
        if (row.optBoolean("completionProofPresent") && ("awaiting_customer_confirmation".equals(bookingStatus) || "customer_issue_reported".equals(bookingStatus) || "completed".equals(bookingStatus))) {
            Button report = new Button(requireContext()); report.setText("View service report"); report.setAllCaps(false);
            report.setOnClickListener(v -> showCompletionReport(row));
            content.addView(report, topMargin(4));
        }
        if (canCustomerManage(bookingStatus)) {
            Button manage = new Button(requireContext()); manage.setText("Change date or cancel"); manage.setAllCaps(false);
            manage.setOnClickListener(v -> showManageBooking(row)); content.addView(manage, topMargin(4));
        }
        if ("completed".equals(bookingStatus) && row.optJSONObject("review") == null) {
            Button review = new Button(requireContext()); review.setText("Rate this service"); review.setAllCaps(false);
            review.setOnClickListener(v -> showReviewDialog(row)); content.addView(review, topMargin(4));
        } else if (row.optJSONObject("review") != null) {
            JSONObject review = row.optJSONObject("review");
            content.addView(Ui.text(requireContext(), "Your rating · " + review.optInt("rating") + "/5" + (review.optString("comment").isEmpty() ? "" : "\n" + review.optString("comment")), 13, Ui.MUTED, false), topMargin(6));
        }
        return card;
    }

    private void showPartnerTracker(String bookingId) {
        CustomerSession.withToken(requireContext(), (token, tokenError) -> {
            if (!isAdded()) return;
            if (tokenError != null) { Toast.makeText(requireContext(), tokenError, Toast.LENGTH_LONG).show(); return; }
            Dialog dialog = new Dialog(requireContext());
            LinearLayout root = new LinearLayout(requireContext()); root.setOrientation(LinearLayout.VERTICAL); root.setBackgroundColor(Color.WHITE);
            LinearLayout header = new LinearLayout(requireContext()); header.setOrientation(LinearLayout.VERTICAL); header.setPadding(Ui.dp(requireContext(), 18), Ui.dp(requireContext(), 14), Ui.dp(requireContext(), 18), Ui.dp(requireContext(), 10));
            header.addView(Ui.text(requireContext(), "Partner on the way", 19, Ui.INK, true));
            TextView updateText = Ui.text(requireContext(), "Waiting for the partner’s first GPS update…", 12, Ui.MUTED, false); updateText.setPadding(0, Ui.dp(requireContext(), 4), 0, 0); header.addView(updateText);
            root.addView(header, new LinearLayout.LayoutParams(-1, -2));
            WebView map = new WebView(requireContext()); map.setBackgroundColor(Color.rgb(237, 243, 237)); map.getSettings().setJavaScriptEnabled(true); map.getSettings().setDomStorageEnabled(true);
            final boolean[] ready = {false};
            map.setWebViewClient(new WebViewClient() { @Override public void onPageFinished(WebView view, String url) { ready[0] = true; } });
            root.addView(map, new LinearLayout.LayoutParams(-1, 0, 1));
            com.google.android.material.button.MaterialButton close = new com.google.android.material.button.MaterialButton(requireContext()); close.setText("Close map"); close.setAllCaps(false);
            LinearLayout.LayoutParams closeParams = new LinearLayout.LayoutParams(-1, Ui.dp(requireContext(), 48)); closeParams.setMargins(Ui.dp(requireContext(), 14), Ui.dp(requireContext(), 8), Ui.dp(requireContext(), 14), Ui.dp(requireContext(), 12)); root.addView(close, closeParams);
            dialog.setContentView(root);
            dialog.setOnShowListener(ignored -> { Window window = dialog.getWindow(); if (window != null) { window.setBackgroundDrawableResource(android.R.color.transparent); window.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT); } });
            Handler handler = new Handler(Looper.getMainLooper());
            Runnable[] poll = new Runnable[1];
            poll[0] = () -> {
                if (!dialog.isShowing() || !isAdded()) return;
                ApiClient.get("/customers/bookings/" + encodePath(bookingId) + "/location", token, (code, object, rows, error) -> {
                    if (!dialog.isShowing() || !isAdded()) return;
                    if (code == 409) { updateText.setText("Trip tracking has ended for this booking."); return; }
                    if (error != null || object == null) updateText.setText(error == null ? "Waiting for the partner to share their location…" : error);
                    else {
                        double latitude = object.optDouble("latitude", Double.NaN), longitude = object.optDouble("longitude", Double.NaN);
                        if (Double.isFinite(latitude) && Double.isFinite(longitude)) {
                            int accuracy = object.optInt("accuracy", -1);
                            updateText.setText("Location updated just now" + (accuracy >= 0 ? " · accuracy about " + accuracy + " m" : ""));
                            if (ready[0]) map.evaluateJavascript("window.updatePartnerPin(" + latitude + "," + longitude + ");", null);
                        }
                    }
                    handler.postDelayed(poll[0], 10000L);
                });
            };
            close.setOnClickListener(v -> dialog.dismiss());
            dialog.setOnDismissListener(ignored -> { handler.removeCallbacks(poll[0]); map.stopLoading(); map.destroy(); });
            dialog.show();
            map.loadDataWithBaseURL("https://unpkg.com/", trackingMapHtml(), "text/html", "UTF-8", null);
            poll[0].run();
        });
    }

    private String trackingMapHtml() {
        return "<!doctype html><html><head><meta name='viewport' content='width=device-width,initial-scale=1,maximum-scale=1,user-scalable=no'><link rel='stylesheet' href='https://unpkg.com/leaflet@1.9.4/dist/leaflet.css'><style>html,body,#map{height:100%;width:100%;margin:0}#map{background:#edf3ed}.leaflet-control-attribution{font-size:10px!important}</style></head><body><div id='map'></div><script src='https://unpkg.com/leaflet@1.9.4/dist/leaflet.js'></script><script>const map=L.map('map').setView([22.8456,89.5403],13);L.tileLayer('https://tile.openstreetmap.org/{z}/{x}/{y}.png',{maxZoom:19,attribution:'&copy; OpenStreetMap contributors'}).addTo(map);let marker=null;window.updatePartnerPin=(lat,lon)=>{const point=[lat,lon];if(!marker)marker=L.marker(point).addTo(map).bindPopup('Service partner');else marker.setLatLng(point);map.setView(point,16);setTimeout(()=>map.invalidateSize(true),100)};</script></body></html>";
    }

    private String encodePath(String value) {
        try { return java.net.URLEncoder.encode(value, StandardCharsets.UTF_8.name()).replace("+", "%20"); }
        catch (Exception ignored) { return value; }
    }

    private void requestServiceStartCode(String bookingId) {
        String key = "start_code_" + bookingId;
        android.content.SharedPreferences cache = requireContext().getSharedPreferences("customer_start_codes", android.content.Context.MODE_PRIVATE);
        String savedCode = cache.getString(key, "");
        if (!savedCode.isEmpty() && cache.getLong(key + "_expires", 0) > System.currentTimeMillis()) { showServiceStartCode(bookingId, savedCode); return; }
        CustomerSession.withToken(requireContext(), (token, tokenError) -> {
            if (!isAdded()) return;
            if (tokenError != null) { Toast.makeText(requireContext(), tokenError, Toast.LENGTH_LONG).show(); return; }
            ApiClient.post("/customers/bookings/" + encodePath(bookingId) + "/start-code", new JSONObject(), token, (code, object, rows, error) -> {
                if (!isAdded()) return;
                if (error != null || object == null) { Toast.makeText(requireContext(), error == null ? "Could not generate the service code." : error, Toast.LENGTH_LONG).show(); return; }
                String generated = object.optString("code", "");
                if (generated.matches("\\d{6}")) cache.edit().putString(key, generated).putLong(key + "_expires", System.currentTimeMillis() + 15 * 60 * 1000L).apply();
                showServiceStartCode(bookingId, generated);
            });
        });
    }

    private void showServiceStartCode(String bookingId, String code) {
        TextView value = Ui.text(requireContext(), code, 34, Ui.GREEN, true); value.setGravity(android.view.Gravity.CENTER); value.setLetterSpacing(.25f);
        LinearLayout content = new LinearLayout(requireContext()); content.setOrientation(LinearLayout.VERTICAL); content.setPadding(Ui.dp(requireContext(), 22), Ui.dp(requireContext(), 8), Ui.dp(requireContext(), 22), 0);
        content.addView(value);
        content.addView(Ui.text(requireContext(), "Share this one-time code only with your assigned partner after they arrive. It expires in 15 minutes.", 14, Ui.MUTED, false), topMargin(10));
        new MaterialAlertDialogBuilder(requireContext()).setTitle("Service start code · " + bookingId).setView(content).setPositiveButton("Done", null).show();
    }

    private void showCompletionReport(JSONObject booking) {
        String bookingId = booking.optString("bookingId"), bookingStatus = booking.optString("status");
        CustomerSession.withToken(requireContext(), (token, tokenError) -> {
            if (!isAdded()) return;
            if (tokenError != null) { Toast.makeText(requireContext(), tokenError, Toast.LENGTH_LONG).show(); return; }
            ApiClient.get("/customers/bookings/" + encodePath(bookingId) + "/completion-proof", token, (code, object, rows, error) -> {
                if (!isAdded()) return;
                if (error != null || object == null) { Toast.makeText(requireContext(), error == null ? "Could not load the service report." : error, Toast.LENGTH_LONG).show(); return; }
                LinearLayout report = new LinearLayout(requireContext()); report.setOrientation(LinearLayout.VERTICAL); report.setPadding(Ui.dp(requireContext(), 20), 0, Ui.dp(requireContext(), 20), 0);
                JSONObject checklist = object.optJSONObject("checklist");
                report.addView(Ui.text(requireContext(), (checklist != null && checklist.optBoolean("workCompleted") ? "✓" : "○") + "  Work completed as agreed", 14, Ui.INK, false));
                report.addView(Ui.text(requireContext(), (checklist != null && checklist.optBoolean("areaChecked") ? "✓" : "○") + "  Service area checked", 14, Ui.INK, false), topMargin(4));
                report.addView(Ui.text(requireContext(), (checklist != null && checklist.optBoolean("followUpExplained") ? "✓" : "○") + "  Follow-up explained", 14, Ui.INK, false), topMargin(4));
                String notes = object.optString("notes", "");
                if (!notes.isEmpty()) report.addView(Ui.text(requireContext(), "Partner note\n" + notes, 14, Ui.MUTED, false), topMargin(10));
                addProofImage(report, "Before service", object.optString("beforeImage", ""));
                addProofImage(report, "After service", object.optString("afterImage", ""));
                ScrollView scroll = new ScrollView(requireContext()); scroll.addView(report);
                MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(requireContext()).setTitle("Service report · " + bookingId).setView(scroll).setPositiveButton("Close", null);
                if ("awaiting_customer_confirmation".equals(bookingStatus)) {
                    builder.setNeutralButton("Report a problem", (dialog, which) -> promptServiceIssue(bookingId));
                    builder.setNegativeButton("Confirm service", (dialog, which) -> submitCustomerConfirmation(bookingId, "confirm", ""));
                }
                builder.show();
            });
        });
    }

    private void promptServiceIssue(String bookingId) {
        EditText reason = new EditText(requireContext()); reason.setHint("Describe what went wrong (10–1500 characters)"); reason.setMinLines(3); reason.setMaxLines(6); reason.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        new MaterialAlertDialogBuilder(requireContext()).setTitle("Report a service concern").setMessage("Sucharu support will follow up. The booking will stay pending until the issue is resolved.").setView(reason).setNegativeButton("Back", null).setPositiveButton("Send to support", (dialog, which) -> submitCustomerConfirmation(bookingId, "report_issue", reason.getText().toString().trim())).show();
    }

    private void submitCustomerConfirmation(String bookingId, String action, String reason) {
        if ("report_issue".equals(action) && reason.length() < 10) { Toast.makeText(requireContext(), "Please describe the problem in at least 10 characters.", Toast.LENGTH_LONG).show(); promptServiceIssue(bookingId); return; }
        try {
            JSONObject body = new JSONObject(); body.put("action", action); if ("report_issue".equals(action)) body.put("reason", reason);
            CustomerSession.withToken(requireContext(), (token, tokenError) -> {
                if (!isAdded()) return;
                if (tokenError != null) { Toast.makeText(requireContext(), tokenError, Toast.LENGTH_LONG).show(); return; }
                ApiClient.post("/customers/bookings/" + encodePath(bookingId) + "/confirmation", body, token, (code, object, rows, error) -> {
                    if (!isAdded()) return;
                    Toast.makeText(requireContext(), error == null && object != null ? object.optString("message", "Booking updated.") : error == null ? "Could not update the booking." : error, Toast.LENGTH_LONG).show();
                    if (error == null && code >= 200 && code < 300) loadBookings();
                });
            });
        } catch (Exception ignored) { Toast.makeText(requireContext(), "Could not prepare this request.", Toast.LENGTH_LONG).show(); }
    }

    private void addProofImage(LinearLayout parent, String heading, String encoded) {
        if (encoded == null || encoded.isEmpty()) return;
        try {
            byte[] bytes = android.util.Base64.decode(encoded, android.util.Base64.DEFAULT);
            Bitmap bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
            if (bitmap == null) return;
            parent.addView(Ui.text(requireContext(), heading, 13, Ui.GREEN, true), topMargin(12));
            ImageView image = new ImageView(requireContext()); image.setImageBitmap(bitmap); image.setAdjustViewBounds(true); image.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, Ui.dp(requireContext(), 220)); params.topMargin = Ui.dp(requireContext(), 5); parent.addView(image, params);
        } catch (Exception ignored) { }
    }

    private boolean canCustomerManage(String value) { return "booking_received".equals(value) || "confirmed".equals(value); }

    private void showManageBooking(JSONObject row) {
        String bookingId = row.optString("bookingId");
        new MaterialAlertDialogBuilder(requireContext()).setTitle("Manage booking")
                .setItems(new String[]{"Reschedule", "Cancel booking", "Keep booking"}, (dialog, which) -> {
                    if (which == 0) showRescheduleDialog(row);
                    else if (which == 1) confirmCancelBooking(bookingId);
                }).show();
    }

    private void showRescheduleDialog(JSONObject row) {
        LinearLayout form = new LinearLayout(requireContext()); form.setOrientation(LinearLayout.VERTICAL); form.setPadding(Ui.dp(requireContext(), 20), 0, Ui.dp(requireContext(), 20), 0);
        EditText date = new EditText(requireContext()); date.setHint("New date · YYYY-MM-DD"); date.setInputType(android.text.InputType.TYPE_CLASS_DATETIME | android.text.InputType.TYPE_DATETIME_VARIATION_DATE); date.setSingleLine(true);
        JSONObject current = row.optJSONObject("schedule"); if (current != null) date.setText(current.optString("date", ""));
        Spinner time = new Spinner(requireContext()); String[] windows = {"09:00 AM - 11:00 AM", "11:00 AM - 01:00 PM", "02:00 PM - 04:00 PM", "04:00 PM - 06:00 PM", "06:00 PM - 08:00 PM"};
        time.setAdapter(new ArrayAdapter<>(requireContext(), android.R.layout.simple_spinner_dropdown_item, windows));
        form.addView(date); form.addView(time);
        if (current != null) for (int i = 0; i < windows.length; i++) if (windows[i].equals(current.optString("time"))) time.setSelection(i);
        new MaterialAlertDialogBuilder(requireContext()).setTitle("Choose a new time")
                .setMessage("You can reschedule up to 3 times before a partner is assigned.")
                .setView(form).setNegativeButton("Back", null).setPositiveButton("Save new time", (d, w) -> {
                    try {
                        JSONObject body = new JSONObject(); body.put("date", date.getText().toString().trim()); body.put("time", time.getSelectedItem().toString());
                        CustomerSession.withToken(requireContext(), (token, tokenError) -> {
                            if (tokenError != null) { Toast.makeText(requireContext(), tokenError, Toast.LENGTH_LONG).show(); return; }
                            ApiClient.patch("/customers/bookings/" + row.optString("bookingId") + "/schedule", body, token, (code, object, array, error) -> {
                                if (!isAdded()) return;
                                Toast.makeText(requireContext(), error == null && code < 300 ? "Booking rescheduled." : error == null ? object.optString("message", "Could not reschedule.") : error, Toast.LENGTH_LONG).show();
                                if (error == null && code >= 200 && code < 300) loadBookings();
                            });
                        });
                    } catch (Exception ignored) { }
                }).show();
    }

    private void confirmCancelBooking(String bookingId) {
        EditText reason = new EditText(requireContext()); reason.setHint("Reason (optional)"); reason.setMaxLines(3);
        new MaterialAlertDialogBuilder(requireContext()).setTitle("Cancel this booking?")
                .setMessage("You can cancel before a service partner is assigned.").setView(reason)
                .setNegativeButton("Keep booking", null).setPositiveButton("Cancel booking", (d, w) -> {
                    try {
                        JSONObject body = new JSONObject(); body.put("reason", reason.getText().toString().trim());
                        CustomerSession.withToken(requireContext(), (token, tokenError) -> {
                            if (tokenError != null) { Toast.makeText(requireContext(), tokenError, Toast.LENGTH_LONG).show(); return; }
                            ApiClient.post("/customers/bookings/" + bookingId + "/cancel", body, token, (code, object, array, error) -> {
                                if (!isAdded()) return;
                                Toast.makeText(requireContext(), error == null && code < 300 ? "Booking cancelled." : error == null ? object.optString("message", "Could not cancel this booking.") : error, Toast.LENGTH_LONG).show();
                                if (error == null && code >= 200 && code < 300) loadBookings();
                            });
                        });
                    } catch (Exception ignored) { }
                }).show();
    }

    private void showReviewDialog(JSONObject row) {
        LinearLayout form = new LinearLayout(requireContext()); form.setOrientation(LinearLayout.VERTICAL); form.setPadding(Ui.dp(requireContext(), 20), 0, Ui.dp(requireContext(), 20), 0);
        RatingBar rating = new RatingBar(requireContext()); rating.setNumStars(5); rating.setStepSize(1f); rating.setRating(5f);
        EditText comment = new EditText(requireContext()); comment.setHint("Tell us about your experience (optional)"); comment.setMinLines(3); comment.setMaxLines(5); comment.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE); comment.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(1000)});
        form.addView(rating); form.addView(comment);
        new MaterialAlertDialogBuilder(requireContext()).setTitle("Rate your service").setView(form).setNegativeButton("Later", null)
                .setPositiveButton("Submit review", (d, w) -> {
                    try {
                        JSONObject body = new JSONObject(); body.put("rating", (int) rating.getRating()); body.put("comment", comment.getText().toString().trim());
                        CustomerSession.withToken(requireContext(), (token, tokenError) -> {
                            if (tokenError != null) { Toast.makeText(requireContext(), tokenError, Toast.LENGTH_LONG).show(); return; }
                            ApiClient.post("/customers/bookings/" + row.optString("bookingId") + "/review", body, token, (code, object, array, error) -> {
                                if (!isAdded()) return;
                                Toast.makeText(requireContext(), error == null && code < 300 ? "Thank you for your review." : error == null ? object.optString("message", "Could not submit review.") : error, Toast.LENGTH_LONG).show();
                                if (error == null && code >= 200 && code < 300) loadBookings();
                            });
                        });
                    } catch (Exception ignored) { }
                }).show();
    }

    private void loadNotifications() {
        CustomerSession.withToken(requireContext(), (token, tokenError) -> {
            if (tokenError != null) { Toast.makeText(requireContext(), tokenError, Toast.LENGTH_LONG).show(); return; }
            ApiClient.get("/customers/notifications", token, (code, object, rows, error) -> {
                if (!isAdded()) return;
                JSONArray notifications = rows == null ? new JSONArray() : rows;
                List<String> labels = new ArrayList<>();
                for (int i = 0; i < notifications.length(); i++) { JSONObject item = notifications.optJSONObject(i); if (item != null) labels.add((item.isNull("readAt") ? "● " : "") + item.optString("title") + "\n" + item.optString("body")); }
                if (labels.isEmpty()) { new MaterialAlertDialogBuilder(requireContext()).setTitle("Notifications").setMessage("You are all caught up.").setPositiveButton("Done", null).show(); return; }
                new MaterialAlertDialogBuilder(requireContext()).setTitle("Notifications").setItems(labels.toArray(new String[0]), (dialog, index) -> {
                    JSONObject item = notifications.optJSONObject(index);
                    if (item != null && item.isNull("readAt")) ApiClient.patch("/customers/notifications/" + item.optString("id") + "/read", new JSONObject(), token, (s, o, a, e) -> { });
                }).setPositiveButton("Done", null).show();
            });
        });
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
        if ("customer_issue_reported".equals(bookingStatus)) return 5;
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
            case "awaiting_customer_confirmation": return "Confirm your service";
            case "customer_issue_reported": return "Support is reviewing your concern";
            case "completed": return "Completed";
            case "cancelled": return "Cancelled";
            default: return status.replace('_', ' ');
        }
    }
}
