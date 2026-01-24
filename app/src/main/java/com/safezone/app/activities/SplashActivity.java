package com.safezone.app.activities;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.view.animation.ScaleAnimation;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;

import com.safezone.app.R;
import com.safezone.app.utils.FirebaseHelper;
import com.safezone.app.utils.SharedPrefsHelper;

/**
 * Splash Activity - Entry point of the app
 * UPDATED: Now applies saved theme on startup
 */
public class SplashActivity extends AppCompatActivity {

    private static final int SPLASH_DURATION = 2500; // 2.5 seconds
    private SharedPrefsHelper prefsHelper;

    private ImageView logoImageView;
    private TextView appNameTextView;
    private TextView taglineTextView;
    private LinearLayout loaderContainer;
    private View dot1, dot2, dot3;
    private Handler dotAnimationHandler;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // Apply saved theme BEFORE calling super.onCreate()
        applyTheme();

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);

        // Initialize views
        initViews();

        // Initialize Firebase
        FirebaseHelper.initialize();

        // Initialize SharedPreferences
        prefsHelper = new SharedPrefsHelper(this);
        
        // Clear permissions skipped flag on fresh app start
        // This ensures permissions page shows again on next app restart
        prefsHelper.clearPermissionsSkippedFlag();

        // Start animations
        startAnimations();

        // Navigate after delay
        new Handler(Looper.getMainLooper()).postDelayed(this::navigateToNextScreen, SPLASH_DURATION);
    }

    /**
     * Apply saved theme preference
     * Must be called BEFORE super.onCreate()
     */
    private void applyTheme() {
        try {
            SharedPreferences prefs = getSharedPreferences("AppSettings", MODE_PRIVATE);
            boolean isDarkMode = prefs.getBoolean("theme_mode", false);

            if (isDarkMode) {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
            } else {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
            }
        } catch (Exception e) {
            // Default to light mode if error
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
        }
    }

    private void initViews() {
        logoImageView = findViewById(R.id.logoImageView);
        appNameTextView = findViewById(R.id.appNameTextView);
        taglineTextView = findViewById(R.id.taglineTextView);
        loaderContainer = findViewById(R.id.loaderContainer);
        dot1 = findViewById(R.id.dot1);
        dot2 = findViewById(R.id.dot2);
        dot3 = findViewById(R.id.dot3);
        dotAnimationHandler = new Handler(Looper.getMainLooper());
    }

    private void startAnimations() {
        // Logo animation - scale and fade in
        Animation logoAnimation = AnimationUtils.loadAnimation(this, R.anim.logo_fade_in_scale);
        logoImageView.startAnimation(logoAnimation);
        logoImageView.setVisibility(View.VISIBLE);

        // App name animation - fade in and slide up
        Animation appNameAnimation = AnimationUtils.loadAnimation(this, R.anim.text_fade_in);
        appNameTextView.startAnimation(appNameAnimation);
        appNameTextView.setAlpha(1f);

        // Tagline animation - fade in
        Animation taglineAnimation = AnimationUtils.loadAnimation(this, R.anim.tagline_fade_in);
        taglineTextView.startAnimation(taglineAnimation);
        taglineTextView.setAlpha(0.9f);

        // Loader container animation - fade in
        Animation progressAnimation = AnimationUtils.loadAnimation(this, R.anim.progress_fade_in);
        loaderContainer.startAnimation(progressAnimation);
        loaderContainer.setAlpha(1f);
        
        // Start dot bounce animation
        startDotAnimation();
    }
    
    /**
     * Animate the three dots with a wave/bounce effect
     */
    private void startDotAnimation() {
        animateDot(dot1, 0);
        animateDot(dot2, 150);
        animateDot(dot3, 300);
    }
    
    private void animateDot(View dot, int delay) {
        dotAnimationHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                ScaleAnimation scaleUp = new ScaleAnimation(
                        1f, 1.4f, 1f, 1.4f,
                        Animation.RELATIVE_TO_SELF, 0.5f,
                        Animation.RELATIVE_TO_SELF, 0.5f);
                scaleUp.setDuration(300);
                scaleUp.setRepeatMode(Animation.REVERSE);
                scaleUp.setRepeatCount(1);
                
                dot.startAnimation(scaleUp);
                
                // Repeat the animation
                dotAnimationHandler.postDelayed(this, 900);
            }
        }, delay);
    }

    /**
     * Navigate to next screen based on login status and user role
     */
    private void navigateToNextScreen() {
        Intent intent;

        // Check if user is already logged in
        if (prefsHelper.isLoggedIn() && FirebaseHelper.isUserAuthenticated()) {
            // User is logged in, check role and navigate to respective dashboard
            String role = prefsHelper.getUserRole();

            if ("parent".equals(role)) {
                // Navigate to Parent Dashboard
                intent = new Intent(this, ParentDashboardActivity.class);
            } else if ("child".equals(role)) {
                // Navigate to Child Dashboard
                intent = new Intent(this, ChildDashboardActivity.class);
            } else {
                // Role not found, go to role selection
                intent = new Intent(this, RoleSelectionActivity.class);
            }
        } else {
            // User not logged in, go to role selection
            intent = new Intent(this, RoleSelectionActivity.class);
        }

        // Clear the back stack
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Clean up animation handler
        if (dotAnimationHandler != null) {
            dotAnimationHandler.removeCallbacksAndMessages(null);
        }
    }
}