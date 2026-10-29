package com.aether.launcher.apps;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Drawable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class AppDeckRepository {

    private final Context context;
    private final SharedPreferences prefs;
    private final List<AppModel> allApps = new ArrayList<>();
    private final Set<String> pinnedPackages = new HashSet<>();

    public AppDeckRepository(Context context) {
        this.context = context.getApplicationContext();
        this.prefs = this.context.getSharedPreferences("aether_apps_prefs", Context.MODE_PRIVATE);
        loadPinnedPackages();
    }

    private void loadPinnedPackages() {
        Set<String> saved = prefs.getStringSet("pinned_apps", null);
        if (saved != null) {
            pinnedPackages.addAll(saved);
        }
    }

    private void savePinnedPackages() {
        prefs.edit().putStringSet("pinned_apps", new HashSet<>(pinnedPackages)).apply();
    }

    public void reloadApps() {
        allApps.clear();
        PackageManager pm = context.getPackageManager();
        Intent intent = new Intent(Intent.ACTION_MAIN, null);
        intent.addCategory(Intent.CATEGORY_LAUNCHER);

        List<ResolveInfo> activities = pm.queryIntentActivities(intent, 0);

        // Also query Leanback TV apps
        Intent tvIntent = new Intent(Intent.ACTION_MAIN, null);
        tvIntent.addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER);
        List<ResolveInfo> tvActivities = pm.queryIntentActivities(tvIntent, 0);

        Set<String> seenPackages = new HashSet<>();
        List<ResolveInfo> combined = new ArrayList<>(activities);
        for (ResolveInfo ri : activities) {
            seenPackages.add(ri.activityInfo.packageName);
        }
        for (ResolveInfo tri : tvActivities) {
            if (!seenPackages.contains(tri.activityInfo.packageName)) {
                combined.add(tri);
                seenPackages.add(tri.activityInfo.packageName);
            }
        }

        for (ResolveInfo ri : combined) {
            String pkg = ri.activityInfo.packageName;
            if (pkg.equals(context.getPackageName())) {
                continue; // Skip ourselves
            }

            String label = ri.loadLabel(pm).toString();
            String actName = ri.activityInfo.name;
            Drawable icon = ri.loadIcon(pm);
            Drawable banner = null;
            try {
                if (ri.activityInfo != null) {
                    banner = ri.activityInfo.loadBanner(pm);
                    if (banner == null && ri.activityInfo.applicationInfo != null) {
                        banner = ri.activityInfo.applicationInfo.loadBanner(pm);
                    }
                }
            } catch (Throwable ignored) {}

            AppModel.Category cat = categorizeApp(pkg, label, ri.activityInfo.applicationInfo);
            AppModel app = new AppModel(label, pkg, actName, icon, banner, cat);
            if (pinnedPackages.contains(pkg)) {
                app.setPinned(true);
            }
            allApps.add(app);
        }

        Collections.sort(allApps, (a, b) -> a.getAppName().compareToIgnoreCase(b.getAppName()));
    }

    private AppModel.Category categorizeApp(String pkg, String label, ApplicationInfo appInfo) {
        String p = pkg.toLowerCase(Locale.US);
        String l = label.toLowerCase(Locale.US);

        // Games
        if ((appInfo.flags & ApplicationInfo.FLAG_IS_GAME) != 0 ||
                p.contains("game") || l.contains("game")) {
            return AppModel.Category.GAMES;
        }

        // Comms
        if (p.contains("dialer") || p.contains("phone") || p.contains("telecom") ||
                p.contains("whatsapp") || p.contains("telegram") || p.contains("signal") ||
                p.contains("messaging") || p.contains("mms") || p.contains("discord") ||
                p.contains("slack") || p.contains("mail") || p.contains("gmail") ||
                l.contains("chat") || l.contains("messages") || l.contains("phone")) {
            return AppModel.Category.COMMS;
        }

        // Media
        if (p.contains("camera") || p.contains("gallery") || p.contains("photos") ||
                p.contains("youtube") || p.contains("spotify") || p.contains("music") ||
                p.contains("video") || p.contains("netflix") || p.contains("twitch") ||
                p.contains("vlc") || p.contains("player") || l.contains("music") ||
                l.contains("gallery") || l.contains("camera")) {
            return AppModel.Category.MEDIA;
        }

        // Net
        if (p.contains("chrome") || p.contains("browser") || p.contains("firefox") ||
                p.contains("opera") || p.contains("brave") || p.contains("edge") ||
                p.contains("cloud") || p.contains("drive") || p.contains("dropbox") ||
                l.contains("browser") || l.contains("internet")) {
            return AppModel.Category.NET;
        }

        // Tools
        if (p.contains("settings") || p.contains("calculator") || p.contains("clock") ||
                p.contains("termux") || p.contains("files") || p.contains("documents") ||
                p.contains("notes") || p.contains("vending") || p.contains("playstore") ||
                l.contains("settings") || l.contains("calc") || l.contains("files")) {
            return AppModel.Category.TOOLS;
        }

        // System
        if ((appInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0) {
            return AppModel.Category.SYSTEM;
        }

        return AppModel.Category.TOOLS;
    }

    public List<AppModel> getAllApps() {
        return allApps;
    }

    public List<AppModel> getPinnedApps() {
        List<AppModel> pinned = new ArrayList<>();
        for (AppModel app : allApps) {
            if (app.isPinned()) pinned.add(app);
        }
        return pinned;
    }

    public List<AppModel> filter(AppModel.Category category, String query) {
        List<AppModel> filtered = new ArrayList<>();
        String q = query != null ? query.trim().toLowerCase(Locale.US) : "";

        for (AppModel app : allApps) {
            boolean matchesCat = (category == null || category == AppModel.Category.ALL || app.getCategory() == category);
            boolean matchesQuery = q.isEmpty() ||
                    app.getAppName().toLowerCase(Locale.US).contains(q) ||
                    app.getPackageName().toLowerCase(Locale.US).contains(q);

            if (matchesCat && matchesQuery) {
                filtered.add(app);
            }
        }
        return filtered;
    }

    public void togglePin(AppModel app) {
        if (app.isPinned()) {
            app.setPinned(false);
            pinnedPackages.remove(app.getPackageName());
        } else {
            app.setPinned(true);
            pinnedPackages.add(app.getPackageName());
        }
        savePinnedPackages();
    }
}
