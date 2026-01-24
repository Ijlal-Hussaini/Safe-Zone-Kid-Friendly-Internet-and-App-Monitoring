package com.safezone.app.models;

/**
 * Model class for Activity Log
 * Stores app usage information from child's device
 */
public class ActivityLog {

    private String logId;
    private String packageName;
    private String appName;
    private long duration;  // in milliseconds
    private long timestamp; // when the log was created

    // Required empty constructor for Firebase
    public ActivityLog() {
    }

    public ActivityLog(String logId, String packageName, String appName, long duration, long timestamp) {
        this.logId = logId;
        this.packageName = packageName;
        this.appName = appName;
        this.duration = duration;
        this.timestamp = timestamp;
    }

    // Getters
    public String getLogId() {
        return logId;
    }

    public String getPackageName() {
        return packageName;
    }

    public String getAppName() {
        return appName;
    }

    public long getDuration() {
        return duration;
    }

    public long getTimestamp() {
        return timestamp;
    }

    // Setters
    public void setLogId(String logId) {
        this.logId = logId;
    }

    public void setPackageName(String packageName) {
        this.packageName = packageName;
    }

    public void setAppName(String appName) {
        this.appName = appName;
    }

    public void setDuration(long duration) {
        this.duration = duration;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    @Override
    public String toString() {
        return "ActivityLog{" +
                "logId='" + logId + '\'' +
                ", packageName='" + packageName + '\'' +
                ", appName='" + appName + '\'' +
                ", duration=" + duration +
                ", timestamp=" + timestamp +
                '}';
    }
}