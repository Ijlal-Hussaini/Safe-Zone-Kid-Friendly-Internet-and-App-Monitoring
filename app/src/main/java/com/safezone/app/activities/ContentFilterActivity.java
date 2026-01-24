package com.safezone.app.activities;

import android.app.AlertDialog;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.ValueEventListener;
import com.safezone.app.R;
import com.safezone.app.adapters.BlockedContentAdapter;
import com.safezone.app.adapters.ChildAppsAdapter;
import com.safezone.app.utils.FirebaseHelper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Content Filter Activity - Block apps and websites
 */
public class ContentFilterActivity extends AppCompatActivity {

    private MaterialToolbar toolbar;
    private TabLayout tabLayout;
    private RecyclerView recyclerBlockedContent;
    private FloatingActionButton fabAdd;
    private LinearLayout emptyState;
    private ProgressBar progressBar;

    private DatabaseReference contentRulesRef;
    private String childUid;
    private int currentTab = 0; // 0 = Apps, 1 = Websites
    private List<String> blockedApps = new ArrayList<>();
    private List<String> blockedWebsites = new ArrayList<>();
    private BlockedContentAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_content_filter);

        // Get child UID from intent
        childUid = getIntent().getStringExtra("childUid");
        if (childUid == null) {
            Toast.makeText(this, "Child data not found", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        initViews();
        setupToolbar();
        setupTabs();
        loadBlockedContent();
    }

    private void initViews() {
        toolbar = findViewById(R.id.toolbar);
        tabLayout = findViewById(R.id.tab_layout);
        recyclerBlockedContent = findViewById(R.id.recycler_blocked_content);
        fabAdd = findViewById(R.id.fab_add);
        emptyState = findViewById(R.id.empty_state);
        progressBar = findViewById(R.id.progress_bar);

        contentRulesRef = FirebaseHelper.getUsersRef().child(childUid).child("contentRules");

        recyclerBlockedContent.setLayoutManager(new LinearLayoutManager(this));

        fabAdd.setOnClickListener(v -> showAddDialog());
    }

    private void setupToolbar() {
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayShowTitleEnabled(false);
        }
        toolbar.setNavigationOnClickListener(v -> finish());
    }

    private void setupTabs() {
        tabLayout.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                currentTab = tab.getPosition();
                updateDisplay();
            }

            @Override
            public void onTabUnselected(TabLayout.Tab tab) {}

            @Override
            public void onTabReselected(TabLayout.Tab tab) {}
        });
    }

    private void loadBlockedContent() {
        showLoading(true);

        contentRulesRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                blockedApps.clear();
                blockedWebsites.clear();

                if (snapshot.exists()) {
                    // Load blocked apps
                    DataSnapshot appsSnapshot = snapshot.child("blockedApps");
                    if (appsSnapshot.exists()) {
                        for (DataSnapshot appSnapshot : appsSnapshot.getChildren()) {
                            String packageName = appSnapshot.getValue(String.class);
                            if (packageName != null) {
                                blockedApps.add(packageName);
                            }
                        }
                    }

                    // Load blocked websites
                    DataSnapshot websitesSnapshot = snapshot.child("blockedWebsites");
                    if (websitesSnapshot.exists()) {
                        for (DataSnapshot siteSnapshot : websitesSnapshot.getChildren()) {
                            String url = siteSnapshot.getValue(String.class);
                            if (url != null) {
                                blockedWebsites.add(url);
                            }
                        }
                    }
                }

                showLoading(false);
                updateDisplay();
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                showLoading(false);
                Toast.makeText(ContentFilterActivity.this,
                        "Error: " + error.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void updateDisplay() {
        List<String> currentList = currentTab == 0 ? blockedApps : blockedWebsites;

        if (currentList.isEmpty()) {
            showEmptyState();
        } else {
            emptyState.setVisibility(View.GONE);
            recyclerBlockedContent.setVisibility(View.VISIBLE);

            adapter = new BlockedContentAdapter(
                    this,
                    currentList,
                    currentTab == 0,
                    this::removeBlockedContent
            );
            recyclerBlockedContent.setAdapter(adapter);
        }
    }

    private void showAddDialog() {
        if (currentTab == 0) {
            // Apps tab - show child's installed apps dialog
            showBlockAppDialog();
        } else {
            // Websites tab - show text input dialog
            showBlockWebsiteDialog();
        }
    }

    /**
     * Show dialog to select app from child's installed apps
     */
    private void showBlockAppDialog() {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_select_app, null);
        
        RecyclerView recyclerApps = dialogView.findViewById(R.id.recycler_apps);
        TextInputEditText etSearch = dialogView.findViewById(R.id.et_search);
        ProgressBar dialogProgress = dialogView.findViewById(R.id.progress_bar);
        MaterialButton btnCancel = dialogView.findViewById(R.id.btn_cancel);

        // Update dialog title and subtitle for blocking
        android.widget.TextView tvTitle = dialogView.findViewById(R.id.tv_title);
        android.widget.TextView tvSubtitle = dialogView.findViewById(R.id.tv_subtitle);
        if (tvTitle != null) {
            tvTitle.setText("Block App");
        }
        if (tvSubtitle != null) {
            tvSubtitle.setText("Select an app from child's device to block");
        }

        recyclerApps.setLayoutManager(new LinearLayoutManager(this));

        com.google.android.material.dialog.MaterialAlertDialogBuilder builder = 
            new com.google.android.material.dialog.MaterialAlertDialogBuilder(this, R.style.ThemeOverlay_App_MaterialAlertDialog);
        builder.setView(dialogView);
        builder.setBackground(getResources().getDrawable(R.drawable.dialog_background, getTheme()));
        
        androidx.appcompat.app.AlertDialog dialog = builder.create();

        // Load child's installed apps from Firebase
        dialogProgress.setVisibility(View.VISIBLE);
        
        loadChildInstalledApps(new OnChildAppsLoadedListener() {
            @Override
            public void onAppsLoaded(List<ChildAppInfo> childApps) {
                dialogProgress.setVisibility(View.GONE);
                
                if (childApps.isEmpty()) {
                    Toast.makeText(ContentFilterActivity.this, 
                            "No apps found. Make sure child's device is connected.", 
                            Toast.LENGTH_LONG).show();
                    dialog.dismiss();
                    return;
                }
                
                ChildAppsAdapter adapter = new ChildAppsAdapter(
                        ContentFilterActivity.this, 
                        childApps, 
                        blockedApps,
                        (packageName, appName) -> {
                            addBlockedContent(packageName);
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
                Toast.makeText(ContentFilterActivity.this, 
                        "Error loading apps: " + error, 
                        Toast.LENGTH_SHORT).show();
                dialog.dismiss();
            }
        });

        btnCancel.setOnClickListener(v -> dialog.dismiss());
        
        dialog.show();
    }

    /**
     * Show dialog to enter website URL
     */
    private void showBlockWebsiteDialog() {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_add_blocked_content, null);

        // Get views
        com.google.android.material.textfield.TextInputLayout tilInput = dialogView.findViewById(R.id.til_input);
        com.google.android.material.textfield.TextInputEditText etInput = dialogView.findViewById(R.id.et_input);
        android.widget.TextView tvTitle = dialogView.findViewById(R.id.tv_title);
        android.widget.TextView tvSubtitle = dialogView.findViewById(R.id.tv_subtitle);
        android.widget.TextView tvInfo = dialogView.findViewById(R.id.tv_info);
        android.widget.ImageView imgIcon = dialogView.findViewById(R.id.img_icon);
        com.google.android.material.button.MaterialButton btnCancel = dialogView.findViewById(R.id.btn_cancel);
        com.google.android.material.button.MaterialButton btnAdd = dialogView.findViewById(R.id.btn_add);

        // Configure for websites
        tvTitle.setText("Block Website");
        tvSubtitle.setText("Enter the website URL to block");
        tilInput.setHint("Website URL");
        tilInput.setHelperText("e.g., facebook.com, youtube.com");
        tvInfo.setText("Tip: Enter the domain without http:// or www.");
        imgIcon.setImageResource(R.drawable.ic_web);
        btnAdd.setText("Block Site");
        btnAdd.setIconResource(R.drawable.ic_block);

        // Create dialog with custom view
        com.google.android.material.dialog.MaterialAlertDialogBuilder builder = 
            new com.google.android.material.dialog.MaterialAlertDialogBuilder(this, R.style.ThemeOverlay_App_MaterialAlertDialog);
        builder.setView(dialogView);
        builder.setBackground(getResources().getDrawable(R.drawable.dialog_background, getTheme()));
        
        androidx.appcompat.app.AlertDialog dialog = builder.create();

        // Set up button click listeners
        btnCancel.setOnClickListener(v -> dialog.dismiss());
        
        btnAdd.setOnClickListener(v -> {
            String input = etInput.getText().toString().trim();

            // Validation
            if (input.isEmpty()) {
                tilInput.setError("This field is required");
                return;
            }

            // Clear error and add content
            tilInput.setError(null);
            addBlockedContent(input);
            dialog.dismiss();
        });

        // Clear error when user starts typing
        etInput.addTextChangedListener(new android.text.TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                tilInput.setError(null);
            }

            @Override
            public void afterTextChanged(android.text.Editable s) {}
        });

        dialog.show();

        // Auto-focus and show keyboard after dialog is shown
        etInput.requestFocus();
        etInput.postDelayed(() -> {
            android.view.inputmethod.InputMethodManager imm = 
                (android.view.inputmethod.InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) {
                imm.showSoftInput(etInput, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT);
            }
        }, 200);
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

    private void addBlockedContent(String content) {
        showLoading(true);

        if (currentTab == 0) {
            // Add to blocked apps
            if (!blockedApps.contains(content)) {
                blockedApps.add(content);

                Map<String, Object> updates = new HashMap<>();
                updates.put("blockedApps", blockedApps);

                contentRulesRef.updateChildren(updates)
                        .addOnSuccessListener(aVoid -> {
                            showLoading(false);
                            Toast.makeText(this, "App blocked successfully", Toast.LENGTH_SHORT).show();
                            updateDisplay();
                        })
                        .addOnFailureListener(e -> {
                            showLoading(false);
                            Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                        });
            } else {
                showLoading(false);
                Toast.makeText(this, "App already blocked", Toast.LENGTH_SHORT).show();
            }
        } else {
            // Add to blocked websites
            if (!blockedWebsites.contains(content)) {
                blockedWebsites.add(content);

                Map<String, Object> updates = new HashMap<>();
                updates.put("blockedWebsites", blockedWebsites);

                contentRulesRef.updateChildren(updates)
                        .addOnSuccessListener(aVoid -> {
                            showLoading(false);
                            Toast.makeText(this, "Website blocked successfully", Toast.LENGTH_SHORT).show();
                            updateDisplay();
                        })
                        .addOnFailureListener(e -> {
                            showLoading(false);
                            Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                        });
            } else {
                showLoading(false);
                Toast.makeText(this, "Website already blocked", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void removeBlockedContent(String content) {
        new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                .setTitle("Remove Block")
                .setMessage("Are you sure you want to unblock this?")
                .setPositiveButton("Yes", (dialog, which) -> {
                    showLoading(true);

                    if (currentTab == 0) {
                        blockedApps.remove(content);

                        Map<String, Object> updates = new HashMap<>();
                        updates.put("blockedApps", blockedApps);

                        contentRulesRef.updateChildren(updates)
                                .addOnSuccessListener(aVoid -> {
                                    showLoading(false);
                                    Toast.makeText(this, "App unblocked", Toast.LENGTH_SHORT).show();
                                    updateDisplay();
                                })
                                .addOnFailureListener(e -> {
                                    showLoading(false);
                                    Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                                });
                    } else {
                        blockedWebsites.remove(content);

                        Map<String, Object> updates = new HashMap<>();
                        updates.put("blockedWebsites", blockedWebsites);

                        contentRulesRef.updateChildren(updates)
                                .addOnSuccessListener(aVoid -> {
                                    showLoading(false);
                                    Toast.makeText(this, "Website unblocked", Toast.LENGTH_SHORT).show();
                                    updateDisplay();
                                })
                                .addOnFailureListener(e -> {
                                    showLoading(false);
                                    Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                                });
                    }
                })
                .setNegativeButton("No", null)
                .show();
    }

    private void showEmptyState() {
        recyclerBlockedContent.setVisibility(View.GONE);
        emptyState.setVisibility(View.VISIBLE);
    }

    private void showLoading(boolean show) {
        progressBar.setVisibility(show ? View.VISIBLE : View.GONE);
    }
}