package com.safezone.app.utils;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Color;

import com.github.mikephil.charting.charts.BarChart;
import com.github.mikephil.charting.charts.PieChart;
import com.github.mikephil.charting.components.Description;
import com.github.mikephil.charting.components.Legend;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.BarData;
import com.github.mikephil.charting.data.BarDataSet;
import com.github.mikephil.charting.data.BarEntry;
import com.github.mikephil.charting.data.PieData;
import com.github.mikephil.charting.data.PieDataSet;
import com.github.mikephil.charting.data.PieEntry;
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter;
import com.github.mikephil.charting.formatter.ValueFormatter;
import com.safezone.app.models.ActivityLog;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Helper class for creating charts using MPAndroidChart
 */
public class ChartHelper {

    // Chart colors matching app theme
    private static final int[] CHART_COLORS = {
            Color.parseColor("#4FC3F7"), // primary
            Color.parseColor("#4DB6AC"), // secondary
            Color.parseColor("#FF9800"), // warning
            Color.parseColor("#9C27B0"), // purple
            Color.parseColor("#F44336")  // error
    };

    /**
     * Check if dark mode is enabled
     */
    private static boolean isDarkMode(Context context) {
        int nightModeFlags = context.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
        return nightModeFlags == Configuration.UI_MODE_NIGHT_YES;
    }

    /**
     * Get text color based on theme
     */
    private static int getTextColor(Context context) {
        return isDarkMode(context) ? Color.WHITE : Color.parseColor("#212121");
    }

    /**
     * Get axis text color based on theme
     */
    private static int getAxisTextColor(Context context) {
        return isDarkMode(context) ? Color.parseColor("#B0B0B0") : Color.parseColor("#757575");
    }

    /**
     * Setup Bar Chart for daily usage
     */
    public static void setupBarChart(BarChart chart, List<ActivityLog> logs, int days) {
        Context context = chart.getContext();
        // Prepare data
        Map<String, Long> dailyUsage = calculateDailyUsage(logs, days);

        ArrayList<BarEntry> entries = new ArrayList<>();
        ArrayList<String> labels = new ArrayList<>();

        Calendar calendar = Calendar.getInstance();
        SimpleDateFormat dateFormat = new SimpleDateFormat("MMM dd", Locale.getDefault());

        int index = 0;
        for (int i = days - 1; i >= 0; i--) {
            calendar.setTimeInMillis(System.currentTimeMillis());
            calendar.add(Calendar.DAY_OF_YEAR, -i);

            String dateKey = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                    .format(calendar.getTime());
            String label = dateFormat.format(calendar.getTime());

            long minutes = dailyUsage.getOrDefault(dateKey, 0L);
            entries.add(new BarEntry(index, minutes));
            labels.add(label);
            index++;
        }

        // Create dataset
        BarDataSet dataSet = new BarDataSet(entries, "Screen Time (minutes)");
        dataSet.setColor(CHART_COLORS[0]);
        dataSet.setValueTextSize(10f);
        dataSet.setValueTextColor(getTextColor(context));

        // Format values to show as integers
        dataSet.setValueFormatter(new ValueFormatter() {
            @Override
            public String getFormattedValue(float value) {
                return String.valueOf((int) value) + "m";
            }
        });

        BarData barData = new BarData(dataSet);
        barData.setBarWidth(0.8f);

        // Configure chart
        chart.setData(barData);
        chart.setFitBars(true);
        chart.animateY(1000);

        // Description
        Description desc = new Description();
        desc.setText("");
        chart.setDescription(desc);

        // X-Axis
        XAxis xAxis = chart.getXAxis();
        xAxis.setValueFormatter(new IndexAxisValueFormatter(labels));
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
        xAxis.setGranularity(1f);
        xAxis.setGranularityEnabled(true);
        xAxis.setLabelCount(labels.size());
        xAxis.setTextSize(10f);
        xAxis.setTextColor(getAxisTextColor(context));

        // Y-Axis
        YAxis leftAxis = chart.getAxisLeft();
        leftAxis.setAxisMinimum(0f);
        leftAxis.setTextColor(getAxisTextColor(context));
        chart.getAxisRight().setEnabled(false);

        // Legend
        Legend legend = chart.getLegend();
        legend.setEnabled(true);
        legend.setTextSize(12f);
        legend.setTextColor(getTextColor(context));

        chart.invalidate();
    }

    /**
     * Setup Pie Chart for top apps
     * Only shows apps with at least 2 minutes of usage
     */
    public static void setupPieChart(PieChart chart, List<ActivityLog> logs, int topN) {
        Context context = chart.getContext();
        // Calculate top apps
        Map<String, Long> appUsage = calculateAppUsage(logs);

        // Sort and get top N
        List<Map.Entry<String, Long>> sortedApps = new ArrayList<>(appUsage.entrySet());
        sortedApps.sort((a, b) -> b.getValue().compareTo(a.getValue()));

        ArrayList<PieEntry> entries = new ArrayList<>();
        int colorIndex = 0;
        long totalOthers = 0;
        int addedApps = 0;
        
        // Minimum usage threshold: 2 minutes
        final long MIN_USAGE_MINUTES = 2;

        for (int i = 0; i < sortedApps.size(); i++) {
            Map.Entry<String, Long> entry = sortedApps.get(i);
            float minutes = entry.getValue();
            
            // Only include apps with at least 2 minutes of usage
            if (minutes >= MIN_USAGE_MINUTES) {
                if (addedApps < topN) {
                    entries.add(new PieEntry(minutes, entry.getKey()));
                    addedApps++;
                } else {
                    totalOthers += (long) minutes;
                }
            } else {
                // Apps with less than 2 minutes go to "Others"
                totalOthers += (long) minutes;
            }
        }

        // Add "Others" if needed (and if it has at least 2 minutes)
        if (totalOthers >= MIN_USAGE_MINUTES) {
            entries.add(new PieEntry(totalOthers, "Others"));
        }

        // Create dataset (empty label to avoid "App Usage" text in legend)
        PieDataSet dataSet = new PieDataSet(entries, "");
        dataSet.setColors(CHART_COLORS);
        dataSet.setValueTextSize(12f);  // Larger for better readability
        dataSet.setValueTextColor(Color.WHITE);
        dataSet.setHighlightEnabled(true);
        
        // Position values INSIDE slices
        dataSet.setYValuePosition(PieDataSet.ValuePosition.INSIDE_SLICE);
        dataSet.setXValuePosition(PieDataSet.ValuePosition.INSIDE_SLICE);

        // Format values - show time inside segments
        dataSet.setValueFormatter(new ValueFormatter() {
            @Override
            public String getFormattedValue(float value) {
                // Only show values for segments with significant time
                if (value < 1) return "";  // Hide very small values
                return formatMinutes((long) value);
            }
        });

        PieData pieData = new PieData(dataSet);

        // Configure chart
        chart.setData(pieData);
        chart.animateY(1000);

        // Description
        Description desc = new Description();
        desc.setText("");
        chart.setDescription(desc);

        // Center text (donut hole) - "Top Apps"
        chart.setDrawHoleEnabled(true);
        chart.setHoleRadius(40f);
        chart.setTransparentCircleRadius(45f);
        chart.setHoleColor(Color.WHITE);  // Ensure white background in center
        chart.setCenterText("Top Apps");
        chart.setCenterTextSize(18f);  // Larger for better visibility
        chart.setCenterTextColor(Color.parseColor("#212121"));  // Dark gray - visible on white background
        chart.setCenterTextRadiusPercent(100f);  // Center the text properly

        // Disable entry labels on slices (only show time values)
        chart.setDrawEntryLabels(false);
        
        // Rotation settings
        chart.setRotationEnabled(true);
        chart.setRotationAngle(0);
        chart.setHighlightPerTapEnabled(true);
        
        // Minimal extra space since labels are inside
        chart.setExtraOffsets(10, 10, 10, 10);
        
        // Don't use percentage values
        chart.setUsePercentValues(false);

        // Legend - horizontal with better spacing
        Legend legend = chart.getLegend();
        legend.setEnabled(true);
        legend.setTextSize(12f);  // Slightly larger for readability
        legend.setTextColor(getTextColor(context));
        legend.setOrientation(Legend.LegendOrientation.HORIZONTAL);
        legend.setVerticalAlignment(Legend.LegendVerticalAlignment.BOTTOM);
        legend.setHorizontalAlignment(Legend.LegendHorizontalAlignment.CENTER);
        legend.setWordWrapEnabled(true);
        legend.setMaxSizePercent(0.95f);
        legend.setDrawInside(false);
        legend.setXEntrySpace(12f);  // More horizontal spacing
        legend.setYEntrySpace(6f);   // More vertical spacing
        legend.setFormSize(12f);     // Larger colored squares
        legend.setFormToTextSpace(6f);  // Space between square and text

        chart.invalidate();
    }

    /**
     * Maximum valid duration per log entry (6 minutes = 360000ms)
     * Service logs every 5 minutes, so any single entry > 6 min is invalid old data
     */
    private static final long MAX_VALID_DURATION_MS = 6 * 60 * 1000;
    
    /**
     * Check if a log entry has valid duration (not old cumulative data)
     */
    private static boolean isValidDuration(long durationMs) {
        return durationMs > 0 && durationMs <= MAX_VALID_DURATION_MS;
    }

    /**
     * Calculate daily usage from logs
     */
    private static Map<String, Long> calculateDailyUsage(List<ActivityLog> logs, int days) {
        Map<String, Long> dailyUsage = new HashMap<>();
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());

        long cutoffTime = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(days);

        for (ActivityLog log : logs) {
            if (log.getTimestamp() < cutoffTime) continue;
            
            // Skip invalid durations (old cumulative data)
            if (!isValidDuration(log.getDuration())) continue;

            String dateKey = dateFormat.format(log.getTimestamp());
            long minutes = TimeUnit.MILLISECONDS.toMinutes(log.getDuration());

            dailyUsage.put(dateKey, dailyUsage.getOrDefault(dateKey, 0L) + minutes);
        }

        return dailyUsage;
    }

    /**
     * Calculate app usage from logs
     */
    private static Map<String, Long> calculateAppUsage(List<ActivityLog> logs) {
        Map<String, Long> appUsage = new HashMap<>();

        for (ActivityLog log : logs) {
            // Skip invalid durations (old cumulative data)
            if (!isValidDuration(log.getDuration())) continue;
            
            String appName = log.getAppName();
            long minutes = TimeUnit.MILLISECONDS.toMinutes(log.getDuration());

            appUsage.put(appName, appUsage.getOrDefault(appName, 0L) + minutes);
        }

        return appUsage;
    }

    /**
     * Calculate total screen time (only valid entries)
     */
    public static long calculateTotalScreenTime(List<ActivityLog> logs) {
        long total = 0;
        for (ActivityLog log : logs) {
            // Skip invalid durations (old cumulative data)
            if (!isValidDuration(log.getDuration())) continue;
            total += log.getDuration();
        }
        return TimeUnit.MILLISECONDS.toMinutes(total);
    }

    /**
     * Format minutes to readable string
     */
    public static String formatMinutes(long minutes) {
        if (minutes < 60) {
            return minutes + "m";
        } else {
            long hours = minutes / 60;
            long mins = minutes % 60;
            return hours + "h " + mins + "m";
        }
    }

    /**
     * Get chart colors
     */
    public static int[] getChartColors() {
        return CHART_COLORS;
    }
}