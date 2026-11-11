package com.example.sucharu;

import android.os.Bundle;
import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.view.MenuItem;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.appcompat.widget.PopupMenu;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.core.content.ContextCompat;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.example.sucharu.databinding.ActivityMainBinding;

public class MainActivity extends AppCompatActivity {
    public static final String EXTRA_CUSTOMER_AUTH = "open_customer_auth";
    private static final String STATE_TAB = "selected_customer_tab";
    private int selectedTab = R.id.nav_home;
    private ServiceItem pendingService;
    private ActivityResultLauncher<String> notificationPermission;
    private boolean customerAuthGate;
    private ActivityMainBinding binding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        notificationPermission = registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
            if (granted) CustomerSession.syncNotificationToken(this);
            else Toast.makeText(this, "You can enable booking updates later in Android settings.", Toast.LENGTH_LONG).show();
        });
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        MaterialToolbar toolbar = binding.toolbar;
        setSupportActionBar(toolbar);
        toolbar.setNavigationOnClickListener(v -> showAppMenu(toolbar));
        BottomNavigationView navigation = binding.bottomNavigation;
        navigation.setOnItemSelectedListener(this::onTabSelected);
        if (savedInstanceState != null) selectedTab = savedInstanceState.getInt(STATE_TAB, R.id.nav_home);
        else if (getIntent().getBooleanExtra(EXTRA_CUSTOMER_AUTH, false) && !CustomerSession.isSignedIn(this)) {
            selectedTab = R.id.nav_account;
            customerAuthGate = true;
            binding.bottomNavigation.setVisibility(View.GONE);
            toolbar.setNavigationIcon(null);
        }
        navigation.setSelectedItemId(selectedTab);
        if (CustomerSession.isSignedIn(this)) {
            CustomerSession.ensureFirebaseCustomerSession(this, ignored -> { });
        }
        openBookingFromNotification(getIntent());
    }

    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        openBookingFromNotification(intent);
    }

    private void openBookingFromNotification(Intent intent) {
        String bookingId = intent == null ? null : intent.getStringExtra("notification_booking_id");
        if (bookingId != null && !bookingId.isEmpty()) showTab(R.id.nav_bookings);
    }

    public void enableBookingNotifications() {
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS);
        } else {
            CustomerSession.syncNotificationToken(this);
            Toast.makeText(this, "Booking updates are enabled on this device.", Toast.LENGTH_SHORT).show();
        }
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
                binding.contentFrame.post(() -> {
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
        binding.contentFrame.post(() -> {
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
        if (customerAuthGate && menuId != R.id.nav_account) return;
        BottomNavigationView navigation = binding.bottomNavigation;
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
        if (CustomerSession.isSignedIn(this) && (Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED)) CustomerSession.syncNotificationToken(this);
        if (pendingService != null && CustomerSession.isSignedIn(this)) {
            ServiceItem service = pendingService;
            pendingService = null;
            showTab(R.id.nav_home);
            binding.contentFrame.post(() -> requestBooking(service));
        } else if (getIntent().getBooleanExtra(EXTRA_CUSTOMER_AUTH, false)) {
            getIntent().removeExtra(EXTRA_CUSTOMER_AUTH);
            customerAuthGate = false;
            binding.bottomNavigation.setVisibility(View.VISIBLE);
            binding.toolbar.setNavigationIcon(R.drawable.ic_menu);
            selectedTab = R.id.nav_home;
            showTab(R.id.nav_home);
        } else {
            showTab(selectedTab);
        }
    }

    @Override protected void onDestroy() { binding = null; super.onDestroy(); }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        outState.putInt(STATE_TAB, selectedTab);
        super.onSaveInstanceState(outState);
    }
}
