package com.safezone.app.utils;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.util.Base64;
import android.util.Log;

import com.google.firebase.database.DatabaseReference;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;

/**
 * Image Helper - Base64 Storage Version
 * Stores images as Base64 strings in Realtime Database
 * NO FIREBASE STORAGE REQUIRED - Works on Spark (Free) Plan
 */
public class ImageHelper {

    private static final String TAG = "ImageHelper";
    private static final int MAX_IMAGE_SIZE = 512; // Reduced for Base64 (512x512 max)
    private static final int COMPRESSION_QUALITY = 60; // Lower quality for smaller size
    private static final int MAX_BASE64_SIZE = 800 * 1024; // 800KB max (stay under 1MB limit)

    /**
     * Compress image from URI
     * Optimized for Base64 storage (smaller size)
     */
    public static byte[] compressImage(Context context, Uri imageUri) {
        try {
            InputStream inputStream = context.getContentResolver().openInputStream(imageUri);
            if (inputStream == null) {
                Log.e(TAG, "Failed to open input stream");
                return null;
            }

            Bitmap originalBitmap = BitmapFactory.decodeStream(inputStream);
            inputStream.close();

            if (originalBitmap == null) {
                Log.e(TAG, "Failed to decode bitmap");
                return null;
            }

            // Calculate new dimensions (smaller for Base64)
            int width = originalBitmap.getWidth();
            int height = originalBitmap.getHeight();

            float scale = 1.0f;
            if (width > MAX_IMAGE_SIZE || height > MAX_IMAGE_SIZE) {
                scale = Math.min(
                        (float) MAX_IMAGE_SIZE / width,
                        (float) MAX_IMAGE_SIZE / height
                );
            }

            int newWidth = Math.round(width * scale);
            int newHeight = Math.round(height * scale);

            // Scale bitmap
            Bitmap scaledBitmap = Bitmap.createScaledBitmap(
                    originalBitmap,
                    newWidth,
                    newHeight,
                    true
            );

            // Compress to JPEG with lower quality for smaller size
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            scaledBitmap.compress(Bitmap.CompressFormat.JPEG, COMPRESSION_QUALITY, outputStream);
            byte[] compressedData = outputStream.toByteArray();

            // Clean up
            originalBitmap.recycle();
            scaledBitmap.recycle();
            outputStream.close();

            Log.d(TAG, "Image compressed successfully. Size: " + compressedData.length + " bytes");

            // Check if Base64 will exceed size limit
            String base64Test = Base64.encodeToString(compressedData, Base64.DEFAULT);
            if (base64Test.length() > MAX_BASE64_SIZE) {
                Log.w(TAG, "Compressed image is still too large, re-compressing with lower quality");
                return compressImageAggressively(scaledBitmap);
            }

            return compressedData;

        } catch (Exception e) {
            Log.e(TAG, "Error compressing image", e);
            return null;
        }
    }

    /**
     * Aggressive compression for large images
     */
    private static byte[] compressImageAggressively(Bitmap bitmap) {
        try {
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            bitmap.compress(Bitmap.CompressFormat.JPEG, 40, outputStream); // Even lower quality
            return outputStream.toByteArray();
        } catch (Exception e) {
            Log.e(TAG, "Error in aggressive compression", e);
            return null;
        }
    }

    /**
     * Upload callback interface
     */
    public interface UploadCallback {
        void onSuccess(String base64Data);
        void onFailure(String error);
        void onProgress(int progress);
    }

    /**
     * Save profile image to Realtime Database as Base64
     * NO FIREBASE STORAGE NEEDED
     */
    public static void uploadProfileImage(
            String userId,
            byte[] imageData,
            UploadCallback callback
    ) {
        if (imageData == null || imageData.length == 0) {
            callback.onFailure("Invalid image data");
            return;
        }

        try {
            // Convert to Base64
            callback.onProgress(30);
            String base64Image = Base64.encodeToString(imageData, Base64.DEFAULT);

            // Check size
            if (base64Image.length() > MAX_BASE64_SIZE) {
                callback.onFailure("Image too large. Please select a smaller image.");
                return;
            }

            callback.onProgress(60);

            // Save to Realtime Database
            DatabaseReference userRef = FirebaseHelper.getUserRef(userId);
            userRef.child("profilePhotoBase64").setValue(base64Image)
                    .addOnSuccessListener(aVoid -> {
                        Log.d(TAG, "Image saved to database successfully");
                        callback.onProgress(100);
                        callback.onSuccess(base64Image);
                    })
                    .addOnFailureListener(e -> {
                        Log.e(TAG, "Failed to save image to database", e);
                        callback.onFailure("Failed to save: " + e.getMessage());
                    });

        } catch (Exception e) {
            Log.e(TAG, "Error uploading image", e);
            callback.onFailure("Upload error: " + e.getMessage());
        }
    }

    /**
     * Delete profile image from Database
     */
    public static void deleteProfileImage(
            String userId,
            com.google.android.gms.tasks.OnSuccessListener<Void> onSuccess,
            com.google.android.gms.tasks.OnFailureListener onFailure
    ) {
        DatabaseReference userRef = FirebaseHelper.getUserRef(userId);
        userRef.child("profilePhotoBase64").removeValue()
                .addOnSuccessListener(onSuccess)
                .addOnFailureListener(onFailure);
    }

    /**
     * Decode Base64 string to Bitmap
     */
    public static Bitmap decodeBase64ToBitmap(String base64String) {
        try {
            if (base64String == null || base64String.isEmpty()) {
                return null;
            }

            byte[] decodedBytes = Base64.decode(base64String, Base64.DEFAULT);
            return BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.length);
        } catch (Exception e) {
            Log.e(TAG, "Error decoding Base64 to Bitmap", e);
            return null;
        }
    }

    /**
     * Validate image URI
     */
    public static boolean isValidImageUri(Context context, Uri uri) {
        try {
            InputStream inputStream = context.getContentResolver().openInputStream(uri);
            if (inputStream == null) return false;

            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inJustDecodeBounds = true;
            BitmapFactory.decodeStream(inputStream, null, options);
            inputStream.close();

            return options.outWidth > 0 && options.outHeight > 0;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Get estimated Base64 size in KB
     */
    public static int getBase64SizeKB(String base64String) {
        if (base64String == null) return 0;
        return base64String.length() / 1024;
    }
}