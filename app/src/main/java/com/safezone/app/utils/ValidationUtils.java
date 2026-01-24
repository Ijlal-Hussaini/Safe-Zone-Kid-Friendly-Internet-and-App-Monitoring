package com.safezone.app.utils;

import android.text.TextUtils;
import android.util.Patterns;

public class ValidationUtils {

    /**
     * Validate email address with strict domain validation
     * Requires proper TLD like .com, .org, .net, .edu, etc. (minimum 2 chars after last dot)
     */
    public static boolean isValidEmail(String email) {
        if (TextUtils.isEmpty(email)) {
            return false;
        }
        
        // First check basic pattern
        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            return false;
        }
        
        // Additional strict validation for proper domain
        // Must have @ symbol and proper TLD
        int atIndex = email.lastIndexOf('@');
        if (atIndex == -1) {
            return false;
        }
        
        String domain = email.substring(atIndex + 1);
        int lastDotIndex = domain.lastIndexOf('.');
        if (lastDotIndex == -1) {
            return false;
        }
        
        String tld = domain.substring(lastDotIndex + 1);
        // TLD must be at least 2 characters and only letters (no .co, .con, etc. unless valid)
        // Common valid TLDs: com, org, net, edu, gov, io, co.uk, etc.
        // We'll require minimum 2 chars and only letters
        if (tld.length() < 2 || !tld.matches("^[a-zA-Z]+$")) {
            return false;
        }
        
        // Reject obviously invalid TLDs like "con", "cmo", "ocm" (common typos)
        String tldLower = tld.toLowerCase();
        if (tldLower.equals("con") || tldLower.equals("cmo") || tldLower.equals("ocm") || 
            tldLower.equals("coom") || tldLower.equals("comm")) {
            return false;
        }
        
        return true;
    }

    /**
     * Validate password (minimum 8 characters, at least one letter and one number)
     */
    public static boolean isValidPassword(String password) {
        if (TextUtils.isEmpty(password) || password.length() < 8) {
            return false;
        }
        // Check for at least one letter and one number
        boolean hasLetter = password.matches(".*[a-zA-Z].*");
        boolean hasNumber = password.matches(".*\\d.*");
        return hasLetter && hasNumber;
    }

    /**
     * Get password validation error message
     */
    public static String getPasswordError(String password) {
        if (TextUtils.isEmpty(password)) {
            return "Password is required";
        }
        if (password.length() < 8) {
            return "Password must be at least 8 characters";
        }
        if (!password.matches(".*[a-zA-Z].*")) {
            return "Password must contain at least one letter";
        }
        if (!password.matches(".*\\d.*")) {
            return "Password must contain at least one number";
        }
        return null;
    }

    /**
     * Validate if passwords match
     */
    public static boolean doPasswordsMatch(String password, String confirmPassword) {
        return password.equals(confirmPassword);
    }

    /**
     * Validate name (not empty, only letters and spaces)
     */
    public static boolean isValidName(String name) {
        return !TextUtils.isEmpty(name) && name.trim().length() >= 2;
    }

    /**
     * Validate phone number (10-15 digits)
     */
    public static boolean isValidPhone(String phone) {
        return !TextUtils.isEmpty(phone) && phone.matches("\\d{10,15}");
    }

    /**
     * Validate OTP (6 digits)
     */
    public static boolean isValidOTP(String otp) {
        return !TextUtils.isEmpty(otp) && otp.matches("\\d{6}");
    }

    /**
     * Validate age (between 1 and 18)
     */
    public static boolean isValidAge(int age) {
        return age >= 1 && age <= 18;
    }

    /**
     * Get password strength
     * 0 = Weak, 1 = Medium, 2 = Strong
     */
    public static int getPasswordStrength(String password) {
        if (TextUtils.isEmpty(password)) return 0;

        int strength = 0;

        // Length check
        if (password.length() >= 8) strength++;

        // Contains number
        if (password.matches(".*\\d.*")) strength++;

        // Contains uppercase and lowercase
        if (password.matches(".*[a-z].*") && password.matches(".*[A-Z].*")) strength++;

        // Contains special character
        if (password.matches(".*[!@#$%^&*()_+\\-=\\[\\]{};':\"\\\\|,.<>\\/?].*")) strength++;

        // Return 0, 1, or 2
        return Math.min(strength / 2, 2);
    }

    /**
     * Get password strength text
     */
    public static String getPasswordStrengthText(int strength) {
        switch (strength) {
            case 0:
                return "Weak";
            case 1:
                return "Medium";
            case 2:
                return "Strong";
            default:
                return "Weak";
        }
    }
}