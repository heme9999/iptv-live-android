package com.heme.iptvlive;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import java.util.List;

final class ChannelAdapter extends RecyclerView.Adapter<ChannelAdapter.Holder> {
    private List<Channel> channels;
    interface OnChannelClick { void accept(Channel channel); }
    private final OnChannelClick onClick;
    private int selected = -1;

    ChannelAdapter(List<Channel> channels, OnChannelClick onClick) {
        this.channels = channels;
        this.onClick = onClick;
    }

    void replace(List<Channel> replacement) {
        channels = replacement;
        selected = -1;
        notifyDataSetChanged();
    }

    void refresh(Channel channel) {
        int index = channels.indexOf(channel);
        if (index >= 0) notifyItemChanged(index);
    }

    @NonNull @Override public Holder onCreateViewHolder(@NonNull ViewGroup parent, int type) {
        return new Holder(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_channel, parent, false));
    }

    @Override public void onBindViewHolder(@NonNull Holder holder, int position) {
        Channel channel = channels.get(position);
        String latency = channel.latencyMs == -2 ? "测速中" : channel.latencyMs < 0 ? "超时" : channel.latencyMs + " ms";
        holder.name.setText(String.format(java.util.Locale.getDefault(), "%02d  %s\n%s  ·  HD  ·  %s", position + 1, channel.name, channel.group, latency));
        holder.itemView.setSelected(position == selected);
        holder.itemView.setOnClickListener(v -> {
            int old = selected;
            selected = holder.getBindingAdapterPosition();
            if (old >= 0) notifyItemChanged(old);
            notifyItemChanged(selected);
            onClick.accept(channel);
        });
    }

    @Override public int getItemCount() { return channels.size(); }

    static final class Holder extends RecyclerView.ViewHolder {
        final TextView name;
        Holder(View view) { super(view); name = view.findViewById(R.id.channel_name); }
    }
}
