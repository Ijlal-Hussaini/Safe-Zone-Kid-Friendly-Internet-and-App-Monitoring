package com.safezone.app.activities;

import android.app.Activity;
import android.app.DatePickerDialog;
import android.app.ProgressDialog;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.ValueEventListener;
import com.safezone.app.R;
import com.safezone.app.models.ActivityLog;
import com.safezone.app.models.ChildUser;
import com.safezone.app.utils.ChartHelper;
import com.safezone.app.utils.FirebaseHelper;
import com.safezone.app.utils.ImageHelper;
import com.safezone.app.utils.ValidationUtils;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

import de.hdodenhof.circleimageview.CircleImageView;

/**
 * Child Profile Activity - Base64 Storage Version
 * NO FIREBASE STORAGE REQUIRED
 */
public class ChildProfileActivity extends AppCompatActivity {

    private MaterialToolbar toolbar;
    private CircleImageView imgProfile;
    private CircleImageView imgParentProfile;
    private Button btnChangePhoto;
    private Button btnRemovePhoto;
    private TextInputEditText etName;
    private TextInputEditText etEmail;
    private TextInputEditText etDateOfBirth;
    private TextInputEditText etDeviceNickname;
    private TextView etParentEmail;
    private TextView etScreenTimeLimit;

    private MaterialCardView layoutUsageStats;
    private TextView tvTodayUsage;
    private TextView tvWeeklyUsage;
    private TextView tvRestrictedApps;

    private Button btnSave;
    private Button btnCancel;

    private ChildUser childUser;
    private String childId;
    private Uri selectedImageUri;
    private boolean shouldRemovePhoto = false;
    private ProgressDialog progressDialog;
    private Calendar selectedDate;

    private ActivityResultLauncher<Intent> imagePickerLauncher;
    private ValueEventListener parentIdListener;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_child_profile);

        childId = getIntent().getStringExtra("childId");
        android.util.Log.e("ChildProfile", "Intent childId: " + childId);
        
        if (childId == null) {
            childId = FirebaseHelper.getCurrentUserId();
            android.util.Log.e("ChildProfile", "Using current user ID: " + childId);
        }

        if (childId == null) {
            android.util.Log.e("ChildProfile", "ERROR: Child ID is null!");
            Toast.makeText(this, "Child ID not found", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        
        android.util.Log.e("ChildProfile", "Final childId: " + childId);

        selectedDate = Calendar.getInstance();

        initViews();
        setupToolbar();
        setupImagePicker();
        setupButtons();
        loadChildProfile(); // This will call setupParentIdListener() when done
        loadUsageStats();
    }

    private void initViews() {
        toolbar = findViewById(R.id.toolbar);
        imgProfile = findViewById(R.id.img_profile);
        imgParentProfile = findViewById(R.id.img_parent_profile);
        btnChangePhoto = findViewById(R.id.btn_change_photo);
        btnRemovePhoto = findViewById(R.id.btn_remove_photo);
        etName = findViewById(R.id.et_name);
        etEmail = findViewById(R.id.et_email);
        etDateOfBirth = findViewById(R.id.et_date_of_birth);
        etDeviceNickname = findViewById(R.id.et_device_nickname);
        etParentEmail = findViewById(R.id.et_parent_email);
        etScreenTimeLimit = findViewById(R.id.et_screen_time_limit);

        layoutUsageStats = findViewById(R.id.layout_usage_stats);
        tvTodayUsage = findViewById(R.id.tv_today_usage);
        tvWeeklyUsage = findViewById(R.id.tv_weekly_usage);
        tvRestrictedApps = findViewById(R.id.tv_restricted_apps);

        btnSave = findViewById(R.id.btn_save);
        btnCancel = findViewById(R.id.btn_cancel);

        progressDialog = new ProgressDialog(this);
        progressDialog.setCancelable(false);
    }

    private void setupToolbar() {
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayShowTitleEnabled(false);
        }
        
        toolbar.setNavigationOnClickListener(v -> finish());
    }

    private void setupImagePicker() {
        imagePickerLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                        selectedImageUri = result.getData().getData();
                        if (selectedImageUri != null && ImageHelper.isValidImageUri(this, selectedImageUri)) {
                            shouldRemovePhoto = false;
                            try {
                                Bitmap bitmap = MediaStore.Images.Media.getBitmap(
                                        getContentResolver(), selectedImageUri);
                                imgProfile.setImageBitmap(bitmap);
                                // Upload immediately
                                uploadPhotoImmediately();
                            } catch (Exception e) {
                                imgProfile.setImageResource(R.drawable.child_avatar);
                            }
                        }
                    }
                }
        );

        btnChangePhoto.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
            intent.setType("image/*");
            imagePickerLauncher.launch(intent);
        });

        btnRemovePhoto.setOnClickListener(v -> {
            new MaterialAlertDialogBuilder(this)
                    .setTitle("Remove Photo")
                    .setMessage("Are you sure you want to remove your profile photo?")
                    .setPositiveButton("Yes", (dialog, which) -> {
                        // Delete immediately
                        deletePhotoImmediately();
                    })
                    .setNegativeButton("No", null)
                    .show();
        });
    }
    
    private void deletePhotoImmediately() {
        progressDialog.setMessage("Removing photo...");
        progressDialog.show();
        
        ImageHelper.deleteProfileImage(childId,
                aVoid -> {
                    progressDialog.dismiss();
                    shouldRemovePhoto = false;
                    selectedImageUri = null;
                    imgProfile.setImageResource(R.drawable.child_avatar);
                    Toast.makeText(this, "Photo removed", Toast.LENGTH_SHORT).show();
                },
                e -> {
                    progressDialog.dismiss();
                    Toast.makeText(this, "Failed to remove photo", Toast.LENGTH_SHORT).show();
                }
        );
    }
    
    private void uploadPhotoImmediately() {
        if (selectedImageUri == null) return;
        
        progressDialog.setMessage("Uploading photo...");
        progressDialog.show();
        
        byte[] imageData = ImageHelper.compressImage(this, selectedImageUri);
        if (imageData == null) {
            progressDialog.dismiss();
            Toast.makeText(this, "Failed to process image", Toast.LENGTH_SHORT).show();
            return;
        }
        
        ImageHelper.uploadProfileImage(childId, imageData, new ImageHelper.UploadCallback() {
            @Override
            public void onSuccess(String base64Data) {
                progressDialog.dismiss();
                selectedImageUri = null;
                Toast.makeText(ChildProfileActivity.this, "Photo updated", Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onFailure(String error) {
                progressDialog.dismiss();
                Toast.makeText(ChildProfileActivity.this, "Upload failed: " + error, Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onProgress(int progress) {
                progressDialog.setMessage("Uploading... " + progress + "%");
            }
        });
    }

    private void setupButtons() {
        etDateOfBirth.setOnClickListener(v -> showDatePicker());
        btnSave.setOnClickListener(v -> saveProfile());
        btnCancel.setOnClickListener(v -> finish());
    }

    private void showDatePicker() {
        DatePickerDialog datePickerDialog = new DatePickerDialog(
                this,
                (view, year, month, dayOfMonth) -> {
                    selectedDate.set(year, month, dayOfMonth);
                    SimpleDateFormat sdf = new SimpleDateFormat("MMM dd, yyyy", Locale.getDefault());
                    etDateOfBirth.setText(sdf.format(selectedDate.getTime()));
                },
                selectedDate.get(Calendar.YEAR),
                selectedDate.get(Calendar.MONTH),
                selectedDate.get(Calendar.DAY_OF_MONTH)
        );

        datePickerDialog.getDatePicker().setMaxDate(System.currentTimeMillis());
        Calendar minDate = Calendar.getInstance();
        minDate.add(Calendar.YEAR, -18);
        datePickerDialog.getDatePicker().setMinDate(minDate.getTimeInMillis());

        datePickerDialog.show();
    }

    private void loadChildProfile() {
        // Don't show blocking dialog - let UI load immediately
        // progressDialog.setMessage("Loading profile...");
        // progressDialog.show();

        FirebaseHelper.getUserRef(childId).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (!snapshot.exists()) {
                    Toast.makeText(ChildProfileActivity.this, "Profile not found", Toast.LENGTH_SHORT).show();
                    finish();
                    return;
                }

                try {
                    // Manually parse child data to avoid deserialization issues
                    childUser = new ChildUser();
                    childUser.setUid(childId);
                    childUser.setName(snapshot.child("name").getValue(String.class));
                    childUser.setEmail(snapshot.child("email").getValue(String.class));
                    childUser.setRole(snapshot.child("role").getValue(String.class));
                    
                    if (snapshot.child("age").exists()) {
                        Integer age = snapshot.child("age").getValue(Integer.class);
                        if (age != null) childUser.setAge(age);
                    }
                    
                    if (snapshot.child("dateOfBirth").exists()) {
                        Long dob = snapshot.child("dateOfBirth").getValue(Long.class);
                        if (dob != null) childUser.setDateOfBirth(dob);
                    }
                    
                    if (snapshot.child("deviceNickname").exists()) {
                        childUser.setDeviceNickname(snapshot.child("deviceNickname").getValue(String.class));
                    }
                    
                    // Load parent info - first check cached values in this snapshot
                    String parentId = snapshot.child("parentId").getValue(String.class);
                    String cachedParentEmail = snapshot.child("parentEmail").getValue(String.class);
                    String cachedParentName = snapshot.child("parentName").getValue(String.class);
                    
                    android.util.Log.d("ChildProfile", "Initial parentId: " + parentId);
                    android.util.Log.d("ChildProfile", "Cached parentEmail: " + cachedParentEmail);
                    android.util.Log.d("ChildProfile", "Cached parentName: " + cachedParentName);
                    
                    if (parentId != null && !parentId.isEmpty()) {
                        childUser.setParentId(parentId);
                        
                        // Use cached info if available (most reliable)
                        if (cachedParentEmail != null && !cachedParentEmail.isEmpty()) {
                            etParentEmail.setText(cachedParentEmail);
                            android.util.Log.d("ChildProfile", "Using cached parentEmail: " + cachedParentEmail);
                        } else if (cachedParentName != null && !cachedParentName.isEmpty()) {
                            etParentEmail.setText(cachedParentName);
                            android.util.Log.d("ChildProfile", "Using cached parentName: " + cachedParentName);
                        } else {
                            // No cached info, try to load from parent's record
                            android.util.Log.d("ChildProfile", "No cached parent info, loading from parent record");
                            loadParentInfoById(parentId);
                        }
                        
                        // Always try to load parent's profile photo
                        tryLoadParentPhoto(parentId);
                    } else {
                        etParentEmail.setText("Not linked");
                        imgParentProfile.setImageResource(R.drawable.parent_avatar);
                    }
                    
                    displayProfile();
                    
                    // Load profile photo from same snapshot (no extra query)
                    String base64Photo = snapshot.child("profilePhotoBase64").getValue(String.class);
                    if (base64Photo != null && !base64Photo.isEmpty()) {
                        Bitmap bitmap = ImageHelper.decodeBase64ToBitmap(base64Photo);
                        if (bitmap != null) {
                            imgProfile.setImageBitmap(bitmap);
                        }
                    }
                    
                    // Set up real-time listener for future changes
                    setupParentIdListener();
                } catch (Exception e) {
                    android.util.Log.e("ChildProfileActivity", "Error parsing profile: " + e.getMessage());
                    Toast.makeText(ChildProfileActivity.this, "Error loading profile", Toast.LENGTH_SHORT).show();
                }
                // progressDialog.dismiss(); - not showing dialog anymore
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Toast.makeText(ChildProfileActivity.this, "Error: " + error.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }



    private void loadProfilePhotoFromDatabase() {
        FirebaseHelper.getUserRef(childId).child("profilePhotoBase64")
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        if (snapshot.exists()) {
                            String base64Image = snapshot.getValue(String.class);
                            if (base64Image != null && !base64Image.isEmpty()) {
                                Bitmap bitmap = ImageHelper.decodeBase64ToBitmap(base64Image);
                                if (bitmap != null) {
                                    imgProfile.setImageBitmap(bitmap);
                                }
                            }
                        }
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        // Ignore
                    }
                });
    }

    private void displayProfile() {
        etName.setText(childUser.getName());
        etEmail.setText(childUser.getEmail());

        // Null-safe dateOfBirth handling
        Long dob = childUser.getDateOfBirth();
        if (dob != null && dob > 0) {
            selectedDate.setTimeInMillis(dob);
            SimpleDateFormat sdf = new SimpleDateFormat("MMM dd, yyyy", Locale.getDefault());
            etDateOfBirth.setText(sdf.format(selectedDate.getTime()));
        }

        if (childUser.getDeviceNickname() != null) {
            etDeviceNickname.setText(childUser.getDeviceNickname());
        }

        etEmail.setEnabled(false);
        
        // Load screen time limit from Firebase separately
        loadScreenTimeLimit();
    }
    
    /**
     * Load screen time limit from Firebase
     */
    private void loadScreenTimeLimit() {
        FirebaseHelper.getUserRef(childId).child("screenTimeRules")
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        if (snapshot.exists()) {
                            Boolean enabled = snapshot.child("enabled").getValue(Boolean.class);
                            Integer limitMinutes = snapshot.child("dailyLimitMinutes").getValue(Integer.class);
                            
                            if (enabled != null && enabled && limitMinutes != null && limitMinutes > 0) {
                                int hours = limitMinutes / 60;
                                int mins = limitMinutes % 60;
                                String limitText;
                                if (hours > 0 && mins > 0) {
                                    limitText = hours + "h " + mins + "m";
                                } else if (hours > 0) {
                                    limitText = hours + "h";
                                } else {
                                    limitText = mins + "m";
                                }
                                etScreenTimeLimit.setText(limitText);
                            } else {
                                etScreenTimeLimit.setText("Not set");
                            }
                        } else {
                            etScreenTimeLimit.setText("Not set");
                        }
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        etScreenTimeLimit.setText("Not set");
                    }
                });
    }

    private void loadUsageStats() {
        // Load activity logs for usage statistics
        FirebaseHelper.getUserRef(childId).child("activityLogs")
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        if (!snapshot.exists()) {
                            tvTodayUsage.setText("0m");
                            tvWeeklyUsage.setText("0h 0m");
                            return;
                        }

                        List<ActivityLog> allLogs = new ArrayList<>();
                        for (DataSnapshot logSnapshot : snapshot.getChildren()) {
                            try {
                                ActivityLog log = logSnapshot.getValue(ActivityLog.class);
                                if (log != null) {
                                    allLogs.add(log);
                                }
                            } catch (Exception e) {
                                // Skip invalid logs
                            }
                        }

                        calculateUsageStats(allLogs);
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        tvTodayUsage.setText("0m");
                        tvWeeklyUsage.setText("0h 0m");
                    }
                });

        // Load restricted content count from Firebase separately
        loadRestrictedContentCount();
    }
    
    /**
     * Load restricted content count from Firebase
     */
    private void loadRestrictedContentCount() {
        FirebaseHelper.getUserRef(childId).child("contentRules")
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        int blockedCount = 0;
                        
                        if (snapshot.exists()) {
                            // Count blocked apps
                            DataSnapshot appsSnapshot = snapshot.child("blockedApps");
                            if (appsSnapshot.exists()) {
                                blockedCount += (int) appsSnapshot.getChildrenCount();
                            }
                            
                            // Count blocked websites
                            DataSnapshot websitesSnapshot = snapshot.child("blockedWebsites");
                            if (websitesSnapshot.exists()) {
                                blockedCount += (int) websitesSnapshot.getChildrenCount();
                            }
                        }
                        
                        tvRestrictedApps.setText(blockedCount + " items");
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        tvRestrictedApps.setText("0 items");
                    }
                });
    }

    private void calculateUsageStats(List<ActivityLog> logs) {
        long todayStart = getTodayStartMillis();
        long weekStart = getWeekStartMillis();
        long now = System.currentTimeMillis();

        long todayUsageMs = 0;
        long weekUsageMs = 0;

        // Track unique log entries to prevent double counting
        java.util.Set<String> processedLogs = new java.util.HashSet<>();
        
        for (ActivityLog log : logs) {
            if (log == null) continue;
            
            long duration = log.getDuration();
            long timestamp = log.getTimestamp();
            
            // Skip invalid durations:
            // - Negative values
            // - Zero values  
            // - More than 6 minutes per log entry (service logs every 5 min, allow 1 min buffer)
            //   This filters out old cumulative data that was incorrectly stored
            if (duration <= 0 || duration > 6 * 60 * 1000) {
                android.util.Log.d("UsageStats", "Skipping invalid duration: " + duration + "ms for " + log.getAppName());
                continue;
            }
            
            // Create unique key to prevent duplicate counting
            String logKey = log.getLogId();
            if (logKey == null || logKey.isEmpty()) {
                logKey = log.getPackageName() + "_" + timestamp;
            }
            
            if (processedLogs.contains(logKey)) {
                continue;
            }
            processedLogs.add(logKey);
            
            // Today's usage
            if (timestamp >= todayStart && timestamp <= now) {
                todayUsageMs += duration;
            }
            
            // Weekly usage
            if (timestamp >= weekStart && timestamp <= now) {
                weekUsageMs += duration;
            }
        }

        // Convert to minutes
        long todayMinutes = todayUsageMs / (1000 * 60);
        long weekMinutes = weekUsageMs / (1000 * 60);
        
        // Sanity check - cap at realistic maximums (shouldn't hit these with fixed data)
        // Max 16 hours/day active usage is realistic
        todayMinutes = Math.min(todayMinutes, 16 * 60);
        // Max 16 hours * 7 days for weekly
        weekMinutes = Math.min(weekMinutes, 7 * 16 * 60);

        android.util.Log.d("UsageStats", "Today: " + todayMinutes + "m, Week: " + weekMinutes + "m from " + processedLogs.size() + " logs");

        tvTodayUsage.setText(formatDuration(todayMinutes));
        tvWeeklyUsage.setText(formatDuration(weekMinutes));
    }
    
    /**
     * Format duration in minutes to human readable string
     */
    private String formatDuration(long totalMinutes) {
        if (totalMinutes <= 0) {
            return "0m";
        }
        
        long hours = totalMinutes / 60;
        long minutes = totalMinutes % 60;
        
        if (hours > 0 && minutes > 0) {
            return hours + "h " + minutes + "m";
        } else if (hours > 0) {
            return hours + "h";
        } else {
            return minutes + "m";
        }
    }

    private long getTodayStartMillis() {
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        return cal.getTimeInMillis();
    }

    private long getWeekStartMillis() {
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.DAY_OF_WEEK, cal.getFirstDayOfWeek());
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        return cal.getTimeInMillis();
    }

    private void saveProfile() {
        String name = etName.getText().toString().trim();
        String deviceNickname = etDeviceNickname.getText().toString().trim();
        String dobString = etDateOfBirth.getText().toString().trim();

        if (!ValidationUtils.isValidName(name)) {
            etName.setError("Enter valid name (min 2 characters)");
            return;
        }

        if (dobString.isEmpty()) {
            etDateOfBirth.setError("Select date of birth");
            return;
        }

        int age = calculateAge(selectedDate);
        if (!ValidationUtils.isValidAge(age)) {
            Toast.makeText(this, "Age must be between 1-18 years", Toast.LENGTH_SHORT).show();
            return;
        }

        childUser.setName(name);
        childUser.setAge(age);
        childUser.setDateOfBirth(selectedDate.getTimeInMillis());
        childUser.setDeviceNickname(deviceNickname.isEmpty() ? null : deviceNickname);

        // Photo is now handled immediately on select/delete, just save profile data
        saveToDatabase();
    }

    private int calculateAge(Calendar dob) {
        Calendar today = Calendar.getInstance();
        int age = today.get(Calendar.YEAR) - dob.get(Calendar.YEAR);
        if (today.get(Calendar.DAY_OF_YEAR) < dob.get(Calendar.DAY_OF_YEAR)) {
            age--;
        }
        return age;
    }

    // Keep for backward compatibility but not used anymore
    private void removePhotoAndSave() {
        saveToDatabase();
    }

    private void uploadImageAndSave() {
        saveToDatabase();
    }
    
    // Unused placeholder to maintain structure
    private void unusedPhotoCallback() {
        ImageHelper.UploadCallback callback = new ImageHelper.UploadCallback() {
            @Override
            public void onSuccess(String base64Data) {
                // Not used
            }

            @Override
            public void onFailure(String error) {
                // Not used
                Toast.makeText(ChildProfileActivity.this, "Upload failed: " + error, Toast.LENGTH_LONG).show();
            }

            @Override
            public void onProgress(int progress) {
                progressDialog.setMessage("Saving photo... " + progress + "%");
            }
        };
    }

    private void saveToDatabase() {
        progressDialog.setMessage("Saving...");
        progressDialog.show();

        // ✅ CRITICAL FIX: Use updateChildren() instead of setValue() to preserve nested data
        // setValue() overwrites EVERYTHING including contentRules, screenTimeRules, etc.
        // updateChildren() only updates specified fields
        
        java.util.Map<String, Object> updates = new java.util.HashMap<>();
        updates.put("name", childUser.getName());
        updates.put("age", childUser.getAge());
        updates.put("dateOfBirth", childUser.getDateOfBirth());
        
        if (childUser.getDeviceNickname() != null && !childUser.getDeviceNickname().isEmpty()) {
            updates.put("deviceNickname", childUser.getDeviceNickname());
        }

        FirebaseHelper.getUserRef(childId).updateChildren(updates)
                .addOnSuccessListener(aVoid -> {
                    progressDialog.dismiss();
                    Toast.makeText(this, "Profile updated", Toast.LENGTH_SHORT).show();
                    setResult(RESULT_OK);
                    finish();
                })
                .addOnFailureListener(e -> {
                    progressDialog.dismiss();
                    Toast.makeText(this, "Failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
    }

    private void loadParentInfoById(String parentId) {
        if (parentId == null || parentId.isEmpty()) {
            etParentEmail.setText("Not linked");
            imgParentProfile.setImageResource(R.drawable.parent_avatar);
            return;
        }
        
        android.util.Log.d("ChildProfile", "Loading parent info for ID: " + parentId);
        
        // Show loading state
        etParentEmail.setText("Loading...");
        
        // STRATEGY: First check if we have cached parent info in child's record
        // This is more reliable than trying to read parent's data directly
        FirebaseHelper.getUserRef(childId).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot childSnapshot) {
                if (isFinishing() || isDestroyed()) return;
                
                // Check for cached parent info first
                String cachedEmail = childSnapshot.child("parentEmail").getValue(String.class);
                String cachedName = childSnapshot.child("parentName").getValue(String.class);
                
                android.util.Log.d("ChildProfile", "Cached parent info - email: " + cachedEmail + ", name: " + cachedName);
                
                if (cachedEmail != null && !cachedEmail.isEmpty()) {
                    // We have cached email, use it
                    etParentEmail.setText(cachedEmail);
                    imgParentProfile.setImageResource(R.drawable.parent_avatar);
                    // Still try to load parent photo in background
                    tryLoadParentPhoto(parentId);
                } else if (cachedName != null && !cachedName.isEmpty()) {
                    etParentEmail.setText(cachedName);
                    imgParentProfile.setImageResource(R.drawable.parent_avatar);
                    tryLoadParentPhoto(parentId);
                } else {
                    // No cached info, try direct parent lookup and cache the result
                    loadAndCacheParentInfo(parentId);
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                if (isFinishing() || isDestroyed()) return;
                // Fallback to direct lookup
                loadAndCacheParentInfo(parentId);
            }
        });
    }
    
    /**
     * Try to load parent photo in background
     */
    private void tryLoadParentPhoto(String parentId) {
        FirebaseHelper.getUserRef(parentId).child("profilePhotoBase64")
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        if (isFinishing() || isDestroyed()) return;
                        if (snapshot.exists()) {
                            String photo = snapshot.getValue(String.class);
                            if (photo != null && !photo.isEmpty()) {
                                Bitmap bmp = ImageHelper.decodeBase64ToBitmap(photo);
                                if (bmp != null) {
                                    imgParentProfile.setImageBitmap(bmp);
                                }
                            }
                        }
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        // Ignore - photo is optional
                    }
                });
    }
    
    /**
     * Load parent info directly and cache it in child's record for future use
     * NOTE: Firebase rules may not allow child to read parent's data directly,
     * so we try multiple approaches
     */
    private void loadAndCacheParentInfo(String parentId) {
        // Check if parentId is actually an email
        if (parentId.contains("@")) {
            etParentEmail.setText(parentId);
            imgParentProfile.setImageResource(R.drawable.parent_avatar);
            return;
        }
        
        // Try to read just the email field (might be allowed by rules)
        FirebaseHelper.getUserRef(parentId).child("email")
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        if (isFinishing() || isDestroyed()) return;
                        
                        if (snapshot.exists()) {
                            String email = snapshot.getValue(String.class);
                            android.util.Log.d("ChildProfile", "Got parent email: " + email);
                            
                            if (email != null && !email.isEmpty()) {
                                etParentEmail.setText(email);
                                // Cache for future use
                                FirebaseHelper.getUserRef(childId).child("parentEmail").setValue(email);
                                imgParentProfile.setImageResource(R.drawable.parent_avatar);
                                return;
                            }
                        }
                        
                        // Email not accessible, try name
                        tryLoadParentName(parentId);
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        if (isFinishing() || isDestroyed()) return;
                        android.util.Log.e("ChildProfile", "Cannot read parent email: " + error.getMessage());
                        // Try name as fallback
                        tryLoadParentName(parentId);
                    }
                });
    }
    
    /**
     * Try to load parent name as fallback
     */
    private void tryLoadParentName(String parentId) {
        FirebaseHelper.getUserRef(parentId).child("name")
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        if (isFinishing() || isDestroyed()) return;
                        
                        if (snapshot.exists()) {
                            String name = snapshot.getValue(String.class);
                            if (name != null && !name.isEmpty()) {
                                etParentEmail.setText(name);
                                FirebaseHelper.getUserRef(childId).child("parentName").setValue(name);
                                imgParentProfile.setImageResource(R.drawable.parent_avatar);
                                return;
                            }
                        }
                        
                        // Neither email nor name accessible - try linkToken as last resort
                        tryLoadParentInfoFromLinkToken(parentId);
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        if (isFinishing() || isDestroyed()) return;
                        android.util.Log.e("ChildProfile", "Cannot read parent name: " + error.getMessage());
                        // Try linkToken as last resort
                        tryLoadParentInfoFromLinkToken(parentId);
                    }
                });
    }
    
    /**
     * Last resort: try to find parent info from linkTokens
     */
    private void tryLoadParentInfoFromLinkToken(String parentId) {
        // Search linkTokens for one that has this parentUid and was used by this child
        FirebaseHelper.getLinkTokensRef().orderByChild("parentUid").equalTo(parentId)
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        if (isFinishing() || isDestroyed()) return;
                        
                        for (DataSnapshot tokenSnapshot : snapshot.getChildren()) {
                            String tokenChildUid = tokenSnapshot.child("childUid").getValue(String.class);
                            if (childId.equals(tokenChildUid)) {
                                // Found the token used to link this child
                                String parentEmail = tokenSnapshot.child("parentEmail").getValue(String.class);
                                String parentName = tokenSnapshot.child("parentName").getValue(String.class);
                                
                                android.util.Log.d("ChildProfile", "Found parent info in linkToken - email: " + parentEmail + ", name: " + parentName);
                                
                                if (parentEmail != null && !parentEmail.isEmpty()) {
                                    etParentEmail.setText(parentEmail);
                                    // Cache for future use
                                    FirebaseHelper.getUserRef(childId).child("parentEmail").setValue(parentEmail);
                                    imgParentProfile.setImageResource(R.drawable.parent_avatar);
                                    return;
                                } else if (parentName != null && !parentName.isEmpty()) {
                                    etParentEmail.setText(parentName);
                                    FirebaseHelper.getUserRef(childId).child("parentName").setValue(parentName);
                                    imgParentProfile.setImageResource(R.drawable.parent_avatar);
                                    return;
                                }
                            }
                        }
                        
                        // No luck - show linked status
                        etParentEmail.setText("Linked");
                        imgParentProfile.setImageResource(R.drawable.parent_avatar);
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        if (isFinishing() || isDestroyed()) return;
                        etParentEmail.setText("Linked");
                        imgParentProfile.setImageResource(R.drawable.parent_avatar);
                    }
                });
    }


    private void setupParentIdListener() {
        // Real-time listener for parentId changes (for disconnect/relink scenarios)
        parentIdListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (isFinishing() || isDestroyed()) return;
                
                String newParentId = snapshot.getValue(String.class);
                String currentParentId = childUser != null ? childUser.getParentId() : null;
                
                // Only update if parentId actually changed
                boolean parentIdChanged = (newParentId == null && currentParentId != null) ||
                                         (newParentId != null && !newParentId.equals(currentParentId));
                
                if (parentIdChanged) {
                    if (childUser != null) {
                        childUser.setParentId(newParentId);
                    }
                    
                    if (newParentId != null && !newParentId.isEmpty()) {
                        loadParentInfoById(newParentId);
                    } else {
                        etParentEmail.setText("Not linked");
                        imgParentProfile.setImageResource(R.drawable.parent_avatar);
                    }
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                // Silently ignore - initial load already handled
            }
        };

        FirebaseHelper.getUserRef(childId).child("parentId")
                .addValueEventListener(parentIdListener);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Remove listener to prevent memory leaks
        if (parentIdListener != null && childId != null) {
            FirebaseHelper.getUserRef(childId).child("parentId")
                    .removeEventListener(parentIdListener);
        }
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}
