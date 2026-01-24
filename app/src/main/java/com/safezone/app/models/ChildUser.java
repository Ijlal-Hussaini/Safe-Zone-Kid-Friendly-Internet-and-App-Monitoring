package com.safezone.app.models;

/**
 * Child User Model - UPDATED WITH ALL REQUIREMENTS
 * New fields: dateOfBirth, deviceNickname
 */
public class ChildUser extends User {
    private int age;
    private long dateOfBirth; // timestamp in milliseconds
    private String deviceNickname; // e.g., "Ali's Tablet"
    private String parentId;
    private String deviceId;
    private ScreenTimeRule screenTimeRules;
    private ContentRule contentRules;

    // Empty constructor for Firebase
    public ChildUser() {
        super();
        this.setRole("child");
    }

    public ChildUser(String uid, String name, String email, int age) {
        super(uid, name, email, "child");
        this.age = age;
        this.screenTimeRules = new ScreenTimeRule();
        this.contentRules = new ContentRule();
    }

    // Getters and Setters
    public int getAge() {
        return age;
    }

    public void setAge(int age) {
        this.age = age;
    }

    public long getDateOfBirth() {
        return dateOfBirth;
    }

    public void setDateOfBirth(long dateOfBirth) {
        this.dateOfBirth = dateOfBirth;
    }

    public String getDeviceNickname() {
        return deviceNickname;
    }

    public void setDeviceNickname(String deviceNickname) {
        this.deviceNickname = deviceNickname;
    }

    public String getParentId() {
        return parentId;
    }

    public void setParentId(String parentId) {
        this.parentId = parentId;
    }

    public String getDeviceId() {
        return deviceId;
    }

    public void setDeviceId(String deviceId) {
        this.deviceId = deviceId;
    }

    public ScreenTimeRule getScreenTimeRules() {
        return screenTimeRules;
    }

    public void setScreenTimeRules(ScreenTimeRule screenTimeRules) {
        this.screenTimeRules = screenTimeRules;
    }

    public ContentRule getContentRules() {
        return contentRules;
    }

    public void setContentRules(ContentRule contentRules) {
        this.contentRules = contentRules;
    }
}