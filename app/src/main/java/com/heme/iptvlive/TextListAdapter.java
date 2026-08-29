package com.heme.iptvlive;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import java.util.List;

final class TextListAdapter extends RecyclerView.Adapter<TextListAdapter.Holder> {
    private final List<String> items;
    interface OnTextClick { void accept(String value); }
    private final OnTextClick onClick;
    private int selected = -1;
    TextListAdapter(List<String> items, OnTextClick onClick) { this.items = items; this.onClick = onClick; }
    @NonNull @Override public Holder onCreateViewHolder(@NonNull ViewGroup parent, int type) { return new Holder(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_channel, parent, false)); }
    @Override public void onBindViewHolder(@NonNull Holder holder, int position) {
        holder.text.setText(items.get(position));
        holder.itemView.setSelected(position == selected);
        holder.itemView.setOnClickListener(v -> { int old = selected; selected = holder.getBindingAdapterPosition(); if (old >= 0) notifyItemChanged(old); notifyItemChanged(selected); onClick.accept(items.get(selected)); });
    }
    @Override public int getItemCount() { return items.size(); }
    static final class Holder extends RecyclerView.ViewHolder { final TextView text; Holder(View view) { super(view); text = view.findViewById(R.id.channel_name); } }
}
