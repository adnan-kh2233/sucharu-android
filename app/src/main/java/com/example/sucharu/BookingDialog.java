package com.example.sucharu;

import android.app.DatePickerDialog;
import android.content.Context;
import android.graphics.Color;
import android.text.InputType;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;

public final class BookingDialog {
    private static final String[] TIMES = {"09:00 AM - 11:00 AM", "11:00 AM - 01:00 PM", "02:00 PM - 04:00 PM", "04:00 PM - 06:00 PM", "06:00 PM - 08:00 PM"};
    private static final String[] AREAS = {"Sonadanga", "Khulna Sadar", "Khalishpur", "Daulatpur", "Other Khulna City Corporation area"};
    private static final String[] AREA_VALUES = {"Sonadanga", "Khulna Sadar", "Khalishpur", "Daulatpur", "other-kcc"};
    private static final String[] PAYMENTS = {"Cash on service (COD)", "bKash (manual after confirmation)", "Nagad (manual after confirmation)"};
    private static final String[] PAYMENT_VALUES = {"cash_on_service", "bkash_manual", "nagad_manual"};

    private BookingDialog() {}

    public static void show(Context context, ServiceItem service, Runnable onReceived) {
        LinearLayout form = new LinearLayout(context);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(Ui.dp(context, 20), Ui.dp(context, 8), Ui.dp(context, 20), Ui.dp(context, 4));

        TextView serviceText = Ui.text(context, service.name + " · " + service.priceLabel(), 14, Color.rgb(18, 107, 89), true);
        form.addView(serviceText);
        EditText date = field(context, "Requested date (YYYY-MM-DD)", InputType.TYPE_CLASS_DATETIME);
        Calendar tomorrow = Calendar.getInstance();
        tomorrow.add(Calendar.DAY_OF_YEAR, 1);
        date.setText(new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(tomorrow.getTime()));
        date.setFocusable(false);
        date.setOnClickListener(v -> new DatePickerDialog(context, (picker, year, month, day) -> {
            Calendar chosen = Calendar.getInstance();
            chosen.set(year, month, day);
            date.setText(new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(chosen.getTime()));
        }, tomorrow.get(Calendar.YEAR), tomorrow.get(Calendar.MONTH), tomorrow.get(Calendar.DAY_OF_MONTH)).show());
        addLabeled(form, context, "Preferred date", date);

        Spinner time = spinner(context, TIMES);
        addLabeled(form, context, "Preferred time", time);
        Spinner area = spinner(context, AREAS);
        addLabeled(form, context, "Service area", area);
        EditText address = field(context, "House, road and area", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        address.setMinLines(2);
        addLabeled(form, context, "Full address", address);
        EditText landmark = field(context, "Nearby landmark (optional)", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        addLabeled(form, context, "Landmark", landmark);
        final JSONObject[] savedAddress = {CustomerSession.getDefaultAddress(context)};
        if (savedAddress[0] != null) {
            address.setText(savedAddress[0].optString("address", ""));
            landmark.setText(savedAddress[0].optString("landmark", ""));
            String savedArea = savedAddress[0].optString("area", "");
            for (int i = 0; i < AREA_VALUES.length; i++) if (AREA_VALUES[i].equals(savedArea)) area.setSelection(i);
        }
        CustomerSession.withToken(context, (token, tokenError) -> {
            if (tokenError != null) return;
            ApiClient.get("/customers/me", token, (status, object, rows, error) -> {
                if (error != null || object == null) return;
                JSONObject customer = object.optJSONObject("customer");
                JSONObject latestAddress = customer == null ? null : customer.optJSONObject("defaultAddress");
                if (latestAddress == null || !address.getText().toString().trim().isEmpty()) return;
                savedAddress[0] = latestAddress;
                address.setText(latestAddress.optString("address", ""));
                landmark.setText(latestAddress.optString("landmark", ""));
                for (int i = 0; i < AREA_VALUES.length; i++) if (AREA_VALUES[i].equals(latestAddress.optString("area"))) area.setSelection(i);
                CustomerSession.saveDefaultAddress(context, latestAddress);
            });
        });
        EditText note = field(context, "Any details for the service team? (optional)", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        note.setMinLines(2);
        addLabeled(form, context, "Additional note", note);
        Spinner payment = spinner(context, PAYMENTS);
        addLabeled(form, context, "Payment preference", payment);
        TextView paymentInfo = Ui.text(context, "No online checkout is active. bKash/Nagad payments are manual; wait for Sucharu to confirm the booking and amount before sending money.", 12, Color.rgb(96, 113, 108), false);
        paymentInfo.setPadding(Ui.dp(context, 2), Ui.dp(context, 2), Ui.dp(context, 2), Ui.dp(context, 4));
        form.addView(paymentInfo);
        payment.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(android.widget.AdapterView<?> parent, android.view.View view, int position, long id) {
                paymentInfo.setText(position == 0
                        ? "Pay cash after the service is completed. Sucharu will confirm availability and the final price."
                        : "Manual payment only. Wait for Sucharu to confirm the booking and payable amount. Never share your PIN or OTP.");
            }
            @Override public void onNothingSelected(android.widget.AdapterView<?> parent) {}
        });

        ScrollView scroll = new ScrollView(context);
        scroll.setFillViewport(false);
        scroll.addView(form);
        AlertDialog dialog = new MaterialAlertDialogBuilder(context)
                .setTitle("Request a service")
                .setView(scroll)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Submit request", null)
                .create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            if (address.getText().toString().trim().isEmpty()) {
                address.setError("Enter the service address");
                return;
            }
            JSONObject body = new JSONObject();
            try {
                JSONObject serviceJson = new JSONObject();
                serviceJson.put("name", service.name);
                JSONObject schedule = new JSONObject();
                schedule.put("date", date.getText().toString());
                schedule.put("time", time.getSelectedItem().toString());
                body.put("service", serviceJson);
                body.put("schedule", schedule);
                body.put("area", AREA_VALUES[area.getSelectedItemPosition()]);
                body.put("address", address.getText().toString().trim());
                body.put("landmark", landmark.getText().toString().trim());
                if (savedAddress[0] != null && !savedAddress[0].isNull("latitude") && !savedAddress[0].isNull("longitude")
                        && savedAddress[0].optString("address").equals(address.getText().toString().trim())) {
                    JSONObject location = new JSONObject();
                    location.put("latitude", savedAddress[0].getDouble("latitude"));
                    location.put("longitude", savedAddress[0].getDouble("longitude"));
                    body.put("location", location);
                }
                body.put("note", note.getText().toString().trim());
                body.put("paymentMethod", PAYMENT_VALUES[payment.getSelectedItemPosition()]);
            } catch (Exception exception) {
                Toast.makeText(context, "Could not prepare the request.", Toast.LENGTH_LONG).show();
                return;
            }
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false);
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setText("Sending…");
            CustomerSession.withToken(context, (token, tokenError) -> {
                if (tokenError != null) {
                    dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(true);
                    dialog.getButton(AlertDialog.BUTTON_POSITIVE).setText("Submit request");
                    Toast.makeText(context, tokenError, Toast.LENGTH_LONG).show();
                    return;
                }
                ApiClient.post("/bookings", body, token, (status, response, array, error) -> {
                    if (status >= 200 && status < 300 && response != null) {
                        String bookingId = response.optString("bookingId", "");
                        dialog.dismiss();
                        new MaterialAlertDialogBuilder(context)
                                .setTitle("Request received")
                                .setMessage("Booking ID: " + bookingId + "\n\nSucharu will confirm availability and the final price. Keep this booking ID to track your request.")
                                .setPositiveButton("View my bookings", (d, which) -> onReceived.run())
                                .setNegativeButton("Done", (d, which) -> onReceived.run())
                                .show();
                    } else {
                        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(true);
                        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setText("Submit request");
                        Toast.makeText(context, error == null ? "Booking request could not be sent." : error, Toast.LENGTH_LONG).show();
                    }
                });
            });
        }));
        dialog.show();
        dialog.getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT, (int) (context.getResources().getDisplayMetrics().heightPixels * 0.88));
    }

    private static EditText field(Context context, String hint, int inputType) {
        EditText input = new EditText(context);
        input.setHint(hint);
        input.setInputType(inputType);
        input.setBackgroundResource(R.drawable.bg_input);
        input.setPadding(Ui.dp(context, 12), 0, Ui.dp(context, 12), 0);
        input.setTextSize(14);
        return input;
    }

    private static Spinner spinner(Context context, String[] values) {
        Spinner spinner = new Spinner(context);
        spinner.setAdapter(new ArrayAdapter<>(context, android.R.layout.simple_spinner_dropdown_item, values));
        return spinner;
    }

    private static void addLabeled(LinearLayout parent, Context context, String label, android.view.View field) {
        TextView title = Ui.text(context, label, 12, Color.rgb(96, 113, 108), true);
        LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(-1, -2);
        labelParams.topMargin = Ui.dp(context, 7);
        parent.addView(title, labelParams);
        LinearLayout.LayoutParams fieldParams = new LinearLayout.LayoutParams(-1, -2);
        fieldParams.topMargin = Ui.dp(context, 2);
        parent.addView(field, fieldParams);
    }
}
