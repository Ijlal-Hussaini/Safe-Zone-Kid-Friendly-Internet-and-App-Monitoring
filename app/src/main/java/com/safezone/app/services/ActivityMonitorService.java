package com.safezone.app.services;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.app.usage.UsageStats;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import com.google.firebase.database.DatabaseReference;
import com.safezone.app.R;
import com.safezone.app.activities.ChildDashboardActivity;
import com.safezone.app.models.ActivityLog;
import com.safezone.app.utils.FirebaseHelper;

import java.util.List;
import java.util.SortedMap;
import java.util.TreeMap;

/**
 * Foreground Service that monitors app usage on child's device
 * Runs every 5 minutes and logs activity to Firebase
 * 
 * FIXED: Now tracks incremental usage (time since last check) instead of cumulative totals
 */
public class ActivityMonitorService extends Service {

    private static final String TAG = "ActivityMonitorService";
    private static final String CHANNEL_ID = "activity_monitor_channel";
    private static final int NOTIFICATION_ID = 1001;
    private static final long LOG_INTERVAL = 5 * 60 * 1000; // 5 minutes

    private Handler handler;
    private Runnable monitoringRunnable;
    private UsageStatsManager usageStatsManager;
    private PackageManager packageManager;
    private DatabaseReference logsRef;
    private String childUid;
    private long lastCheckTime;
    
    // Track previous foreground times to calculate incremental usage
    private java.util.Map<String, Long> previousForegroundTimes = new java.util.HashMap<>();

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "Service created");

        usageStatsManager = (UsageStatsManager) getSystemService(Context.USAGE_STATS_SERVICE);
        packageManager = getPackageManager();

        childUid = FirebaseHelper.getCurrentUserId();
        if (childUid != null) {
            logsRef = FirebaseHelper.getUsersRef().child(childUid).child("activityLogs");
        }

        lastCheckTime = System.currentTimeMillis() - LOG_INTERVAL;

        createNotificationChannel();
        startForeground(NOTIFICATION_ID, createNotification());

        startMonitoring();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "Service started");
        return START_STICKY; // Restart if killed
    }

    private void startMonitoring() {
        handler = new Handler();
        monitoringRunnable = new Runnable() {
            @Override
            public void run() {
                logAppUsage();
                handler.postDelayed(this, LOG_INTERVAL);
            }
        };
        handler.post(monitoringRunnable);
    }

    private void logAppUsage() {
        if (usageStatsManager == null || logsRef == null) {
            Log.e(TAG, "UsageStatsManager or Firebase reference is null");
            return;
        }

        try {
            long currentTime = System.currentTimeMillis();

            // Get usage stats for today (INTERVAL_DAILY gives cumulative for today)
            List<UsageStats> stats = usageStatsManager.queryUsageStats(
                    UsageStatsManager.INTERVAL_DAILY,
                    lastCheckTime,
                    currentTime
            );

            if (stats != null && !stats.isEmpty()) {
                // Process each app
                for (UsageStats usageStat : stats) {
                    String packageName = usageStat.getPackageName();

                    // Skip system apps
                    if (isSystemApp(packageName)) {
                        continue;
                    }

                    long currentTotalForeground = usageStat.getTotalTimeInForeground();
                    
                    // Get previous foreground time for this app
                    Long previousTotal = previousForegroundTimes.get(packageName);
                    
                    // Calculate INCREMENTAL usage since last check
                    long incrementalUsage;
                    if (previousTotal == null) {
                        // First time seeing this app - don't log, just record baseline
                        previousForegroundTimes.put(packageName, currentTotalForeground);
                        continue;
                    } else {
                        incrementalUsage = currentTotalForeground - previousTotal;
                        // Update stored value
                        previousForegroundTimes.put(packageName, currentTotalForeground);
                    }
                    
                    // Only log if app was actually used since last check (more than 10 seconds)
                    // Also cap at LOG_INTERVAL to prevent unrealistic values
                    if (incrementalUsage > 10000 && incrementalUsage <= LOG_INTERVAL + 60000) {
                        String appName = getAppName(packageName);
                        logToFirebase(packageName, appName, incrementalUsage, currentTime);
                        Log.d(TAG, "Logged incremental: " + appName + " - " + (incrementalUsage/1000) + "s");
                    }
                }
            }

            lastCheckTime = currentTime;

        } catch (Exception e) {
            Log.e(TAG, "Error logging app usage: " + e.getMessage(), e);
        }
    }

    private boolean isSystemApp(String packageName) {
        try {
            ApplicationInfo appInfo = packageManager.getApplicationInfo(packageName, 0);
            return (appInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0;
        } catch (PackageManager.NameNotFoundException e) {
            return true; // If can't find, assume system app
        }
    }

    private String getAppName(String packageName) {
        try {
            ApplicationInfo appInfo = packageManager.getApplicationInfo(packageName, 0);
            return packageManager.getApplicationLabel(appInfo).toString();
        } catch (PackageManager.NameNotFoundException e) {
            return packageName;
        }
    }

    private void logToFirebase(String packageName, String appName, long duration, long timestamp) {
        try {
            String logId = logsRef.push().getKey();
            if (logId != null) {
                ActivityLog log = new ActivityLog(
                        logId,
                        packageName,
                        appName,
                        duration,
                        timestamp
                );

                logsRef.child(logId).setValue(log)
                        .addOnSuccessListener(aVoid ->
                                Log.d(TAG, "Logged: " + appName + " - " + (duration/1000) + "s"))
                        .addOnFailureListener(e ->
                                Log.e(TAG, "Failed to log: " + e.getMessage()));
            }
        } catch (Exception e) {
            Log.e(TAG, "Error writing to Firebase: " + e.getMessage(), e);
        }
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Activity Monitoring",
                    NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("Monitors app usage for parental control");
            channel.setShowBadge(false);

            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }

    private Notification createNotification() {
        Intent notificationIntent = new Intent(this, ChildDashboardActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this,
                0,
                notificationIntent,
                PendingIntent.FLAG_IMMUTABLE
        );

        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Safe Zone Active")
                .setContentText("Monitoring app usage")
                .setSmallIcon(R.drawable.logo2)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (handler != null && monitoringRunnable != null) {
            handler.removeCallbacks(monitoringRunnable);
        }
        Log.d(TAG, "Service destroyed");
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}