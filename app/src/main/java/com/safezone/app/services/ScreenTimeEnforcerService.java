package com.safezone.app.services;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
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
import com.safezone.app.utils.FirebaseHelper;

import java.util.ArrayList;
import java.util.List;

/**
 * Service to enforce screen time limits
 * This service only tracks time and sets a flag for AppBlockingService to use
 * The actual blocking is done by AppBlockingService
 */
public class ScreenTimeEnforcerService extends Service {

    private static final String TAG = "ScreenTimeEnforcer";
    private static final String CHANNEL_ID = "screen_time_channel";
    private static final int NOTIFICATION_ID = 1002;
    private static final long CHECK_INTERVAL = 15 * 1000; // Check every 15 seconds

    // Shared preferences for screen time state
    public static final String PREFS_NAME = "ScreenTimePrefs";
    public static final String PREF_LIMIT_EXCEEDED = "limitExceeded";
    public static final String PREF_ALLOWED_APPS = "allowedApps";
    public static final String PREF_SCREEN_TIME_ENABLED = "screenTimeEnabled";
    public static final String PREF_RESTRICTION_START_TIME = "restrictionStartTime";
    public static final String PREF_ACCUMULATED_USAGE = "accumulatedUsage";

    private Handler handler;
    private Runnable enforcementRunnable;
    private DatabaseReference rulesRef;
    private String childUid;

    // Screen time rules
    private boolean screenTimeEnabled = false;
    private boolean wasEnabled = false; // Track previous state to detect ON/OFF changes
    private int dailyLimitMinutes = 120;
    private long totalUsageToday = 0;
    private boolean warningNotificationSent = false;
    private boolean limitReachedNotificationSent = false;
    private List<String> allowedApps = new ArrayList<>();
    
    // Tracking for fresh start - using elapsed time approach
    private long restrictionStartTime = 0;  // When restriction was enabled
    private long accumulatedUsage = 0;      // Accumulated usage in milliseconds
    private long lastCheckTime = 0;         // Last time we checked (for calculating delta)
    
    // Parent link validation
    private boolean isLinkedToParent = false;
    private ValueEventListener parentLinkListener;

    // Default allowed apps (always accessible) - Only essential apps
    // Dialer and Contacts for emergency calls, SafeZone app itself
    private static final List<String> DEFAULT_ALLOWED_APPS = new ArrayList<String>() {{
        add("com.android.dialer");
        add("com.google.android.dialer");
        add("com.samsung.android.dialer");
        add("com.android.contacts");
        add("com.google.android.contacts");
    }};

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "ScreenTimeEnforcer created");

        // Load saved restriction timer state
        loadRestrictionTimerState();

        childUid = FirebaseHelper.getCurrentUserId();
        if (childUid != null) {
            rulesRef = FirebaseHelper.getUsersRef().child(childUid).child("screenTimeRules");
            listenToParentLinkStatus(); // Check if still linked to parent
            listenToRulesChanges();
        } else {
            // No user logged in - clear all restrictions
            clearAllRestrictions();
        }

        createNotificationChannel();
        startForeground(NOTIFICATION_ID, createNotification("Screen time monitoring active"));

        startEnforcement();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "Service started");
        return START_STICKY;
    }

    /**
     * Listen to parent link status - if child is unlinked, clear all restrictions
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
                
                Log.d(TAG, "Parent link status: " + (isLinkedToParent ? "LINKED to " + parentId : "NOT LINKED"));
                
                // If child was linked but now unlinked, clear all restrictions
                if (wasLinked && !isLinkedToParent) {
                    Log.d(TAG, "Child UNLINKED from parent - clearing all screen time restrictions");
                    clearAllRestrictions();
                }
                
                // If not linked at all, ensure restrictions are cleared
                if (!isLinkedToParent) {
                    clearAllRestrictions();
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
     * Clear all screen time restrictions - called when child is unlinked or account deleted
     */
    private void clearAllRestrictions() {
        Log.d(TAG, "CLEARING ALL SCREEN TIME RESTRICTIONS");
        
        // Reset all state
        screenTimeEnabled = false;
        wasEnabled = false;
        restrictionStartTime = 0;
        accumulatedUsage = 0;
        
        // Clear SharedPreferences
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit()
            .putBoolean(PREF_LIMIT_EXCEEDED, false)
            .putBoolean(PREF_SCREEN_TIME_ENABLED, false)
            .putLong(PREF_RESTRICTION_START_TIME, 0)
            .putLong(PREF_ACCUMULATED_USAGE, 0)
            .putString(PREF_ALLOWED_APPS, "")
            .apply();
        
        // Reset notification flags
        warningNotificationSent = false;
        limitReachedNotificationSent = false;
        
        Log.d(TAG, "All restrictions cleared - child can use all apps freely");
    }
    
    /**
     * Check if child account still exists in Firebase
     */
    private void checkAccountExists() {
        if (childUid == null) {
            clearAllRestrictions();
            return;
        }
        
        FirebaseHelper.getUsersRef().child(childUid).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (!snapshot.exists()) {
                    Log.d(TAG, "Child account no longer exists - clearing restrictions");
                    clearAllRestrictions();
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e(TAG, "Failed to check account existence: " + error.getMessage());
            }
        });
    }

    private void listenToRulesChanges() {
        rulesRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (snapshot.exists()) {
                    Boolean enabled = snapshot.child("enabled").getValue(Boolean.class);
                    Integer dailyLimit = snapshot.child("dailyLimitMinutes").getValue(Integer.class);

                    boolean newEnabled = enabled != null && enabled;
                    int newLimit = dailyLimit != null ? dailyLimit : 120;
                    
                    // Detect when restriction is turned ON (was OFF, now ON)
                    // OR when the time limit changes - ALWAYS reset timer
                    boolean shouldReset = false;
                    
                    if (newEnabled && !wasEnabled) {
                        // Restriction just turned ON
                        Log.d(TAG, "Screen time restriction ENABLED - resetting timer to start fresh");
                        shouldReset = true;
                    } else if (newEnabled && newLimit != dailyLimitMinutes) {
                        // Limit changed while enabled - reset timer for fresh start
                        Log.d(TAG, "Time limit CHANGED from " + dailyLimitMinutes + " to " + newLimit + " - resetting timer to start fresh");
                        shouldReset = true;
                    }
                    
                    if (shouldReset) {
                        resetRestrictionTimer();
                    }
                    
                    // Update state
                    wasEnabled = screenTimeEnabled;
                    screenTimeEnabled = newEnabled;
                    dailyLimitMinutes = newLimit;

                    // Load allowed apps
                    allowedApps.clear();
                    allowedApps.addAll(DEFAULT_ALLOWED_APPS);
                    allowedApps.add(getPackageName()); // Always allow Safe Zone
                    
                    DataSnapshot allowedAppsSnapshot = snapshot.child("allowedApps");
                    if (allowedAppsSnapshot.exists()) {
                        for (DataSnapshot appSnapshot : allowedAppsSnapshot.getChildren()) {
                            String packageName = appSnapshot.getValue(String.class);
                            if (packageName != null && !allowedApps.contains(packageName)) {
                                allowedApps.add(packageName);
                            }
                        }
                    }

                    // Save to shared preferences for AppBlockingService to use
                    saveScreenTimeState();
                    
                    // Reset notification flags when settings change
                    warningNotificationSent = false;
                    limitReachedNotificationSent = false;

                    Log.d(TAG, "Rules updated - Enabled: " + screenTimeEnabled +
                            ", Limit: " + dailyLimitMinutes + " mins, Allowed apps: " + allowedApps.size());
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e(TAG, "Failed to load rules: " + error.getMessage());
            }
        });
    }
    
    /**
     * Reset the restriction timer - called when parent turns ON the restriction
     * or when time limit changes. This makes the timer start fresh from 0.
     */
    private void resetRestrictionTimer() {
        long currentTime = System.currentTimeMillis();
        
        // Reset everything to start fresh
        restrictionStartTime = currentTime;
        accumulatedUsage = 0;
        lastCheckTime = currentTime;
        
        // Save to preferences so it persists
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit()
            .putLong(PREF_RESTRICTION_START_TIME, restrictionStartTime)
            .putLong(PREF_ACCUMULATED_USAGE, 0)
            .apply();
        
        // Clear limit exceeded flag
        setLimitExceeded(false);
        
        // IMPORTANT: Also reset Firebase usage to 0 immediately
        // This ensures dashboard shows correct value right away
        updateUsageInFirebase(0);
        
        Log.d(TAG, "Timer RESET - Starting fresh from 0 at time: " + restrictionStartTime);
    }
    
    /**
     * Load saved restriction timer state
     */
    private void loadRestrictionTimerState() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        restrictionStartTime = prefs.getLong(PREF_RESTRICTION_START_TIME, 0);
        accumulatedUsage = prefs.getLong(PREF_ACCUMULATED_USAGE, 0);
        lastCheckTime = System.currentTimeMillis();
        
        Log.d(TAG, "Loaded timer state - Start time: " + restrictionStartTime + 
                   ", Accumulated usage: " + (accumulatedUsage / 60000) + " mins");
    }
    
    /**
     * Save accumulated usage to preferences
     */
    private void saveAccumulatedUsage() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit()
            .putLong(PREF_ACCUMULATED_USAGE, accumulatedUsage)
            .apply();
    }

    /**
     * Save screen time state to shared preferences for AppBlockingService
     */
    private void saveScreenTimeState() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        editor.putBoolean(PREF_SCREEN_TIME_ENABLED, screenTimeEnabled);
        
        // Save allowed apps as comma-separated string
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < allowedApps.size(); i++) {
            String app = allowedApps.get(i);
            if (app != null && !app.isEmpty()) {
                if (sb.length() > 0) {
                    sb.append(",");
                }
                sb.append(app);
            }
        }
        String allowedAppsStr = sb.toString();
        editor.putString(PREF_ALLOWED_APPS, allowedAppsStr);
        editor.apply();
        
        Log.d(TAG, "Saved allowed apps to prefs: " + allowedAppsStr);
        Log.d(TAG, "Total allowed apps count: " + allowedApps.size());
    }

    /**
     * Set limit exceeded flag for AppBlockingService
     */
    private void setLimitExceeded(boolean exceeded) {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().putBoolean(PREF_LIMIT_EXCEEDED, exceeded).apply();
        Log.d(TAG, "Limit exceeded flag set to: " + exceeded);
    }

    private void startEnforcement() {
        handler = new Handler(Looper.getMainLooper());
        enforcementRunnable = new Runnable() {
            @Override
            public void run() {
                checkAndEnforceLimit();
                handler.postDelayed(this, CHECK_INTERVAL);
            }
        };
        handler.post(enforcementRunnable);
    }

    private void checkAndEnforceLimit() {
        try {
            // CRITICAL: If not linked to parent, don't enforce any restrictions
            if (!isLinkedToParent) {
                setLimitExceeded(false);
                return;
            }
            
            long currentTime = System.currentTimeMillis();
            
            // Calculate current usage in minutes BEFORE adding more time
            long currentUsedMinutes = accumulatedUsage / (1000 * 60);
            boolean isCurrentlyExceeded = currentUsedMinutes >= dailyLimitMinutes;
            
            // Only accumulate time if:
            // 1. Screen time is enabled
            // 2. Restriction has started
            // 3. Limit is NOT yet exceeded (stop counting when blocked)
            if (screenTimeEnabled && restrictionStartTime > 0 && !isCurrentlyExceeded) {
                // Calculate time elapsed since last check
                if (lastCheckTime > 0) {
                    long elapsed = currentTime - lastCheckTime;
                    // Only add if reasonable (less than 2 minutes - to handle sleep/pause)
                    if (elapsed > 0 && elapsed < 120000) {
                        accumulatedUsage += elapsed;
                        saveAccumulatedUsage();
                    }
                }
            }
            lastCheckTime = currentTime;
            
            // Calculate usage in minutes (cap at limit for display)
            long usedMinutes = accumulatedUsage / (1000 * 60);
            long remainingMinutes = dailyLimitMinutes - usedMinutes;
            
            // Cap usage at limit for Firebase display (don't show 20/15)
            long displayUsedMinutes = Math.min(usedMinutes, dailyLimitMinutes);

            Log.d(TAG, "Used: " + usedMinutes + " mins, Remaining: " + remainingMinutes + 
                       " mins, Enabled: " + screenTimeEnabled + ", Limit: " + dailyLimitMinutes);

            // Update usage in Firebase for parent/child to see (capped at limit)
            updateUsageInFirebase(displayUsedMinutes);

            // Check if limit exceeded
            if (screenTimeEnabled && usedMinutes >= dailyLimitMinutes) {
                // Set flag for AppBlockingService
                setLimitExceeded(true);
                
                if (!limitReachedNotificationSent) {
                    sendLimitReachedNotification();
                    limitReachedNotificationSent = true;
                    notifyParent(dailyLimitMinutes); // Send limit value, not exceeded value
                }
            } else if (screenTimeEnabled && remainingMinutes <= 5 && remainingMinutes > 0 && !warningNotificationSent) {
                sendWarningNotification(remainingMinutes);
                warningNotificationSent = true;
            } else if (!screenTimeEnabled || usedMinutes < dailyLimitMinutes) {
                // Reset exceeded flag if under limit or disabled
                setLimitExceeded(false);
                
                if (remainingMinutes > 5) {
                    warningNotificationSent = false;
                }
                if (!screenTimeEnabled) {
                    limitReachedNotificationSent = false;
                }
            }

        } catch (Exception e) {
            Log.e(TAG, "Error checking screen time: " + e.getMessage(), e);
        }
    }



    private void updateUsageInFirebase(long usedMinutes) {
        if (childUid == null) return;
        
        FirebaseHelper.getUsersRef().child(childUid)
                .child("screenTimeUsage")
                .child("todayMinutes")
                .setValue(usedMinutes);
    }

    private void notifyParent(int usedMinutes) {
        if (childUid == null) return;

        FirebaseHelper.getUsersRef().child(childUid).addListenerForSingleValueEvent(
                new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        String parentId = snapshot.child("parentId").getValue(String.class);
                        String childName = snapshot.child("name").getValue(String.class);

                        if (parentId != null && childName != null) {
                            String alertId = FirebaseHelper.getDatabase().getReference()
                                    .child("alerts").push().getKey();

                            if (alertId != null) {
                                com.safezone.app.models.Alert alert = new com.safezone.app.models.Alert(
                                        alertId,
                                        parentId,
                                        childUid,
                                        childName,
                                        com.safezone.app.models.Alert.Type.SCREEN_TIME,
                                        childName + " has reached screen time limit",
                                        "Used: " + usedMinutes + "/" + dailyLimitMinutes + " minutes",
                                        System.currentTimeMillis()
                                );

                                FirebaseHelper.getDatabase().getReference()
                                        .child("alerts")
                                        .child(alertId)
                                        .setValue(alert);
                            }
                        }
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        Log.e(TAG, "Error notifying parent: " + error.getMessage());
                    }
                }
        );
    }

    private void sendWarningNotification(long remainingMinutes) {
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.logo2)
                .setContentTitle("Screen Time Warning")
                .setContentText(remainingMinutes + " minutes remaining today")
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setVibrate(new long[]{0, 500, 200, 500});

        NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager != null) {
            manager.notify(NOTIFICATION_ID + 1, builder.build());
        }
    }

    private void sendLimitReachedNotification() {
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.logo2)
                .setContentTitle("Screen Time Limit Reached")
                .setContentText("Daily limit of " + dailyLimitMinutes + " minutes exceeded.")
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setOngoing(true)
                .setVibrate(new long[]{0, 1000, 500, 1000});

        NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager != null) {
            manager.notify(NOTIFICATION_ID, builder.build());
        }
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Screen Time Enforcement",
                    NotificationManager.IMPORTANCE_HIGH
            );
            channel.setDescription("Enforces screen time limits");
            channel.enableVibration(true);

            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }

    private Notification createNotification(String text) {
        Intent notificationIntent = new Intent(this, ChildDashboardActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this, 0, notificationIntent, PendingIntent.FLAG_IMMUTABLE
        );

        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Safe Zone")
                .setContentText(text)
                .setSmallIcon(R.drawable.logo2)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (handler != null && enforcementRunnable != null) {
            handler.removeCallbacks(enforcementRunnable);
        }
        // Remove parent link listener
        if (parentLinkListener != null && childUid != null) {
            FirebaseHelper.getUsersRef().child(childUid).child("parentId")
                    .removeEventListener(parentLinkListener);
        }
        // Clear limit exceeded flag when service stops
        setLimitExceeded(false);
        Log.d(TAG, "Service destroyed");
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
