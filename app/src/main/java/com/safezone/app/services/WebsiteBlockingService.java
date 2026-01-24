package com.safezone.app.services;

import android.accessibilityservice.AccessibilityService;
import android.content.Intent;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

import androidx.annotation.NonNull;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.ValueEventListener;
import com.safezone.app.utils.AlertHelper;
import com.safezone.app.utils.FirebaseHelper;

import java.util.ArrayList;
import java.util.List;

/**
 * Professional Website Blocking Service
 * Uses AccessibilityService to monitor browser URLs and block restricted websites
 */
public class WebsiteBlockingService extends AccessibilityService {

    private static final String TAG = "WebsiteBlockingService";
    
    // Common browser package names
    private static final String[] BROWSER_PACKAGES = {
            "com.android.chrome",
            "com.chrome.beta",
            "com.chrome.dev",
            "com.android.browser",
            "org.mozilla.firefox",
            "com.opera.browser",
            "com.opera.mini.native",
            "com.microsoft.emmx",
            "com.brave.browser",
            "com.duckduckgo.mobile.android",
            "com.UCMobile.intl",
            "com.kiwibrowser.browser",
            "com.sec.android.app.sbrowser"
    };

    private DatabaseReference contentRulesRef;
    private String childUid;
    private String childName;
    private List<String> blockedWebsites = new ArrayList<>();
    private String lastBlockedUrl = null;
    private long lastBlockTime = 0;
    private static final long BLOCK_COOLDOWN = 3000; // 3 seconds cooldown

    private boolean isServiceConnected = false;
    
    @Override
    public void onServiceConnected() {
        super.onServiceConnected();
        Log.d(TAG, "WebsiteBlockingService connected");
        isServiceConnected = true;

        // Initialize on a background thread with delay to prevent ANR
        new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
            try {
                initializeService();
            } catch (Exception e) {
                Log.e(TAG, "Error initializing service: " + e.getMessage(), e);
            }
        }, 500);
    }
    
    private void initializeService() {
        try {
            childUid = FirebaseHelper.getCurrentUserId();
            if (childUid != null && !childUid.isEmpty()) {
                contentRulesRef = FirebaseHelper.getUsersRef().child(childUid).child("contentRules");
                loadChildName();
                listenToBlockedWebsites();
            } else {
                Log.e(TAG, "Child UID is null or empty - service will not work");
            }
        } catch (Exception e) {
            Log.e(TAG, "Error in initializeService: " + e.getMessage(), e);
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

    private void listenToBlockedWebsites() {
        if (contentRulesRef == null) {
            Log.e(TAG, "contentRulesRef is null, cannot listen to blocked websites");
            return;
        }
        
        try {
            contentRulesRef.child("blockedWebsites").addValueEventListener(new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot snapshot) {
                    try {
                        blockedWebsites.clear();
                        
                        if (snapshot.exists()) {
                            for (DataSnapshot siteSnapshot : snapshot.getChildren()) {
                                try {
                                    String url = siteSnapshot.getValue(String.class);
                                    if (url != null && !url.isEmpty()) {
                                        // Normalize URL (remove http://, https://, www.)
                                        url = normalizeUrl(url);
                                        blockedWebsites.add(url);
                                    }
                                } catch (Exception e) {
                                    Log.e(TAG, "Error parsing blocked website: " + e.getMessage());
                                }
                            }
                        }

                        Log.d(TAG, "Blocked websites updated: " + blockedWebsites.size() + " sites");
                    } catch (Exception e) {
                        Log.e(TAG, "Error in onDataChange: " + e.getMessage(), e);
                    }
                }

                @Override
                public void onCancelled(@NonNull DatabaseError error) {
                    Log.e(TAG, "Failed to load blocked websites: " + error.getMessage());
                }
            });
        } catch (Exception e) {
            Log.e(TAG, "Error setting up blocked websites listener: " + e.getMessage(), e);
        }
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        // Wrap everything in try-catch to prevent service crash
        try {
            if (event == null || !isServiceConnected) {
                return;
            }

            CharSequence pkgName = event.getPackageName();
            if (pkgName == null) {
                return;
            }
            
            String packageName = pkgName.toString();
            
            // Ignore SafeZone app itself and empty package names
            if (packageName.isEmpty() || packageName.equals(getPackageName())) {
                return;
            }
            
            // Check if it's a browser
            if (!isBrowser(packageName)) {
                return;
            }

            // Get URL from browser
            String url = extractUrl(event);
            
            if (url != null && !url.isEmpty()) {
                checkAndBlockWebsite(url);
            }
        } catch (Exception e) {
            // Log but don't crash - service must stay alive
            Log.e(TAG, "Error in onAccessibilityEvent: " + e.getMessage(), e);
        }
    }

    private boolean isBrowser(String packageName) {
        for (String browser : BROWSER_PACKAGES) {
            if (packageName.equals(browser)) {
                return true;
            }
        }
        return false;
    }

    private String extractUrl(AccessibilityEvent event) {
        try {
            AccessibilityNodeInfo source = event.getSource();
            if (source == null) return null;

            String packageName = event.getPackageName() != null ? event.getPackageName().toString() : "";
            
            // Try different URL bar IDs based on browser
            String[] urlBarIds = {
                packageName + ":id/url_bar",
                "com.android.chrome:id/url_bar",
                packageName + ":id/mozac_browser_toolbar_url_view",
                packageName + ":id/url",
                "url_bar",
                "search_src_text"
            };

            for (String urlBarId : urlBarIds) {
                try {
                    List<AccessibilityNodeInfo> urlNodes = source.findAccessibilityNodeInfosByViewId(urlBarId);
                    if (urlNodes != null && !urlNodes.isEmpty()) {
                        AccessibilityNodeInfo urlNode = urlNodes.get(0);
                        if (urlNode.getText() != null) {
                            String url = urlNode.getText().toString();
                            if (isValidUrl(url)) {
                                return url;
                            }
                        }
                    }
                } catch (Exception e) {
                    // Continue to next ID
                }
            }

            // Fallback: check content description
            if (event.getContentDescription() != null) {
                String desc = event.getContentDescription().toString();
                if (isValidUrl(desc)) {
                    return desc;
                }
            }

            // Fallback: check event text
            if (event.getText() != null && !event.getText().isEmpty()) {
                String text = event.getText().toString();
                if (isValidUrl(text)) {
                    return text;
                }
            }

        } catch (Exception e) {
            Log.e(TAG, "Error extracting URL: " + e.getMessage());
        }

        return null;
    }

    private boolean isValidUrl(String text) {
        if (text == null || text.isEmpty()) return false;
        text = text.toLowerCase();
        return text.contains("http") || text.contains("www.") || 
               text.contains(".com") || text.contains(".org") || 
               text.contains(".net") || text.contains(".edu");
    }

    private void checkAndBlockWebsite(String url) {
        String normalizedUrl = normalizeUrl(url);
        
        // Don't block if it's a search query
        if (isSearchQuery(url)) {
            Log.d(TAG, "Ignoring search query: " + url);
            return;
        }
        
        // Check if URL matches any blocked site with proper domain matching
        for (String blockedSite : blockedWebsites) {
            if (isUrlBlocked(normalizedUrl, blockedSite)) {
                Log.d(TAG, "Blocking URL: " + url + " (matches: " + blockedSite + ")");
                blockWebsite(url);
                break;
            }
        }
    }

    /**
     * Check if the URL is a search query (not a direct website visit)
     */
    private boolean isSearchQuery(String url) {
        if (url == null || url.isEmpty()) return false;
        
        String lowerUrl = url.toLowerCase();
        
        // Common search patterns
        return lowerUrl.contains("/search?") ||
               lowerUrl.contains("?q=") ||
               lowerUrl.contains("&q=") ||
               lowerUrl.contains("/search/") ||
               lowerUrl.contains("search?q=") ||
               lowerUrl.contains("google.com/search") ||
               lowerUrl.contains("bing.com/search") ||
               lowerUrl.contains("yahoo.com/search") ||
               lowerUrl.contains("duckduckgo.com/?q=");
    }

    /**
     * Check if URL matches blocked site with proper domain matching
     * Only blocks exact domain matches, not partial word matches
     */
    private boolean isUrlBlocked(String url, String blockedSite) {
        if (url == null || blockedSite == null || url.isEmpty() || blockedSite.isEmpty()) {
            return false;
        }
        
        // Exact match
        if (url.equals(blockedSite)) {
            return true;
        }
        
        // Check if URL contains the blocked domain as a complete domain
        // Examples:
        // - "deepseek.com" blocks "deepseek.com", "www.deepseek.com", "chat.deepseek.com"
        // - "deepseek.com" does NOT block "deep", "seek", "mydeepseek.com"
        
        // Pattern 1: URL is exactly the blocked site
        if (url.equals(blockedSite)) {
            return true;
        }
        
        // Pattern 2: URL starts with blocked site followed by path or query
        // Example: "deepseek.com/chat" starts with "deepseek.com"
        if (url.startsWith(blockedSite + "/") || url.startsWith(blockedSite + "?")) {
            return true;
        }
        
        // Pattern 3: URL ends with blocked site (subdomain case)
        // Example: "chat.deepseek.com" ends with "deepseek.com"
        if (url.endsWith("." + blockedSite)) {
            return true;
        }
        
        // Pattern 4: URL contains blocked site with proper domain boundaries
        // Example: "www.deepseek.com/chat" contains ".deepseek.com/"
        if (url.contains("." + blockedSite + "/") || 
            url.contains("." + blockedSite + "?")) {
            return true;
        }
        
        // Pattern 5: Check if it's a subdomain
        // Example: "chat.deepseek.com" should match "deepseek.com"
        String[] urlParts = url.split("\\.");
        String[] blockedParts = blockedSite.split("\\.");
        
        // If blocked site has at least 2 parts (domain.tld)
        if (blockedParts.length >= 2) {
            // Check if URL ends with the same domain.tld
            if (urlParts.length >= blockedParts.length) {
                boolean matches = true;
                for (int i = 0; i < blockedParts.length; i++) {
                    int urlIndex = urlParts.length - blockedParts.length + i;
                    if (!urlParts[urlIndex].equals(blockedParts[i])) {
                        matches = false;
                        break;
                    }
                }
                if (matches) {
                    return true;
                }
            }
        }
        
        return false;
    }

    private String normalizeUrl(String url) {
        if (url == null) return "";
        
        url = url.toLowerCase().trim();
        url = url.replace("https://", "");
        url = url.replace("http://", "");
        url = url.replace("www.", "");
        
        // Remove path (keep only domain)
        int slashIndex = url.indexOf('/');
        if (slashIndex > 0) {
            url = url.substring(0, slashIndex);
        }
        
        return url;
    }

    private void blockWebsite(String url) {
        long currentTime = System.currentTimeMillis();
        
        // Prevent blocking the same URL multiple times in quick succession
        if (url.equals(lastBlockedUrl) && 
            (currentTime - lastBlockTime) < BLOCK_COOLDOWN) {
            return;
        }

        lastBlockedUrl = url;
        lastBlockTime = currentTime;

        Log.d(TAG, "Blocking website: " + url);

        // Send alert to parent with full URL
        sendBlockedWebsiteAlert(url);

        // Immediately perform back action to close the page
        performGlobalAction(GLOBAL_ACTION_BACK);

        // Go to device home screen after a short delay
        new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
            Intent homeIntent = new Intent(Intent.ACTION_MAIN);
            homeIntent.addCategory(Intent.CATEGORY_HOME);
            homeIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            startActivity(homeIntent);
        }, 300);

        // Show blocking dialog after going home (no toast to avoid duplicate notifications)
        new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
            showBlockingDialog(url);
        }, 1000);
    }

    /**
     * Show blocking dialog with options - Enhanced UI with favicon
     */
    private void showBlockingDialog(String url) {
        try {
            // Get default web icon first
            android.graphics.drawable.Drawable defaultIcon = null;
            try {
                defaultIcon = androidx.core.content.ContextCompat.getDrawable(this, com.safezone.app.R.drawable.ic_web);
                if (defaultIcon != null) {
                    defaultIcon = defaultIcon.mutate();
                    defaultIcon.setTint(0xFF26C6DA);
                }
            } catch (Exception e) {
                Log.e(TAG, "Error loading default icon: " + e.getMessage());
            }
            
            final android.graphics.drawable.Drawable finalDefaultIcon = defaultIcon;
            
            // Create and show dialog with default icon first
            android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(
                    this, android.R.style.Theme_Material_Light_Dialog_Alert);
            
            builder.setTitle("Website Blocked")
                    .setMessage(url + "\n\nThis website is blocked by your parent. If you need access, you can request permission.")
                    .setIcon(finalDefaultIcon)
                    .setPositiveButton("OK", (dialog, which) -> dialog.dismiss())
                    .setNegativeButton("Request Access", (dialog, which) -> {
                        Intent intent = new Intent(this, com.safezone.app.activities.RequestAccessActivity.class);
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
                        startActivity(intent);
                    })
                    .setCancelable(true);
            
            android.app.AlertDialog dialog = builder.create();
            
            // Set window type for overlay
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                dialog.getWindow().setType(android.view.WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY);
            } else {
                dialog.getWindow().setType(android.view.WindowManager.LayoutParams.TYPE_SYSTEM_ALERT);
            }
            
            dialog.show();
            
            // Style the dialog
            styleDialog(dialog);
            
            // Load favicon asynchronously and update dialog icon
            loadFaviconAsync(url, dialog);
            
        } catch (Exception e) {
            Log.e(TAG, "Error showing blocking dialog: " + e.getMessage(), e);
        }
    }
    
    /**
     * Style the dialog with app colors
     */
    private void styleDialog(android.app.AlertDialog dialog) {
        try {
            // Style title - cyan color
            int titleId = getResources().getIdentifier("alertTitle", "id", "android");
            if (titleId > 0) {
                android.widget.TextView titleView = dialog.findViewById(titleId);
                if (titleView != null) {
                    titleView.setTextColor(0xFF26C6DA);
                }
            }
            
            // Style buttons with cyan color
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
    }
    
    /**
     * Load website favicon asynchronously using Google's favicon service
     */
    private void loadFaviconAsync(String url, android.app.AlertDialog dialog) {
        new Thread(() -> {
            try {
                // Normalize URL to get domain
                String domain = url.toLowerCase().trim();
                domain = domain.replace("https://", "").replace("http://", "").replace("www.", "");
                int slashIndex = domain.indexOf('/');
                if (slashIndex > 0) {
                    domain = domain.substring(0, slashIndex);
                }
                
                // Use Google's favicon service (reliable and fast)
                String faviconUrl = "https://www.google.com/s2/favicons?domain=" + domain + "&sz=64";
                
                // Download favicon
                java.net.URL imageUrl = new java.net.URL(faviconUrl);
                java.net.HttpURLConnection connection = (java.net.HttpURLConnection) imageUrl.openConnection();
                connection.setDoInput(true);
                connection.setConnectTimeout(3000);
                connection.setReadTimeout(3000);
                connection.connect();
                
                java.io.InputStream input = connection.getInputStream();
                android.graphics.Bitmap bitmap = android.graphics.BitmapFactory.decodeStream(input);
                input.close();
                
                if (bitmap != null) {
                    // Scale bitmap to appropriate size
                    int size = (int) (48 * getResources().getDisplayMetrics().density);
                    android.graphics.Bitmap scaledBitmap = android.graphics.Bitmap.createScaledBitmap(bitmap, size, size, true);
                    android.graphics.drawable.BitmapDrawable faviconDrawable = 
                        new android.graphics.drawable.BitmapDrawable(getResources(), scaledBitmap);
                    
                    // Update dialog icon on main thread
                    new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
                        try {
                            if (dialog.isShowing()) {
                                dialog.setIcon(faviconDrawable);
                            }
                        } catch (Exception e) {
                            Log.e(TAG, "Error updating dialog icon: " + e.getMessage());
                        }
                    });
                }
            } catch (Exception e) {
                Log.e(TAG, "Error loading favicon: " + e.getMessage());
                // Keep default icon on error
            }
        }).start();
    }

    private void sendBlockedWebsiteAlert(String url) {
        try {
            // Ensure full URL is captured
            final String fullUrl = url != null ? url : "Unknown website";
            
            // Get parent ID
            FirebaseHelper.getUserRef(childUid).child("parentId")
                    .addListenerForSingleValueEvent(new ValueEventListener() {
                        @Override
                        public void onDataChange(@NonNull DataSnapshot snapshot) {
                            String parentId = snapshot.getValue(String.class);
                            if (parentId != null) {
                                String childNameSafe = childName != null ? childName : "Child";
                                AlertHelper.sendCustomAlert(
                                        parentId,
                                        childUid,
                                        childNameSafe,
                                        "BLOCKED_WEBSITE",
                                        childNameSafe + " tried to access a blocked website",
                                        "Website: " + fullUrl
                                );
                                Log.d(TAG, "Alert sent for website: " + fullUrl);
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

    @Override
    public void onInterrupt() {
        Log.d(TAG, "Service interrupted");
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        isServiceConnected = false;
        Log.d(TAG, "Service destroyed");
    }
    
    @Override
    public boolean onUnbind(Intent intent) {
        isServiceConnected = false;
        Log.d(TAG, "Service unbound");
        return super.onUnbind(intent);
    }
}
