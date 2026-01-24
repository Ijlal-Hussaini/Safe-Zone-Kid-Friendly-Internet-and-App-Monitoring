package com.safezone.app.utils;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Environment;
import android.util.Log;

import androidx.core.content.FileProvider;

import com.itextpdf.kernel.colors.Color;
import com.itextpdf.kernel.colors.DeviceRgb;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.Cell;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.element.Table;
import com.itextpdf.layout.properties.TextAlignment;
import com.itextpdf.layout.properties.UnitValue;
import com.safezone.app.models.ActivityLog;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Helper class for generating PDF reports
 */
public class PDFHelper {

    private static final String TAG = "PDFHelper";

    // App theme colors
    private static final Color PRIMARY_COLOR = new DeviceRgb(79, 195, 247);
    private static final Color SECONDARY_COLOR = new DeviceRgb(77, 182, 172);
    private static final Color TEXT_COLOR = new DeviceRgb(33, 33, 33);

    /**
     * Generate usage report PDF
     */
    public static File generateUsageReport(
            Context context,
            String childName,
            List<ActivityLog> logs,
            String dateRange
    ) {
        try {
            // Create file
            File pdfDir = new File(context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), "SafeZone");
            if (!pdfDir.exists()) {
                pdfDir.mkdirs();
            }

            String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
            String fileName = "SafeZone_Report_" + childName.replace(" ", "_") + "_" + timestamp + ".pdf";
            File pdfFile = new File(pdfDir, fileName);

            // Create PDF
            PdfWriter writer = new PdfWriter(pdfFile);
            PdfDocument pdf = new PdfDocument(writer);
            Document document = new Document(pdf);

            // Title
            Paragraph title = new Paragraph("Safe Zone Usage Report")
                    .setFontSize(24)
                    .setBold()
                    .setFontColor(PRIMARY_COLOR)
                    .setTextAlignment(TextAlignment.CENTER)
                    .setMarginBottom(10);
            document.add(title);

            // Child info
            Paragraph childInfo = new Paragraph("Child: " + childName)
                    .setFontSize(14)
                    .setFontColor(TEXT_COLOR)
                    .setTextAlignment(TextAlignment.CENTER);
            document.add(childInfo);

            Paragraph dateInfo = new Paragraph("Period: " + dateRange)
                    .setFontSize(12)
                    .setFontColor(TEXT_COLOR)
                    .setTextAlignment(TextAlignment.CENTER)
                    .setMarginBottom(20);
            document.add(dateInfo);

            // Summary statistics
            document.add(new Paragraph("Summary Statistics")
                    .setFontSize(16)
                    .setBold()
                    .setFontColor(PRIMARY_COLOR)
                    .setMarginTop(10));

            long totalMinutes = ChartHelper.calculateTotalScreenTime(logs);
            int totalSessions = logs.size();
            long avgSessionMinutes = totalSessions > 0 ? totalMinutes / totalSessions : 0;

            Table summaryTable = new Table(2);
            summaryTable.setWidth(UnitValue.createPercentValue(100));

            addSummaryRow(summaryTable, "Total Screen Time", ChartHelper.formatMinutes(totalMinutes));
            addSummaryRow(summaryTable, "Total Sessions", String.valueOf(totalSessions));
            addSummaryRow(summaryTable, "Average Session", ChartHelper.formatMinutes(avgSessionMinutes));
            addSummaryRow(summaryTable, "Report Generated", new SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault()).format(new Date()));

            document.add(summaryTable);

            // Top apps section
            document.add(new Paragraph("Top Applications")
                    .setFontSize(16)
                    .setBold()
                    .setFontColor(PRIMARY_COLOR)
                    .setMarginTop(20));

            Map<String, Long> appUsage = calculateAppUsage(logs);
            List<Map.Entry<String, Long>> sortedApps = new java.util.ArrayList<>(appUsage.entrySet());
            sortedApps.sort((a, b) -> b.getValue().compareTo(a.getValue()));

            Table appsTable = new Table(new float[]{3, 1});
            appsTable.setWidth(UnitValue.createPercentValue(100));

            // Header
            appsTable.addHeaderCell(new Cell().add(new Paragraph("Application").setBold()));
            appsTable.addHeaderCell(new Cell().add(new Paragraph("Usage").setBold()));

            // Data rows (top 10)
            for (int i = 0; i < Math.min(10, sortedApps.size()); i++) {
                Map.Entry<String, Long> entry = sortedApps.get(i);
                appsTable.addCell(entry.getKey());
                appsTable.addCell(ChartHelper.formatMinutes(entry.getValue()));
            }

            document.add(appsTable);

            // Footer
            Paragraph footer = new Paragraph("Generated by Safe Zone - Kid-Friendly Internet Monitoring")
                    .setFontSize(10)
                    .setFontColor(TEXT_COLOR)
                    .setTextAlignment(TextAlignment.CENTER)
                    .setMarginTop(30);
            document.add(footer);

            // Close document
            document.close();

            Log.d(TAG, "PDF generated successfully: " + pdfFile.getAbsolutePath());
            return pdfFile;

        } catch (Exception e) {
            Log.e(TAG, "Error generating PDF", e);
            return null;
        }
    }

    /**
     * Add summary row to table
     */
    private static void addSummaryRow(Table table, String label, String value) {
        table.addCell(new Cell().add(new Paragraph(label).setBold()));
        table.addCell(new Cell().add(new Paragraph(value)));
    }

    /**
     * Calculate app usage
     */
    private static Map<String, Long> calculateAppUsage(List<ActivityLog> logs) {
        Map<String, Long> appUsage = new HashMap<>();

        for (ActivityLog log : logs) {
            String appName = log.getAppName();
            long minutes = TimeUnit.MILLISECONDS.toMinutes(log.getDuration());
            appUsage.put(appName, appUsage.getOrDefault(appName, 0L) + minutes);
        }

        return appUsage;
    }

    /**
     * Share PDF file
     */
    public static void sharePDF(Context context, File pdfFile) {
        if (pdfFile == null || !pdfFile.exists()) {
            Log.e(TAG, "PDF file not found");
            return;
        }

        try {
            Uri pdfUri = FileProvider.getUriForFile(
                    context,
                    context.getPackageName() + ".fileprovider",
                    pdfFile
            );

            Intent shareIntent = new Intent(Intent.ACTION_SEND);
            shareIntent.setType("application/pdf");
            shareIntent.putExtra(Intent.EXTRA_STREAM, pdfUri);
            shareIntent.putExtra(Intent.EXTRA_SUBJECT, "Safe Zone Usage Report");
            shareIntent.putExtra(Intent.EXTRA_TEXT, "Usage report generated by Safe Zone app.");
            shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

            context.startActivity(Intent.createChooser(shareIntent, "Share Report"));
        } catch (Exception e) {
            Log.e(TAG, "Error sharing PDF", e);
        }
    }

    /**
     * Open PDF file
     */
    public static void openPDF(Context context, File pdfFile) {
        if (pdfFile == null || !pdfFile.exists()) {
            Log.e(TAG, "PDF file not found");
            return;
        }

        try {
            Uri pdfUri = FileProvider.getUriForFile(
                    context,
                    context.getPackageName() + ".fileprovider",
                    pdfFile
            );

            Intent viewIntent = new Intent(Intent.ACTION_VIEW);
            viewIntent.setDataAndType(pdfUri, "application/pdf");
            viewIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            viewIntent.addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY);

            context.startActivity(Intent.createChooser(viewIntent, "Open Report"));
        } catch (Exception e) {
            Log.e(TAG, "Error opening PDF", e);
        }
    }
}