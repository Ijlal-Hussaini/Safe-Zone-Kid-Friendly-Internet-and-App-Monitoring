package com.safezone.app.services;

import android.app.ActivityManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.app.usage.UsageStats;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.ValueEventListener;
import com.safezone.app.R;
import com.safezone.app.activities.ChildDashboardActivity;
import com.safezone.app.utils.AlertHelper;
import com.safezone.app.utils.FirebaseHelper;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.SortedMap;
import java.util.TreeMap;

/**
 * App Blocking Service - Handles both blocked apps AND screen time enforcement
 */
public class AppBlockingService extends Service {

    private static final String TAG = "AppBlockingService";
    private static final String CHANNEL_ID = "app_blocking_channel";
    private static final int NOTIFICATION_ID = 1003;
    private static final long CHECK_INTERVAL = 50; // Check every 50ms (20 times per second) for instant blocking

    private Handler handler;
    private Runnable blockingRunnable;
    private UsageStatsManager usageStatsManager;
    private ActivityManager activityManager;
    private DatabaseReference contentRulesRef;
    private String childUid;
    private String childName;

    private List<String> blockedApps = new ArrayList<>();
    
    // Dialog cooldown tracking
    private String lastBlockedApp = null;
    private long lastBlockTime = 0;
    private static final long DIALOG_COOLDOWN = 2000; // 2 seconds between dialogs for same app
    
    // Alert tracking
    private static final String PREFS_NAME = "AppBlockingPrefs";
    private static final String PREF_LAST_ALERT_PACKAGE = "lastAlertPackage";
    private static final String PREF_LAST_ALERT_TIME = "lastAlertTime";
    private static final long ALERT_COOLDOWN = 5000; // 5 seconds between alerts
    
    // Parent link status
    private boolean isLinkedToParent = false;
    private ValueEventListener parentLinkListener;

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "AppBlockingService created");

        usageStatsManager = (UsageStatsManager) getSystemService(Context.USAGE_STATS_SERVICE);
        activityManager = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);

        childUid = FirebaseHelper.getCurrentUserId();
        if (childUid != null) {
            contentRulesRef = FirebaseHelper.getUsersRef().child(childUid).child("contentRules");
            loadChildName();
            listenToParentLinkStatus(); // Check if still linked to parent
            listenToBlockedApps();
        } else {
            // No user logged in - clear screen time restrictions
            clearScreenTimeRestrictions();
        }

        createNotificationChannel();
        startForeground(NOTIFICATION_ID, createNotification());

        startBlocking();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "Service started");
        return START_STICKY;
    }

    private void loadChildName() {
        FirebaseHelper.getUserRef(childUid).child("name")
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        childName = snapshot.getValue(String.class);
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        childName = "Child";
                    }
                });
    }
    
    /**
     * Listen to parent link status - if child is unlinked, clear screen time restrictions
     */
    private void listenToParentLinkStatus() {
        if (childUid == null) return;
        
        DatabaseReference parentIdRef = FirebaseHelper.getUsersRef().child(childUid).child("parentId");
        parentLinkListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                String parentId = snapshot.getValue(String.class);
                boolean wasLinked = isLinkedToParent;
                isLinkedToParent = (parentId != null && !parentId.isEmpty());
                
                Log.d(TAG, "Parent link status: " + (isLinkedToParent ? "LINKED" : "NOT LINKED"));
                
                // If child was linked but now unlinked, clear screen time restrictions
                if (wasLinked && !isLinkedToParent) {
                    Log.d(TAG, "Child UNLINKED - clearing screen time restrictions");
                    clearScreenTimeRestrictions();
                }
                
                // If not linked, ensure restrictions are cleared
                if (!isLinkedToParent) {
                    clearScreenTimeRestrictions();
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e(TAG, "Failed to check parent link: " + error.getMessage());
            }
        };
        parentIdRef.addValueEventListener(parentLinkListener);
    }
    
    /**
     * Clear screen time restrictions from SharedPreferences
     */
    private void clearScreenTimeRestrictions() {
        Log.d(TAG, "Clearing screen time restrictions");
        SharedPreferences prefs = getSharedPreferences(ScreenTimeEnforcerService.PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit()
            .putBoolean(ScreenTimeEnforcerService.PREF_LIMIT_EXCEEDED, false)
            .putBoolean(ScreenTimeEnforcerService.PREF_SCREEN_TIME_ENABLED, false)
            .apply();
    }

    private void listenToBlockedApps() {
        contentRulesRef.child("blockedApps").addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                blockedApps.clear();
                
                if (snapshot.exists()) {
                    for (DataSnapshot appSnapshot : snapshot.getChildren()) {
                        String packageName = appSnapshot.getValue(String.class);
                        if (packageName != null && !packageName.isEmpty()) {
                            blockedApps.add(packageName);
                        }
                    }
                }

                Log.d(TAG, "Blocked apps updated: " + blockedApps.size() + " apps");
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e(TAG, "Failed to load blocked apps: " + error.getMessage());
            }
        });
    }

    private void startBlocking() {
        handler = new Handler();
        blockingRunnable = new Runnable() {
            @Override
            public void run() {
                checkAndBlockForegroundApp();
                handler.postDelayed(this, CHECK_INTERVAL);
            }
        };
        handler.post(blockingRunnable);
    }

    private void checkAndBlockForegroundApp() {
        try {
            String foregroundApp = getCurrentForegroundApp();
            
            if (foregroundApp == null) return;
            
            // Skip our own app
            if (foregroundApp.equals(getPackageName())) return;
            
            // Skip launcher - NEVER block launcher
            if (isLauncher(foregroundApp)) return;
            
            // Check if app is explicitly blocked (content filter)
            if (isAppBlocked(foregroundApp)) {
                // Instant blocking - kill immediately even before full block
                killAppProcess(foregroundApp);
                blockApp(foregroundApp, BlockReason.BLOCKED_APP);
                return;
            }
            
            // Check if screen time limit exceeded
            if (isScreenTimeLimitExceeded()) {
                // Check if app is in allowed list for screen time
                if (!isAppAllowedForScreenTime(foregroundApp)) {
                    // Instant blocking for screen time - kill immediately
                    killAppProcess(foregroundApp);
                    blockApp(foregroundApp, BlockReason.SCREEN_TIME);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error checking foreground app: " + e.getMessage(), e);
        }
    }

    private enum BlockReason {
        BLOCKED_APP,
        SCREEN_TIME
    }

    /**
     * Check if screen time limit is exceeded
     * CRITICAL: Only enforce if child is still linked to a parent
     */
    private boolean isScreenTimeLimitExceeded() {
        // If not linked to parent, never enforce screen time
        if (!isLinkedToParent) {
            return false;
        }
        
        SharedPreferences prefs = getSharedPreferences(ScreenTimeEnforcerService.PREFS_NAME, Context.MODE_PRIVATE);
        boolean enabled = prefs.getBoolean(ScreenTimeEnforcerService.PREF_SCREEN_TIME_ENABLED, false);
        boolean exceeded = prefs.getBoolean(ScreenTimeEnforcerService.PREF_LIMIT_EXCEEDED, false);
        return enabled && exceeded;
    }

    /**
     * Check if app is allowed during screen time limit
     */
    private boolean isAppAllowedForScreenTime(String packageName) {
        // First check if it's a default allowed app (phone, dialer, emergency)
        if (isDefaultAllowedApp(packageName)) {
            Log.d(TAG, "App is default allowed: " + packageName);
            return true;
        }
        
        SharedPreferences prefs = getSharedPreferences(ScreenTimeEnforcerService.PREFS_NAME, Context.MODE_PRIVATE);
        String allowedAppsStr = prefs.getString(ScreenTimeEnforcerService.PREF_ALLOWED_APPS, "");
        
        Log.d(TAG, "Checking if allowed: " + packageName);
        Log.d(TAG, "Allowed apps from prefs: " + allowedAppsStr);
        
        if (allowedAppsStr.isEmpty()) {
            Log.d(TAG, "No allowed apps in preferences");
            return false;
        }
        
        // Split and check each allowed app
        String[] allowedAppsArray = allowedAppsStr.split(",");
        for (String allowedApp : allowedAppsArray) {
            String trimmed = allowedApp.trim();
            if (!trimmed.isEmpty() && trimmed.equals(packageName)) {
                Log.d(TAG, "App IS allowed (from prefs): " + packageName);
                return true;
            }
        }
        
        Log.d(TAG, "App NOT allowed: " + packageName);
        return false;
    }
    
    /**
     * Check if app is a default allowed app (phone, dialer, contacts, SafeZone)
     * Only essential apps for emergency calls and SafeZone itself
     */
    private boolean isDefaultAllowedApp(String packageName) {
        // SafeZone app itself - always allowed
        if (packageName.equals(getPackageName())) {
            return true;
        }
        
        // Dialer apps - for emergency calls
        if (packageName.equals("com.android.dialer") ||
            packageName.equals("com.google.android.dialer") ||
            packageName.equals("com.samsung.android.dialer") ||
            packageName.equals("com.android.phone") ||
            packageName.equals("com.samsung.android.incallui") ||
            packageName.equals("com.android.server.telecom")) {
            return true;
        }
        
        // Contacts apps - for emergency contacts
        if (packageName.equals("com.android.contacts") ||
            packageName.equals("com.google.android.contacts")) {
            return true;
        }
        
        return false;
    }

    /**
     * Check if the package is a launcher (home screen)
     */
    private boolean isLauncher(String packageName) {
        try {
            Intent intent = new Intent(Intent.ACTION_MAIN);
            intent.addCategory(Intent.CATEGORY_HOME);
            android.content.pm.ResolveInfo resolveInfo = getPackageManager().resolveActivity(
                    intent, android.content.pm.PackageManager.MATCH_DEFAULT_ONLY);
            if (resolveInfo != null && resolveInfo.activityInfo != null) {
                return packageName.equals(resolveInfo.activityInfo.packageName);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error checking launcher: " + e.getMessage());
        }
        
        // Also check common launcher package names
        return packageName.contains("launcher") || 
               packageName.contains("home") ||
               packageName.equals("com.miui.home") ||
               packageName.equals("com.sec.android.app.launcher") ||
               packageName.equals("com.google.android.apps.nexuslauncher") ||
               packageName.equals("com.huawei.android.launcher");
    }

    private String getCurrentForegroundApp() {
        if (usageStatsManager == null) return null;

        long currentTime = System.currentTimeMillis();
        
        List<UsageStats> stats = usageStatsManager.queryUsageStats(
                UsageStatsManager.INTERVAL_DAILY,
                currentTime - 500,
                currentTime
        );

        if (stats != null && !stats.isEmpty()) {
            SortedMap<Long, UsageStats> sortedStats = new TreeMap<>();
            for (UsageStats usageStat : stats) {
                sortedStats.put(usageStat.getLastTimeUsed(), usageStat);
            }

            if (!sortedStats.isEmpty()) {
                return sortedStats.get(sortedStats.lastKey()).getPackageName();
            }
        }

        return null;
    }

    private boolean isAppBlocked(String packageName) {
        // If not linked to parent, don't block any apps
        if (!isLinkedToParent) {
            return false;
        }
        return blockedApps.contains(packageName);
    }

    private void blockApp(String packageName, BlockReason reason) {
        long currentTime = System.currentTimeMillis();
        
        // INSTANT BLOCKING - Kill immediately multiple times to prevent any app visibility
        for (int i = 0; i < 3; i++) {
            killAppProcess(packageName);
        }
        clearFromRecents(packageName);
        
        // Force stop the app using ActivityManager
        forceStopApp(packageName);
        
        // Go to home screen immediately
        Intent homeIntent = new Intent(Intent.ACTION_MAIN);
        homeIntent.addCategory(Intent.CATEGORY_HOME);
        homeIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | 
                          Intent.FLAG_ACTIVITY_CLEAR_TOP |
                          Intent.FLAG_ACTIVITY_SINGLE_TOP |
                          Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        startActivity(homeIntent);
        
        // Kill again after going home to ensure it's completely closed
        killAppProcess(packageName);
        forceStopApp(packageName);
        
        // Check cooldown before showing dialog
        boolean shouldShowDialog = !packageName.equals(lastBlockedApp) || 
                                   (currentTime - lastBlockTime) >= DIALOG_COOLDOWN;
        
        if (shouldShowDialog) {
            lastBlockedApp = packageName;
            lastBlockTime = currentTime;
            
            Log.d(TAG, "Blocking: " + packageName + " (Reason: " + reason + ")");
            
            // Send alert to parent (with cooldown)
            if (shouldSendAlert(packageName, currentTime)) {
                saveLastAlert(packageName, currentTime);
                sendBlockedAppAlert(packageName, reason);
            }
            
            // Show dialog after very short delay (just enough for home to show)
            new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
                showBlockingDialog(packageName, reason);
            }, 50);
        }
    }
    
    /**
     * Force stop an app using ActivityManager
     */
    private void forceStopApp(String packageName) {
        try {
            if (activityManager != null) {
                // Use reflection to call forceStopPackage if available
                java.lang.reflect.Method method = activityManager.getClass()
                        .getMethod("forceStopPackage", String.class);
                method.invoke(activityManager, packageName);
                Log.d(TAG, "Force stopped: " + packageName);
            }
        } catch (Exception e) {
            // forceStopPackage requires system permission, fall back to killBackgroundProcesses
            killAppProcess(packageName);
        }
    }

    private void killAppProcess(String packageName) {
        try {
            if (activityManager != null) {
                activityManager.killBackgroundProcesses(packageName);
                Log.d(TAG, "Killed background process: " + packageName);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error killing app process: " + e.getMessage());
        }
    }

    private void clearFromRecents(String packageName) {
        try {
            if (activityManager != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                List<ActivityManager.AppTask> tasks = activityManager.getAppTasks();
                for (ActivityManager.AppTask task : tasks) {
                    try {
                        ActivityManager.RecentTaskInfo taskInfo = task.getTaskInfo();
                        if (taskInfo.baseIntent != null && 
                            taskInfo.baseIntent.getComponent() != null &&
                            packageName.equals(taskInfo.baseIntent.getComponent().getPackageName())) {
                            task.finishAndRemoveTask();
                        }
                    } catch (Exception e) {
                        // Continue
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error clearing from recents: " + e.getMessage());
        }
    }

    private void showBlockingDialog(String packageName, BlockReason reason) {
        try {
            String appName = getAppName(packageName);
            
            android.graphics.drawable.Drawable appIcon = null;
            try {
                appIcon = getPackageManager().getApplicationIcon(packageName);
            } catch (Exception e) {
                try {
                    appIcon = androidx.core.content.ContextCompat.getDrawable(this, R.drawable.ic_apps);
                    if (appIcon != null) {
                        appIcon = appIcon.mutate();
                        appIcon.setTint(0xFF26C6DA);
                    }
                } catch (Exception ex) {
                    // Ignore
                }
            }
            
            final android.graphics.drawable.Drawable finalAppIcon = appIcon;
            
            android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(
                    this, android.R.style.Theme_Material_Light_Dialog_Alert);
            
            String title;
            String message;
            String negativeButtonText;
            
            if (reason == BlockReason.SCREEN_TIME) {
                title = "Time Limit Exceeded";
                message = appName + "\n\nScreen time limit reached!\n\nOnly allowed apps can be used. Request more time from your parent.";
                negativeButtonText = "Request Time";
            } else {
                title = "App Blocked";
                message = appName + "\n\nThis app is blocked by your parent. If you need access, you can request permission.";
                negativeButtonText = "Request Access";
            }
            
            builder.setTitle(title)
                    .setMessage(message)
                    .setIcon(finalAppIcon)
                    .setPositiveButton("OK", (dialog, which) -> dialog.dismiss())
                    .setNegativeButton(negativeButtonText, (dialog, which) -> {
                        // Open ChildDashboardActivity with appropriate action
                        Intent intent = new Intent(this, ChildDashboardActivity.class);
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
                        if (reason == BlockReason.SCREEN_TIME) {
                            intent.putExtra("action", "request_time");
                        } else {
                            intent.putExtra("action", "request_access");
                        }
                        startActivity(intent);
                    })
                    .setCancelable(true);
            
            android.app.AlertDialog dialog = builder.create();
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                dialog.getWindow().setType(android.view.WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY);
            } else {
                dialog.getWindow().setType(android.view.WindowManager.LayoutParams.TYPE_SYSTEM_ALERT);
            }
            
            dialog.show();
            
            // Style the dialog
            try {
                int titleId = getResources().getIdentifier("alertTitle", "id", "android");
                if (titleId > 0) {
                    android.widget.TextView titleView = dialog.findViewById(titleId);
                    if (titleView != null) {
                        if (reason == BlockReason.SCREEN_TIME) {
                            titleView.setTextColor(0xFFFF9800); // Orange for time limit
                        } else {
                            titleView.setTextColor(0xFF26C6DA); // Cyan for blocked app
                        }
                    }
                }
                
                android.widget.Button positiveBtn = dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE);
                android.widget.Button negativeBtn = dialog.getButton(android.app.AlertDialog.BUTTON_NEGATIVE);
                
                if (positiveBtn != null) {
                    positiveBtn.setTextColor(0xFF26C6DA);
                    positiveBtn.setAllCaps(false);
                }
                if (negativeBtn != null) {
                    negativeBtn.setTextColor(0xFF26C6DA);
                    negativeBtn.setAllCaps(false);
                }
            } catch (Exception e) {
                // Ignore styling errors
            }
        } catch (Exception e) {
            Log.e(TAG, "Error showing blocking dialog: " + e.getMessage(), e);
        }
    }

    private boolean shouldSendAlert(String packageName, long currentTime) {
        try {
            SharedPreferences prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            String lastPackage = prefs.getString(PREF_LAST_ALERT_PACKAGE, null);
            long lastTime = prefs.getLong(PREF_LAST_ALERT_TIME, 0);
            
            return !packageName.equals(lastPackage) || (currentTime - lastTime) >= ALERT_COOLDOWN;
        } catch (Exception e) {
            return true;
        }
    }
    
    private void saveLastAlert(String packageName, long currentTime) {
        try {
            SharedPreferences prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            prefs.edit()
                .putString(PREF_LAST_ALERT_PACKAGE, packageName)
                .putLong(PREF_LAST_ALERT_TIME, currentTime)
                .apply();
        } catch (Exception e) {
            Log.e(TAG, "Error saving alert info: " + e.getMessage());
        }
    }
    
    private void sendBlockedAppAlert(String packageName, BlockReason reason) {
        try {
            String appName = getAppName(packageName);
            
            FirebaseHelper.getUserRef(childUid).child("parentId")
                    .addListenerForSingleValueEvent(new ValueEventListener() {
                        @Override
                        public void onDataChange(@NonNull DataSnapshot snapshot) {
                            String parentId = snapshot.getValue(String.class);
                            if (parentId != null) {
                                String alertType = reason == BlockReason.SCREEN_TIME ? "SCREEN_TIME_BLOCK" : "BLOCKED_APP";
                                String title = reason == BlockReason.SCREEN_TIME ? 
                                        childName + " tried to use app after time limit" :
                                        childName + " tried to access a blocked app";
                                
                                AlertHelper.sendCustomAlert(
                                        parentId,
                                        childUid,
                                        childName != null ? childName : "Child",
                                        alertType,
                                        title,
                                        "App: " + appName
                                );
                            }
                        }

                        @Override
                        public void onCancelled(@NonNull DatabaseError error) {
                            Log.e(TAG, "Failed to get parent ID: " + error.getMessage());
                        }
                    });
        } catch (Exception e) {
            Log.e(TAG, "Error sending alert: " + e.getMessage(), e);
        }
    }

    private String getAppName(String packageName) {
        try {
            android.content.pm.PackageManager pm = getPackageManager();
            android.content.pm.ApplicationInfo appInfo = pm.getApplicationInfo(packageName, 0);
            return pm.getApplicationLabel(appInfo).toString();
        } catch (android.content.pm.PackageManager.NameNotFoundException e) {
            return packageName;
        }
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "App Protection",
                    NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("Protects against blocked apps");
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
                this, 0, notificationIntent, PendingIntent.FLAG_IMMUTABLE
        );

        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Safe Zone Protection")
                .setContentText("App blocking active")
                .setSmallIcon(R.drawable.logo2)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (handler != null && blockingRunnable != null) {
            handler.removeCallbacks(blockingRunnable);
        }
        // Remove parent link listener
        if (parentLinkListener != null && childUid != null) {
            FirebaseHelper.getUsersRef().child(childUid).child("parentId")
                    .removeEventListener(parentLinkListener);
        }
        Log.d(TAG, "Service destroyed");
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
