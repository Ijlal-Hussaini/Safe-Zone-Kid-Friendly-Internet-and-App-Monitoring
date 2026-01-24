package com.safezone.app.adapters;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.google.android.material.card.MaterialCardView;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.ValueEventListener;
import com.safezone.app.R;
import com.safezone.app.models.Alert;
import com.safezone.app.utils.FirebaseHelper;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import de.hdodenhof.circleimageview.CircleImageView;

/**
 * Adapter for displaying alerts in RecyclerView
 */
public class AlertsAdapter extends RecyclerView.Adapter<AlertsAdapter.AlertViewHolder> {

    public interface OnAlertClickListener {
        void onAlertClick(Alert alert);
    }

    public interface OnAlertDeleteListener {
        void onAlertDelete(Alert alert);
    }

    private Context context;
    private List<Alert> alerts;
    private OnAlertClickListener clickListener;
    private OnAlertDeleteListener deleteListener;

    public AlertsAdapter(Context context, List<Alert> alerts, OnAlertClickListener clickListener, OnAlertDeleteListener deleteListener) {
        this.context = context;
        this.alerts = alerts;
        this.clickListener = clickListener;
        this.deleteListener = deleteListener;
    }

    @NonNull
    @Override
    public AlertViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_alert, parent, false);
        return new AlertViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull AlertViewHolder holder, int position) {
        Alert alert = alerts.get(position);

        // Load child profile picture
        loadChildProfilePicture(holder.ivChildProfile, alert.getChildId());

        // Set child name
        holder.tvChildName.setText(alert.getChildName());

        // Set alert message
        holder.tvMessage.setText(alert.getMessage());

        // Set details if available
        if (alert.getDetails() != null && !alert.getDetails().isEmpty()) {
            holder.tvDetails.setText(alert.getDetails());
            holder.tvDetails.setVisibility(View.VISIBLE);
        } else {
            holder.tvDetails.setVisibility(View.GONE);
        }

        // Set timestamp
        String timeText = formatTimestamp(alert.getTimestamp());
        holder.tvTimestamp.setText(timeText);

        // Visual indicator for unread alerts - use theme-aware colors
        if (!alert.isRead()) {
            holder.cardView.setCardBackgroundColor(context.getColor(R.color.alert_unread));
        } else {
            holder.cardView.setCardBackgroundColor(context.getColor(R.color.alert_read));
        }

        // Click listener - mark as read
        holder.itemView.setOnClickListener(v -> {
            if (clickListener != null) {
                clickListener.onAlertClick(alert);
            }
        });

        // Delete button listener
        holder.btnDelete.setOnClickListener(v -> {
            if (deleteListener != null) {
                deleteListener.onAlertDelete(alert);
            }
        });
    }

    /**
     * Load child's profile picture from Firebase
     */
    private void loadChildProfilePicture(CircleImageView imageView, String childId) {
        if (childId == null || childId.isEmpty()) {
            imageView.setImageResource(R.drawable.child_avatar);
            return;
        }

        FirebaseHelper.getUserRef(childId).child("profilePhotoUrl")
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        String photoUrl = snapshot.getValue(String.class);
                        if (photoUrl != null && !photoUrl.isEmpty()) {
                            Glide.with(context)
                                    .load(photoUrl)
                                    .placeholder(R.drawable.child_avatar)
                                    .error(R.drawable.child_avatar)
                                    .into(imageView);
                        } else {
                            imageView.setImageResource(R.drawable.child_avatar);
                        }
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        imageView.setImageResource(R.drawable.child_avatar);
                    }
                });
    }

    @Override
    public int getItemCount() {
        return alerts != null ? alerts.size() : 0;
    }

    public void updateAlerts(List<Alert> newAlerts) {
        this.alerts = newAlerts;
        notifyDataSetChanged();
    }

    private String formatTimestamp(long timestamp) {
        long now = System.currentTimeMillis();
        long diff = now - timestamp;

        long seconds = diff / 1000;
        long minutes = seconds / 60;
        long hours = minutes / 60;
        long days = hours / 24;

        if (seconds < 60) {
            return "Just now";
        } else if (minutes < 60) {
            return minutes + " min ago";
        } else if (hours < 24) {
            return hours + " hour" + (hours > 1 ? "s" : "") + " ago";
        } else if (days < 7) {
            return days + " day" + (days > 1 ? "s" : "") + " ago";
        } else {
            SimpleDateFormat sdf = new SimpleDateFormat("MMM dd, yyyy", Locale.getDefault());
            return sdf.format(new Date(timestamp));
        }
    }

    static class AlertViewHolder extends RecyclerView.ViewHolder {
        MaterialCardView cardView;
        CircleImageView ivChildProfile;
        TextView tvChildName;
        TextView tvMessage;
        TextView tvDetails;
        TextView tvTimestamp;
        ImageButton btnDelete;

        public AlertViewHolder(@NonNull View itemView) {
            super(itemView);
            cardView = itemView.findViewById(R.id.card_alert);
            ivChildProfile = itemView.findViewById(R.id.iv_child_profile);
            tvChildName = itemView.findViewById(R.id.tv_child_name);
            tvMessage = itemView.findViewById(R.id.tv_message);
            tvDetails = itemView.findViewById(R.id.tv_details);
            tvTimestamp = itemView.findViewById(R.id.tv_timestamp);
            btnDelete = itemView.findViewById(R.id.btn_delete);
        }
    }
}