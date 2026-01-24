package com.safezone.app.models;

import java.util.HashMap;
import java.util.Map;

public class ParentUser extends User {
    private String phone;
    private String address;
    private Map<String, Boolean> children; // childUid -> true

    // Empty constructor required for Firebase
    public ParentUser() {
        super();
        this.children = new HashMap<>();
    }

    public ParentUser(String uid, String name, String email) {
        super(uid, name, email, "parent");
        this.children = new HashMap<>();
    }

    // Getters and Setters
    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public Map<String, Boolean> getChildren() {
        return children;
    }

    public void setChildren(Map<String, Boolean> children) {
        this.children = children;
    }

    public void addChild(String childUid) {
        if (this.children == null) {
            this.children = new HashMap<>();
        }
        this.children.put(childUid, true);
    }

    public void removeChild(String childUid) {
        if (this.children != null) {
            this.children.remove(childUid);
        }
    }
}