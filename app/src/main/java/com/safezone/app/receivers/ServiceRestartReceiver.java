package com.safezone.app.receivers;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.util.Log;

/**
 * Service Restart Receiver - Restarts services if they are killed
 * 
 * This receiver is triggered by:
 * 1. AlarmManager periodic checks
 * 2. Services sending restart broadcasts when destroyed
 * 
 * Ensures monitoring services stay running even if Android kills them
 */
public class ServiceRestartReceiver extends BroadcastReceiver {

    private static final String TAG = "ServiceRestartReceiver";
    public static final String ACTION_RESTART_SERVICES = "com.safezone.app.RESTART_SERVICES";
    private static final String PREFS_NAME = "SafeZonePrefs";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null) return;

        String action = intent.getAction();
        Log.d(TAG, "🔄 ServiceRestartReceiver triggered: " + action);

        if (ACTION_RESTART_SERVICES.equals(action)) {
            restartServicesIfNeeded(context);
        }
    }

    /**
     * Check and restart services if user is child
     */
    private void restartServicesIfNeeded(Context context) {
        try {
            SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            boolean isLoggedIn = prefs.getBoolean("is_logged_in", false);
            String userRole = prefs.getString("user_role", null);

            if (!isLoggedIn || !"child".equals(userRole)) {
                return;
            }

            Log.d(TAG, "Checking and restarting child services...");

            // Restart all monitoring services
            restartService(context, com.safezone.app.services.ActivityMonitorService.class);
            restartService(context, com.safezone.app.services.ScreenTimeEnforcerService.class);
            restartService(context, com.safezone.app.services.AppBlockingService.class);
            restartService(context, com.safezone.app.services.LocationTrackingService.class);

        } catch (Exception e) {
            Log.e(TAG, "Error restarting services: " + e.getMessage(), e);
        }
    }

    private void restartService(Context context, Class<?> serviceClass) {
        try {
            Intent serviceIntent = new Intent(context, serviceClass);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent);
            } else {
                context.startService(serviceIntent);
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to restart " + serviceClass.getSimpleName() + ": " + e.getMessage());
        }
    }

    /**
     * Schedule periodic service checks using AlarmManager
     * Call this from Application class or main activity
     */
    public static void scheduleServiceWatchdog(Context context) {
        try {
            android.app.AlarmManager alarmManager = 
                (android.app.AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
            
            Intent intent = new Intent(context, ServiceRestartReceiver.class);
            intent.setAction(ACTION_RESTART_SERVICES);
            
            android.app.PendingIntent pendingIntent = android.app.PendingIntent.getBroadcast(
                context,
                12345,
                intent,
                android.app.PendingIntent.FLAG_UPDATE_CURRENT | android.app.PendingIntent.FLAG_IMMUTABLE
            );

            // Check every 15 minutes
            long intervalMillis = 15 * 60 * 1000;
            long triggerAtMillis = System.currentTimeMillis() + intervalMillis;

            if (alarmManager != null) {
                alarmManager.setRepeating(
                    android.app.AlarmManager.RTC_WAKEUP,
                    triggerAtMillis,
                    intervalMillis,
                    pendingIntent
                );
                Log.d(TAG, "✅ Service watchdog scheduled (every 15 minutes)");
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to schedule watchdog: " + e.getMessage());
        }
    }

    /**
     * Cancel the service watchdog
     */
    public static void cancelServiceWatchdog(Context context) {
        try {
            android.app.AlarmManager alarmManager = 
                (android.app.AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
            
            Intent intent = new Intent(context, ServiceRestartReceiver.class);
            intent.setAction(ACTION_RESTART_SERVICES);
            
            android.app.PendingIntent pendingIntent = android.app.PendingIntent.getBroadcast(
                context,
                12345,
                intent,
                android.app.PendingIntent.FLAG_UPDATE_CURRENT | android.app.PendingIntent.FLAG_IMMUTABLE
            );

            if (alarmManager != null) {
                alarmManager.cancel(pendingIntent);
                Log.d(TAG, "Service watchdog cancelled");
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to cancel watchdog: " + e.getMessage());
        }
    }
}
