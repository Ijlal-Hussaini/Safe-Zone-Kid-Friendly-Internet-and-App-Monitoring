package com.safezone.app.models;

/**
 * Screen Time Rule Model
 * Defines daily limits and schedules for child's screen time
 */
public class ScreenTimeRule {
    private int dailyLimitMinutes;
    private boolean enabled;
    private String schedule;
    private java.util.List<String> allowedApps;

    // Empty constructor for Firebase
    public ScreenTimeRule() {
        this.dailyLimitMinutes = 120; // Default 2 hours
        this.enabled = false;
        this.schedule = "";
        this.allowedApps = new java.util.ArrayList<>();
    }

    public ScreenTimeRule(int dailyLimitMinutes, boolean enabled, String schedule) {
        this.dailyLimitMinutes = dailyLimitMinutes;
        this.enabled = enabled;
        this.schedule = schedule;
    }

    // Getters and Setters
    public int getDailyLimitMinutes() {
        return dailyLimitMinutes;
    }

    public void setDailyLimitMinutes(int dailyLimitMinutes) {
        this.dailyLimitMinutes = dailyLimitMinutes;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getSchedule() {
        return schedule;
    }

    public void setSchedule(String schedule) {
        this.schedule = schedule;
    }

    public java.util.List<String> getAllowedApps() {
        return allowedApps;
    }

    public void setAllowedApps(java.util.List<String> allowedApps) {
        this.allowedApps = allowedApps;
    }
}