package com.orcterm.ui;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.orcterm.R;
import com.orcterm.core.docker.DockerVolume;

import java.util.ArrayList;
import java.util.List;

public class DockerVolumeAdapter extends RecyclerView.Adapter<DockerVolumeAdapter.ViewHolder> {

    private List<DockerVolume> volumes = new ArrayList<>();
    private OnVolumeClickListener listener;

    public interface OnVolumeClickListener {
        void onVolumeClick(DockerVolume volume);
        void onVolumeLongClick(DockerVolume volume, View anchor);
    }

    public DockerVolumeAdapter(OnVolumeClickListener listener) {
        this.listener = listener;
    }

    public void setVolumes(List<DockerVolume> volumes) {
        this.volumes = volumes;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_volume, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        holder.bind(volumes.get(position), listener);
    }

    @Override
    public int getItemCount() {
        return volumes.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView name, driver, mount;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            name = itemView.findViewById(R.id.text_name);
            driver = itemView.findViewById(R.id.text_driver);
            mount = itemView.findViewById(R.id.text_mount);
        }

        public void bind(DockerVolume v, OnVolumeClickListener listener) {
            name.setText(v.name);
            driver.setText(v.driver);
            mount.setText(v.mountpoint);

            itemView.setOnClickListener(view -> listener.onVolumeClick(v));
            itemView.setOnLongClickListener(view -> {
                listener.onVolumeLongClick(v, view);
                return true;
            });
        }
    }
}
