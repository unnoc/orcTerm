package com.orcterm.ui;

import android.graphics.Color;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.charts.PieChart;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.data.PieData;
import com.github.mikephil.charting.data.PieDataSet;
import com.github.mikephil.charting.data.PieEntry;
import com.google.android.material.color.MaterialColors;
import com.orcterm.R;
import com.orcterm.core.ssh.SshNative;
import com.orcterm.data.AppDatabase;
import com.orcterm.data.HostEntity;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MonitorActivity extends AppCompatActivity {

    private LineChart chartNetwork;
    private PieChart chartDisk;
    private TextView tvSystemLoad;
    private TextView tvDiskTitle, tvDiskTree;
    private TextView tvCpuPercent, tvMemUsage, tvMemDetailSmall, tvDiskPercent, tvDiskUsed, tvDiskTotal, tvNetSpeed;
    private ProgressBar progressCpu, progressMem;
    private RecyclerView rvProcesses;
    private ImageButton btnRefresh;
    private TextView tvSortCpu;
    private TextView tvSortMem;
    private TextView tvSortConn;
    private ProcessAdapter processAdapter;

    private String lsblkOutput = "";
    private Handler handler = new Handler(Looper.getMainLooper());
    private ExecutorService executor = Executors.newSingleThreadExecutor();
    private boolean isMonitoring = false;
    private static final int REFRESH_INTERVAL = 3000; // 3s interval for real SSH

    private long hostId;
    private HostEntity currentHost;
    private SshNative ssh;
    private long sshHandle = 0;

    // Stats history
    private List<Entry> netDownloadEntries = new ArrayList<>();
    private List<ProcessInfo> processItems = new ArrayList<>();

    private int processSortMode = 0;
    
    private List<DiskInfo> diskList = new ArrayList<>();
    private int currentDiskIndex = 0;

    private static class DiskInfo {
        String filesystem;
        long total;
        long used;
        long available;
        String mountPoint;

        DiskInfo(String fs, long t, long u, long a, String mp) {
            filesystem = fs; total = t; used = u; available = a; mountPoint = mp;
        }
        
        @Override
        public String toString() {
            return filesystem + " (" + mountPoint + ")";
        }
    }
    
    // Prev values for calculation
    private long prevCpuTotal = 0, prevCpuWork = 0;
    private long prevNetRx = 0, prevNetTx = 0;
    private long prevTimestamp = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_monitor);

        hostId = getIntent().getLongExtra("host_id", -1);
        if (hostId == -1) {
            Toast.makeText(this, "Invalid Host ID", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        setSupportActionBar(findViewById(R.id.toolbar));
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle(""); 
        }

        initViews();
        initCharts();
        
        ssh = new SshNative();
        loadHostAndStart();
    }

    private void loadHostAndStart() {
        executor.execute(() -> {
            currentHost = AppDatabase.getDatabase(this).hostDao().findById(hostId);
            if (currentHost == null) {
                runOnUiThread(() -> {
                    Toast.makeText(this, "Host not found", Toast.LENGTH_SHORT).show();
                    finish();
                });
                return;
            }
            startMonitoring();
        });
    }

    private void initViews() {
        chartNetwork = findViewById(R.id.chart_network);
        chartDisk = findViewById(R.id.chart_disk);
        
        tvSystemLoad = findViewById(R.id.tv_system_load);
        tvCpuPercent = findViewById(R.id.tv_cpu_percent);
        tvMemUsage = findViewById(R.id.tv_mem_usage);
        tvMemDetailSmall = findViewById(R.id.tv_mem_detail_small);
        tvDiskPercent = findViewById(R.id.tv_disk_percent);
        tvDiskUsed = findViewById(R.id.tv_disk_used);
        tvDiskTotal = findViewById(R.id.tv_disk_total);
        tvNetSpeed = findViewById(R.id.tv_net_speed);
        tvDiskTitle = findViewById(R.id.tv_disk_title);
        tvDiskTree = findViewById(R.id.tv_disk_tree);
        tvDiskTitle.setOnClickListener(v -> showDiskSelector());
        
        progressCpu = findViewById(R.id.progress_cpu);
        progressMem = findViewById(R.id.progress_mem);
        
        rvProcesses = findViewById(R.id.rv_processes);
        rvProcesses.setLayoutManager(new LinearLayoutManager(this));
        processAdapter = new ProcessAdapter();
        rvProcesses.setAdapter(processAdapter);
        
        tvSortCpu = findViewById(R.id.tv_sort_cpu);
        tvSortMem = findViewById(R.id.tv_sort_mem);
        tvSortConn = findViewById(R.id.tv_sort_conn);

        tvSortCpu.setOnClickListener(v -> {
            processSortMode = 0;
            sortProcessItems();
            processAdapter.notifyDataSetChanged();
        });
        tvSortMem.setOnClickListener(v -> {
            processSortMode = 1;
            sortProcessItems();
            processAdapter.notifyDataSetChanged();
        });
        tvSortConn.setOnClickListener(v -> {
            processSortMode = 2;
            sortProcessItems();
            processAdapter.notifyDataSetChanged();
        });

        btnRefresh = findViewById(R.id.btn_refresh);
        btnRefresh.setOnClickListener(v -> {
            executor.execute(this::fetchStats);
            Toast.makeText(this, "Refreshed", Toast.LENGTH_SHORT).show();
        });
    }

    private void initCharts() {
        setupNetworkChart();
        setupDiskChart();
    }

    private void setupNetworkChart() {
        chartNetwork.getDescription().setEnabled(false);
        chartNetwork.setTouchEnabled(false);
        chartNetwork.setDragEnabled(false);
        chartNetwork.setScaleEnabled(false);
        chartNetwork.setPinchZoom(false);
        chartNetwork.setBackgroundColor(Color.TRANSPARENT);
        chartNetwork.getLegend().setEnabled(false);
        chartNetwork.setNoDataText("");
        chartNetwork.setHighlightPerDragEnabled(false);
        chartNetwork.setHighlightPerTapEnabled(false);
        chartNetwork.setAutoScaleMinMaxEnabled(true);
        
        XAxis xAxis = chartNetwork.getXAxis();
        xAxis.setEnabled(false);
        
        YAxis leftAxis = chartNetwork.getAxisLeft();
        leftAxis.setEnabled(false);
        
        YAxis rightAxis = chartNetwork.getAxisRight();
        rightAxis.setEnabled(false);
        
        chartNetwork.setViewPortOffsets(0, 0, 0, 0);
    }
    
    private void setupDiskChart() {
        chartDisk.getDescription().setEnabled(false);
        chartDisk.setTouchEnabled(false);
        chartDisk.setDrawHoleEnabled(true);
        int holeColor = MaterialColors.getColor(chartDisk, com.google.android.material.R.attr.colorSurface, Color.TRANSPARENT);
        chartDisk.setHoleColor(holeColor);
        chartDisk.setTransparentCircleColor(Color.TRANSPARENT);
        chartDisk.setTransparentCircleAlpha(0);
        chartDisk.setHoleRadius(82f);
        chartDisk.setDrawCenterText(false);
        chartDisk.getLegend().setEnabled(false);
        chartDisk.setRotationEnabled(false);
        chartDisk.setHighlightPerTapEnabled(false);
        chartDisk.setDrawEntryLabels(false);
    }

    private void startMonitoring() {
        if (isMonitoring) return;
        isMonitoring = true;
        executor.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    connectSsh();
                    while (isMonitoring) {
                        fetchStats();
                        Thread.sleep(REFRESH_INTERVAL);
                    }
                } catch (Exception e) {
                    runOnUiThread(() -> Toast.makeText(MonitorActivity.this, "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show());
                }
            }
        });
    }

    private void connectSsh() throws Exception {
        if (sshHandle != 0) return;
        sshHandle = ssh.connect(currentHost.hostname, currentHost.port);
        if (sshHandle == 0) throw new Exception("Connect failed");
        
        int auth;
        if (currentHost.authType == 1 && currentHost.keyPath != null) {
            auth = ssh.authKey(sshHandle, currentHost.username, currentHost.keyPath);
        } else {
            auth = ssh.authPassword(sshHandle, currentHost.username, currentHost.password);
        }
        
        if (auth != 0) {
            ssh.disconnect(sshHandle);
            sshHandle = 0;
            throw new Exception("Auth failed");
        }
    }

    private void fetchStats() {
        try {
            // Updated command to include cleaner lsblk output and robust loadavg
            String cmd = "echo 'SECTION_LOAD'; cat /proc/loadavg; echo 'SECTION_CPU'; grep 'cpu ' /proc/stat; echo 'SECTION_MEM'; cat /proc/meminfo; echo 'SECTION_NET'; cat /proc/net/dev; echo 'SECTION_DISK_INFO'; lsblk -o NAME,FSTYPE,SIZE,MOUNTPOINT; echo 'SECTION_DISK_USAGE'; df -P -B1; echo 'SECTION_CONN'; ss -Htp 2>/dev/null || true; echo 'SECTION_PROCESS'; ps -eo pid,pcpu,pmem,stat,lstart,comm,args --sort=-pcpu | head -n 20";
            String output = ssh.exec(sshHandle, cmd);
            parseStats(output);
        } catch (Exception e) {
            // Log or ignore
        }
    }

    private void stopMonitoring() {
        isMonitoring = false;
        // Don't disconnect here immediately, let the thread exit loop then disconnect or in onDestroy
    }

    private void parseStats(String output) {
        String[] sections = output.split("SECTION_");
        long now = System.currentTimeMillis();
        Map<Integer, Integer> connCounts = new HashMap<>();
        
        // 1. LOAD
        if (sections.length > 1) {
            String[] lines = sections[1].split("\n");
            for (String l : lines) {
                 if (l.startsWith("LOAD")) continue;
                 l = l.trim();
                 if (l.isEmpty()) continue;
                 // /proc/loadavg: 0.00 0.00 0.00 1/123 12345
                 String[] parts = l.split("\\s+");
                 if (parts.length >= 3) {
                     try {
                         // Validate they are numbers
                         Double.parseDouble(parts[0]);
                         String load = parts[0] + " " + parts[1] + " " + parts[2];
                         runOnUiThread(() -> {
                             if (tvSystemLoad != null) tvSystemLoad.setText(load);
                         });
                         break;
                     } catch (Exception e) {}
                 }
            }
        }

        // 2. CPU
        if (sections.length > 2) {
            String[] lines = sections[2].split("\n");
            for (String l : lines) {
                if (l.startsWith("cpu ")) {
                    String[] parts = l.trim().split("\\s+");
                    if (parts.length >= 5) {
                        long user = Long.parseLong(parts[1]);
                        long nice = Long.parseLong(parts[2]);
                        long system = Long.parseLong(parts[3]);
                        long idle = Long.parseLong(parts[4]);
                        long iowait = parts.length > 5 ? Long.parseLong(parts[5]) : 0;
                        long irq = parts.length > 6 ? Long.parseLong(parts[6]) : 0;
                        long softirq = parts.length > 7 ? Long.parseLong(parts[7]) : 0;
                        long steal = parts.length > 8 ? Long.parseLong(parts[8]) : 0;
                        
                        long total = user + nice + system + idle + iowait + irq + softirq + steal;
                        long work = user + nice + system + irq + softirq + steal;
                        
                        if (prevCpuTotal > 0) {
                            long totalDelta = total - prevCpuTotal;
                            long workDelta = work - prevCpuWork;
                            if (totalDelta > 0) {
                                int cpuPercent = (int) (workDelta * 100 / totalDelta);
                                runOnUiThread(() -> updateCpuUI(cpuPercent));
                            }
                        }
                        prevCpuTotal = total;
                        prevCpuWork = work;
                    }
                    break; 
                }
            }
        }
        
        // 3. MEM
        if (sections.length > 3) {
            String[] lines = sections[3].split("\n");
            long total = 0, available = 0;
            for (String l : lines) {
                if (l.startsWith("MemTotal:")) {
                    String[] parts = l.split("\\s+");
                    if (parts.length >= 2) total = Long.parseLong(parts[1]);
                } else if (l.startsWith("MemAvailable:")) {
                    String[] parts = l.split("\\s+");
                    if (parts.length >= 2) available = Long.parseLong(parts[1]);
                }
            }
            if (total > 0) {
                long used = total - available;
                long finalTotal = total;
                runOnUiThread(() -> updateMemUI(used, finalTotal));
            }
        }
        
        // 4. NET
        if (sections.length > 4) {
             long rx = 0, tx = 0;
             String[] lines = sections[4].split("\n");
             for (String l : lines) {
                 if (l.contains(":")) {
                     String[] parts = l.split(":")[1].trim().split("\\s+");
                     if (parts.length >= 9) {
                         rx += Long.parseLong(parts[0]);
                         tx += Long.parseLong(parts[8]); 
                     }
                 }
             }
             if (prevTimestamp > 0) {
                 long timeDelta = now - prevTimestamp;
                 if (timeDelta > 0) {
                     long rxDelta = rx - prevNetRx;
                     long txDelta = tx - prevNetTx;
                     // Convert to MB/s
                     float rxSpeed = (float)rxDelta / timeDelta * 1000 / 1024 / 1024;
                     float txSpeed = (float)txDelta / timeDelta * 1000 / 1024 / 1024;
                     runOnUiThread(() -> updateNetUI(rxSpeed, txSpeed));
                 }
             }
             prevNetRx = rx;
             prevNetTx = tx;
             prevTimestamp = now;
        }

        // 5. DISK INFO (lsblk)
        if (sections.length > 5) {
            StringBuilder sb = new StringBuilder();
            String[] lines = sections[5].split("\n");
            for (String l : lines) {
                if (l.startsWith("DISK_INFO")) continue;
                sb.append(l).append("\n");
            }
            lsblkOutput = sb.toString();
            runOnUiThread(() -> {
                if (tvDiskTree != null) {
                    tvDiskTree.setText(lsblkOutput);
                    tvDiskTree.setVisibility(View.VISIBLE);
                }
            });
        }
        
        // 6. DISK USAGE (df)
        if (sections.length > 6) {
             String[] lines = sections[6].split("\n");
             List<DiskInfo> newDisks = new ArrayList<>();
             for (String l : lines) {
                 l = l.trim();
                 if (l.isEmpty()) continue;
                 if (l.startsWith("Filesystem") || l.startsWith("DISK_USAGE")) continue;
                 String[] parts = l.split("\\s+");
                 if (parts.length >= 6) {
                     try {
                         // Filesystem 1024-blocks Used Available Capacity Mounted on
                         String fs = parts[0];
                         long total = Long.parseLong(parts[1]);
                         long used = Long.parseLong(parts[2]);
                         long avail = Long.parseLong(parts[3]);
                         String mount = parts[5];

                         // Filter logic
                         if (fs.equals("tmpfs") || fs.equals("devtmpfs") || fs.equals("overlay") || 
                             fs.equals("squashfs") || fs.equals("aufs") || fs.equals("none")) continue;
                         if (fs.startsWith("/dev/loop")) continue;
                         if (mount.startsWith("/sys") || mount.startsWith("/proc") || 
                             mount.startsWith("/dev") || mount.startsWith("/run") || 
                             mount.startsWith("/snap") || mount.startsWith("/boot/efi")) continue;

                         newDisks.add(new DiskInfo(fs, total, used, avail, mount));
                     } catch (Exception e) {}
                 }
             }
             // Sort: root first, then alphabetical
             Collections.sort(newDisks, (o1, o2) -> {
                 if (o1.mountPoint.equals("/")) return -1;
                 if (o2.mountPoint.equals("/")) return 1;
                 return o1.mountPoint.compareTo(o2.mountPoint);
             });
             if (!newDisks.isEmpty()) {
                 runOnUiThread(() -> updateDiskList(newDisks));
             }
        }
        
        // 7. CONN
        if (sections.length > 7) {
            String[] lines = sections[7].split("\n");
            for (String line : lines) {
                int index = 0;
                while ((index = line.indexOf("pid=", index)) != -1) {
                    int start = index + 4;
                    int end = start;
                    while (end < line.length() && Character.isDigit(line.charAt(end))) end++;
                    if (end > start) {
                        try {
                            int pid = Integer.parseInt(line.substring(start, end));
                            connCounts.put(pid, connCounts.getOrDefault(pid, 0) + 1);
                        } catch (Exception e) {
                        }
                    }
                    index = end;
                }
            }
        }

        // 8. PROCESS
        if (sections.length > 8) {
             String[] lines = sections[8].split("\n");
             List<ProcessInfo> newProcs = new ArrayList<>();
             for (int i=0; i<lines.length; i++) { 
                 String l = lines[i].trim();
                 if (l.isEmpty() || l.startsWith("PROCESS") || l.startsWith("PID")) continue;
                 String[] parts = l.split("\\s+");
                 if (parts.length >= 10) {
                     try {
                         ProcessInfo p = new ProcessInfo();
                         p.pid = Integer.parseInt(parts[0]);
                         p.cpu = Float.parseFloat(parts[1]);
                         p.mem = Float.parseFloat(parts[2]); // This is %MEM
                         p.status = parts[3];
                         
                         // Parse and format time
                         // lstart format: Mon Jan 29 10:00:00 2024
                         String rawTime = parts[4] + " " + parts[5] + " " + parts[6] + " " + parts[7] + " " + parts[8];
                         try {
                             java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("EEE MMM dd HH:mm:ss yyyy", java.util.Locale.US);
                             java.util.Date date = sdf.parse(rawTime);
                             if (date != null) {
                                 java.util.Calendar calNow = java.util.Calendar.getInstance();
                                java.util.Calendar pDate = java.util.Calendar.getInstance();
                                pDate.setTime(date);
                                
                                if (calNow.get(java.util.Calendar.YEAR) == pDate.get(java.util.Calendar.YEAR) &&
                                    calNow.get(java.util.Calendar.DAY_OF_YEAR) == pDate.get(java.util.Calendar.DAY_OF_YEAR)) {
                                    // Today: HH:mm
                                    p.startTime = new java.text.SimpleDateFormat("HH:mm", java.util.Locale.US).format(date);
                                } else if (calNow.get(java.util.Calendar.YEAR) == pDate.get(java.util.Calendar.YEAR)) {
                                    // This year: MM-dd
                                    p.startTime = new java.text.SimpleDateFormat("MM-dd HH:mm", java.util.Locale.US).format(date);
                                } else {
                                     // Other: yyyy-MM-dd
                                     p.startTime = new java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(date);
                                 }
                             } else {
                                 p.startTime = rawTime;
                             }
                         } catch (Exception e) {
                             p.startTime = rawTime;
                         }

                         // Simple name: just the command (parts[9])
                         p.name = parts[9];
                         // If name is wrapped in [], keep it (kernel threads)
                         
                         p.conn = connCounts.getOrDefault(p.pid, 0);
                         newProcs.add(p);
                     } catch (Exception e) {}
                 }
             }
             runOnUiThread(() -> updateProcessUI(newProcs));
        }
    }

    private void updateCpuUI(int percent) {
        tvCpuPercent.setText(String.valueOf(percent));
        progressCpu.setProgress(percent);
    }

    private void updateMemUI(long usedKb, long totalKb) {
        float totalGb = totalKb / 1024f / 1024f;
        float usedGb = usedKb / 1024f / 1024f;
        int percent = (int) (usedKb * 100 / totalKb);
        
        tvMemUsage.setText(String.format("%.1f", usedGb));
        tvMemDetailSmall.setText(String.format("%d%% of %.0fGB", percent, totalGb));
        progressMem.setProgress(percent);
    }

    private void updateNetUI(float downMb, float upMb) {
        tvNetSpeed.setText(String.format("↓ %.1fMB/s ↑ %.1fMB/s", downMb, upMb));
        
        // Chart
        if (netDownloadEntries.size() > 20) netDownloadEntries.remove(0);
        // Re-index
        for (int i=0; i < netDownloadEntries.size(); i++) {
             netDownloadEntries.get(i).setX(i);
        }
        netDownloadEntries.add(new Entry(netDownloadEntries.size(), downMb));
        
        LineData data = chartNetwork.getData();
        if (data == null) {
            LineDataSet set = createNetDataSet(netDownloadEntries);
            chartNetwork.setData(new LineData(set));
        } else {
            LineDataSet set = (LineDataSet) data.getDataSetByIndex(0);
            if (set == null) {
                set = createNetDataSet(netDownloadEntries);
                data.addDataSet(set);
            } else {
                set.setValues(netDownloadEntries);
            }
            data.notifyDataChanged();
            chartNetwork.notifyDataSetChanged();
        }
        chartNetwork.invalidate();
    }

    private LineDataSet createNetDataSet(List<Entry> entries) {
        LineDataSet set = new LineDataSet(entries, "Traffic");
        int lineColor = MaterialColors.getColor(chartNetwork, com.google.android.material.R.attr.colorPrimary, Color.parseColor("#58D1FF"));
        int fillColor = MaterialColors.getColor(chartNetwork, com.google.android.material.R.attr.colorPrimaryContainer, lineColor);
        set.setMode(LineDataSet.Mode.CUBIC_BEZIER);
        set.setCubicIntensity(0.2f);
        set.setDrawFilled(true);
        set.setDrawCircles(false);
        set.setLineWidth(2f);
        set.setDrawValues(false);
        set.setColor(lineColor);
        set.setFillColor(fillColor);
        set.setFillAlpha(140);
        set.setDrawHorizontalHighlightIndicator(false);
        set.setDrawVerticalHighlightIndicator(false);
        return set;
    }

    private void updateDiskList(List<DiskInfo> newDisks) {
        if (newDisks.isEmpty()) return;
        
        String currentMount = null;
        if (!diskList.isEmpty() && currentDiskIndex < diskList.size()) {
            currentMount = diskList.get(currentDiskIndex).mountPoint;
        }
        
        diskList = newDisks;
        
        if (currentMount != null) {
            boolean found = false;
            for (int i = 0; i < diskList.size(); i++) {
                if (diskList.get(i).mountPoint.equals(currentMount)) {
                    currentDiskIndex = i;
                    found = true;
                    break;
                }
            }
            if (!found) currentDiskIndex = 0;
        } else {
            currentDiskIndex = 0;
        }
        
        updateDiskUI();
    }
    
    private void showDiskSelector() {
        if (diskList.isEmpty()) return;
        
        String[] items = new String[diskList.size() + 1];
        for (int i = 0; i < diskList.size(); i++) {
            items[i] = diskList.get(i).toString();
        }
        items[diskList.size()] = getString(R.string.monitor_disk_details); // "Show Device Tree" or similar localized string
        
        new androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle(R.string.monitor_disk_switch)
            .setItems(items, (dialog, which) -> {
                if (which == diskList.size()) {
                    showDiskDetails();
                } else {
                    currentDiskIndex = which;
                    updateDiskUI();
                }
            })
            .show();
    }

    private void showDiskDetails() {
        android.widget.TextView tv = new android.widget.TextView(this);
        tv.setText(lsblkOutput);
        tv.setTypeface(android.graphics.Typeface.MONOSPACE);
        tv.setPadding(32, 32, 32, 32);
        tv.setTextSize(12);
        tv.setTextColor(MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurface, Color.BLACK));
        
        android.widget.HorizontalScrollView hsv = new android.widget.HorizontalScrollView(this);
        hsv.addView(tv);

        android.widget.ScrollView sv = new android.widget.ScrollView(this);
        sv.addView(hsv);

        new androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle(R.string.monitor_disk_details)
            .setView(sv)
            .setPositiveButton(R.string.action_ok, null)
            .show();
    }

    private void updateDiskUI() {
        if (diskList.isEmpty() || currentDiskIndex >= diskList.size()) return;
        DiskInfo disk = diskList.get(currentDiskIndex);
        long usedBytes = disk.used;
        long totalBytes = disk.total;
        
        tvDiskTitle.setText(String.format(getString(R.string.monitor_disk_space_fmt), disk.filesystem));

        float totalGb = totalBytes / 1024f / 1024f / 1024f;
        float usedGb = usedBytes / 1024f / 1024f / 1024f;
        int percent = (totalBytes > 0) ? (int) (usedBytes * 100 / totalBytes) : 0;
        
        tvDiskPercent.setText(String.format("%d%%", percent));
        tvDiskUsed.setText(String.format("%.1f GB", usedGb));
        tvDiskTotal.setText(String.format("Total Capacity %.1fGB", totalGb));
        
        PieData data = chartDisk.getData();
        if (data != null) {
            PieDataSet set = (PieDataSet) data.getDataSet();
            ArrayList<PieEntry> entries = new ArrayList<>();
            entries.add(new PieEntry(usedBytes, "Used"));
            entries.add(new PieEntry(totalBytes - usedBytes, "Free"));
            int colorUsed = MaterialColors.getColor(chartDisk, com.google.android.material.R.attr.colorPrimary, Color.parseColor("#58D1FF"));
            int colorFree = MaterialColors.getColor(chartDisk, com.google.android.material.R.attr.colorSurfaceVariant, Color.parseColor("#21262D"));
            set.setColors(colorUsed, colorFree);
            set.setSliceSpace(0f);
            set.setDrawValues(false);
            set.setValues(entries);
            data.notifyDataChanged();
            chartDisk.notifyDataSetChanged();
            chartDisk.invalidate();
        } else {
            ArrayList<PieEntry> entries = new ArrayList<>();
            entries.add(new PieEntry(usedBytes, "Used"));
            entries.add(new PieEntry(totalBytes - usedBytes, "Free"));
            
            PieDataSet dataSet = new PieDataSet(entries, "");
            dataSet.setDrawIcons(false);
            dataSet.setSliceSpace(0f);
            
            int colorUsed = MaterialColors.getColor(chartDisk, com.google.android.material.R.attr.colorPrimary, Color.parseColor("#58D1FF"));
            int colorFree = MaterialColors.getColor(chartDisk, com.google.android.material.R.attr.colorSurfaceVariant, Color.parseColor("#21262D"));
            ArrayList<Integer> colors = new ArrayList<>();
            colors.add(colorUsed);
            colors.add(colorFree);
            dataSet.setColors(colors);
            
            PieData newData = new PieData(dataSet);
            newData.setDrawValues(false);
            chartDisk.setData(newData);
            chartDisk.invalidate();
        }
    }

    private void updateProcessUI(List<ProcessInfo> newProcs) {
        processItems.clear();
        processItems.addAll(newProcs);
        sortProcessItems();
        processAdapter.notifyDataSetChanged();
    }
    
    // Remove unused refreshData/update* methods that used random data


    private static class ProcessInfo {
        int pid;
        String name;
        float cpu;
        float mem;
        int conn;
        String status;
        String startTime;
    }

    private class ProcessAdapter extends RecyclerView.Adapter<ProcessAdapter.ProcessViewHolder> {
        @Override
        public ProcessViewHolder onCreateViewHolder(android.view.ViewGroup parent, int viewType) {
            float density = parent.getResources().getDisplayMetrics().density;
            int padding = (int) (4 * density);
            LinearLayout layout = new LinearLayout(parent.getContext());
            layout.setOrientation(LinearLayout.HORIZONTAL);
            layout.setPadding(0, padding, 0, padding);
            layout.setBaselineAligned(true);
            layout.setGravity(Gravity.CENTER_VERTICAL);

            int colorOnSurface = MaterialColors.getColor(parent, com.google.android.material.R.attr.colorOnSurface, Color.WHITE);
            int colorSecondary = MaterialColors.getColor(parent, com.google.android.material.R.attr.colorOnSurfaceVariant, Color.LTGRAY);

            TextView pid = new TextView(parent.getContext());
            pid.setTextColor(colorSecondary);
            pid.setTextSize(12);
            pid.setMaxLines(1);
            pid.setIncludeFontPadding(false);
            layout.addView(pid, new LinearLayout.LayoutParams((int) (72 * density), android.view.ViewGroup.LayoutParams.WRAP_CONTENT));

            TextView name = new TextView(parent.getContext());
            name.setTextColor(colorOnSurface);
            name.setTextSize(12);
            name.setMaxLines(1);
            name.setEllipsize(TextUtils.TruncateAt.END);
            name.setIncludeFontPadding(false);
            name.setClickable(true);
            name.setFocusable(true);
            name.setBackgroundResource(android.R.drawable.list_selector_background);
            layout.addView(name, new LinearLayout.LayoutParams((int) (200 * density), android.view.ViewGroup.LayoutParams.WRAP_CONTENT));

            TextView cpu = new TextView(parent.getContext());
            cpu.setTextColor(colorSecondary);
            cpu.setTextSize(12);
            cpu.setMaxLines(1);
            cpu.setIncludeFontPadding(false);
            layout.addView(cpu, new LinearLayout.LayoutParams((int) (90 * density), android.view.ViewGroup.LayoutParams.WRAP_CONTENT));

            TextView mem = new TextView(parent.getContext());
            mem.setTextColor(colorSecondary);
            mem.setTextSize(12);
            mem.setMaxLines(1);
            mem.setIncludeFontPadding(false);
            layout.addView(mem, new LinearLayout.LayoutParams((int) (90 * density), android.view.ViewGroup.LayoutParams.WRAP_CONTENT));

            TextView conn = new TextView(parent.getContext());
            conn.setTextColor(colorSecondary);
            conn.setTextSize(12);
            conn.setMaxLines(1);
            conn.setIncludeFontPadding(false);
            layout.addView(conn, new LinearLayout.LayoutParams((int) (80 * density), android.view.ViewGroup.LayoutParams.WRAP_CONTENT));

            TextView status = new TextView(parent.getContext());
            status.setTextColor(colorSecondary);
            status.setTextSize(12);
            status.setMaxLines(1);
            status.setIncludeFontPadding(false);
            layout.addView(status, new LinearLayout.LayoutParams((int) (90 * density), android.view.ViewGroup.LayoutParams.WRAP_CONTENT));

            TextView startTime = new TextView(parent.getContext());
            startTime.setTextColor(colorSecondary);
            startTime.setTextSize(12);
            startTime.setMaxLines(1);
            startTime.setEllipsize(TextUtils.TruncateAt.END);
            startTime.setIncludeFontPadding(false);
            layout.addView(startTime, new LinearLayout.LayoutParams((int) (180 * density), android.view.ViewGroup.LayoutParams.WRAP_CONTENT));
            return new ProcessViewHolder(layout, pid, name, cpu, mem, conn, status, startTime);
        }

        @Override
        public void onBindViewHolder(ProcessViewHolder holder, int position) {
            ProcessInfo info = processItems.get(position);
            holder.pid.setText(String.valueOf(info.pid));
            holder.name.setText(info.name);
            holder.cpu.setText(String.format("%.1f%%", info.cpu));
            holder.mem.setText(String.format("%.1f%%", info.mem));
            holder.conn.setText(String.valueOf(info.conn));
            holder.status.setText(formatStatus(info.status));
            holder.startTime.setText(info.startTime);
            holder.name.setOnClickListener(v -> openProcessDetail(info));
        }

        @Override
        public int getItemCount() {
            return processItems.size();
        }

        class ProcessViewHolder extends RecyclerView.ViewHolder {
            TextView pid;
            TextView name;
            TextView cpu;
            TextView mem;
            TextView conn;
            TextView status;
            TextView startTime;

            ProcessViewHolder(View itemView, TextView pid, TextView name, TextView cpu, TextView mem, TextView conn, TextView status, TextView startTime) {
                super(itemView);
                this.pid = pid;
                this.name = name;
                this.cpu = cpu;
                this.mem = mem;
                this.conn = conn;
                this.status = status;
                this.startTime = startTime;
            }
        }
    }

    private void sortProcessItems() {
        if (processSortMode == 1) {
            Collections.sort(processItems, (a, b) -> Float.compare(b.mem, a.mem));
        } else if (processSortMode == 2) {
            Collections.sort(processItems, (a, b) -> Integer.compare(b.conn, a.conn));
        } else {
            Collections.sort(processItems, (a, b) -> Float.compare(b.cpu, a.cpu));
        }
    }

    private String formatStatus(String raw) {
        if (raw == null || raw.isEmpty()) return "-";
        char s = raw.charAt(0);
        if (s == 'R') return getString(R.string.process_status_running);
        if (s == 'S') return getString(R.string.process_status_sleeping);
        if (s == 'D') return getString(R.string.process_status_disk_sleep);
        if (s == 'T') return getString(R.string.process_status_stopped);
        if (s == 'Z') return getString(R.string.process_status_zombie);
        if (s == 'I') return getString(R.string.process_status_idle);
        return raw;
    }

    private void openProcessDetail(ProcessInfo info) {
        Intent intent = new Intent(this, ProcessDetailActivity.class);
        intent.putExtra("host_id", hostId);
        intent.putExtra("pid", info.pid);
        startActivity(intent);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopMonitoring();
    }
}
