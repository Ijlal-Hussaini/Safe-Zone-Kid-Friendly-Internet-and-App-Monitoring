package com.safezone.app.activities;

import android.app.ProgressDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.MenuItem;
import android.widget.CompoundButton;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.switchmaterial.SwitchMaterial;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.EmailAuthProvider;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.ValueEventListener;
import com.safezone.app.BuildConfig;
import com.safezone.app.R;
import com.safezone.app.utils.FirebaseHelper;
import com.safezone.app.utils.ImageHelper;
import com.safezone.app.utils.SharedPrefsHelper;
import com.safezone.app.utils.ValidationUtils;

import java.util.HashMap;
import java.util.Map;

/**
 * Settings Activity - COMPLETE WITH ALL REQUIREMENTS
 * - Edit Profile
 * - Change Password
 * - Theme Toggle (Dark/Light mode)
 * - Notification Preferences
 * - Real-time Alerts Toggle
 * - About App (version, credits, privacy)
 * - Logout
 * - Delete Account (with cascade delete of children)
 */
public class SettingsActivity extends AppCompatActivity {

    private MaterialToolbar toolbar;
    private MaterialCardView cardEditProfile;
    private MaterialCardView cardChangePassword;
    private MaterialCardView cardTheme;
    private SwitchMaterial switchTheme;
    private MaterialCardView cardAlerts;
    private SwitchMaterial switchAlerts;
    private MaterialCardView cardPrivacy;
    private MaterialCardView cardAbout;
    private MaterialCardView cardDisconnectParent;
    private MaterialCardView cardLogout;
    private MaterialCardView cardDeleteAccount;

    private SharedPrefsHelper prefsHelper;
    private SharedPreferences settingsPrefs;
    private ProgressDialog progressDialog;

    private static final String PREF_THEME = "theme_mode";
    public static final String PREF_ALERTS = "realtime_alerts_enabled";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        prefsHelper = new SharedPrefsHelper(this);
        settingsPrefs = getSharedPreferences("AppSettings", MODE_PRIVATE);

        initViews();
        setupToolbar();
        loadPreferences();
        setupClickListeners();
    }

    private void initViews() {
        toolbar = findViewById(R.id.toolbar);
        cardEditProfile = findViewById(R.id.card_edit_profile);
        cardChangePassword = findViewById(R.id.card_change_password);
        cardTheme = findViewById(R.id.card_theme);
        switchTheme = findViewById(R.id.switch_theme);
        cardAlerts = findViewById(R.id.card_alerts);
        switchAlerts = findViewById(R.id.switch_alerts);
        cardPrivacy = findViewById(R.id.card_privacy);
        cardAbout = findViewById(R.id.card_about);
        cardDisconnectParent = findViewById(R.id.card_disconnect_parent);
        cardLogout = findViewById(R.id.card_logout);
        cardDeleteAccount = findViewById(R.id.card_delete_account);

        progressDialog = new ProgressDialog(this);
        progressDialog.setCancelable(false);
        
        // Apply theme colors based on user role
        applyThemeColors();
        
        // Configure role-specific settings
        configureRoleSpecificSettings();
    }
    
    /**
     * Show/hide settings based on user role
     */
    private void configureRoleSpecificSettings() {
        // Always hide disconnect from parent option - not needed
        cardDisconnectParent.setVisibility(android.view.View.GONE);
        
        if (prefsHelper.isParent()) {
            // PARENT SETTINGS
            // Parents need alerts to receive notifications about child activity
            cardAlerts.setVisibility(android.view.View.VISIBLE);
            
        } else {
            // CHILD SETTINGS
            // Children don't receive alerts (parents do)
            cardAlerts.setVisibility(android.view.View.GONE);
        }
    }
    
    private void applyThemeColors() {
        int themeColor = prefsHelper.isParent() ? 
            getResources().getColor(R.color.primary, getTheme()) : 
            getResources().getColor(R.color.secondary, getTheme());
        
        // Update switch colors
        switchTheme.setThumbTintList(android.content.res.ColorStateList.valueOf(themeColor));
        switchTheme.setTrackTintList(android.content.res.ColorStateList.valueOf(themeColor));
        switchAlerts.setThumbTintList(android.content.res.ColorStateList.valueOf(themeColor));
        switchAlerts.setTrackTintList(android.content.res.ColorStateList.valueOf(themeColor));
        
        // Update icon colors in each card
        tintCardIcon(cardEditProfile, R.id.card_edit_profile, themeColor);
        tintCardIcon(cardChangePassword, R.id.card_change_password, themeColor);
        tintCardIcon(cardTheme, R.id.card_theme, themeColor);
        tintCardIcon(cardAlerts, R.id.card_alerts, themeColor);
        tintCardIcon(cardPrivacy, R.id.card_privacy, themeColor);
        tintCardIcon(cardAbout, R.id.card_about, themeColor);
    }
    
    private void tintCardIcon(android.view.View card, int cardId, int color) {
        // Find the first ImageView in the card and tint it
        if (card instanceof android.view.ViewGroup) {
            android.view.ViewGroup group = (android.view.ViewGroup) card;
            for (int i = 0; i < group.getChildCount(); i++) {
                android.view.View child = group.getChildAt(i);
                if (child instanceof android.view.ViewGroup) {
                    android.view.ViewGroup innerGroup = (android.view.ViewGroup) child;
                    for (int j = 0; j < innerGroup.getChildCount(); j++) {
                        android.view.View innerChild = innerGroup.getChildAt(j);
                        if (innerChild instanceof android.widget.ImageView) {
                            ((android.widget.ImageView) innerChild).setColorFilter(color);
                            return;
                        }
                    }
                }
            }
        }
    }

    private void setupToolbar() {
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayShowTitleEnabled(false);
        }
        
        // Set toolbar color based on user role
        if (prefsHelper.isParent()) {
            toolbar.setBackgroundColor(getResources().getColor(R.color.primary, getTheme()));
        } else {
            toolbar.setBackgroundColor(getResources().getColor(R.color.secondary, getTheme()));
        }
        
        toolbar.setNavigationOnClickListener(v -> finish());
    }

    private void loadPreferences() {
        // Load theme preference
        boolean isDarkMode = settingsPrefs.getBoolean(PREF_THEME, false);
        switchTheme.setChecked(isDarkMode);

        // Load alerts preference (default ON)
        boolean alertsEnabled = settingsPrefs.getBoolean(PREF_ALERTS, true);
        switchAlerts.setChecked(alertsEnabled);
    }

    private void setupClickListeners() {
        cardEditProfile.setOnClickListener(v -> openEditProfile());
        cardChangePassword.setOnClickListener(v -> showChangePasswordDialog());

        // Theme toggle
        cardTheme.setOnClickListener(v -> switchTheme.setChecked(!switchTheme.isChecked()));
        switchTheme.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (buttonView.isPressed()) {
                toggleTheme(isChecked);
            }
        });

        // Real-time alerts toggle - controls all notifications
        cardAlerts.setOnClickListener(v -> switchAlerts.setChecked(!switchAlerts.isChecked()));
        switchAlerts.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (buttonView.isPressed()) {
                savePreference(PREF_ALERTS, isChecked);
                saveAlertPreferenceToFirebase(isChecked);
                Toast.makeText(this,
                        isChecked ? "Alerts & notifications enabled" : "Alerts & notifications disabled",
                        Toast.LENGTH_SHORT).show();
            }
        });

        cardPrivacy.setOnClickListener(v -> showPrivacyPolicy());
        cardAbout.setOnClickListener(v -> showAboutDialog());
        cardDisconnectParent.setOnClickListener(v -> showDisconnectConfirmation());
        cardLogout.setOnClickListener(v -> showLogoutConfirmation());
        cardDeleteAccount.setOnClickListener(v -> showDeleteAccountConfirmation());
    }

    private void checkParentLinkStatus() {
        String childId = FirebaseHelper.getCurrentUserId();
        if (childId == null) return;

        FirebaseHelper.getUserRef(childId).child("parentId")
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        String parentId = snapshot.getValue(String.class);
                        if (parentId != null && !parentId.isEmpty()) {
                            // Child is linked to a parent - show disconnect option
                            cardDisconnectParent.setVisibility(android.view.View.VISIBLE);
                        } else {
                            // Not linked - hide disconnect option
                            cardDisconnectParent.setVisibility(android.view.View.GONE);
                        }
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        // Hide on error
                        cardDisconnectParent.setVisibility(android.view.View.GONE);
                    }
                });
    }

    private void showDisconnectConfirmation() {
        new MaterialAlertDialogBuilder(this)
                .setTitle("Disconnect from Parent")
                .setMessage("Are you sure you want to disconnect from your parent's account? " +
                        "Your parent will no longer be able to monitor your device.")
                .setPositiveButton("Disconnect", (dialog, which) -> showPasswordConfirmationForDisconnect())
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showPasswordConfirmationForDisconnect() {
        android.view.View dialogView = getLayoutInflater().inflate(R.layout.dialog_confirm_password, null);
        TextInputEditText etPassword = dialogView.findViewById(R.id.et_password);

        new MaterialAlertDialogBuilder(this)
                .setTitle("Confirm Password")
                .setMessage("Enter your password to disconnect from parent")
                .setView(dialogView)
                .setPositiveButton("Confirm", (dialog, which) -> {
                    String password = etPassword.getText().toString().trim();
                    if (password.isEmpty()) {
                        Toast.makeText(this, "Please enter password", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    verifyPasswordAndDisconnect(password);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void verifyPasswordAndDisconnect(String password) {
        FirebaseUser user = FirebaseHelper.getCurrentUser();
        if (user == null || user.getEmail() == null) {
            Toast.makeText(this, "User not found", Toast.LENGTH_SHORT).show();
            return;
        }

        progressDialog.setMessage("Verifying...");
        progressDialog.show();

        AuthCredential credential = EmailAuthProvider.getCredential(user.getEmail(), password);

        user.reauthenticate(credential)
                .addOnSuccessListener(aVoid -> {
                    progressDialog.dismiss();
                    disconnectFromParent();
                })
                .addOnFailureListener(e -> {
                    progressDialog.dismiss();
                    Toast.makeText(this, "Incorrect password", Toast.LENGTH_SHORT).show();
                });
    }

    private void disconnectFromParent() {
        progressDialog.setMessage("Disconnecting...");
        progressDialog.show();

        String childId = FirebaseHelper.getCurrentUserId();
        if (childId == null) {
            progressDialog.dismiss();
            Toast.makeText(this, "Error: User not found", Toast.LENGTH_SHORT).show();
            return;
        }

        // First get the parent ID
        FirebaseHelper.getUserRef(childId).child("parentId")
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        String parentId = snapshot.getValue(String.class);
                        if (parentId != null && !parentId.isEmpty()) {
                            // Use atomic update to remove both sides at once
                            java.util.Map<String, Object> updates = new java.util.HashMap<>();
                            updates.put("users/" + parentId + "/children/" + childId, null);
                            updates.put("users/" + childId + "/parentId", null);

                            FirebaseHelper.getDatabase().getReference().updateChildren(updates)
                                    .addOnSuccessListener(aVoid -> {
                                        progressDialog.dismiss();
                                        // Clear screen time restrictions immediately
                                        clearScreenTimeRestrictions();
                                        Toast.makeText(SettingsActivity.this,
                                                "Successfully disconnected from parent",
                                                Toast.LENGTH_SHORT).show();
                                        // Hide the disconnect card
                                        cardDisconnectParent.setVisibility(android.view.View.GONE);
                                    })
                                    .addOnFailureListener(e -> {
                                        progressDialog.dismiss();
                                        Toast.makeText(SettingsActivity.this,
                                                "Failed to disconnect: " + e.getMessage(),
                                                Toast.LENGTH_SHORT).show();
                                    });
                        } else {
                            progressDialog.dismiss();
                            Toast.makeText(SettingsActivity.this,
                                    "You are not connected to any parent",
                                    Toast.LENGTH_SHORT).show();
                            cardDisconnectParent.setVisibility(android.view.View.GONE);
                        }
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        progressDialog.dismiss();
                        Toast.makeText(SettingsActivity.this,
                                "Error: " + error.getMessage(),
                                Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void openEditProfile() {
        Intent intent;
        if (prefsHelper.isParent()) {
            intent = new Intent(this, ParentProfileActivity.class);
        } else {
            intent = new Intent(this, ChildProfileActivity.class);
        }
        startActivity(intent);
    }

    private void toggleTheme(boolean isDarkMode) {
        savePreference(PREF_THEME, isDarkMode);

        if (isDarkMode) {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
        } else {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
        }

        // Recreate activity to apply theme
        recreate();
    }

    private void savePreference(String key, boolean value) {
        settingsPrefs.edit().putBoolean(key, value).apply();
    }

    private void saveAlertPreferenceToFirebase(boolean enabled) {
        String userId = FirebaseHelper.getCurrentUserId();
        if (userId == null) return;

        Map<String, Object> settings = new HashMap<>();
        settings.put("realtimeAlertsEnabled", enabled);
        settings.put("updatedAt", FirebaseHelper.getServerTimestamp());

        FirebaseHelper.getDatabase().getReference("settings")
                .child(userId)
                .updateChildren(settings);
    }

    private void showChangePasswordDialog() {
        android.view.View dialogView = getLayoutInflater().inflate(R.layout.dialog_change_password, null);
        TextInputEditText etCurrentPassword = dialogView.findViewById(R.id.et_current_password);
        TextInputEditText etNewPassword = dialogView.findViewById(R.id.et_new_password);
        TextInputEditText etConfirmPassword = dialogView.findViewById(R.id.et_confirm_password);

        new MaterialAlertDialogBuilder(this)
                .setTitle("Change Password")
                .setView(dialogView)
                .setPositiveButton("Change", (dialog, which) -> {
                    String current = etCurrentPassword.getText().toString().trim();
                    String newPass = etNewPassword.getText().toString().trim();
                    String confirm = etConfirmPassword.getText().toString().trim();

                    if (current.isEmpty()) {
                        Toast.makeText(this, "Enter current password", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    if (!ValidationUtils.isValidPassword(newPass)) {
                        Toast.makeText(this, ValidationUtils.getPasswordError(newPass), Toast.LENGTH_SHORT).show();
                        return;
                    }

                    if (!newPass.equals(confirm)) {
                        Toast.makeText(this, "Passwords don't match", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    changePassword(current, newPass);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void changePassword(String currentPassword, String newPassword) {
        progressDialog.setMessage("Changing password...");
        progressDialog.show();

        FirebaseUser user = FirebaseHelper.getCurrentUser();
        if (user == null || user.getEmail() == null) {
            progressDialog.dismiss();
            Toast.makeText(this, "User not found", Toast.LENGTH_SHORT).show();
            return;
        }

        AuthCredential credential = EmailAuthProvider.getCredential(user.getEmail(), currentPassword);

        user.reauthenticate(credential).addOnSuccessListener(aVoid -> {
            user.updatePassword(newPassword)
                    .addOnSuccessListener(aVoid1 -> {
                        progressDialog.dismiss();
                        Toast.makeText(this, "Password changed successfully", Toast.LENGTH_SHORT).show();
                    })
                    .addOnFailureListener(e -> {
                        progressDialog.dismiss();
                        Toast.makeText(this, "Failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    });
        }).addOnFailureListener(e -> {
            progressDialog.dismiss();
            Toast.makeText(this, "Current password incorrect", Toast.LENGTH_SHORT).show();
        });
    }

    /**
     * Clear all screen time restrictions from SharedPreferences
     * Called when child disconnects from parent or deletes account
     */
    private void clearScreenTimeRestrictions() {
        android.content.SharedPreferences prefs = getSharedPreferences("ScreenTimePrefs", MODE_PRIVATE);
        prefs.edit()
            .putBoolean("limitExceeded", false)
            .putBoolean("screenTimeEnabled", false)
            .putLong("restrictionStartTime", 0)
            .putLong("accumulatedUsage", 0)
            .putString("allowedApps", "")
            .apply();
        android.util.Log.d("SettingsActivity", "Screen time restrictions cleared");
    }

    private void showPrivacyPolicy() {
        new MaterialAlertDialogBuilder(this)
                .setTitle("Privacy Policy")
                .setMessage("Safe Zone Privacy Policy\n\n" +
                        "1. Data Collection: We collect only necessary information to provide parental control services.\n\n" +
                        "2. Data Usage: Your data is used solely for monitoring and reporting purposes.\n\n" +
                        "3. Data Security: All data is encrypted and securely stored in Firebase.\n\n" +
                        "4. Data Sharing: We never share your data with third parties.\n\n" +
                        "5. Data Deletion: You can delete your account and all associated data anytime.\n\n" +
                        "For full privacy policy, visit: safezone.app/privacy")
                .setPositiveButton("OK", null)
                .show();
    }

    private void showAboutDialog() {
        String versionName = BuildConfig.VERSION_NAME;

        new MaterialAlertDialogBuilder(this)
                .setTitle("About Safe Zone")
                .setMessage("Safe Zone - Kid-Friendly Internet Monitoring\n\n" +
                        "Version: " + versionName + "\n\n" +
                        "A comprehensive parental control application to monitor and manage children's device usage safely.\n\n" +
                        "Features:\n" +
                        "• Real-time activity monitoring\n" +
                        "• Screen time management\n" +
                        "• Content filtering\n" +
                        "• Location tracking\n" +
                        "• Usage analytics & reports\n\n" +
                        "Developed with ❤️ for families\n\n" +
                        "© 2025 Safe Zone. All rights reserved.")
                .setPositiveButton("OK", null)
                .setNeutralButton("Credits", (dialog, which) -> showCreditsDialog())
                .show();
    }

    private void showCreditsDialog() {
        new MaterialAlertDialogBuilder(this)
                .setTitle("Credits")
                .setMessage("Safe Zone Development Team\n\n" +
                        "Lead Developer: Ijlal Hussain\n" +
                        "UI/UX Design: Material Design 3\n\n" +
                        "Technologies:\n" +
                        "• Android SDK\n" +
                        "• Firebase (Auth, Database, Storage)\n" +
                        "• MPAndroidChart\n" +
                        "• Glide Image Library\n" +
                        "• ZXing QR Scanner\n" +
                        "• iText7 PDF Generation\n\n" +
                        "Special Thanks:\n" +
                        "• Google Firebase Team\n" +
                        "• Open Source Community")
                .setPositiveButton("OK", null)
                .show();
    }

    private void showLogoutConfirmation() {
        // Check if user is a child - require password
        if (prefsHelper.isChild()) {
            showPasswordConfirmationForLogout();
        } else {
            // Parent can logout without password
            new MaterialAlertDialogBuilder(this)
                    .setTitle("Logout")
                    .setMessage("Are you sure you want to logout?")
                    .setPositiveButton("Yes", (dialog, which) -> logout())
                    .setNegativeButton("No", null)
                    .show();
        }
    }

    private void showPasswordConfirmationForLogout() {
        android.view.View dialogView = getLayoutInflater().inflate(R.layout.dialog_confirm_password, null);
        TextInputEditText etPassword = dialogView.findViewById(R.id.et_password);

        new MaterialAlertDialogBuilder(this)
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

    private void verifyPasswordAndLogout(String password) {
        FirebaseUser user = FirebaseHelper.getCurrentUser();
        if (user == null || user.getEmail() == null) {
            Toast.makeText(this, "User not found", Toast.LENGTH_SHORT).show();
            return;
        }

        progressDialog.setMessage("Verifying...");
        progressDialog.show();

        AuthCredential credential = EmailAuthProvider.getCredential(user.getEmail(), password);

        user.reauthenticate(credential)
                .addOnSuccessListener(aVoid -> {
                    progressDialog.dismiss();
                    logout();
                })
                .addOnFailureListener(e -> {
                    progressDialog.dismiss();
                    Toast.makeText(this, "Incorrect password", Toast.LENGTH_SHORT).show();
                });
    }

    private void logout() {
        // Clear screen time restrictions for child accounts on logout
        if (prefsHelper.isChild()) {
            clearScreenTimeRestrictions();
        }
        
        FirebaseHelper.signOut();
        prefsHelper.clearSession();

        Intent intent = new Intent(this, LoginActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    private void showDeleteAccountConfirmation() {
        String message = prefsHelper.isParent()
                ? "WARNING: This will permanently delete your account and ALL linked children's accounts. This action cannot be undone.\n\nAre you sure?"
                : "WARNING: This will permanently delete your account and all data. This action cannot be undone.\n\nAre you sure?";

        new MaterialAlertDialogBuilder(this)
                .setTitle("Delete Account")
                .setMessage(message)
                .setPositiveButton("Delete", (dialog, which) -> showPasswordConfirmation())
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showPasswordConfirmation() {
        android.view.View dialogView = getLayoutInflater().inflate(R.layout.dialog_confirm_password, null);
        TextInputEditText etPassword = dialogView.findViewById(R.id.et_password);

        new MaterialAlertDialogBuilder(this)
                .setTitle("Confirm Password")
                .setMessage("Enter your password to delete account")
                .setView(dialogView)
                .setPositiveButton("Confirm", (dialog, which) -> {
                    String password = etPassword.getText().toString().trim();
                    if (!password.isEmpty()) {
                        deleteAccount(password);
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void deleteAccount(String password) {
        progressDialog.setMessage("Deleting account...");
        progressDialog.show();

        FirebaseUser user = FirebaseHelper.getCurrentUser();
        if (user == null || user.getEmail() == null) {
            progressDialog.dismiss();
            return;
        }

        AuthCredential credential = EmailAuthProvider.getCredential(user.getEmail(), password);

        user.reauthenticate(credential).addOnSuccessListener(aVoid -> {
            String userId = user.getUid();

            if (prefsHelper.isParent()) {
                deleteParentAccountWithChildren(userId, user);
            } else {
                deleteChildAccount(userId, user);
            }
        }).addOnFailureListener(e -> {
            progressDialog.dismiss();
            Toast.makeText(this, "Incorrect password", Toast.LENGTH_SHORT).show();
        });
    }

    private void deleteParentAccountWithChildren(String parentId, FirebaseUser user) {
        // First, get all children
        FirebaseHelper.getUserRef(parentId).child("children")
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        // Delete all children first
                        for (DataSnapshot childSnapshot : snapshot.getChildren()) {
                            String childId = childSnapshot.getKey();
                            if (childId != null) {
                                // Delete child data
                                FirebaseHelper.getUserRef(childId).removeValue();
                                // Delete child storage
                                ImageHelper.deleteProfileImage(childId, aVoid -> {}, e -> {});
                            }
                        }

                        // Then delete parent
                        deleteUserData(parentId, user);
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        // Continue with deletion anyway
                        deleteUserData(parentId, user);
                    }
                });
    }

    private void deleteChildAccount(String childId, FirebaseUser user) {
        // Remove child reference from parent
        FirebaseHelper.getUserRef(childId).addListenerForSingleValueEvent(
                new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        String parentId = null;
                        if (snapshot.exists()) {
                            parentId = snapshot.child("parentId").getValue(String.class);
                        }
                        
                        // Delete data in sequence to handle different permission levels
                        final String finalParentId = parentId;
                        
                        // Step 1: Delete user data (child has access to their own user node)
                        FirebaseHelper.getUserRef(childId).removeValue()
                                .addOnSuccessListener(aVoid -> {
                                    // Step 2: Delete locations (child has write access)
                                    FirebaseHelper.getDatabase().getReference("locations").child(childId).removeValue()
                                            .addOnCompleteListener(task1 -> {
                                                // Step 3: Delete settings (child has write access)
                                                FirebaseHelper.getDatabase().getReference("settings").child(childId).removeValue()
                                                        .addOnCompleteListener(task2 -> {
                                                            // Step 4: Try to remove from parent's children list
                                                            // This may fail if child doesn't have permission, but that's OK
                                                            if (finalParentId != null && !finalParentId.isEmpty()) {
                                                                FirebaseHelper.getUserRef(finalParentId)
                                                                        .child("children").child(childId).removeValue()
                                                                        .addOnCompleteListener(task3 -> {
                                                                            // Continue regardless of result
                                                                            finalizeChildAccountDeletion(childId, user);
                                                                        });
                                                            } else {
                                                                finalizeChildAccountDeletion(childId, user);
                                                            }
                                                        });
                                            });
                                })
                                .addOnFailureListener(e -> {
                                    progressDialog.dismiss();
                                    Toast.makeText(SettingsActivity.this, 
                                            "Failed to delete account data: " + e.getMessage(), 
                                            Toast.LENGTH_SHORT).show();
                                });
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        progressDialog.dismiss();
                        Toast.makeText(SettingsActivity.this, 
                                "Error reading data: " + error.getMessage(), 
                                Toast.LENGTH_SHORT).show();
                    }
                }
        );
    }
    
    /**
     * Final step of child account deletion - delete profile image and auth account
     */
    private void finalizeChildAccountDeletion(String childId, FirebaseUser user) {
        // Delete profile photo (ignore errors)
        ImageHelper.deleteProfileImage(childId, aVoid2 -> {}, e -> {});
        
        // Clear screen time restrictions before deleting account
        clearScreenTimeRestrictions();
        
        // Delete Firebase auth account
        user.delete().addOnSuccessListener(aVoid2 -> {
            progressDialog.dismiss();
            prefsHelper.clearSession();

            Toast.makeText(SettingsActivity.this, 
                    "Account deleted successfully", Toast.LENGTH_SHORT).show();

            Intent intent = new Intent(SettingsActivity.this, LoginActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();
        }).addOnFailureListener(e -> {
            progressDialog.dismiss();
            Toast.makeText(SettingsActivity.this, 
                    "Failed to delete account: " + e.getMessage(), 
                    Toast.LENGTH_SHORT).show();
        });
    }

    private void deleteUserData(String userId, FirebaseUser user) {
        // Delete all related data for this user
        DatabaseReference database = FirebaseHelper.getDatabase().getReference();
        
        // Create a map of all paths to delete
        java.util.Map<String, Object> updates = new java.util.HashMap<>();
        updates.put("users/" + userId, null);
        updates.put("activityLogs/" + userId, null);
        updates.put("screenTime/" + userId, null);
        updates.put("contentFilters/" + userId, null);
        updates.put("locations/" + userId, null);
        updates.put("settings/" + userId, null);
        
        // Delete all user data in one atomic operation
        database.updateChildren(updates)
                .addOnSuccessListener(aVoid1 -> {
                    // Delete profile photo
                    ImageHelper.deleteProfileImage(userId, aVoid2 -> {}, e -> {});

                    // Delete auth account
                    user.delete().addOnSuccessListener(aVoid2 -> {
                        progressDialog.dismiss();
                        prefsHelper.clearSession();

                        Toast.makeText(this, "Account deleted successfully", Toast.LENGTH_SHORT).show();

                        Intent intent = new Intent(this, LoginActivity.class);
                        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                        startActivity(intent);
                        finish();
                    }).addOnFailureListener(e -> {
                        progressDialog.dismiss();
                        Toast.makeText(this, "Failed to delete account: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    });
                })
                .addOnFailureListener(e -> {
                    progressDialog.dismiss();
                    Toast.makeText(this, "Failed to delete data: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}