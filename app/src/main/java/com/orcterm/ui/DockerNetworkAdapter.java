package com.orcterm.ui;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;

import com.orcterm.R;
import com.orcterm.core.docker.DockerNetwork;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 网络列表适配器
 */
public class DockerNetworkAdapter extends ListAdapter<DockerNetwork, DockerNetworkAdapter.ViewHolder> {

    private final OnNetworkClickListener listener;

    public interface OnNetworkClickListener {
        void onNetworkClick(DockerNetwork network);
        void onNetworkLongClick(DockerNetwork network, View anchor);
    }

    public DockerNetworkAdapter(OnNetworkClickListener listener) {
        super(DIFF_CALLBACK);
        this.listener = listener;
        setHasStableIds(true);
    }

    public void setNetworks(List<DockerNetwork> networks) {
        submitList(networks == null ? new ArrayList<>() : new ArrayList<>(networks));
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_network, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        holder.bind(getItem(position), listener);
    }

    @Override
    public long getItemId(int position) {
        DockerNetwork item = getItem(position);
        if (item == null || item.id == null) {
            return position;
        }
        return item.id.hashCode();
    }

    static class ViewHolder extends androidx.recyclerview.widget.RecyclerView.ViewHolder {
        TextView name, driver, id;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            name = itemView.findViewById(R.id.text_name);
            driver = itemView.findViewById(R.id.text_driver);
            id = itemView.findViewById(R.id.text_id);
        }

        public void bind(DockerNetwork n, OnNetworkClickListener listener) {
            name.setText(n.name);
            driver.setText(n.driver);
            String networkId = n.id == null ? "" : n.id;
            id.setText("ID: " + (networkId.length() > 12 ? networkId.substring(0, 12) : networkId));

            if (listener != null) {
                itemView.setOnClickListener(v -> listener.onNetworkClick(n));
                itemView.setOnLongClickListener(v -> {
                    listener.onNetworkLongClick(n, v);
                    return true;
                });
            } else {
                itemView.setOnClickListener(null);
                itemView.setOnLongClickListener(null);
            }
        }
    }

    private static final DiffUtil.ItemCallback<DockerNetwork> DIFF_CALLBACK = new DiffUtil.ItemCallback<DockerNetwork>() {
        @Override
        public boolean areItemsTheSame(@NonNull DockerNetwork oldItem, @NonNull DockerNetwork newItem) {
            return Objects.equals(oldItem.id, newItem.id);
        }

        @Override
        public boolean areContentsTheSame(@NonNull DockerNetwork oldItem, @NonNull DockerNetwork newItem) {
            return Objects.equals(oldItem.id, newItem.id)
                    && Objects.equals(oldItem.name, newItem.name)
                    && Objects.equals(oldItem.driver, newItem.driver)
                    && Objects.equals(oldItem.scope, newItem.scope);
        }
    };
}
