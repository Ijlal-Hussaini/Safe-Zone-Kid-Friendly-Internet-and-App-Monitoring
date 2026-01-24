package com.safezone.app.fragments;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.ValueEventListener;
import com.safezone.app.R;
import com.safezone.app.activities.QRScannerActivity;
import com.safezone.app.adapters.BlockedContentAdapter;
import com.safezone.app.models.ChildUser;
import com.safezone.app.utils.FirebaseHelper;

/**
 * Fragment to display usage overview for child
 */
public class UsageOverviewFragment extends Fragment {

    private static final int QR_SCANNER_REQUEST = 100;

    private MaterialCardView cardLinkStatus;
    private MaterialCardView cardScreenTime;
    private MaterialButton btnScanQrLink;

    private TextView tvRemainingTime;
    private TextView tvDailyLimit;
    private TextView tvBlockedAppsCount;
    private TextView tvBlockedWebsitesCount;
    private TextView tvAllowedAppsCount;
    private View layoutNoBlockedApps;
    private View layoutNoBlockedWebsites;
    private View layoutNoAllowedApps;
    private View scrollBlockedApps;
    private View scrollBlockedWebsites;
    private View scrollAllowedApps;
    private RecyclerView recyclerBlockedApps;
    private RecyclerView recyclerBlockedWebsites;
    private RecyclerView recyclerAllowedApps;
    private MaterialCardView cardAllowedApps;
    private ProgressBar progressBar;

    private DatabaseReference usersRef;
    private String currentUserId;
    private ValueEventListener userDataListener;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_usage_overview, container, false);

        initViews(view);
        setupListeners();
        loadUserData();

        return view;
    }

    private void initViews(View view) {
        cardLinkStatus = view.findViewById(R.id.card_link_status);
        cardScreenTime = view.findViewById(R.id.card_screen_time);
        btnScanQrLink = view.findViewById(R.id.btn_scan_qr_link);

        tvRemainingTime = view.findViewById(R.id.tv_remaining_time);
        tvDailyLimit = view.findViewById(R.id.tv_daily_limit);
        tvBlockedAppsCount = view.findViewById(R.id.tv_blocked_apps_count);
        tvBlockedWebsitesCount = view.findViewById(R.id.tv_blocked_websites_count);
        tvAllowedAppsCount = view.findViewById(R.id.tv_allowed_apps_count);
        layoutNoBlockedApps = view.findViewById(R.id.layout_no_blocked_apps);
        layoutNoBlockedWebsites = view.findViewById(R.id.layout_no_blocked_websites);
        layoutNoAllowedApps = view.findViewById(R.id.layout_no_allowed_apps);
        scrollBlockedApps = view.findViewById(R.id.scroll_blocked_apps);
        scrollBlockedWebsites = view.findViewById(R.id.scroll_blocked_websites);
        scrollAllowedApps = view.findViewById(R.id.scroll_allowed_apps);
        recyclerBlockedApps = view.findViewById(R.id.recycler_blocked_apps);
        recyclerBlockedWebsites = view.findViewById(R.id.recycler_blocked_websites);
        recyclerAllowedApps = view.findViewById(R.id.recycler_allowed_apps);
        cardAllowedApps = view.findViewById(R.id.card_allowed_apps);
        progressBar = view.findViewById(R.id.progress_bar);

        usersRef = FirebaseHelper.getUsersRef();
        currentUserId = FirebaseHelper.getCurrentUserId();

        // Setup RecyclerViews
        recyclerBlockedApps.setLayoutManager(new androidx.recyclerview.widget.LinearLayoutManager(getContext()));
        recyclerBlockedWebsites.setLayoutManager(new androidx.recyclerview.widget.LinearLayoutManager(getContext()));
        recyclerAllowedApps.setLayoutManager(new androidx.recyclerview.widget.LinearLayoutManager(getContext()));
    }

    private void setupListeners() {
        btnScanQrLink.setOnClickListener(v -> openQRScanner());
    }

    private void loadUserData() {
        showLoading(true);

        userDataListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                showLoading(false);
                if (snapshot.exists()) {
                    try {
                        // Manually parse child data to avoid deserialization issues
                        ChildUser childUser = new ChildUser();
                        childUser.setUid(snapshot.child("uid").getValue(String.class));
                        childUser.setName(snapshot.child("name").getValue(String.class));
                        childUser.setEmail(snapshot.child("email").getValue(String.class));
                        childUser.setRole(snapshot.child("role").getValue(String.class));
                        
                        // Parse parentId
                        if (snapshot.child("parentId").exists()) {
                            childUser.setParentId(snapshot.child("parentId").getValue(String.class));
                        }
                        
                        // Parse screenTimeRules if exists
                        if (snapshot.child("screenTimeRules").exists()) {
                            DataSnapshot rulesSnapshot = snapshot.child("screenTimeRules");
                            com.safezone.app.models.ScreenTimeRule rules = new com.safezone.app.models.ScreenTimeRule();
                            
                            if (rulesSnapshot.child("enabled").exists()) {
                                Boolean enabled = rulesSnapshot.child("enabled").getValue(Boolean.class);
                                if (enabled != null) rules.setEnabled(enabled);
                            }
                            if (rulesSnapshot.child("dailyLimitMinutes").exists()) {
                                Integer limit = rulesSnapshot.child("dailyLimitMinutes").getValue(Integer.class);
                                if (limit != null) rules.setDailyLimitMinutes(limit);
                            }
                            
                            // Parse allowed apps
                            if (rulesSnapshot.child("allowedApps").exists()) {
                                java.util.List<String> allowedApps = new java.util.ArrayList<>();
                                for (DataSnapshot appSnapshot : rulesSnapshot.child("allowedApps").getChildren()) {
                                    String app = appSnapshot.getValue(String.class);
                                    if (app != null) allowedApps.add(app);
                                }
                                rules.setAllowedApps(allowedApps);
                            }
                            
                            childUser.setScreenTimeRules(rules);
                        }
                        
                        // Parse contentRules if exists
                        if (snapshot.child("contentRules").exists()) {
                            DataSnapshot contentSnapshot = snapshot.child("contentRules");
                            com.safezone.app.models.ContentRule contentRules = new com.safezone.app.models.ContentRule();
                            
                            // Parse blocked apps
                            if (contentSnapshot.child("blockedApps").exists()) {
                                java.util.List<String> blockedApps = new java.util.ArrayList<>();
                                for (DataSnapshot appSnapshot : contentSnapshot.child("blockedApps").getChildren()) {
                                    String app = appSnapshot.getValue(String.class);
                                    if (app != null) blockedApps.add(app);
                                }
                                contentRules.setBlockedApps(blockedApps);
                            }
                            
                            // Parse blocked websites
                            if (contentSnapshot.child("blockedWebsites").exists()) {
                                java.util.List<String> blockedWebsites = new java.util.ArrayList<>();
                                for (DataSnapshot siteSnapshot : contentSnapshot.child("blockedWebsites").getChildren()) {
                                    String site = siteSnapshot.getValue(String.class);
                                    if (site != null) blockedWebsites.add(site);
                                }
                                contentRules.setBlockedWebsites(blockedWebsites);
                            }
                            
                            childUser.setContentRules(contentRules);
                        }
                        
                        updateUI(childUser);
                    } catch (Exception e) {
                        android.util.Log.e("UsageOverviewFragment", "Error parsing user data: " + e.getMessage());
                        Toast.makeText(getContext(), "Error loading data", Toast.LENGTH_SHORT).show();
                    }
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                showLoading(false);
                Toast.makeText(getContext(), "Error: " + error.getMessage(),
                        Toast.LENGTH_SHORT).show();
            }
        };

        usersRef.child(currentUserId).addValueEventListener(userDataListener);
    }

    private void updateUI(ChildUser childUser) {
        // Check if linked to parent
        if (childUser.getParentId() == null || childUser.getParentId().isEmpty()) {
            // Not linked - show link card
            cardLinkStatus.setVisibility(View.VISIBLE);
            cardScreenTime.setVisibility(View.GONE);
        } else {
            // Already linked - hide link card and show usage
            cardLinkStatus.setVisibility(View.GONE);
            cardScreenTime.setVisibility(View.VISIBLE);

            // Update screen time
            updateScreenTime(childUser);

            // Update blocked content
            updateBlockedContent(childUser);
            
            // Update allowed apps
            updateAllowedApps(childUser);
        }
    }
    
    private void updateBlockedContent(ChildUser childUser) {
        if (childUser.getContentRules() == null) {
            // No content rules set
            showNoBlockedApps();
            showNoBlockedWebsites();
            return;
        }
        
        // Update blocked apps
        java.util.List<String> blockedApps = childUser.getContentRules().getBlockedApps();
        if (blockedApps != null && !blockedApps.isEmpty()) {
            tvBlockedAppsCount.setText(String.valueOf(blockedApps.size()));
            layoutNoBlockedApps.setVisibility(View.GONE);
            scrollBlockedApps.setVisibility(View.VISIBLE);
            
            // Create adapter for blocked apps (child view - no remove button)
            if (getContext() != null) {
                BlockedContentAdapter appsAdapter = new BlockedContentAdapter(
                        getContext(), 
                        blockedApps, 
                        true,  // isAppList
                        null   // no remove listener for child
                );
                recyclerBlockedApps.setAdapter(appsAdapter);
                
                // Adjust height based on number of items (flexible for up to 3, then scrollable)
                adjustScrollViewHeight(scrollBlockedApps, blockedApps.size());
                
                android.util.Log.d("UsageOverview", "Blocked apps adapter set with " + blockedApps.size() + " items");
            } else {
                android.util.Log.e("UsageOverview", "Context is null, cannot create apps adapter");
            }
        } else {
            showNoBlockedApps();
        }
        
        // Update blocked websites
        java.util.List<String> blockedWebsites = childUser.getContentRules().getBlockedWebsites();
        if (blockedWebsites != null && !blockedWebsites.isEmpty()) {
            tvBlockedWebsitesCount.setText(String.valueOf(blockedWebsites.size()));
            layoutNoBlockedWebsites.setVisibility(View.GONE);
            scrollBlockedWebsites.setVisibility(View.VISIBLE);
            
            // Create adapter for blocked websites (child view - no remove button)
            if (getContext() != null) {
                BlockedContentAdapter websitesAdapter = new BlockedContentAdapter(
                        getContext(), 
                        blockedWebsites, 
                        false,  // isAppList
                        null    // no remove listener for child
                );
                recyclerBlockedWebsites.setAdapter(websitesAdapter);
                
                // Adjust height based on number of items (flexible for up to 3, then scrollable)
                adjustScrollViewHeight(scrollBlockedWebsites, blockedWebsites.size());
                
                android.util.Log.d("UsageOverview", "Blocked websites adapter set with " + blockedWebsites.size() + " items");
            } else {
                android.util.Log.e("UsageOverview", "Context is null, cannot create websites adapter");
            }
        } else {
            showNoBlockedWebsites();
        }
    }
    
    private void showNoBlockedApps() {
        tvBlockedAppsCount.setText("0");
        layoutNoBlockedApps.setVisibility(View.VISIBLE);
        scrollBlockedApps.setVisibility(View.GONE);
    }
    
    private void showNoBlockedWebsites() {
        tvBlockedWebsitesCount.setText("0");
        layoutNoBlockedWebsites.setVisibility(View.VISIBLE);
        scrollBlockedWebsites.setVisibility(View.GONE);
    }
    
    private void updateAllowedApps(ChildUser childUser) {
        if (childUser.getScreenTimeRules() == null || !childUser.getScreenTimeRules().isEnabled()) {
            // Screen time not enabled - hide allowed apps card
            cardAllowedApps.setVisibility(View.GONE);
            return;
        }
        
        java.util.List<String> allowedApps = childUser.getScreenTimeRules().getAllowedApps();
        if (allowedApps != null && !allowedApps.isEmpty()) {
            cardAllowedApps.setVisibility(View.VISIBLE);
            tvAllowedAppsCount.setText(String.valueOf(allowedApps.size()));
            layoutNoAllowedApps.setVisibility(View.GONE);
            scrollAllowedApps.setVisibility(View.VISIBLE);
            
            if (getContext() != null) {
                BlockedContentAdapter adapter = new BlockedContentAdapter(
                        getContext(), 
                        allowedApps, 
                        true,  // isAppList
                        null   // no remove listener for child
                );
                recyclerAllowedApps.setAdapter(adapter);
                adjustScrollViewHeight(scrollAllowedApps, allowedApps.size());
            }
        } else {
            cardAllowedApps.setVisibility(View.VISIBLE);
            tvAllowedAppsCount.setText("0");
            layoutNoAllowedApps.setVisibility(View.VISIBLE);
            scrollAllowedApps.setVisibility(View.GONE);
        }
    }
    
    /**
     * Adjust ScrollView height based on number of items
     * Shows up to 3 items without scrolling, then becomes scrollable
     * 
     * @param scrollView The NestedScrollView to adjust
     * @param itemCount Number of items in the list
     */
    private void adjustScrollViewHeight(View scrollView, int itemCount) {
        ViewGroup.LayoutParams params = scrollView.getLayoutParams();
        
        // Each item is approximately 80dp (48dp icon + 16dp padding top/bottom + 16dp margin)
        int itemHeightDp = 88;
        float density = getResources().getDisplayMetrics().density;
        int itemHeightPx = (int) (itemHeightDp * density);
        
        if (itemCount <= 3) {
            // Show all items without scrolling (flexible height)
            params.height = ViewGroup.LayoutParams.WRAP_CONTENT;
        } else {
            // Show 3 items and make it scrollable
            params.height = itemHeightPx * 3;
        }
        
        scrollView.setLayoutParams(params);
    }
    
    private void updateScreenTime(ChildUser childUser) {
        if (childUser.getScreenTimeRules() != null) {
            int dailyLimit = childUser.getScreenTimeRules().getDailyLimitMinutes();
            boolean enabled = childUser.getScreenTimeRules().isEnabled();

            if (enabled && dailyLimit > 0) {
                // Load actual usage from Firebase
                loadActualUsage(dailyLimit);
            } else {
                tvRemainingTime.setText("Unlimited");
                tvDailyLimit.setText("No time limit set");
            }
        } else {
            tvRemainingTime.setText("Unlimited");
            tvDailyLimit.setText("No time limit set");
        }
    }
    
    /**
     * Load actual screen time usage from Firebase and display remaining time
     */
    private void loadActualUsage(int dailyLimit) {
        FirebaseHelper.getUsersRef().child(currentUserId).child("screenTimeUsage").child("todayMinutes")
                .addValueEventListener(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        long usedMinutes = 0;
                        if (snapshot.exists()) {
                            Long value = snapshot.getValue(Long.class);
                            if (value != null) {
                                usedMinutes = value;
                            }
                        }
                        
                        long remainingMinutes = dailyLimit - usedMinutes;
                        
                        if (remainingMinutes <= 0) {
                            // Time exceeded
                            tvRemainingTime.setText("0h 0m");
                            tvRemainingTime.setTextColor(getResources().getColor(R.color.error, null));
                            tvDailyLimit.setText("Time limit exceeded! Used " + usedMinutes + "/" + dailyLimit + " min");
                            tvDailyLimit.setTextColor(getResources().getColor(R.color.error, null));
                        } else {
                            // Time remaining
                            int hours = (int) (remainingMinutes / 60);
                            int minutes = (int) (remainingMinutes % 60);
                            
                            String remainingText = hours + "h " + minutes + "m";
                            tvRemainingTime.setText(remainingText);
                            tvRemainingTime.setTextColor(getResources().getColor(R.color.text_white, null));
                            
                            String limitText = "Out of " + dailyLimit + " minutes today";
                            tvDailyLimit.setText(limitText);
                            tvDailyLimit.setTextColor(getResources().getColor(R.color.text_white, null));
                        }
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        // Show limit as fallback
                        int hours = dailyLimit / 60;
                        int minutes = dailyLimit % 60;
                        tvRemainingTime.setText(hours + "h " + minutes + "m");
                        tvDailyLimit.setText("Out of " + dailyLimit + " minutes today");
                    }
                });
    }

    private void openQRScanner() {
        Intent intent = new Intent(getContext(), QRScannerActivity.class);
        startActivityForResult(intent, QR_SCANNER_REQUEST);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == QR_SCANNER_REQUEST && resultCode == getActivity().RESULT_OK) {
            // Successfully linked, reload data
            Toast.makeText(getContext(), "Successfully linked to parent!",
                    Toast.LENGTH_SHORT).show();
            loadUserData();
        }
    }



    private void showLoading(boolean show) {
        progressBar.setVisibility(show ? View.VISIBLE : View.GONE);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        // Remove listener to prevent memory leaks
        if (userDataListener != null && usersRef != null && currentUserId != null) {
            usersRef.child(currentUserId).removeEventListener(userDataListener);
        }
    }
}