package com.example.sucharu;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.core.content.ContextCompat;

import com.google.android.material.card.MaterialCardView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.firebase.messaging.FirebaseMessaging;

import org.json.JSONArray;
import org.json.JSONObject;

import java.net.URLEncoder;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

public class StaffPortalActivity extends AppCompatActivity {
    public static final String EXTRA_ROLE = "staff_role";
    private static final int INK = Color.rgb(23, 60, 52), GREEN = Color.rgb(24, 116, 94), MUTED = Color.rgb(100, 118, 110);
    private static final String[] BOOKING_STATUSES = {"booking_received", "confirmed", "assigned", "on_the_way", "service_started", "awaiting_customer_confirmation", "customer_issue_reported", "completed", "cancelled"};
    private String role, token, staffName;
    private LinearLayout page, section;
    private ActivityResultLauncher<String> agreementSaveLauncher;
    private ActivityResultLauncher<String> partnerNotificationPermission;
    private ActivityResultLauncher<String[]> partnerLocationPermission;
    private ActivityResultLauncher<String> beforePhotoPicker;
    private ActivityResultLauncher<String> afterPhotoPicker;
    private String pendingLocationBookingId;
    private String pendingCompletionBookingId, completionBeforeImage, completionAfterImage;
    private TextView beforePhotoStatus, afterPhotoStatus;
    private boolean partnerAssignmentReceiverRegistered;
    private final BroadcastReceiver partnerAssignmentReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            if ("partner".equals(role) && section != null && !isFinishing()) loadPartnerJobs();
        }
    };

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        agreementSaveLauncher = registerForActivityResult(new ActivityResultContracts.CreateDocument(), this::saveAgreementPdf);
        beforePhotoPicker = registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> handleCompletionPhoto(uri, true));
        afterPhotoPicker = registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> handleCompletionPhoto(uri, false));
        partnerNotificationPermission = registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
            if (granted) {
                registerPartnerDeviceToken();
                if (pendingLocationBookingId != null) requestPartnerLocationPermission(pendingLocationBookingId);
            } else {
                if (pendingLocationBookingId != null) toast("Allow notifications so Android can keep the location sharing indicator visible.");
                else toast("Turn on notifications to receive new job alerts when Sucharu is closed.");
                pendingLocationBookingId = null;
            }
        });
        partnerLocationPermission = registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), grants -> {
            boolean granted = Boolean.TRUE.equals(grants.get(Manifest.permission.ACCESS_FINE_LOCATION))
                    || Boolean.TRUE.equals(grants.get(Manifest.permission.ACCESS_COARSE_LOCATION))
                    || ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED
                    || ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED;
            if (granted && pendingLocationBookingId != null) startPartnerLocationService(pendingLocationBookingId);
            else { pendingLocationBookingId = null; toast("Location sharing stayed off. Allow location permission to share your trip with the customer."); }
        });
        role = getIntent().getStringExtra(EXTRA_ROLE);
        if (!"admin".equals(role) && !"partner".equals(role)) role = "admin";
        getWindow().setStatusBarColor(Color.rgb(244, 247, 237));
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
        showLogin();
    }

    private void basePage(String heading, String subtitle) {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(Color.rgb(244, 247, 237));
        page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(dp(20), dp(18), dp(20), dp(28));
        scroll.addView(page);
        TextView back = text("‹  Switch role", 14, GREEN, true);
        back.setPadding(0, dp(4), 0, dp(8));
        back.setOnClickListener(v -> finish());
        page.addView(back);
        page.addView(text("SUCHARU  ·  " + heading.toUpperCase(), 12, GREEN, true));
        TextView title = text(heading, 26, INK, true);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(-1, -2);
        titleParams.topMargin = dp(8);
        page.addView(title, titleParams);
        TextView hint = text(subtitle, 14, MUTED, false);
        LinearLayout.LayoutParams hintParams = new LinearLayout.LayoutParams(-1, -2);
        hintParams.topMargin = dp(5);
        page.addView(hint, hintParams);
        setContentView(scroll);
    }

    private void showLogin() {
        String heading = "admin".equals(role) ? "Admin sign in" : "Partner sign in";
        basePage(heading, "Use the credentials issued for your Sucharu " + role + " account.");
        MaterialCardView card = card();
        LinearLayout form = cardContent(card);
        if ("admin".equals(role)) {
            form.addView(label("Email address"));
            EditText email = input("Admin email", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
            form.addView(email);
            form.addView(label("Password"));
            EditText password = input("Password", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
            form.addView(password);
            form.addView(primaryButton("Sign in as Admin", v -> login(email.getText().toString().trim(), password.getText().toString())));
        } else {
            form.addView(label("Phone number"));
            EditText phone = input("Partner phone number", InputType.TYPE_CLASS_PHONE);
            form.addView(phone);
            form.addView(label("Password"));
            EditText password = input("Password", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
            form.addView(password);
            form.addView(primaryButton("Sign in as Partner", v -> login(phone.getText().toString().trim(), password.getText().toString())));
        }
        page.addView(card, marginTop(22));
        TextView note = text("This sign-in is verified by the Sucharu server. Customer, admin and partner sessions are kept separate.", 12, MUTED, false);
        note.setLineSpacing(dp(2), 1f);
        page.addView(note, marginTop(14));
    }

    private void login(String identifier, String password) {
        if (identifier.isEmpty() || password.isEmpty()) { toast("Enter your sign-in details."); return; }
        JSONObject body = new JSONObject();
        try {
            if ("admin".equals(role)) body.put("email", identifier);
            else body.put("phone", identifier);
            body.put("password", password);
        } catch (Exception ignored) { }
        String path = "admin".equals(role) ? "/auth/login" : "/auth/partner/login";
        ApiClient.post(path, body, null, (status, response, rows, error) -> {
            if (isFinishing()) return;
            if (error != null || response == null || status < 200 || status >= 300) {
                toast(error == null ? "Could not sign in. Please try again." : error);
                return;
            }
            token = response.optString("token", "");
            JSONObject profile = response.optJSONObject("admin");
            if (profile == null) profile = response.optJSONObject("partner");
            staffName = profile == null ? role : profile.optString("name", role);
            if (token.isEmpty()) { toast("The server did not return a secure session."); return; }
            if ("admin".equals(role)) showAdminPortal("overview"); else {
                showPartnerPortal();
                enablePartnerNotifications();
            }
        });
    }

    private void enablePartnerNotifications() {
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            partnerNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS);
        } else registerPartnerDeviceToken();
    }

    private void registerPartnerDeviceToken() {
        if (!"partner".equals(role) || token == null || token.isEmpty()) return;
        FirebaseMessaging.getInstance().getToken().addOnSuccessListener(deviceToken -> {
            if (deviceToken == null || deviceToken.isEmpty() || isFinishing()) return;
            try {
                JSONObject body = new JSONObject(); body.put("token", deviceToken); body.put("platform", "android");
                ApiClient.post("/partner/device-tokens", body, token, (code, result, rows, error) -> { });
            } catch (Exception ignored) { }
        });
    }

    private void removePartnerDeviceToken() {
        if (!"partner".equals(role) || token == null || token.isEmpty()) return;
        FirebaseMessaging.getInstance().getToken().addOnSuccessListener(deviceToken -> {
            if (deviceToken == null || deviceToken.isEmpty()) return;
            try {
                JSONObject body = new JSONObject(); body.put("token", deviceToken); body.put("action", "delete");
                ApiClient.post("/partner/device-tokens", body, token, (code, result, rows, error) -> { });
            } catch (Exception ignored) { }
        });
    }

    @Override protected void onStart() {
        super.onStart();
        registerPartnerAssignmentReceiver();
    }

    @Override protected void onStop() {
        unregisterPartnerAssignmentReceiver();
        super.onStop();
    }

    @Override protected void onDestroy() {
        unregisterPartnerAssignmentReceiver();
        super.onDestroy();
    }

    private void registerPartnerAssignmentReceiver() {
        if (!"partner".equals(role) || section == null || partnerAssignmentReceiverRegistered) return;
        IntentFilter filter = new IntentFilter(BookingMessagingService.ACTION_PARTNER_JOB_ASSIGNED);
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(partnerAssignmentReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        else registerReceiver(partnerAssignmentReceiver, filter);
        partnerAssignmentReceiverRegistered = true;
    }

    private void unregisterPartnerAssignmentReceiver() {
        if (!partnerAssignmentReceiverRegistered) return;
        try { unregisterReceiver(partnerAssignmentReceiver); } catch (IllegalArgumentException ignored) { }
        partnerAssignmentReceiverRegistered = false;
    }

    private void showAdminPortal(String tab) {
        basePage("Admin portal", "Signed in as " + staffName + ". Manage bookings and customer operations.");
        LinearLayout nav = new LinearLayout(this);
        nav.setOrientation(LinearLayout.HORIZONTAL);
        nav.setGravity(Gravity.CENTER_VERTICAL);
        String[] tabs = {"Overview", "Bookings", "Partners", "Services", "Support"};
        String[] keys = {"overview", "bookings", "partners", "services", "support"};
        for (int i = 0; i < tabs.length; i++) {
            final String key = keys[i];
            com.google.android.material.button.MaterialButton button = new com.google.android.material.button.MaterialButton(this);
            button.setText(tabs[i]); button.setAllCaps(false); button.setTextSize(11);
            button.setTextColor(key.equals(tab) ? Color.WHITE : INK);
            button.setBackgroundTintList(android.content.res.ColorStateList.valueOf(key.equals(tab) ? GREEN : Color.WHITE));
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-2, dp(42)); p.rightMargin = dp(5);
            nav.addView(button, p);
            button.setOnClickListener(v -> showAdminPortal(key));
        }
        ScrollView navScroll = new ScrollView(this);
        navScroll.setFillViewport(false);
        navScroll.setHorizontalScrollBarEnabled(false);
        navScroll.addView(nav);
        page.addView(navScroll, marginTop(16));
        section = new LinearLayout(this); section.setOrientation(LinearLayout.VERTICAL);
        page.addView(section, marginTop(12));
        addButton(page, "Sign out", v -> { token = null; finish(); }, true);
        if ("overview".equals(tab)) loadAdminOverview();
        else if ("bookings".equals(tab)) loadAdminBookings();
        else if ("partners".equals(tab)) loadAdminPartners();
        else if ("services".equals(tab)) loadAdminServices();
        else loadAdminSupport();
    }

    private void loadAdminOverview() {
        section.addView(text("Loading overview…", 14, MUTED, false));
        ApiClient.get("/dashboard/summary", token, (status, object, rows, error) -> {
            if (!ready(error)) return;
            section.removeAllViews();
            addInfoCard("Bookings", String.valueOf(object.optInt("bookings")));
            addInfoCard("Active partners", String.valueOf(object.optInt("activePartners")));
            addInfoCard("Active services", String.valueOf(object.optInt("activeServices")));
            addInfoCard("Completed service value · estimate", "৳" + object.optLong("completedServiceValue"));
            JSONArray counts = object.optJSONArray("statusCounts");
            if (counts != null) {
                MaterialCardView card = card(); LinearLayout content = cardContent(card);
                content.addView(label("Booking status"));
                for (int i = 0; i < counts.length(); i++) {
                    JSONObject row = counts.optJSONObject(i); if (row != null) content.addView(text(row.optString("_id").replace('_', ' ') + "  ·  " + row.optInt("count"), 14, INK, false));
                }
                section.addView(card, marginTop(10));
            }
        });
    }

    private void loadAdminBookings() {
        section.removeAllViews();
        section.addView(text("Loading bookings…", 14, MUTED, false));
        ApiClient.get("/bookings", token, (status, object, rows, error) -> {
            if (!ready(error)) return;
            if (section == null || isFinishing()) return;
            section.removeAllViews();
            if (rows == null || rows.length() == 0) { section.addView(text("No bookings yet. New customer requests will appear here.", 14, MUTED, false)); return; }
            loadAssignablePartners(partners -> {
                if (isFinishing() || section == null) return;
                section.removeAllViews();
                EditText search = input("Search booking, service, customer or partner", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
                Spinner filter = new Spinner(this);
                String[] filters = {"All bookings", "Needs a partner", "Request received", "Confirmed", "Assigned", "On the way", "Service started", "Awaiting customer", "Customer issue", "Completed", "Cancelled"};
                filter.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, filters));
                TextView count = text("", 12, MUTED, false);
                LinearLayout bookingCards = new LinearLayout(this); bookingCards.setOrientation(LinearLayout.VERTICAL);
                section.addView(label("Find and filter bookings")); section.addView(search); section.addView(filter, marginTop(6)); section.addView(count, marginTop(7)); section.addView(bookingCards);
                Runnable render = () -> renderAdminBookingCards(rows, partners, bookingCards, search.getText().toString(), filter.getSelectedItemPosition(), count);
                search.addTextChangedListener(new TextWatcher() {
                    @Override public void beforeTextChanged(CharSequence s, int start, int c, int a) { }
                    @Override public void onTextChanged(CharSequence s, int start, int before, int countChanged) { render.run(); }
                    @Override public void afterTextChanged(Editable s) { }
                });
                filter.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
                    @Override public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) { render.run(); }
                    @Override public void onNothingSelected(android.widget.AdapterView<?> parent) { }
                });
                render.run();
            });
        });
    }

    private void renderAdminBookingCards(JSONArray bookings, JSONArray partners, LinearLayout target, String query, int filter, TextView count) {
        if (target == null || isFinishing()) return;
        target.removeAllViews();
        String needle = query == null ? "" : query.trim().toLowerCase(java.util.Locale.ROOT);
        String[] statuses = {"", "", "booking_received", "confirmed", "assigned", "on_the_way", "service_started", "awaiting_customer_confirmation", "customer_issue_reported", "completed", "cancelled"};
        int shown = 0;
        for (int i = 0; i < bookings.length(); i++) {
            JSONObject booking = bookings.optJSONObject(i); if (booking == null) continue;
            String status = booking.optString("status"), expectedStatus = statuses[Math.max(0, Math.min(filter, statuses.length - 1))];
            JSONObject assignedPartner = booking.optJSONObject("partner");
            boolean needsPartner = assignedPartner == null || assignedPartner.optString("partnerId").isEmpty();
            if (filter == 1 && (!needsPartner || "cancelled".equals(status) || "completed".equals(status))) continue;
            if (filter > 1 && !expectedStatus.equals(status)) continue;
            JSONObject service = booking.optJSONObject("service"), customer = booking.optJSONObject("customer"), schedule = booking.optJSONObject("schedule");
            String haystack = (booking.optString("bookingId") + " " + (service == null ? "" : service.optString("name") + " " + service.optString("category"))
                    + " " + (customer == null ? "" : customer.optString("name") + " " + customer.optString("phone"))
                    + " " + (schedule == null ? "" : schedule.optString("date")) + " " + booking.optString("area")
                    + " " + (assignedPartner == null ? "" : assignedPartner.optString("name"))).toLowerCase(java.util.Locale.ROOT);
            if (!needle.isEmpty() && !haystack.contains(needle)) continue;
            addAdminBooking(booking, partners, bookings, target);
            shown++;
        }
        count.setText("Showing " + shown + " of " + bookings.length() + " bookings");
        if (shown == 0) target.addView(text("No bookings match this search or filter. Try another status or clear the search.", 14, MUTED, false), marginTop(12));
    }

    private interface RowsCallback { void complete(JSONArray rows); }
    private void loadAssignablePartners(RowsCallback callback) {
        ApiClient.get("/partners", token, (status, object, rows, error) -> callback.complete(rows == null ? new JSONArray() : rows));
    }

    private void addAdminBooking(JSONObject booking, JSONArray partners, JSONArray allBookings, LinearLayout bookingCards) {
        MaterialCardView card = card(); LinearLayout content = cardContent(card);
        JSONObject service = booking.optJSONObject("service"), customer = booking.optJSONObject("customer"), schedule = booking.optJSONObject("schedule");
        String bookingId = booking.optString("bookingId");
        content.addView(text((service == null ? "Service" : service.optString("name", "Service")) + "  ·  " + booking.optString("status").replace('_', ' '), 16, INK, true));
        content.addView(text(bookingId + "\n" + (customer == null ? "" : customer.optString("name") + " · " + customer.optString("phone"))
                + "\n" + (schedule == null ? "" : schedule.optString("date") + " · " + schedule.optString("time"))
                + "\n" + booking.optString("area") + ", " + booking.optString("address"), 13, MUTED, false), marginTop(7));

        Spinner partnerPicker = new Spinner(this); List<String> partnerIds = new ArrayList<>(), partnerLabels = new ArrayList<>();
        partnerIds.add(""); partnerLabels.add("Choose active partner with portal access");
        int selectedPartner = 0; JSONObject currentPartner = booking.optJSONObject("partner");
        int availableCount = 0;
        for (int i = 0; i < partners.length(); i++) {
            JSONObject partner = partners.optJSONObject(i); if (partner == null) continue;
            String id = partner.optString("_id");
            boolean currentSelection = currentPartner != null && id.equals(currentPartner.optString("partnerId"));
            boolean canBeAssigned = "active".equals(partner.optString("status")) && "verified".equals(partner.optString("verificationStatus")) && partner.optBoolean("portalEnabled");
            if (currentSelection) {
                partnerIds.add(id); partnerLabels.add(partner.optString("name") + " · " + partner.optString("partnerId") + " · currently assigned");
                selectedPartner = partnerIds.size() - 1; availableCount++;
                continue;
            }
            if (!canBeAssigned || !partnerMatchesBooking(partner, booking) || partnerHasTimeConflict(partner, booking, allBookings)) continue;
            availableCount++;
            partnerIds.add(id); partnerLabels.add(partner.optString("name") + " · " + partner.optString("partnerId") + " · available");
        }
        partnerPicker.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, partnerLabels)); partnerPicker.setSelection(selectedPartner);
        content.addView(label("Assign partner"), marginTop(9)); content.addView(partnerPicker);
        content.addView(text(availableCount == 0 ? "No active, verified partner matches this service and time." : availableCount + " active, verified partner option(s) available for this service and time.", 12, MUTED, false), marginTop(4));
        addButton(content, "Save assignment", v -> {
            JSONObject body = new JSONObject(); try { body.put("partnerId", partnerIds.get(partnerPicker.getSelectedItemPosition())); } catch (Exception ignored) { }
            ApiClient.patch("/bookings/" + encode(bookingId) + "/partner", body, token, (code, result, array, err) -> {
                if (!ready(err)) return;
                toast("Partner assigned. They will be notified and the customer booking status is updated.");
                loadAdminBookings();
            });
        }, false);

        Spinner statusPicker = new Spinner(this); statusPicker.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, BOOKING_STATUSES));
        String currentStatus = booking.optString("status"); for (int i = 0; i < BOOKING_STATUSES.length; i++) if (BOOKING_STATUSES[i].equals(currentStatus)) statusPicker.setSelection(i);
        content.addView(label("Booking status"), marginTop(7)); content.addView(statusPicker);
        addButton(content, "Update status", v -> {
            JSONObject body = new JSONObject(); try { body.put("status", BOOKING_STATUSES[statusPicker.getSelectedItemPosition()]); } catch (Exception ignored) { }
            ApiClient.patch("/bookings/" + encode(bookingId) + "/status", body, token, (code, result, array, err) -> { if (ready(err)) loadAdminBookings(); });
        }, false);
        if ("completed".equals(booking.optString("status"))) {
            JSONObject invoice = booking.optJSONObject("invoice");
            addButton(content, invoice == null ? "Create customer invoice" : "Edit customer invoice", v -> showAdminInvoiceDialog(booking), false);
            if (invoice != null) content.addView(text("Invoice total · ৳" + invoice.optLong("total") + " · " + booking.optString("paymentStatus", "unpaid"), 12, MUTED, false), marginTop(4));
        } else {
            content.addView(text("Customer invoice is available after the booking is completed.", 12, MUTED, false), marginTop(8));
        }
        bookingCards.addView(card, marginTop(10));
    }

    private void showAdminInvoiceDialog(JSONObject booking) {
        JSONObject current = booking.optJSONObject("invoice"), service = booking.optJSONObject("service");
        LinearLayout form = new LinearLayout(this); form.setOrientation(LinearLayout.VERTICAL);
        int pad = Ui.dp(this, 20); form.setPadding(pad, pad / 2, pad, 0);
        EditText serviceCharge = invoiceAmountField("Service charge", current == null ? (service == null ? 0 : service.optLong("estimatedPrice")) : current.optLong("serviceCharge"));
        EditText laborCharge = invoiceAmountField("Labor charge", current == null ? 0 : current.optLong("laborCharge"));
        EditText materialsCharge = invoiceAmountField("Materials charge", current == null ? 0 : current.optLong("materialsCharge"));
        EditText discount = invoiceAmountField("Discount", current == null ? 0 : current.optLong("discount"));
        EditText note = new EditText(this); note.setHint("Invoice note (optional)"); note.setText(current == null ? "" : current.optString("note"));
        note.setMinLines(2); note.setMaxLines(3); form.addView(note, marginTop(6));
        Spinner payment = new Spinner(this); String[] paymentOptions = {"unpaid", "paid"};
        payment.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, paymentOptions));
        if ("paid".equals(booking.optString("paymentStatus"))) payment.setSelection(1);
        form.addView(label("Payment status"), marginTop(8)); form.addView(payment);
        form.addView(serviceCharge, marginTop(5)); form.addView(laborCharge, marginTop(5)); form.addView(materialsCharge, marginTop(5)); form.addView(discount, marginTop(5));
        new MaterialAlertDialogBuilder(this).setTitle("Customer invoice · BDT")
                .setView(form).setNegativeButton("Cancel", null)
                .setPositiveButton("Save invoice", (dialog, which) -> {
                    JSONObject body = new JSONObject();
                    try {
                        body.put("serviceCharge", amountText(serviceCharge)); body.put("laborCharge", amountText(laborCharge));
                        body.put("materialsCharge", amountText(materialsCharge)); body.put("discount", amountText(discount));
                        body.put("note", note.getText().toString().trim()); body.put("paymentStatus", paymentOptions[payment.getSelectedItemPosition()]);
                    } catch (Exception ignored) { }
                    ApiClient.patch("/bookings/" + encode(booking.optString("bookingId")) + "/invoice", body, token, (code, result, rows, error) -> {
                        if (ready(error)) { toast("Invoice saved and customer notified."); loadAdminBookings(); }
                    });
                }).show();
    }

    private EditText invoiceAmountField(String hint, long value) {
        EditText field = new EditText(this); field.setHint(hint + " (৳)"); field.setInputType(InputType.TYPE_CLASS_NUMBER); field.setText(String.valueOf(Math.max(0, value))); return field;
    }

    private long amountText(EditText field) { try { return Long.parseLong(field.getText().toString().trim()); } catch (Exception ignored) { return 0; } }

    private boolean partnerMatchesBooking(JSONObject partner, JSONObject booking) {
        JSONObject service = booking.optJSONObject("service");
        JSONArray categories = partner.optJSONArray("category");
        if (service == null || categories == null) return false;
        boolean categoryMatch = false;
        for (int i = 0; i < categories.length(); i++) {
            String partnerCategory = normalizeCategory(categories.optString(i));
            if (partnerCategory.equals(normalizeCategory(service.optString("category"))) || partnerCategory.equals(normalizeCategory(service.optString("name")))) {
                categoryMatch = true; break;
            }
        }
        if (!categoryMatch) return false;
        JSONArray areas = partner.optJSONArray("area");
        if (areas == null || areas.length() == 0) return true;
        for (int i = 0; i < areas.length(); i++) if (normalizeArea(areas.optString(i)).equals(normalizeArea(booking.optString("area")))) return true;
        return false;
    }

    private boolean partnerHasTimeConflict(JSONObject partner, JSONObject booking, JSONArray allBookings) {
        JSONObject targetSchedule = booking.optJSONObject("schedule");
        String targetDate = targetSchedule == null ? "" : targetSchedule.optString("date"), targetTime = targetSchedule == null ? "" : targetSchedule.optString("time");
        for (int i = 0; i < allBookings.length(); i++) {
            JSONObject other = allBookings.optJSONObject(i); if (other == null || other.optString("bookingId").equals(booking.optString("bookingId"))) continue;
            String status = other.optString("status"); if ("completed".equals(status) || "cancelled".equals(status)) continue;
            JSONObject assigned = other.optJSONObject("partner"), schedule = other.optJSONObject("schedule");
            if (assigned != null && partner.optString("_id").equals(assigned.optString("partnerId")) && schedule != null
                    && targetDate.equals(schedule.optString("date")) && targetTime.equals(schedule.optString("time"))) return true;
        }
        return false;
    }

    private String normalizeCategory(String value) {
        String normalized = (value == null ? "" : value).trim().toLowerCase(java.util.Locale.ROOT).replaceAll("[._-]+", " ").replaceAll("\\s+", " ");
        if (normalized.contains("electric")) return "electrical";
        if (normalized.contains("plumb")) return "plumbing";
        if (normalized.equals("ac") || normalized.contains("air condition")) return "ac services";
        if (normalized.contains("ac service") || normalized.contains("ac servic")) return "ac services";
        if (normalized.contains("clean")) return "cleaning";
        if (normalized.equals("electrician")) return "electrical";
        if (normalized.equals("plumber")) return "plumbing";
        return normalized;
    }

    private String normalizeArea(String value) {
        String normalized = (value == null ? "" : value).trim().toLowerCase(java.util.Locale.ROOT).replaceAll("[._-]+", " ").replaceAll("\\s+", " ");
        if ("sodor".equals(normalized) || "sadar".equals(normalized) || "khulna sodor".equals(normalized)) return "khulna sadar";
        if ("khalispur".equals(normalized)) return "khalishpur";
        return normalized;
    }

    private void loadAdminPartners() {
        section.addView(text("Loading partners…", 14, MUTED, false));
        ApiClient.get("/partners", token, (status, object, rows, error) -> {
            if (!ready(error)) return; section.removeAllViews();
            section.addView(primaryButton("+ Add a partner", v -> showAddPartnerDialog()), marginTop(6));
            section.addView(primaryButton("Download offline partner agreement PDF", v -> agreementSaveLauncher.launch("Sucharu_Partner_Agreement_Form.pdf")), marginTop(6));
            if (rows == null || rows.length() == 0) { section.addView(text("No partners yet. Add a partner to start assigning work.", 14, MUTED, false), marginTop(12)); return; }
            for (int i = 0; i < rows.length(); i++) { JSONObject p = rows.optJSONObject(i); if (p == null) continue;
                addAdminPartner(p);
            }
        });
    }

    private void saveAgreementPdf(Uri destination) {
        if (destination == null) return;
        try (InputStream input = getAssets().open("Sucharu_Partner_Agreement_Form.pdf");
             OutputStream output = getContentResolver().openOutputStream(destination, "w")) {
            if (output == null) { toast("Could not open the selected folder."); return; }
            byte[] buffer = new byte[8192]; int count;
            while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
            output.flush();
            toast("Partner agreement PDF saved for offline use.");
        } catch (Exception error) {
            toast("Could not save the agreement PDF. Please choose another folder.");
        }
    }

    private void showAddPartnerDialog() {
        ApiClient.get("/services", token, (status, object, rows, error) -> {
            if (error != null && !ready(error)) return;
            LinkedHashSet<String> categories = new LinkedHashSet<>();
            if (rows != null) for (int i = 0; i < rows.length(); i++) {
                JSONObject service = rows.optJSONObject(i);
                if (service != null && service.optBoolean("active", true)) {
                    String category = service.optString("category", "").trim();
                    if (!category.isEmpty()) categories.add(category);
                }
            }
            if (categories.isEmpty()) {
                categories.add("Electrical"); categories.add("Plumbing"); categories.add("AC services"); categories.add("Cleaning");
            }
            buildAddPartnerDialog(new ArrayList<>(categories));
        });
    }

    private void buildAddPartnerDialog(List<String> categoryOptions) {
        LinearLayout form = new LinearLayout(this); form.setOrientation(LinearLayout.VERTICAL); form.setPadding(dp(8), dp(2), dp(8), dp(2));
        EditText name = input("Partner full name", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        EditText phone = input("Bangladesh phone number", InputType.TYPE_CLASS_PHONE);
        EditText fatherName = input("Father's name (optional)", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        EditText motherName = input("Mother's name (optional)", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        EditText permanentAddress = input("Permanent address (optional)", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        EditText occupation = input("Current occupation (optional)", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        EditText monthlyIncome = input("Monthly income in BDT (optional)", InputType.TYPE_CLASS_NUMBER);
        EditText nationality = input("Nationality (optional)", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        EditText facebook = input("Facebook profile/link (optional)", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        EditText whatsapp = input("WhatsApp number (optional)", InputType.TYPE_CLASS_PHONE);
        EditText email = input("Email (optional)", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
        EditText nidNumber = input("NID number (optional)", InputType.TYPE_CLASS_TEXT);
        EditText customCategories = input("Other categories (comma separated)", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        EditText customSkills = input("Other skills (comma separated)", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        EditText customAreas = input("Other areas (comma separated)", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        EditText customDays = input("Other available days (optional)", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        EditText customTimes = input("Other available times (optional)", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        List<String> selectedCategories = new ArrayList<>(), selectedAreas = new ArrayList<>(), selectedSkills = new ArrayList<>(), selectedDays = new ArrayList<>(), selectedTimes = new ArrayList<>();
        TextView selectedCategorySummary = text("No categories selected", 13, MUTED, false);
        TextView selectedAreaSummary = text("No areas selected · applies to all areas", 13, MUTED, false);
        TextView selectedSkillSummary = text("No skills selected", 13, MUTED, false);
        TextView selectedDaySummary = text("No availability days selected", 13, MUTED, false);
        TextView selectedTimeSummary = text("No availability times selected", 13, MUTED, false);
        form.addView(label("Name")); form.addView(name);
        form.addView(label("Phone")); form.addView(phone);
        form.addView(label("Available service categories"));
        form.addView(primaryButton("Choose categories", v -> choosePartnerOptions("Choose service categories", categoryOptions, selectedCategories, selectedCategorySummary, "No categories selected")));
        form.addView(selectedCategorySummary, marginTop(4));
        form.addView(label("Add a category manually (optional)")); form.addView(customCategories);

        form.addView(text("Additional partner information · all optional", 15, INK, true), marginTop(14));
        form.addView(label("Father's name")); form.addView(fatherName);
        form.addView(label("Mother's name")); form.addView(motherName);
        form.addView(label("Permanent address")); form.addView(permanentAddress);
        form.addView(label("Current occupation")); form.addView(occupation);
        form.addView(label("Monthly income (BDT)")); form.addView(monthlyIncome);
        form.addView(label("Nationality")); form.addView(nationality);
        form.addView(label("Facebook profile/link")); form.addView(facebook);
        form.addView(label("WhatsApp number")); form.addView(whatsapp);
        form.addView(label("Email")); form.addView(email);
        form.addView(label("NID number")); form.addView(nidNumber);

        List<String> skillOptions = java.util.Arrays.asList("AC service", "Water tank cleaning", "Cleaning", "Shifting", "Sofa/carpet cleaning", "Plumbing", "Painting", "Electrical", "Other");
        form.addView(label("Additional skills (optional)"));
        form.addView(primaryButton("Choose skills", v -> choosePartnerOptions("Choose additional skills", skillOptions, selectedSkills, selectedSkillSummary, "No skills selected")));
        form.addView(selectedSkillSummary, marginTop(4)); form.addView(customSkills);

        List<String> dayOptions = java.util.Arrays.asList("Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday");
        form.addView(label("Available days (optional)"));
        form.addView(primaryButton("Choose available days", v -> choosePartnerOptions("Choose available days", dayOptions, selectedDays, selectedDaySummary, "No availability days selected")));
        form.addView(selectedDaySummary, marginTop(4)); form.addView(customDays);

        List<String> timeOptions = java.util.Arrays.asList("Morning", "Noon", "Evening", "Night", "Any time");
        form.addView(label("Available times (optional)"));
        form.addView(primaryButton("Choose available times", v -> choosePartnerOptions("Choose available times", timeOptions, selectedTimes, selectedTimeSummary, "No availability times selected")));
        form.addView(selectedTimeSummary, marginTop(4)); form.addView(customTimes);

        String[] areaLabels = {"Sonadanga", "Khulna Sadar", "Khalishpur", "Daulatpur", "Other Khulna City Corporation area"};
        String[] areaValues = {"Sonadanga", "Khulna Sadar", "Khalishpur", "Daulatpur", "other-kcc"};
        form.addView(label("Available coverage areas"));
        form.addView(primaryButton("Choose areas", v -> choosePartnerOptions("Choose coverage areas", java.util.Arrays.asList(areaLabels), selectedAreas, selectedAreaSummary, "No areas selected · applies to all areas")));
        form.addView(selectedAreaSummary, marginTop(4));
        form.addView(label("Add an area manually (optional)")); form.addView(customAreas);
        form.addView(text("Select one or more options, or type additional categories/areas above. If no coverage area is selected, the partner can be assigned to any configured area.", 12, MUTED, false), marginTop(8));
        final androidx.appcompat.app.AlertDialog[] partnerDialog = new androidx.appcompat.app.AlertDialog[1];
        form.addView(primaryButton("Submit partner application", v -> {
            String partnerName = name.getText().toString().trim(), partnerPhone = phone.getText().toString().trim();
            JSONArray categoryList = combineValues(selectedCategories, customCategories.getText().toString());
            JSONArray areaList = combineValues(mapAreaLabels(selectedAreas, areaLabels, areaValues), customAreas.getText().toString());
            JSONArray skillList = combineValues(selectedSkills, customSkills.getText().toString());
            JSONArray dayList = combineValues(selectedDays, customDays.getText().toString());
            JSONArray timeList = combineValues(selectedTimes, customTimes.getText().toString());
            if (partnerName.isEmpty() || partnerPhone.isEmpty() || categoryList.length() == 0) { toast("Enter a name, phone number and at least one service category."); return; }
            JSONObject body = new JSONObject();
            try {
                body.put("name", partnerName); body.put("phone", partnerPhone); body.put("category", categoryList); body.put("area", areaList);
                body.put("fatherName", fatherName.getText().toString().trim()); body.put("motherName", motherName.getText().toString().trim());
                body.put("permanentAddress", permanentAddress.getText().toString().trim()); body.put("occupation", occupation.getText().toString().trim());
                body.put("monthlyIncome", monthlyIncome.getText().toString().trim()); body.put("nationality", nationality.getText().toString().trim());
                body.put("facebook", facebook.getText().toString().trim()); body.put("whatsapp", whatsapp.getText().toString().trim());
                body.put("email", email.getText().toString().trim()); body.put("nidNumber", nidNumber.getText().toString().trim());
                body.put("skills", skillList); body.put("availableDays", dayList); body.put("availableTimes", timeList);
            } catch (Exception ignored) { }
            ApiClient.post("/partners", body, token, (code, result, array, error) -> {
                if (!ready(error)) return;
                toast("Partner application submitted. Verify and activate the partner next.");
                if (partnerDialog[0] != null) partnerDialog[0].dismiss();
                loadAdminPartners();
            });
        }), marginTop(14));
        ScrollView formScroll = new ScrollView(this); formScroll.setFillViewport(false); formScroll.addView(form);
        partnerDialog[0] = new MaterialAlertDialogBuilder(this).setTitle("Add partner")
                .setMessage("The new partner starts pending and cannot sign in until Sucharu verifies and activates them.")
                .setView(formScroll).setNegativeButton("Cancel", null).create();
        partnerDialog[0].show();
    }

    private void choosePartnerOptions(String title, List<String> options, List<String> selected, TextView summary, String emptyText) {
        CharSequence[] labels = options.toArray(new CharSequence[0]);
        boolean[] checked = new boolean[options.size()];
        for (int i = 0; i < options.size(); i++) checked[i] = selected.contains(options.get(i));
        new MaterialAlertDialogBuilder(this).setTitle(title)
                .setMultiChoiceItems(labels, checked, (dialog, which, isChecked) -> {
                    String value = options.get(which);
                    if (isChecked && !selected.contains(value)) selected.add(value);
                    else if (!isChecked) selected.remove(value);
                })
                .setPositiveButton("Done", (dialog, which) -> summary.setText(selected.isEmpty() ? emptyText : android.text.TextUtils.join(", ", selected)))
                .setNegativeButton("Cancel", null).show();
    }

    private List<String> mapAreaLabels(List<String> selected, String[] labels, String[] values) {
        List<String> mapped = new ArrayList<>();
        for (String value : selected) for (int i = 0; i < labels.length; i++) if (labels[i].equals(value)) mapped.add(values[i]);
        return mapped;
    }

    private JSONArray combineValues(List<String> selected, String manual) {
        LinkedHashSet<String> values = new LinkedHashSet<>();
        for (String value : selected) if (value != null && !value.trim().isEmpty()) values.add(value.trim());
        for (String value : manual.split(",")) if (!value.trim().isEmpty()) values.add(value.trim());
        JSONArray result = new JSONArray();
        for (String value : values) result.put(value);
        return result;
    }

    private void addAdminPartner(JSONObject partner) {
        MaterialCardView card = card(); LinearLayout content = cardContent(card);
        String id = partner.optString("_id"), verification = partner.optString("verificationStatus"), status = partner.optString("status");
        JSONArray categories = partner.optJSONArray("category"), areas = partner.optJSONArray("area");
        content.addView(text(partner.optString("name") + " · " + partner.optString("partnerId"), 16, INK, true));
        content.addView(text(partner.optString("phone") + "\nVerification: " + verification.replace('_', ' ')
                + " · Status: " + status.replace('_', ' ')
                + "\nCategories: " + (categories == null ? "-" : categories.toString())
                + "\nAreas: " + (areas == null || areas.length() == 0 ? "All configured areas" : areas.toString())
                + "\nPortal access: " + (partner.optBoolean("portalEnabled") ? "enabled" : "not enabled"), 13, MUTED, false), marginTop(6));
        StringBuilder details = new StringBuilder();
        appendPartnerDetail(details, "Father", partner.optString("fatherName"));
        appendPartnerDetail(details, "Mother", partner.optString("motherName"));
        appendPartnerDetail(details, "Address", partner.optString("permanentAddress"));
        appendPartnerDetail(details, "Occupation", partner.optString("occupation"));
        if (partner.has("monthlyIncome") && !partner.isNull("monthlyIncome")) appendPartnerDetail(details, "Monthly income (BDT)", partner.optString("monthlyIncome"));
        appendPartnerDetail(details, "Nationality", partner.optString("nationality"));
        appendPartnerDetail(details, "Facebook", partner.optString("facebook"));
        appendPartnerDetail(details, "WhatsApp", partner.optString("whatsapp"));
        appendPartnerDetail(details, "Email", partner.optString("email"));
        appendPartnerDetail(details, "NID", partner.optString("nidNumber"));
        appendPartnerArrayDetail(details, "Additional skills", partner.optJSONArray("skills"));
        appendPartnerArrayDetail(details, "Available days", partner.optJSONArray("availableDays"));
        appendPartnerArrayDetail(details, "Available times", partner.optJSONArray("availableTimes"));
        if (details.length() > 0) content.addView(text("Application details\n" + details, 13, MUTED, false), marginTop(8));

        if (!"verified".equals(verification)) {
            addButton(content, "Verify partner", v -> {
                ApiClient.post("/partners/" + encode(id) + "/verify", new JSONObject(), token, (code, result, rows, error) -> {
                    if (!ready(error)) return;
                    toast("Partner verified.");
                    loadAdminPartners();
                });
            }, false);
        }
        if ("verified".equals(verification) && !"active".equals(status)) {
            addButton(content, "Activate partner", v -> {
                JSONObject body = new JSONObject(); try { body.put("verificationStatus", "verified"); body.put("status", "active"); } catch (Exception ignored) { }
                patchPartner(id, body, "Partner activated and can be assigned work.");
            }, false);
        }
        if ("verified".equals(verification) && "active".equals(status)) {
            addButton(content, partner.optBoolean("portalEnabled") ? "Reset partner password" : "Set partner login password", v -> showPartnerPasswordDialog(partner), false);
        }
        section.addView(card, marginTop(10));
    }

    private void appendPartnerDetail(StringBuilder details, String label, String value) {
        if (value != null && !value.trim().isEmpty()) {
            if (details.length() > 0) details.append('\n');
            details.append(label).append(": ").append(value.trim());
        }
    }

    private void appendPartnerArrayDetail(StringBuilder details, String label, JSONArray values) {
        if (values != null && values.length() > 0) appendPartnerDetail(details, label, values.toString());
    }

    private void patchPartner(String id, JSONObject body, String successMessage) {
        ApiClient.patch("/partners/" + encode(id), body, token, (code, result, rows, error) -> {
            if (!ready(error)) return;
            toast(successMessage);
            loadAdminPartners();
        });
    }

    private void showPartnerPasswordDialog(JSONObject partner) {
        EditText password = input("Initial password (at least 12 characters)", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
        new MaterialAlertDialogBuilder(this).setTitle("Enable partner sign in")
                .setMessage("Give this password to the partner directly after saving. It is stored as a hash and cannot be viewed again here.")
                .setView(password).setNegativeButton("Cancel", null)
                .setPositiveButton("Save and enable login", (dialog, which) -> {
                    String value = password.getText().toString();
                    if (value.length() < 12) { toast("Partner passwords must be at least 12 characters."); return; }
                    JSONObject body = new JSONObject(); try { body.put("password", value); } catch (Exception ignored) { }
                    ApiClient.patch("/partners/" + encode(partner.optString("_id")) + "/portal-access", body, token, (code, result, rows, error) -> {
                        if (!ready(error)) return;
                        toast("Partner login enabled. Share the password with them privately.");
                        loadAdminPartners();
                    });
                }).show();
    }

    private void loadAdminServices() {
        section.addView(text("Loading services…", 14, MUTED, false));
        ApiClient.get("/services?all=true", token, (status, object, rows, error) -> {
            if (!ready(error)) return; section.removeAllViews();
            if (rows == null || rows.length() == 0) { section.addView(text("No services found.", 14, MUTED, false)); return; }
            for (int i = 0; i < rows.length(); i++) { JSONObject s = rows.optJSONObject(i); if (s == null) continue;
                String price = s.isNull("startingPrice") ? "Quotation required" : "Starting estimate · ৳" + s.optString("startingPrice");
                addInfoCard(s.optString("icon") + "  " + s.optString("name"), s.optString("category") + " · " + price + "\n" + (s.optBoolean("active") ? "Active" : "Inactive"));
            }
        });
    }

    private void loadAdminSupport() {
        section.addView(text("Loading support requests…", 14, MUTED, false));
        ApiClient.get("/support/tickets", token, (status, object, rows, error) -> {
            if (!ready(error)) return; section.removeAllViews();
            if (rows == null || rows.length() == 0) { section.addView(text("No support requests.", 14, MUTED, false)); return; }
            for (int i = 0; i < rows.length(); i++) { JSONObject ticket = rows.optJSONObject(i); if (ticket != null) addSupportTicket(ticket); }
        });
    }

    private void addSupportTicket(JSONObject ticket) {
        MaterialCardView card = card(); LinearLayout content = cardContent(card);
        content.addView(text(ticket.optString("subject") + " · " + ticket.optString("status").replace('_', ' '), 16, INK, true));
        JSONObject customer = ticket.optJSONObject("customer");
        content.addView(text(ticket.optString("ticketId") + " · " + (customer == null ? "Customer" : customer.optString("name")) + "\n" + ticket.optString("message"), 13, MUTED, false), marginTop(6));
        EditText reply = input("Reply to customer (optional)", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE); reply.setMinLines(2); content.addView(reply, marginTop(8));
        String[] statuses = {"open", "in_progress", "resolved"}; Spinner status = new Spinner(this);
        status.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, statuses));
        for (int i = 0; i < statuses.length; i++) if (statuses[i].equals(ticket.optString("status"))) status.setSelection(i);
        content.addView(status);
        addButton(content, "Save reply and status", v -> {
            JSONObject body = new JSONObject(); try { body.put("reply", reply.getText().toString().trim()); body.put("status", statuses[status.getSelectedItemPosition()]); } catch (Exception ignored) { }
            ApiClient.patch("/support/tickets/" + encode(ticket.optString("_id")), body, token, (code, result, array, err) -> { if (ready(err)) loadAdminSupport(); });
        }, false);
        section.addView(card, marginTop(10));
    }

    private void showPartnerPortal() {
        basePage("Partner portal", "Signed in as " + staffName + ". View assigned jobs and update service progress.");
        section = new LinearLayout(this); section.setOrientation(LinearLayout.VERTICAL);
        page.addView(primaryButton("Refresh assigned jobs", v -> loadPartnerJobs()), marginTop(16));
        page.addView(section, marginTop(8));
        addButton(page, "Sign out", v -> { removePartnerDeviceToken(); token = null; finish(); }, true);
        loadPartnerJobs();
        registerPartnerAssignmentReceiver();
    }

    private void loadPartnerJobs() {
        if (section == null) return;
        section.removeAllViews(); section.addView(text("Loading assigned jobs…", 14, MUTED, false));
        ApiClient.get("/partner/jobs", token, (status, object, rows, error) -> {
            if (section == null || isFinishing()) return;
            section.removeAllViews();
            if (error != null) {
                section.addView(text("Could not load assigned jobs. Check your connection, then tap Refresh assigned jobs.", 14, MUTED, false));
                ready(error);
                return;
            }
            if (rows == null || rows.length() == 0) { section.addView(text("No assigned jobs for this partner account yet. New assignments appear here automatically. If Admin just assigned work, confirm it was assigned to this same partner account.", 14, MUTED, false)); return; }
            for (int i = 0; i < rows.length(); i++) { JSONObject job = rows.optJSONObject(i); if (job != null) addPartnerJob(job); }
        });
    }

    private void addPartnerJob(JSONObject job) {
        MaterialCardView card = card(); LinearLayout content = cardContent(card);
        JSONObject service = job.optJSONObject("service"), customer = job.optJSONObject("customer"), schedule = job.optJSONObject("schedule");
        String status = job.optString("status"), bookingId = job.optString("bookingId");
        content.addView(text((service == null ? "Service" : service.optString("name")) + "  ·  " + status.replace('_', ' '), 16, INK, true));
        content.addView(text(bookingId + "\n" + (schedule == null ? "" : schedule.optString("date") + " · " + schedule.optString("time"))
                + "\nCustomer: " + (customer == null ? "" : customer.optString("name") + " · " + customer.optString("phone"))
                + "\nAddress: " + job.optString("area") + ", " + job.optString("address")
                + (job.optString("note").isEmpty() ? "" : "\nNote: " + job.optString("note")), 13, MUTED, false), marginTop(6));
        String next = "assigned".equals(status) ? "on_the_way" : "on_the_way".equals(status) ? "service_started" : "service_started".equals(status) ? "awaiting_customer_confirmation" : "";
        if ("on_the_way".equals(status)) {
            addButton(content, "Verify customer code & start service", v -> showPartnerStartServiceDialog(bookingId), false);
        } else if ("service_started".equals(status)) {
            addButton(content, "Submit completion report", v -> showCompletionReportDialog(bookingId), false);
        } else if ("awaiting_customer_confirmation".equals(status)) {
            content.addView(text("Your completion report is with the customer. The booking closes after they confirm it.", 13, MUTED, false), marginTop(8));
        } else if ("customer_issue_reported".equals(status)) {
            content.addView(text("The customer asked Sucharu support to review a service concern. Follow up with the admin team.", 13, MUTED, false), marginTop(8));
        } else if (!next.isEmpty()) {
            String label = "Mark on the way";
            addButton(content, label, v -> {
            JSONObject body = new JSONObject(); try { body.put("status", next); } catch (Exception ignored) { }
            ApiClient.patch("/partner/jobs/" + encode(bookingId) + "/status", body, token, (code, response, array, err) -> {
                if (ready(err)) {
                    if ("on_the_way".equals(next)) requestPartnerLocationSharing(bookingId);
                    loadPartnerJobs();
                }
            });
            }, false);
        }
        if ("on_the_way".equals(status) && !isLocationSharing(bookingId)) {
            addButton(content, "Start location sharing", v -> requestPartnerLocationSharing(bookingId), false);
            content.addView(text("The customer sees your last location only while you are on the way. You can stop sharing from the phone notification.", 12, MUTED, false), marginTop(5));
        }
        section.addView(card, marginTop(10));
    }

    private void showPartnerStartServiceDialog(String bookingId) {
        EditText code = input("6-digit code from the customer", InputType.TYPE_CLASS_NUMBER);
        code.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(6)});
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(this).setTitle("Confirm service start")
                .setMessage("Ask the customer to open their Sucharu app and show the code. Enter it here to start the job.")
                .setView(code).setNegativeButton("Back", null).setPositiveButton("Verify & start", null);
        androidx.appcompat.app.AlertDialog dialog = builder.create();
        dialog.setOnShowListener(ignored -> dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String value = code.getText().toString().trim();
            if (!value.matches("\\d{6}")) { code.setError("Enter all 6 digits."); return; }
            try {
                JSONObject body = new JSONObject(); body.put("code", value);
                ApiClient.post("/partner/jobs/" + encode(bookingId) + "/start-service", body, token, (statusCode, response, rows, error) -> {
                    if (error != null || statusCode < 200 || statusCode >= 300) { toast(error == null ? "Could not verify the customer code." : error); return; }
                    dialog.dismiss(); stopPartnerLocationService(); toast("Customer code verified. Service started."); loadPartnerJobs();
                });
            } catch (Exception ignoredError) { toast("Could not prepare the service code."); }
        }));
        dialog.show();
    }

    private void showCompletionReportDialog(String bookingId) {
        pendingCompletionBookingId = bookingId; completionBeforeImage = null; completionAfterImage = null;
        CheckBox workCompleted = new CheckBox(this); workCompleted.setText("Work completed as agreed with the customer");
        CheckBox areaChecked = new CheckBox(this); areaChecked.setText("Service area checked and left safely");
        CheckBox followUpExplained = new CheckBox(this); followUpExplained.setText("Any follow-up or care advice explained");
        EditText notes = input("Completion note for the customer (optional)", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE); notes.setMinLines(2); notes.setMaxLines(4);
        com.google.android.material.button.MaterialButton beforePhoto = new com.google.android.material.button.MaterialButton(this); beforePhoto.setText("Choose before photo (optional)"); beforePhoto.setAllCaps(false);
        com.google.android.material.button.MaterialButton afterPhoto = new com.google.android.material.button.MaterialButton(this); afterPhoto.setText("Choose after photo (optional)"); afterPhoto.setAllCaps(false);
        beforePhotoStatus = text("No before photo selected", 12, MUTED, false);
        afterPhotoStatus = text("No after photo selected", 12, MUTED, false);
        beforePhoto.setOnClickListener(v -> beforePhotoPicker.launch("image/*")); afterPhoto.setOnClickListener(v -> afterPhotoPicker.launch("image/*"));
        LinearLayout form = new LinearLayout(this); form.setOrientation(LinearLayout.VERTICAL); form.setPadding(dp(18), 0, dp(18), 0);
        form.addView(text("Confirm the service steps. Photos are optional; avoid faces and private documents.", 13, MUTED, false));
        form.addView(workCompleted, marginTop(5)); form.addView(areaChecked, marginTop(2)); form.addView(followUpExplained, marginTop(2)); form.addView(notes, marginTop(6));
        form.addView(beforePhoto, marginTop(8)); form.addView(beforePhotoStatus, marginTop(2)); form.addView(afterPhoto, marginTop(8)); form.addView(afterPhotoStatus, marginTop(2));
        ScrollView scroll = new ScrollView(this); scroll.addView(form);
        androidx.appcompat.app.AlertDialog dialog = new MaterialAlertDialogBuilder(this).setTitle("Service completion report").setView(scroll).setNegativeButton("Keep working", null).setPositiveButton("Complete booking", null).create();
        dialog.setOnShowListener(ignored -> dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            if (!workCompleted.isChecked() || !areaChecked.isChecked() || !followUpExplained.isChecked()) { toast("Confirm all checklist items before completing the booking."); return; }
            if ((beforePhotoStatus != null && beforePhotoStatus.getText().toString().startsWith("Preparing")) || (afterPhotoStatus != null && afterPhotoStatus.getText().toString().startsWith("Preparing"))) { toast("Wait for the selected photo to finish compressing."); return; }
            JSONObject checklist = new JSONObject(), body = new JSONObject();
            try {
                checklist.put("workCompleted", true); checklist.put("areaChecked", true); checklist.put("followUpExplained", true);
                body.put("checklist", checklist); body.put("notes", notes.getText().toString().trim());
                body.put("beforeImage", completionBeforeImage == null ? JSONObject.NULL : completionBeforeImage);
                body.put("afterImage", completionAfterImage == null ? JSONObject.NULL : completionAfterImage);
            } catch (Exception ignoredError) { toast("Could not prepare the service report."); return; }
            ApiClient.post("/partner/jobs/" + encode(bookingId) + "/complete", body, token, (statusCode, response, rows, error) -> {
                if (error != null || statusCode < 200 || statusCode >= 300) { toast(error == null ? "Could not save the completion report." : error); return; }
                dialog.dismiss(); completionBeforeImage = null; completionAfterImage = null; pendingCompletionBookingId = null; stopPartnerLocationService();
                toast("Service report saved. Booking completed."); loadPartnerJobs();
            });
        }));
        dialog.setOnDismissListener(ignored -> { beforePhotoStatus = null; afterPhotoStatus = null; });
        dialog.show();
    }

    private void handleCompletionPhoto(Uri uri, boolean before) {
        if (uri == null || pendingCompletionBookingId == null) return;
        final String bookingId = pendingCompletionBookingId;
        TextView target = before ? beforePhotoStatus : afterPhotoStatus;
        if (target != null) target.setText("Preparing photo…");
        new Thread(() -> {
            String encoded = compressCompletionPhoto(uri);
            runOnUiThread(() -> {
                if (!bookingId.equals(pendingCompletionBookingId)) return;
                if (encoded == null) {
                    if (target != null) target.setText("Photo too large or unreadable. Choose another one.");
                    return;
                }
                if (before) completionBeforeImage = encoded; else completionAfterImage = encoded;
                if (target != null) target.setText("Photo ready · compressed for upload");
            });
        }, "sucharu-photo-compress").start();
    }

    private String compressCompletionPhoto(Uri uri) {
        try {
            BitmapFactory.Options bounds = new BitmapFactory.Options(); bounds.inJustDecodeBounds = true;
            try (InputStream input = getContentResolver().openInputStream(uri)) { BitmapFactory.decodeStream(input, null, bounds); }
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null;
            int sample = 1; while (bounds.outWidth / sample > 1280 || bounds.outHeight / sample > 1280) sample *= 2;
            BitmapFactory.Options options = new BitmapFactory.Options(); options.inSampleSize = sample;
            Bitmap bitmap;
            try (InputStream input = getContentResolver().openInputStream(uri)) { bitmap = BitmapFactory.decodeStream(input, null, options); }
            if (bitmap == null) return null;
            int max = Math.max(bitmap.getWidth(), bitmap.getHeight());
            if (max > 800) {
                float scale = 800f / max;
                Bitmap scaled = Bitmap.createScaledBitmap(bitmap, Math.round(bitmap.getWidth() * scale), Math.round(bitmap.getHeight() * scale), true);
                if (scaled != bitmap) bitmap.recycle(); bitmap = scaled;
            }
            for (int maxDimension = 800; maxDimension >= 250; maxDimension = Math.round(maxDimension * .75f)) {
                Bitmap working = bitmap;
                int workingMax = Math.max(bitmap.getWidth(), bitmap.getHeight());
                if (workingMax > maxDimension) {
                    float scale = (float) maxDimension / workingMax;
                    working = Bitmap.createScaledBitmap(bitmap, Math.round(bitmap.getWidth() * scale), Math.round(bitmap.getHeight() * scale), true);
                }
                for (int quality = 82; quality >= 40; quality -= 7) {
                    ByteArrayOutputStream output = new ByteArrayOutputStream(); working.compress(Bitmap.CompressFormat.JPEG, quality, output);
                    if (output.size() <= 150 * 1024) {
                        String result = android.util.Base64.encodeToString(output.toByteArray(), android.util.Base64.NO_WRAP);
                        if (working != bitmap) working.recycle(); bitmap.recycle(); return result;
                    }
                }
                if (working != bitmap) working.recycle();
            }
            bitmap.recycle(); return null;
        } catch (Exception ignored) { return null; }
    }

    private boolean isLocationSharing(String bookingId) {
        return bookingId.equals(getSharedPreferences("partner_location", MODE_PRIVATE).getString("active_booking", ""));
    }

    private void requestPartnerLocationSharing(String bookingId) {
        if (token == null || token.isEmpty()) { toast("Sign in to the partner portal before sharing location."); return; }
        pendingLocationBookingId = bookingId;
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            partnerNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS);
            return;
        }
        requestPartnerLocationPermission(bookingId);
    }

    private void requestPartnerLocationPermission(String bookingId) {
        boolean fine = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED;
        boolean coarse = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED;
        if (!fine && !coarse) {
            partnerLocationPermission.launch(new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION});
            return;
        }
        startPartnerLocationService(bookingId);
    }

    private void startPartnerLocationService(String bookingId) {
        if (token == null || token.isEmpty()) { toast("Partner session expired. Sign in again to share location."); return; }
        pendingLocationBookingId = null;
        Intent intent = new Intent(this, PartnerLocationService.class);
        intent.putExtra(PartnerLocationService.EXTRA_BOOKING_ID, bookingId);
        intent.putExtra(PartnerLocationService.EXTRA_TOKEN, token);
        try {
            if (Build.VERSION.SDK_INT >= 26) ContextCompat.startForegroundService(this, intent);
            else startService(intent);
            toast("Location sharing started for this trip. A phone notification is now visible.");
        } catch (SecurityException error) {
            toast("Could not start location sharing. Check location and notification permissions.");
        }
    }

    private void stopPartnerLocationService() {
        Intent intent = new Intent(this, PartnerLocationService.class).setAction(PartnerLocationService.ACTION_STOP);
        startService(intent);
    }

    private void addInfoCard(String heading, String details) {
        MaterialCardView card = card(); LinearLayout content = cardContent(card);
        content.addView(text(heading, 16, INK, true)); content.addView(text(details == null ? "" : details, 13, MUTED, false), marginTop(5));
        section.addView(card, marginTop(8));
    }

    private boolean ready(String error) {
        if (isFinishing()) return false;
        if (error != null) {
            if (error.toLowerCase().contains("session") || error.toLowerCase().contains("sign in")) { token = null; showLogin(); }
            else toast(error);
            return false;
        }
        return true;
    }

    private MaterialCardView card() { MaterialCardView card = new MaterialCardView(this); card.setRadius(dp(16)); card.setCardElevation(dp(1)); card.setStrokeWidth(dp(1)); card.setStrokeColor(Color.rgb(223, 231, 218)); card.setCardBackgroundColor(Color.WHITE); return card; }
    private LinearLayout cardContent(MaterialCardView card) { LinearLayout content = new LinearLayout(this); content.setOrientation(LinearLayout.VERTICAL); content.setPadding(dp(15), dp(14), dp(15), dp(14)); card.addView(content); return content; }
    private TextView label(String value) { TextView t = text(value, 12, MUTED, true); t.setPadding(0, dp(8), 0, dp(3)); return t; }
    private EditText input(String hint, int inputType) { EditText edit = new EditText(this); edit.setSingleLine(inputType != (InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE)); edit.setHint(hint); edit.setInputType(inputType); edit.setTextColor(INK); edit.setHintTextColor(MUTED); edit.setPadding(dp(12), dp(8), dp(12), dp(8)); edit.setBackgroundResource(R.drawable.bg_input); return edit; }
    private View primaryButton(String title, View.OnClickListener listener) { com.google.android.material.button.MaterialButton b = new com.google.android.material.button.MaterialButton(this); b.setText(title); b.setAllCaps(false); b.setTextColor(Color.WHITE); b.setBackgroundTintList(android.content.res.ColorStateList.valueOf(GREEN)); b.setOnClickListener(listener); return b; }
    private void addButton(LinearLayout parent, String title, View.OnClickListener listener, boolean outline) { View b = primaryButton(title, listener); if (outline) { ((com.google.android.material.button.MaterialButton)b).setTextColor(INK); b.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.rgb(234, 242, 230))); } parent.addView(b, marginTop(8)); }
    private TextView text(String value, float size, int color, boolean bold) { TextView t = new TextView(this); t.setText(value); t.setTextSize(size); t.setTextColor(color); t.setLineSpacing(dp(2), 1f); if (bold) t.setTypeface(t.getTypeface(), android.graphics.Typeface.BOLD); return t; }
    private LinearLayout.LayoutParams marginTop(int value) { LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2); p.topMargin = dp(value); return p; }
    private int dp(float value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    private String encode(String value) { try { return URLEncoder.encode(value, "UTF-8"); } catch (Exception ignored) { return value; } }
    private void toast(String message) { Toast.makeText(this, message, Toast.LENGTH_LONG).show(); }
}
