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

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.google.android.material.card.MaterialCardView;

import org.json.JSONArray;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.text.NumberFormat;
import java.util.Set;

public class HomeFragment extends Fragment {
    private final List<ServiceItem> catalog = new ArrayList<>();
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

    private void renderFilters() {
        filters.removeAllViews();
        Set<String> categories = new LinkedHashSet<>();
        categories.add("All");
        for (ServiceItem item : catalog) categories.add(item.category);
        for (String category : categories) {
            boolean selected = category.equals(activeCategory);
            TextView chip = Ui.text(requireContext(), category, 13, selected ? Color.WHITE : Ui.INK, true);
            chip.setGravity(android.view.Gravity.CENTER);
            chip.setPadding(Ui.dp(requireContext(), 14), Ui.dp(requireContext(), 9), Ui.dp(requireContext(), 14), Ui.dp(requireContext(), 9));
            android.graphics.drawable.GradientDrawable chipBackground = Ui.pill(selected ? Ui.GREEN : Color.WHITE, Ui.dp(requireContext(), 24));
            if (!selected) chipBackground.setStroke(Ui.dp(requireContext(), 1), Ui.LINE);
            chip.setBackground(chipBackground);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-2, -2);
            params.rightMargin = Ui.dp(requireContext(), 8);
            chip.setLayoutParams(params);
            chip.setOnClickListener(v -> { activeCategory = category; renderFilters(); renderServices(); });
            filters.addView(chip);
        }
    }

    private void renderServices() {
        if (!isAdded() || serviceList == null) return;
        serviceList.removeAllViews();
        String query = search.getText().toString().trim().toLowerCase(Locale.ROOT);
        int count = 0;
        for (ServiceItem item : catalog) {
            boolean matchesCategory = "All".equals(activeCategory) || item.category.equals(activeCategory);
            String haystack = (item.name + " " + item.category + " " + item.description).toLowerCase(Locale.ROOT);
            if (!matchesCategory || !haystack.contains(query)) continue;
            serviceList.addView(serviceCard(item));
            count++;
        }
        if (count == 0 && !catalog.isEmpty()) {
            TextView empty = Ui.text(requireContext(), "No matching services. Try another search.", 14, Ui.MUTED, false);
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
        TextView category = Ui.text(requireContext(), item.icon + "  " + item.category.toUpperCase(Locale.ROOT), 11, Ui.GREEN, true);
        content.addView(category);
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
        Button book = new Button(requireContext());
        book.setText("Request service");
        book.setAllCaps(false);
        book.setTextColor(Color.WHITE);
        book.setBackgroundTintList(ColorStateList.valueOf(Ui.GREEN));
        book.setOnClickListener(v -> ((MainActivity) requireActivity()).requestBooking(item));
        LinearLayout.LayoutParams buttonParams = new LinearLayout.LayoutParams(-1, -2);
        buttonParams.topMargin = Ui.dp(requireContext(), 5);
        content.addView(book, buttonParams);
        return card;
    }
}
