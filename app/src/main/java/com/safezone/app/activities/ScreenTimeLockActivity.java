package com.safezone.app.activities;

import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.ValueEventListener;
import com.safezone.app.R;
import com.safezone.app.utils.FirebaseHelper;

import java.util.ArrayList;
import java.util.List;

/**
 * Full-screen lock activity shown when screen time limit is reached
 * Only allowed apps can be opened from this screen
 */
public class ScreenTimeLockActivity extends AppCompatActivity {

    private TextView tvUsedTime, tvDailyLimit, tvNoAllowedApps;
    private RecyclerView recyclerAllowedApps;
    private MaterialButton btnRequestTime;

    private List<String> allowedApps = new ArrayList<>();
    private int usedMinutes = 0;
    private int limitMinutes = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_screen_time_lock);

        // Get data from intent
        usedMinutes = getIntent().getIntExtra("usedMinutes", 0);
        limitMinutes = getIntent().getIntExtra("limitMinutes", 0);

        initViews();
        setupListeners();
        loadAllowedApps();
        updateTimeDisplay();
    }

    private void initViews() {
        tvUsedTime = findViewById(R.id.tv_used_time);
        tvDailyLimit = findViewById(R.id.tv_daily_limit);
        tvNoAllowedApps = findViewById(R.id.tv_no_allowed_apps);
        recyclerAllowedApps = findViewById(R.id.recycler_allowed_apps);
        btnRequestTime = findViewById(R.id.btn_request_time);

        // Use grid layout for allowed apps (2 columns)
        recyclerAllowedApps.setLayoutManager(new GridLayoutManager(this, 3));
    }

    private void setupListeners() {
        btnRequestTime.setOnClickListener(v -> requestMoreTime());
    }

    private void updateTimeDisplay() {
        int usedHours = usedMinutes / 60;
        int usedMins = usedMinutes % 60;
        tvUsedTime.setText(usedHours + "h " + usedMins + "m");

        int limitHours = limitMinutes / 60;
        int limitMins = limitMinutes % 60;
        tvDailyLimit.setText(limitHours + "h " + limitMins + "m");
    }

    private void loadAllowedApps() {
        String childUid = FirebaseHelper.getCurrentUserId();
        if (childUid == null) return;

        FirebaseHelper.getUsersRef().child(childUid).child("screenTimeRules")
                .child("allowedApps")
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        allowedApps.clear();
                        
                        // Always add Safe Zone itself
                        allowedApps.add(getPackageName());
                        
                        // Add phone dialer
                        allowedApps.add("com.android.dialer");
                        allowedApps.add("com.google.android.dialer");
                        allowedApps.add("com.samsung.android.dialer");
                        
                        if (snapshot.exists()) {
                            for (DataSnapshot appSnapshot : snapshot.getChildren()) {
                                String packageName = appSnapshot.getValue(String.class);
                                if (packageName != null && !allowedApps.contains(packageName)) {
                                    allowedApps.add(packageName);
                                }
                            }
                        }

                        updateAllowedAppsDisplay();
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        // Still show default allowed apps
                        updateAllowedAppsDisplay();
                    }
                });
    }

    private void updateAllowedAppsDisplay() {
        // Filter to only show installed apps
        List<AppInfo> installedAllowedApps = new ArrayList<>();
        PackageManager pm = getPackageManager();

        for (String packageName : allowedApps) {
            try {
                ApplicationInfo appInfo = pm.getApplicationInfo(packageName, 0);
                String appName = pm.getApplicationLabel(appInfo).toString();
                Drawable icon = pm.getApplicationIcon(appInfo);
                installedAllowedApps.add(new AppInfo(packageName, appName, icon));
            } catch (PackageManager.NameNotFoundException e) {
                // App not installed, skip
            }
        }

        if (installedAllowedApps.isEmpty()) {
            recyclerAllowedApps.setVisibility(View.GONE);
            tvNoAllowedApps.setVisibility(View.VISIBLE);
        } else {
            recyclerAllowedApps.setVisibility(View.VISIBLE);
            tvNoAllowedApps.setVisibility(View.GONE);
            recyclerAllowedApps.setAdapter(new AllowedAppGridAdapter(installedAllowedApps));
        }
    }

    private void requestMoreTime() {
        String currentUserId = FirebaseHelper.getCurrentUserId();
        if (currentUserId == null) {
            Toast.makeText(this, "User not found", Toast.LENGTH_SHORT).show();
            return;
        }

        // Get parent ID and send request
        FirebaseHelper.getUserRef(currentUserId).addListenerForSingleValueEvent(
                new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        if (snapshot.exists()) {
                            String parentId = snapshot.child("parentId").getValue(String.class);
                            String childName = snapshot.child("name").getValue(String.class);

                            if (parentId == null || parentId.isEmpty()) {
                                Toast.makeText(ScreenTimeLockActivity.this,
                                        "Not linked to a parent", Toast.LENGTH_SHORT).show();
                                return;
                            }

                            showTimeRequestDialog(parentId, childName);
                        }
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        Toast.makeText(ScreenTimeLockActivity.this,
                                "Error: " + error.getMessage(), Toast.LENGTH_SHORT).show();
                    }
                }
        );
    }

    private void showTimeRequestDialog(String parentId, String childName) {
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_request_time, null);

        com.google.android.material.dialog.MaterialAlertDialogBuilder builder =
                new com.google.android.material.dialog.MaterialAlertDialogBuilder(this, 
                        R.style.ThemeOverlay_App_MaterialAlertDialog);
        builder.setView(dialogView);
        builder.setBackground(getResources().getDrawable(R.drawable.dialog_background, getTheme()));

        androidx.appcompat.app.AlertDialog dialog = builder.create();

        dialogView.findViewById(R.id.option_15_min).setOnClickListener(v -> {
            sendTimeRequest(parentId, childName, 15);
            dialog.dismiss();
        });

        dialogView.findViewById(R.id.option_30_min).setOnClickListener(v -> {
            sendTimeRequest(parentId, childName, 30);
            dialog.dismiss();
        });

        dialogView.findViewById(R.id.option_1_hour).setOnClickListener(v -> {
            sendTimeRequest(parentId, childName, 60);
            dialog.dismiss();
        });

        dialogView.findViewById(R.id.option_2_hours).setOnClickListener(v -> {
            sendTimeRequest(parentId, childName, 120);
            dialog.dismiss();
        });

        dialogView.findViewById(R.id.btn_cancel).setOnClickListener(v -> dialog.dismiss());

        dialog.show();
    }

    private void sendTimeRequest(String parentId, String childName, int minutes) {
        String currentUserId = FirebaseHelper.getCurrentUserId();

        String alertId = FirebaseHelper.getDatabase().getReference()
                .child("alerts").push().getKey();

        if (alertId == null) return;

        com.safezone.app.models.Alert alert = new com.safezone.app.models.Alert(
                alertId,
                parentId,
                currentUserId,
                childName,
                com.safezone.app.models.Alert.Type.REQUEST,
                childName + " is requesting more screen time",
                "Requested: " + minutes + " minutes (Screen time limit reached)",
                System.currentTimeMillis()
        );

        FirebaseHelper.getDatabase().getReference()
                .child("alerts")
                .child(alertId)
                .setValue(alert)
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(this, "Request sent to parent!", Toast.LENGTH_SHORT).show();
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Failed to send request", Toast.LENGTH_SHORT).show();
                });
    }

    private void openApp(String packageName) {
        try {
            Intent launchIntent = getPackageManager().getLaunchIntentForPackage(packageName);
            if (launchIntent != null) {
                startActivity(launchIntent);
            }
        } catch (Exception e) {
            Toast.makeText(this, "Cannot open app", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onBackPressed() {
        // Go to home screen instead of back
        Intent homeIntent = new Intent(Intent.ACTION_MAIN);
        homeIntent.addCategory(Intent.CATEGORY_HOME);
        homeIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(homeIntent);
    }

    // Inner class for app info
    private static class AppInfo {
        String packageName;
        String appName;
        Drawable icon;

        AppInfo(String packageName, String appName, Drawable icon) {
            this.packageName = packageName;
            this.appName = appName;
            this.icon = icon;
        }
    }

    // Inner adapter for allowed apps grid
    private class AllowedAppGridAdapter extends RecyclerView.Adapter<AllowedAppGridAdapter.ViewHolder> {
        private final List<AppInfo> apps;

        AllowedAppGridAdapter(List<AppInfo> apps) {
            this.apps = apps;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_allowed_app_grid, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            AppInfo app = apps.get(position);
            holder.imgIcon.setImageDrawable(app.icon);
            holder.tvName.setText(app.appName);
            holder.itemView.setOnClickListener(v -> openApp(app.packageName));
        }

        @Override
        public int getItemCount() {
            return apps.size();
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            ImageView imgIcon;
            TextView tvName;

            ViewHolder(@NonNull View itemView) {
                super(itemView);
                imgIcon = itemView.findViewById(R.id.img_app_icon);
                tvName = itemView.findViewById(R.id.tv_app_name);
            }
        }
    }
}
