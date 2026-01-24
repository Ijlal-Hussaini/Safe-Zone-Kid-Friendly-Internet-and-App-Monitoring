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
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.Query;
import com.google.firebase.database.ValueEventListener;
import com.safezone.app.R;
import com.safezone.app.adapters.AlertsAdapter;
import com.safezone.app.models.Alert;
import com.safezone.app.utils.FirebaseHelper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Activity to display alerts for parent
 */
public class AlertsActivity extends AppCompatActivity {

    private MaterialToolbar toolbar;
    private RecyclerView recyclerAlerts;
    private LinearLayout emptyState;
    private ProgressBar progressBar;

    private DatabaseReference alertsRef;
    private String childUid;
    private String parentUid;
    private AlertsAdapter adapter;
    private ValueEventListener alertsListener;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_alerts);

        // Get child UID from intent (optional - can show all alerts or filtered)
        childUid = getIntent().getStringExtra("childUid");
        parentUid = FirebaseHelper.getCurrentUserId();

        if (parentUid == null) {
            Toast.makeText(this, "User not authenticated", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        initViews();
        setupToolbar();
        setupRecyclerView();
        loadAlerts();
    }

    private void initViews() {
        toolbar = findViewById(R.id.toolbar);
        recyclerAlerts = findViewById(R.id.recycler_alerts);
        emptyState = findViewById(R.id.empty_state);
        progressBar = findViewById(R.id.progress_bar);

        alertsRef = FirebaseHelper.getAlertsRef();

        // Setup delete all button
        android.widget.ImageButton btnDeleteAll = findViewById(R.id.btn_delete_all);
        if (btnDeleteAll != null) {
            btnDeleteAll.setOnClickListener(v -> deleteAllAlerts());
        }
    }

    private void setupToolbar() {
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayShowTitleEnabled(false);
        }
        toolbar.setNavigationOnClickListener(v -> finish());
        
        // Add delete all menu item
        toolbar.inflateMenu(R.menu.menu_alerts);
        toolbar.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == R.id.action_delete_all) {
                deleteAllAlerts();
                return true;
            }
            return false;
        });
    }

    private void setupRecyclerView() {
        recyclerAlerts.setLayoutManager(new LinearLayoutManager(this));
        adapter = new AlertsAdapter(this, new ArrayList<>(), this::markAlertAsRead, this::deleteAlert);
        recyclerAlerts.setAdapter(adapter);
    }

    private void loadAlerts() {
        showLoading(true);

        // Query alerts for current parent
        Query query = alertsRef.orderByChild("parentId").equalTo(parentUid);

        alertsListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                List<Alert> alerts = new ArrayList<>();

                if (snapshot.exists()) {
                    for (DataSnapshot alertSnapshot : snapshot.getChildren()) {
                        Alert alert = alertSnapshot.getValue(Alert.class);
                        if (alert != null) {
                            // Filter by child if specified
                            if (childUid == null || childUid.equals(alert.getChildId())) {
                                alerts.add(alert);
                            }
                        }
                    }
                }

                // Sort by timestamp (newest first)
                Collections.sort(alerts, (a1, a2) ->
                        Long.compare(a2.getTimestamp(), a1.getTimestamp()));

                showLoading(false);
                displayAlerts(alerts);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                showLoading(false);
                Toast.makeText(AlertsActivity.this,
                        "Error: " + error.getMessage(), Toast.LENGTH_SHORT).show();
            }
        };

        query.addValueEventListener(alertsListener);
    }

    private void displayAlerts(List<Alert> alerts) {
        if (alerts.isEmpty()) {
            showEmptyState();
        } else {
            emptyState.setVisibility(View.GONE);
            recyclerAlerts.setVisibility(View.VISIBLE);
            adapter.updateAlerts(alerts);
        }
    }

    private void markAlertAsRead(Alert alert) {
        if (alert.getAlertId() != null && !alert.isRead()) {
            alertsRef.child(alert.getAlertId()).child("read").setValue(true)
                    .addOnFailureListener(e -> {
                        Toast.makeText(this, "Failed to update alert", Toast.LENGTH_SHORT).show();
                    });
        }
    }

    private void deleteAlert(Alert alert) {
        if (alert.getAlertId() != null) {
            new com.google.android.material.dialog.MaterialAlertDialogBuilder(this, R.style.ThemeOverlay_App_MaterialAlertDialog)
                    .setTitle("Delete Alert")
                    .setMessage("Are you sure you want to delete this alert?")
                    .setPositiveButton("Delete", (dialog, which) -> {
                        alertsRef.child(alert.getAlertId()).removeValue()
                                .addOnSuccessListener(aVoid -> {
                                    Toast.makeText(this, "Alert deleted", Toast.LENGTH_SHORT).show();
                                })
                                .addOnFailureListener(e -> {
                                    Toast.makeText(this, "Failed to delete alert", Toast.LENGTH_SHORT).show();
                                });
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
        }
    }

    /**
     * Delete all alerts for current parent
     */
    private void deleteAllAlerts() {
        new com.google.android.material.dialog.MaterialAlertDialogBuilder(this, R.style.ThemeOverlay_App_MaterialAlertDialog)
                .setTitle("Delete All Alerts")
                .setMessage("Are you sure you want to delete all alerts? This action cannot be undone.")
                .setPositiveButton("Delete All", (dialog, which) -> {
                    showLoading(true);
                    
                    // Query all alerts for this parent
                    alertsRef.orderByChild("parentId").equalTo(parentUid)
                            .addListenerForSingleValueEvent(new ValueEventListener() {
                                @Override
                                public void onDataChange(@NonNull DataSnapshot snapshot) {
                                    if (snapshot.exists()) {
                                        // Delete each alert
                                        for (DataSnapshot alertSnapshot : snapshot.getChildren()) {
                                            alertSnapshot.getRef().removeValue();
                                        }
                                        Toast.makeText(AlertsActivity.this, 
                                                "All alerts deleted", Toast.LENGTH_SHORT).show();
                                    }
                                    showLoading(false);
                                }

                                @Override
                                public void onCancelled(@NonNull DatabaseError error) {
                                    showLoading(false);
                                    Toast.makeText(AlertsActivity.this, 
                                            "Failed to delete alerts", Toast.LENGTH_SHORT).show();
                                }
                            });
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showEmptyState() {
        recyclerAlerts.setVisibility(View.GONE);
        emptyState.setVisibility(View.VISIBLE);
    }

    private void showLoading(boolean show) {
        progressBar.setVisibility(show ? View.VISIBLE : View.GONE);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (alertsRef != null && alertsListener != null) {
            alertsRef.removeEventListener(alertsListener);
        }
    }
}