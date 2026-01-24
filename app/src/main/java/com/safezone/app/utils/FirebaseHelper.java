package com.safezone.app.utils;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ServerValue;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;

/**
 * Firebase Helper - Enhanced for Phase 5+
 * Centralized Firebase access with all references
 */
public class FirebaseHelper {

    // Firebase instances
    private static FirebaseAuth auth;
    private static FirebaseDatabase database;
    private static FirebaseStorage storage;

    // Database references
    private static DatabaseReference usersRef;
    private static DatabaseReference alertsRef;
    private static DatabaseReference reportsRef;
    private static DatabaseReference linkTokensRef;
    private static DatabaseReference otpsRef;
    private static DatabaseReference notificationsRef;

    // Storage references
    private static StorageReference storageRef;
    private static StorageReference profilePhotosRef;

    // Initialize Firebase instances
    public static void initialize() {
        if (auth == null) {
            auth = FirebaseAuth.getInstance();
        }
        if (database == null) {
            database = FirebaseDatabase.getInstance();
            usersRef = database.getReference("users");
            alertsRef = database.getReference("alerts");
            reportsRef = database.getReference("reports");
            linkTokensRef = database.getReference("linkTokens");
            otpsRef = database.getReference("otps");
            notificationsRef = database.getReference("notifications");
        }
        if (storage == null) {
            storage = FirebaseStorage.getInstance();
            storageRef = storage.getReference();
            profilePhotosRef = storageRef.child("profile_photos");
        }
    }

    // ==================== Authentication ====================

    public static FirebaseAuth getAuth() {
        if (auth == null) {
            initialize();
        }
        return auth;
    }

    public static FirebaseUser getCurrentUser() {
        return getAuth().getCurrentUser();
    }

    public static String getCurrentUserId() {
        FirebaseUser user = getCurrentUser();
        return user != null ? user.getUid() : null;
    }

    public static boolean isUserAuthenticated() {
        return getCurrentUser() != null;
    }

    public static void signOut() {
        if (auth != null) {
            auth.signOut();
        }
    }

    // ==================== Database ====================

    public static FirebaseDatabase getDatabase() {
        if (database == null) {
            initialize();
        }
        return database;
    }

    // Users
    public static DatabaseReference getUsersRef() {
        if (usersRef == null) {
            initialize();
        }
        return usersRef;
    }

    public static DatabaseReference getUserRef(String userId) {
        return getUsersRef().child(userId);
    }

    public static DatabaseReference getCurrentUserRef() {
        String userId = getCurrentUserId();
        return userId != null ? getUserRef(userId) : null;
    }

    // Alerts
    public static DatabaseReference getAlertsRef() {
        if (alertsRef == null) {
            initialize();
        }
        return alertsRef;
    }

    public static DatabaseReference getAlertRef(String alertId) {
        return getAlertsRef().child(alertId);
    }

    // Reports
    public static DatabaseReference getReportsRef() {
        if (reportsRef == null) {
            initialize();
        }
        return reportsRef;
    }

    public static DatabaseReference getChildReportsRef(String childId) {
        return getReportsRef().child(childId);
    }

    // Link Tokens
    public static DatabaseReference getLinkTokensRef() {
        if (linkTokensRef == null) {
            initialize();
        }
        return linkTokensRef;
    }

    public static DatabaseReference getLinkTokenRef(String tokenId) {
        return getLinkTokensRef().child(tokenId);
    }

    // OTPs
    public static DatabaseReference getOtpsRef() {
        if (otpsRef == null) {
            initialize();
        }
        return otpsRef;
    }

    public static DatabaseReference getOtpRef(String uid) {
        return getOtpsRef().child(uid);
    }

    // Notifications
    public static DatabaseReference getNotificationsRef() {
        if (notificationsRef == null) {
            initialize();
        }
        return notificationsRef;
    }

    public static DatabaseReference getUserNotificationsRef(String userId) {
        return getNotificationsRef().child(userId);
    }

    // ==================== Storage ====================

    public static FirebaseStorage getStorage() {
        if (storage == null) {
            initialize();
        }
        return storage;
    }

    public static StorageReference getStorageRef() {
        if (storageRef == null) {
            initialize();
        }
        return storageRef;
    }

    public static StorageReference getProfilePhotosRef() {
        if (profilePhotosRef == null) {
            initialize();
        }
        return profilePhotosRef;
    }

    public static StorageReference getUserProfilePhotoRef(String userId) {
        return getProfilePhotosRef().child(userId + ".jpg");
    }

    // ==================== Helper Methods ====================

    /**
     * Generate unique ID using Firebase push key
     */
    public static String generateUniqueId() {
        return getUsersRef().push().getKey();
    }

    /**
     * Get Firebase server timestamp
     */
    public static Object getServerTimestamp() {
        return ServerValue.TIMESTAMP;
    }

    /**
     * Get current timestamp in milliseconds
     */
    public static long getCurrentTimestamp() {
        return System.currentTimeMillis();
    }
}