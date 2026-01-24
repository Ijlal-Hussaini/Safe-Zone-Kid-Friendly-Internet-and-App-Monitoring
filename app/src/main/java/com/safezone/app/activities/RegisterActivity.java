package com.safezone.app.activities;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.safezone.app.R;
import com.safezone.app.models.ChildUser;
import com.safezone.app.models.ParentUser;
import com.safezone.app.utils.FirebaseHelper;
import com.safezone.app.utils.ValidationUtils;

public class RegisterActivity extends AppCompatActivity {

    private EditText etName, etEmail, etPassword, etConfirmPassword, etAge;
    private Button btnRegister;
    private TextView tvLogin, tvRoleTitle;
    private ProgressBar progressBar;
    private View ageContainer;

    private String role;
    private FirebaseAuth mAuth;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register);

        // Initialize Firebase
        mAuth = FirebaseHelper.getAuth();

        // Get role from intent
        role = getIntent().getStringExtra("role");
        if (role == null) role = "parent";

        // Initialize views
        initViews();

        // Setup UI based on role
        setupRoleUI();

        // Set click listeners
        btnRegister.setOnClickListener(v -> validateAndRegister());

        // IMPORTANT: instead of starting LoginActivity, simply finish()
        // so we return to the LoginActivity instance that holds selectedRole
        tvLogin.setOnClickListener(v -> navigateToLogin());
    }

    private void initViews() {
        tvRoleTitle = findViewById(R.id.tvRoleTitle);
        etName = findViewById(R.id.etName);
        etEmail = findViewById(R.id.etEmail);
        etPassword = findViewById(R.id.etPassword);
        etConfirmPassword = findViewById(R.id.etConfirmPassword);
        etAge = findViewById(R.id.etAge);
        ageContainer = findViewById(R.id.ageContainer);
        btnRegister = findViewById(R.id.btnRegister);
        tvLogin = findViewById(R.id.tvLogin);
        progressBar = findViewById(R.id.progressBar);
    }

    private void setupRoleUI() {
        if ("child".equals(role)) {
            tvRoleTitle.setText("Register as Child");
            ageContainer.setVisibility(View.VISIBLE);
        } else {
            tvRoleTitle.setText("Register as Parent");
            ageContainer.setVisibility(View.GONE);
        }
    }

    private void validateAndRegister() {
        String name = etName.getText().toString().trim();
        String email = etEmail.getText().toString().trim();
        String password = etPassword.getText().toString().trim();
        String confirmPassword = etConfirmPassword.getText().toString().trim();
        String ageStr = etAge.getText().toString().trim();

        // Reset errors
        etName.setError(null);
        etEmail.setError(null);
        etPassword.setError(null);
        etConfirmPassword.setError(null);
        etAge.setError(null);

        // Validate name
        if (!ValidationUtils.isValidName(name)) {
            Toast.makeText(this, "Please enter your name", Toast.LENGTH_SHORT).show();
            etName.requestFocus();
            return;
        }

        // Validate email
        if (!ValidationUtils.isValidEmail(email)) {
            Toast.makeText(this, "Please enter a valid email", Toast.LENGTH_SHORT).show();
            etEmail.requestFocus();
            return;
        }

        // Validate password
        String passwordError = ValidationUtils.getPasswordError(password);
        if (passwordError != null) {
            Toast.makeText(this, passwordError, Toast.LENGTH_LONG).show();
            etPassword.requestFocus();
            return;
        }

        // Check if passwords match
        if (!ValidationUtils.doPasswordsMatch(password, confirmPassword)) {
            Toast.makeText(this, "Passwords do not match", Toast.LENGTH_SHORT).show();
            etConfirmPassword.requestFocus();
            return;
        }

        // Validate age for child
        int age = 0;
        if ("child".equals(role)) {
            if (TextUtils.isEmpty(ageStr)) {
                Toast.makeText(this, "Please enter age", Toast.LENGTH_SHORT).show();
                etAge.requestFocus();
                return;
            }
            try {
                age = Integer.parseInt(ageStr);
                if (!ValidationUtils.isValidAge(age)) {
                    Toast.makeText(this, "Age must be between 1 and 18", Toast.LENGTH_SHORT).show();
                    etAge.requestFocus();
                    return;
                }
            } catch (NumberFormatException e) {
                Toast.makeText(this, "Invalid age", Toast.LENGTH_SHORT).show();
                etAge.requestFocus();
                return;
            }
        }

        // All validations passed
        registerUser(name, email, password, age);
    }

    private void registerUser(String name, String email, String password, int age) {
        showProgress(true);

        // Check if email already exists
        mAuth.fetchSignInMethodsForEmail(email)
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful() && task.getResult().getSignInMethods() != null
                            && !task.getResult().getSignInMethods().isEmpty()) {
                        showProgress(false);
                        Toast.makeText(this, "Email already registered. Please log in", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    // Create account
                    createUserAccount(name, email, password, age);
                });
    }

    private void createUserAccount(String name, String email, String password, int age) {
        mAuth.createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        FirebaseUser user = mAuth.getCurrentUser();
                        if (user != null) {
                            // Send email verification
                            sendEmailVerification(user, name, email, age);
                        }
                    } else {
                        showProgress(false);
                        String error = task.getException() != null ? task.getException().getMessage() : "";

                        if (error.contains("already in use") || error.contains("EMAIL_EXISTS")) {
                            Toast.makeText(this, "Email already registered. Please log in", Toast.LENGTH_SHORT).show();
                        } else if (error.contains("weak password") || error.contains("WEAK_PASSWORD")) {
                            Toast.makeText(this, "Password is too weak. Please use a stronger password", Toast.LENGTH_SHORT).show();
                        } else if (error.contains("network")) {
                            Toast.makeText(this, "Network error. Please check your internet connection", Toast.LENGTH_SHORT).show();
                        } else {
                            Toast.makeText(this, "Registration failed. Please try again", Toast.LENGTH_SHORT).show();
                        }
                    }
                });
    }

    private void sendEmailVerification(FirebaseUser user, String name, String email, int age) {
        user.sendEmailVerification()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        // DON'T save user to database yet - only save after email is verified
                        // Store registration data temporarily in SharedPreferences for later use
                        savePendingRegistrationData(user.getUid(), name, email, age);
                        showProgress(false);
                        // DON'T sign out - keep user signed in so they can resend verification
                        navigateToEmailVerificationInfo(email);
                    } else {
                        // If verification email fails, delete the created auth account
                        user.delete();
                        showProgress(false);
                        Exception exception = task.getException();
                        String errorMessage = "";
                        if (exception != null) {
                            errorMessage = exception.getMessage();
                        }

                        if (errorMessage != null && (errorMessage.contains("TOO_MANY_REQUESTS") ||
                                errorMessage.contains("too many requests") ||
                                errorMessage.contains("blocked"))) {
                            Toast.makeText(this, "Too many requests. Please wait a few minutes and try again", Toast.LENGTH_SHORT).show();
                        } else if (errorMessage != null && errorMessage.contains("network")) {
                            Toast.makeText(this, "Network error. Please check your internet connection", Toast.LENGTH_SHORT).show();
                        } else {
                            Toast.makeText(this, "Failed to send verification email. Please try again later", Toast.LENGTH_SHORT).show();
                        }
                    }
                });
    }

    private void savePendingRegistrationData(String uid, String name, String email, int age) {
        // Save pending registration data to SharedPreferences
        // This will be used to create the database entry after email verification
        getSharedPreferences("pending_registration", MODE_PRIVATE)
                .edit()
                .putString("uid", uid)
                .putString("name", name)
                .putString("email", email)
                .putString("role", role)
                .putInt("age", age)
                .apply();
    }



    private void navigateToEmailVerificationInfo(String email) {
        Toast.makeText(this, "Verification link sent to your email", Toast.LENGTH_SHORT).show();
        Intent intent = new Intent(this, EmailVerificationInfoActivity.class);
        intent.putExtra("email", email);
        startActivity(intent);
        finish();
    }

    private void navigateToLogin() {
        // Return to previous LoginActivity instance which retains selectedRole
        finish();
    }

    private void showProgress(boolean show) {
        progressBar.setVisibility(show ? View.VISIBLE : View.GONE);
        btnRegister.setEnabled(!show);
    }
}
