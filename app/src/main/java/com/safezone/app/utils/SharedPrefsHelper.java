package com.safezone.app.utils;

import android.content.Context;
import android.content.SharedPreferences;

public class SharedPrefsHelper {

    private static final String PREF_NAME = "SafeZonePrefs";
    private static final String KEY_USER_ID = "user_id";
    private static final String KEY_USER_ROLE = "user_role";
    private static final String KEY_USER_NAME = "user_name";
    private static final String KEY_USER_EMAIL = "user_email";
    private static final String KEY_IS_LOGGED_IN = "is_logged_in";
    private static final String KEY_REMEMBER_ME = "remember_me";
    private static final String KEY_LAST_USAGE_PERMISSION_REQUEST = "last_usage_permission_request";
    private static final String KEY_ASKED_ACCESSIBILITY_PERMISSION = "asked_accessibility_permission";
    private static final String KEY_ASKED_OVERLAY_PERMISSION = "asked_overlay_permission";
    private static final String KEY_ASKED_DEVICE_ADMIN_PERMISSION = "asked_device_admin_permission";
    private static final String KEY_PERMISSIONS_SKIPPED_SESSION = "permissions_skipped_session";

    private SharedPreferences prefs;
    private SharedPreferences.Editor editor;

    public SharedPrefsHelper(Context context) {
        prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        editor = prefs.edit();
    }

    // Save user session
    public void saveUserSession(String uid, String name, String email, String role) {
        editor.putString(KEY_USER_ID, uid);
        editor.putString(KEY_USER_NAME, name);
        editor.putString(KEY_USER_EMAIL, email);
        editor.putString(KEY_USER_ROLE, role);
        editor.putBoolean(KEY_IS_LOGGED_IN, true);
        editor.apply();
    }

    // Get user ID
    public String getUserId() {
        return prefs.getString(KEY_USER_ID, null);
    }

    // Get user name
    public String getUserName() {
        return prefs.getString(KEY_USER_NAME, null);
    }

    // Get user email
    public String getUserEmail() {
        return prefs.getString(KEY_USER_EMAIL, null);
    }

    // Get user role
    public String getUserRole() {
        return prefs.getString(KEY_USER_ROLE, null);
    }

    // Check if user is logged in
    public boolean isLoggedIn() {
        return prefs.getBoolean(KEY_IS_LOGGED_IN, false);
    }

    // Check if user is parent
    public boolean isParent() {
        return "parent".equals(getUserRole());
    }

    // Check if user is child
    public boolean isChild() {
        return "child".equals(getUserRole());
    }

    // Set remember me
    public void setRememberMe(boolean remember) {
        editor.putBoolean(KEY_REMEMBER_ME, remember);
        editor.apply();
    }

    // Get remember me
    public boolean getRememberMe() {
        return prefs.getBoolean(KEY_REMEMBER_ME, false);
    }

    // Clear session (logout)
    public void clearSession() {
        boolean rememberMe = getRememberMe();
        String email = getUserEmail();
        editor.clear();
        if (rememberMe) {
            editor.putBoolean(KEY_REMEMBER_ME, true);
            editor.putString(KEY_USER_EMAIL, email);
        }
        editor.apply();
    }

    // Clear remembered email
    public void clearRememberedEmail() {
        editor.remove(KEY_USER_EMAIL);
        editor.apply();
    }

    // Update user name
    public void updateUserName(String name) {
        editor.putString(KEY_USER_NAME, name);
        editor.apply();
    }

    // Get last time usage permission was requested
    public long getLastUsagePermissionRequestTime() {
        return prefs.getLong(KEY_LAST_USAGE_PERMISSION_REQUEST, 0);
    }

    // Set last time usage permission was requested
    public void setLastUsagePermissionRequestTime(long time) {
        editor.putLong(KEY_LAST_USAGE_PERMISSION_REQUEST, time);
        editor.apply();
    }

    // Check if we've asked for accessibility permission this session
    public boolean hasAskedAccessibilityPermission() {
        return prefs.getBoolean(KEY_ASKED_ACCESSIBILITY_PERMISSION, false);
    }

    // Set that we've asked for accessibility permission
    public void setAskedAccessibilityPermission(boolean asked) {
        editor.putBoolean(KEY_ASKED_ACCESSIBILITY_PERMISSION, asked);
        editor.apply();
    }

    // Check if we've asked for overlay permission this session
    public boolean hasAskedOverlayPermission() {
        return prefs.getBoolean(KEY_ASKED_OVERLAY_PERMISSION, false);
    }

    // Set that we've asked for overlay permission
    public void setAskedOverlayPermission(boolean asked) {
        editor.putBoolean(KEY_ASKED_OVERLAY_PERMISSION, asked);
        editor.apply();
    }

    // Check if we've asked for device admin permission this session
    public boolean hasAskedDeviceAdminPermission() {
        return prefs.getBoolean(KEY_ASKED_DEVICE_ADMIN_PERMISSION, false);
    }

    // Set that we've asked for device admin permission
    public void setAskedDeviceAdminPermission(boolean asked) {
        editor.putBoolean(KEY_ASKED_DEVICE_ADMIN_PERMISSION, asked);
        editor.apply();
    }

    // Check if permissions were skipped this session
    public boolean hasPermissionsSkippedThisSession() {
        return prefs.getBoolean(KEY_PERMISSIONS_SKIPPED_SESSION, false);
    }

    // Set that permissions were skipped this session
    public void setPermissionsSkippedThisSession(boolean skipped) {
        editor.putBoolean(KEY_PERMISSIONS_SKIPPED_SESSION, skipped);
        editor.apply();
    }

    // Clear the permissions skipped flag (call on app restart/logout)
    public void clearPermissionsSkippedFlag() {
        editor.putBoolean(KEY_PERMISSIONS_SKIPPED_SESSION, false);
        editor.apply();
    }
}