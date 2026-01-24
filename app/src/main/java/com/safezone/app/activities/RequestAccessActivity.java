package com.safezone.app.activities;

import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.tabs.TabLayout;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.ValueEventListener;
import com.safezone.app.R;
import com.safezone.app.adapters.RequestAccessAdapter;
import com.safezone.app.utils.AlertHelper;
import com.safezone.app.utils.FirebaseHelper;
import com.safezone.app.utils.SharedPrefsHelper;

import java.util.ArrayList;
import java.util.List;

/**
 * Activity for child to request access to blocked apps/websites
 */
public class RequestAccessActivity extends AppCompatActivity {

    private MaterialToolbar toolbar;
    private TabLayout tabLayout;
    private RecyclerView recyclerContent;
    private LinearLayout emptyState;
    private ProgressBar progressBar;

    private DatabaseReference contentRulesRef;
    private String childUid;
    private String childName;
    private String parentId;
    private int currentTab = 0; // 0 = Apps, 1 = Websites
    private List<String> blockedApps = new ArrayList<>();
    private List<String> blockedWebsites = new ArrayList<>();
    private RequestAccessAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_request_access);

        SharedPrefsHelper prefs = new SharedPrefsHelper(this);
        childUid = prefs.getUserId();
        childName = prefs.getUserName();

        if (childUid == null) {
            Toast.makeText(this, "User not authenticated", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        initViews();
        setupToolbar();
        setupTabs();
        loadParentId();
    }

    private void initViews() {
        toolbar = findViewById(R.id.toolbar);
        tabLayout = findViewById(R.id.tab_layout);
        recyclerContent = findViewById(R.id.recycler_content);
        emptyState = findViewById(R.id.empty_state);
        progressBar = findViewById(R.id.progress_bar);

        contentRulesRef = FirebaseHelper.getUsersRef().child(childUid).child("contentRules");

        recyclerContent.setLayoutManager(new LinearLayoutManager(this));
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

    private void loadParentId() {
        FirebaseHelper.getUserRef(childUid).child("parentId")
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        parentId = snapshot.getValue(String.class);
                        if (parentId != null) {
                            loadBlockedContent();
                        } else {
                            Toast.makeText(RequestAccessActivity.this,
                                    "Not linked to parent", Toast.LENGTH_SHORT).show();
                            finish();
                        }
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        Toast.makeText(RequestAccessActivity.this,
                                "Error loading data", Toast.LENGTH_SHORT).show();
                        finish();
                    }
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
                Toast.makeText(RequestAccessActivity.this,
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
            recyclerContent.setVisibility(View.VISIBLE);

            adapter = new RequestAccessAdapter(
                    this,
                    currentList,
                    currentTab == 0,
                    this::requestAccess
            );
            recyclerContent.setAdapter(adapter);
        }
    }

    private void requestAccess(String content) {
        String type = currentTab == 0 ? "app" : "website";
        String contentName = content;

        // Get app name if it's an app
        if (currentTab == 0) {
            try {
                android.content.pm.PackageManager pm = getPackageManager();
                android.content.pm.ApplicationInfo appInfo = pm.getApplicationInfo(content, 0);
                contentName = pm.getApplicationLabel(appInfo).toString();
            } catch (android.content.pm.PackageManager.NameNotFoundException e) {
                contentName = content;
            }
        }

        final String finalContentName = contentName;

        new com.google.android.material.dialog.MaterialAlertDialogBuilder(this, R.style.ThemeOverlay_App_MaterialAlertDialog)
                .setTitle("Request Access")
                .setMessage("Request access to " + finalContentName + "?")
                .setPositiveButton("Request", (dialog, which) -> {
                    sendAccessRequest(type, finalContentName, content);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void sendAccessRequest(String type, String contentName, String content) {
        if (parentId == null) {
            Toast.makeText(this, "Parent not found", Toast.LENGTH_SHORT).show();
            return;
        }

        String message = childName + " requested access to " + type;
        String details = (type.equals("app") ? "App: " : "Website: ") + contentName;

        AlertHelper.sendCustomAlert(
                parentId,
                childUid,
                childName,
                "ACCESS_REQUEST",
                message,
                details
        );

        Toast.makeText(this, "Access request sent to parent", Toast.LENGTH_LONG).show();
    }

    private void showEmptyState() {
        recyclerContent.setVisibility(View.GONE);
        emptyState.setVisibility(View.VISIBLE);
    }

    private void showLoading(boolean show) {
        progressBar.setVisibility(show ? View.VISIBLE : View.GONE);
    }
}
