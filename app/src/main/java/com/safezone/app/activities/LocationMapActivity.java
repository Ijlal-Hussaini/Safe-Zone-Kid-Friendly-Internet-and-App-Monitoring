package com.safezone.app.activities;

import android.os.Bundle;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.FragmentManager;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.ValueEventListener;
import com.safezone.app.R;
import com.safezone.app.utils.FirebaseHelper;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Activity to display child's location on Google Maps
 */
public class LocationMapActivity extends AppCompatActivity implements OnMapReadyCallback {

    private MaterialToolbar toolbar;
    private TextView tvChildName, tvLastUpdate, tvAccuracy;
    private FloatingActionButton fabRefresh;
    private ProgressBar progressBar;

    private GoogleMap googleMap;
    private DatabaseReference locationRef;
    private String childUid;
    private String childName;
    private ValueEventListener locationListener;

    private double currentLatitude = 0;
    private double currentLongitude = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_location_map);

        // Get child info from intent
        childUid = getIntent().getStringExtra("childUid");
        childName = getIntent().getStringExtra("childName");

        if (childUid == null) {
            Toast.makeText(this, "Child data not found", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        initViews();
        setupToolbar();
        setupMap();
        setupListeners();
    }

    private void initViews() {
        toolbar = findViewById(R.id.toolbar);
        tvChildName = findViewById(R.id.tv_child_name);
        tvLastUpdate = findViewById(R.id.tv_last_update);
        tvAccuracy = findViewById(R.id.tv_accuracy);
        fabRefresh = findViewById(R.id.fab_refresh);
        progressBar = findViewById(R.id.progress_bar);

        locationRef = FirebaseHelper.getUsersRef().child(childUid).child("location");

        if (childName != null) {
            tvChildName.setText(childName + "'s Location");
        }
    }

    private void setupToolbar() {
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayShowTitleEnabled(false);
        }
        toolbar.setNavigationOnClickListener(v -> finish());
    }

    private void setupMap() {
        FragmentManager fragmentManager = getSupportFragmentManager();
        SupportMapFragment mapFragment = (SupportMapFragment) fragmentManager
                .findFragmentById(R.id.map_fragment);

        if (mapFragment != null) {
            mapFragment.getMapAsync(this);
        }
    }

    private void setupListeners() {
        fabRefresh.setOnClickListener(v -> refreshLocation());
    }

    @Override
    public void onMapReady(@NonNull GoogleMap map) {
        this.googleMap = map;
        googleMap.getUiSettings().setZoomControlsEnabled(true);
        googleMap.getUiSettings().setMyLocationButtonEnabled(false);
        
        // Move zoom controls to bottom-left to avoid overlap with refresh FAB
        googleMap.setPadding(0, 0, 0, 0);
        try {
            // Get the zoom controls view and reposition it
            View mapView = getSupportFragmentManager().findFragmentById(R.id.map_fragment).getView();
            if (mapView != null) {
                View zoomControls = mapView.findViewWithTag("GoogleMapZoomInButton");
                if (zoomControls != null && zoomControls.getParent() instanceof View) {
                    View zoomLayout = (View) zoomControls.getParent();
                    if (zoomLayout.getLayoutParams() instanceof android.widget.RelativeLayout.LayoutParams) {
                        android.widget.RelativeLayout.LayoutParams params = 
                            (android.widget.RelativeLayout.LayoutParams) zoomLayout.getLayoutParams();
                        params.addRule(android.widget.RelativeLayout.ALIGN_PARENT_END, 0);
                        params.addRule(android.widget.RelativeLayout.ALIGN_PARENT_START);
                        params.setMargins(16, 0, 0, 0);
                        zoomLayout.setLayoutParams(params);
                    }
                }
            }
        } catch (Exception e) {
            // Fallback: just use padding to push controls away from FAB
            googleMap.setPadding(0, 0, 200, 0);
        }

        // Start listening to location updates
        startLocationListener();
    }

    private void startLocationListener() {
        showLoading(true);

        locationListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                showLoading(false);

                if (snapshot.exists()) {
                    try {
                        Double latitude = snapshot.child("latitude").getValue(Double.class);
                        Double longitude = snapshot.child("longitude").getValue(Double.class);
                        Float accuracy = snapshot.child("accuracy").getValue(Float.class);
                        Long timestamp = snapshot.child("timestamp").getValue(Long.class);

                        if (latitude != null && longitude != null) {
                            currentLatitude = latitude;
                            currentLongitude = longitude;

                            updateMapMarker(latitude, longitude);

                            // Update UI
                            if (timestamp != null) {
                                String timeText = formatTimestamp(timestamp);
                                tvLastUpdate.setText("Last updated: " + timeText);
                            }

                            if (accuracy != null) {
                                tvAccuracy.setText(String.format(Locale.getDefault(),
                                        "Accuracy: ±%.0fm", accuracy));
                            }
                        }
                    } catch (Exception e) {
                        Toast.makeText(LocationMapActivity.this,
                                "Error parsing location data", Toast.LENGTH_SHORT).show();
                    }
                } else {
                    Toast.makeText(LocationMapActivity.this,
                            "No location data available yet", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                showLoading(false);
                Toast.makeText(LocationMapActivity.this,
                        "Error: " + error.getMessage(), Toast.LENGTH_SHORT).show();
            }
        };

        locationRef.addValueEventListener(locationListener);
    }

    private void updateMapMarker(double latitude, double longitude) {
        if (googleMap == null) return;

        LatLng location = new LatLng(latitude, longitude);

        // Clear existing markers
        googleMap.clear();

        // Add marker
        String markerTitle = childName != null ? childName : "Child";
        googleMap.addMarker(new MarkerOptions()
                .position(location)
                .title(markerTitle + "'s Location"));

        // Move camera
        googleMap.animateCamera(CameraUpdateFactory.newLatLngZoom(location, 15f));
    }

    private void refreshLocation() {
        Toast.makeText(this, "Refreshing location...", Toast.LENGTH_SHORT).show();
        // Location will auto-refresh via ValueEventListener
        showLoading(true);

        // Hide loading after 2 seconds
        fabRefresh.postDelayed(() -> showLoading(false), 2000);
    }

    private String formatTimestamp(long timestamp) {
        long now = System.currentTimeMillis();
        long diff = now - timestamp;

        long seconds = diff / 1000;
        long minutes = seconds / 60;
        long hours = minutes / 60;

        if (seconds < 60) {
            return "Just now";
        } else if (minutes < 60) {
            return minutes + " min ago";
        } else if (hours < 24) {
            return hours + " hour" + (hours > 1 ? "s" : "") + " ago";
        } else {
            SimpleDateFormat sdf = new SimpleDateFormat("MMM dd, hh:mm a", Locale.getDefault());
            return sdf.format(new Date(timestamp));
        }
    }

    private void showLoading(boolean show) {
        progressBar.setVisibility(show ? View.VISIBLE : View.GONE);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (locationRef != null && locationListener != null) {
            locationRef.removeEventListener(locationListener);
        }
    }
}