package com.example.sucharu;

import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.appcompat.widget.PopupMenu;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.bottomnavigation.BottomNavigationView;

public class MainActivity extends AppCompatActivity {
    private static final String STATE_TAB = "selected_customer_tab";
    private int selectedTab = R.id.nav_home;
    private ServiceItem pendingService;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        toolbar.setNavigationOnClickListener(v -> showAppMenu(toolbar));
        BottomNavigationView navigation = findViewById(R.id.bottom_navigation);
        navigation.setOnItemSelectedListener(this::onTabSelected);
        if (savedInstanceState != null) selectedTab = savedInstanceState.getInt(STATE_TAB, R.id.nav_home);
        navigation.setSelectedItemId(selectedTab);
    }

    private void showAppMenu(View anchor) {
        PopupMenu menu = new PopupMenu(this, anchor);
        menu.inflate(R.menu.menu_customer_top);
        menu.setOnMenuItemClickListener(this::onTopMenuSelected);
        menu.show();
    }

    private boolean onTopMenuSelected(@NonNull MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.top_home) {
            if (selectedTab != R.id.nav_home) showTab(R.id.nav_home);
            postHomeScroll(0);
            return true;
        }
        if (id == R.id.top_services) {
            if (selectedTab != R.id.nav_home) showTab(R.id.nav_home);
            postHomeScroll(R.id.services_section_title);
            return true;
        }
        if (id == R.id.top_pricing) {
            if (selectedTab != R.id.nav_home) showTab(R.id.nav_home);
            postHomeScroll(R.id.pricing_card);
            return true;
        }
        if (id == R.id.top_track_booking || id == R.id.top_bookings) {
            if (selectedTab != R.id.nav_bookings) showTab(R.id.nav_bookings);
            if (id == R.id.top_track_booking) {
                findViewById(R.id.content_frame).post(() -> {
                    Fragment current = getSupportFragmentManager().findFragmentById(R.id.content_frame);
                    if (current instanceof BookingsFragment) ((BookingsFragment) current).scrollToTracker();
                });
            }
            return true;
        }
        if (id == R.id.top_account) {
            showTab(R.id.nav_account);
            return true;
        }
        return false;
    }

    private void postHomeScroll(int targetId) {
        findViewById(R.id.content_frame).post(() -> {
            getSupportFragmentManager().executePendingTransactions();
            Fragment current = getSupportFragmentManager().findFragmentById(R.id.content_frame);
            if (!(current instanceof HomeFragment)) return;
            HomeFragment home = (HomeFragment) current;
            if (targetId == 0) home.scrollToTop();
            else home.scrollTo(targetId);
        });
    }

    private boolean onTabSelected(@NonNull MenuItem item) {
        selectedTab = item.getItemId();
        Fragment fragment;
        String title;
        if (selectedTab == R.id.nav_bookings) {
            fragment = new BookingsFragment();
            title = "My bookings";
        } else if (selectedTab == R.id.nav_account) {
            fragment = new AccountFragment();
            title = "Account";
        } else {
            selectedTab = R.id.nav_home;
            fragment = new HomeFragment();
            title = "সুচারু";
        }
        getSupportFragmentManager().beginTransaction().replace(R.id.content_frame, fragment).commit();
        if (getSupportActionBar() != null) getSupportActionBar().setTitle(title);
        return true;
    }

    public void showTab(int menuId) {
        BottomNavigationView navigation = findViewById(R.id.bottom_navigation);
        if (navigation.getSelectedItemId() == menuId) onTabSelected(navigation.getMenu().findItem(menuId));
        else navigation.setSelectedItemId(menuId);
    }

    public void requestBooking(ServiceItem service) {
        if (!CustomerSession.isSignedIn(this)) {
            pendingService = service;
            Toast.makeText(this, "Sign in or create an account to request a service.", Toast.LENGTH_LONG).show();
            showTab(R.id.nav_account);
            return;
        }
        BookingDialog.show(this, service, () -> showTab(R.id.nav_bookings));
    }

    public void onCustomerSessionChanged() {
        if (pendingService != null && CustomerSession.isSignedIn(this)) {
            ServiceItem service = pendingService;
            pendingService = null;
            showTab(R.id.nav_home);
            findViewById(R.id.content_frame).post(() -> requestBooking(service));
        } else {
            showTab(selectedTab);
        }
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        outState.putInt(STATE_TAB, selectedTab);
        super.onSaveInstanceState(outState);
    }
}
