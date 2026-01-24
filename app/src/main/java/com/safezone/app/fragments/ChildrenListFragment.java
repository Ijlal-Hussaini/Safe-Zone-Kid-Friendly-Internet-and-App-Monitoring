package com.safezone.app.fragments;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.ValueEventListener;
import com.safezone.app.R;
import com.safezone.app.activities.ActivityLogsActivity;
import com.safezone.app.activities.ScreenTimeSettingsActivity;
import com.safezone.app.activities.ContentFilterActivity;
import com.safezone.app.adapters.ChildrenAdapter;
import com.safezone.app.models.ChildUser;
import com.safezone.app.utils.FirebaseHelper;

import java.util.ArrayList;
import java.util.List;

/**
 * Fragment to display list of linked children
 */
public class ChildrenListFragment extends Fragment {

    private RecyclerView recyclerChildren;
    private ChildrenAdapter adapter;
    private ProgressBar progressBar;
    private LinearLayout emptyState;
    private TextView tvChildrenCount;

    private DatabaseReference usersRef;
    private String currentUserId;
    private ValueEventListener childrenListener;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_children_list, container, false);

        initViews(view);
        setupRecyclerView();
        loadChildren();

        return view;
    }

    private void initViews(View view) {
        recyclerChildren = view.findViewById(R.id.recycler_children);
        progressBar = view.findViewById(R.id.progress_bar);
        emptyState = view.findViewById(R.id.empty_state);
        tvChildrenCount = view.findViewById(R.id.tv_children_count);

        usersRef = FirebaseHelper.getUsersRef();
        currentUserId = FirebaseHelper.getCurrentUserId();
    }

    private void setupRecyclerView() {
        adapter = new ChildrenAdapter(getContext(), new ChildrenAdapter.OnChildClickListener() {
            @Override
            public void onChildClick(ChildUser child) {
                // Show options dialog for child
                showChildOptionsDialog(child);
            }

            @Override
            public void onRemoveChild(ChildUser child, int position) {
                // Show confirmation dialog
                showRemoveConfirmation(child, position);
            }
        });

        recyclerChildren.setLayoutManager(new LinearLayoutManager(getContext()));
        recyclerChildren.setAdapter(adapter);
    }

    private void showChildOptionsDialog(ChildUser child) {
        String[] options = {
                "View Activity Logs",
                "Screen Time Settings",
                "Content Filtering"
        };

        new androidx.appcompat.app.AlertDialog.Builder(getContext())
                .setTitle(child.getName())
                .setItems(options, (dialog, which) -> {
                    Intent intent;
                    switch (which) {
                        case 0: // Activity Logs
                            intent = new Intent(getContext(), ActivityLogsActivity.class);
                            intent.putExtra("childUid", child.getUid());
                            startActivity(intent);
                            break;
                        case 1: // Screen Time
                            intent = new Intent(getContext(), ScreenTimeSettingsActivity.class);
                            intent.putExtra("childUid", child.getUid());
                            startActivity(intent);
                            break;
                        case 2: // Content Filter
                            intent = new Intent(getContext(), ContentFilterActivity.class);
                            intent.putExtra("childUid", child.getUid());
                            startActivity(intent);
                            break;
                    }
                })
                .show();
    }

    private void showRemoveConfirmation(ChildUser child, int position) {
        android.view.View dialogView = getLayoutInflater().inflate(R.layout.dialog_remove_child, null);
        
        // Set child name in dialog
        android.widget.TextView tvChildName = dialogView.findViewById(R.id.tv_child_name);
        tvChildName.setText("Are you sure you want to unlink " + child.getName() + "?");
        
        com.google.android.material.dialog.MaterialAlertDialogBuilder builder = 
            new com.google.android.material.dialog.MaterialAlertDialogBuilder(getContext(), R.style.ThemeOverlay_App_MaterialAlertDialog);
        builder.setView(dialogView);
        builder.setBackground(getResources().getDrawable(R.drawable.dialog_background, getContext().getTheme()));
        builder.setCancelable(true);
        
        androidx.appcompat.app.AlertDialog dialog = builder.create();
        
        dialogView.findViewById(R.id.btn_cancel).setOnClickListener(v -> dialog.dismiss());
        
        dialogView.findViewById(R.id.btn_remove).setOnClickListener(v -> {
            dialog.dismiss();
            removeChild(child, position);
        });
        
        dialog.show();
    }

    private void removeChild(ChildUser child, int position) {
        if (child == null || child.getUid() == null) {
            Toast.makeText(getContext(), "Error: Invalid child data", Toast.LENGTH_SHORT).show();
            return;
        }

        showLoading(true);

        String childUid = child.getUid();

        // Use atomic update to remove both sides at once
        java.util.Map<String, Object> updates = new java.util.HashMap<>();
        updates.put("users/" + currentUserId + "/children/" + childUid, null);
        updates.put("users/" + childUid + "/parentId", null);

        com.google.firebase.database.FirebaseDatabase.getInstance().getReference()
                .updateChildren(updates)
                .addOnSuccessListener(aVoid -> {
                    showLoading(false);

                    // Remove from adapter
                    adapter.removeChild(position);

                    Toast.makeText(getContext(),
                            child.getName() + " has been unlinked successfully",
                            Toast.LENGTH_SHORT).show();

                    // The real-time listener will automatically update the list
                })
                .addOnFailureListener(e -> {
                    showLoading(false);
                    Toast.makeText(getContext(),
                            "Failed to unlink child: " + e.getMessage(),
                            Toast.LENGTH_SHORT).show();
                });
    }

    private void loadChildren() {
        showLoading(true);

        // Remove old listener if exists
        if (childrenListener != null) {
            usersRef.child(currentUserId).removeEventListener(childrenListener);
        }

        // Use real-time listener to detect new children
        childrenListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (snapshot.exists()) {
                    // Get children UIDs from parent's children map
                    DataSnapshot childrenSnapshot = snapshot.child("children");

                    if (childrenSnapshot.exists()) {
                        List<String> childUids = new ArrayList<>();
                        for (DataSnapshot childSnapshot : childrenSnapshot.getChildren()) {
                            childUids.add(childSnapshot.getKey());
                        }

                        if (childUids.isEmpty()) {
                            showEmptyState();
                        } else {
                            loadChildrenDetails(childUids);
                        }
                    } else {
                        showEmptyState();
                    }
                } else {
                    showLoading(false);
                    Toast.makeText(getContext(), "User data not found", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                showLoading(false);
                Toast.makeText(getContext(), "Error: " + error.getMessage(),
                        Toast.LENGTH_SHORT).show();
            }
        };

        // Attach real-time listener
        usersRef.child(currentUserId).addValueEventListener(childrenListener);
    }

    private void loadChildrenDetails(List<String> childUids) {
        List<ChildUser> children = new ArrayList<>();
        final int[] loadedCount = {0};

        for (String childUid : childUids) {
            usersRef.child(childUid).addListenerForSingleValueEvent(new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot snapshot) {
                    if (snapshot.exists()) {
                        try {
                            // Manually parse child data to avoid deserialization issues
                            ChildUser child = new ChildUser();
                            child.setUid(snapshot.child("uid").getValue(String.class));
                            child.setName(snapshot.child("name").getValue(String.class));
                            child.setEmail(snapshot.child("email").getValue(String.class));
                            child.setRole(snapshot.child("role").getValue(String.class));
                            
                            // Parse age safely
                            if (snapshot.child("age").exists()) {
                                Integer age = snapshot.child("age").getValue(Integer.class);
                                if (age != null) {
                                    child.setAge(age);
                                }
                            }
                            
                            // Parse dateOfBirth safely
                            if (snapshot.child("dateOfBirth").exists()) {
                                Long dob = snapshot.child("dateOfBirth").getValue(Long.class);
                                if (dob != null) {
                                    child.setDateOfBirth(dob);
                                }
                            }
                            
                            // Parse deviceNickname safely
                            if (snapshot.child("deviceNickname").exists()) {
                                child.setDeviceNickname(snapshot.child("deviceNickname").getValue(String.class));
                            }
                            
                            // Parse parentId safely
                            if (snapshot.child("parentId").exists()) {
                                child.setParentId(snapshot.child("parentId").getValue(String.class));
                            }
                            
                            // Parse deviceId safely
                            if (snapshot.child("deviceId").exists()) {
                                child.setDeviceId(snapshot.child("deviceId").getValue(String.class));
                            }
                            
                            if (child.getUid() != null && child.getName() != null) {
                                children.add(child);
                                
                                // Sync parent info to child's record (for child profile display)
                                syncParentInfoToChild(childUid);
                            }
                        } catch (Exception e) {
                            // Log error but continue loading other children
                            android.util.Log.e("ChildrenListFragment", "Error parsing child data: " + e.getMessage());
                        }
                    }

                    loadedCount[0]++;
                    if (loadedCount[0] == childUids.size()) {
                        // All children loaded
                        showLoading(false);
                        if (children.isEmpty()) {
                            showEmptyState();
                        } else {
                            showChildren(children);
                        }
                    }
                }

                @Override
                public void onCancelled(@NonNull DatabaseError error) {
                    loadedCount[0]++;
                    if (loadedCount[0] == childUids.size()) {
                        showLoading(false);
                        Toast.makeText(getContext(), "Error loading children",
                                Toast.LENGTH_SHORT).show();
                    }
                }
            });
        }
    }

    private void showChildren(List<ChildUser> children) {
        adapter.setChildren(children);
        recyclerChildren.setVisibility(View.VISIBLE);
        emptyState.setVisibility(View.GONE);

        // Update count
        String countText = children.size() + " " +
                (children.size() == 1 ? "child" : "children") + " linked";
        tvChildrenCount.setText(countText);
    }

    private void showEmptyState() {
        showLoading(false);
        recyclerChildren.setVisibility(View.GONE);
        emptyState.setVisibility(View.VISIBLE);
        tvChildrenCount.setText("0 children linked");
    }

    private void showLoading(boolean show) {
        progressBar.setVisibility(show ? View.VISIBLE : View.GONE);
        if (!show) {
            recyclerChildren.setVisibility(View.VISIBLE);
        }
    }

    /**
     * Sync parent's email and name to child's record
     * This allows the child to display parent info without needing read access to parent's data
     */
    private void syncParentInfoToChild(String childUid) {
        // Get current parent's info
        usersRef.child(currentUserId).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot parentSnapshot) {
                if (!parentSnapshot.exists()) return;
                
                String parentEmail = parentSnapshot.child("email").getValue(String.class);
                String parentName = parentSnapshot.child("name").getValue(String.class);
                
                // Check if child already has this info
                usersRef.child(childUid).addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot childSnapshot) {
                        String existingEmail = childSnapshot.child("parentEmail").getValue(String.class);
                        String existingName = childSnapshot.child("parentName").getValue(String.class);
                        
                        // Only update if info is missing or different
                        java.util.Map<String, Object> updates = new java.util.HashMap<>();
                        
                        if (parentEmail != null && !parentEmail.isEmpty() && 
                            (existingEmail == null || !existingEmail.equals(parentEmail))) {
                            updates.put("parentEmail", parentEmail);
                        }
                        
                        if (parentName != null && !parentName.isEmpty() && 
                            (existingName == null || !existingName.equals(parentName))) {
                            updates.put("parentName", parentName);
                        }
                        
                        if (!updates.isEmpty()) {
                            usersRef.child(childUid).updateChildren(updates)
                                    .addOnSuccessListener(aVoid -> 
                                        android.util.Log.d("ChildrenListFragment", "Synced parent info to child: " + childUid))
                                    .addOnFailureListener(e -> 
                                        android.util.Log.e("ChildrenListFragment", "Failed to sync parent info: " + e.getMessage()));
                        }
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        // Ignore - not critical
                    }
                });
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                // Ignore - not critical
            }
        });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        // Remove listeners to prevent memory leaks
        if (childrenListener != null && usersRef != null && currentUserId != null) {
            usersRef.child(currentUserId).removeEventListener(childrenListener);
        }
    }
}