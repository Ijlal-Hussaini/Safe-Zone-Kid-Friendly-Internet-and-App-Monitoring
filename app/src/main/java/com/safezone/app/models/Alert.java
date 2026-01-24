package com.safezone.app.models;

/**
 * Model class for Alert
 * Represents safety alerts sent to parents
 */
public class Alert {

    private String alertId;
    private String parentId;
    private String childId;
    private String childName;
    private String type; // "BLOCKED_APP", "SCREEN_TIME", "LOCATION", "CONTENT"
    private String message;
    private String details;
    private long timestamp;
    private boolean read;

    // Required empty constructor for Firebase
    public Alert() {
    }

    public Alert(String alertId, String parentId, String childId, String childName,
                 String type, String message, String details, long timestamp) {
        this.alertId = alertId;
        this.parentId = parentId;
        this.childId = childId;
        this.childName = childName;
        this.type = type;
        this.message = message;
        this.details = details;
        this.timestamp = timestamp;
        this.read = false;
    }

    // Getters
    public String getAlertId() {
        return alertId;
    }

    public String getParentId() {
        return parentId;
    }

    public String getChildId() {
        return childId;
    }

    public String getChildName() {
        return childName;
    }

    public String getType() {
        return type;
    }

    public String getMessage() {
        return message;
    }

    public String getDetails() {
        return details;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public boolean isRead() {
        return read;
    }

    // Setters
    public void setAlertId(String alertId) {
        this.alertId = alertId;
    }

    public void setParentId(String parentId) {
        this.parentId = parentId;
    }

    public void setChildId(String childId) {
        this.childId = childId;
    }

    public void setChildName(String childName) {
        this.childName = childName;
    }

    public void setType(String type) {
        this.type = type;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public void setDetails(String details) {
        this.details = details;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    public void setRead(boolean read) {
        this.read = read;
    }

    // Alert Types
    public static class Type {
        public static final String BLOCKED_APP = "BLOCKED_APP";
        public static final String SCREEN_TIME = "SCREEN_TIME";
        public static final String LOCATION = "LOCATION";
        public static final String CONTENT = "CONTENT";
        public static final String REQUEST = "REQUEST";
    }

    @Override
    public String toString() {
        return "Alert{" +
                "alertId='" + alertId + '\'' +
                ", childName='" + childName + '\'' +
                ", type='" + type + '\'' +
                ", message='" + message + '\'' +
                ", timestamp=" + timestamp +
                ", read=" + read +
                '}';
    }
}