package com.safezone.app.activities;

import android.app.ProgressDialog;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.github.mikephil.charting.charts.BarChart;
import com.github.mikephil.charting.charts.PieChart;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.card.MaterialCardView;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.ValueEventListener;
import com.safezone.app.R;
import com.safezone.app.models.ActivityLog;
import com.safezone.app.models.ChildUser;
import com.safezone.app.utils.ChartHelper;
import com.safezone.app.utils.FirebaseHelper;
import com.safezone.app.utils.PDFHelper;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Reports & Analytics Activity
 * Shows usage statistics with charts and PDF export
 */
public class ReportsActivity extends AppCompatActivity {

    private MaterialToolbar toolbar;
    private Spinner spinnerChild;
    private Spinner spinnerTimeRange;
    private MaterialCardView cardSummary;
    private TextView tvTotalScreenTime;
    private TextView tvTotalSessions;
    private TextView tvAverageSession;
    private TextView tvTopApp;
    private BarChart barChartDaily;
    private PieChart pieChartApps;
    private Button btnExportPDF;
    private Button btnShareReport;
    private LinearLayout layoutCharts;
    private LinearLayout layoutEmpty;

    private List<ChildUser> childrenList;
    private Map<String, String> childIdNameMap;
    private String selectedChildId;
    private int selectedTimeRange = 7; // days

    private List<ActivityLog> activityLogs;
    private ProgressDialog progressDialog;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_reports);

        initViews();
        setupToolbar();
        setupSpinners();
        loadChildren();
    }

    private void initViews() {
        toolbar = findViewById(R.id.toolbar);
        spinnerChild = findViewById(R.id.spinner_child);
        spinnerTimeRange = findViewById(R.id.spinner_time_range);
        cardSummary = findViewById(R.id.card_summary);
        tvTotalScreenTime = findViewById(R.id.tv_total_screen_time);
        tvTotalSessions = findViewById(R.id.tv_total_sessions);
        tvAverageSession = findViewById(R.id.tv_average_session);
        tvTopApp = findViewById(R.id.tv_top_app);
        barChartDaily = findViewById(R.id.bar_chart_daily);
        pieChartApps = findViewById(R.id.pie_chart_apps);
        btnExportPDF = findViewById(R.id.btn_export_pdf);
        btnShareReport = findViewById(R.id.btn_share_report);
        layoutCharts = findViewById(R.id.layout_charts);
        layoutEmpty = findViewById(R.id.layout_empty);

        childrenList = new ArrayList<>();
        childIdNameMap = new HashMap<>();
        activityLogs = new ArrayList<>();

        progressDialog = new ProgressDialog(this);
        progressDialog.setMessage("Loading reports...");
        progressDialog.setCancelable(false);
    }

    private void setupToolbar() {
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayShowTitleEnabled(false);
        }
        
        toolbar.setNavigationOnClickListener(v -> finish());
    }

    private void setupSpinners() {
        // Time range spinner
        String[] timeRanges = {"Last 7 Days", "Last 14 Days", "Last 30 Days"};
        ArrayAdapter<String> rangeAdapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_spinner_item,
                timeRanges
        );
        rangeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerTimeRange.setAdapter(rangeAdapter);

        spinnerTimeRange.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                switch (position) {
                    case 0: selectedTimeRange = 7; break;
                    case 1: selectedTimeRange = 14; break;
                    case 2: selectedTimeRange = 30; break;
                }
                if (selectedChildId != null) {
                    loadActivityLogs(selectedChildId);
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });

        // PDF export button
        btnExportPDF.setOnClickListener(v -> exportToPDF());
        btnShareReport.setOnClickListener(v -> shareReport());
    }

    private void loadChildren() {
        progressDialog.show();

        String parentId = FirebaseHelper.getCurrentUserId();
        DatabaseReference parentRef = FirebaseHelper.getUserRef(parentId);

        parentRef.child("children").addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                childrenList.clear();
                childIdNameMap.clear();

                if (!snapshot.exists()) {
                    progressDialog.dismiss();
                    showEmptyState();
                    Toast.makeText(ReportsActivity.this,
                            "No children found. Add a child first.",
                            Toast.LENGTH_LONG).show();
                    return;
                }

                // Load each child's data
                for (DataSnapshot childSnapshot : snapshot.getChildren()) {
                    String childId = childSnapshot.getKey();
                    if (childId != null) {
                        loadChildData(childId);
                    }
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                progressDialog.dismiss();
                Toast.makeText(ReportsActivity.this,
                        "Error loading children: " + error.getMessage(),
                        Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void loadChildData(String childId) {
        FirebaseHelper.getUserRef(childId).addListenerForSingleValueEvent(
                new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        try {
                            // Manually parse child data to avoid deserialization issues
                            ChildUser child = new ChildUser();
                            child.setUid(childId);
                            child.setName(snapshot.child("name").getValue(String.class));
                            child.setEmail(snapshot.child("email").getValue(String.class));
                            
                            if (child.getName() != null) {
                                childrenList.add(child);
                                childIdNameMap.put(childId, child.getName());

                                // Update spinner when all children loaded
                                updateChildSpinner();
                            }
                        } catch (Exception e) {
                            android.util.Log.e("ReportsActivity", "Error parsing child data: " + e.getMessage());
                        }
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        Toast.makeText(ReportsActivity.this,
                                "Error loading child data",
                                Toast.LENGTH_SHORT).show();
                    }
                }
        );
    }

    private void updateChildSpinner() {
        if (childrenList.isEmpty()) return;

        List<String> childNames = new ArrayList<>();
        for (ChildUser child : childrenList) {
            childNames.add(child.getName());
        }

        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_spinner_item,
                childNames
        );
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerChild.setAdapter(adapter);

        spinnerChild.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (position < childrenList.size()) {
                    selectedChildId = childrenList.get(position).getUid();
                    loadActivityLogs(selectedChildId);
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });

        // Auto-select first child
        if (!childrenList.isEmpty()) {
            selectedChildId = childrenList.get(0).getUid();
            loadActivityLogs(selectedChildId);
        }

        progressDialog.dismiss();
    }

    private void loadActivityLogs(String childId) {
        progressDialog.show();

        DatabaseReference logsRef = FirebaseHelper.getUserRef(childId).child("activityLogs");

        logsRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                activityLogs.clear();

                if (!snapshot.exists()) {
                    progressDialog.dismiss();
                    showEmptyState();
                    return;
                }

                // Filter logs by time range
                long cutoffTime = System.currentTimeMillis() -
                        (selectedTimeRange * 24L * 60L * 60L * 1000L);

                for (DataSnapshot logSnapshot : snapshot.getChildren()) {
                    ActivityLog log = logSnapshot.getValue(ActivityLog.class);
                    if (log != null && log.getTimestamp() >= cutoffTime) {
                        activityLogs.add(log);
                    }
                }

                progressDialog.dismiss();

                if (activityLogs.isEmpty()) {
                    showEmptyState();
                } else {
                    showCharts();
                    updateSummary();
                    updateCharts();
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                progressDialog.dismiss();
                Toast.makeText(ReportsActivity.this,
                        "Error loading logs: " + error.getMessage(),
                        Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void updateSummary() {
        long totalMinutes = ChartHelper.calculateTotalScreenTime(activityLogs);
        int totalSessions = activityLogs.size();
        long avgSessionMinutes = totalSessions > 0 ? totalMinutes / totalSessions : 0;

        tvTotalScreenTime.setText(ChartHelper.formatMinutes(totalMinutes));
        tvTotalSessions.setText(String.valueOf(totalSessions));
        tvAverageSession.setText(ChartHelper.formatMinutes(avgSessionMinutes));

        // Find top app
        Map<String, Long> appUsage = new HashMap<>();
        for (ActivityLog log : activityLogs) {
            long minutes = log.getDuration() / (60 * 1000);
            appUsage.put(log.getAppName(),
                    appUsage.getOrDefault(log.getAppName(), 0L) + minutes);
        }

        String topApp = "N/A";
        long maxUsage = 0;
        for (Map.Entry<String, Long> entry : appUsage.entrySet()) {
            if (entry.getValue() > maxUsage) {
                maxUsage = entry.getValue();
                topApp = entry.getKey();
            }
        }

        tvTopApp.setText(topApp);
    }

    private void updateCharts() {
        ChartHelper.setupBarChart(barChartDaily, activityLogs, selectedTimeRange);
        ChartHelper.setupPieChart(pieChartApps, activityLogs, 5);
    }

    private void showCharts() {
        layoutCharts.setVisibility(View.VISIBLE);
        layoutEmpty.setVisibility(View.GONE);
    }

    private void showEmptyState() {
        layoutCharts.setVisibility(View.GONE);
        layoutEmpty.setVisibility(View.VISIBLE);
    }

    private void exportToPDF() {
        if (selectedChildId == null || activityLogs.isEmpty()) {
            Toast.makeText(this, "No data to export", Toast.LENGTH_SHORT).show();
            return;
        }

        progressDialog.setMessage("Generating PDF...");
        progressDialog.show();

        new Thread(() -> {
            String childName = childIdNameMap.get(selectedChildId);
            String dateRange = getDateRangeString();

            File pdfFile = PDFHelper.generateUsageReport(
                    this,
                    childName,
                    activityLogs,
                    dateRange
            );

            runOnUiThread(() -> {
                progressDialog.dismiss();
                if (pdfFile != null) {
                    Toast.makeText(this,
                            "Report saved: " + pdfFile.getName(),
                            Toast.LENGTH_LONG).show();
                    PDFHelper.openPDF(this, pdfFile);
                } else {
                    Toast.makeText(this,
                            "Failed to generate report",
                            Toast.LENGTH_SHORT).show();
                }
            });
        }).start();
    }

    private void shareReport() {
        if (selectedChildId == null || activityLogs.isEmpty()) {
            Toast.makeText(this, "No data to share", Toast.LENGTH_SHORT).show();
            return;
        }

        progressDialog.setMessage("Preparing report...");
        progressDialog.show();

        new Thread(() -> {
            String childName = childIdNameMap.get(selectedChildId);
            String dateRange = getDateRangeString();

            File pdfFile = PDFHelper.generateUsageReport(
                    this,
                    childName,
                    activityLogs,
                    dateRange
            );

            runOnUiThread(() -> {
                progressDialog.dismiss();
                if (pdfFile != null) {
                    PDFHelper.sharePDF(this, pdfFile);
                } else {
                    Toast.makeText(this,
                            "Failed to generate report",
                            Toast.LENGTH_SHORT).show();
                }
            });
        }).start();
    }

    private String getDateRangeString() {
        Calendar endDate = Calendar.getInstance();
        Calendar startDate = Calendar.getInstance();
        startDate.add(Calendar.DAY_OF_YEAR, -selectedTimeRange);

        SimpleDateFormat format = new SimpleDateFormat("MMM dd, yyyy", Locale.getDefault());
        return format.format(startDate.getTime()) + " - " + format.format(endDate.getTime());
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