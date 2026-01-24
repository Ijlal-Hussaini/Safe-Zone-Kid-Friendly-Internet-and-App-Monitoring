package com.safezone.app.activities;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.view.View;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.database.DatabaseReference;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.journeyapps.barcodescanner.BarcodeEncoder;
import com.safezone.app.R;
import com.safezone.app.models.LinkToken;
import com.safezone.app.utils.FirebaseHelper;
import com.safezone.app.utils.ValidationUtils;

import java.util.UUID;

/**
 * Activity to add a new child and generate QR code for linking
 */
public class AddChildActivity extends AppCompatActivity {

    private MaterialToolbar toolbar;
    private TextInputLayout tilChildName, tilChildAge;
    private TextInputEditText etChildName, etChildAge;
    private MaterialButton btnGenerateQR, btnCopyToken, btnDone;
    private MaterialCardView cardQRSection;
    private ImageView ivQRCode;
    private TextView tvTimer;
    private TextView tvShortCode;
    private ProgressBar progressBar;

    private DatabaseReference linkTokensRef;
    private String currentUserId;
    private LinkToken currentToken;
    private String shortCode;
    private CountDownTimer countDownTimer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_add_child);

        initViews();
        setupListeners();

        linkTokensRef = FirebaseHelper.getLinkTokensRef();
        currentUserId = FirebaseHelper.getCurrentUserId();
    }

    private void initViews() {
        toolbar = findViewById(R.id.toolbar);
        tilChildName = findViewById(R.id.til_child_name);
        tilChildAge = findViewById(R.id.til_child_age);
        etChildName = findViewById(R.id.et_child_name);
        etChildAge = findViewById(R.id.et_child_age);
        btnGenerateQR = findViewById(R.id.btn_generate_qr);
        btnCopyToken = findViewById(R.id.btn_copy_token);
        btnDone = findViewById(R.id.btn_done);
        cardQRSection = findViewById(R.id.card_qr_section);
        ivQRCode = findViewById(R.id.iv_qr_code);
        tvTimer = findViewById(R.id.tv_timer);
        tvShortCode = findViewById(R.id.tv_short_code);
        progressBar = findViewById(R.id.progress_bar);
    }

    private void setupListeners() {
        toolbar.setNavigationOnClickListener(v -> onBackPressed());

        btnGenerateQR.setOnClickListener(v -> validateAndGenerateQR());

        btnCopyToken.setOnClickListener(v -> copyTokenToClipboard());

        btnDone.setOnClickListener(v -> finish());
    }

    private void validateAndGenerateQR() {
        String name = etChildName.getText().toString().trim();
        String ageStr = etChildAge.getText().toString().trim();

        // Validate name
        if (!ValidationUtils.isValidName(name)) {
            tilChildName.setError("Please enter a valid name (min 2 characters)");
            etChildName.requestFocus();
            return;
        } else {
            tilChildName.setError(null);
        }

        // Validate age
        if (ageStr.isEmpty()) {
            tilChildAge.setError("Please enter child's age");
            etChildAge.requestFocus();
            return;
        }

        int age = Integer.parseInt(ageStr);
        if (!ValidationUtils.isValidAge(age)) {
            tilChildAge.setError("Age must be between 1 and 18");
            etChildAge.requestFocus();
            return;
        } else {
            tilChildAge.setError(null);
        }

        // Generate QR code
        generateQRCode(name, age);
    }

    private void generateQRCode(String childName, int childAge) {
        showLoading(true);

        // Generate short 6-character code (uppercase letters and numbers)
        shortCode = generateShortCode();

        // Get parent's email and name to include in token
        FirebaseHelper.getUserRef(currentUserId).addListenerForSingleValueEvent(
                new com.google.firebase.database.ValueEventListener() {
                    @Override
                    public void onDataChange(@androidx.annotation.NonNull com.google.firebase.database.DataSnapshot snapshot) {
                        String parentEmail = snapshot.child("email").getValue(String.class);
                        String parentName = snapshot.child("name").getValue(String.class);
                        
                        // Create link token with parent info using short code as ID
                        currentToken = new LinkToken(shortCode, currentUserId, parentEmail, parentName, childName);
                        
                        // Save to Firebase
                        saveTokenAndGenerateQR(shortCode);
                    }

                    @Override
                    public void onCancelled(@androidx.annotation.NonNull com.google.firebase.database.DatabaseError error) {
                        // Fallback: create token without parent info
                        currentToken = new LinkToken(shortCode, currentUserId, childName);
                        saveTokenAndGenerateQR(shortCode);
                    }
                });
    }
    
    /**
     * Generate a short 6-character alphanumeric code
     * Uses uppercase letters and numbers for easy typing
     */
    private String generateShortCode() {
        String chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"; // Removed confusing chars: I, O, 0, 1
        StringBuilder code = new StringBuilder();
        java.util.Random random = new java.util.Random();
        
        for (int i = 0; i < 6; i++) {
            code.append(chars.charAt(random.nextInt(chars.length())));
        }
        
        return code.toString();
    }
    
    private void saveTokenAndGenerateQR(String tokenId) {
        linkTokensRef.child(tokenId).setValue(currentToken)
                .addOnSuccessListener(aVoid -> {
                    showLoading(false);

                    // Generate QR code bitmap
                    Bitmap qrBitmap = generateQRBitmap(tokenId);
                    if (qrBitmap != null) {
                        ivQRCode.setImageBitmap(qrBitmap);
                        
                        // Display the short code
                        tvShortCode.setText(shortCode);

                        // Show QR section
                        cardQRSection.setVisibility(View.VISIBLE);
                        btnGenerateQR.setVisibility(View.GONE);
                        btnDone.setVisibility(View.VISIBLE);

                        // Disable input fields
                        etChildName.setEnabled(false);
                        etChildAge.setEnabled(false);

                        // Start countdown timer
                        startCountdownTimer();

                        Toast.makeText(this, "Link code generated: " + shortCode,
                                Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(this, "Failed to generate QR code",
                                Toast.LENGTH_SHORT).show();
                    }
                })
                .addOnFailureListener(e -> {
                    showLoading(false);
                    Toast.makeText(this, "Error: " + e.getMessage(),
                            Toast.LENGTH_SHORT).show();
                });
    }

    private Bitmap generateQRBitmap(String tokenId) {
        try {
            // QR code content (just the token ID)
            String qrContent = tokenId;

            MultiFormatWriter writer = new MultiFormatWriter();
            BitMatrix bitMatrix = writer.encode(qrContent, BarcodeFormat.QR_CODE, 500, 500);

            BarcodeEncoder encoder = new BarcodeEncoder();
            return encoder.createBitmap(bitMatrix);
        } catch (WriterException e) {
            e.printStackTrace();
            return null;
        }
    }

    private void startCountdownTimer() {
        // 10 minutes countdown
        countDownTimer = new CountDownTimer(10 * 60 * 1000, 1000) {
            @Override
            public void onTick(long millisUntilFinished) {
                long minutes = millisUntilFinished / 1000 / 60;
                long seconds = (millisUntilFinished / 1000) % 60;

                String timeText = String.format("Expires in: %02d:%02d", minutes, seconds);
                tvTimer.setText(timeText);
            }

            @Override
            public void onFinish() {
                tvTimer.setText("Expired!");
                tvTimer.setTextColor(getResources().getColor(R.color.error));

                // Delete expired token from Firebase using short code
                if (shortCode != null) {
                    linkTokensRef.child(shortCode).removeValue();
                }

                Toast.makeText(AddChildActivity.this,
                        "Link code expired. Please generate a new one.",
                        Toast.LENGTH_LONG).show();

                // Reset UI
                resetUI();
            }
        };
        countDownTimer.start();
    }

    private void copyTokenToClipboard() {
        if (shortCode != null && !shortCode.isEmpty()) {
            ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            ClipData clip = ClipData.newPlainText("Link Code", shortCode);
            clipboard.setPrimaryClip(clip);

            Toast.makeText(this, "Code " + shortCode + " copied!", Toast.LENGTH_SHORT).show();
        }
    }

    private void resetUI() {
        cardQRSection.setVisibility(View.GONE);
        btnGenerateQR.setVisibility(View.VISIBLE);
        btnDone.setVisibility(View.GONE);
        etChildName.setEnabled(true);
        etChildAge.setEnabled(true);
        etChildName.setText("");
        etChildAge.setText("");
        ivQRCode.setImageBitmap(null);
        tvShortCode.setText("");
        shortCode = null;
    }

    private void showLoading(boolean show) {
        progressBar.setVisibility(show ? View.VISIBLE : View.GONE);
        btnGenerateQR.setEnabled(!show);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Cancel timer to prevent memory leak
        if (countDownTimer != null) {
            countDownTimer.cancel();
        }
    }

    @Override
    public void onBackPressed() {
        if (cardQRSection.getVisibility() == View.VISIBLE) {
            // Warn user about losing QR code with styled dialog
            new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                    .setTitle("Cancel Link Process")
                    .setMessage("The link code will be invalidated. Are you sure?")
                    .setPositiveButton("Yes", (dialog, which) -> {
                        // Delete token from Firebase using short code
                        if (shortCode != null) {
                            linkTokensRef.child(shortCode).removeValue();
                        }
                        finish();
                    })
                    .setNegativeButton("No", null)
                    .show();
        } else {
            super.onBackPressed();
        }
    }
}