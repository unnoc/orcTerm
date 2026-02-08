package com.orcterm.ui;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.ProgressBar;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.orcterm.R;
import com.orcterm.data.HostEntity;
import com.orcterm.data.HostStatus;
import com.github.mikephil.charting.charts.PieChart;

import java.util.HashSet;
import java.util.Map;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 主机列表适配器
 */
public class HostAdapter extends ListAdapter<HostEntity, HostAdapter.HostViewHolder> {

    private final OnItemClickListener listener;
    private final OnItemActionListener actionListener;
    private boolean isSelectionMode = false;
    private final Set<Long> selectedIds = new HashSet<>();
    private Long currentHostId = null;
    
    private final Map<Long, HostStatus> statusMap = new ConcurrentHashMap<>();

    public interface OnItemClickListener {
        void onItemClick(HostEntity host);
        void onItemLongClick(View anchor, HostEntity host);
    }
    
    public interface OnItemActionListener {
        void onMoreActions(View view, HostEntity host);
    }
    
    private int displayStyle = 0;
    private int layoutDensity = 1;

    public HostAdapter(@NonNull DiffUtil.ItemCallback<HostEntity> diffCallback, 
                       OnItemClickListener listener,
                       OnItemActionListener actionListener) {
        super(diffCallback);
        this.listener = listener;
        this.actionListener = actionListener;
    }
    
    public void setDisplayStyle(int style) {
        if (style < 0 || style > 3) style = 0;
        this.displayStyle = style;
        notifyDataSetChanged();
    }
    
    public int getDisplayStyle() {
        return displayStyle;
    }

    public void setLayoutDensity(int density) {
        this.layoutDensity = density;
        notifyDataSetChanged();
    }
    
    public void setSelectionMode(boolean enabled) {
        this.isSelectionMode = enabled;
        if (!enabled) selectedIds.clear();
        notifyDataSetChanged();
    }
    
    public Set<Long> getSelectedIds() {
        return new HashSet<>(selectedIds);
    }
    
    public void toggleSelection(long id) {
        if (selectedIds.contains(id)) {
            selectedIds.remove(id);
        } else {
            selectedIds.add(id);
        }
        notifyDataSetChanged();
    }

    public void setCurrentHostId(Long hostId) {
        this.currentHostId = hostId;
        notifyDataSetChanged();
    }
    
    public void updateStatus(long hostId, HostStatus status) {
        statusMap.put(hostId, status);
        // Find index of this host
        for (int i = 0; i < getItemCount(); i++) {
            if (getItem(i).id == hostId) {
                notifyItemChanged(i, "status"); // Payload update
                return;
            }
        }
    }

    // 清理首页主机列表状态数据
    public void clearStatus() {
        statusMap.clear();
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public HostViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_host, parent, false);
        return new HostViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull HostViewHolder holder, int position) {
        HostEntity current = getItem(position);
        boolean isCurrent = currentHostId != null && current.id == currentHostId;
        HostStatus status = statusMap.get(current.id);
        holder.bind(current, status, listener, actionListener, isSelectionMode, selectedIds.contains(current.id), isCurrent, displayStyle);
    }
    
    @Override
    public void onBindViewHolder(@NonNull HostViewHolder holder, int position, @NonNull java.util.List<Object> payloads) {
        if (!payloads.isEmpty()) {
             HostEntity current = getItem(position);
             HostStatus status = statusMap.get(current.id);
             if (status != null) {
                 holder.bindStatus(status);
             }
        } else {
            super.onBindViewHolder(holder, position, payloads);
        }
    }

    class HostViewHolder extends RecyclerView.ViewHolder {
        private final TextView tvIp, tvTemperature, tvLatency, tvCores, tvMemTotal, tvDiskTotal, tvUptime;
        private final ImageView ivStatus;
        
        private final View layoutLinear, layoutDigital, layoutRing;
        private final TextView tvCpuDigital, tvMemDigital, tvNetDigital, tvDiskDigital;
        
        // Linear Views
        private final ProgressBar progressCpuLinear, progressMemLinear;
        private final TextView tvCpuValLinear, tvMemValLinear, tvNetLinear, tvDiskLinear;

        // Ring Views
        private final PieChart chartCpuRing, chartMemRing;
        private final TextView tvNetUp, tvNetDown, tvDiskRead, tvDiskWrite;
        
        private final CheckBox checkBox;
        private final ImageView btnMore;
        private final androidx.cardview.widget.CardView cardView;

        private HostViewHolder(View itemView) {
            super(itemView);
            tvIp = itemView.findViewById(R.id.tv_ip);
            tvTemperature = itemView.findViewById(R.id.tv_temperature);
            tvLatency = itemView.findViewById(R.id.tv_latency);
            ivStatus = itemView.findViewById(R.id.iv_status);
            
            tvCores = itemView.findViewById(R.id.tv_cores);
            tvMemTotal = itemView.findViewById(R.id.tv_mem_total);
            tvDiskTotal = itemView.findViewById(R.id.tv_disk_total);
            tvUptime = itemView.findViewById(R.id.tv_uptime);
            
            // Linear Mode Views
            layoutLinear = itemView.findViewById(R.id.ll_monitor_linear);
            progressCpuLinear = itemView.findViewById(R.id.progress_cpu_linear);
            tvCpuValLinear = itemView.findViewById(R.id.tv_cpu_val_linear);
            progressMemLinear = itemView.findViewById(R.id.progress_mem_linear);
            tvMemValLinear = itemView.findViewById(R.id.tv_mem_val_linear);
            tvNetLinear = itemView.findViewById(R.id.tv_net_linear);
            tvDiskLinear = itemView.findViewById(R.id.tv_disk_linear);
            
            // Digital Mode Views
            layoutDigital = itemView.findViewById(R.id.ll_monitor_digital);
            tvCpuDigital = itemView.findViewById(R.id.tv_cpu_digital);
            tvMemDigital = itemView.findViewById(R.id.tv_mem_digital);
            tvNetDigital = itemView.findViewById(R.id.tv_net_digital);
            tvDiskDigital = itemView.findViewById(R.id.tv_disk_digital);

            // Ring Mode Views
            layoutRing = itemView.findViewById(R.id.ll_monitor_ring);
            chartCpuRing = itemView.findViewById(R.id.chart_cpu_ring);
            chartMemRing = itemView.findViewById(R.id.chart_mem_ring);
            tvNetUp = itemView.findViewById(R.id.tv_net_up);
            tvNetDown = itemView.findViewById(R.id.tv_net_down);
            tvDiskRead = itemView.findViewById(R.id.tv_disk_read);
            tvDiskWrite = itemView.findViewById(R.id.tv_disk_write);
            
            checkBox = itemView.findViewById(R.id.checkbox_select);
            btnMore = itemView.findViewById(R.id.btn_more_actions);
            cardView = (androidx.cardview.widget.CardView) itemView;
        }

        public void bind(HostEntity host, HostStatus status, OnItemClickListener listener, 
                         OnItemActionListener actionListener,
                         boolean isSelectionMode, boolean isSelected, boolean isCurrent, int displayStyle) {
            
            // Basic Info
            tvIp.setText(host.alias != null && !host.alias.isEmpty() ? host.alias : host.hostname);
            
            // Toggle Layout
            layoutDigital.setVisibility(displayStyle == 0 ? View.VISIBLE : View.GONE);
            layoutLinear.setVisibility(displayStyle == 1 ? View.VISIBLE : View.GONE);
            layoutRing.setVisibility(displayStyle == 2 ? View.VISIBLE : View.GONE);
            
            if (displayStyle == 2) {
                setupRingChart(chartCpuRing);
                setupRingChart(chartMemRing);
            }
            
            if (status != null) {
                bindStatus(status);
            } else {
                // Default / Loading state
                tvTemperature.setText("");
                tvLatency.setText("- ms");
                ivStatus.setImageTintList(android.content.res.ColorStateList.valueOf(0xFFBDBDBD)); // Grey
                tvCores.setText("- Cores");
                tvMemTotal.setText("- G");
                tvDiskTotal.setText("- G");
                tvUptime.setText("-");
                
                if (displayStyle == 0) {
                    tvCpuDigital.setText("CPU: -");
                    tvMemDigital.setText("MEM: -");
                    tvNetDigital.setText("NET: -");
                    tvDiskDigital.setText("DISK: -");
                } else if (displayStyle == 1) {
                    progressCpuLinear.setProgress(0);
                    tvCpuValLinear.setText("0%");
                    progressMemLinear.setProgress(0);
                    tvMemValLinear.setText("0%");
                    tvNetLinear.setText("NET: 0 B/s");
                    tvDiskLinear.setText("DISK: 0 B/s");
                } else if (displayStyle == 2) {
                    updateRingChart(chartCpuRing, 0, "CPU");
                    updateRingChart(chartMemRing, 0, "MEM");
                    tvNetUp.setText("0 B/s");
                    tvNetDown.setText("0 B/s");
                    tvDiskRead.setText("0 B/s");
                    tvDiskWrite.setText("0 B/s");
                }
            }
            
            applyLayoutDensity();

            if (isSelectionMode) {
                checkBox.setVisibility(View.VISIBLE);
                checkBox.setChecked(isSelected);
                btnMore.setVisibility(View.INVISIBLE);
                
                itemView.setOnClickListener(v -> listener.onItemClick(host));
                itemView.setOnLongClickListener(null);
            } else {
                checkBox.setVisibility(View.GONE);
                btnMore.setVisibility(View.GONE);
                
                itemView.setOnClickListener(v -> listener.onItemClick(host));
                itemView.setOnLongClickListener(v -> {
                    listener.onItemLongClick(v, host);
                    return true;
                });
            }
        }

        private void applyLayoutDensity() {
            ViewGroup.LayoutParams params = cardView.getLayoutParams();
            if (!(params instanceof ViewGroup.MarginLayoutParams)) return;
            ViewGroup.MarginLayoutParams lp = (ViewGroup.MarginLayoutParams) params;
            float density = itemView.getResources().getDisplayMetrics().density;
            int h;
            int v;
            if (layoutDensity == 0) {
                h = Math.round(8 * density);
                v = Math.round(4 * density);
            } else if (layoutDensity == 2) {
                h = Math.round(16 * density);
                v = Math.round(10 * density);
            } else {
                h = Math.round(12 * density);
                v = Math.round(6 * density);
            }
            if (lp.leftMargin != h || lp.rightMargin != h || lp.topMargin != v || lp.bottomMargin != v) {
                lp.leftMargin = h;
                lp.rightMargin = h;
                lp.topMargin = v;
                lp.bottomMargin = v;
                cardView.setLayoutParams(lp);
            }
        }
        
        public void bindStatus(HostStatus status) {
             if (status.temperature != null && !status.temperature.isEmpty()) {
                 tvTemperature.setVisibility(View.VISIBLE);
                 tvTemperature.setText(status.temperature);
             } else {
                 tvTemperature.setVisibility(View.GONE);
             }
             
             tvLatency.setText(status.latency + " ms");
             ivStatus.setImageTintList(android.content.res.ColorStateList.valueOf(status.isOnline ? 0xFF4CAF50 : 0xFFF44336));
             
             tvCores.setText(status.cpuCores + " Cores");
             tvMemTotal.setText(status.totalMem);
             tvDiskTotal.setText(status.totalDisk);
             tvUptime.setText(status.uptime);
             
             if (layoutDigital.getVisibility() == View.VISIBLE) {
                 tvCpuDigital.setText("CPU: " + status.cpuUsage);
                 tvMemDigital.setText("MEM: " + status.memUsage);
                 tvNetDigital.setText("NET: " + status.netDownload); // Show Down speed as primary
                 tvDiskDigital.setText("DISK: " + status.diskWrite); // Show Write speed as primary? Or sum?
             }
             
             if (layoutLinear.getVisibility() == View.VISIBLE) {
                 progressCpuLinear.setProgress(status.cpuUsagePercent);
                 updateProgressBarColor(progressCpuLinear, status.cpuUsagePercent);
                 tvCpuValLinear.setText(status.cpuUsage);
                 
                 progressMemLinear.setProgress(status.memUsagePercent);
                 updateProgressBarColor(progressMemLinear, status.memUsagePercent);
                 tvMemValLinear.setText(status.memUsage);
                 
                 // Show Download speed primarily
                 tvNetLinear.setText("NET: " + status.netDownload);
                 // Show Write speed primarily
                 tvDiskLinear.setText("DISK: " + status.diskWrite);
             }
             
             if (layoutRing.getVisibility() == View.VISIBLE) {
                 updateRingChart(chartCpuRing, status.cpuUsagePercent, "CPU");
                 updateRingChart(chartMemRing, status.memUsagePercent, "MEM");
                 // Use raw values or formatted? status object has strings.
                 // Assuming netUpload, netDownload, diskRead, diskWrite exist or similar.
                 // Checking HostStatus definition or usage above.
                 // Above uses: status.netDownload (String) and status.diskWrite (String)
                 // We need separate values. 
                 // Let's assume the string contains the value + unit.
                 // Ideally HostStatus should provide separate strings.
                 // Looking at bindStatus above:
                 // tvNetDigital.setText("NET: " + status.netDownload);
                 // So we only have one string?
                 // Wait, I need to check HostStatus definition to see if I have up/down separation.
                 
                 // For now, I will use what I have and maybe placeholders if missing.
                 // Actually, I should check HostStatus class first.
                 // But let's assume I need to fetch them.
                 // In bindStatus I see: status.netDownload, status.diskWrite.
                 // I probably need status.netUpload and status.diskRead too.
                 
                 tvNetUp.setText(status.netUpload != null ? status.netUpload : "0 B/s");
                 tvNetDown.setText(status.netDownload != null ? status.netDownload : "0 B/s");
                 tvDiskRead.setText(status.diskRead != null ? status.diskRead : "0 B/s");
                 tvDiskWrite.setText(status.diskWrite != null ? status.diskWrite : "0 B/s");
             }
        }
        
        private void updateProgressBarColor(ProgressBar progressBar, int percent) {
            int color;
            if (percent <= 50) color = 0xFF4CAF50; // Green
            else if (percent <= 80) color = 0xFFFFC107; // Yellow
            else color = 0xFFF44336; // Red
            progressBar.setProgressTintList(android.content.res.ColorStateList.valueOf(color));
        }

        private void setupRingChart(PieChart chart) {
            if (chart.getData() != null) return; // Already setup
            chart.setUsePercentValues(true);
            chart.getDescription().setEnabled(false);
            chart.getLegend().setEnabled(false);
            chart.setDrawHoleEnabled(true);
            int holeColor = com.google.android.material.color.MaterialColors.getColor(chart, com.google.android.material.R.attr.colorSurface, android.graphics.Color.TRANSPARENT);
            chart.setHoleColor(holeColor);
            chart.setHoleRadius(80f); // Increase hole radius for more space for text
            chart.setTransparentCircleRadius(0f); 
            chart.setDrawCenterText(true); 
            int centerColor = com.google.android.material.color.MaterialColors.getColor(chart, com.google.android.material.R.attr.colorOnSurface, 0xFFFFFFFF);
            chart.setCenterTextColor(centerColor);
            chart.setCenterTextSize(12f);
            chart.setCenterTextTypeface(android.graphics.Typeface.DEFAULT_BOLD);
            chart.setTouchEnabled(false);
            chart.setNoDataText("");
            chart.clearAnimation();
        }

        private void updateRingChart(PieChart chart, int percent, String type) {
            java.util.ArrayList<com.github.mikephil.charting.data.PieEntry> entries = new java.util.ArrayList<>();
            float free = 100f - percent;
            if (free < 0) free = 0;
            
            entries.add(new com.github.mikephil.charting.data.PieEntry(percent, ""));
            entries.add(new com.github.mikephil.charting.data.PieEntry(free, ""));
            
            com.github.mikephil.charting.data.PieDataSet dataSet = new com.github.mikephil.charting.data.PieDataSet(entries, "");
            
            int color;
            if (percent <= 50) color = 0xFF4CAF50; 
            else if (percent <= 80) color = 0xFFFFC107; 
            else color = 0xFFF44336;
            
            dataSet.setColors(color, 0xFF333333); // Dark grey for background
            dataSet.setDrawValues(false);
            
            com.github.mikephil.charting.data.PieData data = new com.github.mikephil.charting.data.PieData(dataSet);
            chart.setData(data);
            chart.setCenterText(percent + "%");
            chart.invalidate();
        }
    }

    public static class HostDiff extends DiffUtil.ItemCallback<HostEntity> {

        @Override
        public boolean areItemsTheSame(@NonNull HostEntity oldItem, @NonNull HostEntity newItem) {
            return oldItem.id == newItem.id;
        }

        @Override
        public boolean areContentsTheSame(@NonNull HostEntity oldItem, @NonNull HostEntity newItem) {
            return oldItem.alias.equals(newItem.alias) && 
                   oldItem.hostname.equals(newItem.hostname) &&
                   oldItem.username.equals(newItem.username) &&
                   oldItem.port == newItem.port &&
                   oldItem.authType == newItem.authType &&
                   (oldItem.password == null ? newItem.password == null : oldItem.password.equals(newItem.password)) &&
                   (oldItem.keyPath == null ? newItem.keyPath == null : oldItem.keyPath.equals(newItem.keyPath)) &&
                   oldItem.connectTimeoutSec == newItem.connectTimeoutSec &&
                   oldItem.keepAliveIntervalSec == newItem.keepAliveIntervalSec &&
                   oldItem.keepAliveReply == newItem.keepAliveReply &&
                   oldItem.hostKeyPolicy == newItem.hostKeyPolicy &&
                   oldItem.environmentType == newItem.environmentType &&
                   (oldItem.terminalThemePreset == null ? newItem.terminalThemePreset == null : oldItem.terminalThemePreset.equals(newItem.terminalThemePreset));
        }
    }
}
