package com.heme.iptvlive;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import java.util.ArrayList;
import java.util.List;

public final class CastDeviceAdapter extends RecyclerView.Adapter<CastDeviceAdapter.ViewHolder> {
    public interface OnDeviceClickListener {
        void onDeviceClick(DlnaDevice device);
    }

    private final List<DlnaDevice> devices = new ArrayList<>();
    private final OnDeviceClickListener listener;
    private DlnaDevice currentCastDevice;

    public CastDeviceAdapter(OnDeviceClickListener listener) {
        this.listener = listener;
    }

    public void updateDevices(List<DlnaDevice> newDevices, DlnaDevice activeDevice) {
        this.currentCastDevice = activeDevice;
        this.devices.clear();
        this.devices.addAll(newDevices);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_cast_device, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        DlnaDevice device = devices.get(position);
        holder.name.setText(device.getFriendlyName());
        holder.ip.setText(device.getIpAddress() + " · " + device.getModelName());

        boolean isCurrent = currentCastDevice != null && currentCastDevice.getControlUrl().equals(device.getControlUrl());
        if (isCurrent) {
            holder.status.setVisibility(View.VISIBLE);
            holder.status.setText("投屏中");
        } else {
            holder.status.setVisibility(View.GONE);
        }

        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onDeviceClick(device);
            }
        });
    }

    @Override
    public int getItemCount() {
        return devices.size();
    }

    static final class ViewHolder extends RecyclerView.ViewHolder {
        final TextView name;
        final TextView ip;
        final TextView status;

        ViewHolder(View itemView) {
            super(itemView);
            name = itemView.findViewById(R.id.cast_device_name);
            ip = itemView.findViewById(R.id.cast_device_ip);
            status = itemView.findViewById(R.id.cast_device_status);
        }
    }
}
