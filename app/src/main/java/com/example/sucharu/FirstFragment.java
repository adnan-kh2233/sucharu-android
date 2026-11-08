package com.example.sucharu;

import android.app.DatePickerDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;

import com.example.sucharu.databinding.FragmentFirstBinding;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

public class FirstFragment extends Fragment {
    private static final String API_BASE = BuildConfig.API_BASE_URL;
    private static final String PREFS = "sucharu_customer";
    private FragmentFirstBinding binding;
    private FirebaseAuth firebaseAuth;
    private final FirebaseAuth.IdTokenListener idTokenListener = auth -> {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null || !isAdded()) return;
        user.getIdToken(false).addOnSuccessListener(result -> {
            if (!isAdded() || result.getToken() == null) return;
            requireContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString("token", result.getToken()).apply();
            View current = getView();
            if (current != null) updateAccountButton(current);
        });
    };

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        binding = FragmentFirstBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        firebaseAuth = FirebaseAuth.getInstance();
        firebaseAuth.addIdTokenListener(idTokenListener);
        view.findViewById(R.id.btn_account).setOnClickListener(v -> showAuthChoices());
        bindBookingButton(view, R.id.book_ac, "AC Servicing");
        bindBookingButton(view, R.id.book_cleaning, "Home Cleaning");
        bindBookingButton(view, R.id.book_electrical, "Electrical Repair");
        bindBookingButton(view, R.id.book_plumbing, "Plumbing Repair");
        updateAccountButton(view);
    }

    private void bindBookingButton(View view, int id, String service) {
        view.findViewById(id).setOnClickListener(v -> {
            if (getToken() == null) showAuthChoices();
            else showBookingForm(service);
        });
    }

    private void showAuthChoices() {
        if (!isAdded()) return;
        new AlertDialog.Builder(requireContext())
                .setTitle("Customer account")
                .setItems(new String[]{"Sign in", "Create account"}, (dialog, which) -> showAuthForm(which == 1))
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showAuthForm(boolean register) {
        Context context = requireContext();
        LinearLayout form = formContainer(context);
        EditText name = register ? field(context, "Full name", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS) : null;
        EditText identifier = field(context, register ? "Phone number" : "Phone or email", InputType.TYPE_CLASS_TEXT);
        EditText email = register ? field(context, "Email (optional)", InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS) : null;
        EditText password = field(context, "Password (minimum 6 characters)", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        if (name != null) form.addView(name);
        form.addView(identifier);
        if (email != null) form.addView(email);
        form.addView(password);
        AlertDialog dialog = new AlertDialog.Builder(context)
                .setTitle(register ? "Create Sucharu account" : "Sign in to Sucharu")
                .setView(form)
                .setNegativeButton("Cancel", null)
                .setPositiveButton(register ? "Create account" : "Sign in", null)
                .create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String identity = identifier.getText().toString().trim();
            String pass = password.getText().toString();
            if (identity.isEmpty() || pass.isEmpty() || (register && name.getText().toString().trim().isEmpty())) {
                toast("Fill in the required fields first.");
                return;
            }
            JSONObject body = new JSONObject();
            try {
                if (register) {
                    body.put("name", name.getText().toString().trim());
                    body.put("phone", identity);
                    body.put("email", email.getText().toString().trim());
                    body.put("password", pass);
                } else {
                    body.put("identifier", identity);
                    body.put("password", pass);
                }
            } catch (Exception e) { toast("Could not prepare sign-in details."); return; }
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false);
            post(register ? "/auth/customer/register" : "/auth/customer/login", body, null, (status, response) -> {
                try {
                    String sessionToken = response.optString("token", "");
                    String firebaseCustomToken = response.optString("firebaseCustomToken", "");
                    if (status >= 200 && status < 300 && !firebaseCustomToken.isEmpty()) {
                        firebaseAuth.signInWithCustomToken(firebaseCustomToken).addOnCompleteListener(signInTask -> {
                            if (!isAdded()) return;
                            FirebaseUser user = signInTask.isSuccessful() ? signInTask.getResult().getUser() : null;
                            if (user == null) {
                                toast("Firebase sign-in failed. Please try again.");
                                if (dialog.isShowing()) dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(true);
                                return;
                            }
                            user.getIdToken(true).addOnCompleteListener(tokenTask -> {
                                if (!isAdded()) return;
                                String idToken = tokenTask.isSuccessful() && tokenTask.getResult() != null ? tokenTask.getResult().getToken() : null;
                                if (idToken == null) {
                                    toast("Could not finish the Firebase session. Please try again.");
                                    if (dialog.isShowing()) dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(true);
                                    return;
                                }
                                JSONObject profile = response.optJSONObject("customer");
                                String customerName = profile == null ? "Customer" : profile.optString("name", "Customer");
                                requireContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString("token", idToken).putString("name", customerName).apply();
                                dialog.dismiss();
                                View current = getView();
                                if (current != null) updateAccountButton(current);
                                toast("Signed in successfully. Now choose a service to book.");
                            });
                        });
                    } else if (status >= 200 && status < 300 && !sessionToken.isEmpty()) {
                        JSONObject profile = response.optJSONObject("customer");
                        String customerName = profile == null ? "Customer" : profile.optString("name", "Customer");
                        requireContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString("token", sessionToken).putString("name", customerName).apply();
                        dialog.dismiss();
                        View current = getView();
                        if (current != null) updateAccountButton(current);
                        toast("Signed in successfully. Now choose a service to book.");
                    } else if (status >= 200 && status < 300) {
                        toast("The server did not return a customer session.");
                        if (dialog.isShowing()) dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(true);
                    } else {
                        toast(response.optString("message", "Sign-in failed. Check your details."));
                        if (dialog.isShowing()) dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(true);
                    }
                } catch (Exception e) {
                    toast("Could not read the server response.");
                    if (dialog.isShowing()) dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(true);
                }
            });
        }));
        dialog.show();
    }

    private void showBookingForm(String serviceName) {
        Context context = requireContext();
        LinearLayout form = formContainer(context);
        EditText date = field(context, "Date (YYYY-MM-DD)", InputType.TYPE_CLASS_DATETIME | InputType.TYPE_DATETIME_VARIATION_DATE);
        Calendar tomorrow = Calendar.getInstance();
        tomorrow.add(Calendar.DAY_OF_YEAR, 1);
        date.setText(new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(tomorrow.getTime()));
        date.setFocusable(false);
        date.setOnClickListener(v -> new DatePickerDialog(context, (picker, year, month, day) -> {
            Calendar chosen = Calendar.getInstance(); chosen.set(year, month, day);
            date.setText(new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(chosen.getTime()));
        }, tomorrow.get(Calendar.YEAR), tomorrow.get(Calendar.MONTH), tomorrow.get(Calendar.DAY_OF_MONTH)).show());
        Spinner time = spinner(context, new String[]{"09:00 AM - 11:00 AM", "11:00 AM - 01:00 PM", "02:00 PM - 04:00 PM", "04:00 PM - 06:00 PM", "06:00 PM - 08:00 PM"});
        Spinner area = spinner(context, new String[]{"Sonadanga", "Khulna Sadar", "Khalishpur", "Daulatpur", "Other KCC area"});
        Spinner payment = spinner(context, new String[]{"Cash on service", "bKash manual", "Nagad manual"});
        EditText address = field(context, "Full service address", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        EditText note = field(context, "Note (optional)", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        TextView selected = new TextView(context); selected.setText("Service: " + serviceName); selected.setTextSize(16); selected.setPadding(4, 8, 4, 12); form.addView(selected);
        addLabel(form, context, "Requested date"); form.addView(date);
        addLabel(form, context, "Time window"); form.addView(time);
        addLabel(form, context, "Service area"); form.addView(area);
        form.addView(address);
        form.addView(note);
        addLabel(form, context, "Payment method"); form.addView(payment);
        AlertDialog dialog = new AlertDialog.Builder(context).setTitle("Request service")
                .setView(form).setNegativeButton("Cancel", null).setPositiveButton("Submit booking", null).create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            if (address.getText().toString().trim().isEmpty()) { toast("Enter the service address."); return; }
            JSONObject body = new JSONObject();
            try {
                JSONObject service = new JSONObject(); service.put("name", serviceName); body.put("service", service);
                JSONObject schedule = new JSONObject(); schedule.put("date", date.getText().toString()); schedule.put("time", time.getSelectedItem().toString()); body.put("schedule", schedule);
                String[] areas = {"Sonadanga", "Khulna Sadar", "Khalishpur", "Daulatpur", "other-kcc"};
                String[] payments = {"cash_on_service", "bkash_manual", "nagad_manual"};
                body.put("area", areas[area.getSelectedItemPosition()]); body.put("address", address.getText().toString().trim());
                body.put("note", note.getText().toString().trim()); body.put("paymentMethod", payments[payment.getSelectedItemPosition()]);
            } catch (Exception e) { toast("Could not prepare booking."); return; }
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false);
            post("/bookings", body, getToken(), (status, response) -> {
                if (status >= 200 && status < 300) {
                    dialog.dismiss();
                    toast("Booking received: " + response.optString("bookingId", ""));
                } else {
                    toast(response.optString("message", "Booking failed. Please try again."));
                    if (dialog.isShowing()) dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(true);
                }
            });
        }));
        dialog.show();
    }

    private interface ApiCallback { void complete(int status, JSONObject response); }

    private void post(String path, JSONObject body, String authToken, ApiCallback callback) {
        new Thread(() -> {
            int status = 0;
            JSONObject response = new JSONObject();
            HttpURLConnection connection = null;
            try {
                connection = (HttpURLConnection) new URL(API_BASE + path).openConnection();
                connection.setRequestMethod("POST"); connection.setConnectTimeout(12000); connection.setReadTimeout(12000);
                connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
                if (authToken != null) connection.setRequestProperty("Authorization", "Bearer " + authToken);
                connection.setDoOutput(true);
                try (OutputStream out = connection.getOutputStream()) { out.write(body.toString().getBytes(StandardCharsets.UTF_8)); }
                status = connection.getResponseCode();
                InputStream stream = status >= 200 && status < 400 ? connection.getInputStream() : connection.getErrorStream();
                if (stream != null) {
                    StringBuilder text = new StringBuilder();
                    try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
                        String line; while ((line = reader.readLine()) != null) text.append(line);
                    }
                    response = new JSONObject(text.toString());
                }
            } catch (Exception e) {
                try { response.put("message", "Can't reach the backend. Start the API and connect it to this phone."); } catch (Exception ignored) {}
            } finally { if (connection != null) connection.disconnect(); }
            int resultStatus = status; JSONObject result = response;
            if (getActivity() != null) requireActivity().runOnUiThread(() -> callback.complete(resultStatus, result));
        }).start();
    }

    private String getToken() {
        if (!isAdded()) return null;
        return requireContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("token", null);
    }

    private void updateAccountButton(View root) {
        Button button = root.findViewById(R.id.btn_account);
        String name = requireContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("name", null);
        button.setText(getToken() == null ? "Sign in / Create account" : "Signed in: " + name);
    }

    private LinearLayout formContainer(Context context) {
        LinearLayout form = new LinearLayout(context); form.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (20 * getResources().getDisplayMetrics().density); form.setPadding(pad, pad, pad, 0); return form;
    }

    private EditText field(Context context, String hint, int inputType) {
        EditText field = new EditText(context); field.setHint(hint); field.setInputType(inputType); field.setSingleLine(false);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.bottomMargin = (int) (8 * getResources().getDisplayMetrics().density); field.setLayoutParams(params); return field;
    }

    private Spinner spinner(Context context, String[] options) {
        Spinner spinner = new Spinner(context); spinner.setAdapter(new ArrayAdapter<>(context, android.R.layout.simple_spinner_dropdown_item, options));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.bottomMargin = (int) (8 * getResources().getDisplayMetrics().density); spinner.setLayoutParams(params); return spinner;
    }

    private void addLabel(LinearLayout form, Context context, String label) {
        TextView view = new TextView(context); view.setText(label); view.setTextSize(14); view.setPadding(4, 4, 4, 2); form.addView(view);
    }

    private void toast(String message) {
        if (isAdded()) Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show();
    }

    @Override
    public void onDestroyView() {
        if (firebaseAuth != null) firebaseAuth.removeIdTokenListener(idTokenListener);
        super.onDestroyView();
        binding = null;
    }
}
