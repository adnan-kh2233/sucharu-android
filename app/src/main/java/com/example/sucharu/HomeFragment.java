package com.example.sucharu;

import android.graphics.Color;
import android.graphics.Rect;
import android.content.Intent;
import android.content.IntentFilter;
import android.provider.Settings;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.core.content.ContextCompat;
import androidx.core.widget.NestedScrollView;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.example.sucharu.databinding.FragmentHomeBinding;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class HomeFragment extends Fragment implements SystemEventCallbacks, ConnectivityMonitor.Listener {
    private FragmentHomeBinding binding;
    private final List<ServiceItem> catalog = new ArrayList<>();
    private final Set<String> favoriteIds = new HashSet<>();
    private static final String SAVED_FILTER = "__saved_services__";
    private RecyclerView serviceList;
    private LinearLayout filters;
    private ServiceAdapter serviceAdapter;
    private EditText search;
    private TextView status, emptyServices, systemBanner, serviceCoverage;
    private NestedScrollView homeScroll;
    private String activeCategory = "All";
    private boolean airplaneMode, batteryLow, networkAvailable;
    private AirplaneModeReceiver airplaneReceiver;
    private LowBatteryReceiver batteryReceiver;
    private boolean receiversRegistered;

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentHomeBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        serviceList = binding.serviceList;
        homeScroll = binding.homeScroll;
        filters = binding.categoryFilters;
        search = binding.serviceSearch;
        status = binding.catalogStatus;
        emptyServices = binding.emptyServices;
        systemBanner = binding.systemStatusBanner;
        serviceCoverage = binding.serviceCoverage;
        serviceList.setLayoutManager(new LinearLayoutManager(requireContext()));
        serviceList.setNestedScrollingEnabled(true);
        serviceAdapter = new ServiceAdapter(requireContext(), new ServiceAdapter.Listener() {
            @Override public void onRequest(ServiceItem item) { ((MainActivity) requireActivity()).requestBooking(item); }
            @Override public void onDetails(ServiceItem item) { showServiceDetails(item); }
            @Override public void onFavorite(ServiceItem item) { toggleFavorite(item); }
        });
        serviceList.setAdapter(serviceAdapter);
        List<String> coverage = ServiceCoverageParser.parse(requireContext());
        if (!coverage.isEmpty()) serviceCoverage.setText(android.text.TextUtils.join(" · ", coverage));
        airplaneMode = Settings.Global.getInt(requireContext().getContentResolver(), Settings.Global.AIRPLANE_MODE_ON, 0) == 1;
        networkAvailable = ConnectivityMonitor.isConnected();
        renderSystemBanner();
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

    @Override public void onStart() {
        super.onStart();
        if (!receiversRegistered) {
            airplaneReceiver = new AirplaneModeReceiver(this);
            batteryReceiver = new LowBatteryReceiver(this);
            ContextCompat.registerReceiver(requireContext(), airplaneReceiver,
                    new IntentFilter(Intent.ACTION_AIRPLANE_MODE_CHANGED), ContextCompat.RECEIVER_NOT_EXPORTED);
            IntentFilter batteryFilter = new IntentFilter();
            batteryFilter.addAction(Intent.ACTION_BATTERY_LOW);
            batteryFilter.addAction(Intent.ACTION_BATTERY_OKAY);
            ContextCompat.registerReceiver(requireContext(), batteryReceiver, batteryFilter, ContextCompat.RECEIVER_NOT_EXPORTED);
            receiversRegistered = true;
        }
        ConnectivityMonitor.addListener(this);
    }

    @Override public void onStop() {
        ConnectivityMonitor.removeListener(this);
        if (receiversRegistered && getContext() != null) {
            requireContext().unregisterReceiver(airplaneReceiver);
            requireContext().unregisterReceiver(batteryReceiver);
            receiversRegistered = false;
        }
        super.onStop();
    }

    @Override public void onAirplaneModeChanged(boolean enabled) { airplaneMode = enabled; renderSystemBanner(); }
    @Override public void onBatteryLow(boolean low) { batteryLow = low; renderSystemBanner(); }
    @Override public void onConnectivityChanged(boolean available) { networkAvailable = available; renderSystemBanner(); }

    private void renderSystemBanner() {
        if (systemBanner == null || !isAdded()) return;
        String message = null;
        if (!networkAvailable) message = "Internet unavailable. Service requests and account updates need a connection.";
        else if (airplaneMode) message = "Airplane mode is on, but internet is available through another connection.";
        else if (batteryLow) message = "Battery is low. Some devices may limit background activity.";
        if (message == null) systemBanner.setVisibility(View.GONE);
        else { systemBanner.setText(message); systemBanner.setVisibility(View.VISIBLE); }
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
            if (!isAdded() || binding == null) return;
            catalog.clear();
            if (array != null) {
                for (int i = 0; i < array.length(); i++) {
                    try { catalog.add(ServiceItem.fromJson(array.getJSONObject(i))); }
                    catch (Exception ignored) { }
                }
            }
            if (error != null || catalog.isEmpty()) {
                status.setText(error == null ? "No services are available right now." : error + "  Tap to retry.");
                serviceAdapter.submitItems(new ArrayList<>());
                emptyServices.setVisibility(View.GONE);
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
                if (!isAdded() || binding == null || error != null) return;
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
        if (!isAdded() || binding == null || serviceList == null || serviceAdapter == null) return;
        String query = search.getText().toString().trim().toLowerCase(Locale.ROOT);
        List<ServiceItem> visibleItems = new ArrayList<>();
        for (ServiceItem item : catalog) {
            boolean matchesCategory = "All".equals(activeCategory)
                    || (SAVED_FILTER.equals(activeCategory) ? favoriteIds.contains(item.id) : item.category.equals(activeCategory));
            String haystack = (item.name + " " + item.category + " " + item.description).toLowerCase(Locale.ROOT);
            if (!matchesCategory || !haystack.contains(query)) continue;
            visibleItems.add(item);
        }
        serviceAdapter.submitItems(visibleItems);
        serviceAdapter.setFavoriteIds(favoriteIds);
        if (visibleItems.isEmpty() && !catalog.isEmpty()) {
            String emptyMessage = SAVED_FILTER.equals(activeCategory) ? "No saved services yet. Tap the heart on a service to save it." : "No matching services. Try another search.";
            emptyServices.setText(emptyMessage);
            emptyServices.setVisibility(View.VISIBLE);
        } else {
            emptyServices.setVisibility(View.GONE);
        }
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

    @Override public void onDestroyView() {
        binding = null;
        serviceList = null;
        filters = null;
        serviceAdapter = null;
        search = null;
        status = null;
        emptyServices = null;
        systemBanner = null;
        serviceCoverage = null;
        homeScroll = null;
        super.onDestroyView();
    }
}
