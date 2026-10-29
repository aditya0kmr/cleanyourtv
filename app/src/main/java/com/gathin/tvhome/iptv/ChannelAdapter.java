package com.gathin.tvhome.iptv;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.gathin.tvhome.R;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class ChannelAdapter extends RecyclerView.Adapter<ChannelAdapter.ViewHolder> {

    public interface OnChannelClickListener {
        void onChannelClick(Channel channel, int position);
    }

    private final List<Channel> channels = new ArrayList<>();
    private final OnChannelClickListener listener;
    private int selectedPosition = -1;

    public ChannelAdapter(OnChannelClickListener listener) {
        this.listener = listener;
    }

    public void setChannels(List<Channel> newChannels) {
        channels.clear();
        if (newChannels != null) {
            channels.addAll(newChannels);
        }
        notifyDataSetChanged();
    }

    public Channel getChannel(int position) {
        if (position >= 0 && position < channels.size()) {
            return channels.get(position);
        }
        return null;
    }

    public int getSelectedPosition() {
        return selectedPosition;
    }

    public void setSelectedPosition(int position) {
        int old = selectedPosition;
        selectedPosition = position;
        if (old >= 0 && old < channels.size()) notifyItemChanged(old);
        if (selectedPosition >= 0 && selectedPosition < channels.size()) notifyItemChanged(selectedPosition);
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_iptv_channel, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Channel channel = channels.get(position);
        holder.txtIndex.setText(String.format(Locale.ROOT, "#%02d", position + 1));
        holder.txtName.setText(channel.getName());
        
        String country = channel.getCountry();
        String cat = channel.getCategory();
        String meta = cat + (country.isEmpty() ? "" : " • " + country) + " • 1080p";
        holder.txtMeta.setText(meta);

        boolean isCurrent = (position == selectedPosition);
        holder.itemView.setActivated(isCurrent);

        holder.itemView.setOnClickListener(v -> {
            setSelectedPosition(holder.getBindingAdapterPosition());
            if (listener != null) {
                listener.onChannelClick(channel, holder.getBindingAdapterPosition());
            }
        });

        holder.itemView.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) {
                v.animate().scaleX(1.02f).scaleY(1.02f).setDuration(120).start();
                com.gathin.tvhome.audio.CyberSoundSynth.get().playDataClick();
            } else {
                v.animate().scaleX(1.0f).scaleY(1.0f).setDuration(120).start();
            }
        });
    }

    @Override
    public int getItemCount() {
        return channels.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        final TextView txtIndex;
        final TextView txtName;
        final TextView txtMeta;
        final TextView txtLive;

        ViewHolder(View itemView) {
            super(itemView);
            txtIndex = itemView.findViewById(R.id.txtChannelIndex);
            txtName = itemView.findViewById(R.id.txtChannelName);
            txtMeta = itemView.findViewById(R.id.txtChannelMeta);
            txtLive = itemView.findViewById(R.id.txtLiveIndicator);
        }
    }
}
