package com.safezone.app.utils;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.AudioAttributes;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.util.Log;

import androidx.core.app.NotificationCompat;

import com.safezone.app.R;
import com.safezone.app.activities.AlertsActivity;

/**
 * Helper class for showing local notifications
 * Shows notifications immediately when alerts are created
 * Respects the real-time alerts setting from Settings
 */
public class NotificationHelper {

    private static final String TAG = "NotificationHelper";
    private static final String PREF_ALERTS = "realtime_alerts_enabled";
    
    // Notification Channels - must match ParentNotificationService
    public static final String CHANNEL_ALERTS = "alerts_channel";
    public static final String CHANNEL_BLOCKED_APPS = "blocked_apps_channel";
    public static final String CHANNEL_SCREEN_TIME = "screen_time_channel";
    public static final String CHANNEL_LOCATION = "location_channel";
    
    /**
     * Initialize notification channels (call once on app start)
     * Creates all channels needed for parent notifications
     */
    public static void createNotificationChannels(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager manager = 
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            
            if (manager == null) return;

            // Sound and vibration settings
            Uri soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
            AudioAttributes audioAttributes = new AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                    .build();

            // General Alerts Channel - HIGH PRIORITY
            NotificationChannel alertsChannel = new NotificationChannel(
                CHANNEL_ALERTS,
                "Child Activity Alerts",
                NotificationManager.IMPORTANCE_HIGH
            );
            alertsChannel.setDescription("Important alerts about your child's device activity");
            alertsChannel.enableVibration(true);
            alertsChannel.setVibrationPattern(new long[]{0, 300, 200, 300});
            alertsChannel.setShowBadge(true);
            alertsChannel.enableLights(true);
            alertsChannel.setLightColor(android.graphics.Color.RED);
            alertsChannel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
            alertsChannel.setSound(soundUri, audioAttributes);
            manager.createNotificationChannel(alertsChannel);

            // Blocked Apps Channel - HIGH PRIORITY
            NotificationChannel blockedAppsChannel = new NotificationChannel(
                CHANNEL_BLOCKED_APPS,
                "Blocked App Attempts",
                NotificationManager.IMPORTANCE_HIGH
            );
            blockedAppsChannel.setDescription("Notifications when your child tries to access blocked apps");
            blockedAppsChannel.enableVibration(true);
            blockedAppsChannel.setVibrationPattern(new long[]{0, 300, 200, 300});
            blockedAppsChannel.setShowBadge(true);
            blockedAppsChannel.enableLights(true);
            blockedAppsChannel.setLightColor(android.graphics.Color.RED);
            blockedAppsChannel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
            blockedAppsChannel.setSound(soundUri, audioAttributes);
            manager.createNotificationChannel(blockedAppsChannel);

            // Screen Time Channel - DEFAULT PRIORITY
            NotificationChannel screenTimeChannel = new NotificationChannel(
                CHANNEL_SCREEN_TIME,
                "Screen Time Alerts",
                NotificationManager.IMPORTANCE_DEFAULT
            );
            screenTimeChannel.setDescription("Notifications about screen time limits");
            screenTimeChannel.enableVibration(true);
            screenTimeChannel.setShowBadge(true);
            screenTimeChannel.setSound(soundUri, audioAttributes);
            manager.createNotificationChannel(screenTimeChannel);

            // Location Channel - LOW PRIORITY (silent)
            NotificationChannel locationChannel = new NotificationChannel(
                CHANNEL_LOCATION,
                "Location Updates",
                NotificationManager.IMPORTANCE_LOW
            );
            locationChannel.setDescription("Location updates from your child's device");
            locationChannel.setShowBadge(false);
            locationChannel.setSound(null, null);
            manager.createNotificationChannel(locationChannel);

            Log.d(TAG, "✅ Notification channels created");
        }
    }

    /**
     * Show notification for blocked app attempt
     */
    public static void showBlockedAppNotification(Context context, String childName, String appName) {
        String title = "🚫 Blocked App Attempt";
        String message = childName + " tried to access " + appName;
        
        showNotification(
            context,
            CHANNEL_BLOCKED_APPS,
            title,
            message,
            childName,
            true, // high priority
            true  // with sound
        );
        
        Log.d(TAG, "📢 Blocked app notification shown: " + appName);
    }

    /**
     * Show notification for screen time limit
     */
    public static void showScreenTimeLimitNotification(Context context, String childName, int limitMinutes) {
        String title = "⏰ Screen Time Limit";
        String message = childName + " exceeded " + limitMinutes + " minutes limit";
        
        showNotification(
            context,
            CHANNEL_SCREEN_TIME,
            title,
            message,
            childName,
            false, // normal priority
            true   // with sound
        );
    }

    /**
     * Show notification for location update
     */
    public static void showLocationNotification(Context context, String childName, String location) {
        String title = "📍 Location Update";
        String message = childName + " is at " + location;
        
        showNotification(
            context,
            CHANNEL_LOCATION,
            title,
            message,
            childName,
            false, // low priority
            false  // no sound
        );
    }

    /**
     * Show notification for blocked content
     */
    public static void showBlockedContentNotification(Context context, String childName, String content) {
        String title = "🚫 Blocked Content";
        String message = childName + " tried to access blocked content";
        
        showNotification(
            context,
            CHANNEL_BLOCKED_APPS,
            title,
            message,
            childName,
            true, // high priority
            true  // with sound
        );
    }

    /**
     * Show notification for time request
     */
    public static void showTimeRequestNotification(Context context, String childName, int extraMinutes) {
        String title = "⏰ Time Request";
        String message = childName + " requested " + extraMinutes + " extra minutes";
        
        showNotification(
            context,
            CHANNEL_ALERTS,
            title,
            message,
            childName,
            true, // high priority
            true  // with sound
        );
    }

    /**
     * Show custom notification
     */
    public static void showCustomNotification(Context context, String type, String title, 
                                              String message, String childName) {
        String channelId = CHANNEL_ALERTS;
        
        // Choose channel based on type
        if ("BLOCKED_APP".equals(type) || "BLOCKED_CONTENT".equals(type)) {
            channelId = CHANNEL_BLOCKED_APPS;
        } else if ("SCREEN_TIME".equals(type)) {
            channelId = CHANNEL_SCREEN_TIME;
        } else if ("LOCATION".equals(type)) {
            channelId = CHANNEL_LOCATION;
        }
        
        showNotification(
            context,
            channelId,
            title,
            message,
            childName,
            true, // high priority
            true  // with sound
        );
    }

    /**
     * Check if real-time alerts are enabled in settings
     */
    public static boolean areAlertsEnabled(Context context) {
        SharedPreferences prefs = context.getSharedPreferences("AppSettings", Context.MODE_PRIVATE);
        return prefs.getBoolean(PREF_ALERTS, true); // Default to true
    }

    /**
     * Internal method to show notification
     * Only shows if alerts are enabled in settings
     */
    private static void showNotification(Context context, String channelId, String title, 
                                        String message, String childName, 
                                        boolean highPriority, boolean withSound) {
        // Check if alerts are enabled
        if (!areAlertsEnabled(context)) {
            Log.d(TAG, "⏸️ Alerts disabled - skipping notification: " + title);
            return;
        }
        
        try {
            // Create intent to open AlertsActivity
            Intent intent = new Intent(context, AlertsActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            
            PendingIntent pendingIntent = PendingIntent.getActivity(
                context,
                (int) System.currentTimeMillis(),
                intent,
                PendingIntent.FLAG_ONE_SHOT | PendingIntent.FLAG_IMMUTABLE
            );

            // Build notification
            NotificationCompat.Builder builder = new NotificationCompat.Builder(context, channelId)
                    .setSmallIcon(R.drawable.ic_notification)
                    .setContentTitle(title)
                    .setContentText(message)
                    .setStyle(new NotificationCompat.BigTextStyle().bigText(message))
                    .setAutoCancel(true)
                    .setContentIntent(pendingIntent)
                    .setCategory(NotificationCompat.CATEGORY_ALARM);

            // Add child name as subtext
            if (childName != null && !childName.isEmpty()) {
                builder.setSubText(childName);
            }

            // Set priority
            if (highPriority) {
                builder.setPriority(NotificationCompat.PRIORITY_HIGH);
                builder.setColor(context.getResources().getColor(R.color.error, null));
            } else {
                builder.setPriority(NotificationCompat.PRIORITY_DEFAULT);
            }

            // Add sound and vibration
            if (withSound) {
                Uri soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
                builder.setSound(soundUri);
                builder.setVibrate(new long[]{0, 500, 200, 500});
            }

            // Show notification
            NotificationManager notificationManager = 
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);

            if (notificationManager != null) {
                int notificationId = (int) System.currentTimeMillis();
                notificationManager.notify(notificationId, builder.build());
                Log.d(TAG, "✅ Notification shown: " + title);
            }
        } catch (Exception e) {
            Log.e(TAG, "❌ Error showing notification: " + e.getMessage(), e);
        }
    }
}
