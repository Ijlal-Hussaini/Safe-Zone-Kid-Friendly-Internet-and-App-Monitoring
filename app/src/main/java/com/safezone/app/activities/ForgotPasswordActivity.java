package com.safezone.app.activities;

import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.safezone.app.R;
import com.safezone.app.utils.FirebaseHelper;
import com.safezone.app.utils.ValidationUtils;

public class ForgotPasswordActivity extends AppCompatActivity {

    private EditText etEmail;
    private Button btnSendResetLink;
    private TextView tvBackToLogin;
    private ProgressBar progressBar;

    private FirebaseAuth mAuth;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_forgot_password);

        // Initialize Firebase
        mAuth = FirebaseHelper.getAuth();

        // Initialize views
        initViews();

        // Set click listeners
        btnSendResetLink.setOnClickListener(v -> validateAndSendResetLink());
        tvBackToLogin.setOnClickListener(v -> finish());
    }

    private void initViews() {
        etEmail = findViewById(R.id.etEmail);
        btnSendResetLink = findViewById(R.id.btnSendResetLink);
        tvBackToLogin = findViewById(R.id.tvBackToLogin);
        progressBar = findViewById(R.id.progressBar);
    }

    private void validateAndSendResetLink() {
        String email = etEmail.getText().toString().trim();

        // Reset error
        etEmail.setError(null);

        // Validate email
        if (!ValidationUtils.isValidEmail(email)) {
            etEmail.setError(getString(R.string.error_invalid_email));
            etEmail.requestFocus();
            return;
        }

        // Send reset link
        sendPasswordResetEmail(email);
    }

    private void sendPasswordResetEmail(String email) {
        showProgress(true);

        // Send password reset email via Firebase
        mAuth.sendPasswordResetEmail(email)
                .addOnCompleteListener(task -> {
                    showProgress(false);

                    if (task.isSuccessful()) {
                        // Success
                        Toast.makeText(this,
                                "Reset link sent to your email. Please check your inbox",
                                Toast.LENGTH_SHORT).show();

                        // Go back to login after 2 seconds
                        new android.os.Handler().postDelayed(() -> finish(), 2000);

                    } else {
                        // Failed
                        String errorMessage = task.getException() != null ?
                                task.getException().getMessage() : "Failed to send reset link";

                        if (errorMessage.contains("no user record") || errorMessage.contains("USER_NOT_FOUND") || 
                            errorMessage.contains("user-not-found")) {
                            Toast.makeText(this,
                                    "This email is not registered. Please register first",
                                    Toast.LENGTH_SHORT).show();
                        } else if (errorMessage.contains("TOO_MANY_REQUESTS") || 
                                 errorMessage.contains("too many requests") || 
                                 errorMessage.contains("blocked")) {
                            Toast.makeText(this,
                                    "Too many requests. Please wait a few minutes before requesting another reset link",
                                    Toast.LENGTH_SHORT).show();
                        } else if (errorMessage.contains("network")) {
                            Toast.makeText(this,
                                    "Network error. Please check your internet connection",
                                    Toast.LENGTH_SHORT).show();
                        } else {
                            Toast.makeText(this,
                                    "Failed to send reset link. Please try again later",
                                    Toast.LENGTH_SHORT).show();
                        }
                    }
                });
    }

    private void showProgress(boolean show) {
        progressBar.setVisibility(show ? View.VISIBLE : View.GONE);
        btnSendResetLink.setEnabled(!show);
        btnSendResetLink.setText(show ? "Sending..." : "Send Reset Link");
    }
}