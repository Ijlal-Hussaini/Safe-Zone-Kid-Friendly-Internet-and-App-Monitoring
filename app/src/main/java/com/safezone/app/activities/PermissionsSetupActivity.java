package com.safezone.app.activities;

import android.app.AppOpsManager;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;

import com.google.android.material.appbar.MaterialToolbar;
import com.safezone.app.R;
import com.safezone.app.receivers.SafeZoneDeviceAdminReceiver;
import com.safezone.app.utils.SharedPrefsHelper;

/**
 * Professional permissions setup screen
 * Guides users through all required permissions with direct links
 */
public class PermissionsSetupActivity extends AppCompatActivity {

    private static final String TAG = "PermissionsSetup";
    
    private MaterialToolbar toolbar;
    private CardView cardAccessibility;
    private CardView cardUsageAccess;
    private CardView cardOverlay;
    private CardView cardDeviceAdmin;
    private CardView cardNotifications;
    
    private ImageView iconAccessibility;
    private ImageView iconUsageAccess;
    private ImageView iconOverlay;
    private ImageView iconDeviceAdmin;
    private ImageView iconNotifications;
    
    private TextView statusAccessibility;
    private TextView statusUsageAccess;
    private TextView statusOverlay;
    private TextView statusDeviceAdmin;
    private TextView statusNotifications;
    
    private Button btnContinue;
    private Button btnSkip;
    
    private SharedPrefsHelper prefsHelper;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_permissions_setup);

        prefsHelper = new SharedPrefsHelper(this);
        
        initViews();
        setupToolbar();
        setupClickListeners();
    }

    @Override
    protected void onResume() {
        super.onResume();
        updatePermissionStatus();
    }

    private void initViews() {
        toolbar = findViewById(R.id.toolbar);
        
        cardAccessibility = findViewById(R.id.card_accessibility);
        cardUsageAccess = findViewById(R.id.card_usage_access);
        cardOverlay = findViewById(R.id.card_overlay);
        cardDeviceAdmin = findViewById(R.id.card_device_admin);
        cardNotifications = findViewById(R.id.card_notifications);
        
        iconAccessibility = findViewById(R.id.icon_accessibility);
        iconUsageAccess = findViewById(R.id.icon_usage_access);
        iconOverlay = findViewById(R.id.icon_overlay);
        iconDeviceAdmin = findViewById(R.id.icon_device_admin);
        iconNotifications = findViewById(R.id.icon_notifications);
        
        statusAccessibility = findViewById(R.id.status_accessibility);
        statusUsageAccess = findViewById(R.id.status_usage_access);
        statusOverlay = findViewById(R.id.status_overlay);
        statusDeviceAdmin = findViewById(R.id.status_device_admin);
        statusNotifications = findViewById(R.id.status_notifications);
        
        btnContinue = findViewById(R.id.btn_continue);
        btnSkip = findViewById(R.id.btn_skip);
        
        // Set role-specific description
        TextView tvDescription = findViewById(R.id.tv_description);
        if (prefsHelper.isParent()) {
            tvDescription.setText("Grant notification permission to receive alerts from your children");
        } else {
            tvDescription.setText("Safe Zone needs these permissions to protect you and monitor your device");
        }
    }

    private void setupToolbar() {
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayShowTitleEnabled(false);
        }
        
        // Set color based on user role
        if (prefsHelper.isChild()) {
            toolbar.setBackgroundColor(getResources().getColor(R.color.secondary, getTheme()));
        } else {
            toolbar.setBackgroundColor(getResources().getColor(R.color.primary, getTheme()));
        }
    }

    private void setupClickListeners() {
        cardAccessibility.setOnClickListener(v -> openAccessibilitySettings());
        cardUsageAccess.setOnClickListener(v -> openUsageAccessSettings());
        cardOverlay.setOnClickListener(v -> openOverlaySettings());
        cardDeviceAdmin.setOnClickListener(v -> openDeviceAdminSettings());
        cardNotifications.setOnClickListener(v -> openNotificationSettings());
        
        btnContinue.setOnClickListener(v -> {
            if (allPermissionsGranted()) {
                navigateToDashboard();
            } else {
                showMissingPermissionsDialog();
            }
        });
        
        // Skip button - allows user to proceed without all permissions
        // Permissions page will show again on next app restart
        btnSkip.setOnClickListener(v -> {
            showSkipConfirmationDialog();
        });
    }
    
    /**
     * Show confirmation dialog when user wants to skip permissions
     */
    private void showSkipConfirmationDialog() {
        // Create custom dialog with app styling
        android.view.View dialogView = getLayoutInflater().inflate(R.layout.dialog_skip_permissions, null);
        
        com.google.android.material.dialog.MaterialAlertDialogBuilder builder = 
            new com.google.android.material.dialog.MaterialAlertDialogBuilder(this, R.style.ThemeOverlay_App_MaterialAlertDialog);
        builder.setView(dialogView);
        builder.setBackground(getResources().getDrawable(R.drawable.dialog_background, getTheme()));
        
        androidx.appcompat.app.AlertDialog dialog = builder.create();
        
        // Set up button click listeners
        dialogView.findViewById(R.id.btn_grant).setOnClickListener(v -> {
            dialog.dismiss();
        });
        
        dialogView.findViewById(R.id.btn_skip).setOnClickListener(v -> {
            dialog.dismiss();
            navigateToDashboard(true);
        });
        
        dialog.show();
    }

    /**
     * Update permission status indicators
     */
    private void updatePermissionStatus() {
        if (prefsHelper.isParent()) {
            // PARENT: Only needs notification permission
            cardAccessibility.setVisibility(View.GONE);
            cardUsageAccess.setVisibility(View.GONE);
            cardOverlay.setVisibility(View.GONE);
            cardDeviceAdmin.setVisibility(View.GONE);
            cardNotifications.setVisibility(View.VISIBLE);
            
            boolean hasNotifications = hasNotificationPermission();
            updatePermissionCard(iconNotifications, statusNotifications, hasNotifications);
        } else {
            // CHILD: Needs all monitoring permissions
            cardAccessibility.setVisibility(View.VISIBLE);
            cardUsageAccess.setVisibility(View.VISIBLE);
            cardOverlay.setVisibility(View.VISIBLE);
            cardDeviceAdmin.setVisibility(View.VISIBLE);
            cardNotifications.setVisibility(View.GONE);
            
            boolean hasAccessibility = isAccessibilityServiceEnabled();
            updatePermissionCard(iconAccessibility, statusAccessibility, hasAccessibility);
            
            boolean hasUsageAccess = hasUsageStatsPermission();
            updatePermissionCard(iconUsageAccess, statusUsageAccess, hasUsageAccess);
            
            boolean hasOverlay = hasOverlayPermission();
            updatePermissionCard(iconOverlay, statusOverlay, hasOverlay);
            
            boolean hasDeviceAdmin = isDeviceAdminEnabled();
            updatePermissionCard(iconDeviceAdmin, statusDeviceAdmin, hasDeviceAdmin);
        }
        
        // Update continue button
        if (allPermissionsGranted()) {
            btnContinue.setText("Continue to Dashboard");
            btnContinue.setEnabled(true);
        } else {
            btnContinue.setText("Grant All Permissions First");
            btnContinue.setEnabled(false);
        }
    }

    private void updatePermissionCard(ImageView icon, TextView status, boolean granted) {
        if (granted) {
            icon.setImageResource(R.drawable.ic_check_circle);
            icon.setColorFilter(getResources().getColor(R.color.success, getTheme()));
            status.setText("Granted");
            status.setTextColor(getResources().getColor(R.color.success, getTheme()));
        } else {
            icon.setImageResource(R.drawable.ic_error);
            icon.setColorFilter(getResources().getColor(R.color.error, getTheme()));
            status.setText("Required");
            status.setTextColor(getResources().getColor(R.color.error, getTheme()));
        }
    }

    /**
     * Open Accessibility Settings
     */
    private void openAccessibilitySettings() {
        try {
            Intent intent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
            startActivity(intent);
            showToast("Enable 'Safe Zone App Blocking' and 'Safe Zone Website Blocking'");
        } catch (Exception e) {
            showToast("Please go to Settings → Accessibility");
        }
    }

    /**
     * Open Usage Access Settings
     */
    private void openUsageAccessSettings() {
        try {
            Intent intent = new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS);
            startActivity(intent);
            showToast("Find 'Safe Zone' and enable Usage Access");
        } catch (Exception e) {
            showToast("Please go to Settings → Apps → Special Access → Usage Access");
        }
    }

    /**
     * Open Overlay Settings
     */
    private void openOverlaySettings() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:" + getPackageName()));
                startActivity(intent);
                showToast("Enable 'Display over other apps' for Safe Zone");
            } catch (Exception e) {
                showToast("Please go to Settings → Apps → Special Access → Display over other apps");
            }
        }
    }

    /**
     * Open Device Admin Settings
     */
    private void openDeviceAdminSettings() {
        try {
            Intent intent = new Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN);
            ComponentName componentName = new ComponentName(this, SafeZoneDeviceAdminReceiver.class);
            intent.putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, componentName);
            intent.putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                    "Safe Zone needs administrator access to prevent unauthorized uninstallation and ensure your safety.");
            startActivity(intent);
        } catch (Exception e) {
            showToast("Please enable Device Administrator for Safe Zone");
        }
    }

    /**
     * Open Notification Settings
     */
    private void openNotificationSettings() {
        // For Android 13+, request permission directly
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) 
                    != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                requestPermissions(
                    new String[]{android.Manifest.permission.POST_NOTIFICATIONS}, 
                    1001
                );
                return;
            }
        }
        
        // For older versions or if already granted, open settings
        try {
            Intent intent = new Intent();
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                intent.setAction(Settings.ACTION_APP_NOTIFICATION_SETTINGS);
                intent.putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName());
            } else {
                intent.setAction(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                intent.setData(Uri.parse("package:" + getPackageName()));
            }
            startActivity(intent);
            showToast("Enable all notification categories");
        } catch (Exception e) {
            showToast("Please enable notifications for Safe Zone");
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 1001) {
            updatePermissionStatus();
        }
    }

    /**
     * Check if all permissions are granted (role-specific)
     */
    private boolean allPermissionsGranted() {
        if (prefsHelper.isParent()) {
            // Parents only need notification permission
            return hasNotificationPermission();
        } else {
            // Children need all monitoring permissions
            return isAccessibilityServiceEnabled() &&
                   hasUsageStatsPermission() &&
                   hasOverlayPermission() &&
                   isDeviceAdminEnabled();
        }
    }

    /**
     * Check if Accessibility Service is enabled
     */
    private boolean isAccessibilityServiceEnabled() {
        String service1 = getPackageName() + "/" + com.safezone.app.services.AppBlockingAccessibilityService.class.getName();
        String service2 = getPackageName() + "/" + com.safezone.app.services.WebsiteBlockingService.class.getName();
        
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
                    return settingValue.contains(service1) || settingValue.contains(service2);
                }
            }
        } catch (Settings.SettingNotFoundException e) {
            return false;
        }
        return false;
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
                return false;
            }
        }
        return true;
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
        DevicePolicyManager devicePolicyManager = 
            (DevicePolicyManager) getSystemService(Context.DEVICE_POLICY_SERVICE);
        ComponentName componentName = new ComponentName(this, SafeZoneDeviceAdminReceiver.class);
        
        return devicePolicyManager != null && devicePolicyManager.isAdminActive(componentName);
    }

    /**
     * Check if notification permission is granted (Android 13+)
     */
    private boolean hasNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) 
                    == android.content.pm.PackageManager.PERMISSION_GRANTED;
        }
        return true; // Not required for older versions
    }

    /**
     * Show missing permissions dialog
     */
    private void showMissingPermissionsDialog() {
        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Permissions Required")
                .setMessage("Please grant all required permissions for Safe Zone to work properly. " +
                        "Tap on each permission card to enable it.")
                .setPositiveButton("OK", null)
                .show();
    }

    /**
     * Navigate to appropriate dashboard
     */
    private void navigateToDashboard() {
        navigateToDashboard(false);
    }
    
    /**
     * Navigate to appropriate dashboard with skip flag
     */
    private void navigateToDashboard(boolean skipped) {
        // Save skip status for this session
        if (skipped) {
            prefsHelper.setPermissionsSkippedThisSession(true);
        }
        
        Intent intent;
        if (prefsHelper.isParent()) {
            intent = new Intent(this, ParentDashboardActivity.class);
        } else {
            intent = new Intent(this, ChildDashboardActivity.class);
        }
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    private void showToast(String message) {
        android.widget.Toast.makeText(this, message, android.widget.Toast.LENGTH_LONG).show();
    }

    @Override
    public void onBackPressed() {
        // Show options when back is pressed
        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Permissions Setup")
                .setMessage("What would you like to do?")
                .setPositiveButton("Continue Setup", null)
                .setNeutralButton("Skip for now", (dialog, which) -> {
                    navigateToDashboard(true);
                })
                .setNegativeButton("Exit App", (dialog, which) -> {
                    finishAffinity();
                })
                .show();
    }
}
