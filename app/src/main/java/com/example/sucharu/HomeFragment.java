package com.example.sucharu;

import android.graphics.Color;
import android.graphics.Rect;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.ScrollView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.google.android.material.card.MaterialCardView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.text.NumberFormat;
import java.util.Set;

public class HomeFragment extends Fragment {
    private final List<ServiceItem> catalog = new ArrayList<>();
    private final Set<String> favoriteIds = new HashSet<>();
    private static final String SAVED_FILTER = "__saved_services__";
    private LinearLayout serviceList, filters;
    private EditText search;
    private TextView status;
    private ScrollView homeScroll;
    private String activeCategory = "All";

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_home, container, false);
    }

    @Override public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        serviceList = view.findViewById(R.id.service_list);
        homeScroll = view.findViewById(R.id.home_scroll);
        filters = view.findViewById(R.id.category_filters);
        search = view.findViewById(R.id.service_search);
        status = view.findViewById(R.id.catalog_status);
        status.setOnClickListener(v -> loadCatalog());
        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { renderServices(); }
            @Override public void afterTextChanged(Editable s) {}
        });
        loadCatalog();
    }

    @Override public void onResume() {
        super.onResume();
        loadFavorites();
    }

    public void scrollToTop() {
        if (homeScroll != null) homeScroll.smoothScrollTo(0, 0);
    }

    public void scrollTo(int targetId) {
        if (homeScroll == null) return;
        View target = homeScroll.findViewById(targetId);
        if (target != null) homeScroll.post(() -> homeScroll.postDelayed(() -> {
            Rect bounds = new Rect();
            target.getDrawingRect(bounds);
            homeScroll.offsetDescendantRectToMyCoords(target, bounds);
            homeScroll.smoothScrollTo(0, Math.max(0, bounds.top - Ui.dp(requireContext(), 12)));
        }, 120));
    }

    private void loadCatalog() {
        status.setText("Loading service catalog…");
        ApiClient.get("/services", null, (code, object, array, error) -> {
            if (!isAdded()) return;
            catalog.clear();
            if (array != null) {
                for (int i = 0; i < array.length(); i++) {
                    try { catalog.add(ServiceItem.fromJson(array.getJSONObject(i))); }
                    catch (Exception ignored) { }
                }
            }
            if (error != null || catalog.isEmpty()) {
                status.setText(error == null ? "No services are available right now." : error + "  Tap to retry.");
                serviceList.removeAllViews();
                filters.removeAllViews();
                return;
            }
            status.setText("Choose a service. Prices shown are starting estimates.");
            renderFilters();
            renderServices();
        });
    }

    private void loadFavorites() {
        if (!CustomerSession.isSignedIn(requireContext())) {
            favoriteIds.clear();
            if (filters != null) renderFilters();
            if (serviceList != null) renderServices();
            return;
        }
        CustomerSession.withToken(requireContext(), (token, tokenError) -> {
            if (tokenError != null || !isAdded()) return;
            ApiClient.get("/customers/favorites", token, (code, object, rows, error) -> {
                if (!isAdded() || error != null) return;
                favoriteIds.clear();
                if (rows != null) for (int i = 0; i < rows.length(); i++) {
                    JSONObject row = rows.optJSONObject(i);
                    if (row != null && !row.optString("serviceId").isEmpty()) favoriteIds.add(row.optString("serviceId"));
                }
                renderFilters();
                renderServices();
            });
        });
    }

    private void renderFilters() {
        filters.removeAllViews();
        Set<String> categories = new LinkedHashSet<>();
        categories.add("All");
        for (ServiceItem item : catalog) categories.add(item.category);
        categories.add(SAVED_FILTER);
        for (String category : categories) {
            boolean selected = category.equals(activeCategory);
            String label = SAVED_FILTER.equals(category) ? "♥  Saved" : category;
            TextView chip = Ui.text(requireContext(), label, 13, selected ? Color.WHITE : Ui.INK, true);
            chip.setGravity(android.view.Gravity.CENTER);
            chip.setPadding(Ui.dp(requireContext(), 14), Ui.dp(requireContext(), 9), Ui.dp(requireContext(), 14), Ui.dp(requireContext(), 9));
            android.graphics.drawable.GradientDrawable chipBackground = Ui.pill(selected ? Ui.GREEN : Color.WHITE, Ui.dp(requireContext(), 24));
            if (!selected) chipBackground.setStroke(Ui.dp(requireContext(), 1), Ui.LINE);
            chip.setBackground(chipBackground);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-2, -2);
            params.rightMargin = Ui.dp(requireContext(), 8);
            chip.setLayoutParams(params);
            chip.setOnClickListener(v -> {
                if (SAVED_FILTER.equals(category) && !CustomerSession.isSignedIn(requireContext())) {
                    Toast.makeText(requireContext(), "Sign in to sync your saved services.", Toast.LENGTH_SHORT).show();
                    ((MainActivity) requireActivity()).showTab(R.id.nav_account);
                    return;
                }
                activeCategory = category; renderFilters(); renderServices();
            });
            filters.addView(chip);
        }
    }

    private void renderServices() {
        if (!isAdded() || serviceList == null) return;
        serviceList.removeAllViews();
        String query = search.getText().toString().trim().toLowerCase(Locale.ROOT);
        int count = 0;
        for (ServiceItem item : catalog) {
            boolean matchesCategory = "All".equals(activeCategory)
                    || (SAVED_FILTER.equals(activeCategory) ? favoriteIds.contains(item.id) : item.category.equals(activeCategory));
            String haystack = (item.name + " " + item.category + " " + item.description).toLowerCase(Locale.ROOT);
            if (!matchesCategory || !haystack.contains(query)) continue;
            serviceList.addView(serviceCard(item));
            count++;
        }
        if (count == 0 && !catalog.isEmpty()) {
            String emptyMessage = SAVED_FILTER.equals(activeCategory) ? "No saved services yet. Tap the heart on a service to save it." : "No matching services. Try another search.";
            TextView empty = Ui.text(requireContext(), emptyMessage, 14, Ui.MUTED, false);
            empty.setPadding(Ui.dp(requireContext(), 4), Ui.dp(requireContext(), 12), Ui.dp(requireContext(), 4), Ui.dp(requireContext(), 20));
            serviceList.addView(empty);
        }
    }

    private View serviceCard(ServiceItem item) {
        MaterialCardView card = Ui.card(requireContext());
        LinearLayout content = new LinearLayout(requireContext());
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(Ui.dp(requireContext(), 17), Ui.dp(requireContext(), 16), Ui.dp(requireContext(), 17), Ui.dp(requireContext(), 14));
        card.addView(content);
        LinearLayout topRow = new LinearLayout(requireContext());
        topRow.setGravity(android.view.Gravity.CENTER_VERTICAL);
        TextView category = Ui.text(requireContext(), item.icon + "  " + item.category.toUpperCase(Locale.ROOT), 11, Ui.GREEN, true);
        topRow.addView(category, new LinearLayout.LayoutParams(0, -2, 1));
        TextView favorite = Ui.text(requireContext(), favoriteIds.contains(item.id) ? "♥" : "♡", 25,
                favoriteIds.contains(item.id) ? Color.rgb(201, 72, 91) : Ui.MUTED, true);
        favorite.setGravity(android.view.Gravity.CENTER);
        favorite.setContentDescription(favoriteIds.contains(item.id) ? "Remove from saved services" : "Save service");
        favorite.setPadding(Ui.dp(requireContext(), 8), 0, Ui.dp(requireContext(), 4), 0);
        favorite.setOnClickListener(v -> toggleFavorite(item));
        topRow.addView(favorite, new LinearLayout.LayoutParams(-2, Ui.dp(requireContext(), 38)));
        content.addView(topRow);
        TextView name = Ui.text(requireContext(), item.name, 19, Ui.INK, true);
        LinearLayout.LayoutParams nameParams = new LinearLayout.LayoutParams(-1, -2);
        nameParams.topMargin = Ui.dp(requireContext(), 5);
        content.addView(name, nameParams);
        if (!item.description.isEmpty()) {
            TextView description = Ui.text(requireContext(), item.description, 13, Ui.MUTED, false);
            LinearLayout.LayoutParams descParams = new LinearLayout.LayoutParams(-1, -2);
            descParams.topMargin = Ui.dp(requireContext(), 5);
            content.addView(description, descParams);
        }
        String priceText = item.startingPrice == null
                ? "QUOTE REQUIRED"
                : "STARTING AT  ৳" + NumberFormat.getIntegerInstance(Locale.forLanguageTag("en-BD")).format(item.startingPrice);
        TextView price = Ui.text(requireContext(), priceText, 15, Ui.GREEN, true);
        LinearLayout.LayoutParams priceParams = new LinearLayout.LayoutParams(-1, -2);
        priceParams.topMargin = Ui.dp(requireContext(), 9);
        content.addView(price, priceParams);
        LinearLayout actions = new LinearLayout(requireContext());
        actions.setOrientation(LinearLayout.HORIZONTAL);
        Button book = new Button(requireContext());
        book.setText("Request service"); book.setAllCaps(false); book.setTextColor(Color.WHITE);
        book.setBackgroundTintList(ColorStateList.valueOf(Ui.GREEN));
        book.setOnClickListener(v -> ((MainActivity) requireActivity()).requestBooking(item));
        Button details = new Button(requireContext());
        details.setText("Details"); details.setAllCaps(false);
        details.setOnClickListener(v -> showServiceDetails(item));
        LinearLayout.LayoutParams actionParams = new LinearLayout.LayoutParams(0, -2, 1);
        actionParams.topMargin = Ui.dp(requireContext(), 5);
        actionParams.rightMargin = Ui.dp(requireContext(), 4);
        actions.addView(book, actionParams);
        LinearLayout.LayoutParams detailsParams = new LinearLayout.LayoutParams(0, -2, 1);
        detailsParams.topMargin = Ui.dp(requireContext(), 5);
        detailsParams.leftMargin = Ui.dp(requireContext(), 4);
        actions.addView(details, detailsParams);
        content.addView(actions);
        return card;
    }

    private void showServiceDetails(ServiceItem item) {
        StringBuilder details = new StringBuilder();
        details.append(item.description.isEmpty() ? "Our team will confirm the service details with you." : item.description);
        details.append("\n\n").append(item.priceLabel());
        details.append("\nFinal price and availability are confirmed before the service.");
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(item.icon + "  " + item.name)
                .setMessage(details.toString())
                .setNegativeButton("Close", null)
                .setPositiveButton("Request this service", (dialog, which) -> ((MainActivity) requireActivity()).requestBooking(item))
                .show();
    }

    private void toggleFavorite(ServiceItem item) {
        if (!CustomerSession.isSignedIn(requireContext())) {
            Toast.makeText(requireContext(), "Sign in to save services to your account.", Toast.LENGTH_SHORT).show();
            ((MainActivity) requireActivity()).showTab(R.id.nav_account);
            return;
        }
        boolean removing = favoriteIds.contains(item.id);
        CustomerSession.withToken(requireContext(), (token, tokenError) -> {
            if (tokenError != null) { Toast.makeText(requireContext(), tokenError, Toast.LENGTH_LONG).show(); return; }
            String encoded;
            try { encoded = java.net.URLEncoder.encode(item.id, "UTF-8"); }
            catch (Exception ignored) { encoded = item.id; }
            String path = "/customers/favorites/" + encoded;
            ApiClient.Callback callback = (code, object, rows, error) -> {
                if (!isAdded()) return;
                if (error != null || code < 200 || code >= 300) {
                    Toast.makeText(requireContext(), error == null ? "Could not update saved services." : error, Toast.LENGTH_LONG).show();
                    return;
                }
                if (removing) favoriteIds.remove(item.id); else favoriteIds.add(item.id);
                renderFilters(); renderServices();
                Toast.makeText(requireContext(), removing ? "Removed from saved services." : "Saved to your services.", Toast.LENGTH_SHORT).show();
            };
            if (removing) ApiClient.delete(path, token, callback);
            else ApiClient.put(path, new JSONObject(), token, callback);
        });
    }
}
