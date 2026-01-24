package com.safezone.app.models;

import java.util.ArrayList;
import java.util.List;

/**
 * Content Rule Model
 * Defines blocked apps and websites for child
 */
public class ContentRule {
    private List<String> blockedApps;
    private List<String> blockedWebsites;

    // Empty constructor for Firebase
    public ContentRule() {
        this.blockedApps = new ArrayList<>();
        this.blockedWebsites = new ArrayList<>();
    }

    public ContentRule(List<String> blockedApps, List<String> blockedWebsites) {
        this.blockedApps = blockedApps != null ? blockedApps : new ArrayList<>();
        this.blockedWebsites = blockedWebsites != null ? blockedWebsites : new ArrayList<>();
    }

    // Getters and Setters
    public List<String> getBlockedApps() {
        return blockedApps;
    }

    public void setBlockedApps(List<String> blockedApps) {
        this.blockedApps = blockedApps;
    }

    public List<String> getBlockedWebsites() {
        return blockedWebsites;
    }

    public void setBlockedWebsites(List<String> blockedWebsites) {
        this.blockedWebsites = blockedWebsites;
    }

    /**
     * Add a blocked app
     */
    public void addBlockedApp(String appPackageName) {
        if (!blockedApps.contains(appPackageName)) {
            blockedApps.add(appPackageName);
        }
    }

    /**
     * Remove a blocked app
     */
    public void removeBlockedApp(String appPackageName) {
        blockedApps.remove(appPackageName);
    }

    /**
     * Add a blocked website
     */
    public void addBlockedWebsite(String websiteUrl) {
        if (!blockedWebsites.contains(websiteUrl)) {
            blockedWebsites.add(websiteUrl);
        }
    }

    /**
     * Remove a blocked website
     */
    public void removeBlockedWebsite(String websiteUrl) {
        blockedWebsites.remove(websiteUrl);
    }
}