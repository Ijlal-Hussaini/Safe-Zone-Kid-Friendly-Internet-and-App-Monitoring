package com.safezone.app.activities;

import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.card.MaterialCardView;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.ValueEventListener;
import com.safezone.app.R;
import com.safezone.app.models.ChildUser;
import com.safezone.app.utils.FirebaseHelper;
import com.safezone.app.utils.ImageHelper;

import de.hdodenhof.circleimageview.CircleImageView;

/**
 * Child Details Activity - Shows monitoring options for a specific child
 * Replaces the dropdown menu with a dedicated screen
 */
public class ChildDetailsActivity extends AppCompatActivity {

    private MaterialToolbar toolbar;
    private CircleImageView imgChildProfile;
    private TextView tvChildName;
    private TextView tvChildAge;
    private TextView tvDeviceNickname;
    private MaterialCardView cardActivityLogs;
    private MaterialCardView cardScreenTime;
    private MaterialCardView cardContentFilter;
    private MaterialCardView cardLocation;
    private ProgressBar progressBar;

    private String childUid;
    private ChildUser childUser;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_child_details);

        // Get child UID from intent
        childUid = getIntent().getStringExtra("childUid");
        if (childUid == null || childUid.isEmpty()) {
            Toast.makeText(this, "Child data not found", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        initViews();
        setupToolbar();
        setupClickListeners();
        loadChildData();
    }

    private void initViews() {
        toolbar = findViewById(R.id.toolbar);
        imgChildProfile = findViewById(R.id.img_child_profile);
        tvChildName = findViewById(R.id.tv_child_name);
        tvChildAge = findViewById(R.id.tv_child_age);
        tvDeviceNickname = findViewById(R.id.tv_device_nickname);
        cardActivityLogs = findViewById(R.id.card_activity_logs);
        cardScreenTime = findViewById(R.id.card_screen_time);
        cardContentFilter = findViewById(R.id.card_content_filter);
        cardLocation = findViewById(R.id.card_location);
        progressBar = findViewById(R.id.progress_bar);
    }

    private void setupToolbar() {
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayShowTitleEnabled(false);
        }
        toolbar.setNavigationOnClickListener(v -> finish());
    }

    private void setupClickListeners() {
        cardActivityLogs.setOnClickListener(v -> openActivityLogs());
        cardScreenTime.setOnClickListener(v -> openScreenTimeSettings());
        cardContentFilter.setOnClickListener(v -> openContentFilter());
        cardLocation.setOnClickListener(v -> openLocationMap());
    }

    private void loadChildData() {
        showLoading(true);
        
        // Hide image initially to prevent flash of default icon
        imgChildProfile.setVisibility(View.INVISIBLE);

        FirebaseHelper.getUserRef(childUid).addListenerForSingleValueEvent(
                new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        showLoading(false);

                        if (snapshot.exists()) {
                            try {
                                // Manually parse child data to avoid deserialization issues
                                childUser = new ChildUser();
                                childUser.setUid(childUid);
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
                                
                                if (snapshot.child("parentId").exists()) {
                                    childUser.setParentId(snapshot.child("parentId").getValue(String.class));
                                }
                                
                                // Load profile photo from same snapshot (no extra query)
                                String base64Photo = snapshot.child("profilePhotoBase64").getValue(String.class);
                                if (base64Photo != null && !base64Photo.isEmpty()) {
                                    Bitmap bitmap = ImageHelper.decodeBase64ToBitmap(base64Photo);
                                    if (bitmap != null) {
                                        imgChildProfile.setImageBitmap(bitmap);
                                    } else {
                                        imgChildProfile.setImageResource(R.drawable.child_avatar);
                                    }
                                } else {
                                    imgChildProfile.setImageResource(R.drawable.child_avatar);
                                }
                                imgChildProfile.setVisibility(View.VISIBLE);
                                
                                displayChildInfo();
                            } catch (Exception e) {
                                android.util.Log.e("ChildDetailsActivity", "Error parsing child data: " + e.getMessage());
                                Toast.makeText(ChildDetailsActivity.this,
                                        "Error loading child data", Toast.LENGTH_SHORT).show();
                                finish();
                            }
                        } else {
                            Toast.makeText(ChildDetailsActivity.this,
                                    "Child data not found", Toast.LENGTH_SHORT).show();
                            finish();
                        }
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        showLoading(false);
                        imgChildProfile.setVisibility(View.VISIBLE);
                        Toast.makeText(ChildDetailsActivity.this,
                                "Error: " + error.getMessage(), Toast.LENGTH_SHORT).show();
                        finish();
                    }
                }
        );
    }

    private void displayChildInfo() {
        tvChildName.setText(childUser.getName());

        String ageText = childUser.getAge() > 0
                ? childUser.getAge() + " years old"
                : "Age not set";
        tvChildAge.setText(ageText);

        if (childUser.getDeviceNickname() != null && !childUser.getDeviceNickname().isEmpty()) {
            tvDeviceNickname.setText(childUser.getDeviceNickname());
            tvDeviceNickname.setVisibility(View.VISIBLE);
        } else {
            tvDeviceNickname.setVisibility(View.GONE);
        }
    }



    private void openActivityLogs() {
        Intent intent = new Intent(this, ActivityLogsActivity.class);
        intent.putExtra("childUid", childUid);
        startActivity(intent);
    }

    private void openScreenTimeSettings() {
        Intent intent = new Intent(this, ScreenTimeSettingsActivity.class);
        intent.putExtra("childUid", childUid);
        startActivity(intent);
    }

    private void openContentFilter() {
        Intent intent = new Intent(this, ContentFilterActivity.class);
        intent.putExtra("childUid", childUid);
        startActivity(intent);
    }

    private void openLocationMap() {
        FirebaseHelper.getUserRef(childUid).addListenerForSingleValueEvent(
                new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        if (snapshot.exists()) {
                            String childName = snapshot.child("name").getValue(String.class);

                            Intent intent = new Intent(ChildDetailsActivity.this, LocationMapActivity.class);
                            intent.putExtra("childUid", childUid);
                            intent.putExtra("childName", childName);
                            startActivity(intent);
                        } else {
                            Toast.makeText(ChildDetailsActivity.this,
                                    "Child data not found", Toast.LENGTH_SHORT).show();
                        }
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        Toast.makeText(ChildDetailsActivity.this,
                                "Error loading child data", Toast.LENGTH_SHORT).show();
                    }
                }
        );
    }

    private void showLoading(boolean show) {
        progressBar.setVisibility(show ? View.VISIBLE : View.GONE);
    }
}
