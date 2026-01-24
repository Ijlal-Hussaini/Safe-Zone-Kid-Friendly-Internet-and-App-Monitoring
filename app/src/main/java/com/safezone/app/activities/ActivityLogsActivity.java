package com.safezone.app.activities;

import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
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
import com.safezone.app.adapters.ActivityLogsAdapter;
import com.safezone.app.models.ActivityLog;
import com.safezone.app.utils.FirebaseHelper;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Activity Logs - View child's app usage history
 * UPDATED VERSION with RecyclerView adapter
 */
public class ActivityLogsActivity extends AppCompatActivity {

    private MaterialToolbar toolbar;
    private TabLayout tabLayout;
    private RecyclerView recyclerActivityLogs;
    private TextView tvTotalTime, tvAppsCount;
    private LinearLayout emptyState;
    private ProgressBar progressBar;

    private DatabaseReference logsRef;
    private String childUid;
    private int currentFilter = 0; // 0=Today, 1=Week, 2=All
    private ActivityLogsAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_activity_logs);

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
        loadActivityLogs();
    }

    private void initViews() {
        toolbar = findViewById(R.id.toolbar);
        tabLayout = findViewById(R.id.tab_layout);
        recyclerActivityLogs = findViewById(R.id.recycler_activity_logs);
        tvTotalTime = findViewById(R.id.tv_total_time);
        tvAppsCount = findViewById(R.id.tv_apps_count);
        emptyState = findViewById(R.id.empty_state);
        progressBar = findViewById(R.id.progress_bar);

        logsRef = FirebaseHelper.getUsersRef().child(childUid).child("activityLogs");

        recyclerActivityLogs.setLayoutManager(new LinearLayoutManager(this));
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
                currentFilter = tab.getPosition();
                loadActivityLogs();
            }

            @Override
            public void onTabUnselected(TabLayout.Tab tab) {}

            @Override
            public void onTabReselected(TabLayout.Tab tab) {}
        });
    }

    private void loadActivityLogs() {
        showLoading(true);

        logsRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                List<ActivityLog> logs = new ArrayList<>();

                if (snapshot.exists()) {
                    for (DataSnapshot logSnapshot : snapshot.getChildren()) {
                        ActivityLog log = logSnapshot.getValue(ActivityLog.class);
                        if (log != null && matchesFilter(log)) {
                            logs.add(log);
                        }
                    }
                }

                showLoading(false);
                displayLogs(logs);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                showLoading(false);
                Toast.makeText(ActivityLogsActivity.this,
                        "Error: " + error.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private boolean matchesFilter(ActivityLog log) {
        Calendar now = Calendar.getInstance();
        Calendar logTime = Calendar.getInstance();
        logTime.setTimeInMillis(log.getTimestamp());

        switch (currentFilter) {
            case 0: // Today
                return now.get(Calendar.YEAR) == logTime.get(Calendar.YEAR) &&
                        now.get(Calendar.DAY_OF_YEAR) == logTime.get(Calendar.DAY_OF_YEAR);

            case 1: // This Week
                now.add(Calendar.DAY_OF_YEAR, -7);
                return logTime.after(now);

            case 2: // All Time
            default:
                return true;
        }
    }

    private void displayLogs(List<ActivityLog> logs) {
        if (logs.isEmpty()) {
            showEmptyState();
        } else {
            emptyState.setVisibility(View.GONE);
            recyclerActivityLogs.setVisibility(View.VISIBLE);

            // Calculate stats
            long totalDuration = 0;
            Map<String, Boolean> uniqueApps = new HashMap<>();

            for (ActivityLog log : logs) {
                totalDuration += log.getDuration();
                uniqueApps.put(log.getPackageName(), true);
            }

            // Update summary
            updateSummary(totalDuration, uniqueApps.size());

            // Set adapter with logs
            if (adapter == null) {
                adapter = new ActivityLogsAdapter(this, logs);
                recyclerActivityLogs.setAdapter(adapter);
            } else {
                adapter.updateLogs(logs);
            }
        }
    }

    private void updateSummary(long totalDuration, int appsCount) {
        // Format total duration
        long hours = totalDuration / (1000 * 60 * 60);
        long minutes = (totalDuration / (1000 * 60)) % 60;

        String timeText = hours + "h " + minutes + "m";
        tvTotalTime.setText(timeText);
        tvAppsCount.setText(String.valueOf(appsCount));
    }

    private void showEmptyState() {
        recyclerActivityLogs.setVisibility(View.GONE);
        emptyState.setVisibility(View.VISIBLE);
        tvTotalTime.setText("0h 0m");
        tvAppsCount.setText("0");
    }

    private void showLoading(boolean show) {
        progressBar.setVisibility(show ? View.VISIBLE : View.GONE);
    }
}