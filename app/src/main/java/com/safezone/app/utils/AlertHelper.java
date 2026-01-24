package com.safezone.app.utils;

import android.util.Log;

import com.google.firebase.database.DatabaseReference;
import com.safezone.app.models.Alert;

/**
 * Utility class for creating and sending alerts to parents
 * Alerts are saved to Firebase and notifications are shown on parent's device
 * via ParentNotificationService
 */
public class AlertHelper {

    private static final String TAG = "AlertHelper";

    /**
     * Send an alert when child attempts to access blocked app
     */
    public static void sendBlockedAppAlert(String parentId, String childId,
                                           String childName, String appName) {
        String alertId = FirebaseHelper.generateUniqueId();
        String message = childName + " tried to access blocked app";
        String details = "App: " + appName;

        Alert alert = new Alert(
                alertId,
                parentId,
                childId,
                childName,
                Alert.Type.BLOCKED_APP,
                message,
                details,
                System.currentTimeMillis()
        );

        sendAlert(alert);
        // NOTE: Notification will be shown on PARENT's device via ParentNotificationService
    }

    /**
     * Send an alert when screen time limit is exceeded
     */
    public static void sendScreenTimeLimitAlert(String parentId, String childId,
                                                String childName, int limitMinutes) {
        String alertId = FirebaseHelper.generateUniqueId();
        String message = childName + " exceeded screen time limit";
        String details = "Daily limit: " + limitMinutes + " minutes";

        Alert alert = new Alert(
                alertId,
                parentId,
                childId,
                childName,
                Alert.Type.SCREEN_TIME,
                message,
                details,
                System.currentTimeMillis()
        );

        sendAlert(alert);
        // NOTE: Notification will be shown on PARENT's device via ParentNotificationService
    }

    /**
     * Send a location update alert
     */
    public static void sendLocationAlert(String parentId, String childId,
                                         String childName, String location) {
        String alertId = FirebaseHelper.generateUniqueId();
        String message = childName + "'s location updated";
        String details = location;

        Alert alert = new Alert(
                alertId,
                parentId,
                childId,
                childName,
                Alert.Type.LOCATION,
                message,
                details,
                System.currentTimeMillis()
        );

        sendAlert(alert);
        // NOTE: Notification will be shown on PARENT's device via ParentNotificationService
    }

    /**
     * Send an alert when child tries to access blocked content
     */
    public static void sendBlockedContentAlert(String parentId, String childId,
                                               String childName, String content) {
        String alertId = FirebaseHelper.generateUniqueId();
        String message = childName + " tried to access blocked content";
        String details = "Content: " + content;

        Alert alert = new Alert(
                alertId,
                parentId,
                childId,
                childName,
                Alert.Type.CONTENT,
                message,
                details,
                System.currentTimeMillis()
        );

        sendAlert(alert);
        // NOTE: Notification will be shown on PARENT's device via ParentNotificationService
    }

    /**
     * Send an alert when child requests extra time
     */
    public static void sendTimeRequestAlert(String parentId, String childId,
                                            String childName, int extraMinutes) {
        String alertId = FirebaseHelper.generateUniqueId();
        String message = childName + " requested extra screen time";
        String details = "Requested: " + extraMinutes + " minutes";

        Alert alert = new Alert(
                alertId,
                parentId,
                childId,
                childName,
                Alert.Type.REQUEST,
                message,
                details,
                System.currentTimeMillis()
        );

        sendAlert(alert);
        // NOTE: Notification will be shown on PARENT's device via ParentNotificationService
    }

    /**
     * Send a custom alert
     */
    public static void sendCustomAlert(String parentId, String childId, String childName,
                                       String type, String message, String details) {
        String alertId = FirebaseHelper.generateUniqueId();

        Alert alert = new Alert(
                alertId,
                parentId,
                childId,
                childName,
                type,
                message,
                details,
                System.currentTimeMillis()
        );

        sendAlert(alert);
        // NOTE: Notification will be shown on PARENT's device via ParentNotificationService
    }

    /**
     * Internal method to send alert to Firebase
     */
    private static void sendAlert(Alert alert) {
        if (alert.getAlertId() == null) {
            Log.e(TAG, "❌ Alert ID is null");
            return;
        }

        Log.e(TAG, "========================================");
        Log.e(TAG, "📤 SENDING ALERT TO FIREBASE");
        Log.e(TAG, "Alert ID: " + alert.getAlertId());
        Log.e(TAG, "Parent ID: " + alert.getParentId());
        Log.e(TAG, "Child ID: " + alert.getChildId());
        Log.e(TAG, "Type: " + alert.getType());
        Log.e(TAG, "Message: " + alert.getMessage());
        Log.e(TAG, "Timestamp: " + alert.getTimestamp());
        Log.e(TAG, "========================================");

        DatabaseReference alertsRef = FirebaseHelper.getAlertsRef();
        String path = "alerts/" + alert.getAlertId();
        Log.e(TAG, "Firebase path: " + path);
        
        alertsRef.child(alert.getAlertId()).setValue(alert)
                .addOnSuccessListener(aVoid -> {
                    Log.e(TAG, "✅✅✅ ALERT SAVED TO FIREBASE SUCCESSFULLY!");
                    Log.e(TAG, "Alert: " + alert.getMessage());
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "❌❌❌ FAILED TO SAVE ALERT: " + e.getMessage());
                });
    }

    /**
     * Mark an alert as read
     */
    public static void markAlertAsRead(String alertId) {
        if (alertId == null) return;

        DatabaseReference alertRef = FirebaseHelper.getAlertsRef().child(alertId);
        alertRef.child("read").setValue(true)
                .addOnSuccessListener(aVoid -> {
                    Log.d(TAG, "Alert marked as read: " + alertId);
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to mark alert as read: " + e.getMessage());
                });
    }

    /**
     * Delete an alert
     */
    public static void deleteAlert(String alertId) {
        if (alertId == null) return;

        DatabaseReference alertRef = FirebaseHelper.getAlertsRef().child(alertId);
        alertRef.removeValue()
                .addOnSuccessListener(aVoid -> {
                    Log.d(TAG, "Alert deleted: " + alertId);
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to delete alert: " + e.getMessage());
                });
    }
}