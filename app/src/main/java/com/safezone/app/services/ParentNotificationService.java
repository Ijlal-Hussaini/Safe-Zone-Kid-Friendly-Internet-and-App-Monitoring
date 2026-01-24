package com.safezone.app.services;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.AudioAttributes;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import com.google.firebase.database.ChildEventListener;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.ValueEventListener;
import com.safezone.app.R;
import com.safezone.app.activities.AlertsActivity;
import com.safezone.app.utils.FirebaseHelper;
import com.safezone.app.utils.NotificationHelper;

import java.util.HashSet;
import java.util.Set;

/**
 * Foreground service that listens for alerts and shows system notifications
 * Runs on parent's device only
 * 
 * FIXED: Now correctly reads alert fields (message, details instead of title)
 */
public class ParentNotificationService extends Service {

    private static final String TAG = "ParentNotifService";
    private static final String CHANNEL_ID = "parent_notifications";
    private static final String CHANNEL_ALERTS = "alerts_channel";
    private static final int FOREGROUND_ID = 9001;
    private static final String PREFS_NAME = "ParentNotificationPrefs";
    private static final String PREF_SHOWN_ALERTS = "shown_alerts";
    
    private DatabaseReference alertsRef;
    private ChildEventListener alertsListener;
    private String parentId;
    private long serviceStartTime;
    private Handler pollingHandler;
    private Runnable pollingRunnable;
    private Set<String> shownAlertIds = new HashSet<>();

    @Override
    public void onCreate() {
        super.onCreate();
        // Set service start time to 5 seconds ago to catch recent alerts
        serviceStartTime = System.currentTimeMillis() - 5000;
        Log.d(TAG, "========================================");
        Log.d(TAG, "🔔 NOTIFICATION SERVICE CREATED");
        Log.d(TAG, "Service start time: " + serviceStartTime);
        Log.d(TAG, "========================================");
        
        // Load previously shown alert IDs to prevent duplicates after restart
        loadShownAlerts();
        
        createNotificationChannels();
    }
    
    /**
     * Load shown alert IDs from SharedPreferences
     */
    private void loadShownAlerts() {
        try {
            SharedPreferences prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            Set<String> saved = prefs.getStringSet(PREF_SHOWN_ALERTS, new HashSet<>());
            shownAlertIds = new HashSet<>(saved);
            
            // Clean up old entries (keep only last 100)
            if (shownAlertIds.size() > 100) {
                shownAlertIds.clear();
                prefs.edit().remove(PREF_SHOWN_ALERTS).apply();
            }
            Log.d(TAG, "Loaded " + shownAlertIds.size() + " shown alert IDs");
        } catch (Exception e) {
            Log.e(TAG, "Error loading shown alerts: " + e.getMessage());
            shownAlertIds = new HashSet<>();
        }
    }
    
    /**
     * Save shown alert ID to prevent duplicate notifications
     */
    private void saveShownAlert(String alertId) {
        try {
            shownAlertIds.add(alertId);
            SharedPreferences prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            prefs.edit().putStringSet(PREF_SHOWN_ALERTS, shownAlertIds).apply();
        } catch (Exception e) {
            Log.e(TAG, "Error saving shown alert: " + e.getMessage());
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null) {
            parentId = intent.getStringExtra("parentId");
        }

        if (parentId == null) {
            Log.e(TAG, "❌ Parent ID is null, stopping service");
            stopSelf();
            return START_NOT_STICKY;
        }

        Log.d(TAG, "========================================");
        Log.d(TAG, "🚀 SERVICE STARTING for parent: " + parentId);
        Log.d(TAG, "========================================");

        // Start as foreground service
        try {
            startForeground(FOREGROUND_ID, createForegroundNotification());
            Log.d(TAG, "✅ Foreground service started");
        } catch (Exception e) {
            Log.e(TAG, "❌ Failed to start foreground: " + e.getMessage());
        }
        
        // Start listening for alerts
        startListeningForAlerts();
        
        Log.d(TAG, "✅ Service fully started for parent: " + parentId);
        return START_STICKY;
    }

    /**
     * Create foreground notification (required for Android O+)
     */
    private Notification createForegroundNotification() {
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle("Safe Zone")
                .setContentText("Monitoring your children's devices")
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setOngoing(true);

        return builder.build();
    }

    /**
     * Start listening for new alerts
     */
    private void startListeningForAlerts() {
        alertsRef = FirebaseHelper.getDatabase().getReference().child("alerts");

        alertsListener = new ChildEventListener() {
            @Override
            public void onChildAdded(@NonNull DataSnapshot snapshot, @Nullable String previousChildName) {
                Log.d(TAG, "🔔 onChildAdded: " + snapshot.getKey());
                handleNewAlert(snapshot);
            }

            @Override
            public void onChildChanged(@NonNull DataSnapshot snapshot, @Nullable String previousChildName) {
                // Ignore changes
            }

            @Override
            public void onChildRemoved(@NonNull DataSnapshot snapshot) {
                // Ignore removals
            }

            @Override
            public void onChildMoved(@NonNull DataSnapshot snapshot, @Nullable String previousChildName) {
                // Ignore moves
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e(TAG, "❌ Alerts listener cancelled: " + error.getMessage());
                // Restart listener after error
                new Handler(Looper.getMainLooper()).postDelayed(() -> {
                    if (alertsRef != null) {
                        alertsRef.addChildEventListener(alertsListener);
                    }
                }, 5000);
            }
        };

        alertsRef.addChildEventListener(alertsListener);
        Log.d(TAG, "👂 LISTENING FOR ALERTS on /alerts for parent: " + parentId);
        
        // BACKUP: Also poll for alerts every 15 seconds
        startPollingForAlerts();
    }
    
    /**
     * Backup polling mechanism in case listener doesn't work
     */
    private void startPollingForAlerts() {
        // Stop existing polling if any
        if (pollingHandler != null && pollingRunnable != null) {
            pollingHandler.removeCallbacks(pollingRunnable);
        }
        
        pollingHandler = new Handler(Looper.getMainLooper());
        pollingRunnable = new Runnable() {
            @Override
            public void run() {
                checkForNewAlerts();
                pollingHandler.postDelayed(this, 15000); // Poll every 15 seconds
            }
        };
        pollingHandler.postDelayed(pollingRunnable, 15000);
        Log.d(TAG, "✅ Started backup polling (every 15 seconds)");
    }
    
    /**
     * Manually check for new alerts
     */
    private void checkForNewAlerts() {
        if (alertsRef == null || parentId == null) return;
        
        alertsRef.orderByChild("parentId")
                .equalTo(parentId)
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        for (DataSnapshot alertSnapshot : snapshot.getChildren()) {
                            Long timestamp = alertSnapshot.child("timestamp").getValue(Long.class);
                            if (timestamp != null && timestamp >= serviceStartTime) {
                                handleNewAlert(alertSnapshot);
                            }
                        }
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        Log.e(TAG, "Poll cancelled: " + error.getMessage());
                    }
                });
    }

    /**
     * Handle new alert - FIXED to use correct field names from Alert model
     * Alert model has: message, details (NOT title)
     */
    private void handleNewAlert(DataSnapshot snapshot) {
        try {
            String alertId = snapshot.getKey();
            
            // Skip if already shown
            if (alertId == null || shownAlertIds.contains(alertId)) {
                return;
            }
            
            // Check if this alert is for this parent
            String alertParentId = snapshot.child("parentId").getValue(String.class);
            if (alertParentId == null || !parentId.equals(alertParentId)) {
                return;
            }

            // Check timestamp - only show alerts created after service started
            Long timestamp = snapshot.child("timestamp").getValue(Long.class);
            if (timestamp == null || timestamp < serviceStartTime) {
                return;
            }

            // Get alert details - FIXED: Alert model uses 'message' and 'details', NOT 'title'
            String type = snapshot.child("type").getValue(String.class);
            String message = snapshot.child("message").getValue(String.class);
            String details = snapshot.child("details").getValue(String.class);
            String childName = snapshot.child("childName").getValue(String.class);
            
            // Build notification title based on type
            String title = buildNotificationTitle(type, childName);
            
            // Build notification body - combine message and details
            String body = message;
            if (details != null && !details.isEmpty()) {
                body = message + "\n" + details;
            }
            
            Log.d(TAG, "📢 Alert: type=" + type + ", child=" + childName);

            if (body != null && !body.isEmpty()) {
                // Mark as shown BEFORE showing to prevent duplicates
                saveShownAlert(alertId);
                
                // Show the notification
                showSystemNotification(type, title, body, childName);
                Log.d(TAG, "✅ Notification shown for alert: " + alertId);
            }
        } catch (Exception e) {
            Log.e(TAG, "❌ Error handling alert: " + e.getMessage(), e);
        }
    }
    
    /**
     * Build a user-friendly notification title based on alert type
     */
    private String buildNotificationTitle(String type, String childName) {
        if (type == null) type = "ALERT";
        
        switch (type) {
            case "BLOCKED_APP":
                return "Blocked App Attempt";
            case "BLOCKED_WEBSITE":
                return "Blocked Website Attempt";
            case "SCREEN_TIME":
                return "Screen Time Alert";
            case "LOCATION":
                return "Location Update";
            case "CONTENT":
                return "Blocked Content";
            case "REQUEST":
            case "ACCESS_REQUEST":
                return "Access Request";
            default:
                return "Safe Zone Alert";
        }
    }

    /**
     * Show system notification directly
     * Only shows if alerts are enabled in settings
     */
    private void showSystemNotification(String type, String title, String message, String childName) {
        // Check if alerts are enabled in settings
        if (!NotificationHelper.areAlertsEnabled(this)) {
            Log.d(TAG, "⏸️ Alerts disabled - skipping notification: " + title);
            return;
        }
        
        try {
            // Create intent to open AlertsActivity when notification is tapped
            Intent intent = new Intent(this, AlertsActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            
            PendingIntent pendingIntent = PendingIntent.getActivity(
                this,
                (int) System.currentTimeMillis(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
            );

            // Get notification manager
            NotificationManager notificationManager = 
                (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            
            if (notificationManager == null) {
                Log.e(TAG, "❌ NotificationManager is null!");
                return;
            }

            // Get default notification sound
            Uri soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
            
            // Build notification with all required attributes
            NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ALERTS)
                    .setSmallIcon(R.drawable.ic_notification)
                    .setContentTitle(title)
                    .setContentText(message)
                    .setStyle(new NotificationCompat.BigTextStyle().bigText(message))
                    .setAutoCancel(true)
                    .setSound(soundUri)
                    .setVibrate(new long[]{0, 300, 200, 300})
                    .setPriority(NotificationCompat.PRIORITY_MAX)
                    .setCategory(NotificationCompat.CATEGORY_MESSAGE)
                    .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                    .setContentIntent(pendingIntent)
                    .setDefaults(NotificationCompat.DEFAULT_ALL);

            // Add child name as subtext if available
            if (childName != null && !childName.isEmpty()) {
                builder.setSubText("From: " + childName);
            }

            // Set color based on alert type
            int color = getNotificationColor(type);
            builder.setColor(color);

            // Generate unique notification ID
            int notificationId = (int) (System.currentTimeMillis() % Integer.MAX_VALUE);
            
            // Post the notification
            notificationManager.notify(notificationId, builder.build());
            
            Log.d(TAG, "✅ Notification posted: " + title);
        } catch (Exception e) {
            Log.e(TAG, "❌ Exception showing notification: " + e.getMessage(), e);
        }
    }
    
    /**
     * Get notification color based on alert type
     */
    private int getNotificationColor(String type) {
        if (type == null) return getResources().getColor(R.color.primary, null);
        
        switch (type) {
            case "BLOCKED_APP":
            case "BLOCKED_WEBSITE":
            case "CONTENT":
                return getResources().getColor(R.color.error, null);
            case "SCREEN_TIME":
                return getResources().getColor(R.color.warning, null);
            case "REQUEST":
            case "ACCESS_REQUEST":
                return getResources().getColor(R.color.secondary, null);
            default:
                return getResources().getColor(R.color.primary, null);
        }
    }

    /**
     * Create notification channels - required for Android O+
     */
    private void createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            if (manager == null) {
                Log.e(TAG, "❌ NotificationManager is null, cannot create channels");
                return;
            }

            // Foreground service channel (low priority, silent)
            NotificationChannel serviceChannel = new NotificationChannel(
                CHANNEL_ID,
                "Background Service",
                NotificationManager.IMPORTANCE_LOW
            );
            serviceChannel.setDescription("Keeps the app running to receive notifications");
            serviceChannel.setShowBadge(false);
            serviceChannel.setSound(null, null);
            manager.createNotificationChannel(serviceChannel);

            // Alerts channel - HIGH PRIORITY with sound and vibration
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
            
            // Set sound with proper audio attributes
            Uri soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
            AudioAttributes audioAttributes = new AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                    .build();
            alertsChannel.setSound(soundUri, audioAttributes);
            
            manager.createNotificationChannel(alertsChannel);
            
            Log.d(TAG, "✅ Notification channels created");
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        
        // Remove Firebase listener
        if (alertsRef != null && alertsListener != null) {
            alertsRef.removeEventListener(alertsListener);
        }
        
        // Stop polling
        if (pollingHandler != null && pollingRunnable != null) {
            pollingHandler.removeCallbacks(pollingRunnable);
        }
        
        Log.d(TAG, "Service destroyed");
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
