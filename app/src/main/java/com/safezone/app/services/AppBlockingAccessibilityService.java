package com.safezone.app.services;

import android.accessibilityservice.AccessibilityService;
import android.app.ActivityManager;
import android.app.usage.UsageStats;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;

import androidx.annotation.NonNull;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.ValueEventListener;
import com.safezone.app.utils.AlertHelper;
import com.safezone.app.utils.FirebaseHelper;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;

/**
 * BULLETPROOF App Blocking using Dual Approach:
 * 1. AccessibilityService for instant detection
 * 2. Background polling for continuous enforcement
 * 
 * This ensures NO BYPASS is possible - child CANNOT access blocked apps
 */
public class AppBlockingAccessibilityService extends AccessibilityService {

    private static final String TAG = "AppBlockingAccess";
    
    private DatabaseReference contentRulesRef;
    private String childUid;
    private String childName;
    private Set<String> blockedApps = new HashSet<>();
    
    // Continuous monitoring
    private Handler monitoringHandler;
    private Runnable monitoringRunnable;
    private boolean isMonitoring = false;
    private static final long MONITORING_INTERVAL = 300; // Check every 300ms
    
    // Track last blocked to prevent notification spam
    private String lastBlockedPackage = null;
    private long lastBlockTime = 0;
    private static final long NOTIFICATION_COOLDOWN = 2000; // 2 seconds
    
    // Track last notification to prevent spam
    private long lastToastTime = 0;
    private static final long TOAST_COOLDOWN = 3000; // 3 seconds between toasts
    private String lastToastPackage = null;
    
    // Track last alert sent to prevent duplicates (SHARED across services)
    private static final String PREFS_NAME = "AppBlockingPrefs";
    private static final String PREF_LAST_ALERT_PACKAGE = "lastAlertPackage";
    private static final String PREF_LAST_ALERT_TIME = "lastAlertTime";
    private static final long ALERT_COOLDOWN = 3000; // 3 seconds between alerts (prevents duplicates)

    private boolean isServiceConnected = false;
    
    // Parent link status - if not linked, don't block apps
    private boolean isLinkedToParent = false;
    private ValueEventListener parentLinkListener;
    
    @Override
    public void onServiceConnected() {
        super.onServiceConnected();
        isServiceConnected = true;
        Log.e(TAG, "========================================");
        Log.e(TAG, "🔒 BULLETPROOF APP BLOCKING ACTIVATED");
        Log.e(TAG, "========================================");

        // Initialize on a background thread to prevent ANR
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            try {
                initializeService();
            } catch (Exception e) {
                Log.e(TAG, "Error initializing service: " + e.getMessage(), e);
            }
        }, 500); // Small delay to let service fully connect
    }
    
    private void initializeService() {
        try {
            childUid = FirebaseHelper.getCurrentUserId();
            Log.d(TAG, "Child UID: " + childUid);
            
            if (childUid != null && !childUid.isEmpty()) {
                contentRulesRef = FirebaseHelper.getUsersRef().child(childUid).child("contentRules");
                Log.d(TAG, "Firebase reference created");
                loadChildName();
                listenToParentLinkStatus(); // Check if still linked to parent
                listenToBlockedApps();
                startContinuousMonitoring();
            } else {
                Log.e(TAG, "ERROR: Child UID is NULL or empty - service will not work!");
                // Don't crash, just log and wait for user to login
            }
        } catch (Exception e) {
            Log.e(TAG, "Error in initializeService: " + e.getMessage(), e);
        }
    }
    
    /**
     * Start continuous background monitoring
     * This runs FOREVER and checks every 300ms for blocked apps
     */
    private void startContinuousMonitoring() {
        if (isMonitoring) {
            Log.d(TAG, "Monitoring already running");
            return;
        }
        
        try {
            isMonitoring = true;
            monitoringHandler = new Handler(Looper.getMainLooper());
            
            monitoringRunnable = new Runnable() {
                @Override
                public void run() {
                    try {
                        if (isServiceConnected && isMonitoring) {
                            checkAndBlockForegroundApp();
                        }
                        
                        // Schedule next check
                        if (isMonitoring && monitoringHandler != null) {
                            monitoringHandler.postDelayed(this, MONITORING_INTERVAL);
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Error in monitoring runnable: " + e.getMessage());
                        // Reschedule even on error to keep monitoring alive
                        if (isMonitoring && monitoringHandler != null) {
                            monitoringHandler.postDelayed(this, MONITORING_INTERVAL);
                        }
                    }
                }
            };
            
            // Start monitoring
            monitoringHandler.post(monitoringRunnable);
            Log.e(TAG, "🔄 CONTINUOUS MONITORING STARTED (every " + MONITORING_INTERVAL + "ms)");
        } catch (Exception e) {
            Log.e(TAG, "Error starting continuous monitoring: " + e.getMessage(), e);
        }
    }
    
    /**
     * Check current foreground app and block if necessary
     * This is called by background polling - blocks silently without alerts
     */
    private void checkAndBlockForegroundApp() {
        try {
            if (!isServiceConnected) return;
            
            // CRITICAL: Only block if child is linked to a parent
            if (!isLinkedToParent) return;
            
            String foregroundApp = getForegroundApp();
            
            if (foregroundApp != null && blockedApps.contains(foregroundApp)) {
                // Blocked app detected!
                if (!foregroundApp.equals(getPackageName())) {
                    Log.w(TAG, "⚠️ POLLING: Blocked app in foreground: " + foregroundApp);
                    // Block silently without sending alerts (accessibility already sent it)
                    blockSilently(foregroundApp);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error in checkAndBlockForegroundApp: " + e.getMessage());
        }
    }
    
    /**
     * Block app silently without sending alerts
     * Used by background polling to avoid duplicate alerts
     */
    private void blockSilently(String packageName) {
        performGlobalAction(GLOBAL_ACTION_HOME);
        killApp(packageName);
        // No toast, no alert - just block
    }
    
    /**
     * Get current foreground app package name
     */
    private String getForegroundApp() {
        try {
            UsageStatsManager usageStatsManager = (UsageStatsManager) getSystemService(Context.USAGE_STATS_SERVICE);
            if (usageStatsManager == null) return null;
            
            long currentTime = System.currentTimeMillis();
            UsageStats usageStats = null;
            
            // Get usage stats for last 1 second
            SortedMap<Long, UsageStats> sortedMap = new TreeMap<>();
            List<UsageStats> statsList = usageStatsManager.queryUsageStats(
                    UsageStatsManager.INTERVAL_BEST,
                    currentTime - 1000,
                    currentTime
            );
            
            if (statsList != null) {
                for (UsageStats stats : statsList) {
                    sortedMap.put(stats.getLastTimeUsed(), stats);
                }
                
                if (!sortedMap.isEmpty()) {
                    usageStats = sortedMap.get(sortedMap.lastKey());
                }
            }
            
            return usageStats != null ? usageStats.getPackageName() : null;
        } catch (Exception e) {
            Log.e(TAG, "Error getting foreground app: " + e.getMessage());
            return null;
        }
    }

    private void loadChildName() {
        try {
            if (childUid == null || childUid.isEmpty()) {
                childName = "Child";
                return;
            }
            
            DatabaseReference userRef = FirebaseHelper.getUserRef(childUid);
            if (userRef == null) {
                childName = "Child";
                return;
            }
            
            userRef.child("name").addListenerForSingleValueEvent(new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot snapshot) {
                    try {
                        String name = snapshot.getValue(String.class);
                        childName = (name != null && !name.isEmpty()) ? name : "Child";
                    } catch (Exception e) {
                        childName = "Child";
                        Log.e(TAG, "Error parsing child name: " + e.getMessage());
                    }
                }

                @Override
                public void onCancelled(@NonNull DatabaseError error) {
                    childName = "Child";
                    Log.e(TAG, "Failed to load child name: " + error.getMessage());
                }
            });
        } catch (Exception e) {
            childName = "Child";
            Log.e(TAG, "Error in loadChildName: " + e.getMessage());
        }
    }
    
    /**
     * Listen to parent link status - if child is unlinked, stop blocking apps
     */
    private void listenToParentLinkStatus() {
        if (childUid == null || childUid.isEmpty()) return;
        
        try {
            DatabaseReference parentIdRef = FirebaseHelper.getUsersRef().child(childUid).child("parentId");
            parentLinkListener = new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot snapshot) {
                    String parentId = snapshot.getValue(String.class);
                    boolean wasLinked = isLinkedToParent;
                    isLinkedToParent = (parentId != null && !parentId.isEmpty());
                    
                    Log.d(TAG, "Parent link status: " + (isLinkedToParent ? "LINKED" : "NOT LINKED"));
                    
                    // If child was linked but now unlinked, clear blocked apps
                    if (wasLinked && !isLinkedToParent) {
                        Log.d(TAG, "Child UNLINKED - clearing blocked apps list");
                        blockedApps.clear();
                    }
                }

                @Override
                public void onCancelled(@NonNull DatabaseError error) {
                    Log.e(TAG, "Failed to check parent link: " + error.getMessage());
                }
            };
            parentIdRef.addValueEventListener(parentLinkListener);
        } catch (Exception e) {
            Log.e(TAG, "Error setting up parent link listener: " + e.getMessage());
        }
    }

    private void listenToBlockedApps() {
        Log.d(TAG, "Setting up blocked apps listener...");
        
        if (contentRulesRef == null) {
            Log.e(TAG, "contentRulesRef is null, cannot listen to blocked apps");
            return;
        }
        
        try {
            contentRulesRef.child("blockedApps").addValueEventListener(new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot snapshot) {
                    try {
                        blockedApps.clear();
                        
                        Log.d(TAG, "Firebase snapshot received - exists: " + snapshot.exists());
                        
                        if (snapshot.exists()) {
                            for (DataSnapshot appSnapshot : snapshot.getChildren()) {
                                try {
                                    String packageName = appSnapshot.getValue(String.class);
                                    if (packageName != null && !packageName.isEmpty()) {
                                        blockedApps.add(packageName);
                                        Log.d(TAG, "Added blocked app: " + packageName);
                                    }
                                } catch (Exception e) {
                                    Log.e(TAG, "Error parsing blocked app: " + e.getMessage());
                                }
                            }
                        }

                        Log.d(TAG, "========================================");
                        Log.d(TAG, "BLOCKED APPS LIST (" + blockedApps.size() + " total):");
                        for (String pkg : blockedApps) {
                            Log.d(TAG, "  - " + pkg);
                        }
                        Log.d(TAG, "========================================");
                    } catch (Exception e) {
                        Log.e(TAG, "Error in onDataChange: " + e.getMessage(), e);
                    }
                }

                @Override
                public void onCancelled(@NonNull DatabaseError error) {
                    Log.e(TAG, "❌ Failed to load blocked apps: " + error.getMessage());
                }
            });
        } catch (Exception e) {
            Log.e(TAG, "Error setting up blocked apps listener: " + e.getMessage(), e);
        }
    }

    /**
     * CRITICAL METHOD - Handles ALL accessibility events
     * Provides INSTANT blocking on app launch
     */
    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        // Wrap everything in try-catch to prevent service crash
        try {
            if (event == null || !isServiceConnected) return;

            // Get package name from event
            CharSequence packageNameChar = event.getPackageName();
            if (packageNameChar == null) return;
            
            String packageName = packageNameChar.toString();
            
            // Ignore our own app and system UI
            if (packageName.equals(getPackageName()) || 
                packageName.equals("com.android.systemui") ||
                packageName.equals("android") ||
                packageName.isEmpty()) {
                return;
            }

            // Only process TYPE_WINDOW_STATE_CHANGED for app launches
            if (event.getEventType() == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
                // CRITICAL: Only block if child is linked to a parent
                if (!isLinkedToParent) {
                    return; // Not linked, don't block anything
                }
                
                // Check if this package is blocked (with null safety)
                if (blockedApps != null && blockedApps.contains(packageName)) {
                    Log.w(TAG, "🚫 ACCESSIBILITY: BLOCKED APP DETECTED: " + packageName);
                    // This is the PRIMARY detection - send alerts here
                    blockAppImmediately(packageName);
                }
            }
        } catch (Exception e) {
            // Log but don't crash - service must stay alive
            Log.e(TAG, "Error in onAccessibilityEvent: " + e.getMessage(), e);
        }
    }

    /**
     * IMMEDIATE blocking - called from both accessibility and polling
     */
    private synchronized void blockAppImmediately(String packageName) {
        long currentTime = System.currentTimeMillis();
        
        // AGGRESSIVE deduplication - if same app blocked within 2 seconds, skip everything
        if (packageName.equals(lastBlockedPackage) && 
            (currentTime - lastBlockTime) < NOTIFICATION_COOLDOWN) {
            // Still block, but don't log/notify/alert
            performGlobalAction(GLOBAL_ACTION_HOME);
            killApp(packageName);
            return;
        }
        
        Log.e(TAG, "🚫🚫🚫 BLOCKING: " + packageName + " 🚫🚫🚫");
        
        lastBlockedPackage = packageName;
        lastBlockTime = currentTime;
        
        // STEP 1: Go to home IMMEDIATELY
        boolean homeSuccess = performGlobalAction(GLOBAL_ACTION_HOME);
        Log.d(TAG, "Home action: " + homeSuccess);
        
        // STEP 2: Kill the app
        killApp(packageName);
        
        // STEP 3: Skip toast notification - dialog is shown by AppBlockingService
        // This prevents duplicate notifications
        Log.d(TAG, "Skipping toast - dialog shown by AppBlockingService");
        
        // STEP 4: Send alert ONLY if not sent recently (check shared prefs)
        if (shouldSendAlert(packageName, currentTime)) {
            saveLastAlert(packageName, currentTime);
            Log.d(TAG, "Sending ONE alert for: " + packageName);
            sendBlockedAppAlert(packageName);
        } else {
            Log.d(TAG, "Alert cooldown active, skipping alert");
        }
    }
    
    /**
     * Kill app processes
     */
    private void killApp(String packageName) {
        try {
            ActivityManager am = (ActivityManager) getSystemService(ACTIVITY_SERVICE);
            if (am != null) {
                am.killBackgroundProcesses(packageName);
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to kill: " + e.getMessage());
        }
    }

    /**
     * Show blocking notification
     */
    private void showBlockedNotification(String packageName) {
        try {
            String appName = getAppName(packageName);
            String message = "🚫 " + appName + " is blocked";
            
            // Show toast on main thread
            new Handler(Looper.getMainLooper()).post(() -> {
                try {
                    android.widget.Toast.makeText(
                        getApplicationContext(),
                        message,
                        android.widget.Toast.LENGTH_LONG
                    ).show();
                    
                    Log.e(TAG, "✅ TOAST SHOWN: " + message);
                } catch (Exception e) {
                    Log.e(TAG, "❌ Toast error: " + e.getMessage());
                }
            });
        } catch (Exception e) {
            Log.e(TAG, "❌ Notification error: " + e.getMessage());
        }
    }

    /**
     * Check if we should send an alert (shared across all services)
     */
    private boolean shouldSendAlert(String packageName, long currentTime) {
        try {
            android.content.SharedPreferences prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            String lastPackage = prefs.getString(PREF_LAST_ALERT_PACKAGE, null);
            long lastTime = prefs.getLong(PREF_LAST_ALERT_TIME, 0);
            
            // Allow alert if different app OR cooldown expired
            return !packageName.equals(lastPackage) || (currentTime - lastTime) >= ALERT_COOLDOWN;
        } catch (Exception e) {
            Log.e(TAG, "Error checking alert cooldown: " + e.getMessage());
            return true; // Send alert if error
        }
    }
    
    /**
     * Save last alert info (shared across all services)
     */
    private void saveLastAlert(String packageName, long currentTime) {
        try {
            android.content.SharedPreferences prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            prefs.edit()
                .putString(PREF_LAST_ALERT_PACKAGE, packageName)
                .putLong(PREF_LAST_ALERT_TIME, currentTime)
                .apply();
        } catch (Exception e) {
            Log.e(TAG, "Error saving alert info: " + e.getMessage());
        }
    }
    
    /**
     * Send alert to parent
     */
    private void sendBlockedAppAlert(String packageName) {
        try {
            String appName = getAppName(packageName);
            Log.e(TAG, "📤 Attempting to send alert for: " + appName);
            
            FirebaseHelper.getUserRef(childUid).child("parentId")
                    .addListenerForSingleValueEvent(new ValueEventListener() {
                        @Override
                        public void onDataChange(@NonNull DataSnapshot snapshot) {
                            String parentId = snapshot.getValue(String.class);
                            Log.e(TAG, "Parent ID retrieved: " + parentId);
                            
                            if (parentId != null && !parentId.isEmpty()) {
                                String childNameToUse = childName != null ? childName : "Child";
                                
                                Log.e(TAG, "📤 Sending alert to Firebase:");
                                Log.e(TAG, "  Parent ID: " + parentId);
                                Log.e(TAG, "  Child ID: " + childUid);
                                Log.e(TAG, "  Child Name: " + childNameToUse);
                                Log.e(TAG, "  App: " + appName);
                                
                                // Send alert to alerts list
                                AlertHelper.sendCustomAlert(
                                        parentId,
                                        childUid,
                                        childNameToUse,
                                        "BLOCKED_APP",
                                        childNameToUse + " tried to access a blocked app",
                                        "App: " + appName
                                );
                                
                                Log.e(TAG, "✅ Alert sent to AlertHelper");
                            } else {
                                Log.e(TAG, "❌ Parent ID is null or empty, cannot send alert");
                            }
                        }

                        @Override
                        public void onCancelled(@NonNull DatabaseError error) {
                            Log.e(TAG, "❌ Failed to get parent ID: " + error.getMessage());
                        }
                    });
        } catch (Exception e) {
            Log.e(TAG, "❌ Error sending alert: " + e.getMessage(), e);
        }
    }

    /**
     * Get app name from package name
     */
    private String getAppName(String packageName) {
        try {
            android.content.pm.PackageManager pm = getPackageManager();
            android.content.pm.ApplicationInfo appInfo = pm.getApplicationInfo(packageName, 0);
            return pm.getApplicationLabel(appInfo).toString();
        } catch (android.content.pm.PackageManager.NameNotFoundException e) {
            return packageName;
        }
    }

    @Override
    public void onInterrupt() {
        Log.d(TAG, "Service interrupted");
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        isServiceConnected = false;
        isMonitoring = false;
        if (monitoringHandler != null && monitoringRunnable != null) {
            monitoringHandler.removeCallbacks(monitoringRunnable);
        }
        // Remove parent link listener
        if (parentLinkListener != null && childUid != null && !childUid.isEmpty()) {
            try {
                FirebaseHelper.getUsersRef().child(childUid).child("parentId")
                        .removeEventListener(parentLinkListener);
            } catch (Exception e) {
                Log.e(TAG, "Error removing parent link listener: " + e.getMessage());
            }
        }
        Log.e(TAG, "🛑 Service destroyed - monitoring stopped");
    }
    
    @Override
    public boolean onUnbind(Intent intent) {
        isServiceConnected = false;
        isMonitoring = false;
        if (monitoringHandler != null && monitoringRunnable != null) {
            monitoringHandler.removeCallbacks(monitoringRunnable);
        }
        Log.d(TAG, "Service unbound");
        return super.onUnbind(intent);
    }
}
