package com.safezone.app.adapters;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.safezone.app.R;

import java.util.List;

/**
 * Adapter for displaying allowed apps in Screen Time Settings
 * Uses grid layout to show multiple apps per row
 */
public class AllowedAppsAdapter extends RecyclerView.Adapter<AllowedAppsAdapter.ViewHolder> {

    private final Context context;
    private final List<String> allowedApps;
    private final OnAppRemoveListener removeListener;
    private final PackageManager packageManager;
    private final boolean showRemoveButton;

    public interface OnAppRemoveListener {
        void onRemove(String packageName);
    }

    public AllowedAppsAdapter(Context context, List<String> allowedApps, 
                              OnAppRemoveListener removeListener, boolean showRemoveButton) {
        this.context = context;
        this.allowedApps = allowedApps;
        this.removeListener = removeListener;
        this.packageManager = context.getPackageManager();
        this.showRemoveButton = showRemoveButton;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        // Use grid layout for allowed apps
        View view = LayoutInflater.from(context).inflate(R.layout.item_allowed_app_grid, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        String packageName = allowedApps.get(position);
        
        try {
            ApplicationInfo appInfo = packageManager.getApplicationInfo(packageName, 0);
            String appName = packageManager.getApplicationLabel(appInfo).toString();
            Drawable appIcon = packageManager.getApplicationIcon(appInfo);
            
            holder.tvAppName.setText(appName);
            holder.imgAppIcon.setImageDrawable(appIcon);
        } catch (PackageManager.NameNotFoundException e) {
            // App not installed on parent's device - try to get name from Firebase data
            // For now, show shortened package name
            String shortName = packageName;
            if (packageName.contains(".")) {
                String[] parts = packageName.split("\\.");
                shortName = parts[parts.length - 1];
                // Capitalize first letter
                shortName = shortName.substring(0, 1).toUpperCase() + shortName.substring(1);
            }
            holder.tvAppName.setText(shortName);
            holder.imgAppIcon.setImageResource(R.drawable.ic_apps);
        }

        // Handle long press to remove (for grid layout)
        if (showRemoveButton && removeListener != null) {
            holder.itemView.setOnLongClickListener(v -> {
                removeListener.onRemove(packageName);
                return true;
            });
            
            // Also handle click to remove
            holder.itemView.setOnClickListener(v -> {
                removeListener.onRemove(packageName);
            });
        }
    }

    @Override
    public int getItemCount() {
        return allowedApps.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        ImageView imgAppIcon;
        TextView tvAppName;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            imgAppIcon = itemView.findViewById(R.id.img_app_icon);
            tvAppName = itemView.findViewById(R.id.tv_app_name);
        }
    }
}
