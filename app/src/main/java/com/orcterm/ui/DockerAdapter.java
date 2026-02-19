package com.orcterm.ui;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.github.mikephil.charting.charts.PieChart;
import com.github.mikephil.charting.data.PieData;
import com.github.mikephil.charting.data.PieDataSet;
import com.github.mikephil.charting.data.PieEntry;
import com.orcterm.R;
import com.orcterm.core.docker.DockerContainer;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Docker 容器列表适配器
 */
public class DockerAdapter extends ListAdapter<DockerContainer, DockerAdapter.ContainerViewHolder> {
    private final OnContainerClickListener listener;

    public interface OnContainerClickListener {
        void onContainerClick(DockerContainer container);
        void onContainerLongClick(DockerContainer container, View anchor);
    }

    public DockerAdapter() {
        this(null);
    }

    public DockerAdapter(OnContainerClickListener listener) {
        super(DIFF_CALLBACK);
        this.listener = listener;
        setHasStableIds(true);
    }

    public void setContainers(List<DockerContainer> list) {
        submitList(list == null ? new ArrayList<>() : new ArrayList<>(list));
    }

    @NonNull
    @Override
    public ContainerViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_container, parent, false);
        return new ContainerViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ContainerViewHolder holder, int position) {
        holder.bind(getItem(position), listener);
    }

    @Override
    public long getItemId(int position) {
        DockerContainer item = getItem(position);
        if (item == null || item.id == null) {
            return position;
        }
        return item.id.hashCode();
    }

    static class ContainerViewHolder extends RecyclerView.ViewHolder {
        TextView name, image, status, state;
        TextView netUp, netDown, blockRead, blockWrite;
        PieChart cpuChart, memChart;
        android.widget.ImageView stateDot;

        public ContainerViewHolder(@NonNull View itemView) {
            super(itemView);
            name = itemView.findViewById(R.id.text_name);
            image = itemView.findViewById(R.id.text_image);
            status = itemView.findViewById(R.id.text_status);
            state = itemView.findViewById(R.id.text_state); // hidden legacy
            stateDot = itemView.findViewById(R.id.iv_state_dot);
            
            cpuChart = itemView.findViewById(R.id.chart_cpu_item);
            memChart = itemView.findViewById(R.id.chart_mem_item);
            
            netUp = itemView.findViewById(R.id.text_net_up);
            netDown = itemView.findViewById(R.id.text_net_down);
            blockRead = itemView.findViewById(R.id.text_block_read);
            blockWrite = itemView.findViewById(R.id.text_block_write);
            
            setupChart(cpuChart);
            setupChart(memChart);
        }

        private void setupChart(PieChart chart) {
            chart.setUsePercentValues(true);
            chart.getDescription().setEnabled(false);
            chart.getLegend().setEnabled(false);
            chart.setDrawHoleEnabled(true);
            chart.setHoleColor(android.graphics.Color.TRANSPARENT);
            chart.setHoleRadius(80f);
            chart.setTransparentCircleRadius(0f);
            chart.setDrawCenterText(true);
            chart.setCenterTextColor(0xFFFFFFFF);
            chart.setCenterTextSize(12f);
            chart.setCenterTextTypeface(android.graphics.Typeface.DEFAULT_BOLD);
            chart.setTouchEnabled(false); 
            chart.setNoDataText("");
            chart.clearAnimation();
        }

        public void bind(DockerContainer c, OnContainerClickListener listener) {
            name.setText(c.names);
            image.setText(c.image);
            status.setText(c.status);
            
            if ("running".equals(c.state)) {
                if (stateDot != null) stateDot.setColorFilter(0xFF4CAF50); // Green
                cpuChart.setVisibility(View.VISIBLE);
                memChart.setVisibility(View.VISIBLE);
                
                String cpuVal = c.cpuUsage != null ? c.cpuUsage : "0%";
                String memVal = c.memUsage != null ? c.memUsage : "0%";
                
                updateChart(cpuChart, parsePercentage(cpuVal), "CPU");
                updateChart(memChart, parsePercentage(memVal), "MEM");
                
                // Parse Net IO
                if (c.netIO != null && c.netIO.contains("/")) {
                    String[] parts = c.netIO.split("/");
                    if (parts.length >= 2) {
                        netUp.setText(parts[0].trim());
                        netDown.setText(parts[1].trim());
                    }
                } else {
                    netUp.setText("0B");
                    netDown.setText("0B");
                }
                
                // Parse Block IO
                if (c.blockIO != null && c.blockIO.contains("/")) {
                    String[] parts = c.blockIO.split("/");
                    if (parts.length >= 2) {
                        blockRead.setText(parts[0].trim());
                        blockWrite.setText(parts[1].trim());
                    }
                } else {
                    blockRead.setText("0B");
                    blockWrite.setText("0B");
                }
                
            } else {
                if (stateDot != null) stateDot.setColorFilter(0xFFF44336); // Red
                cpuChart.setVisibility(View.INVISIBLE);
                memChart.setVisibility(View.INVISIBLE);
                netUp.setText("-");
                netDown.setText("-");
                blockRead.setText("-");
                blockWrite.setText("-");
            }

            if (listener != null) {
                itemView.setOnClickListener(v -> listener.onContainerClick(c));
                itemView.setOnLongClickListener(v -> {
                    listener.onContainerLongClick(c, v);
                    return true;
                });
            }
        }

        private void updateChart(PieChart chart, float val, String type) {
            ArrayList<PieEntry> entries = new ArrayList<>();
            float free = 100f - val;
            if (free < 0) free = 0;
            
            entries.add(new PieEntry(val, ""));
            entries.add(new PieEntry(free, ""));
            
            PieDataSet dataSet = new PieDataSet(entries, "");
            int color;
            if (val <= 50) color = 0xFF4CAF50; 
            else if (val <= 80) color = 0xFFFFC107; 
            else color = 0xFFF44336;
            
            dataSet.setColors(color, 0xFF333333);
            dataSet.setDrawValues(false);
            
            PieData data = new PieData(dataSet);
            chart.setData(data);
            chart.setCenterText(String.format("%.0f%%", val));
            chart.invalidate();
        }
        
        private float parsePercentage(String s) {
            if (s == null || s.contains("--")) return 0;
            try {
                String val = s.replace("%", "").trim();
                return Float.parseFloat(val);
            } catch (Exception e) {
                return 0;
            }
        }
    }

    private static final DiffUtil.ItemCallback<DockerContainer> DIFF_CALLBACK = new DiffUtil.ItemCallback<DockerContainer>() {
        @Override
        public boolean areItemsTheSame(@NonNull DockerContainer oldItem, @NonNull DockerContainer newItem) {
            return Objects.equals(oldItem.id, newItem.id);
        }

        @Override
        public boolean areContentsTheSame(@NonNull DockerContainer oldItem, @NonNull DockerContainer newItem) {
            return Objects.equals(oldItem.id, newItem.id)
                    && Objects.equals(oldItem.image, newItem.image)
                    && Objects.equals(oldItem.status, newItem.status)
                    && Objects.equals(oldItem.names, newItem.names)
                    && Objects.equals(oldItem.state, newItem.state)
                    && Objects.equals(oldItem.createdAt, newItem.createdAt)
                    && Objects.equals(oldItem.cpuUsage, newItem.cpuUsage)
                    && Objects.equals(oldItem.memUsage, newItem.memUsage)
                    && Objects.equals(oldItem.netIO, newItem.netIO)
                    && Objects.equals(oldItem.blockIO, newItem.blockIO);
        }
    };
}
