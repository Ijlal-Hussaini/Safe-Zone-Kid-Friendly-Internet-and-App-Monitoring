package com.safezone.app.activities;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import android.content.SharedPreferences;

import com.safezone.app.R;
import com.safezone.app.models.ChildUser;
import com.safezone.app.models.ParentUser;
import com.safezone.app.utils.FirebaseHelper;
import com.safezone.app.utils.SharedPrefsHelper;
import com.safezone.app.utils.ValidationUtils;

public class LoginActivity extends AppCompatActivity {

    private EditText etEmail, etPassword;
    private CheckBox cbRememberMe;
    private Button btnLogin;
    private TextView tvForgotPassword, tvRegister;
    private ProgressBar progressBar;

    private FirebaseAuth mAuth;
    private SharedPrefsHelper prefsHelper;

    private String selectedRole; // role chosen from RoleSelectionActivity

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        mAuth = FirebaseHelper.getAuth();
        prefsHelper = new SharedPrefsHelper(this);

        // Get role selected from previous screen (may be null if someone opened login directly)
        selectedRole = getIntent().getStringExtra("role");

        initViews();
        prefillRememberedEmail();

        btnLogin.setOnClickListener(v -> validateAndLogin());
        tvForgotPassword.setOnClickListener(v -> navigateToForgotPassword());
        tvRegister.setOnClickListener(v -> navigateToRegister());
    }

    @Override
    protected void onStart() {
        super.onStart();

        // Ensure no cached user remains authenticated when showing login screen
        if (mAuth == null) {
            mAuth = FirebaseHelper.getAuth();
        }

        if (mAuth.getCurrentUser() != null) {
            mAuth.signOut();
            // also clear any saved session
            if (prefsHelper != null) prefsHelper.clearSession();
        }

        // Ensure we still have selectedRole (user might have navigated back without it)
        if (selectedRole == null || selectedRole.trim().isEmpty()) {
            // Try to re-read from intent (in case activity was recreated)
            selectedRole = getIntent().getStringExtra("role");
        }

        if (selectedRole == null || selectedRole.trim().isEmpty()) {
            // Force role selection first
            Toast.makeText(this, "Please select Parent or Child", Toast.LENGTH_SHORT).show();
            Intent i = new Intent(this, RoleSelectionActivity.class);
            // Clear stack so user can't bypass role selection
            i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(i);
            finish();
        }
    }

    private void initViews() {
        etEmail = findViewById(R.id.etEmail);
        etPassword = findViewById(R.id.etPassword);
        cbRememberMe = findViewById(R.id.cbRememberMe);
        btnLogin = findViewById(R.id.btnLogin);
        tvForgotPassword = findViewById(R.id.tvForgotPassword);
        tvRegister = findViewById(R.id.tvRegister);
        progressBar = findViewById(R.id.progressBar);
    }

    private void prefillRememberedEmail() {
        if (prefsHelper.getRememberMe()) {
            String email = prefsHelper.getUserEmail();
            if (email != null) {
                etEmail.setText(email);
                cbRememberMe.setChecked(true);
            }
        }
    }

    private void validateAndLogin() {
        String email = etEmail.getText().toString().trim();
        String password = etPassword.getText().toString().trim();

        if (email.isEmpty() || password.isEmpty()) {
            Toast.makeText(this, "Please fill in all fields", Toast.LENGTH_SHORT).show();
            return;
        }

        if (!ValidationUtils.isValidEmail(email)) {
            Toast.makeText(this, "Please enter a valid email", Toast.LENGTH_SHORT).show();
            return;
        }

        loginUser(email, password);
    }

    private void loginUser(String email, String password) {
        showProgress(true);

        mAuth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        FirebaseUser user = mAuth.getCurrentUser();
                        if (user != null) {
                            if (!user.isEmailVerified()) {
                                showProgress(false);
                                mAuth.signOut();
                                Toast.makeText(this, "Please verify your email first", Toast.LENGTH_SHORT).show();
                                return;
                            }

                            prefsHelper.setRememberMe(cbRememberMe.isChecked());
                            if (!cbRememberMe.isChecked()) {
                                prefsHelper.clearRememberedEmail();
                            }

                            FirebaseHelper.getUserRef(user.getUid()).get()
                                    .addOnSuccessListener(snapshot -> {
                                        if (snapshot.exists()) {
                                            showProgress(false);
                                            String name = snapshot.child("name").getValue(String.class);
                                            String role = snapshot.child("role").getValue(String.class);

                                            // Ensure selectedRole still exists; otherwise force role selection
                                            if (selectedRole == null || selectedRole.trim().isEmpty()) {
                                                mAuth.signOut();
                                                prefsHelper.clearSession();
                                                Toast.makeText(this, "Please select Parent or Child", Toast.LENGTH_SHORT).show();
                                                Intent i = new Intent(this, RoleSelectionActivity.class);
                                                i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                                                startActivity(i);
                                                finish();
                                                return;
                                            }

                                            // Role mismatch handling
                                            if (role != null && !selectedRole.equals(role)) {
                                                if ("parent".equals(role)) {
                                                    Toast.makeText(this, "This account belongs to a Parent.", Toast.LENGTH_SHORT).show();
                                                } else if ("child".equals(role)) {
                                                    Toast.makeText(this, "This account belongs to a Child.", Toast.LENGTH_SHORT).show();
                                                } else {
                                                    Toast.makeText(this, "Invalid account role.", Toast.LENGTH_SHORT).show();
                                                }

                                                // Sign out & stop login
                                                mAuth.signOut();
                                                prefsHelper.clearSession();
                                                return;
                                            }

                                            // Roles match -> continue as before
                                            prefsHelper.saveUserSession(user.getUid(), name, email, role);
                                            Toast.makeText(this, "Welcome " + (name != null ? name : "User"), Toast.LENGTH_SHORT).show();
                                            navigateBasedOnRole(role);
                                        } else {
                                            // User data not in database - check for pending registration
                                            savePendingUserToDatabase(user, email);
                                        }
                                    })
                                    .addOnFailureListener(e -> {
                                        showProgress(false);
                                        Toast.makeText(this, "Failed to load user data. Please try again", Toast.LENGTH_SHORT).show();
                                        mAuth.signOut();
                                    });
                        }
                    } else {
                        showProgress(false);
                        handleLoginError(task.getException());
                    }
                });
    }

    private void handleLoginError(Exception exception) {
        String errorCode = "";
        String errorMessage = "";

        if (exception != null) {
            errorMessage = exception.getMessage();
            if (exception instanceof com.google.firebase.auth.FirebaseAuthException) {
                errorCode = ((com.google.firebase.auth.FirebaseAuthException) exception).getErrorCode();
            }
        }

        // For security, show generic message for invalid email OR password
        if (errorCode.equals("ERROR_USER_NOT_FOUND") ||
                errorCode.equals("ERROR_WRONG_PASSWORD") ||
                errorCode.equals("ERROR_INVALID_CREDENTIAL") ||
                (errorMessage != null && (errorMessage.contains("no user") ||
                        errorMessage.contains("USER_NOT_FOUND") ||
                        errorMessage.contains("user-not-found") ||
                        errorMessage.contains("wrong-password") ||
                        errorMessage.contains("invalid-credential") ||
                        errorMessage.contains("INVALID_PASSWORD")))) {
            Toast.makeText(this, "Invalid email or password", Toast.LENGTH_SHORT).show();
        } else if (errorCode.equals("ERROR_NETWORK_REQUEST_FAILED") ||
                (errorMessage != null && errorMessage.contains("network"))) {
            Toast.makeText(this, "Network error. Check your internet", Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(this, "Login failed. Try again", Toast.LENGTH_SHORT).show();
        }
    }

    private void navigateBasedOnRole(String role) {
        Intent intent;

        if ("parent".equals(role)) {
            intent = new Intent(this, ParentDashboardActivity.class);
        } else if ("child".equals(role)) {
            intent = new Intent(this, ChildDashboardActivity.class);
        } else {
            Toast.makeText(this, "Invalid user role", Toast.LENGTH_SHORT).show();
            mAuth.signOut();
            prefsHelper.clearSession();
            return;
        }

        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    private void navigateToForgotPassword() {
        Intent intent = new Intent(this, ForgotPasswordActivity.class);
        startActivity(intent);
    }

    private void navigateToRegister() {
        Intent intent = new Intent(this, RegisterActivity.class);
        intent.putExtra("role", selectedRole); // keep the chosen role
        startActivity(intent);
        // Do NOT finish() - we want to return to this instance so selectedRole stays intact
    }

    private void savePendingUserToDatabase(FirebaseUser user, String email) {
        // Check for pending registration data saved during registration
        SharedPreferences pendingPrefs = getSharedPreferences("pending_registration", MODE_PRIVATE);
        String pendingUid = pendingPrefs.getString("uid", null);
        String pendingName = pendingPrefs.getString("name", null);
        String pendingEmail = pendingPrefs.getString("email", null);
        String pendingRole = pendingPrefs.getString("role", null);
        int pendingAge = pendingPrefs.getInt("age", 0);

        // Verify the pending data matches current user
        if (pendingUid != null && pendingUid.equals(user.getUid()) && pendingName != null && pendingRole != null) {
            // Role mismatch check
            if (!selectedRole.equals(pendingRole)) {
                showProgress(false);
                if ("parent".equals(pendingRole)) {
                    Toast.makeText(this, "This account belongs to a Parent.", Toast.LENGTH_SHORT).show();
                } else if ("child".equals(pendingRole)) {
                    Toast.makeText(this, "This account belongs to a Child.", Toast.LENGTH_SHORT).show();
                }
                mAuth.signOut();
                prefsHelper.clearSession();
                return;
            }

            // Save user to database now that email is verified
            if ("parent".equals(pendingRole)) {
                ParentUser parentUser = new ParentUser(pendingUid, pendingName, pendingEmail);
                FirebaseHelper.getUserRef(pendingUid).setValue(parentUser)
                        .addOnSuccessListener(aVoid -> {
                            showProgress(false);
                            // Clear pending data
                            pendingPrefs.edit().clear().apply();
                            // Complete login
                            prefsHelper.saveUserSession(user.getUid(), pendingName, email, pendingRole);
                            Toast.makeText(this, "Welcome " + pendingName, Toast.LENGTH_SHORT).show();
                            navigateBasedOnRole(pendingRole);
                        })
                        .addOnFailureListener(e -> {
                            showProgress(false);
                            Toast.makeText(this, "Failed to save user data. Please try again", Toast.LENGTH_SHORT).show();
                            mAuth.signOut();
                        });
            } else {
                ChildUser childUser = new ChildUser(pendingUid, pendingName, pendingEmail, pendingAge);
                FirebaseHelper.getUserRef(pendingUid).setValue(childUser)
                        .addOnSuccessListener(aVoid -> {
                            showProgress(false);
                            // Clear pending data
                            pendingPrefs.edit().clear().apply();
                            // Complete login
                            prefsHelper.saveUserSession(user.getUid(), pendingName, email, pendingRole);
                            Toast.makeText(this, "Welcome " + pendingName, Toast.LENGTH_SHORT).show();
                            navigateBasedOnRole(pendingRole);
                        })
                        .addOnFailureListener(e -> {
                            showProgress(false);
                            Toast.makeText(this, "Failed to save user data. Please try again", Toast.LENGTH_SHORT).show();
                            mAuth.signOut();
                        });
            }
        } else {
            // No pending data found - user needs to register again
            showProgress(false);
            Toast.makeText(this, "User data not found. Please register again", Toast.LENGTH_SHORT).show();
            mAuth.signOut();
        }
    }

    private void showProgress(boolean show) {
        progressBar.setVisibility(show ? View.VISIBLE : View.GONE);
        btnLogin.setEnabled(!show);
    }
}
