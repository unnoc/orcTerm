package com.orcterm.ui;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.orcterm.R;
import com.orcterm.core.sftp.SftpFile;
import com.google.android.material.card.MaterialCardView;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.text.SimpleDateFormat;

public class FileAdapter extends RecyclerView.Adapter<FileAdapter.FileViewHolder> {

    private List<SftpFile> files = new ArrayList<>();
    private final OnFileClickListener listener;
    private int viewMode = 0; // 0: List, 1: Grid
    
    // Selection support
    private boolean isSelectionMode = false;
    private java.util.Set<SftpFile> selectedFiles = new java.util.HashSet<>();

    public interface OnFileClickListener {
        void onFileClick(SftpFile file);
        void onFileLongClick(SftpFile file, View anchor);
        void onSelectionChanged(int count);
        void onFileMenuClick(SftpFile file, View anchor);
    }
    
    public void setSelectionMode(boolean active) {
        this.isSelectionMode = active;
        if (!active) selectedFiles.clear();
        notifyDataSetChanged();
        listener.onSelectionChanged(selectedFiles.size());
    }
    
    public boolean isSelectionMode() {
        return isSelectionMode;
    }
    
    public void toggleSelection(SftpFile file) {
        if (selectedFiles.contains(file)) {
            selectedFiles.remove(file);
        } else {
            selectedFiles.add(file);
        }
        notifyDataSetChanged();
        listener.onSelectionChanged(selectedFiles.size());
    }
    
    public void selectAll() {
        if (!files.isEmpty()) {
            selectedFiles.addAll(files);
            notifyDataSetChanged();
            listener.onSelectionChanged(selectedFiles.size());
        }
    }
    
    public void clearSelection() {
        selectedFiles.clear();
        notifyDataSetChanged();
        listener.onSelectionChanged(0);
    }
    
    public java.util.Set<SftpFile> getSelectedFiles() {
        return new java.util.HashSet<>(selectedFiles);
    }

    public FileAdapter(OnFileClickListener listener) {
        this.listener = listener;
    }

    public void setViewMode(int mode) {
        this.viewMode = mode;
        notifyDataSetChanged();
    }

    public void setFiles(List<SftpFile> list) {
        this.files = list;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public FileViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        int layoutId = viewType == 0 ? R.layout.item_file : R.layout.item_file_grid;
        View view = LayoutInflater.from(parent.getContext())
                .inflate(layoutId, parent, false);
        return new FileViewHolder(view);
    }

    @Override
    public int getItemViewType(int position) {
        return viewMode;
    }

    @Override
    public void onBindViewHolder(@NonNull FileViewHolder holder, int position) {
        holder.bind(files.get(position), listener, isSelectionMode, selectedFiles.contains(files.get(position)));
    }

    @Override
    public int getItemCount() {
        return files.size();
    }

    static class FileViewHolder extends RecyclerView.ViewHolder {
        TextView name, details;
        ImageView icon;
        ImageView moreButton;
        android.widget.CheckBox checkBox;
        MaterialCardView cardView;

        public FileViewHolder(@NonNull View itemView) {
            super(itemView);
            name = itemView.findViewById(R.id.text_name);
            details = itemView.findViewById(R.id.text_details);
            icon = itemView.findViewById(R.id.icon_file);
            checkBox = itemView.findViewById(R.id.checkbox_select);
            moreButton = itemView.findViewById(R.id.btn_more);
            if (itemView instanceof MaterialCardView) {
                cardView = (MaterialCardView) itemView;
            } else {
                View card = itemView.findViewById(R.id.file_card);
                if (card instanceof MaterialCardView) {
                    cardView = (MaterialCardView) card;
                }
            }
        }

        public void bind(SftpFile f, OnFileClickListener listener, boolean isSelectionMode, boolean isSelected) {
            Context context = itemView.getContext();
            name.setText(f.name);
            
            if (checkBox != null) {
                checkBox.setVisibility(isSelectionMode ? View.VISIBLE : View.GONE);
                checkBox.setChecked(isSelected);
            }

            if (cardView != null) {
                int strokeColor = resolveColorAttr(context, isSelectionMode && isSelected ? com.google.android.material.R.attr.colorPrimary : com.google.android.material.R.attr.colorOutline);
                int strokeWidth = dpToPx(context, isSelectionMode && isSelected ? 2 : 1);
                cardView.setStrokeColor(strokeColor);
                cardView.setStrokeWidth(strokeWidth);
            } else if (isSelectionMode) {
                itemView.setBackgroundColor(isSelected ? 0x331976D2 : 0x00000000);
            } else {
                itemView.setBackgroundResource(android.R.drawable.list_selector_background);
            }
            
            if (f.isDir) {
                icon.setImageResource(R.drawable.ic_action_folder);
                icon.setColorFilter(resolveColorAttr(context, com.google.android.material.R.attr.colorPrimary));
                details.setText(formatDetails(f));
            } else {
                icon.setImageResource(R.drawable.ic_action_insert_drive_file);
                int color = resolveColorAttr(context, com.google.android.material.R.attr.colorOnSurfaceVariant);
                String ext = getExtension(f.name);
                switch (ext) {
                    case "jpg": case "jpeg": case "png": case "gif": case "bmp": case "webp":
                        color = 0xFF9C27B0; // Purple
                        break;
                    case "mp4": case "mkv": case "avi": case "mov":
                        color = 0xFFFF9800; // Orange
                        break;
                    case "mp3": case "wav": case "flac": case "aac":
                        color = 0xFFF44336; // Red
                        break;
                    case "zip": case "rar": case "7z": case "tar": case "gz": case "bz2":
                        color = 0xFF4CAF50; // Green
                        break;
                    case "java": case "kt": case "xml": case "json": case "js": case "ts": case "py": case "c": case "cpp": case "h": case "html": case "css": case "md": case "txt":
                        color = 0xFF2196F3; // Blue
                        break;
                    case "pdf": case "doc": case "docx": case "xls": case "xlsx": case "ppt": case "pptx":
                        color = 0xFF00BCD4; // Cyan
                        break;
                }
                icon.setColorFilter(color);
                details.setText(formatDetails(f));
            }

            boolean isHidden = f.name != null && f.name.startsWith(".");
            boolean isExecutable = !f.isDir && f.perm != null && f.perm.contains("x");
            name.setTextColor(isExecutable ? resolveColorAttr(context, com.google.android.material.R.attr.colorPrimary) : resolveColorAttr(context, com.google.android.material.R.attr.colorOnSurface));
            float alpha = isHidden ? 0.55f : 1f;
            name.setAlpha(alpha);
            details.setAlpha(alpha);
            icon.setAlpha(alpha);

            itemView.setOnClickListener(v -> listener.onFileClick(f));
            itemView.setOnLongClickListener(v -> {
                listener.onFileLongClick(f, v);
                return true;
            });
            if (moreButton != null) {
                moreButton.setOnClickListener(v -> {
                    if (isSelectionMode) {
                        listener.onFileClick(f);
                    } else {
                        listener.onFileMenuClick(f, v);
                    }
                });
            }
        }

        private String getExtension(String name) {
            int i = name.lastIndexOf('.');
            if (i > 0) return name.substring(i + 1).toLowerCase();
            return "";
        }

        private String formatSize(long size) {
            if (size < 1024) return size + " B";
            int z = (63 - Long.numberOfLeadingZeros(size)) / 10;
            return String.format("%.1f %sB", (double)size / (1L << (z*10)), " KMGTPE".charAt(z));
        }

        private String formatDetails(SftpFile f) {
            StringBuilder sb = new StringBuilder();
            if (!f.isDir) {
                sb.append(formatSize(f.size)).append(" · ");
            }
            if (f.perm != null && !f.perm.isEmpty()) {
                sb.append(f.perm);
            } else {
                sb.append("-");
            }
            String timeText = formatTime(f.mtime);
            if (!timeText.isEmpty()) {
                sb.append(" · ").append(timeText);
            }
            return sb.toString();
        }

        private String formatTime(long epochSeconds) {
            if (epochSeconds <= 0) return "";
            Date date = new Date(epochSeconds * 1000L);
            SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
            return df.format(date);
        }

        private int resolveColorAttr(Context context, int attr) {
            android.util.TypedValue typedValue = new android.util.TypedValue();
            context.getTheme().resolveAttribute(attr, typedValue, true);
            if (typedValue.resourceId != 0) {
                return ContextCompat.getColor(context, typedValue.resourceId);
            }
            return typedValue.data;
        }

        private int dpToPx(Context context, int dp) {
            return Math.round(dp * context.getResources().getDisplayMetrics().density);
        }
    }
}
