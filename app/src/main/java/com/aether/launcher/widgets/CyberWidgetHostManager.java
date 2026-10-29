package com.aether.launcher.widgets;

import android.app.Activity;
import android.appwidget.AppWidgetHost;
import android.appwidget.AppWidgetHostView;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProviderInfo;
import android.content.Context;
import android.content.Intent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import com.aether.launcher.R;

public class CyberWidgetHostManager {

    public static final int APPWIDGET_HOST_ID = 2048;
    public static final int REQUEST_PICK_APPWIDGET = 3001;
    public static final int REQUEST_CREATE_APPWIDGET = 3002;

    private final Activity activity;
    private final AppWidgetManager appWidgetManager;
    private final AppWidgetHost appWidgetHost;

    public CyberWidgetHostManager(Activity activity) {
        this.activity = activity;
        this.appWidgetManager = AppWidgetManager.getInstance(activity);
        this.appWidgetHost = new AppWidgetHost(activity, APPWIDGET_HOST_ID);
    }

    public void startListening() {
        try {
            appWidgetHost.startListening();
        } catch (Exception ignored) {}
    }

    public void stopListening() {
        try {
            appWidgetHost.stopListening();
        } catch (Exception ignored) {}
    }

    public void pickWidget() {
        int appWidgetId = appWidgetHost.allocateAppWidgetId();
        Intent pickIntent = new Intent(AppWidgetManager.ACTION_APPWIDGET_PICK);
        pickIntent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId);
        activity.startActivityForResult(pickIntent, REQUEST_PICK_APPWIDGET);
    }

    public void handleActivityResult(int requestCode, int resultCode, Intent data, ViewGroup container) {
        if (resultCode != Activity.RESULT_OK || data == null) return;

        if (requestCode == REQUEST_PICK_APPWIDGET) {
            int appWidgetId = data.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1);
            configureWidget(appWidgetId, container);
        } else if (requestCode == REQUEST_CREATE_APPWIDGET) {
            int appWidgetId = data.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1);
            attachWidgetToContainer(appWidgetId, container);
        }
    }

    private void configureWidget(int appWidgetId, ViewGroup container) {
        AppWidgetProviderInfo appWidgetInfo = appWidgetManager.getAppWidgetInfo(appWidgetId);
        if (appWidgetInfo != null && appWidgetInfo.configure != null) {
            Intent intent = new Intent(AppWidgetManager.ACTION_APPWIDGET_CONFIGURE);
            intent.setComponent(appWidgetInfo.configure);
            intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId);
            activity.startActivityForResult(intent, REQUEST_CREATE_APPWIDGET);
        } else {
            attachWidgetToContainer(appWidgetId, container);
        }
    }

    public void attachWidgetToContainer(int appWidgetId, ViewGroup container) {
        if (container == null || appWidgetId == -1) return;
        AppWidgetProviderInfo appWidgetInfo = appWidgetManager.getAppWidgetInfo(appWidgetId);
        if (appWidgetInfo == null) return;

        AppWidgetHostView hostView = appWidgetHost.createView(activity, appWidgetId, appWidgetInfo);
        hostView.setAppWidget(appWidgetId, appWidgetInfo);

        // Wrap hostView in a cyber frame
        FrameLayout cyberFrame = new FrameLayout(activity);
        cyberFrame.setBackgroundResource(R.drawable.bg_cyber_card);
        cyberFrame.setPadding(8, 8, 8, 8);

        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        cyberFrame.addView(hostView, lp);

        container.removeAllViews();
        container.addView(cyberFrame);
        container.setVisibility(View.VISIBLE);
    }
}
