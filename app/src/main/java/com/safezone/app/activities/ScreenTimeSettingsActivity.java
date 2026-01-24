package com.safezone.app.activities;

import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.slider.Slider;
import com.google.android.material.switchmaterial.SwitchMaterial;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.ValueEventListener;
import com.safezone.app.R;
import com.safezone.app.adapters.AllowedAppsAdapter;
import com.safezone.app.adapters.ChildAppsAdapter;
import com.safezone.app.utils.FirebaseHelper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Screen Time Settings - Set daily limits and allowed apps for child
 */
public class ScreenTimeSettingsActivity extends AppCompatActivity {

    private MaterialToolbar toolbar;
    private SwitchMaterial switchEnable;
    private MaterialCardView cardDailyLimit, cardAllowedApps;
    private Slider sliderHours, sliderMinutes;
    private TextView tvHoursValue, tvMinutesValue, tvTotalLimit;
    private MaterialButton btnSave, btnAddAllowedApp;
    private ProgressBar progressBar;
    private RecyclerView recyclerAllowedApps;
    private LinearLayout emptyAllowedApps;

    private DatabaseReference childRef;
    private String childUid;
    private boolean isEnabled = false;
    private int currentHours = 2;
    private int currentMinutes = 0;
    private List<String> allowedApps = new ArrayList<>();
    private AllowedAppsAdapter allowedAppsAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        try {
            setContentView(R.layout.activity_screen_time_settings);

            // Get child UID from intent
            childUid = getIntent().getStringExtra("childUid");
            if (childUid == null || childUid.isEmpty()) {
                Toast.makeText(this, "Child data not found", Toast.LENGTH_SHORT).show();
                finish();
                return;
            }

            initViews();
            setupToolbar();
            setupSliders();
            setupAllowedApps();
            setupListeners();
            loadCurrentSettings();

        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_LONG).show();
            finish();
        }
    }

    private void initViews() {
        toolbar = findViewById(R.id.toolbar);
        switchEnable = findViewById(R.id.switch_enable);
        cardDailyLimit = findViewById(R.id.card_daily_limit);
        cardAllowedApps = findViewById(R.id.card_allowed_apps);
        sliderHours = findViewById(R.id.slider_hours);
        sliderMinutes = findViewById(R.id.slider_minutes);
        tvHoursValue = findViewById(R.id.tv_hours_value);
        tvMinutesValue = findViewById(R.id.tv_minutes_value);
        tvTotalLimit = findViewById(R.id.tv_total_limit);
        btnSave = findViewById(R.id.btn_save);
        btnAddAllowedApp = findViewById(R.id.btn_add_allowed_app);
        progressBar = findViewById(R.id.progress_bar);
        recyclerAllowedApps = findViewById(R.id.recycler_allowed_apps);
        emptyAllowedApps = findViewById(R.id.empty_allowed_apps);

        childRef = FirebaseHelper.getUsersRef().child(childUid);
    }

    private void setupToolbar() {
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayShowTitleEnabled(false);
        }
        toolbar.setNavigationOnClickListener(v -> finish());
    }

    private void setupSliders() {
        try {
            sliderHours.setValueFrom(0f);
            sliderHours.setValueTo(12f);
            sliderHours.setStepSize(1f);
            sliderHours.setValue(2f);

            sliderHours.addOnChangeListener((slider, value, fromUser) -> {
                if (fromUser) {
                    currentHours = (int) value;
                    tvHoursValue.setText(currentHours + "h");
                    updateTotalLimit();
                }
            });

            sliderMinutes.setValueFrom(0f);
            sliderMinutes.setValueTo(55f);
            sliderMinutes.setStepSize(5f);
            sliderMinutes.setValue(0f);

            sliderMinutes.addOnChangeListener((slider, value, fromUser) -> {
                if (fromUser) {
                    currentMinutes = (int) value;
                    tvMinutesValue.setText(currentMinutes + "m");
                    updateTotalLimit();
                }
            });

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void setupAllowedApps() {
        // Use horizontal LinearLayoutManager for scrollable apps
        LinearLayoutManager horizontalLayoutManager = new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false);
        recyclerAllowedApps.setLayoutManager(horizontalLayoutManager);
        allowedAppsAdapter = new AllowedAppsAdapter(this, allowedApps, this::removeAllowedApp, true);
        recyclerAllowedApps.setAdapter(allowedAppsAdapter);
        updateAllowedAppsVisibility();
    }

    private void setupListeners() {
        switchEnable.setOnCheckedChangeListener((buttonView, isChecked) -> {
            isEnabled = isChecked;
            updateCardsState();
        });

        btnSave.setOnClickListener(v -> saveSettings());
        btnAddAllowedApp.setOnClickListener(v -> showAddAllowedAppDialog());
    }

    private void updateCardsState() {
        cardDailyLimit.setEnabled(isEnabled);
        cardDailyLimit.setAlpha(isEnabled ? 1.0f : 0.5f);
        cardAllowedApps.setEnabled(isEnabled);
        cardAllowedApps.setAlpha(isEnabled ? 1.0f : 0.5f);
        sliderHours.setEnabled(isEnabled);
        sliderMinutes.setEnabled(isEnabled);
        btnAddAllowedApp.setEnabled(isEnabled);
    }

    private void loadCurrentSettings() {
        showLoading(true);

        childRef.child("screenTimeRules").addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                try {
                    showLoading(false);

                    if (snapshot.exists()) {
                        Boolean enabled = snapshot.child("enabled").getValue(Boolean.class);
                        Integer dailyLimit = snapshot.child("dailyLimitMinutes").getValue(Integer.class);

                        if (enabled != null) {
                            isEnabled = enabled;
                        }

                        if (dailyLimit != null) {
                            currentHours = dailyLimit / 60;
                            currentMinutes = dailyLimit % 60;
                        }

                        // Load allowed apps
                        allowedApps.clear();
                        DataSnapshot allowedAppsSnapshot = snapshot.child("allowedApps");
                        if (allowedAppsSnapshot.exists()) {
                            for (DataSnapshot appSnapshot : allowedAppsSnapshot.getChildren()) {
                                String packageName = appSnapshot.getValue(String.class);
                                if (packageName != null && !packageName.isEmpty()) {
                                    allowedApps.add(packageName);
                                }
                            }
                        }
                    }

                    applySettings();

                } catch (Exception e) {
                    e.printStackTrace();
                    showLoading(false);
                    applySettings();
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                showLoading(false);
                Toast.makeText(ScreenTimeSettingsActivity.this,
                        "Database error: " + error.getMessage(),
                        Toast.LENGTH_SHORT).show();
                applySettings();
            }
        });
    }

    private void applySettings() {
        try {
            switchEnable.setChecked(isEnabled);

            if (sliderHours != null) {
                sliderHours.setValue(currentHours);
            }
            if (sliderMinutes != null) {
                sliderMinutes.setValue(currentMinutes);
            }

            tvHoursValue.setText(currentHours + "h");
            tvMinutesValue.setText(currentMinutes + "m");
            updateTotalLimit();
            updateCardsState();
            updateAllowedAppsVisibility();
            allowedAppsAdapter.notifyDataSetChanged();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void updateTotalLimit() {
        try {
            int totalMinutes = (currentHours * 60) + currentMinutes;

            String limitText;
            if (totalMinutes == 0) {
                limitText = "No limit set";
            } else {
                limitText = "Total: " + currentHours + " hours " + currentMinutes + " minutes per day";
            }

            tvTotalLimit.setText(limitText);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void updateAllowedAppsVisibility() {
        if (allowedApps.isEmpty()) {
            recyclerAllowedApps.setVisibility(View.GONE);
            emptyAllowedApps.setVisibility(View.VISIBLE);
        } else {
            recyclerAllowedApps.setVisibility(View.VISIBLE);
            emptyAllowedApps.setVisibility(View.GONE);
        }
    }

    private void showAddAllowedAppDialog() {
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_select_app, null);
        
        RecyclerView recyclerApps = dialogView.findViewById(R.id.recycler_apps);
        TextInputEditText etSearch = dialogView.findViewById(R.id.et_search);
        ProgressBar dialogProgress = dialogView.findViewById(R.id.progress_bar);
        MaterialButton btnCancel = dialogView.findViewById(R.id.btn_cancel);

        recyclerApps.setLayoutManager(new LinearLayoutManager(this));

        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(this, R.style.ThemeOverlay_App_MaterialAlertDialog);
        builder.setView(dialogView);
        builder.setBackground(getResources().getDrawable(R.drawable.dialog_background, getTheme()));
        
        AlertDialog dialog = builder.create();

        // Load child's installed apps from Firebase
        dialogProgress.setVisibility(View.VISIBLE);
        
        loadChildInstalledApps(new OnChildAppsLoadedListener() {
            @Override
            public void onAppsLoaded(List<ChildAppInfo> childApps) {
                dialogProgress.setVisibility(View.GONE);
                
                if (childApps.isEmpty()) {
                    Toast.makeText(ScreenTimeSettingsActivity.this, 
                            "No apps found. Make sure child's device is connected.", 
                            Toast.LENGTH_LONG).show();
                    dialog.dismiss();
                    return;
                }
                
                ChildAppsAdapter adapter = new ChildAppsAdapter(
                        ScreenTimeSettingsActivity.this, 
                        childApps, 
                        allowedApps,
                        (packageName, appName) -> {
                            addAllowedApp(packageName);
                            dialog.dismiss();
                        }
                );
                
                recyclerApps.setAdapter(adapter);

                // Search functionality
                etSearch.addTextChangedListener(new TextWatcher() {
                    @Override
                    public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

                    @Override
                    public void onTextChanged(CharSequence s, int start, int before, int count) {
                        adapter.filter(s.toString());
                    }

                    @Override
                    public void afterTextChanged(Editable s) {}
                });
            }

            @Override
            public void onError(String error) {
                dialogProgress.setVisibility(View.GONE);
                Toast.makeText(ScreenTimeSettingsActivity.this, 
                        "Error loading apps: " + error, 
                        Toast.LENGTH_SHORT).show();
                dialog.dismiss();
            }
        });

        btnCancel.setOnClickListener(v -> dialog.dismiss());
        
        dialog.show();
    }

    /**
     * Load child's installed apps from Firebase
     */
    private void loadChildInstalledApps(OnChildAppsLoadedListener listener) {
        FirebaseHelper.getUsersRef().child(childUid).child("installedApps")
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        List<ChildAppInfo> apps = new ArrayList<>();
                        
                        if (snapshot.exists()) {
                            for (DataSnapshot appSnapshot : snapshot.getChildren()) {
                                String packageName = appSnapshot.child("packageName").getValue(String.class);
                                String appName = appSnapshot.child("appName").getValue(String.class);
                                
                                if (packageName != null && appName != null) {
                                    apps.add(new ChildAppInfo(packageName, appName));
                                }
                            }
                        }
                        
                        listener.onAppsLoaded(apps);
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        listener.onError(error.getMessage());
                    }
                });
    }

    /**
     * Interface for child apps loading callback
     */
    interface OnChildAppsLoadedListener {
        void onAppsLoaded(List<ChildAppInfo> apps);
        void onError(String error);
    }

    /**
     * Simple class to hold child app info from Firebase
     */
    public static class ChildAppInfo {
        public String packageName;
        public String appName;

        public ChildAppInfo(String packageName, String appName) {
            this.packageName = packageName;
            this.appName = appName;
        }
    }

    private void addAllowedApp(String packageName) {
        if (!allowedApps.contains(packageName)) {
            allowedApps.add(packageName);
            allowedAppsAdapter.notifyDataSetChanged();
            updateAllowedAppsVisibility();
            Toast.makeText(this, "App added to allowed list", Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(this, "App already in allowed list", Toast.LENGTH_SHORT).show();
        }
    }

    private void removeAllowedApp(String packageName) {
        new MaterialAlertDialogBuilder(this)
                .setTitle("Remove App")
                .setMessage("Remove this app from allowed list?")
                .setPositiveButton("Remove", (dialog, which) -> {
                    allowedApps.remove(packageName);
                    allowedAppsAdapter.notifyDataSetChanged();
                    updateAllowedAppsVisibility();
                    Toast.makeText(this, "App removed", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void saveSettings() {
        showLoading(true);

        try {
            int totalMinutes = (currentHours * 60) + currentMinutes;

            Map<String, Object> screenTimeRules = new HashMap<>();
            screenTimeRules.put("enabled", isEnabled);
            screenTimeRules.put("dailyLimitMinutes", totalMinutes);
            screenTimeRules.put("allowedApps", allowedApps);
            screenTimeRules.put("schedule", "");

            childRef.child("screenTimeRules").setValue(screenTimeRules)
                    .addOnSuccessListener(aVoid -> {
                        showLoading(false);
                        Toast.makeText(this, "Settings saved successfully!",
                                Toast.LENGTH_SHORT).show();
                        finish();
                    })
                    .addOnFailureListener(e -> {
                        showLoading(false);
                        Toast.makeText(this, "Error saving: " + e.getMessage(),
                                Toast.LENGTH_LONG).show();
                    });

        } catch (Exception e) {
            e.printStackTrace();
            showLoading(false);
            Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void showLoading(boolean show) {
        try {
            if (progressBar != null) {
                progressBar.setVisibility(show ? View.VISIBLE : View.GONE);
            }
            if (btnSave != null) {
                btnSave.setEnabled(!show);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
