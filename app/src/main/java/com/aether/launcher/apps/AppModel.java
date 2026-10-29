package com.aether.launcher.apps;

import android.graphics.drawable.Drawable;

public class AppModel {

    public enum Category {
        ALL("ALL"),
        COMMS("COMMS"),
        MEDIA("MEDIA"),
        NET("NET"),
        TOOLS("TOOLS"),
        GAMES("GAMES"),
        SYSTEM("SYSTEM");

        public final String label;
        Category(String label) {
            this.label = label;
        }
    }

    private final String appName;
    private final String packageName;
    private final String activityName;
    private final Drawable icon;
    private final Drawable banner;
    private final Category category;
    private boolean isPinned;

    public AppModel(String appName, String packageName, String activityName, Drawable icon, Drawable banner, Category category) {
        this.appName = appName;
        this.packageName = packageName;
        this.activityName = activityName;
        this.icon = icon;
        this.banner = banner;
        this.category = category;
        this.isPinned = false;
    }

    public AppModel(String appName, String packageName, String activityName, Drawable icon, Category category) {
        this(appName, packageName, activityName, icon, null, category);
    }

    public String getAppName() { return appName; }
    public String getPackageName() { return packageName; }
    public String getActivityName() { return activityName; }
    public Drawable getIcon() { return icon; }
    public Drawable getBanner() { return banner; }
    public Category getCategory() { return category; }
    public boolean isPinned() { return isPinned; }
    public void setPinned(boolean pinned) { isPinned = pinned; }
}
