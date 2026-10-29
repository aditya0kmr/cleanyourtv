package com.aether.launcher.apps;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.aether.launcher.R;
import com.aether.launcher.audio.CyberSoundSynth;
import com.aether.launcher.hud.HologramTheme;
import java.util.ArrayList;
import java.util.List;

public class HolographicDeckAdapter extends RecyclerView.Adapter<HolographicDeckAdapter.AppViewHolder> {

    public interface OnAppActionListener {
        void onAppLaunched(AppModel app);
        void onAppLongClicked(AppModel app);
    }

    public interface OnAppFocusChangeListener {
        void onAppFocused(AppModel app);
    }

    private final Context context;
    private final List<AppModel> apps = new ArrayList<>();
    private final OnAppActionListener actionListener;
    private OnAppFocusChangeListener focusChangeListener;

    public HolographicDeckAdapter(Context context, OnAppActionListener listener) {
        this.context = context;
        this.actionListener = listener;
    }

    public void setOnAppFocusChangeListener(OnAppFocusChangeListener listener) {
        this.focusChangeListener = listener;
    }

    public void setApps(List<AppModel> newApps) {
        apps.clear();
        if (newApps != null) {
            apps.addAll(newApps);
        }
        notifyDataSetChanged();
    }

    public List<AppModel> getApps() {
        return apps;
    }

    @NonNull
    @Override
    public AppViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_cyber_app, parent, false);
        return new AppViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull AppViewHolder holder, int position) {
        AppModel app = apps.get(position);
        holder.bind(app);
    }

    @Override
    public int getItemCount() {
        return apps.size();
    }

    class AppViewHolder extends RecyclerView.ViewHolder {
        final View container;
        final ImageView icon;
        final TextView label;
        final TextView category;
        final View pinDot;

        AppViewHolder(@NonNull View itemView) {
            super(itemView);
            container = itemView.findViewById(R.id.card_container);
            icon = itemView.findViewById(R.id.app_icon);
            label = itemView.findViewById(R.id.app_label);
            category = itemView.findViewById(R.id.app_category);
            pinDot = itemView.findViewById(R.id.pin_indicator);
        }

        void bind(AppModel app) {
            label.setText(app.getAppName());
            category.setText(app.getCategory().label);
            category.setTextColor(HologramTheme.get().getPrimary());
            icon.setImageDrawable(app.getIcon());
            pinDot.setVisibility(app.isPinned() ? View.VISIBLE : View.GONE);
            pinDot.setBackgroundColor(HologramTheme.get().getPrimary());

            // Prevent remote focus trapping
            container.setNextFocusUpId(R.id.shelf_category_tabs);
            container.setNextFocusDownId(R.id.btn_open_terminal);

            // TV Remote Focus Scaling (CleanYourTV 1.10x Decelerate Interpolator) & Audio
            container.setOnFocusChangeListener((v, hasFocus) -> {
                if (hasFocus) {
                    v.animate().scaleX(1.10f).scaleY(1.10f).translationZ(6f).setDuration(130)
                            .setInterpolator(new DecelerateInterpolator(1.5f)).start();
                    v.setSelected(true);
                    CyberSoundSynth.get().playDataClick();
                    if (focusChangeListener != null) {
                        focusChangeListener.onAppFocused(app);
                    }
                } else {
                    v.animate().scaleX(1.0f).scaleY(1.0f).translationZ(0f).setDuration(130)
                            .setInterpolator(new DecelerateInterpolator(1.5f)).start();
                    v.setSelected(false);
                }
            });

            container.setOnClickListener(v -> {
                CyberSoundSynth.get().playWarp();
                // Subtle press animation
                v.animate().scaleX(0.96f).scaleY(0.96f).setDuration(70).withEndAction(() -> {
                    v.animate().scaleX(1.10f).scaleY(1.10f).setDuration(70).start();
                    launchApp(app);
                }).start();
            });

            container.setOnLongClickListener(v -> {
                CyberSoundSynth.get().playChirp();
                if (actionListener != null) {
                    actionListener.onAppLongClicked(app);
                }
                return true;
            });
        }

        private void launchApp(AppModel app) {
            try {
                Intent intent = new Intent(Intent.ACTION_MAIN);
                intent.addCategory(Intent.CATEGORY_LAUNCHER);
                intent.setComponent(new ComponentName(app.getPackageName(), app.getActivityName()));
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
                context.startActivity(intent);

                if (actionListener != null) {
                    actionListener.onAppLaunched(app);
                }
            } catch (Exception ignored) {}
        }
    }
}
