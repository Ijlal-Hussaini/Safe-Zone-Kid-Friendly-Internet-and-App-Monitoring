package com.safezone.app.adapters;

import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.safezone.app.R;
import com.safezone.app.models.ActivityLog;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Adapter for displaying activity logs in RecyclerView
 */
public class ActivityLogsAdapter extends RecyclerView.Adapter<ActivityLogsAdapter.LogViewHolder> {

    private Context context;
    private List<ActivityLog> logs;
    private PackageManager packageManager;

    public ActivityLogsAdapter(Context context, List<ActivityLog> logs) {
        this.context = context;
        this.logs = logs;
        this.packageManager = context.getPackageManager();
    }

    @NonNull
    @Override
    public LogViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_activity_log, parent, false);
        return new LogViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull LogViewHolder holder, int position) {
        ActivityLog log = logs.get(position);

        // Set app icon
        try {
            Drawable icon = packageManager.getApplicationIcon(log.getPackageName());
            holder.ivAppIcon.setImageDrawable(icon);
        } catch (PackageManager.NameNotFoundException e) {
            holder.ivAppIcon.setImageResource(R.drawable.ic_child_avatar);
        }

        // Set app name
        holder.tvAppName.setText(log.getAppName());

        // Set duration
        long durationMs = log.getDuration();
        String durationText = formatDuration(durationMs);
        holder.tvDuration.setText(durationText);

        // Set timestamp
        String timeText = formatTimestamp(log.getTimestamp());
        holder.tvTimestamp.setText(timeText);

        // Set package name (smaller text)
        holder.tvPackageName.setText(log.getPackageName());
    }

    @Override
    public int getItemCount() {
        return logs != null ? logs.size() : 0;
    }

    private String formatDuration(long durationMs) {
        long seconds = (durationMs / 1000) % 60;
        long minutes = (durationMs / (1000 * 60)) % 60;
        long hours = (durationMs / (1000 * 60 * 60));

        if (hours > 0) {
            return String.format(Locale.getDefault(), "%dh %dm", hours, minutes);
        } else if (minutes > 0) {
            return String.format(Locale.getDefault(), "%dm %ds", minutes, seconds);
        } else {
            return String.format(Locale.getDefault(), "%ds", seconds);
        }
    }

    private String formatTimestamp(long timestamp) {
        SimpleDateFormat sdf = new SimpleDateFormat("MMM dd, hh:mm a", Locale.getDefault());
        return sdf.format(new Date(timestamp));
    }

    public void updateLogs(List<ActivityLog> newLogs) {
        this.logs = newLogs;
        notifyDataSetChanged();
    }

    static class LogViewHolder extends RecyclerView.ViewHolder {
        ImageView ivAppIcon;
        TextView tvAppName;
        TextView tvDuration;
        TextView tvTimestamp;
        TextView tvPackageName;

        public LogViewHolder(@NonNull View itemView) {
            super(itemView);
            ivAppIcon = itemView.findViewById(R.id.iv_app_icon);
            tvAppName = itemView.findViewById(R.id.tv_app_name);
            tvDuration = itemView.findViewById(R.id.tv_duration);
            tvTimestamp = itemView.findViewById(R.id.tv_timestamp);
            tvPackageName = itemView.findViewById(R.id.tv_package_name);
        }
    }
}