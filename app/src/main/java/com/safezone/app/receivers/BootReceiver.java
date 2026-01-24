package com.safezone.app.receivers;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.util.Log;

import com.safezone.app.services.ActivityMonitorService;
import com.safezone.app.services.AppBlockingService;
import com.safezone.app.services.LocationTrackingService;
import com.safezone.app.services.ScreenTimeEnforcerService;

/**
 * Boot Receiver - Auto-starts all monitoring services after device restart
 * 
 * This is CRITICAL for parental control apps to ensure:
 * 1. Screen time enforcement continues after restart
 * 2. App blocking works immediately after boot
 * 3. Location tracking resumes automatically
 * 4. Activity monitoring continues
 * 
 * Also handles:
 * - QUICKBOOT_POWERON (for some devices)
 * - MY_PACKAGE_REPLACED (app updates)
 * - Package data cleared recovery
 */
public class BootReceiver extends BroadcastReceiver {

    private static final String TAG = "BootReceiver";
    private static final String PREFS_NAME = "SafeZonePrefs";
    private static final String KEY_USER_ROLE = "user_role";
    private static final String KEY_IS_LOGGED_IN = "is_logged_in";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || intent.getAction() == null) {
            return;
        }

        String action = intent.getAction();
        Log.d(TAG, "========================================");
        Log.d(TAG, "🚀 BOOT RECEIVER TRIGGERED");
        Log.d(TAG, "Action: " + action);
        Log.d(TAG, "========================================");

        // Handle different boot/restart scenarios
        switch (action) {
            case Intent.ACTION_BOOT_COMPLETED:
            case "android.intent.action.QUICKBOOT_POWERON":
            case "com.htc.intent.action.QUICKBOOT_POWERON":
                Log.d(TAG, "📱 Device boot completed - starting services");
                startServicesIfChild(context);
                break;

            case Intent.ACTION_MY_PACKAGE_REPLACED:
                Log.d(TAG, "📦 App updated - restarting services");
                startServicesIfChild(context);
                break;

            case Intent.ACTION_PACKAGE_DATA_CLEARED:
                Log.d(TAG, "🗑️ Package data cleared");
                // Can't do much here as data is cleared
                break;

            default:
                Log.d(TAG, "Unknown action: " + action);
                break;
        }
    }

    /**
     * Start all monitoring services if user is logged in as child
     */
    private void startServicesIfChild(Context context) {
        try {
            SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            boolean isLoggedIn = prefs.getBoolean(KEY_IS_LOGGED_IN, false);
            String userRole = prefs.getString(KEY_USER_ROLE, null);

            Log.d(TAG, "User logged in: " + isLoggedIn);
            Log.d(TAG, "User role: " + userRole);

            if (!isLoggedIn) {
                Log.d(TAG, "❌ User not logged in - skipping service start");
                return;
            }

            if ("child".equals(userRole)) {
                Log.d(TAG, "✅ Child user detected - starting all monitoring services");
                startChildServices(context);
            } else if ("parent".equals(userRole)) {
                Log.d(TAG, "👨‍👩‍👧 Parent user detected - starting notification service");
                startParentServices(context);
            } else {
                Log.d(TAG, "❓ Unknown role - skipping service start");
            }

        } catch (Exception e) {
            Log.e(TAG, "❌ Error starting services: " + e.getMessage(), e);
        }
    }

    /**
     * Start all child monitoring services
     */
    private void startChildServices(Context context) {
        // Small delay to ensure system is ready
        new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
            try {
                Log.d(TAG, "Starting child services with delay...");
                
                // 1. Activity Monitor Service
                startService(context, ActivityMonitorService.class, "ActivityMonitorService");

                // 2. Screen Time Enforcer Service
                startService(context, ScreenTimeEnforcerService.class, "ScreenTimeEnforcerService");

                // 3. App Blocking Service
                startService(context, AppBlockingService.class, "AppBlockingService");

                // 4. Location Tracking Service
                startService(context, LocationTrackingService.class, "LocationTrackingService");

                Log.d(TAG, "✅ All child services started successfully");
                
                // Send alert to parent that child device restarted
                sendDeviceRestartAlert(context);

            } catch (Exception e) {
                Log.e(TAG, "❌ Error starting child services: " + e.getMessage(), e);
            }
        }, 5000); // 5 second delay for system stability
    }

    /**
     * Start parent notification service
     */
    private void startParentServices(Context context) {
        new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
            try {
                SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
                String userId = prefs.getString("user_id", null);
                
                if (userId != null) {
                    Intent serviceIntent = new Intent(context, 
                        com.safezone.app.services.ParentNotificationService.class);
                    serviceIntent.putExtra("parentId", userId);
                    
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        context.startForegroundService(serviceIntent);
                    } else {
                        context.startService(serviceIntent);
                    }
                    Log.d(TAG, "✅ ParentNotificationService started");
                }
            } catch (Exception e) {
                Log.e(TAG, "❌ Error starting parent services: " + e.getMessage(), e);
            }
        }, 5000);
    }

    /**
     * Helper method to start a foreground service
     */
    private void startService(Context context, Class<?> serviceClass, String serviceName) {
        try {
            Intent serviceIntent = new Intent(context, serviceClass);
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent);
            } else {
                context.startService(serviceIntent);
            }
            
            Log.d(TAG, "✅ " + serviceName + " started");
        } catch (Exception e) {
            Log.e(TAG, "❌ Failed to start " + serviceName + ": " + e.getMessage());
        }
    }

    /**
     * Send alert to parent that child's device was restarted
     * This helps parent know if child is trying to bypass monitoring
     */
    private void sendDeviceRestartAlert(Context context) {
        try {
            SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            String childUid = prefs.getString("user_id", null);
            String childName = prefs.getString("user_name", "Child");

            if (childUid == null) return;

            // Get parent ID from Firebase and send alert
            com.google.firebase.database.DatabaseReference userRef = 
                com.safezone.app.utils.FirebaseHelper.getUserRef(childUid);
            
            userRef.child("parentId").addListenerForSingleValueEvent(
                new com.google.firebase.database.ValueEventListener() {
                    @Override
                    public void onDataChange(com.google.firebase.database.DataSnapshot snapshot) {
                        String parentId = snapshot.getValue(String.class);
                        if (parentId != null && !parentId.isEmpty()) {
                            // Send device restart alert
                            com.safezone.app.utils.AlertHelper.sendCustomAlert(
                                parentId,
                                childUid,
                                childName,
                                "DEVICE_RESTART",
                                childName + "'s device was restarted",
                                "Monitoring services have been automatically restored"
                            );
                            Log.d(TAG, "📤 Device restart alert sent to parent");
                        }
                    }

                    @Override
                    public void onCancelled(com.google.firebase.database.DatabaseError error) {
                        Log.e(TAG, "Failed to get parent ID: " + error.getMessage());
                    }
                }
            );
        } catch (Exception e) {
            Log.e(TAG, "Error sending restart alert: " + e.getMessage());
        }
    }
}
