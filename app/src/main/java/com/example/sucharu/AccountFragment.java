package com.example.sucharu;

import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.card.MaterialCardView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Locale;

public class AccountFragment extends Fragment {
    private static final String[] ADDRESS_AREAS = {"Sonadanga", "Khulna Sadar", "Khalishpur", "Daulatpur", "Other Khulna City Corporation area"};
    private static final String[] ADDRESS_AREA_VALUES = {"Sonadanga", "Khulna Sadar", "Khalishpur", "Daulatpur", "other-kcc"};
    private boolean registering = false;
    private boolean resetting = false;
    private View formCard, profileCard, registerFields, resetFields, resetCodeFields, modeTabs, customerProfileEditor;
    private View savedAddressEditor;
    private EditText identifier, loginPassword, name, phone, email, registerPassword, resetEmail, resetCode, resetNewPassword, resetConfirmPassword;
    private EditText savedAddressText, savedAddressLandmark, profileEditName, profileEditPhone, profileEditEmail, profileCurrentPassword, profileNewPassword, profileConfirmPassword;
    private Spinner savedAddressArea;
    private Button submit, forgotPassword, resetSendCode, resetComplete;
    private TextView message, profileName, profileContact, profileAddressSummary, profileAddressPinStatus, profileAddressMessage, profileEditMessage, intro, title;
    private Double savedLatitude, savedLongitude;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private boolean resetCodeRequested = false;
    private Runnable resendCountdown;
    private int resendSeconds;

    @Nullable @Override public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_account, container, false);
    }

    @Override public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        formCard = view.findViewById(R.id.account_form_card);
        profileCard = view.findViewById(R.id.account_profile_card);
        customerProfileEditor = view.findViewById(R.id.customer_profile_editor);
        profileEditName = view.findViewById(R.id.profile_edit_name);
        profileEditPhone = view.findViewById(R.id.profile_edit_phone);
        profileEditEmail = view.findViewById(R.id.profile_edit_email);
        profileCurrentPassword = view.findViewById(R.id.profile_current_password);
        profileNewPassword = view.findViewById(R.id.profile_new_password);
        profileConfirmPassword = view.findViewById(R.id.profile_confirm_password);
        profileEditMessage = view.findViewById(R.id.profile_edit_message);
        registerFields = view.findViewById(R.id.register_fields);
        resetFields = view.findViewById(R.id.reset_fields);
        resetCodeFields = view.findViewById(R.id.reset_code_fields);
        modeTabs = view.findViewById(R.id.account_mode_tabs);
        identifier = view.findViewById(R.id.login_identifier);
        loginPassword = view.findViewById(R.id.login_password);
        name = view.findViewById(R.id.register_name);
        phone = view.findViewById(R.id.register_phone);
        email = view.findViewById(R.id.register_email);
        registerPassword = view.findViewById(R.id.register_password);
        resetEmail = view.findViewById(R.id.reset_email);
        resetCode = view.findViewById(R.id.reset_code);
        resetNewPassword = view.findViewById(R.id.reset_new_password);
        resetConfirmPassword = view.findViewById(R.id.reset_confirm_password);
        forgotPassword = view.findViewById(R.id.forgot_password);
        resetSendCode = view.findViewById(R.id.reset_send_code);
        resetComplete = view.findViewById(R.id.reset_complete);
        submit = view.findViewById(R.id.submit_account);
        message = view.findViewById(R.id.account_message);
        profileName = view.findViewById(R.id.profile_name);
        profileContact = view.findViewById(R.id.profile_contact);
        profileAddressSummary = view.findViewById(R.id.profile_address_summary);
        profileAddressPinStatus = view.findViewById(R.id.profile_address_pin_status);
        profileAddressMessage = view.findViewById(R.id.profile_address_message);
        savedAddressEditor = view.findViewById(R.id.saved_address_editor);
        savedAddressText = view.findViewById(R.id.profile_address_text);
        savedAddressLandmark = view.findViewById(R.id.profile_address_landmark);
        savedAddressArea = view.findViewById(R.id.profile_address_area);
        savedAddressArea.setAdapter(new ArrayAdapter<>(requireContext(), android.R.layout.simple_spinner_dropdown_item, ADDRESS_AREAS));
        intro = view.findViewById(R.id.account_intro);
        title = view.findViewById(R.id.account_title);
        addPublicContactCard(view);

        view.findViewById(R.id.show_login).setOnClickListener(v -> setMode(false));
        view.findViewById(R.id.show_register).setOnClickListener(v -> setMode(true));
        forgotPassword.setOnClickListener(v -> setResetMode());
        resetSendCode.setOnClickListener(v -> sendResetCode());
        resetComplete.setOnClickListener(v -> completePasswordReset());
        view.findViewById(R.id.reset_back_to_login).setOnClickListener(v -> setMode(false));
        submit.setOnClickListener(v -> submit());
        view.findViewById(R.id.sign_out).setOnClickListener(v -> {
            CustomerSession.withToken(requireContext(), (authToken, error) -> CustomerSession.removeNotificationToken(requireContext(), authToken));
            CustomerSession.signOut(requireContext());
            startActivity(new android.content.Intent(requireContext(), RoleChooserActivity.class));
            requireActivity().finish();
        });
        view.findViewById(R.id.view_my_bookings).setOnClickListener(v -> ((MainActivity) requireActivity()).showTab(R.id.nav_bookings));
        view.findViewById(R.id.enable_booking_notifications).setOnClickListener(v -> ((MainActivity) requireActivity()).enableBookingNotifications());
        view.findViewById(R.id.contact_support).setOnClickListener(v -> showSupportDialog());
        view.findViewById(R.id.edit_saved_address).setOnClickListener(v -> savedAddressEditor.setVisibility(savedAddressEditor.getVisibility() == View.VISIBLE ? View.GONE : View.VISIBLE));
        view.findViewById(R.id.edit_customer_profile).setOnClickListener(v -> {
            if (customerProfileEditor.getVisibility() == View.VISIBLE) customerProfileEditor.setVisibility(View.GONE);
            else { profileEditName.setText(CustomerSession.getName(requireContext())); profileEditPhone.setText(CustomerSession.getPhone(requireContext())); profileEditEmail.setText(CustomerSession.getEmail(requireContext())); profileEditMessage.setText(""); customerProfileEditor.setVisibility(View.VISIBLE); }
        });
        view.findViewById(R.id.save_customer_profile).setOnClickListener(v -> saveCustomerProfile());
        view.findViewById(R.id.pick_address_pin).setOnClickListener(v -> MapPinPicker.show(requireActivity(), savedLatitude, savedLongitude, (latitude, longitude) -> {
            savedLatitude = latitude; savedLongitude = longitude;
            updatePinStatus();
        }));
        view.findViewById(R.id.save_saved_address).setOnClickListener(v -> saveSavedAddress());
        view.findViewById(R.id.remove_saved_address).setOnClickListener(v -> removeSavedAddress());

        if (CustomerSession.isSignedIn(requireContext())) loadProfile();
        else renderSignedOut();
    }

    private void addPublicContactCard(View root) {
        if (!(root instanceof ViewGroup) || ((ViewGroup) root).getChildCount() == 0) return;
        View firstChild = ((ViewGroup) root).getChildAt(0);
        if (!(firstChild instanceof LinearLayout)) return;
        LinearLayout page = (LinearLayout) firstChild;
        MaterialCardView card = new MaterialCardView(requireContext());
        card.setCardBackgroundColor(Color.WHITE); card.setRadius(Ui.dp(requireContext(), 18)); card.setCardElevation(0);
        card.setStrokeColor(Color.rgb(226, 233, 225)); card.setStrokeWidth(Ui.dp(requireContext(), 1));
        LinearLayout content = new LinearLayout(requireContext()); content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(Ui.dp(requireContext(), 16), Ui.dp(requireContext(), 14), Ui.dp(requireContext(), 16), Ui.dp(requireContext(), 10));
        TextView heading = Ui.text(requireContext(), "Contact Sucharu", 18, Ui.INK, true); content.addView(heading);
        content.addView(Ui.text(requireContext(), "Tap a contact option to open the matching app.", 12, Ui.MUTED, false), topMargin(3));
        addContactRow(content, "➤", "Telegram", "@adnkhrad", "https://t.me/adnkhrad", Color.rgb(35, 145, 210));
        addContactRow(content, "◉", "WhatsApp", "01303-434078", "https://wa.me/8801303434078", Color.rgb(31, 145, 92));
        addContactRow(content, "☎", "Call / Offline", "01303-434078", "tel:+8801303434078", Ui.GREEN);
        addContactRow(content, "☏", "Telephone", "+880 9678-736286", "tel:+8809678736286", Ui.GREEN);
        addContactRow(content, "✉", "Email", "adnankhandoker03@gmail.com", "mailto:adnankhandoker03@gmail.com", Ui.GREEN);
        addContactRow(content, "⌖", "Office · Open in Maps", "78/6 KDA R/A, 3rd phase, Sonadanga, Khulna", "https://www.google.com/maps/search/?api=1&query=78%2F6%20KDA%20R%2FA%2C%203rd%20phase%2C%20Sonadanga%2C%20Khulna", Ui.GREEN);
        card.addView(content);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.topMargin = Ui.dp(requireContext(), 16); page.addView(card, params);
    }

    private void addContactRow(LinearLayout parent, String iconText, String titleText, String value, String destination, int iconColor) {
        LinearLayout row = new LinearLayout(requireContext()); row.setOrientation(LinearLayout.HORIZONTAL); row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(Ui.dp(requireContext(), 4), Ui.dp(requireContext(), 8), Ui.dp(requireContext(), 4), Ui.dp(requireContext(), 8));
        row.setClickable(true); row.setFocusable(true); row.setContentDescription(titleText + ", " + value);
        TextView icon = Ui.text(requireContext(), iconText, 20, iconColor, true); icon.setGravity(Gravity.CENTER);
        icon.setBackground(Ui.pill(Color.rgb(241, 247, 242), Ui.dp(requireContext(), 22)));
        row.addView(icon, new LinearLayout.LayoutParams(Ui.dp(requireContext(), 42), Ui.dp(requireContext(), 42)));
        LinearLayout labels = new LinearLayout(requireContext()); labels.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams labelsParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1); labelsParams.leftMargin = Ui.dp(requireContext(), 12);
        labels.addView(Ui.text(requireContext(), titleText, 13, Ui.INK, true));
        TextView valueText = Ui.text(requireContext(), value, 12, Ui.MUTED, false); labels.addView(valueText, topMargin(1)); row.addView(labels, labelsParams);
        row.setOnClickListener(v -> {
            try { startActivity(new android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(destination))); }
            catch (Exception error) { Toast.makeText(requireContext(), "No app is available to open this contact link.", Toast.LENGTH_LONG).show(); }
        });
        parent.addView(row, topMargin(3));
        View divider = new View(requireContext()); divider.setBackgroundColor(Color.rgb(238, 242, 238));
        LinearLayout.LayoutParams dividerParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(requireContext(), 1));
        parent.addView(divider, dividerParams);
    }

    private LinearLayout.LayoutParams topMargin(int marginDp) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.topMargin = Ui.dp(requireContext(), marginDp); return params;
    }

    private void setMode(boolean register) {
        registering = register;
        resetting = false;
        cancelResendCountdown();
        resetFields.setVisibility(View.GONE);
        resetCodeFields.setVisibility(View.GONE);
        modeTabs.setVisibility(View.VISIBLE);
        forgotPassword.setVisibility(register ? View.GONE : View.VISIBLE);
        submit.setVisibility(View.VISIBLE);
        registerFields.setVisibility(register ? View.VISIBLE : View.GONE);
        identifier.setVisibility(register ? View.GONE : View.VISIBLE);
        loginPassword.setVisibility(register ? View.GONE : View.VISIBLE);
        submit.setText(register ? "Create account" : "Sign in");
        message.setText(register ? "Use a Bangladesh phone number and a valid email. Password must be at least 6 characters." : "Sign in with your phone number or registered email.");
        message.setTextColor(Color.rgb(96, 113, 108));
    }

    private void setResetMode() {
        registering = false;
        resetting = true;
        resetCodeRequested = false;
        cancelResendCountdown();
        forgotPassword.setVisibility(View.GONE);
        registerFields.setVisibility(View.GONE);
        identifier.setVisibility(View.GONE);
        loginPassword.setVisibility(View.GONE);
        modeTabs.setVisibility(View.GONE);
        submit.setVisibility(View.GONE);
        resetFields.setVisibility(View.VISIBLE);
        resetCodeFields.setVisibility(View.GONE);
        resetEmail.setEnabled(true);
        resetEmail.setText(""); resetCode.setText(""); resetNewPassword.setText(""); resetConfirmPassword.setText("");
        resetSendCode.setEnabled(true); resetSendCode.setText("Send verification code");
        resetCode.setEnabled(true);
        title.setText("Reset your password");
        intro.setText("Use your registered email to receive a one-time reset code.");
        message.setText("A 6-digit code will be sent to your registered email.");
        message.setTextColor(Color.rgb(96, 113, 108));
    }

    private void sendResetCode() {
        String emailAddress = resetEmail.getText().toString().trim().toLowerCase(Locale.US);
        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(emailAddress).matches()) { setError("Enter a valid registered email address."); return; }
        resetEmail.setEnabled(false);
        resetSendCode.setEnabled(false);
        resetSendCode.setText("Sending code…");
        message.setText("Requesting a reset code by email…");
        message.setTextColor(Color.rgb(96, 113, 108));
        JSONObject body = new JSONObject();
        try { body.put("email", emailAddress); }
        catch (Exception exception) { setError("Could not prepare the reset request."); return; }
        ApiClient.post("/auth/customer/password/reset/request", body, null, (status, response, array, error) -> {
            if (!isAdded() || !resetting) return;
            if (error != null || response == null || status < 200 || status >= 300) {
                resetEmail.setEnabled(true); resetSendCode.setEnabled(true); resetSendCode.setText(resetCodeRequested ? "Resend code" : "Send verification code");
                setError(error == null ? "Could not send the reset code. Try again later." : error);
                return;
            }
            resetCodeRequested = true;
            resetCodeFields.setVisibility(View.VISIBLE);
            resetCode.setEnabled(true);
            message.setText("If an account uses that email, a 6-digit reset code has been sent.");
            message.setTextColor(Color.rgb(35, 120, 79));
            startResendCountdown();
        });
    }

    private void startResendCountdown() {
        cancelResendCountdown();
        resendSeconds = 60;
        resetSendCode.setEnabled(false);
        resendCountdown = new Runnable() {
            @Override public void run() {
                if (!isAdded()) return;
                if (resendSeconds <= 0) { resetSendCode.setEnabled(true); resetSendCode.setText("Resend code"); resendCountdown = null; return; }
                resetSendCode.setText("Resend in " + resendSeconds + "s");
                resendSeconds--;
                mainHandler.postDelayed(this, 1000L);
            }
        };
        mainHandler.post(resendCountdown);
    }

    private void cancelResendCountdown() {
        if (resendCountdown != null) mainHandler.removeCallbacks(resendCountdown);
        resendCountdown = null;
    }

    private void completePasswordReset() {
        String emailAddress = resetEmail.getText().toString().trim().toLowerCase(Locale.US);
        String code = resetCode.getText().toString().trim();
        String password = resetNewPassword.getText().toString();
        if (!resetCodeRequested) { sendResetCode(); return; }
        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(emailAddress).matches()) { setError("Enter a valid registered email address."); return; }
        if (!code.matches("\\d{6}")) { setError("Enter the 6-digit email code."); return; }
        if (password.length() < 6) { setError("New password must be at least 6 characters."); return; }
        if (!password.equals(resetConfirmPassword.getText().toString())) { setError("The passwords do not match."); return; }
        resetComplete.setEnabled(false);
        resetComplete.setText("Verifying and updating…");
        message.setText("Verifying the email code…");
        message.setTextColor(Color.rgb(96, 113, 108));
        JSONObject body = new JSONObject();
        try { body.put("email", emailAddress); body.put("code", code); body.put("password", password); }
        catch (Exception exception) { finishResetWithError("Could not prepare the password reset request."); return; }
        ApiClient.post("/auth/customer/password/reset/complete", body, null, (status, response, array, error) -> {
            if (!isAdded()) return;
            if (error != null || response == null || status < 200 || status >= 300) {
                finishResetWithError(error == null ? "Could not reset the password. Check the code and try again." : error);
                return;
            }
            cancelResendCountdown();
            resetCodeRequested = false;
            resetCode.setEnabled(true); resetComplete.setEnabled(true); resetComplete.setText("Verify code and reset password");
            setMode(false);
            identifier.setText(emailAddress);
            message.setText(response.optString("message", "Password updated. Sign in with your new password."));
            message.setTextColor(Color.rgb(35, 120, 79));
        });
    }

    private void finishResetWithError(String text) {
        resetComplete.setEnabled(true); resetComplete.setText("Verify code and reset password");
        setError(text);
    }

    private void setError(String text) { message.setText(text); message.setTextColor(Color.rgb(179, 38, 30)); }

    @Override public void onDestroyView() {
        cancelResendCountdown();
        super.onDestroyView();
    }

    private void renderSignedOut() {
        formCard.setVisibility(View.VISIBLE);
        profileCard.setVisibility(View.GONE);
        title.setText("Your Sucharu account");
        intro.setText("Sign in to request a service and see your bookings.");
        setMode(false);
        message.setText("");
    }

    private void renderProfile(String customerName, String customerPhone, String customerEmail) { renderProfile(customerName, customerPhone, customerEmail, null); }

    private void renderProfile(String customerName, String customerPhone, String customerEmail, JSONObject defaultAddress) {
        formCard.setVisibility(View.GONE);
        profileCard.setVisibility(View.VISIBLE);
        title.setText("Welcome to Sucharu");
        intro.setText("Your profile is ready for service requests.");
        profileName.setText(customerName == null || customerName.isEmpty() ? "Customer" : customerName);
        String contact = customerPhone == null ? "" : customerPhone;
        if (customerEmail != null && !customerEmail.isEmpty()) contact += (contact.isEmpty() ? "" : " · ") + customerEmail;
        profileContact.setText(contact);
        profileEditName.setText(profileName.getText());
        profileEditPhone.setText(customerPhone == null ? "" : customerPhone);
        profileEditEmail.setText(customerEmail == null ? "" : customerEmail);
        if (defaultAddress != null) {
            String area = defaultAddress.optString("area", "");
            String address = defaultAddress.optString("address", "");
            String landmark = defaultAddress.optString("landmark", "");
            String summary = address + (area.isEmpty() ? "" : "\n" + area) + (landmark.isEmpty() ? "" : "\nNear " + landmark);
            profileAddressSummary.setText(summary);
            savedAddressText.setText(address);
            savedAddressLandmark.setText(landmark);
            int selected = 0;
            for (int i = 0; i < ADDRESS_AREA_VALUES.length; i++) if (ADDRESS_AREA_VALUES[i].equals(area)) selected = i;
            savedAddressArea.setSelection(selected);
            if (defaultAddress.isNull("latitude") || defaultAddress.isNull("longitude")) {
                savedLatitude = null; savedLongitude = null;
            } else {
                savedLatitude = defaultAddress.optDouble("latitude"); savedLongitude = defaultAddress.optDouble("longitude");
            }
        } else {
            profileAddressSummary.setText("No saved service address yet.");
            savedAddressText.setText(""); savedAddressLandmark.setText("");
            savedAddressArea.setSelection(0); savedLatitude = null; savedLongitude = null;
        }
        updatePinStatus();
    }

    private void loadProfile() {
        renderProfile(CustomerSession.getName(requireContext()), CustomerSession.getPhone(requireContext()), CustomerSession.getEmail(requireContext()));
        CustomerSession.withToken(requireContext(), (token, tokenError) -> {
            if (!isAdded() || tokenError != null) return;
            ApiClient.get("/customers/me", token, (status, object, array, error) -> {
                if (!isAdded() || error != null || object == null) return;
                JSONObject customer = object.optJSONObject("customer");
                if (customer != null) {
                    CustomerSession.saveDefaultAddress(requireContext(), customer.optJSONObject("defaultAddress"));
                    renderProfile(customer.optString("name"), customer.optString("phone"), customer.optString("email"), customer.optJSONObject("defaultAddress"));
                    loadSupportTickets();
                }
            });
        });
    }

    private void loadSupportTickets() {
        CustomerSession.withToken(requireContext(), (token, tokenError) -> {
            if (tokenError != null || !isAdded()) return;
            ApiClient.get("/customers/support", token, (status, object, tickets, error) -> {
                if (!isAdded() || error != null) return;
                TextView summary = requireView().findViewById(R.id.support_ticket_summary);
                if (tickets == null || tickets.length() == 0) { summary.setText("Ask the Sucharu team for help or check your recent requests."); return; }
                StringBuilder text = new StringBuilder("Recent requests\n");
                for (int i = 0; i < Math.min(tickets.length(), 3); i++) {
                    JSONObject ticket = tickets.optJSONObject(i); if (ticket == null) continue;
                    text.append(ticket.optString("ticketId")).append(" · ").append(ticket.optString("status").replace('_', ' '));
                    if (!ticket.optString("lastReply").isEmpty()) text.append("\n").append(ticket.optString("lastReply"));
                    text.append("\n");
                }
                summary.setText(text.toString().trim());
            });
        });
    }

    private void showSupportDialog() {
        LinearLayout form = new LinearLayout(requireContext()); form.setOrientation(LinearLayout.VERTICAL); form.setPadding(Ui.dp(requireContext(), 20), 0, Ui.dp(requireContext(), 20), 0);
        Spinner category = new Spinner(requireContext()); String[] labels = {"General", "Booking", "Payment", "Partner", "Other"}, values = {"general", "booking", "payment", "partner", "other"};
        category.setAdapter(new ArrayAdapter<>(requireContext(), android.R.layout.simple_spinner_dropdown_item, labels));
        EditText subject = new EditText(requireContext()); subject.setHint("Subject"); subject.setSingleLine(true);
        EditText bookingId = new EditText(requireContext()); bookingId.setHint("Booking ID (optional)"); bookingId.setSingleLine(true);
        EditText message = new EditText(requireContext()); message.setHint("How can we help? (10–2000 characters)"); message.setMinLines(3); message.setMaxLines(5); message.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE); message.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(2000)});
        form.addView(category); form.addView(subject); form.addView(bookingId); form.addView(message);
        new MaterialAlertDialogBuilder(requireContext()).setTitle("Contact Sucharu support").setView(form).setNegativeButton("Cancel", null)
                .setPositiveButton("Send request", (dialog, which) -> {
                    try {
                        JSONObject body = new JSONObject(); body.put("category", values[category.getSelectedItemPosition()]); body.put("subject", subject.getText().toString().trim()); body.put("bookingId", bookingId.getText().toString().trim()); body.put("message", message.getText().toString().trim());
                        CustomerSession.withToken(requireContext(), (token, tokenError) -> {
                            if (tokenError != null) { Toast.makeText(requireContext(), tokenError, Toast.LENGTH_LONG).show(); return; }
                            ApiClient.post("/customers/support", body, token, (code, result, rows, error) -> {
                                if (!isAdded()) return;
                                Toast.makeText(requireContext(), error == null && code < 300 ? "Support request " + result.optString("ticketId") + " sent." : error == null ? result.optString("message", "Could not send support request.") : error, Toast.LENGTH_LONG).show();
                                if (error == null && code >= 200 && code < 300) loadSupportTickets();
                            });
                        });
                    } catch (Exception ignored) { }
                }).show();
    }

    private void updatePinStatus() {
        if (profileAddressPinStatus == null) return;
        profileAddressPinStatus.setText(savedLatitude == null || savedLongitude == null
                ? "Pin optional — coordinates help the service team find your entrance."
                : String.format(Locale.US, "Pin selected: %.6f, %.6f", savedLatitude, savedLongitude));
    }

    private void saveSavedAddress() {
        String address = savedAddressText.getText().toString().trim();
        String landmark = savedAddressLandmark.getText().toString().trim();
        if (address.isEmpty()) { savedAddressText.setError("Enter your full address."); return; }
        JSONObject addressBody = new JSONObject();
        JSONObject body = new JSONObject();
        try {
            addressBody.put("label", "Home"); addressBody.put("area", ADDRESS_AREA_VALUES[savedAddressArea.getSelectedItemPosition()]);
            addressBody.put("address", address); addressBody.put("landmark", landmark);
            if (savedLatitude != null && savedLongitude != null) { addressBody.put("latitude", savedLatitude); addressBody.put("longitude", savedLongitude); }
            body.put("defaultAddress", addressBody);
        } catch (Exception exception) { setError("Could not prepare your address."); return; }
        View save = requireView().findViewById(R.id.save_saved_address); save.setEnabled(false);
        setAddressMessage("Saving address…", false);
        CustomerSession.withToken(requireContext(), (token, tokenError) -> {
            if (!isAdded()) return;
            if (tokenError != null) { save.setEnabled(true); setAddressMessage(tokenError, true); return; }
            ApiClient.patch("/customers/me", body, token, (status, response, array, error) -> {
                if (!isAdded()) return;
                save.setEnabled(true);
                JSONObject customer = response == null ? null : response.optJSONObject("customer");
                if (error != null || status < 200 || status >= 300 || customer == null) { setAddressMessage(error == null ? "Could not save your address. Please try again." : error, true); return; }
                CustomerSession.saveDefaultAddress(requireContext(), customer.optJSONObject("defaultAddress"));
                renderProfile(customer.optString("name"), customer.optString("phone"), customer.optString("email"), customer.optJSONObject("defaultAddress"));
                savedAddressEditor.setVisibility(View.GONE);
                setAddressMessage("Address saved to your account.", false);
            });
        });
    }

    private void setAddressMessage(String text, boolean isError) {
        if (profileAddressMessage == null) return;
        profileAddressMessage.setText(text);
        profileAddressMessage.setTextColor(isError ? Color.rgb(179, 38, 30) : Color.rgb(35, 120, 79));
    }

    private void saveCustomerProfile() {
        String updatedName = profileEditName.getText().toString().trim();
        String updatedPhone = profileEditPhone.getText().toString().trim();
        String updatedEmail = profileEditEmail.getText().toString().trim();
        String currentPassword = profileCurrentPassword.getText().toString();
        String newPassword = profileNewPassword.getText().toString();
        String confirmPassword = profileConfirmPassword.getText().toString();
        boolean identityChanged = !updatedPhone.equals(CustomerSession.getPhone(requireContext()))
                || !updatedEmail.equalsIgnoreCase(CustomerSession.getEmail(requireContext()));
        if (updatedName.isEmpty()) { profileEditName.setError("Enter your name."); return; }
        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(updatedEmail).matches()) { profileEditEmail.setError("Enter a valid email address."); return; }
        if ((identityChanged || !newPassword.isEmpty()) && currentPassword.isEmpty()) { profileCurrentPassword.setError("Enter your current password to change phone, email or password."); return; }
        if (newPassword.length() > 0 && newPassword.length() < 6) { profileNewPassword.setError("Use at least 6 characters."); return; }
        if (!newPassword.equals(confirmPassword)) { profileConfirmPassword.setError("Passwords do not match."); return; }
        JSONObject body = new JSONObject();
        try {
            body.put("name", updatedName); body.put("phone", updatedPhone); body.put("email", updatedEmail);
            if (identityChanged || !newPassword.isEmpty()) body.put("currentPassword", currentPassword);
            if (!newPassword.isEmpty()) body.put("newPassword", newPassword);
        } catch (Exception exception) { setProfileMessage("Could not prepare the profile update.", true); return; }
        View save = requireView().findViewById(R.id.save_customer_profile); save.setEnabled(false);
        setProfileMessage("Saving account details…", false);
        CustomerSession.withToken(requireContext(), (token, tokenError) -> {
            if (!isAdded()) return;
            if (tokenError != null) { save.setEnabled(true); setProfileMessage(tokenError, true); return; }
            ApiClient.patch("/customers/me", body, token, (status, response, array, error) -> {
                if (!isAdded()) return;
                save.setEnabled(true);
                JSONObject customer = response == null ? null : response.optJSONObject("customer");
                if (error != null || status < 200 || status >= 300 || customer == null) { setProfileMessage(error == null ? "Could not save account details." : error, true); return; }
                CustomerSession.saveCustomerProfile(requireContext(), customer);
                CustomerSession.saveDefaultAddress(requireContext(), customer.optJSONObject("defaultAddress"));
                profileCurrentPassword.setText(""); profileNewPassword.setText(""); profileConfirmPassword.setText("");
                renderProfile(customer.optString("name"), customer.optString("phone"), customer.optString("email"), customer.optJSONObject("defaultAddress"));
                customerProfileEditor.setVisibility(View.GONE);
                setProfileMessage("Account details saved.", false);
            });
        });
    }

    private void setProfileMessage(String text, boolean isError) {
        if (profileEditMessage == null) return;
        profileEditMessage.setText(text);
        profileEditMessage.setTextColor(isError ? Color.rgb(179, 38, 30) : Color.rgb(35, 120, 79));
    }

    private void removeSavedAddress() {
        JSONObject body = new JSONObject();
        try { body.put("defaultAddress", JSONObject.NULL); } catch (Exception ignored) {}
        CustomerSession.withToken(requireContext(), (token, tokenError) -> {
            if (!isAdded()) return;
            if (tokenError != null) { setAddressMessage(tokenError, true); return; }
            ApiClient.patch("/customers/me", body, token, (status, response, array, error) -> {
                if (!isAdded()) return;
                JSONObject customer = response == null ? null : response.optJSONObject("customer");
                if (error != null || status < 200 || status >= 300 || customer == null) { setAddressMessage(error == null ? "Could not remove the saved address." : error, true); return; }
                CustomerSession.saveDefaultAddress(requireContext(), null);
                renderProfile(customer.optString("name"), customer.optString("phone"), customer.optString("email"), null);
                savedAddressEditor.setVisibility(View.GONE);
                setAddressMessage("Saved address removed.", false);
            });
        });
    }

    private void submit() {
        JSONObject body = new JSONObject();
        String path;
        try {
            if (registering) {
                String customerName = name.getText().toString().trim();
                String customerPhone = phone.getText().toString().trim();
                String customerEmail = email.getText().toString().trim();
                String password = registerPassword.getText().toString();
                if (customerName.isEmpty() || customerPhone.isEmpty() || !android.util.Patterns.EMAIL_ADDRESS.matcher(customerEmail).matches() || password.length() < 6) {
                    message.setText("Enter your name, phone number, and a valid email. Password must be at least 6 characters.");
                    message.setTextColor(Color.rgb(179, 38, 30));
                    return;
                }
                body.put("name", customerName);
                body.put("phone", customerPhone);
                body.put("email", customerEmail);
                body.put("password", password);
                path = "/auth/customer/register";
            } else {
                String login = identifier.getText().toString().trim();
                String password = loginPassword.getText().toString();
                if (login.isEmpty() || password.isEmpty()) {
                    message.setText("Enter your phone or email and password.");
                    message.setTextColor(Color.rgb(179, 38, 30));
                    return;
                }
                body.put("identifier", login);
                body.put("password", password);
                path = "/auth/customer/login";
            }
        } catch (Exception exception) {
            message.setText("Could not prepare your account request.");
            return;
        }
        submit.setEnabled(false);
        submit.setText(registering ? "Creating account…" : "Signing in…");
        message.setText("Connecting securely to Sucharu…");
        message.setTextColor(Color.rgb(96, 113, 108));
        ApiClient.post(path, body, null, (status, response, array, error) -> {
            if (!isAdded()) return;
            if (error != null || response == null || status < 200 || status >= 300) {
                String userMessage = status == 409 && registering
                        ? "An account already uses this phone or email. Select Sign in above."
                        : error == null ? (registering ? "Could not create the account. Please try again." : "Could not sign in. Please try again.") : error;
                message.setText(userMessage);
                message.setTextColor(Color.rgb(179, 38, 30));
                submit.setEnabled(true);
                submit.setText(registering ? "Create account" : "Sign in");
                return;
            }
            CustomerSession.saveAuthResponse(requireContext(), response, saveError -> {
                if (!isAdded()) return;
                if (saveError != null) {
                    message.setText(saveError);
                    message.setTextColor(Color.rgb(179, 38, 30));
                    submit.setEnabled(true);
                    submit.setText(registering ? "Create account" : "Sign in");
                    return;
                }
                JSONObject profile = response.optJSONObject("customer");
                if (profile != null) loadProfile();
                ((MainActivity) requireActivity()).onCustomerSessionChanged();
            });
        });
    }
}

