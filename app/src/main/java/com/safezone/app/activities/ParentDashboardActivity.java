package com.safezone.app.activities;

import android.content.Intent;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.badge.BadgeDrawable;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.ValueEventListener;
import com.safezone.app.R;
import com.safezone.app.fragments.ChildrenListFragment;
import com.safezone.app.models.Alert;
import com.safezone.app.utils.FirebaseHelper;
import com.safezone.app.utils.SharedPrefsHelper;

/**
 * Parent Dashboard Activity - UPDATED WITH NAVIGATION
 * Bottom Navigation: Children, Reports, Profile
 * Toolbar Menu: Settings, Notifications, Logout
 */
public class ParentDashboardActivity extends AppCompatActivity {

    private MaterialToolbar toolbar;
    private BottomNavigationView bottomNavigation;
    private FloatingActionButton fabAddChild;
    private FragmentManager fragmentManager;

    private SharedPrefsHelper prefsHelper;
    private ValueEventListener alertsListener;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_parent_dashboard);

        prefsHelper = new SharedPrefsHelper(this);

        // Verify user is logged in and is parent
        if (!prefsHelper.isLoggedIn() || !prefsHelper.isParent()) {
            navigateToLogin();
            return;
        }

        initViews();
        setupToolbar();
        setupBottomNavigation();
        setupFAB();
        setupAlertsBadge();
        
        // Start notification service for real-time push notifications
        startNotificationService();

        // Load default fragment (Children List)
        if (savedInstanceState == null) {
            loadFragment(new ChildrenListFragment());
            bottomNavigation.setSelectedItemId(R.id.nav_children);
        }
    }
    
    /**
     * Start notification service to receive real-time notifications
     */
    private void startNotificationService() {
        String parentId = FirebaseHelper.getCurrentUserId();
        if (parentId != null) {
            Intent serviceIntent = new Intent(this, com.safezone.app.services.ParentNotificationService.class);
            serviceIntent.putExtra("parentId", parentId);
            
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent);
            } else {
                startService(serviceIntent);
            }
            
            android.util.Log.d("ParentDashboard", "Notification service started");
        }
    }

    private void initViews() {
        toolbar = findViewById(R.id.toolbar);
        bottomNavigation = findViewById(R.id.bottom_navigation);
        fabAddChild = findViewById(R.id.fab_add_child);
        fragmentManager = getSupportFragmentManager();
    }

    private void setupToolbar() {
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayShowTitleEnabled(false);
        }
        // Set overflow menu icon color to white for visibility on cyan toolbar
        toolbar.setOverflowIcon(getDrawable(R.drawable.ic_more_vert));
        if (toolbar.getOverflowIcon() != null) {
            toolbar.getOverflowIcon().setTint(getResources().getColor(android.R.color.white, getTheme()));
        }
        // Set popup theme for overflow menu
        toolbar.setPopupTheme(R.style.AppTheme_PopupOverlay);
    }

    private void setupBottomNavigation() {
        bottomNavigation.setOnItemSelectedListener(item -> {
            int id = item.getItemId();

            if (id == R.id.nav_children) {
                // Children List Fragment
                loadFragment(new ChildrenListFragment());
                fabAddChild.show();
                return true;

            } else if (id == R.id.nav_alerts) {
                // Navigate to Alerts Activity
                Intent intent = new Intent(this, AlertsActivity.class);
                startActivity(intent);
                // Don't change fragment, keep children selected
                return false;

            } else if (id == R.id.nav_reports) {
                // Navigate to Reports Activity
                Intent intent = new Intent(this, ReportsActivity.class);
                startActivity(intent);
                // Don't change fragment, keep children selected
                return false;

            } else if (id == R.id.nav_profile) {
                // Navigate to Parent Profile Activity
                Intent intent = new Intent(this, ParentProfileActivity.class);
                startActivity(intent);
                // Don't change fragment, keep children selected
                return false;
            }

            return false;
        });

        // Reselect children when returning from other activities
        bottomNavigation.setOnItemReselectedListener(item -> {
            // Do nothing on reselect
        });
    }

    private void setupFAB() {
        fabAddChild.setOnClickListener(v -> {
            Intent intent = new Intent(this, AddChildActivity.class);
            startActivity(intent);
        });
    }

    /**
     * Setup badge on alerts icon to show unread count
     */
    private void setupAlertsBadge() {
        String parentId = prefsHelper.getUserId();
        if (parentId == null || parentId.isEmpty()) {
            return;
        }

        // Listen for alerts changes
        alertsListener = FirebaseHelper.getAlertsRef()
                .orderByChild("parentId")
                .equalTo(parentId)
                .addValueEventListener(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        int unreadCount = 0;

                        for (DataSnapshot alertSnapshot : snapshot.getChildren()) {
                            Alert alert = alertSnapshot.getValue(Alert.class);
                            if (alert != null && !alert.isRead()) {
                                unreadCount++;
                            }
                        }

                        updateAlertsBadge(unreadCount);
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        // Handle error silently
                    }
                });
    }

    /**
     * Update the badge on alerts icon
     */
    private void updateAlertsBadge(int count) {
        BadgeDrawable badge = bottomNavigation.getOrCreateBadge(R.id.nav_alerts);
        
        if (count > 0) {
            badge.setVisible(true);
            badge.setNumber(count);
            badge.setBackgroundColor(getColor(R.color.error));
        } else {
            badge.setVisible(false);
            badge.clearNumber();
        }
    }

    // ============ PHASE 5: NAVIGATION METHODS ============

    /**
     * Navigate to Activity Logs for a specific child
     */
    public void openActivityLogs(String childUid) {
        if (childUid == null || childUid.isEmpty()) {
            Toast.makeText(this, "Child data not found", Toast.LENGTH_SHORT).show();
            return;
        }

        Intent intent = new Intent(this, ActivityLogsActivity.class);
        intent.putExtra("childUid", childUid);
        startActivity(intent);
    }

    /**
     * Navigate to Screen Time Settings for a specific child
     */
    public void openScreenTimeSettings(String childUid) {
        if (childUid == null || childUid.isEmpty()) {
            Toast.makeText(this, "Child data not found", Toast.LENGTH_SHORT).show();
            return;
        }

        Intent intent = new Intent(this, ScreenTimeSettingsActivity.class);
        intent.putExtra("childUid", childUid);
        startActivity(intent);
    }

    /**
     * Navigate to Content Filter for a specific child
     */
    public void openContentFilter(String childUid) {
        if (childUid == null || childUid.isEmpty()) {
            Toast.makeText(this, "Child data not found", Toast.LENGTH_SHORT).show();
            return;
        }

        Intent intent = new Intent(this, ContentFilterActivity.class);
        intent.putExtra("childUid", childUid);
        startActivity(intent);
    }

    /**
     * Navigate to Location Map for a specific child
     */
    public void openLocationMap(String childUid) {
        if (childUid == null || childUid.isEmpty()) {
            Toast.makeText(this, "Child data not found", Toast.LENGTH_SHORT).show();
            return;
        }

        FirebaseHelper.getUserRef(childUid).addListenerForSingleValueEvent(
                new com.google.firebase.database.ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull com.google.firebase.database.DataSnapshot snapshot) {
                        if (snapshot.exists()) {
                            String childName = snapshot.child("name").getValue(String.class);

                            Intent intent = new Intent(ParentDashboardActivity.this, LocationMapActivity.class);
                            intent.putExtra("childUid", childUid);
                            intent.putExtra("childName", childName);
                            startActivity(intent);
                        } else {
                            Toast.makeText(ParentDashboardActivity.this, "Child data not found", Toast.LENGTH_SHORT).show();
                        }
                    }

                    @Override
                    public void onCancelled(@NonNull com.google.firebase.database.DatabaseError error) {
                        Toast.makeText(ParentDashboardActivity.this, "Error loading child data", Toast.LENGTH_SHORT).show();
                    }
                }
        );
    }

    /**
     * Navigate to Alerts for a specific child
     */
    public void openAlerts(String childUid) {
        Intent intent = new Intent(this, AlertsActivity.class);
        intent.putExtra("childUid", childUid);
        startActivity(intent);
    }

    // ============ FRAGMENT MANAGEMENT ============

    private void loadFragment(Fragment fragment) {
        fragmentManager.beginTransaction()
                .replace(R.id.fragment_container, fragment)
                .commit();
    }

    // ============ TOOLBAR MENU ============

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.menu_parent_dashboard, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();

        if (id == R.id.action_settings) {
            // Open Settings Activity
            Intent intent = new Intent(this, SettingsActivity.class);
            startActivity(intent);
            return true;

        } else if (id == R.id.action_logout) {
            handleLogout();
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    // ============ LOGOUT ============

    private void handleLogout() {
        new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                .setTitle("Logout")
                .setMessage("Are you sure you want to logout?")
                .setPositiveButton("Yes", (dialog, which) -> {
                    // Stop notification service
                    stopService(new Intent(this, com.safezone.app.services.ParentNotificationService.class));
                    
                    FirebaseHelper.signOut();
                    prefsHelper.clearSession();
                    navigateToLogin();
                })
                .setNegativeButton("No", null)
                .show();
    }

    private void navigateToLogin() {
        Intent intent = new Intent(this, LoginActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    // ============ BACK PRESS ============

    @Override
    public void onBackPressed() {
        new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                .setTitle("Exit App")
                .setMessage("Are you sure you want to exit?")
                .setPositiveButton("Yes", (dialog, which) -> {
                    super.onBackPressed();
                    finishAffinity();
                })
                .setNegativeButton("No", null)
                .show();
    }

    // ============ LIFECYCLE ============

    @Override
    protected void onResume() {
        super.onResume();
        // Ensure Children tab is selected when returning
        bottomNavigation.setSelectedItemId(R.id.nav_children);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Remove alerts listener to prevent memory leaks
        if (alertsListener != null) {
            String parentId = prefsHelper.getUserId();
            if (parentId != null && !parentId.isEmpty()) {
                FirebaseHelper.getAlertsRef()
                        .orderByChild("parentId")
                        .equalTo(parentId)
                        .removeEventListener(alertsListener);
            }
        }
    }
}