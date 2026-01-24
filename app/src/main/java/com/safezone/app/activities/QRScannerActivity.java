package com.safezone.app.activities;

import android.Manifest;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.ValueEventListener;
import com.journeyapps.barcodescanner.BarcodeCallback;
import com.journeyapps.barcodescanner.BarcodeResult;
import com.journeyapps.barcodescanner.DecoratedBarcodeView;
import com.safezone.app.R;
import com.safezone.app.models.LinkToken;
import com.safezone.app.utils.FirebaseHelper;

/**
 * QR Scanner Activity for Child to scan parent's QR code
 */
public class QRScannerActivity extends AppCompatActivity {

    private static final int CAMERA_PERMISSION_REQUEST = 100;

    private DecoratedBarcodeView barcodeScanner;
    private ImageView btnClose;
    private MaterialButton btnEnterCode;
    private RelativeLayout progressOverlay;
    private TextView tvProgressMessage;

    private DatabaseReference linkTokensRef;
    private String currentChildUid;
    private boolean isScanning = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_qr_scanner);

        initViews();
        setupListeners();

        linkTokensRef = FirebaseHelper.getLinkTokensRef();
        currentChildUid = FirebaseHelper.getCurrentUserId();

        // Check if already linked before allowing scan
        checkIfAlreadyLinked();
    }

    private void checkIfAlreadyLinked() {
        DatabaseReference userRef = FirebaseHelper.getUsersRef().child(currentChildUid);

        userRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (snapshot.exists()) {
                    String parentId = snapshot.child("parentId").getValue(String.class);
                    if (parentId != null && !parentId.isEmpty()) {
                        // Check if parent actually has this child in their list
                        verifyParentLinkage(parentId);
                    } else {
                        // Not linked - proceed with scanning
                        if (checkCameraPermission()) {
                            startScanning();
                        } else {
                            requestCameraPermission();
                        }
                    }
                } else {
                    // User not found - proceed with scanning
                    if (checkCameraPermission()) {
                        startScanning();
                    } else {
                        requestCameraPermission();
                    }
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Toast.makeText(QRScannerActivity.this,
                        "Error checking link status", Toast.LENGTH_SHORT).show();
                finish();
            }
        });
    }

    private void verifyParentLinkage(String parentId) {
        // Verify that parent actually has this child in their children list
        FirebaseHelper.getUsersRef().child(parentId).child("children").child(currentChildUid)
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        if (snapshot.exists() && Boolean.TRUE.equals(snapshot.getValue(Boolean.class))) {
                            // Parent has this child - truly linked
                            showAlreadyLinkedDialog();
                        } else {
                            // Parent doesn't have this child - orphaned link, clean it up
                            cleanupOrphanedLink();
                        }
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        // On error, assume linked to be safe
                        showAlreadyLinkedDialog();
                    }
                });
    }

    private void cleanupOrphanedLink() {
        // Remove the orphaned parentId from child
        FirebaseHelper.getUsersRef().child(currentChildUid).child("parentId").removeValue()
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(this, "Previous link was invalid and has been cleared. You can now link to a parent.", 
                            Toast.LENGTH_LONG).show();
                    // Now proceed with scanning
                    if (checkCameraPermission()) {
                        startScanning();
                    } else {
                        requestCameraPermission();
                    }
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Error cleaning up old link. Please try again.", 
                            Toast.LENGTH_SHORT).show();
                    finish();
                });
    }

    private void showAlreadyLinkedDialog() {
        new AlertDialog.Builder(this)
                .setTitle("Already Linked")
                .setMessage("This device is already connected to a parent account. " +
                        "If you want to link to a different parent, you need to disconnect first from Settings.")
                .setPositiveButton("Go to Settings", (dialog, which) -> {
                    Intent intent = new Intent(this, SettingsActivity.class);
                    startActivity(intent);
                    finish();
                })
                .setNegativeButton("Cancel", (dialog, which) -> finish())
                .setCancelable(false)
                .show();
    }

    private void initViews() {
        barcodeScanner = findViewById(R.id.barcode_scanner);
        btnClose = findViewById(R.id.btn_close);
        btnEnterCode = findViewById(R.id.btn_enter_code);
        progressOverlay = findViewById(R.id.progress_overlay);
        tvProgressMessage = findViewById(R.id.tv_progress_message);
    }

    private void setupListeners() {
        btnClose.setOnClickListener(v -> finish());
        btnEnterCode.setOnClickListener(v -> showManualCodeDialog());
    }

    private boolean checkCameraPermission() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED;
    }

    private void requestCameraPermission() {
        ActivityCompat.requestPermissions(this,
                new String[]{Manifest.permission.CAMERA},
                CAMERA_PERMISSION_REQUEST);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == CAMERA_PERMISSION_REQUEST) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startScanning();
            } else {
                Toast.makeText(this, "Camera permission is required to scan QR code",
                        Toast.LENGTH_LONG).show();
                finish();
            }
        }
    }

    private void startScanning() {
        barcodeScanner.decodeContinuous(new BarcodeCallback() {
            @Override
            public void barcodeResult(BarcodeResult result) {
                if (isScanning && result.getText() != null) {
                    isScanning = false;
                    barcodeScanner.pause();
                    handleScannedCode(result.getText());
                }
            }
        });
    }

    private void handleScannedCode(String code) {
        showProgress(true, "Verifying code...");

        linkTokensRef.child(code).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (snapshot.exists()) {
                    LinkToken token = snapshot.getValue(LinkToken.class);
                    if (token != null) {
                        validateAndLinkToken(token, code);
                    } else {
                        showProgress(false, null);
                        showErrorDialog("Invalid Code", 
                                "This code format is not recognized.",
                                "Please scan a valid QR code from your parent's Safe Zone app.");
                    }
                } else {
                    showProgress(false, null);
                    showErrorDialog("Code Not Found", 
                            "The code you entered doesn't exist. Please check and try again.",
                            "Make sure you're entering the exact code shown on your parent's device.");
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                showProgress(false, null);
                showError("Error: " + error.getMessage());
                resumeScanning();
            }
        });
    }

    private void validateAndLinkToken(LinkToken token, String tokenId) {
        // Check if token is valid
        if (!token.isValid()) {
            showProgress(false, null);
            if (token.isExpired()) {
                showErrorDialog("Code Expired", 
                        "This code has expired and is no longer valid.",
                        "Ask your parent to generate a new QR code from their dashboard.");
            } else {
                showErrorDialog("Code Already Used", 
                        "This code has already been used to link a device.",
                        "Ask your parent to generate a new QR code for your device.");
            }
            return;
        }

        // Link child to parent
        tvProgressMessage.setText("Linking to parent...");
        linkChildToParent(token.getParentUid(), tokenId);
    }

    private void linkChildToParent(String parentUid, String tokenId) {
        DatabaseReference database = FirebaseHelper.getDatabase().getReference();

        // Get parent info from the token (more reliable than reading parent's data)
        linkTokensRef.child(tokenId).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot tokenSnapshot) {
                String parentEmail = tokenSnapshot.child("parentEmail").getValue(String.class);
                String parentName = tokenSnapshot.child("parentName").getValue(String.class);
                
                android.util.Log.d("QRScanner", "Token parent info - email: " + parentEmail + ", name: " + parentName);
                
                // Use atomic update to link both sides at once
                java.util.Map<String, Object> updates = new java.util.HashMap<>();
                updates.put("users/" + currentChildUid + "/parentId", parentUid);
                
                // Store parent email and name from token
                if (parentEmail != null && !parentEmail.isEmpty()) {
                    updates.put("users/" + currentChildUid + "/parentEmail", parentEmail);
                }
                if (parentName != null && !parentName.isEmpty()) {
                    updates.put("users/" + currentChildUid + "/parentName", parentName);
                }
                
                updates.put("users/" + parentUid + "/children/" + currentChildUid, true);
                updates.put("linkTokens/" + tokenId + "/used", true);
                updates.put("linkTokens/" + tokenId + "/childUid", currentChildUid);

                database.updateChildren(updates)
                        .addOnSuccessListener(aVoid -> {
                            showProgress(false, null);
                            showSuccessDialog();
                        })
                        .addOnFailureListener(e -> {
                            showProgress(false, null);
                            String errorMsg = e.getMessage();
                            if (errorMsg != null && errorMsg.contains("Permission denied")) {
                                showError("Permission denied. Please check:\n" +
                                        "1. Your account exists in the database\n" +
                                        "2. Firebase rules allow this operation\n" +
                                        "3. You're properly authenticated");
                            } else {
                                showError("Failed to link: " + errorMsg);
                            }
                            resumeScanning();
                        });
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                // Fallback: try to link without parent info
                java.util.Map<String, Object> updates = new java.util.HashMap<>();
                updates.put("users/" + currentChildUid + "/parentId", parentUid);
                updates.put("users/" + parentUid + "/children/" + currentChildUid, true);
                updates.put("linkTokens/" + tokenId + "/used", true);
                updates.put("linkTokens/" + tokenId + "/childUid", currentChildUid);

                database.updateChildren(updates)
                        .addOnSuccessListener(aVoid -> {
                            showProgress(false, null);
                            showSuccessDialog();
                        })
                        .addOnFailureListener(e -> {
                            showProgress(false, null);
                            showError("Failed to link: " + e.getMessage());
                            resumeScanning();
                        });
            }
        });
    }

    private void showManualCodeDialog() {
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_enter_link_code, null);
        TextInputEditText etCode = dialogView.findViewById(R.id.et_code);
        
        com.google.android.material.dialog.MaterialAlertDialogBuilder builder = 
            new com.google.android.material.dialog.MaterialAlertDialogBuilder(this, R.style.ThemeOverlay_App_MaterialAlertDialog);
        builder.setView(dialogView);
        builder.setBackground(getResources().getDrawable(R.drawable.dialog_background, getTheme()));
        builder.setCancelable(true);
        
        AlertDialog dialog = builder.create();
        
        dialogView.findViewById(R.id.btn_cancel).setOnClickListener(v -> dialog.dismiss());
        
        dialogView.findViewById(R.id.btn_link).setOnClickListener(v -> {
            String code = etCode.getText().toString().trim();
            if (!code.isEmpty()) {
                dialog.dismiss();
                isScanning = false;
                barcodeScanner.pause();
                handleScannedCode(code);
            } else {
                Toast.makeText(this, "Please enter a code", Toast.LENGTH_SHORT).show();
            }
        });
        
        dialog.show();
    }

    private void showSuccessDialog() {
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_link_success, null);
        
        com.google.android.material.dialog.MaterialAlertDialogBuilder builder = 
            new com.google.android.material.dialog.MaterialAlertDialogBuilder(this, R.style.ThemeOverlay_App_MaterialAlertDialog);
        builder.setView(dialogView);
        builder.setBackground(getResources().getDrawable(R.drawable.dialog_background, getTheme()));
        builder.setCancelable(false);
        
        AlertDialog dialog = builder.create();
        
        dialogView.findViewById(R.id.btn_ok).setOnClickListener(v -> {
            dialog.dismiss();
            setResult(RESULT_OK);
            finish();
        });
        
        dialog.show();
    }

    private void showError(String message) {
        showErrorDialog("Invalid Code", message, "Ask your parent to generate a new QR code and try again.");
    }
    
    private void showErrorDialog(String title, String message, String helpText) {
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_link_error, null);
        
        TextView tvTitle = dialogView.findViewById(R.id.tv_error_title);
        TextView tvMessage = dialogView.findViewById(R.id.tv_error_message);
        TextView tvHelp = dialogView.findViewById(R.id.tv_help_text);
        
        tvTitle.setText(title);
        tvMessage.setText(message);
        tvHelp.setText(helpText);
        
        com.google.android.material.dialog.MaterialAlertDialogBuilder builder = 
            new com.google.android.material.dialog.MaterialAlertDialogBuilder(this, R.style.ThemeOverlay_App_MaterialAlertDialog);
        builder.setView(dialogView);
        builder.setBackground(getResources().getDrawable(R.drawable.dialog_background, getTheme()));
        builder.setCancelable(true);
        
        AlertDialog dialog = builder.create();
        
        dialogView.findViewById(R.id.btn_try_again).setOnClickListener(v -> {
            dialog.dismiss();
            resumeScanning();
        });
        
        dialog.show();
    }

    private void showProgress(boolean show, String message) {
        progressOverlay.setVisibility(show ? View.VISIBLE : View.GONE);
        if (message != null) {
            tvProgressMessage.setText(message);
        }
    }

    private void resumeScanning() {
        isScanning = true;
        barcodeScanner.resume();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (checkCameraPermission() && isScanning) {
            barcodeScanner.resume();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        barcodeScanner.pause();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        barcodeScanner.pause();
    }
}