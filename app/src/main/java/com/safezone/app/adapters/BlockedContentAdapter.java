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

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.DataSource;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.load.engine.GlideException;
import com.bumptech.glide.request.RequestListener;
import com.bumptech.glide.request.target.Target;
import com.safezone.app.R;

import java.util.List;

/**
 * Adapter for displaying blocked apps and websites
 */
public class BlockedContentAdapter extends RecyclerView.Adapter<BlockedContentAdapter.BlockedViewHolder> {

    public interface OnRemoveClickListener {
        void onRemoveClick(String content);
    }

    private Context context;
    private List<String> blockedItems;
    private boolean isAppList; // true for apps, false for websites
    private OnRemoveClickListener removeListener;
    private PackageManager packageManager;

    public BlockedContentAdapter(Context context, List<String> blockedItems,
                                 boolean isAppList, OnRemoveClickListener removeListener) {
        this.context = context;
        this.blockedItems = blockedItems;
        this.isAppList = isAppList;
        this.removeListener = removeListener;
        this.packageManager = context.getPackageManager();
    }

    @NonNull
    @Override
    public BlockedViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_blocked_content, parent, false);
        return new BlockedViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull BlockedViewHolder holder, int position) {
        String content = blockedItems.get(position);

        if (isAppList) {
            // Display app info
            try {
                ApplicationInfo appInfo = packageManager.getApplicationInfo(content, 0);
                String appName = packageManager.getApplicationLabel(appInfo).toString();
                Drawable icon = packageManager.getApplicationIcon(content);

                holder.ivIcon.setImageDrawable(icon);
                holder.ivIcon.setScaleType(ImageView.ScaleType.FIT_CENTER);
                holder.tvTitle.setText(appName);
                holder.tvSubtitle.setText("Blocked App");
            } catch (PackageManager.NameNotFoundException e) {
                // App not installed
                holder.ivIcon.setImageResource(R.drawable.ic_app_placeholder);
                holder.ivIcon.setScaleType(ImageView.ScaleType.FIT_CENTER);
                holder.tvTitle.setText(content);
                holder.tvSubtitle.setText("Blocked App (Not Installed)");
            }
        } else {
            // Display website info with favicon
            holder.tvTitle.setText(content);
            holder.tvSubtitle.setText("Blocked Website");
            
            // Load website favicon using Google's favicon service
            String faviconUrl = getFaviconUrl(content);
            
            // Log for debugging
            android.util.Log.d("BlockedContentAdapter", "Loading favicon for: " + content + " from: " + faviconUrl);
            
            try {
                // Set scale type first
                holder.ivIcon.setScaleType(ImageView.ScaleType.FIT_CENTER);
                
                Glide.with(holder.itemView.getContext().getApplicationContext())
                        .load(faviconUrl)
                        .diskCacheStrategy(DiskCacheStrategy.ALL)
                        .placeholder(R.drawable.ic_web)
                        .error(R.drawable.ic_web)
                        .override(64, 64)  // Resize to 64x64
                        .timeout(10000)  // 10 second timeout
                        .listener(new RequestListener<Drawable>() {
                            @Override
                            public boolean onLoadFailed(@androidx.annotation.Nullable GlideException e, Object model, Target<Drawable> target, boolean isFirstResource) {
                                android.util.Log.e("BlockedContentAdapter", "Favicon load failed for " + content + ": " + (e != null ? e.getMessage() : "unknown"));
                                if (e != null) {
                                    e.logRootCauses("BlockedContentAdapter");
                                }
                                return false; // Let Glide handle error drawable
                            }

                            @Override
                            public boolean onResourceReady(Drawable resource, Object model, Target<Drawable> target, DataSource dataSource, boolean isFirstResource) {
                                android.util.Log.d("BlockedContentAdapter", "Favicon loaded successfully for " + content + " from " + dataSource);
                                return false; // Let Glide handle the drawable
                            }
                        })
                        .into(holder.ivIcon);
            } catch (Exception e) {
                android.util.Log.e("BlockedContentAdapter", "Error loading favicon: " + e.getMessage(), e);
                holder.ivIcon.setImageResource(R.drawable.ic_web);
            }
        }

        // Remove button click
        if (removeListener != null) {
            holder.btnRemove.setVisibility(View.VISIBLE);
            holder.btnRemove.setOnClickListener(v -> removeListener.onRemoveClick(content));
        } else {
            // Hide remove button for child view (they can't remove blocks)
            holder.btnRemove.setVisibility(View.GONE);
        }
    }

    @Override
    public int getItemCount() {
        return blockedItems != null ? blockedItems.size() : 0;
    }

    /**
     * Get favicon URL for a website
     * Uses Google's favicon service and DuckDuckGo as fallback
     * 
     * @param websiteUrl The website URL
     * @return Favicon URL
     */
    private String getFaviconUrl(String websiteUrl) {
        // Clean up the URL
        String cleanUrl = websiteUrl.trim();
        
        // Remove protocol if present
        cleanUrl = cleanUrl.replaceAll("^(https?://)?(www\\.)?", "");
        
        // Remove trailing slash and path
        if (cleanUrl.contains("/")) {
            cleanUrl = cleanUrl.substring(0, cleanUrl.indexOf("/"));
        }
        
        // Extract domain
        String domain = cleanUrl;
        
        // Use Google's favicon service (most reliable)
        // Alternative: DuckDuckGo - https://icons.duckduckgo.com/ip3/" + domain + ".ico
        return "https://www.google.com/s2/favicons?domain=" + domain + "&sz=64";
    }

    static class BlockedViewHolder extends RecyclerView.ViewHolder {
        ImageView ivIcon;
        TextView tvTitle;
        TextView tvSubtitle;
        ImageButton btnRemove;

        public BlockedViewHolder(@NonNull View itemView) {
            super(itemView);
            ivIcon = itemView.findViewById(R.id.iv_icon);
            tvTitle = itemView.findViewById(R.id.tv_title);
            tvSubtitle = itemView.findViewById(R.id.tv_subtitle);
            btnRemove = itemView.findViewById(R.id.btn_remove);
        }
    }
}