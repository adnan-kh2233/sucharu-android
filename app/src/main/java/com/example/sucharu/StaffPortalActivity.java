package com.example.sucharu;

import android.graphics.Color;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.card.MaterialCardView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.json.JSONArray;
import org.json.JSONObject;

import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;

public class StaffPortalActivity extends AppCompatActivity {
    public static final String EXTRA_ROLE = "staff_role";
    private static final int INK = Color.rgb(23, 60, 52), GREEN = Color.rgb(24, 116, 94), MUTED = Color.rgb(100, 118, 110);
    private static final String[] BOOKING_STATUSES = {"booking_received", "confirmed", "assigned", "on_the_way", "service_started", "completed", "cancelled"};
    private String role, token, staffName;
    private LinearLayout page, section;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
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
            if ("admin".equals(role)) showAdminPortal("overview"); else showPartnerPortal();
        });
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
        section.addView(text("Loading bookings…", 14, MUTED, false));
        ApiClient.get("/bookings", token, (status, object, rows, error) -> {
            if (!ready(error)) return;
            section.removeAllViews();
            if (rows == null || rows.length() == 0) { section.addView(text("No bookings yet.", 14, MUTED, false)); return; }
            loadAssignablePartners(partners -> {
                if (!isFinishing()) for (int i = 0; i < rows.length(); i++) {
                    JSONObject booking = rows.optJSONObject(i); if (booking != null) addAdminBooking(booking, partners);
                }
            });
        });
    }

    private interface RowsCallback { void complete(JSONArray rows); }
    private void loadAssignablePartners(RowsCallback callback) {
        ApiClient.get("/partners", token, (status, object, rows, error) -> callback.complete(rows == null ? new JSONArray() : rows));
    }

    private void addAdminBooking(JSONObject booking, JSONArray partners) {
        MaterialCardView card = card(); LinearLayout content = cardContent(card);
        JSONObject service = booking.optJSONObject("service"), customer = booking.optJSONObject("customer"), schedule = booking.optJSONObject("schedule");
        String bookingId = booking.optString("bookingId");
        content.addView(text((service == null ? "Service" : service.optString("name", "Service")) + "  ·  " + booking.optString("status").replace('_', ' '), 16, INK, true));
        content.addView(text(bookingId + "\n" + (customer == null ? "" : customer.optString("name") + " · " + customer.optString("phone"))
                + "\n" + (schedule == null ? "" : schedule.optString("date") + " · " + schedule.optString("time"))
                + "\n" + booking.optString("area") + ", " + booking.optString("address"), 13, MUTED, false), marginTop(7));

        Spinner partnerPicker = new Spinner(this); List<String> partnerIds = new ArrayList<>(), partnerLabels = new ArrayList<>();
        partnerIds.add(""); partnerLabels.add("Choose active, verified partner");
        int selectedPartner = 0; JSONObject currentPartner = booking.optJSONObject("partner");
        for (int i = 0; i < partners.length(); i++) {
            JSONObject partner = partners.optJSONObject(i); if (partner == null || !"active".equals(partner.optString("status")) || !"verified".equals(partner.optString("verificationStatus"))) continue;
            String id = partner.optString("_id"); partnerIds.add(id); partnerLabels.add(partner.optString("name") + " · " + partner.optString("partnerId"));
            if (currentPartner != null && id.equals(currentPartner.optString("partnerId"))) selectedPartner = partnerIds.size() - 1;
        }
        partnerPicker.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, partnerLabels)); partnerPicker.setSelection(selectedPartner);
        content.addView(label("Assign partner"), marginTop(9)); content.addView(partnerPicker);
        addButton(content, "Save assignment", v -> {
            JSONObject body = new JSONObject(); try { body.put("partnerId", partnerIds.get(partnerPicker.getSelectedItemPosition())); } catch (Exception ignored) { }
            ApiClient.patch("/bookings/" + encode(bookingId) + "/partner", body, token, (code, result, array, err) -> { if (ready(err)) loadAdminBookings(); });
        }, false);

        Spinner statusPicker = new Spinner(this); statusPicker.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, BOOKING_STATUSES));
        String currentStatus = booking.optString("status"); for (int i = 0; i < BOOKING_STATUSES.length; i++) if (BOOKING_STATUSES[i].equals(currentStatus)) statusPicker.setSelection(i);
        content.addView(label("Booking status"), marginTop(7)); content.addView(statusPicker);
        addButton(content, "Update status", v -> {
            JSONObject body = new JSONObject(); try { body.put("status", BOOKING_STATUSES[statusPicker.getSelectedItemPosition()]); } catch (Exception ignored) { }
            ApiClient.patch("/bookings/" + encode(bookingId) + "/status", body, token, (code, result, array, err) -> { if (ready(err)) loadAdminBookings(); });
        }, false);
        section.addView(card, marginTop(10));
    }

    private void loadAdminPartners() {
        section.addView(text("Loading partners…", 14, MUTED, false));
        ApiClient.get("/partners", token, (status, object, rows, error) -> {
            if (!ready(error)) return; section.removeAllViews();
            if (rows == null || rows.length() == 0) { section.addView(text("No partners yet.", 14, MUTED, false)); return; }
            for (int i = 0; i < rows.length(); i++) { JSONObject p = rows.optJSONObject(i); if (p == null) continue;
                addInfoCard(p.optString("name") + " · " + p.optString("partnerId"), p.optString("phone") + "\n" + p.optString("verificationStatus") + " · " + p.optString("status") + "\n" + p.optJSONArray("category"));
            }
        });
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
        addButton(page, "Sign out", v -> { token = null; finish(); }, true);
        loadPartnerJobs();
    }

    private void loadPartnerJobs() {
        if (section == null) return;
        section.removeAllViews(); section.addView(text("Loading assigned jobs…", 14, MUTED, false));
        ApiClient.get("/partner/jobs", token, (status, object, rows, error) -> {
            if (!ready(error)) return; section.removeAllViews();
            if (rows == null || rows.length() == 0) { section.addView(text("No active assigned bookings right now.", 14, MUTED, false)); return; }
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
        String next = "assigned".equals(status) ? "on_the_way" : "on_the_way".equals(status) ? "service_started" : "service_started".equals(status) ? "completed" : "";
        String label = "on_the_way".equals(next) ? "Mark on the way" : "service_started".equals(next) ? "Start service" : "completed".equals(next) ? "Mark completed" : "Completed";
        if (!next.isEmpty()) addButton(content, label, v -> {
            JSONObject body = new JSONObject(); try { body.put("status", next); } catch (Exception ignored) { }
            ApiClient.patch("/partner/jobs/" + encode(bookingId) + "/status", body, token, (code, response, array, err) -> { if (ready(err)) loadPartnerJobs(); });
        }, false);
        section.addView(card, marginTop(10));
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
