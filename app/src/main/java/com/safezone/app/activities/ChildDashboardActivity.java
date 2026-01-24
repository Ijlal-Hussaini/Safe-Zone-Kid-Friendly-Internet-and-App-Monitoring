package com.safezone.app.activities;

import android.app.ActivityManager;
import android.app.AppOpsManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Log;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.firebase.database.DatabaseReference;
import com.safezone.app.R;
import com.safezone.app.fragments.UsageOverviewFragment;
import com.safezone.app.receivers.ServiceRestartReceiver;
import com.safezone.app.services.ActivityMonitorService;
import com.safezone.app.services.ScreenTimeEnforcerService;
import com.safezone.app.utils.FirebaseHelper;
import com.safezone.app.utils.SharedPrefsHelper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Main Child Dashboard with bottom navigation
 * UPDATED: Now starts Phase 5 monitoring services
 */
public class ChildDashboardActivity extends AppCompatActivity {

    private static final String TAG = "ChildDashboard";
    private static final int QR_SCANNER_REQUEST = 100;
    private static final int REQUEST_LOCATION_PERMISSION = 101;

    private MaterialToolbar toolbar;
    private BottomNavigationView bottomNavigation;
    private FloatingActionButton fabScanQr;
    private FragmentManager fragmentManager;

    private SharedPrefsHelper prefsHelper;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_child_dashboard);

        prefsHelper = new SharedPrefsHelper(this);

        // Verify user is logged in and is child
        if (!prefsHelper.isLoggedIn() || !prefsHelper.isChild()) {
            navigateToLogin();
            return;
        }

        initViews();
        setupToolbar();
        setupBottomNavigation();
        setupFAB();
        checkLinkStatus(); // Check if already linked

        // ============ PHASE 5: START MONITORING SERVICES ============
        startMonitoringServices();

        // ============ PHASE 6: START LOCATION TRACKING ============
        startLocationTracking();

        // ============ PHASE 8: START BLOCKING SERVICES ============
        startBlockingServices();
        
        // ============ SERVICE WATCHDOG: ENSURE SERVICES STAY RUNNING ============
        startServiceWatchdog();
        
        // ============ UPLOAD INSTALLED APPS TO FIREBASE ============
        uploadInstalledAppsToFirebase();
        
        // Check if permissions are granted, if not show setup screen
        // But allow if user has skipped permissions this session
        if (!allPermissionsGranted() && !prefsHelper.hasPermissionsSkippedThisSession()) {
            showPermissionsSetup();
            return;
        }

        // Load default fragment (Usage Overview)
        if (savedInstanceState == null) {
            loadFragment(new UsageOverviewFragment());
            bottomNavigation.setSelectedItemId(R.id.nav_children);
        }
        
        // Handle intent actions from blocking dialogs
        handleIntentAction(getIntent());
    }
    
    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        handleIntentAction(intent);
    }
    
    /**
     * Handle actions from blocking dialogs
     */
    private void handleIntentAction(Intent intent) {
        if (intent == null) return;
        
        String action = intent.getStringExtra("action");
        if (action == null) return;
        
        switch (action) {
            case "request_time":
                // Show request time dialog
                showRequestTimeDialog();
                break;
            case "request_access":
                // Navigate to Request Access Activity
                Intent accessIntent = new Intent(this, RequestAccessActivity.class);
                startActivity(accessIntent);
                break;
        }
        
        // Clear the action to prevent re-triggering
        intent.removeExtra("action");
    }
    
    /**
     * Check if all required permissions are granted
     */
    private boolean allPermissionsGranted() {
        return isAccessibilityServiceEnabled() &&
               hasUsageStatsPermission() &&
               hasOverlayPermission() &&
               isDeviceAdminEnabled();
    }
    
    /**
     * Show permissions setup screen
     */
    private void showPermissionsSetup() {
        Intent intent = new Intent(this, com.safezone.app.activities.PermissionsSetupActivity.class);
        startActivity(intent);
    }

    // ============ PHASE 5: MONITORING SERVICES ============

    /**
     * Start both monitoring services for activity tracking and screen time enforcement
     * Only starts if permissions are already granted (handled by PermissionsSetupActivity)
     */
    private void startMonitoringServices() {
        if (hasUsageStatsPermission()) {
            // Permission granted - start services
            startActivityMonitoring();
            startScreenTimeEnforcement();
            Log.d(TAG, "Monitoring services started successfully");
        } else {
            Log.d(TAG, "Usage stats permission not granted - services not started");
        }
    }
    
    /**
     * Check if services are already running
     */
    private boolean isServiceRunning(Class<?> serviceClass) {
        ActivityManager manager = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        if (manager != null) {
            for (ActivityManager.RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
                if (serviceClass.getName().equals(service.service.getClassName())) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Start Activity Monitor Service (tracks app usage)
     */
    private void startActivityMonitoring() {
        // Check if service is already running
        if (isServiceRunning(ActivityMonitorService.class)) {
            Log.d(TAG, "ActivityMonitorService already running");
            return;
        }
        
        try {
            Intent serviceIntent = new Intent(this, ActivityMonitorService.class);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent);
            } else {
                startService(serviceIntent);
            }
            Log.d(TAG, "ActivityMonitorService started");
        } catch (Exception e) {
            Log.e(TAG, "Error starting ActivityMonitorService: " + e.getMessage(), e);
        }
    }

    /**
     * Start Screen Time Enforcer Service (enforces daily limits)
     */
    private void startScreenTimeEnforcement() {
        // Check if service is already running
        if (isServiceRunning(ScreenTimeEnforcerService.class)) {
            Log.d(TAG, "ScreenTimeEnforcerService already running");
            return;
        }
        
        try {
            Intent serviceIntent = new Intent(this, ScreenTimeEnforcerService.class);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent);
            } else {
                startService(serviceIntent);
            }
            Log.d(TAG, "ScreenTimeEnforcerService started");
        } catch (Exception e) {
            Log.e(TAG, "Error starting ScreenTimeEnforcerService: " + e.getMessage(), e);
        }
    }

    // ============ PHASE 8: BLOCKING SERVICES ============

    /**
     * Start app and website blocking services
     * Only starts if permissions are already granted (handled by PermissionsSetupActivity)
     */
    private void startBlockingServices() {
        if (hasUsageStatsPermission()) {
            startAppBlocking();
        }
        // Note: Accessibility, overlay, and device admin permissions are now
        // handled by PermissionsSetupActivity - no dialogs shown here
    }

    // ============ SERVICE WATCHDOG ============

    /**
     * Start service watchdog to ensure services stay running
     * This schedules periodic checks to restart services if they are killed
     */
    private void startServiceWatchdog() {
        try {
            ServiceRestartReceiver.scheduleServiceWatchdog(this);
            Log.d(TAG, "✅ Service watchdog started");
        } catch (Exception e) {
            Log.e(TAG, "Error starting service watchdog: " + e.getMessage(), e);
        }
    }

    /**
     * Start App Blocking Service (blocks restricted apps in real-time)
     */
    private void startAppBlocking() {
        // Check if service is already running
        if (isServiceRunning(com.safezone.app.services.AppBlockingService.class)) {
            Log.d(TAG, "AppBlockingService already running");
            return;
        }
        
        try {
            Intent serviceIntent = new Intent(this, com.safezone.app.services.AppBlockingService.class);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent);
            } else {
                startService(serviceIntent);
            }
            Log.d(TAG, "AppBlockingService started");
        } catch (Exception e) {
            Log.e(TAG, "Error starting AppBlockingService: " + e.getMessage(), e);
        }
    }

    /**
     * Check if Accessibility Service is enabled (for both website and app blocking)
     */
    private boolean isAccessibilityServiceEnabled() {
        String websiteService = getPackageName() + "/" + com.safezone.app.services.WebsiteBlockingService.class.getName();
        String appService = getPackageName() + "/" + com.safezone.app.services.AppBlockingAccessibilityService.class.getName();
        
        try {
            int accessibilityEnabled = Settings.Secure.getInt(
                    getContentResolver(),
                    Settings.Secure.ACCESSIBILITY_ENABLED
            );
            
            if (accessibilityEnabled == 1) {
                String settingValue = Settings.Secure.getString(
                        getContentResolver(),
                        Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
                );
                
                if (settingValue != null) {
                    // Check if either service is enabled
                    return settingValue.contains(websiteService) || settingValue.contains(appService);
                }
            }
        } catch (Settings.SettingNotFoundException e) {
            Log.e(TAG, "Error checking accessibility: " + e.getMessage());
        }
        return false;
    }



    /**
     * Check if overlay permission is granted
     */
    private boolean hasOverlayPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return Settings.canDrawOverlays(this);
        }
        return true;
    }



    /**
     * Check if device admin is enabled
     */
    private boolean isDeviceAdminEnabled() {
        android.app.admin.DeviceAdminReceiver deviceAdminReceiver = new com.safezone.app.receivers.SafeZoneDeviceAdminReceiver();
        android.app.admin.DevicePolicyManager devicePolicyManager = 
            (android.app.admin.DevicePolicyManager) getSystemService(Context.DEVICE_POLICY_SERVICE);
        android.content.ComponentName componentName = new android.content.ComponentName(this, deviceAdminReceiver.getClass());
        
        return devicePolicyManager != null && devicePolicyManager.isAdminActive(componentName);
    }



    /**
     * Check if Usage Stats permission is granted
     */
    private boolean hasUsageStatsPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            try {
                AppOpsManager appOps = (AppOpsManager) getSystemService(Context.APP_OPS_SERVICE);
                int mode = appOps.checkOpNoThrow(
                        AppOpsManager.OPSTR_GET_USAGE_STATS,
                        android.os.Process.myUid(),
                        getPackageName()
                );
                return mode == AppOpsManager.MODE_ALLOWED;
            } catch (Exception e) {
                Log.e(TAG, "Error checking permission: " + e.getMessage(), e);
                return false;
            }
        }
        return true;
    }



    // ============ PHASE 6: LOCATION TRACKING ============

    /**
     * Start Location Tracking Service
     */
    private void startLocationTracking() {
        if (hasLocationPermission()) {
            // Check if service is already running
            if (isServiceRunning(com.safezone.app.services.LocationTrackingService.class)) {
                Log.d(TAG, "LocationTrackingService already running");
                return;
            }
            
            try {
                Intent serviceIntent = new Intent(this, com.safezone.app.services.LocationTrackingService.class);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    startForegroundService(serviceIntent);
                } else {
                    startService(serviceIntent);
                }
                Log.d(TAG, "LocationTrackingService started");
            } catch (Exception e) {
                Log.e(TAG, "Error starting LocationTrackingService: " + e.getMessage(), e);
            }
        } else {
            requestLocationPermission();
        }
    }

    /**
     * Check if location permission is granted
     */
    private boolean hasLocationPermission() {
        return androidx.core.app.ActivityCompat.checkSelfPermission(this,
                android.Manifest.permission.ACCESS_FINE_LOCATION)
                == android.content.pm.PackageManager.PERMISSION_GRANTED;
    }

    /**
     * Request location permission
     */
    private void requestLocationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            requestPermissions(
                    new String[]{
                            android.Manifest.permission.ACCESS_FINE_LOCATION,
                            android.Manifest.permission.ACCESS_COARSE_LOCATION
                    },
                    REQUEST_LOCATION_PERMISSION
            );
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @androidx.annotation.NonNull String[] permissions,
                                           @androidx.annotation.NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == REQUEST_LOCATION_PERMISSION) {
            if (grantResults.length > 0 && grantResults[0] == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                Log.d(TAG, "Location permission granted");
                startLocationTracking();
            } else {
                Toast.makeText(this,
                        "Location permission is required for safety tracking",
                        Toast.LENGTH_LONG).show();
            }
        }
    }

    // ============ UPLOAD INSTALLED APPS TO FIREBASE ============

    /**
     * Upload child's installed apps to Firebase so parent can see them
     * This runs on a background thread to avoid blocking UI
     */
    private void uploadInstalledAppsToFirebase() {
        new Thread(() -> {
            try {
                String childUid = FirebaseHelper.getCurrentUserId();
                if (childUid == null) return;

                PackageManager pm = getPackageManager();
                List<ApplicationInfo> installedApps = pm.getInstalledApplications(PackageManager.GET_META_DATA);
                
                List<Map<String, String>> appsList = new ArrayList<>();
                
                for (ApplicationInfo app : installedApps) {
                    // Only include launchable apps (exclude system services)
                    if (pm.getLaunchIntentForPackage(app.packageName) != null) {
                        // Exclude Safe Zone itself
                        if (!app.packageName.equals(getPackageName())) {
                            Map<String, String> appInfo = new HashMap<>();
                            appInfo.put("packageName", app.packageName);
                            appInfo.put("appName", pm.getApplicationLabel(app).toString());
                            appsList.add(appInfo);
                        }
                    }
                }
                
                // Sort by app name
                appsList.sort((a, b) -> a.get("appName").compareToIgnoreCase(b.get("appName")));
                
                // Upload to Firebase
                FirebaseHelper.getUsersRef().child(childUid)
                        .child("installedApps")
                        .setValue(appsList)
                        .addOnSuccessListener(aVoid -> {
                            Log.d(TAG, "Uploaded " + appsList.size() + " installed apps to Firebase");
                        })
                        .addOnFailureListener(e -> {
                            Log.e(TAG, "Failed to upload installed apps: " + e.getMessage());
                        });
                        
            } catch (Exception e) {
                Log.e(TAG, "Error uploading installed apps: " + e.getMessage(), e);
            }
        }).start();
    }

    // ============ EXISTING CODE (UNCHANGED) ============

    private void checkLinkStatus() {
        // Check if child is already linked to parent
        DatabaseReference userRef = FirebaseHelper.getUsersRef()
                .child(FirebaseHelper.getCurrentUserId());

        userRef.addListenerForSingleValueEvent(new com.google.firebase.database.ValueEventListener() {
            @Override
            public void onDataChange(@androidx.annotation.NonNull com.google.firebase.database.DataSnapshot snapshot) {
                if (snapshot.exists()) {
                    String parentId = snapshot.child("parentId").getValue(String.class);
                    if (parentId != null && !parentId.isEmpty()) {
                        // Already linked - hide FAB
                        fabScanQr.hide();
                    } else {
                        // Not linked - show FAB
                        fabScanQr.show();
                    }
                }
            }

            @Override
            public void onCancelled(@androidx.annotation.NonNull com.google.firebase.database.DatabaseError error) {
                // Error - show FAB by default
                fabScanQr.show();
            }
        });
    }

    private void initViews() {
        toolbar = findViewById(R.id.toolbar);
        bottomNavigation = findViewById(R.id.bottom_navigation);
        fabScanQr = findViewById(R.id.fab_scan_qr);
        fragmentManager = getSupportFragmentManager();
    }

    private void setupToolbar() {
        setSupportActionBar(toolbar);
        
        // Remove default title to avoid duplication with custom layout
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayShowTitleEnabled(false);
        }
        
        // Set overflow icon (three dots) to white for visibility
        toolbar.setOverflowIcon(getDrawable(R.drawable.ic_more_vert));
        if (toolbar.getOverflowIcon() != null) {
            toolbar.getOverflowIcon().setTint(getResources().getColor(android.R.color.white, getTheme()));
        }
        
        // Set popup theme for overflow menu
        toolbar.setPopupTheme(R.style.AppTheme_PopupOverlay);
    }

    @Override
    public boolean onCreateOptionsMenu(android.view.Menu menu) {
        getMenuInflater().inflate(R.menu.menu_child_dashboard, menu);
        
        // Ensure menu icons are white for visibility on colored toolbar
        for (int i = 0; i < menu.size(); i++) {
            android.view.MenuItem item = menu.getItem(i);
            android.graphics.drawable.Drawable icon = item.getIcon();
            if (icon != null) {
                icon.setTint(getResources().getColor(android.R.color.white, getTheme()));
            }
        }
        
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(android.view.MenuItem item) {
        int id = item.getItemId();

        if (id == R.id.action_settings) {
            // Open Settings Activity
            Intent intent = new Intent(this, SettingsActivity.class);
            startActivity(intent);
            return true;
        } else if (id == R.id.action_logout) {
            showPasswordConfirmationForLogout();
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    private void setupBottomNavigation() {
        bottomNavigation.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            Fragment fragment = null;

            if (id == R.id.nav_children) {
                fragment = new UsageOverviewFragment();
            } else if (id == R.id.nav_request_time) {
                // Request more time functionality
                showRequestTimeDialog();
                return false; // Don't change selection
            } else if (id == R.id.nav_request_access) {
                // Navigate to Request Access Activity
                Intent intent = new Intent(this, RequestAccessActivity.class);
                startActivity(intent);
                return false; // Don't change selection
            } else if (id == R.id.nav_profile) {
                // Navigate to Child Profile Activity
                Intent intent = new Intent(this, ChildProfileActivity.class);
                startActivity(intent);
                return false; // Don't change selection
            }

            if (fragment != null) {
                loadFragment(fragment);
            }

            return true;
        });
    }

    private void setupFAB() {
        fabScanQr.setOnClickListener(v -> openQRScanner());
    }

    /**
     * Show dialog to request more screen time from parent
     */
    private void showRequestTimeDialog() {
        String currentUserId = FirebaseHelper.getCurrentUserId();
        
        // Get child's parent ID
        FirebaseHelper.getUserRef(currentUserId).addListenerForSingleValueEvent(
                new com.google.firebase.database.ValueEventListener() {
                    @Override
                    public void onDataChange(@androidx.annotation.NonNull com.google.firebase.database.DataSnapshot snapshot) {
                        if (snapshot.exists()) {
                            String parentId = snapshot.child("parentId").getValue(String.class);
                            String childName = snapshot.child("name").getValue(String.class);
                            
                            if (parentId == null || parentId.isEmpty()) {
                                Toast.makeText(ChildDashboardActivity.this,
                                        "You're not linked to a parent",
                                        Toast.LENGTH_SHORT).show();
                                return;
                            }
                            
                            // Show time selection dialog
                            showTimeSelectionDialog(parentId, childName);
                        }
                    }

                    @Override
                    public void onCancelled(@androidx.annotation.NonNull com.google.firebase.database.DatabaseError error) {
                        Toast.makeText(ChildDashboardActivity.this,
                                "Error: " + error.getMessage(),
                                Toast.LENGTH_SHORT).show();
                    }
                }
        );
    }

    /**
     * Show dialog to select how much time to request
     */
    private void showTimeSelectionDialog(String parentId, String childName) {
        // Create custom dialog with enhanced UI
        android.view.View dialogView = getLayoutInflater().inflate(R.layout.dialog_request_time, null);
        
        com.google.android.material.dialog.MaterialAlertDialogBuilder builder = 
            new com.google.android.material.dialog.MaterialAlertDialogBuilder(this, R.style.ThemeOverlay_App_MaterialAlertDialog);
        builder.setView(dialogView);
        builder.setBackground(getResources().getDrawable(R.drawable.dialog_background, getTheme()));
        
        androidx.appcompat.app.AlertDialog dialog = builder.create();
        
        // Set up click listeners for time options
        dialogView.findViewById(R.id.option_15_min).setOnClickListener(v -> {
            sendTimeRequest(parentId, childName, 15);
            dialog.dismiss();
        });
        
        dialogView.findViewById(R.id.option_30_min).setOnClickListener(v -> {
            sendTimeRequest(parentId, childName, 30);
            dialog.dismiss();
        });
        
        dialogView.findViewById(R.id.option_1_hour).setOnClickListener(v -> {
            sendTimeRequest(parentId, childName, 60);
            dialog.dismiss();
        });
        
        dialogView.findViewById(R.id.option_2_hours).setOnClickListener(v -> {
            sendTimeRequest(parentId, childName, 120);
            dialog.dismiss();
        });
        
        dialogView.findViewById(R.id.btn_cancel).setOnClickListener(v -> dialog.dismiss());
        
        dialog.show();
    }

    /**
     * Send time request to parent as an alert
     */
    private void sendTimeRequest(String parentId, String childName, int minutes) {
        String currentUserId = FirebaseHelper.getCurrentUserId();
        
        // Create alert for parent
        String alertId = FirebaseHelper.getDatabase().getReference()
                .child("alerts").push().getKey();
        
        if (alertId == null) return;
        
        com.safezone.app.models.Alert alert = new com.safezone.app.models.Alert(
                alertId,
                parentId,
                currentUserId,
                childName,
                com.safezone.app.models.Alert.Type.REQUEST,
                childName + " is requesting more screen time",
                "Requested: " + minutes + " minutes",
                System.currentTimeMillis()
        );
        
        FirebaseHelper.getDatabase().getReference()
                .child("alerts")
                .child(alertId)
                .setValue(alert)
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(this,
                            "Request sent to parent!",
                            Toast.LENGTH_SHORT).show();
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this,
                            "Failed to send request: " + e.getMessage(),
                            Toast.LENGTH_SHORT).show();
                });
    }

    private void openQRScanner() {
        Intent intent = new Intent(this, QRScannerActivity.class);
        startActivityForResult(intent, QR_SCANNER_REQUEST);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == QR_SCANNER_REQUEST && resultCode == RESULT_OK) {
            // Successfully linked, refresh current fragment
            Toast.makeText(this, "Successfully linked to parent!", Toast.LENGTH_SHORT).show();

            // Hide FAB after successful link
            fabScanQr.hide();

            // Reload current fragment
            Fragment currentFragment = fragmentManager.findFragmentById(R.id.fragment_container);
            if (currentFragment != null) {
                fragmentManager.beginTransaction()
                        .detach(currentFragment)
                        .attach(currentFragment)
                        .commit();
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Only check and start services if permission was just granted
        // Don't show dialog again if already denied
        if (hasUsageStatsPermission()) {
            startMonitoringServices();
        }
        if (hasLocationPermission()) {
            startLocationTracking();
        }
        // Refresh link status (in case user disconnected from parent in settings)
        checkLinkStatus();
    }

    private void loadFragment(Fragment fragment) {
        fragmentManager.beginTransaction()
                .replace(R.id.fragment_container, fragment)
                .commit();
    }

    /**
     * Show password confirmation dialog before logout
     */
    private void showPasswordConfirmationForLogout() {
        android.view.View dialogView = getLayoutInflater().inflate(R.layout.dialog_confirm_password, null);
        com.google.android.material.textfield.TextInputEditText etPassword = dialogView.findViewById(R.id.et_password);

        new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                .setTitle("Confirm Password")
                .setMessage("Enter your password to logout")
                .setView(dialogView)
                .setPositiveButton("Confirm", (dialog, which) -> {
                    String password = etPassword.getText().toString().trim();
                    if (password.isEmpty()) {
                        Toast.makeText(this, "Please enter password", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    verifyPasswordAndLogout(password);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    /**
     * Verify password and logout if correct
     */
    private void verifyPasswordAndLogout(String password) {
        com.google.firebase.auth.FirebaseUser user = FirebaseHelper.getCurrentUser();
        if (user == null || user.getEmail() == null) {
            Toast.makeText(this, "User not found", Toast.LENGTH_SHORT).show();
            return;
        }

        android.app.ProgressDialog progressDialog = new android.app.ProgressDialog(this);
        progressDialog.setMessage("Verifying...");
        progressDialog.setCancelable(false);
        progressDialog.show();

        com.google.firebase.auth.AuthCredential credential = 
            com.google.firebase.auth.EmailAuthProvider.getCredential(user.getEmail(), password);

        user.reauthenticate(credential)
                .addOnSuccessListener(aVoid -> {
                    progressDialog.dismiss();
                    handleLogout();
                })
                .addOnFailureListener(e -> {
                    progressDialog.dismiss();
                    Toast.makeText(this, "Incorrect password", Toast.LENGTH_SHORT).show();
                });
    }

    private void handleLogout() {
        // Stop services before logout
        stopMonitoringServices();

        // Sign out from Firebase
        FirebaseHelper.signOut();

        // Clear session
        prefsHelper.clearSession();

        // Navigate to login
        navigateToLogin();
    }

    private void stopMonitoringServices() {
        try {
            stopService(new Intent(this, ActivityMonitorService.class));
            stopService(new Intent(this, ScreenTimeEnforcerService.class));
            stopService(new Intent(this, com.safezone.app.services.LocationTrackingService.class));
            Log.d(TAG, "All monitoring services stopped");
        } catch (Exception e) {
            Log.e(TAG, "Error stopping services: " + e.getMessage(), e);
        }
    }

    private void navigateToLogin() {
        Intent intent = new Intent(this, LoginActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    @Override
    public void onBackPressed() {
        // Ask for confirmation before exiting
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

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Services will continue running in background even after activity is destroyed
        // This is intentional for monitoring to work properly
    }
}
