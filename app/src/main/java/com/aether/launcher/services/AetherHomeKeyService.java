package com.aether.launcher.services;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.content.Intent;
import android.os.SystemClock;
import android.view.accessibility.AccessibilityEvent;
import com.aether.launcher.AetherHomeActivity;

public class AetherHomeKeyService extends AccessibilityService {

    private static final String AMAZON_LAUNCHER_PKG = "com.amazon.tv.launcher";
    private static final String GOOGLE_TV_LAUNCHER_PKG = "com.google.android.apps.tv.launcherx";
    private static final String ANDROID_TV_LAUNCHER_PKG = "com.google.android.tvlauncher";

    private long lastRedirectTime = 0;

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        AccessibilityServiceInfo info = new AccessibilityServiceInfo();
        info.eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED;
        info.feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC;
        info.flags = AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS;
        setServiceInfo(info);
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event == null || event.getPackageName() == null) return;
        String pkg = event.getPackageName().toString();

        // Check if the stock ad-filled launcher was forced to the foreground
        if (pkg.equals(AMAZON_LAUNCHER_PKG) ||
                pkg.equals(GOOGLE_TV_LAUNCHER_PKG) ||
                pkg.equals(ANDROID_TV_LAUNCHER_PKG)) {

            long now = SystemClock.uptimeMillis();
            // Debounce redirect to avoid event loops
            if (now - lastRedirectTime > 300) {
                lastRedirectTime = now;
                redirectToAether();
            }
        }
    }

    private void redirectToAether() {
        Intent intent = new Intent(this, AetherHomeActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK |
                Intent.FLAG_ACTIVITY_CLEAR_TOP |
                Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        startActivity(intent);
    }

    @Override
    public void onInterrupt() {}
}
