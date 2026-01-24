package com.safezone.app.activities;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.MenuItem;
import android.widget.Button;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.ValueEventListener;
import com.safezone.app.R;
import com.safezone.app.models.ParentUser;
import com.safezone.app.utils.FirebaseHelper;
import com.safezone.app.utils.ImageHelper;
import com.safezone.app.utils.SharedPrefsHelper;
import com.safezone.app.utils.ValidationUtils;

import de.hdodenhof.circleimageview.CircleImageView;

/**
 * Parent Profile Activity - Base64 Storage Version
 * NO FIREBASE STORAGE REQUIRED
 */
public class ParentProfileActivity extends AppCompatActivity {

    private MaterialToolbar toolbar;
    private CircleImageView imgProfile;
    private Button btnChangePhoto;
    private Button btnRemovePhoto;
    private TextInputEditText etName;
    private TextInputEditText etEmail;
    private TextInputEditText etPhone;
    private Button btnSave;
    private Button btnCancel;

    private ParentUser parentUser;
    private String userId;
    private Uri selectedImageUri;
    private boolean shouldRemovePhoto = false;
    private ProgressDialog progressDialog;
    private SharedPrefsHelper prefsHelper;

    private ActivityResultLauncher<Intent> imagePickerLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_parent_profile);

        prefsHelper = new SharedPrefsHelper(this);
        userId = FirebaseHelper.getCurrentUserId();

        if (userId == null) {
            Toast.makeText(this, "User not logged in", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        initViews();
        setupToolbar();
        setupImagePicker();
        setupButtons();
        loadUserProfile();
    }

    private void initViews() {
        toolbar = findViewById(R.id.toolbar);
        imgProfile = findViewById(R.id.img_profile);
        btnChangePhoto = findViewById(R.id.btn_change_photo);
        btnRemovePhoto = findViewById(R.id.btn_remove_photo);
        etName = findViewById(R.id.et_name);
        etEmail = findViewById(R.id.et_email);
        etPhone = findViewById(R.id.et_phone);
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
                        if (selectedImageUri != null) {
                            if (!ImageHelper.isValidImageUri(this, selectedImageUri)) {
                                Toast.makeText(this, "Invalid image selected", Toast.LENGTH_SHORT).show();
                                return;
                            }

                            shouldRemovePhoto = false;

                            // Display selected image immediately
                            try {
                                Bitmap bitmap = MediaStore.Images.Media.getBitmap(
                                        getContentResolver(), selectedImageUri);
                                imgProfile.setImageBitmap(bitmap);
                                // Upload immediately
                                uploadPhotoImmediately();
                            } catch (Exception e) {
                                imgProfile.setImageResource(R.drawable.parent_avatar);
                            }
                        }
                    }
                }
        );

        btnChangePhoto.setOnClickListener(v -> openImagePicker());
        imgProfile.setOnClickListener(v -> openImagePicker());

        btnRemovePhoto.setOnClickListener(v -> {
            new MaterialAlertDialogBuilder(this)
                    .setTitle("Remove Photo")
                    .setMessage("Are you sure you want to remove your profile photo?")
                    .setPositiveButton("Yes", (dialog, which) -> {
                        // Delete immediately from Firebase
                        deletePhotoImmediately();
                    })
                    .setNegativeButton("No", null)
                    .show();
        });
    }
    
    private void deletePhotoImmediately() {
        progressDialog.setMessage("Removing photo...");
        progressDialog.show();
        
        ImageHelper.deleteProfileImage(userId,
                aVoid -> {
                    progressDialog.dismiss();
                    shouldRemovePhoto = false;
                    selectedImageUri = null;
                    imgProfile.setImageResource(R.drawable.parent_avatar);
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
        
        ImageHelper.uploadProfileImage(userId, imageData, new ImageHelper.UploadCallback() {
            @Override
            public void onSuccess(String base64Data) {
                progressDialog.dismiss();
                selectedImageUri = null;
                Toast.makeText(ParentProfileActivity.this, "Photo updated", Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onFailure(String error) {
                progressDialog.dismiss();
                Toast.makeText(ParentProfileActivity.this, "Upload failed: " + error, Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onProgress(int progress) {
                progressDialog.setMessage("Uploading... " + progress + "%");
            }
        });
    }

    private void openImagePicker() {
        Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        intent.setType("image/*");
        imagePickerLauncher.launch(intent);
    }

    private void setupButtons() {
        btnSave.setOnClickListener(v -> saveProfile());
        btnCancel.setOnClickListener(v -> finish());
    }

    private void loadUserProfile() {
        progressDialog.setMessage("Loading profile...");
        progressDialog.show();

        DatabaseReference userRef = FirebaseHelper.getUserRef(userId);

        userRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                progressDialog.dismiss();

                if (!snapshot.exists()) {
                    Toast.makeText(ParentProfileActivity.this,
                            "Profile not found", Toast.LENGTH_SHORT).show();
                    finish();
                    return;
                }

                parentUser = snapshot.getValue(ParentUser.class);
                if (parentUser != null) {
                    parentUser.setUid(userId);
                    displayUserProfile();
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                progressDialog.dismiss();
                Toast.makeText(ParentProfileActivity.this,
                        "Error loading profile: " + error.getMessage(),
                        Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void displayUserProfile() {
        etName.setText(parentUser.getName());
        etEmail.setText(parentUser.getEmail());
        etPhone.setText(parentUser.getPhone() != null ? parentUser.getPhone() : "");

        etEmail.setEnabled(false);
        etEmail.setFocusable(false);

        // Load profile photo from Base64
        loadProfilePhotoFromDatabase();
    }

    private void loadProfilePhotoFromDatabase() {
        DatabaseReference userRef = FirebaseHelper.getUserRef(userId);
        userRef.child("profilePhotoBase64").addListenerForSingleValueEvent(
                new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        if (snapshot.exists()) {
                            String base64Image = snapshot.getValue(String.class);
                            if (base64Image != null && !base64Image.isEmpty()) {
                                Bitmap bitmap = ImageHelper.decodeBase64ToBitmap(base64Image);
                                if (bitmap != null) {
                                    imgProfile.setImageBitmap(bitmap);
                                } else {
                                    imgProfile.setImageResource(R.drawable.parent_avatar);
                                }
                            }
                        }
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        // Ignore errors for photo loading
                    }
                }
        );
    }

    private void saveProfile() {
        String name = etName.getText().toString().trim();
        String phone = etPhone.getText().toString().trim();

        if (!ValidationUtils.isValidName(name)) {
            etName.setError("Please enter a valid name (minimum 2 characters)");
            etName.requestFocus();
            return;
        }

        if (!phone.isEmpty() && !ValidationUtils.isValidPhone(phone)) {
            etPhone.setError("Please enter a valid phone number (10-15 digits)");
            etPhone.requestFocus();
            return;
        }

        parentUser.setName(name);
        parentUser.setPhone(phone);

        // Photo is now handled immediately on select/delete, just save profile data
        saveProfileToDatabase();
    }

    // Keep these for backward compatibility but they're not used anymore
    private void removePhotoAndSave() {
        saveProfileToDatabase();
    }

    private void uploadImageAndSaveProfile() {
        saveProfileToDatabase();
    }
    
    // Unused callback placeholder
    private void unusedCallback() {
        ImageHelper.UploadCallback callback = new ImageHelper.UploadCallback() {
            @Override
            public void onSuccess(String base64Data) {
                saveProfileToDatabase();
            }

            @Override
            public void onFailure(String error) {
                progressDialog.dismiss();
                Toast.makeText(ParentProfileActivity.this,
                        "Failed to upload photo: " + error,
                        Toast.LENGTH_LONG).show();
            }

            @Override
            public void onProgress(int progress) {
                progressDialog.setMessage("Saving photo... " + progress + "%");
            }
        };
    }

    private void saveProfileToDatabase() {
        progressDialog.setMessage("Saving profile...");
        progressDialog.show();

        DatabaseReference userRef = FirebaseHelper.getUserRef(userId);

        // Use updateChildren instead of setValue to preserve profilePhotoBase64 and other data
        java.util.Map<String, Object> updates = new java.util.HashMap<>();
        updates.put("name", parentUser.getName());
        updates.put("email", parentUser.getEmail());
        updates.put("role", parentUser.getRole());
        if (parentUser.getPhone() != null && !parentUser.getPhone().isEmpty()) {
            updates.put("phone", parentUser.getPhone());
        }

        userRef.updateChildren(updates)
                .addOnSuccessListener(aVoid -> {
                    progressDialog.dismiss();
                    prefsHelper.updateUserName(parentUser.getName());
                    Toast.makeText(this, "Profile updated successfully", Toast.LENGTH_SHORT).show();
                    setResult(RESULT_OK);
                    finish();
                })
                .addOnFailureListener(e -> {
                    progressDialog.dismiss();
                    Toast.makeText(this,
                            "Failed to save profile: " + e.getMessage(),
                            Toast.LENGTH_SHORT).show();
                });
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