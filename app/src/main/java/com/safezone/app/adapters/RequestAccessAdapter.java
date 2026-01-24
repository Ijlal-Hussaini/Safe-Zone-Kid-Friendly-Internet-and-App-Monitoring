package com.safezone.app.adapters;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.safezone.app.R;

import java.util.List;

/**
 * Adapter for displaying blocked content that child can request access to
 */
public class RequestAccessAdapter extends RecyclerView.Adapter<RequestAccessAdapter.ViewHolder> {

    public interface OnRequestClickListener {
        void onRequestClick(String content);
    }

    private Context context;
    private List<String> contentList;
    private boolean isAppList;
    private OnRequestClickListener listener;

    public RequestAccessAdapter(Context context, List<String> contentList, 
                               boolean isAppList, OnRequestClickListener listener) {
        this.context = context;
        this.contentList = contentList;
        this.isAppList = isAppList;
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_request_access, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        String content = contentList.get(position);

        if (isAppList) {
            // Display app
            try {
                PackageManager pm = context.getPackageManager();
                ApplicationInfo appInfo = pm.getApplicationInfo(content, 0);
                
                String appName = pm.getApplicationLabel(appInfo).toString();
                Drawable appIcon = pm.getApplicationIcon(appInfo);

                holder.tvTitle.setText(appName);
                holder.tvSubtitle.setText(content);
                holder.ivIcon.setImageDrawable(appIcon);
            } catch (PackageManager.NameNotFoundException e) {
                holder.tvTitle.setText(content);
                holder.tvSubtitle.setText("App not installed");
                holder.ivIcon.setImageResource(R.drawable.ic_app);
            }
        } else {
            // Display website
            holder.tvTitle.setText(content);
            holder.tvSubtitle.setText("Website");
            
            // Load favicon
            String faviconUrl = "https://www.google.com/s2/favicons?domain=" + content + "&sz=64";
            Glide.with(context)
                    .load(faviconUrl)
                    .placeholder(R.drawable.ic_web)
                    .error(R.drawable.ic_web)
                    .into(holder.ivIcon);
        }

        holder.btnRequest.setOnClickListener(v -> {
            if (listener != null) {
                listener.onRequestClick(content);
            }
        });
    }

    @Override
    public int getItemCount() {
        return contentList != null ? contentList.size() : 0;
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        ImageView ivIcon;
        TextView tvTitle;
        TextView tvSubtitle;
        Button btnRequest;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            ivIcon = itemView.findViewById(R.id.iv_icon);
            tvTitle = itemView.findViewById(R.id.tv_title);
            tvSubtitle = itemView.findViewById(R.id.tv_subtitle);
            btnRequest = itemView.findViewById(R.id.btn_request);
        }
    }
}
