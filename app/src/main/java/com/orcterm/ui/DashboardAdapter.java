package com.orcterm.ui;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.orcterm.R;

import java.util.List;

public class DashboardAdapter extends RecyclerView.Adapter<DashboardAdapter.DashboardViewHolder> {

    public static class DashboardItem {
        public String title;
        public String desc;
        public int iconRes;
        public Runnable onClick;

        public DashboardItem(String title, String desc, int iconRes, Runnable onClick) {
            this.title = title;
            this.desc = desc;
            this.iconRes = iconRes;
            this.onClick = onClick;
        }
    }

    private List<DashboardItem> items;

    public DashboardAdapter(List<DashboardItem> items) {
        this.items = items;
    }

    @NonNull
    @Override
    public DashboardViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_dashboard_card, parent, false);
        return new DashboardViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull DashboardViewHolder holder, int position) {
        DashboardItem item = items.get(position);
        holder.tvTitle.setText(item.title);
        holder.tvDesc.setText(item.desc);
        holder.imgIcon.setImageResource(item.iconRes);
        holder.itemView.setOnClickListener(v -> {
            if (item.onClick != null) item.onClick.run();
        });
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class DashboardViewHolder extends RecyclerView.ViewHolder {
        TextView tvTitle, tvDesc;
        ImageView imgIcon;

        public DashboardViewHolder(@NonNull View itemView) {
            super(itemView);
            tvTitle = itemView.findViewById(R.id.tv_title);
            tvDesc = itemView.findViewById(R.id.tv_desc);
            imgIcon = itemView.findViewById(R.id.img_icon);
        }
    }
}
