package com.orcterm.ui;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;

import com.orcterm.R;
import com.orcterm.core.docker.DockerVolume;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 数据卷列表适配器
 */
public class DockerVolumeAdapter extends ListAdapter<DockerVolume, DockerVolumeAdapter.ViewHolder> {

    private final OnVolumeClickListener listener;

    public interface OnVolumeClickListener {
        void onVolumeClick(DockerVolume volume);
        void onVolumeLongClick(DockerVolume volume, View anchor);
    }

    public DockerVolumeAdapter(OnVolumeClickListener listener) {
        super(DIFF_CALLBACK);
        this.listener = listener;
        setHasStableIds(true);
    }

    public void setVolumes(List<DockerVolume> volumes) {
        submitList(volumes == null ? new ArrayList<>() : new ArrayList<>(volumes));
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_volume, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        holder.bind(getItem(position), listener);
    }

    @Override
    public long getItemId(int position) {
        DockerVolume item = getItem(position);
        if (item == null || item.name == null) {
            return position;
        }
        return item.name.hashCode();
    }

    static class ViewHolder extends androidx.recyclerview.widget.RecyclerView.ViewHolder {
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

            if (listener != null) {
                itemView.setOnClickListener(view -> listener.onVolumeClick(v));
                itemView.setOnLongClickListener(view -> {
                    listener.onVolumeLongClick(v, view);
                    return true;
                });
            } else {
                itemView.setOnClickListener(null);
                itemView.setOnLongClickListener(null);
            }
        }
    }

    private static final DiffUtil.ItemCallback<DockerVolume> DIFF_CALLBACK = new DiffUtil.ItemCallback<DockerVolume>() {
        @Override
        public boolean areItemsTheSame(@NonNull DockerVolume oldItem, @NonNull DockerVolume newItem) {
            return Objects.equals(oldItem.name, newItem.name);
        }

        @Override
        public boolean areContentsTheSame(@NonNull DockerVolume oldItem, @NonNull DockerVolume newItem) {
            return Objects.equals(oldItem.name, newItem.name)
                    && Objects.equals(oldItem.driver, newItem.driver)
                    && Objects.equals(oldItem.mountpoint, newItem.mountpoint);
        }
    };
}
