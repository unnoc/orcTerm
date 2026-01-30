package com.orcterm.ui;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.orcterm.R;
import com.orcterm.core.docker.DockerNetwork;

import java.util.ArrayList;
import java.util.List;

public class DockerNetworkAdapter extends RecyclerView.Adapter<DockerNetworkAdapter.ViewHolder> {

    private List<DockerNetwork> networks = new ArrayList<>();
    private OnNetworkClickListener listener;

    public interface OnNetworkClickListener {
        void onNetworkClick(DockerNetwork network);
        void onNetworkLongClick(DockerNetwork network, View anchor);
    }

    public DockerNetworkAdapter(OnNetworkClickListener listener) {
        this.listener = listener;
    }

    public void setNetworks(List<DockerNetwork> networks) {
        this.networks = networks;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_network, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        holder.bind(networks.get(position), listener);
    }

    @Override
    public int getItemCount() {
        return networks.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
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
            id.setText("ID: " + (n.id.length() > 12 ? n.id.substring(0, 12) : n.id));

            itemView.setOnClickListener(v -> listener.onNetworkClick(n));
            itemView.setOnLongClickListener(v -> {
                listener.onNetworkLongClick(n, v);
                return true;
            });
        }
    }
}
