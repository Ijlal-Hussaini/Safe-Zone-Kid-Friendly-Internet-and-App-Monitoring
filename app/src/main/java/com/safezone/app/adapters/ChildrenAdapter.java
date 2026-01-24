package com.safezone.app.adapters;

import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.ValueEventListener;
import com.safezone.app.R;
import com.safezone.app.models.ChildUser;
import com.safezone.app.utils.FirebaseHelper;
import com.safezone.app.utils.ImageHelper;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import de.hdodenhof.circleimageview.CircleImageView;

/**
 * Children Adapter - Base64 Image Loading
 * Loads profile photos from Firebase Realtime Database (Base64)
 * NO FIREBASE STORAGE REQUIRED
 */
public class ChildrenAdapter extends RecyclerView.Adapter<ChildrenAdapter.ChildViewHolder> {

    private Context context;
    private List<ChildUser> childrenList;
    private OnChildClickListener listener;

    public interface OnChildClickListener {
        void onChildClick(ChildUser child);
        void onRemoveChild(ChildUser child, int position);
    }

    public ChildrenAdapter(Context context, OnChildClickListener listener) {
        this.context = context;
        this.childrenList = new ArrayList<>();
        this.listener = listener;
    }

    @NonNull
    @Override
    public ChildViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_child, parent, false);
        return new ChildViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ChildViewHolder holder, int position) {
        ChildUser child = childrenList.get(position);
        holder.bind(child);
    }

    @Override
    public int getItemCount() {
        return childrenList.size();
    }

    public void setChildren(List<ChildUser> children) {
        this.childrenList = children;
        notifyDataSetChanged();
    }

    public void addChild(ChildUser child) {
        this.childrenList.add(child);
        notifyItemInserted(childrenList.size() - 1);
    }

    public void removeChild(int position) {
        if (position >= 0 && position < childrenList.size()) {
            this.childrenList.remove(position);
            notifyItemRemoved(position);
            notifyItemRangeChanged(position, childrenList.size());
        }
    }



    class ChildViewHolder extends RecyclerView.ViewHolder {
        CircleImageView imgProfile;
        TextView tvName;
        TextView tvAge;
        TextView tvDeviceNickname;
        ImageView imgArrow;
        ImageView btnRemove;

        public ChildViewHolder(@NonNull View itemView) {
            super(itemView);
            imgProfile = itemView.findViewById(R.id.img_profile);
            tvName = itemView.findViewById(R.id.tv_name);
            tvAge = itemView.findViewById(R.id.tv_age);
            tvDeviceNickname = itemView.findViewById(R.id.tv_device_nickname);
            imgArrow = itemView.findViewById(R.id.img_arrow);

            try {
                Resources resources = itemView.getContext().getResources();
                int resourceId = resources.getIdentifier("btn_remove_child", "id", itemView.getContext().getPackageName());
                btnRemove = itemView.findViewById(resourceId);
            } catch (Exception e) {
                btnRemove = null;
            }

            itemView.setOnClickListener(v -> {
                int position = getAdapterPosition();
                if (position != RecyclerView.NO_POSITION) {
                    ChildUser child = childrenList.get(position);
                    // Navigate to Child Details Activity
                    Intent intent = new Intent(context, com.safezone.app.activities.ChildDetailsActivity.class);
                    intent.putExtra("childUid", child.getUid());
                    context.startActivity(intent);
                }
            });

            if (btnRemove != null) {
                btnRemove.setOnClickListener(v -> {
                    v.setEnabled(false);
                    int position = getAdapterPosition();
                    if (position != RecyclerView.NO_POSITION && listener != null) {
                        listener.onRemoveChild(childrenList.get(position), position);
                    }
                    v.postDelayed(() -> v.setEnabled(true), 1000);
                });
            }
        }

        public void bind(ChildUser child) {
            tvName.setText(child.getName());

            String ageText = child.getAge() > 0
                    ? child.getAge() + " years old"
                    : "Age not set";
            tvAge.setText(ageText);

            if (child.getDeviceNickname() != null && !child.getDeviceNickname().isEmpty()) {
                tvDeviceNickname.setText(child.getDeviceNickname());
                tvDeviceNickname.setVisibility(View.VISIBLE);
            } else {
                tvDeviceNickname.setVisibility(View.GONE);
            }

            // Load profile photo from Firebase Database (Base64)
            loadProfilePhoto(child.getUid());
        }

        /**
         * Load profile photo from Realtime Database as Base64 with real-time updates
         */
        private ValueEventListener photoListener;
        private String currentChildId;
        
        private void loadProfilePhoto(String childId) {
            // Remove old listener if exists
            if (photoListener != null && currentChildId != null) {
                FirebaseHelper.getUserRef(currentChildId).child("profilePhotoBase64")
                        .removeEventListener(photoListener);
            }
            
            currentChildId = childId;
            
            // Set default image first
            imgProfile.setImageResource(R.drawable.child_avatar);

            // Load Base64 image from database with real-time listener
            photoListener = new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot snapshot) {
                    if (snapshot.exists()) {
                        String base64Image = snapshot.getValue(String.class);
                        if (base64Image != null && !base64Image.isEmpty()) {
                            // Decode Base64 to Bitmap
                            Bitmap bitmap = ImageHelper.decodeBase64ToBitmap(base64Image);
                            if (bitmap != null) {
                                imgProfile.setImageBitmap(bitmap);
                            } else {
                                imgProfile.setImageResource(R.drawable.child_avatar);
                            }
                        }
                    } else {
                        imgProfile.setImageResource(R.drawable.child_avatar);
                    }
                }

                @Override
                public void onCancelled(@NonNull DatabaseError error) {
                    // Keep default avatar on error
                    imgProfile.setImageResource(R.drawable.child_avatar);
                }
            };
            
            FirebaseHelper.getUserRef(childId).child("profilePhotoBase64")
                    .addValueEventListener(photoListener);
        }
    }

    private String formatLastActive(long timestamp) {
        long now = System.currentTimeMillis();
        long diff = now - timestamp;

        long seconds = diff / 1000;
        long minutes = seconds / 60;
        long hours = minutes / 60;
        long days = hours / 24;

        if (seconds < 60) {
            return "Just now";
        } else if (minutes < 60) {
            return minutes + " minute" + (minutes > 1 ? "s" : "") + " ago";
        } else if (hours < 24) {
            return hours + " hour" + (hours > 1 ? "s" : "") + " ago";
        } else if (days < 7) {
            return days + " day" + (days > 1 ? "s" : "") + " ago";
        } else {
            SimpleDateFormat sdf = new SimpleDateFormat("MMM dd, yyyy", Locale.getDefault());
            return sdf.format(new Date(timestamp));
        }
    }
}
