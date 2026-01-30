package com.orcterm.ui;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.github.mikephil.charting.charts.PieChart;
import com.github.mikephil.charting.data.PieData;
import com.github.mikephil.charting.data.PieDataSet;
import com.github.mikephil.charting.data.PieEntry;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.color.MaterialColors;
import com.orcterm.R;
import com.orcterm.core.ssh.SshNative;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class DockerContainerDetailActivity extends AppCompatActivity {

    private String containerId;
    private String containerName;
    
    // SSH params
    private String hostname;
    private int port;
    private String username;
    private String password;
    private int authType;
    private String keyPath;
    
    private SshNative sshNative;
    private long sshHandle = 0;
    
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    
    private TextView tvName, tvId, tvImage, tvStatus, tvCreated, tvCommand, tvEnv;
    private TextView tvNetworkMode, tvIp, tvPorts;
    private TextView tvMounts;
    private ProgressBar progressBar;
    private MaterialCardView cardStatus;
    
    private PieChart cpuChart, memChart;
    private Button btnStart, btnStop, btnRestart;
    
    private final Handler statsHandler = new Handler(Looper.getMainLooper());
    private Runnable statsRunnable;
    private boolean isMonitoring = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_docker_container_detail);
        
        setSupportActionBar(findViewById(R.id.toolbar));
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setTitle("容器镜像详情");
        
        containerId = getIntent().getStringExtra("container_id");
        containerName = getIntent().getStringExtra("container_name");
        
        String cImage = getIntent().getStringExtra("container_image");
        String cStatus = getIntent().getStringExtra("container_status");
        String cCreated = getIntent().getStringExtra("container_created");
        
        hostname = getIntent().getStringExtra("hostname");
        port = getIntent().getIntExtra("port", 22);
        username = getIntent().getStringExtra("username");
        password = getIntent().getStringExtra("password");
        authType = getIntent().getIntExtra("auth_type", 0);
        keyPath = getIntent().getStringExtra("key_path");
        
        initViews();
        
        // Pre-populate UI
        if (containerName != null) tvName.setText(containerName.replace("/", ""));
        if (containerId != null) tvId.setText(containerId.substring(0, Math.min(containerId.length(), 12)));
        if (cImage != null) tvImage.setText(cImage);
        if (cStatus != null) tvStatus.setText(cStatus);
        if (cCreated != null) tvCreated.setText(cCreated);
        updateStatusBadge(cStatus);
        
        sshNative = new SshNative();
        loadDetails();
        startStatsMonitoring();
    }
    
    private void initViews() {
        tvName = findViewById(R.id.tv_name);
        tvId = findViewById(R.id.tv_id);
        tvImage = findViewById(R.id.tv_image);
        tvStatus = findViewById(R.id.tv_status);
        tvCreated = findViewById(R.id.tv_created);
        tvCommand = findViewById(R.id.tv_command);
        tvEnv = findViewById(R.id.tv_env);
        cardStatus = findViewById(R.id.card_status);
        
        tvNetworkMode = findViewById(R.id.tv_network_mode);
        tvIp = findViewById(R.id.tv_ip);
        tvPorts = findViewById(R.id.tv_ports);
        
        tvMounts = findViewById(R.id.tv_mounts);
        
        progressBar = findViewById(R.id.progress_bar);
        
        cpuChart = findViewById(R.id.chart_cpu_detail);
        memChart = findViewById(R.id.chart_mem_detail);
        setupChart(cpuChart);
        setupChart(memChart);
        
        btnStart = findViewById(R.id.btn_start);
        btnStop = findViewById(R.id.btn_stop);
        btnRestart = findViewById(R.id.btn_restart);
        
        btnStart.setOnClickListener(v -> controlContainer("start"));
        btnStop.setOnClickListener(v -> controlContainer("stop"));
        btnRestart.setOnClickListener(v -> controlContainer("restart"));
    }
    
    private void setupChart(PieChart chart) {
        chart.setUsePercentValues(true);
        chart.getDescription().setEnabled(false);
        chart.getLegend().setEnabled(false);
        chart.setDrawHoleEnabled(true);
        chart.setHoleColor(android.graphics.Color.TRANSPARENT);
        chart.setHoleRadius(70f);
        chart.setTransparentCircleRadius(75f);
        chart.setDrawCenterText(true);
        chart.setTouchEnabled(false);
        chart.setNoDataText("Loading...");
    }
    
    private void startStatsMonitoring() {
        statsRunnable = new Runnable() {
            @Override
            public void run() {
                if (isMonitoring) {
                    loadStats();
                    statsHandler.postDelayed(this, 5000); // 5 seconds refresh
                }
            }
        };
        isMonitoring = true;
        statsHandler.post(statsRunnable);
    }
    
    private void stopStatsMonitoring() {
        isMonitoring = false;
        statsHandler.removeCallbacks(statsRunnable);
    }

    private void loadStats() {
        executor.execute(() -> {
            try {
                ensureConnected();
                // --no-stream ensures we get a snapshot and exit
                String cmd = "docker stats " + containerId + " --no-stream --format \"{{.CPUPerc}}|{{.MemPerc}}\"";
                String response = sshNative.exec(sshHandle, cmd);
                
                if (response != null && !response.trim().isEmpty()) {
                    String[] parts = response.trim().split("\\|");
                    if (parts.length == 2) {
                        float cpu = parsePercentage(parts[0]);
                        float mem = parsePercentage(parts[1]);
                        
                        mainHandler.post(() -> {
                            updateChart(cpuChart, cpu, "CPU");
                            updateChart(memChart, mem, "MEM");
                        });
                    }
                }
            } catch (Exception e) {
                // ignore stats errors silently to not annoy user
            }
        });
    }
    
    private void updateChart(PieChart chart, float val, String type) {
        ArrayList<PieEntry> entries = new ArrayList<>();
        float free = 100f - val;
        if (free < 0) free = 0;

        entries.add(new PieEntry(val, ""));
        entries.add(new PieEntry(free, ""));

        PieDataSet dataSet = new PieDataSet(entries, "");
        int color = type.equals("CPU") ? 0xFFFF9800 : 0xFF2196F3;
        dataSet.setColors(color, 0xFFE0E0E0);
        dataSet.setDrawValues(false);

        PieData data = new PieData(dataSet);
        chart.setData(data);
        chart.setCenterText(String.format("%.1f%%", val));
        chart.setCenterTextSize(14f); // Larger text for detail view
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
    
    private void controlContainer(String action) {
        setProgressVisible(true);
        Toast.makeText(this, "正在执行: " + action, Toast.LENGTH_SHORT).show();
        
        executor.execute(() -> {
            try {
                ensureConnected();
                String cmd = "docker " + action + " " + containerId;
                sshNative.exec(sshHandle, cmd);
                
                // Reload details to reflect new status
                mainHandler.post(this::loadDetails);
                
                mainHandler.post(() -> {
                    Toast.makeText(this, action + " 完成", Toast.LENGTH_SHORT).show();
                });
            } catch (Exception e) {
                showError("执行失败: " + e.getMessage());
            }
        });
    }
    
    private void ensureConnected() throws Exception {
        if (sshNative == null) {
            sshNative = new SshNative();
        }
        if (hostname == null || hostname.trim().isEmpty()) {
            throw new Exception("Host is empty");
        }
        if (username == null || username.trim().isEmpty()) {
            throw new Exception("User is empty");
        }
        if (sshHandle == 0) {
            sshHandle = sshNative.connect(hostname, port);
            if (sshHandle == 0) throw new Exception("Connect failed");
            
            int ret;
            if (authType == 1 && keyPath != null) {
                ret = sshNative.authKey(sshHandle, username, keyPath);
            } else {
                ret = sshNative.authPassword(sshHandle, username, password);
            }
            
            if (ret != 0) {
                throw new Exception("Auth failed");
            }
        }
    }
    
    private void loadDetails() {
        setProgressVisible(true);
        executor.execute(() -> {
            try {
                ensureConnected();
                String cmd = "docker inspect " + containerId;
                String response = sshNative.exec(sshHandle, cmd);
                
                if (response == null || response.trim().isEmpty()) {
                    showError("未获取到数据");
                    return;
                }
                
                if (!response.trim().startsWith("[")) {
                     showError("数据格式错误");
                     return;
                }
                
                JSONArray array = new JSONArray(response);
                if (array.length() > 0) {
                    JSONObject json = array.getJSONObject(0);
                    parseDetails(json);
                } else {
                    showError("未返回数据");
                }
                
            } catch (Exception e) {
                showError("加载失败: " + e.getMessage());
            }
        });
    }
    
    private void parseDetails(JSONObject json) {
        try {
            String name = json.optString("Name");
            String id = json.optString("Id");
            String created = json.optString("Created");
            JSONObject stateObj = json.optJSONObject("State");
            String status = stateObj != null ? stateObj.optString("Status") : "-";
            
            JSONObject config = json.optJSONObject("Config");
            String image = config != null ? config.optString("Image") : "-";
            
            // Command
            StringBuilder cmdStr = new StringBuilder();
            if (config != null) {
                JSONArray cmdArr = config.optJSONArray("Cmd");
                if (cmdArr != null) {
                    for (int i=0; i<cmdArr.length(); i++) cmdStr.append(cmdArr.getString(i)).append(" ");
                }
            }
            
            // Env
            StringBuilder envStr = new StringBuilder();
            if (config != null) {
                JSONArray envArr = config.optJSONArray("Env");
                if (envArr != null) {
                    for (int i=0; i<envArr.length(); i++) envStr.append(envArr.getString(i)).append("\n");
                }
            }
            
            // Network
            JSONObject netSettings = json.optJSONObject("NetworkSettings");
            StringBuilder netMode = new StringBuilder();
            StringBuilder ipAddr = new StringBuilder();
            StringBuilder portsStr = new StringBuilder();
            
            if (netSettings != null) {
                JSONObject networks = netSettings.optJSONObject("Networks");
                if (networks != null) {
                    Iterator<String> keys = networks.keys();
                    while (keys.hasNext()) {
                        String key = keys.next();
                        netMode.append(key).append(" ");
                        JSONObject netInfo = networks.getJSONObject(key);
                        ipAddr.append(netInfo.optString("IPAddress")).append(" ");
                    }
                }
                
                JSONObject ports = netSettings.optJSONObject("Ports");
                if (ports != null) {
                    Iterator<String> keys = ports.keys();
                    while (keys.hasNext()) {
                        String portKey = keys.next();
                        JSONArray bindings = ports.optJSONArray(portKey);
                        if (bindings != null && bindings.length() > 0) {
                             portsStr.append(portKey).append(" -> ");
                             for (int i=0; i<bindings.length(); i++) {
                                 JSONObject bind = bindings.getJSONObject(i);
                                 portsStr.append(bind.optString("HostPort"));
                                 if (i < bindings.length() - 1) portsStr.append(", ");
                             }
                             portsStr.append("\n");
                        }
                    }
                }
            }
            
            // Mounts
            StringBuilder mountsStr = new StringBuilder();
            JSONArray mounts = json.optJSONArray("Mounts");
            if (mounts != null) {
                for (int i=0; i<mounts.length(); i++) {
                    JSONObject m = mounts.getJSONObject(i);
                    mountsStr.append("Type: ").append(m.optString("Type")).append("\n");
                    mountsStr.append("Source: ").append(m.optString("Source")).append("\n");
                    mountsStr.append("Dest: ").append(m.optString("Destination")).append("\n\n");
                }
            }
            
            mainHandler.post(() -> {
                tvName.setText(name.replace("/", ""));
                tvId.setText(id.substring(0, Math.min(id.length(), 12)));
                tvImage.setText(image);
                tvStatus.setText(status);
                tvCreated.setText(created);
                tvCommand.setText(cmdStr.toString().trim());
                tvEnv.setText(envStr.toString().trim());
                updateStatusBadge(status);

                tvNetworkMode.setText(netMode.toString());
                tvIp.setText(ipAddr.toString());
                tvPorts.setText(portsStr.toString().trim());

                tvMounts.setText(mountsStr.toString().trim());
                
                setProgressVisible(false);
            });
            
        } catch (Exception e) {
            showError("解析错误: " + e.getMessage());
        }
    }

    private void updateStatusBadge(String status) {
        if (cardStatus == null || tvStatus == null) return;
        String val = status == null ? "" : status.toLowerCase();
        int bg;
        int fg;
        if (val.contains("up") || val.contains("running")) {
            bg = MaterialColors.getColor(cardStatus, com.google.android.material.R.attr.colorSecondaryContainer);
            fg = MaterialColors.getColor(cardStatus, com.google.android.material.R.attr.colorOnSecondaryContainer);
        } else {
            bg = MaterialColors.getColor(cardStatus, com.google.android.material.R.attr.colorSurfaceVariant);
            fg = MaterialColors.getColor(cardStatus, com.google.android.material.R.attr.colorOnSurfaceVariant);
        }
        cardStatus.setCardBackgroundColor(bg);
        tvStatus.setTextColor(fg);
    }
    
    private void showError(String msg) {
        mainHandler.post(() -> {
            setProgressVisible(false);
            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
        });
    }

    private void setProgressVisible(boolean visible) {
        if (progressBar == null) return;
        if (Looper.myLooper() == Looper.getMainLooper()) {
            progressBar.setVisibility(visible ? View.VISIBLE : View.GONE);
        } else {
            mainHandler.post(() -> progressBar.setVisibility(visible ? View.VISIBLE : View.GONE));
        }
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
        stopStatsMonitoring();
        executor.shutdownNow();
        if (sshHandle != 0 && sshNative != null) {
            new Thread(() -> {
                try {
                    sshNative.disconnect(sshHandle);
                } catch (Exception e) {
                    // ignore
                }
                sshHandle = 0;
            }).start();
        }
    }
}
