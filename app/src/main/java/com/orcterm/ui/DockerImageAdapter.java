package com.orcterm.ui;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.orcterm.R;
import com.orcterm.core.docker.DockerImage;

import java.util.ArrayList;
import java.util.List;

/**
 * 镜像列表适配器
 */
public class DockerImageAdapter extends RecyclerView.Adapter<DockerImageAdapter.ViewHolder> {

    private List<DockerImage> images = new ArrayList<>();
    private OnImageClickListener listener;

    public interface OnImageClickListener {
        void onImageClick(DockerImage image);
        void onImageLongClick(DockerImage image, View anchor);
    }

    public DockerImageAdapter(OnImageClickListener listener) {
        this.listener = listener;
    }

    public void setImages(List<DockerImage> images) {
        this.images = images;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_image, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        holder.bind(images.get(position), listener);
    }

    @Override
    public int getItemCount() {
        return images.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView repo, tag, size, created;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            repo = itemView.findViewById(R.id.text_repo);
            tag = itemView.findViewById(R.id.text_tag);
            size = itemView.findViewById(R.id.text_size);
            created = itemView.findViewById(R.id.text_created);
        }

        public void bind(DockerImage i, OnImageClickListener listener) {
            repo.setText(i.repository);
            tag.setText(i.tag);
            size.setText(i.size);
            created.setText(i.createdSince);

            itemView.setOnClickListener(v -> listener.onImageClick(i));
            itemView.setOnLongClickListener(v -> {
                listener.onImageLongClick(i, v);
                return true;
            });
        }
    }
}
