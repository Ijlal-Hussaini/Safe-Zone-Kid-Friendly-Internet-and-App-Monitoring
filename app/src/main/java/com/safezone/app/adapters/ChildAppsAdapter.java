package com.safezone.app.adapters;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.card.MaterialCardView;
import com.safezone.app.R;

import java.util.ArrayList;
import java.util.List;

/**
 * Adapter for displaying child's installed apps fetched from Firebase
 * Used in both ScreenTimeSettingsActivity and ContentFilterActivity
 */
public class ChildAppsAdapter extends RecyclerView.Adapter<ChildAppsAdapter.ViewHolder> {

    private Context context;
    private List<AppInfo> allApps;
    private List<AppInfo> filteredApps;
    private List<String> excludedApps; // Apps already added (allowed or blocked)
    private OnAppSelectedListener listener;

    /**
     * Simple class to hold app info
     */
    public static class AppInfo {
        public String packageName;
        public String appName;

        public AppInfo(String packageName, String appName) {
            this.packageName = packageName;
            this.appName = appName;
        }
    }

    public interface OnAppSelectedListener {
        void onAppSelected(String packageName, String appName);
    }

    public <T> ChildAppsAdapter(Context context, List<T> apps, 
                           List<String> excludedApps, OnAppSelectedListener listener) {
        this.context = context;
        this.allApps = new ArrayList<>();
        this.filteredApps = new ArrayList<>();
        this.excludedApps = excludedApps != null ? excludedApps : new ArrayList<>();
        this.listener = listener;
        
        // Convert input list to AppInfo list
        for (T app : apps) {
            if (app instanceof AppInfo) {
                this.allApps.add((AppInfo) app);
            } else {
                // Handle objects with packageName and appName fields via reflection or duck typing
                try {
                    String packageName = (String) app.getClass().getField("packageName").get(app);
                    String appName = (String) app.getClass().getField("appName").get(app);
                    this.allApps.add(new AppInfo(packageName, appName));
                } catch (Exception e) {
                    // Skip invalid items
                }
            }
        }
        this.filteredApps.addAll(this.allApps);
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_selectable_app, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        AppInfo app = filteredApps.get(position);
        
        holder.tvAppName.setText(app.appName);
        holder.tvPackageName.setText(app.packageName);
        
        // Try to get app icon from device (if installed on parent's device too)
        Drawable appIcon = getAppIcon(app.packageName);
        holder.imgAppIcon.setImageDrawable(appIcon);
        
        // Check if already added
        boolean isExcluded = excludedApps.contains(app.packageName);
        
        if (isExcluded) {
            holder.cardApp.setAlpha(0.5f);
            holder.tvStatus.setVisibility(View.VISIBLE);
            holder.tvStatus.setText("Already added");
        } else {
            holder.cardApp.setAlpha(1.0f);
            holder.tvStatus.setVisibility(View.GONE);
        }
        
        holder.cardApp.setOnClickListener(v -> {
            if (!isExcluded && listener != null) {
                listener.onAppSelected(app.packageName, app.appName);
            }
        });
    }

    @Override
    public int getItemCount() {
        return filteredApps.size();
    }

    /**
     * Filter apps by search query
     */
    public void filter(String query) {
        filteredApps.clear();
        
        if (query == null || query.isEmpty()) {
            filteredApps.addAll(allApps);
        } else {
            String lowerQuery = query.toLowerCase();
            for (AppInfo app : allApps) {
                if (app.appName.toLowerCase().contains(lowerQuery) ||
                    app.packageName.toLowerCase().contains(lowerQuery)) {
                    filteredApps.add(app);
                }
            }
        }
        
        notifyDataSetChanged();
    }

    /**
     * Try to get app icon - if not installed on parent's device, use default icon
     */
    private Drawable getAppIcon(String packageName) {
        try {
            return context.getPackageManager().getApplicationIcon(packageName);
        } catch (Exception e) {
            // App not installed on parent's device, use default icon
            Drawable defaultIcon = ContextCompat.getDrawable(context, R.drawable.ic_apps);
            if (defaultIcon != null) {
                defaultIcon = defaultIcon.mutate();
                defaultIcon.setTint(0xFF26C6DA);
            }
            return defaultIcon;
        }
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        MaterialCardView cardApp;
        ImageView imgAppIcon;
        TextView tvAppName;
        TextView tvPackageName;
        TextView tvStatus;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            cardApp = itemView.findViewById(R.id.card_app);
            imgAppIcon = itemView.findViewById(R.id.img_app_icon);
            tvAppName = itemView.findViewById(R.id.tv_app_name);
            tvPackageName = itemView.findViewById(R.id.tv_package_name);
            tvStatus = itemView.findViewById(R.id.tv_status);
        }
    }
}
