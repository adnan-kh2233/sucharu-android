package com.example.sucharu;

import android.graphics.Color;
import android.net.Uri;
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
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;

import com.google.android.material.card.MaterialCardView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.json.JSONArray;
import org.json.JSONObject;

import java.net.URLEncoder;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

public class StaffPortalActivity extends AppCompatActivity {
    public static final String EXTRA_ROLE = "staff_role";
    private static final int INK = Color.rgb(23, 60, 52), GREEN = Color.rgb(24, 116, 94), MUTED = Color.rgb(100, 118, 110);
    private static final String[] BOOKING_STATUSES = {"booking_received", "confirmed", "assigned", "on_the_way", "service_started", "completed", "cancelled"};
    private String role, token, staffName;
    private LinearLayout page, section;
    private ActivityResultLauncher<String> agreementSaveLauncher;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        agreementSaveLauncher = registerForActivityResult(new ActivityResultContracts.CreateDocument(), this::saveAgreementPdf);
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
        ScrollView formScroll = new ScrollView(this); formScroll.setFillViewport(false); formScroll.addView(form);
        new MaterialAlertDialogBuilder(this).setTitle("Add partner")
                .setMessage("The new partner starts pending and cannot sign in until Sucharu verifies and activates them.")
                .setView(formScroll).setNegativeButton("Cancel", null)
                .setPositiveButton("Create partner", (dialog, which) -> {
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
                    }
                    catch (Exception ignored) { }
                    ApiClient.post("/partners", body, token, (code, result, array, error) -> {
                        if (!ready(error)) return;
                        toast("Partner created. Verify and activate them, then set their portal password.");
                        loadAdminPartners();
                    });
                }).show();
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
                JSONObject body = new JSONObject(); try { body.put("verificationStatus", "verified"); body.put("status", status); } catch (Exception ignored) { }
                patchPartner(id, body, "Partner verified.");
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
