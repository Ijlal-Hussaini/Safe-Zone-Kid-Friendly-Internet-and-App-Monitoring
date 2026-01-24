package com.safezone.app;

import android.app.Application;
import android.content.SharedPreferences;
import android.util.Log;

import com.safezone.app.receivers.ServiceRestartReceiver;
import com.safezone.app.utils.NotificationHelper;

/**
 * Application class for Safe Zone
 * Initializes global components and service watchdog
 */
public class SafeZoneApplication extends Application {

    private static final String TAG = "SafeZoneApp";
    private static final String PREFS_NAME = "SafeZonePrefs";

    @Override
    public void onCreate() {
        super.onCreate();
        
        Log.d(TAG, "🚀 Safe Zone Application starting...");
        
        // Initialize notification channels (for both parent and child devices)
        NotificationHelper.createNotificationChannels(this);
        Log.d(TAG, "✅ Notification channels created");
        
        // Start service watchdog for child users
        initializeServiceWatchdog();
        
        Log.d(TAG, "✅ Safe Zone Application ready");
    }
    
    /**
     * Initialize service watchdog for child users
     * This ensures services stay running even if Android kills them
     */
    private void initializeServiceWatchdog() {
        try {
            SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
            boolean isLoggedIn = prefs.getBoolean("is_logged_in", false);
            String userRole = prefs.getString("user_role", null);
            
            if (isLoggedIn && "child".equals(userRole)) {
                // Schedule watchdog for child users
                ServiceRestartReceiver.scheduleServiceWatchdog(this);
                Log.d(TAG, "✅ Service watchdog scheduled for child user");
            } else {
                // Cancel watchdog for non-child users
                ServiceRestartReceiver.cancelServiceWatchdog(this);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error initializing watchdog: " + e.getMessage());
        }
    }
}
