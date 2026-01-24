package com.safezone.app.activities;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.safezone.app.R;
import com.safezone.app.utils.FirebaseHelper;

public class EmailVerificationInfoActivity extends AppCompatActivity {

    private TextView tvEmail;
    private Button btnContinue, btnResend;
    private ProgressBar progressBar;
    private FirebaseAuth mAuth;
    private String email;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_email_verification_info);

        // Initialize Firebase
        mAuth = FirebaseHelper.getAuth();

        // Get email from intent
        email = getIntent().getStringExtra("email");

        // Initialize views
        initViews();

        // Display email
        if (email != null) {
            tvEmail.setText(email);
        }

        // Set click listeners
        btnContinue.setOnClickListener(v -> checkVerificationAndContinue());
        btnResend.setOnClickListener(v -> resendVerificationEmail());
    }

    @Override
    public void onBackPressed() {
        // When user goes back without verifying, delete the unverified account
        // so they can register again with the same email
        deleteUnverifiedAccountAndGoBack();
    }

    private void deleteUnverifiedAccountAndGoBack() {
        FirebaseUser user = mAuth.getCurrentUser();
        if (user != null && !user.isEmailVerified()) {
            // Delete the unverified auth account
            user.delete().addOnCompleteListener(task -> {
                // Clear pending registration data
                clearPendingRegistrationData();
                // Navigate to role selection
                navigateToRoleSelection();
            });
        } else {
            clearPendingRegistrationData();
            navigateToRoleSelection();
        }
    }

    private void clearPendingRegistrationData() {
        SharedPreferences pendingPrefs = getSharedPreferences("pending_registration", MODE_PRIVATE);
        pendingPrefs.edit().clear().apply();
    }

    private void navigateToRoleSelection() {
        Intent intent = new Intent(this, RoleSelectionActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    private void initViews() {
        tvEmail = findViewById(R.id.tvEmail);
        btnContinue = findViewById(R.id.btnContinue);
        btnResend = findViewById(R.id.btnResend);
        progressBar = findViewById(R.id.progressBar);
    }

    private void checkVerificationAndContinue() {
        showProgress(true);

        FirebaseUser user = mAuth.getCurrentUser();
        if (user != null) {
            // Reload user to get latest verification status
            user.reload().addOnCompleteListener(task -> {
                FirebaseUser updatedUser = mAuth.getCurrentUser();
                if (updatedUser != null && updatedUser.isEmailVerified()) {
                    // Email verified - sign out and navigate to login
                    showProgress(false);
                    Toast.makeText(this, "Email verified successfully! Please login", Toast.LENGTH_SHORT).show();
                    
                    // Get the role from pending registration data
                    SharedPreferences pendingPrefs = getSharedPreferences("pending_registration", MODE_PRIVATE);
                    String role = pendingPrefs.getString("role", "parent");
                    
                    // Sign out so user can login fresh
                    mAuth.signOut();
                    
                    Intent intent = new Intent(this, LoginActivity.class);
                    intent.putExtra("role", role);
                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    startActivity(intent);
                    finish();
                } else {
                    // Email not yet verified
                    showProgress(false);
                    Toast.makeText(this, "Email not yet verified. Please check your inbox and click the verification link", Toast.LENGTH_LONG).show();
                }
            }).addOnFailureListener(e -> {
                showProgress(false);
                Toast.makeText(this, "Error checking verification status. Please try again", Toast.LENGTH_SHORT).show();
            });
        } else {
            showProgress(false);
            Toast.makeText(this, "Session expired. Please register again", Toast.LENGTH_SHORT).show();
            clearPendingRegistrationData();
            navigateToRoleSelection();
        }
    }

    private void resendVerificationEmail() {
        showProgress(true);

        FirebaseUser user = mAuth.getCurrentUser();
        if (user != null) {
            user.sendEmailVerification()
                    .addOnCompleteListener(task -> {
                        showProgress(false);
                        if (task.isSuccessful()) {
                            Toast.makeText(this, "Verification link sent to your email", Toast.LENGTH_SHORT).show();
                        } else {
                            Exception exception = task.getException();
                            String errorMessage = "";
                            if (exception != null) {
                                errorMessage = exception.getMessage();
                            }
                            
                            // Handle rate limiting
                            if (errorMessage != null && (errorMessage.contains("TOO_MANY_REQUESTS") || 
                                    errorMessage.contains("too many requests") || 
                                    errorMessage.contains("blocked") ||
                                    errorMessage.contains("rate limit"))) {
                                Toast.makeText(this, "Too many requests. Please wait a few minutes before requesting another verification email", Toast.LENGTH_LONG).show();
                            } else if (errorMessage != null && errorMessage.contains("network")) {
                                Toast.makeText(this, "Network error. Please check your internet connection", Toast.LENGTH_SHORT).show();
                            } else {
                                Toast.makeText(this, "Failed to send verification email. Please try again later", Toast.LENGTH_SHORT).show();
                            }
                        }
                    });
        } else {
            showProgress(false);
            Toast.makeText(this, "Session expired. Please register again", Toast.LENGTH_SHORT).show();
            clearPendingRegistrationData();
            navigateToRoleSelection();
        }
    }

    private void showProgress(boolean show) {
        progressBar.setVisibility(show ? View.VISIBLE : View.GONE);
        btnContinue.setEnabled(!show);
        btnResend.setEnabled(!show);
    }
}

