package com.orcterm.ui;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;

import com.orcterm.R;
import com.orcterm.core.docker.DockerImage;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 镜像列表适配器
 */
public class DockerImageAdapter extends ListAdapter<DockerImage, DockerImageAdapter.ViewHolder> {

    private final OnImageClickListener listener;

    public interface OnImageClickListener {
        void onImageClick(DockerImage image);
        void onImageLongClick(DockerImage image, View anchor);
    }

    public DockerImageAdapter(OnImageClickListener listener) {
        super(DIFF_CALLBACK);
        this.listener = listener;
        setHasStableIds(true);
    }

    public void setImages(List<DockerImage> images) {
        submitList(images == null ? new ArrayList<>() : new ArrayList<>(images));
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_image, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        holder.bind(getItem(position), listener);
    }

    @Override
    public long getItemId(int position) {
        DockerImage item = getItem(position);
        if (item == null || item.id == null) {
            return position;
        }
        return item.id.hashCode();
    }

    static class ViewHolder extends androidx.recyclerview.widget.RecyclerView.ViewHolder {
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

            if (listener != null) {
                itemView.setOnClickListener(v -> listener.onImageClick(i));
                itemView.setOnLongClickListener(v -> {
                    listener.onImageLongClick(i, v);
                    return true;
                });
            } else {
                itemView.setOnClickListener(null);
                itemView.setOnLongClickListener(null);
            }
        }
    }

    private static final DiffUtil.ItemCallback<DockerImage> DIFF_CALLBACK = new DiffUtil.ItemCallback<DockerImage>() {
        @Override
        public boolean areItemsTheSame(@NonNull DockerImage oldItem, @NonNull DockerImage newItem) {
            return Objects.equals(oldItem.id, newItem.id);
        }

        @Override
        public boolean areContentsTheSame(@NonNull DockerImage oldItem, @NonNull DockerImage newItem) {
            return Objects.equals(oldItem.id, newItem.id)
                    && Objects.equals(oldItem.repository, newItem.repository)
                    && Objects.equals(oldItem.tag, newItem.tag)
                    && Objects.equals(oldItem.size, newItem.size)
                    && Objects.equals(oldItem.createdSince, newItem.createdSince);
        }
    };
}
