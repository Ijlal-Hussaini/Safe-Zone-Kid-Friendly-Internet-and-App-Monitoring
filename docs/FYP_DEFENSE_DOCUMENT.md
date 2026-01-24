# SafeZone - Parental Control Android Application
## Final Year Project Defense Document

---

## 1. PROJECT OVERVIEW

### 1.1 Project Title
**SafeZone** - A Comprehensive Parental Control Application for Android

### 1.2 Project Description
SafeZone is a native Android application designed to help parents monitor and manage their children's smartphone usage. The application provides real-time monitoring, app blocking, website filtering, screen time management, and location tracking capabilities.

### 1.3 Problem Statement
- Children are increasingly exposed to inappropriate content online
- Excessive screen time affects children's physical and mental health
- Parents lack visibility into their children's digital activities
- Existing solutions are either too expensive or lack comprehensive features

### 1.4 Solution
SafeZone provides a dual-app architecture where:
- **Parent Mode**: Monitor children, set restrictions, receive alerts
- **Child Mode**: Runs monitoring services, enforces restrictions

### 1.5 Key Features
1. User Authentication (Firebase Auth with Email Verification)
2. Parent-Child Linking via QR Code/Manual Code
3. Real-time App Usage Monitoring
4. Screen Time Management with Daily Limits
5. App Blocking (Instant blocking via Accessibility Service)
6. Website Blocking (Browser URL monitoring)
7. Real-time Location Tracking (Google Maps)
8. Push Notifications for Parents
9. Activity Reports and Analytics
10. Profile Management

---

## 2. SYSTEM ARCHITECTURE

### 2.1 Technology Stack

| Component | Technology |
|-----------|------------|
| Platform | Android (Native Java) |
| Minimum SDK | API 24 (Android 7.0) |
| Target SDK | API 34 (Android 14) |
| Backend | Firebase Realtime Database |
| Authentication | Firebase Authentication |
| Storage | Firebase Storage (Profile Photos) |
| Maps | Google Maps SDK |
| QR Code | ZXing Library |
| Build System | Gradle |

### 2.2 Architecture Pattern
- **MVVM-like Architecture** with Activities, Fragments, Services
- **Repository Pattern** via FirebaseHelper utility class
- **Observer Pattern** using Firebase ValueEventListener

### 2.3 Firebase Database Structure
```
firebase-database/
├── users/
│   └── {userId}/
│       ├── uid, name, email, role
│       ├── profilePhotoBase64
│       ├── children/ (for parents)
│       ├── parentId (for children)
│       ├── screenTimeRules/
│       │   ├── enabled, dailyLimitMinutes
│       │   └── allowedApps[]
│       ├── contentRules/
│       │   ├── blockedApps[]
│       │   └── blockedWebsites[]
│       ├── activityLogs/{logId}/
│       ├── location/
│       └── installedApps[]
├── alerts/{alertId}/
├── linkTokens/{tokenId}/
└── reports/{childId}/
```

---

## 3. MODULE-WISE IMPLEMENTATION

### 3.1 Authentication Module

**Files Involved:**
- `LoginActivity.java`
- `RegisterActivity.java`
- `RoleSelectionActivity.java`
- `EmailVerificationInfoActivity.java`
- `ForgotPasswordActivity.java`

**Implementation Details:**

1. **Role Selection**: Users first select their role (Parent/Child) before login/registration
2. **Registration Flow**:
   - Validate name, email, password (min 8 chars, uppercase, lowercase, number, special char)
   - Create Firebase Auth account
   - Send email verification link
   - Store pending registration in SharedPreferences
   - User data saved to database only after email verification

3. **Login Flow**:
   - Verify email is verified
   - Check role matches selected role (prevents parent logging as child)
   - Load user data from Firebase
   - Navigate to appropriate dashboard

4. **Security Features**:
   - Email verification required
   - Password strength validation
   - Role mismatch prevention
   - Session management via SharedPrefsHelper

**Code Snippet - Password Validation:**
```java
public static String getPasswordError(String password) {
    if (password.length() < 8) return "Password must be at least 8 characters";
    if (!password.matches(".*[A-Z].*")) return "Must contain uppercase letter";
    if (!password.matches(".*[a-z].*")) return "Must contain lowercase letter";
    if (!password.matches(".*\\d.*")) return "Must contain a number";
    if (!password.matches(".*[!@#$%^&*()].*")) return "Must contain special character";
    return null;
}
```

---

### 3.2 Parent-Child Linking Module

**Files Involved:**
- `AddChildActivity.java`
- `QRScannerActivity.java`
- `LinkToken.java`

**Implementation Details:**

1. **QR Code Generation (Parent Side)**:
   - Parent enters child's name and age
   - System generates 6-character alphanumeric code
   - Creates LinkToken with 10-minute expiry
   - Generates QR code using ZXing library
   - Stores token in Firebase `/linkTokens/{tokenId}`

2. **QR Code Scanning (Child Side)**:
   - Uses device camera via ZXing BarcodeScanner
   - Also supports manual code entry
   - Validates token (not expired, not used)
   - Creates bidirectional link:
     - Adds `parentId` to child's record
     - Adds child to parent's `children` map

3. **Token Structure:**
```java
public class LinkToken {
    private String tokenId;
    private String parentUid;
    private String parentEmail;
    private String parentName;
    private String childUid;
    private long createdAt;
    private long expiresAt; // 10 minutes
    private boolean used;
}
```

---

### 3.3 Activity Monitoring Module

**Files Involved:**
- `ActivityMonitorService.java`
- `ActivityLogsActivity.java`
- `ActivityLog.java`

**Implementation Details:**

1. **Foreground Service**: Runs continuously with notification
2. **UsageStatsManager**: Queries app usage every 5 minutes
3. **Incremental Tracking**: Calculates time since last check (not cumulative)
4. **Firebase Sync**: Logs stored at `/users/{childId}/activityLogs/`

**Key Algorithm:**
```java
// Calculate INCREMENTAL usage since last check
long incrementalUsage = currentTotalForeground - previousForegroundTimes.get(packageName);

// Only log if app was actually used (>10 seconds, <5 minutes)
if (incrementalUsage > 10000 && incrementalUsage <= LOG_INTERVAL + 60000) {
    logToFirebase(packageName, appName, incrementalUsage, currentTime);
}
```

**Permissions Required:**
- `PACKAGE_USAGE_STATS` - Special permission for usage data
- `FOREGROUND_SERVICE` - Keep service running

---

### 3.4 Screen Time Management Module

**Files Involved:**
- `ScreenTimeEnforcerService.java`
- `ScreenTimeSettingsActivity.java`
- `ScreenTimeLockActivity.java`
- `ScreenTimeRule.java`

**Implementation Details:**

1. **Parent Configuration**:
   - Enable/disable screen time limits
   - Set daily limit (hours + minutes slider)
   - Add allowed apps (always accessible even after limit)

2. **Enforcement Service**:
   - Runs every 15 seconds
   - Tracks accumulated usage time
   - Resets timer when parent enables/changes limit
   - Sets `limitExceeded` flag in SharedPreferences
   - Sends warning at 5 minutes remaining
   - Sends alert to parent when limit reached

3. **Timer Reset Logic:**
```java
// Reset when restriction is turned ON or limit changes
if (newEnabled && !wasEnabled) {
    resetRestrictionTimer(); // Start fresh from 0
} else if (newEnabled && newLimit != dailyLimitMinutes) {
    resetRestrictionTimer(); // Limit changed, reset
}
```

4. **Allowed Apps**: Phone, Dialer, Contacts, Emergency, Settings always allowed

---

### 3.5 App Blocking Module

**Files Involved:**
- `AppBlockingService.java`
- `AppBlockingAccessibilityService.java`

**Implementation Details:**

**Dual-Layer Blocking Architecture:**

1. **Layer 1 - Accessibility Service** (`AppBlockingAccessibilityService`):
   - Instant detection via `TYPE_WINDOW_STATE_CHANGED`
   - Blocks app before it fully opens
   - Sends alert to parent

2. **Layer 2 - Background Polling** (`AppBlockingService`):
   - Polls every 50ms for continuous enforcement
   - Catches any apps that bypass accessibility
   - Uses `UsageStatsManager` to detect foreground app

**Blocking Process:**
```java
private void blockAppImmediately(String packageName) {
    // 1. Go to home screen
    performGlobalAction(GLOBAL_ACTION_HOME);
    
    // 2. Kill the app process
    activityManager.killBackgroundProcesses(packageName);
    
    // 3. Show blocking dialog
    showBlockingDialog(packageName);
    
    // 4. Send alert to parent
    sendBlockedAppAlert(packageName);
}
```

**Permissions Required:**
- `BIND_ACCESSIBILITY_SERVICE` - For instant blocking
- `KILL_BACKGROUND_PROCESSES` - To terminate blocked apps
- `SYSTEM_ALERT_WINDOW` - For overlay dialogs

---

### 3.6 Website Blocking Module

**Files Involved:**
- `WebsiteBlockingService.java`

**Implementation Details:**

1. **Accessibility Service**: Monitors browser URL bars
2. **Supported Browsers**: Chrome, Firefox, Opera, Brave, Edge, Samsung Browser, etc.
3. **URL Extraction**: Reads URL from browser's address bar view

**Domain Matching Algorithm:**
```java
private boolean isUrlBlocked(String url, String blockedSite) {
    // Exact match
    if (url.equals(blockedSite)) return true;
    
    // Subdomain match: chat.facebook.com matches facebook.com
    if (url.endsWith("." + blockedSite)) return true;
    
    // Path match: facebook.com/login matches facebook.com
    if (url.startsWith(blockedSite + "/")) return true;
    
    return false;
}
```

**Blocking Action:**
```java
private void blockWebsite(String url) {
    // 1. Press back to close page
    performGlobalAction(GLOBAL_ACTION_BACK);
    
    // 2. Go to home screen
    startActivity(homeIntent);
    
    // 3. Show blocking dialog with favicon
    showBlockingDialog(url);
    
    // 4. Alert parent
    sendBlockedWebsiteAlert(url);
}
```

---

### 3.7 Location Tracking Module

**Files Involved:**
- `LocationTrackingService.java`
- `LocationMapActivity.java`

**Implementation Details:**

1. **FusedLocationProviderClient**: Google's optimized location API
2. **Update Interval**: Every 5 minutes (battery-efficient)
3. **Firebase Storage**: Location stored at `/users/{childId}/location/`
4. **Google Maps Integration**: Displays child's location with marker

**Location Data Structure:**
```java
Map<String, Object> locationData = new HashMap<>();
locationData.put("latitude", location.getLatitude());
locationData.put("longitude", location.getLongitude());
locationData.put("accuracy", location.getAccuracy());
locationData.put("timestamp", System.currentTimeMillis());
```

**Permissions Required:**
- `ACCESS_FINE_LOCATION`
- `ACCESS_COARSE_LOCATION`
- `ACCESS_BACKGROUND_LOCATION`
- `FOREGROUND_SERVICE_LOCATION`

---

### 3.8 Alerts & Notifications Module

**Files Involved:**
- `AlertHelper.java`
- `NotificationHelper.java`
- `ParentNotificationService.java`
- `AlertsActivity.java`
- `Alert.java`

**Implementation Details:**

1. **Alert Types**:
   - `BLOCKED_APP` - Child tried to open blocked app
   - `BLOCKED_WEBSITE` - Child tried to access blocked site
   - `SCREEN_TIME` - Screen time limit reached
   - `REQUEST` - Child requested more time
   - `DEVICE_RESTART` - Child's device restarted

2. **Real-time Notifications**:
   - `ParentNotificationService` runs as foreground service on parent device
   - Uses Firebase `ChildEventListener` for instant updates
   - Backup polling every 15 seconds

3. **Notification Channels** (Android O+):
   - `alerts_channel` - High priority with sound
   - `blocked_apps_channel` - High priority
   - `screen_time_channel` - Default priority
   - `location_channel` - Low priority (silent)

---

### 3.9 Service Persistence Module

**Files Involved:**
- `BootReceiver.java`
- `ServiceRestartReceiver.java`
- `SafeZoneDeviceAdminReceiver.java`

**Implementation Details:**

1. **Boot Receiver**: Auto-starts services after device restart
   - Listens for `BOOT_COMPLETED`, `QUICKBOOT_POWERON`
   - Starts all monitoring services with 5-second delay

2. **Service Watchdog**: AlarmManager checks every 15 minutes
   - Restarts any killed services
   - Ensures continuous monitoring

3. **Device Admin**: Prevents app uninstallation
   - Shows warning when user tries to disable
   - Requires explicit permission removal

**Boot Receiver Actions:**
```java
switch (action) {
    case Intent.ACTION_BOOT_COMPLETED:
        startServicesIfChild(context);
        break;
    case Intent.ACTION_MY_PACKAGE_REPLACED:
        startServicesIfChild(context); // After app update
        break;
}
```

---

### 3.10 Permissions Setup Module

**Files Involved:**
- `PermissionsSetupActivity.java`

**Required Permissions for Child Device:**

| Permission | Purpose |
|------------|---------|
| Usage Access | Monitor app usage |
| Accessibility | Block apps/websites instantly |
| Overlay | Show blocking dialogs |
| Device Admin | Prevent uninstallation |
| Location | Track child's location |
| Notifications | Show alerts |

**Permission Check Flow:**
```java
private boolean allPermissionsGranted() {
    return isAccessibilityServiceEnabled() &&
           hasUsageStatsPermission() &&
           hasOverlayPermission() &&
           isDeviceAdminEnabled();
}
```

---

## 4. DATA FLOW DIAGRAMS

### 4.1 Parent-Child Linking Flow
```
Parent                          Firebase                         Child
  |                                |                               |
  |-- Generate QR Code ----------->|                               |
  |                                |-- Store LinkToken             |
  |                                |                               |
  |                                |<-------- Scan QR Code --------|
  |                                |                               |
  |                                |-- Validate Token              |
  |                                |-- Create Bidirectional Link   |
  |                                |                               |
  |<-- Receive Child in List ------|-------- Link Confirmed ------>|
```

### 4.2 App Blocking Flow
```
Child Device                    Firebase                    Parent Device
     |                             |                              |
     |-- Opens Blocked App         |                              |
     |                             |                              |
     |-- Accessibility Detects --->|                              |
     |                             |                              |
     |-- Block App (Go Home)       |                              |
     |-- Show Dialog               |                              |
     |                             |                              |
     |-- Send Alert -------------->|-- Store Alert                |
     |                             |                              |
     |                             |-- Push Notification -------->|
     |                             |                              |
     |                             |<-- Parent Views Alert -------|
```

---

## 5. SECURITY IMPLEMENTATION

### 5.1 Firebase Security Rules
```json
{
  "rules": {
    "users": {
      "$uid": {
        ".read": "$uid === auth.uid || parent-child relationship exists",
        ".write": "$uid === auth.uid",
        
        "screenTimeRules": {
          ".write": "parent can write to child's rules"
        },
        "contentRules": {
          ".write": "parent can write to child's rules"
        }
      }
    }
  }
}
```

### 5.2 Security Features
1. **Email Verification**: Required before account activation
2. **Role Enforcement**: Parent can't login as child and vice versa
3. **Token Expiry**: QR codes expire in 10 minutes
4. **Device Admin**: Prevents unauthorized app removal
5. **Password Confirmation**: Required for logout on child device

---

## 6. TESTING SCENARIOS

### 6.1 Functional Testing
| Test Case | Expected Result | Status |
|-----------|-----------------|--------|
| Parent Registration | Account created, verification email sent | ✓ |
| Child Registration | Account created, verification email sent | ✓ |
| QR Code Linking | Child linked to parent successfully | ✓ |
| App Blocking | Blocked app closes immediately | ✓ |
| Website Blocking | Browser redirects to home | ✓ |
| Screen Time Limit | Device locks after limit | ✓ |
| Location Tracking | Location shown on map | ✓ |
| Push Notifications | Parent receives alerts | ✓ |

### 6.2 Edge Cases Tested
- Device restart (services auto-start)
- App update (services restart)
- Network disconnection (offline caching)
- Multiple children per parent
- Orphaned links cleanup

---

## 7. CHALLENGES & SOLUTIONS

| Challenge | Solution |
|-----------|----------|
| Android kills background services | Foreground services + AlarmManager watchdog |
| Instant app blocking | Dual-layer: Accessibility + Polling |
| Battery drain from location | 5-minute intervals with FusedLocationProvider |
| Firebase permission denied | Comprehensive security rules for parent-child |
| QR code expiry handling | 10-minute timer with visual countdown |
| Child bypassing restrictions | Device Admin prevents uninstall |

---

## 8. FUTURE ENHANCEMENTS

1. **Geofencing**: Alert when child leaves safe zones
2. **Call/SMS Monitoring**: Track communications
3. **Social Media Monitoring**: Monitor specific apps
4. **AI Content Analysis**: Detect inappropriate content
5. **Multi-platform**: iOS version
6. **Web Dashboard**: Parent portal for desktop
7. **Family Groups**: Multiple parents per child

---


## 9. POSSIBLE FYP PANEL QUESTIONS & ANSWERS

### Q1: Why did you choose Firebase over a custom backend?

**Answer:**
We chose Firebase for several reasons:
1. **Real-time Database**: Firebase provides real-time synchronization which is critical for instant alerts and live location updates
2. **Built-in Authentication**: Firebase Auth handles email verification, password reset, and secure token management
3. **Scalability**: Firebase automatically scales without server management
4. **Cost-effective**: Free tier sufficient for development and small-scale deployment
5. **Offline Support**: Firebase caches data locally and syncs when online
6. **Security Rules**: Declarative security rules protect data at the database level

---

### Q2: How do you ensure the monitoring services keep running even if Android kills them?

**Answer:**
We implemented a multi-layer persistence strategy:

1. **Foreground Services**: All monitoring services run as foreground services with persistent notifications, which Android prioritizes
2. **START_STICKY**: Services return `START_STICKY` in `onStartCommand()`, telling Android to restart them if killed
3. **Boot Receiver**: `BootReceiver` listens for `BOOT_COMPLETED` and restarts all services after device restart
4. **Service Watchdog**: `ServiceRestartReceiver` uses `AlarmManager` to check every 15 minutes and restart any stopped services
5. **Device Admin**: Prevents the app from being uninstalled, ensuring services can't be permanently stopped

---

### Q3: How does the app blocking work instantly without any delay?

**Answer:**
We use a dual-layer blocking architecture:

**Layer 1 - Accessibility Service:**
- Listens for `TYPE_WINDOW_STATE_CHANGED` events
- Detects app launch before it fully opens
- Immediately calls `performGlobalAction(GLOBAL_ACTION_HOME)` to go to home screen
- Kills the app process using `ActivityManager.killBackgroundProcesses()`

**Layer 2 - Background Polling:**
- Polls every 50ms (20 times per second)
- Uses `UsageStatsManager` to detect current foreground app
- Acts as backup if accessibility misses anything

This dual approach ensures no blocked app can be used even for a split second.

---

### Q4: How do you handle the parent-child relationship securely?

**Answer:**
Security is implemented at multiple levels:

1. **Token-based Linking**: 
   - Parent generates a 6-character code with 10-minute expiry
   - Code is stored in Firebase with parent's UID
   - Child scans code and validates before linking

2. **Bidirectional Verification**:
   - Child stores `parentId` in their record
   - Parent stores child in `children` map
   - Both must exist for valid relationship

3. **Firebase Security Rules**:
   - Parents can only read/write their linked children's data
   - Children can only write their own activity logs
   - Prevents unauthorized access to other users' data

4. **Role Enforcement**:
   - Login validates role matches account type
   - Parent account can't login in child mode

---

### Q5: What happens if the child's device loses internet connection?

**Answer:**
The app handles offline scenarios gracefully:

1. **Local Enforcement**: Screen time and app blocking work entirely offline using SharedPreferences
2. **Firebase Offline Persistence**: Firebase SDK caches data locally and syncs when online
3. **Activity Logging**: Logs are queued locally and uploaded when connection restores
4. **Location Caching**: Last known location is stored and displayed until new data arrives

The child cannot bypass restrictions by turning off internet because blocking logic runs locally.

---

### Q6: How do you prevent the child from uninstalling the app?

**Answer:**
We use Android's Device Administration API:

1. **Device Admin Receiver**: `SafeZoneDeviceAdminReceiver` registers as device administrator
2. **Uninstall Prevention**: When device admin is active, the app cannot be uninstalled without first disabling admin
3. **Warning Message**: When child tries to disable admin, a warning message appears: "Disabling Safe Zone will remove all parental controls. Contact your parent for permission."
4. **Password Protection**: Logout requires password confirmation on child device

---

### Q7: How does website blocking work across different browsers?

**Answer:**
Website blocking uses Accessibility Service to monitor browser URL bars:

1. **Browser Detection**: We maintain a list of common browser package names (Chrome, Firefox, Opera, Brave, Edge, Samsung Browser, etc.)

2. **URL Extraction**: When a browser is in foreground, we search for the URL bar view using known view IDs:
   ```java
   String[] urlBarIds = {
       packageName + ":id/url_bar",
       "com.android.chrome:id/url_bar",
       packageName + ":id/mozac_browser_toolbar_url_view"
   };
   ```

3. **Domain Matching**: We normalize URLs and check against blocked list with proper domain matching (handles subdomains, paths)

4. **Blocking Action**: Press back, go to home, show dialog

---

### Q8: How do you handle screen time fairly - does it reset at midnight?

**Answer:**
Our screen time implementation uses a "fresh start" approach:

1. **Timer Starts When Enabled**: When parent enables screen time or changes the limit, the timer resets to 0
2. **Accumulated Usage**: We track actual device usage time, not wall clock time
3. **Pause Detection**: If device is idle or screen is off, time doesn't accumulate
4. **Persistence**: Timer state is saved in SharedPreferences and survives app restarts

This approach is fairer than midnight reset because:
- Child gets full allocated time from when restriction starts
- Changing limit gives fresh start (prevents confusion)
- Actual usage is tracked, not just time since midnight

---

### Q9: What are the main Android permissions required and why?

**Answer:**

| Permission | Why Required |
|------------|--------------|
| `PACKAGE_USAGE_STATS` | Access app usage data for monitoring |
| `BIND_ACCESSIBILITY_SERVICE` | Instant app/website blocking |
| `SYSTEM_ALERT_WINDOW` | Show blocking dialogs over other apps |
| `BIND_DEVICE_ADMIN` | Prevent app uninstallation |
| `ACCESS_FINE_LOCATION` | Track child's location |
| `ACCESS_BACKGROUND_LOCATION` | Location updates when app is in background |
| `FOREGROUND_SERVICE` | Keep monitoring services running |
| `RECEIVE_BOOT_COMPLETED` | Auto-start services after device restart |
| `QUERY_ALL_PACKAGES` | Get list of installed apps |
| `KILL_BACKGROUND_PROCESSES` | Terminate blocked apps |

---

### Q10: How do you ensure real-time notifications reach the parent?

**Answer:**
We implemented a robust notification system:

1. **Foreground Service**: `ParentNotificationService` runs continuously on parent's device
2. **Firebase ChildEventListener**: Listens for new alerts in real-time
3. **Backup Polling**: Every 15 seconds, we query for any missed alerts
4. **Notification Channels**: High-priority channel with sound and vibration
5. **Deduplication**: Track shown alert IDs to prevent duplicate notifications

The service starts automatically when parent logs in and survives app closure.

---

### Q11: How did you implement the QR code scanning feature?

**Answer:**
We used the ZXing (Zebra Crossing) library:

1. **QR Generation** (Parent side):
   ```java
   MultiFormatWriter writer = new MultiFormatWriter();
   BitMatrix bitMatrix = writer.encode(tokenId, BarcodeFormat.QR_CODE, 500, 500);
   BarcodeEncoder encoder = new BarcodeEncoder();
   Bitmap qrBitmap = encoder.createBitmap(bitMatrix);
   ```

2. **QR Scanning** (Child side):
   - Uses `DecoratedBarcodeView` for camera preview
   - `decodeContinuous()` for real-time scanning
   - Also supports manual code entry as fallback

3. **Token Validation**:
   - Check if token exists in Firebase
   - Verify not expired (10-minute window)
   - Verify not already used

---

### Q12: What is the difference between your app and existing solutions like Google Family Link?

**Answer:**

| Feature | SafeZone | Google Family Link |
|---------|----------|-------------------|
| App Blocking | Instant (Accessibility) | Delayed |
| Website Blocking | Yes (Browser monitoring) | Limited |
| Custom Allowed Apps | Yes | Limited |
| QR Code Linking | Yes | No |
| Open Source | Yes | No |
| Works Offline | Yes | Partially |
| Device Admin | Yes | Yes |
| Cross-platform | Android only | Android + iOS |

Our advantages:
- More instant blocking
- More customizable
- Works without Google account
- Transparent (open source)

---

### Q13: How do you handle multiple children for one parent?

**Answer:**
The data model supports multiple children:

1. **Parent's Children Map**:
   ```json
   "users/parentId/children": {
     "child1Uid": true,
     "child2Uid": true,
     "child3Uid": true
   }
   ```

2. **UI Implementation**:
   - `ChildrenListFragment` displays all linked children
   - Each child has separate settings (screen time, blocked apps)
   - Alerts are tagged with `childId` and `childName`

3. **Firebase Rules**: Parent can access any child in their `children` map

---

### Q14: What testing methodology did you use?

**Answer:**
We used multiple testing approaches:

1. **Unit Testing**: Validation utilities, model classes
2. **Integration Testing**: Firebase operations, service communication
3. **Manual Testing**: 
   - Two physical devices (parent + child)
   - Various Android versions (7.0 to 14)
   - Different manufacturers (Samsung, Xiaomi, OnePlus)
4. **Edge Case Testing**:
   - Device restart
   - Network disconnection
   - Battery optimization
   - App updates

---

### Q15: What are the limitations of your application?

**Answer:**
Current limitations:

1. **Android Only**: No iOS version (would require complete rewrite)
2. **Browser Dependency**: Website blocking only works in supported browsers
3. **VPN Bypass**: Child could potentially use VPN to bypass website blocking
4. **Root Access**: Rooted devices could bypass restrictions
5. **Battery Impact**: Multiple foreground services consume battery
6. **Manufacturer Variations**: Some phones (Xiaomi, Huawei) aggressively kill background services

---

### Q16: How would you scale this application for commercial use?

**Answer:**
For commercial scaling:

1. **Backend Migration**: Move to dedicated server (Node.js/Python) for complex logic
2. **Push Notifications**: Implement FCM for more reliable notifications
3. **Analytics**: Add Firebase Analytics for usage insights
4. **Subscription Model**: Implement in-app purchases for premium features
5. **Web Dashboard**: Create parent portal for desktop access
6. **iOS Version**: Develop native iOS app
7. **Load Balancing**: Use Firebase's paid tier for guaranteed performance

---

### Q17: Explain the Model-View architecture in your project.

**Answer:**
Our architecture follows a simplified MVVM pattern:

**Models** (`/models/`):
- `User.java`, `ParentUser.java`, `ChildUser.java`
- `Alert.java`, `ActivityLog.java`
- `ScreenTimeRule.java`, `ContentRule.java`
- `LinkToken.java`

**Views** (`/activities/`, `/fragments/`, `/res/layout/`):
- Activities handle UI and user interaction
- Fragments for reusable UI components
- XML layouts define UI structure

**ViewModels/Controllers**:
- Activities act as controllers
- `FirebaseHelper` provides data access
- Services handle background logic

**Adapters** (`/adapters/`):
- `ChildrenAdapter`, `AlertsAdapter`, `ActivityLogsAdapter`
- Bridge between data and RecyclerViews

---

### Q18: How do you handle the app's lifecycle and configuration changes?

**Answer:**

1. **Activity Lifecycle**:
   - `onCreate()`: Initialize views, start services
   - `onResume()`: Refresh data, check permissions
   - `onDestroy()`: Remove Firebase listeners, stop handlers

2. **Service Lifecycle**:
   - `START_STICKY`: Restart if killed
   - `onDestroy()`: Clean up resources, schedule restart

3. **Configuration Changes**:
   - `android:screenOrientation="portrait"` for critical screens
   - ViewModel pattern for data persistence (where applicable)

4. **Memory Management**:
   - Remove Firebase listeners in `onDestroy()`
   - Cancel handlers and timers
   - Use weak references where appropriate

---

### Q19: What security vulnerabilities exist and how did you mitigate them?

**Answer:**

| Vulnerability | Mitigation |
|---------------|------------|
| Unauthorized data access | Firebase security rules |
| Token interception | Short expiry (10 min), single use |
| App uninstallation | Device Admin |
| Service killing | Foreground service + watchdog |
| Password exposure | Firebase Auth handles securely |
| Man-in-middle | HTTPS enforced by Firebase |
| Replay attacks | Timestamps on all operations |

---

### Q20: What did you learn from this project?

**Answer:**
Key learnings:

1. **Android Services**: Deep understanding of foreground services, accessibility services, and service persistence
2. **Firebase**: Real-time database, authentication, security rules
3. **System Permissions**: Android's permission model and special permissions
4. **Background Processing**: Challenges of keeping apps running on modern Android
5. **Security**: Implementing secure parent-child relationships
6. **UX Design**: Creating intuitive interfaces for both parents and children
7. **Testing**: Importance of testing on multiple devices and Android versions
8. **Problem Solving**: Finding creative solutions to Android's restrictions

---

## 10. DEMONSTRATION CHECKLIST

### Before Defense:
- [ ] Both devices charged and connected to internet
- [ ] Parent account logged in on Device 1
- [ ] Child account logged in on Device 2
- [ ] All permissions granted on child device
- [ ] Some blocked apps configured
- [ ] Screen time limit set (e.g., 5 minutes for demo)

### Demo Flow:
1. Show parent dashboard with linked child
2. Show child dashboard with usage overview
3. Demonstrate QR code linking (if second child available)
4. Show app blocking in action
5. Show website blocking in action
6. Show screen time limit enforcement
7. Show location on map
8. Show real-time alert notification
9. Show activity logs
10. Show settings and profile management

---

## 11. CONCLUSION

SafeZone successfully addresses the need for comprehensive parental control on Android devices. The application provides:

- **Real-time monitoring** of app usage and location
- **Instant blocking** of restricted apps and websites
- **Flexible screen time management** with allowed apps
- **Secure parent-child linking** via QR codes
- **Reliable notifications** for important events

The project demonstrates proficiency in Android development, Firebase integration, background services, and security implementation. Future enhancements could include iOS support, geofencing, and AI-based content analysis.

---

*Document prepared for FYP Defense - December 2025*
