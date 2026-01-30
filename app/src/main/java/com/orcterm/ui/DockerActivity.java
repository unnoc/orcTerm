package com.orcterm.ui;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.content.Intent;
import android.text.TextUtils;
import android.view.View;
import android.widget.PopupMenu;
import android.widget.TextView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import com.google.android.material.snackbar.Snackbar;
import android.view.animation.AlphaAnimation;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.github.mikephil.charting.charts.PieChart;
import com.google.android.material.tabs.TabLayout;
import com.orcterm.R;
import com.orcterm.core.docker.DockerContainer;
import com.orcterm.core.docker.DockerImage;
import com.orcterm.core.docker.DockerNetwork;
import com.orcterm.core.docker.DockerVolume;
import com.orcterm.core.ssh.SshNative;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class DockerActivity extends AppCompatActivity {

    private RecyclerView recyclerContainers;
    private RecyclerView recyclerImages;
    private RecyclerView recyclerNetworks;
    private RecyclerView recyclerVolumes;
    private android.widget.ProgressBar progressBar;
    private SwipeRefreshLayout swipeOverview;
    private View cardOverview;
    private View llTotalContainers;
    private View llRunningContainers;
    private View llStoppedContainers;
    private View llTotalImages;
    private View layoutDockerVersion;
    private TextView tvTotalContainers;
    private TextView tvRunningContainers;
    private TextView tvStoppedContainers;
    private TextView tvTotalImages;
    private TextView tvDockerVersion;
    private TextView tvEmptyHint;
    private TabLayout tabLayout;

    private DockerAdapter containerAdapter;
    private DockerImageAdapter imageAdapter;
    private DockerNetworkAdapter networkAdapter;
    private DockerVolumeAdapter volumeAdapter;
    
    private SshNative sshNative;
    private long sshHandle = 0;
    
    private List<DockerContainer> containerList = new ArrayList<>();
    
    // Connection params
    private String hostname;
    private int port;
    private String username;
    private String password;
    private int authType;
    private String keyPath;
    private String containerEngine = "docker";
    private String dockerVersion = "";
    
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_docker);

        setSupportActionBar(findViewById(R.id.toolbar));
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        recyclerContainers = findViewById(R.id.recycler_containers);
        recyclerImages = findViewById(R.id.recycler_images);
        recyclerNetworks = findViewById(R.id.recycler_networks);
        recyclerVolumes = findViewById(R.id.recycler_volumes);
        swipeOverview = findViewById(R.id.swipe_overview);
        cardOverview = findViewById(R.id.card_overview);
        llTotalContainers = findViewById(R.id.ll_total_containers);
        llRunningContainers = findViewById(R.id.ll_running_containers);
        llStoppedContainers = findViewById(R.id.ll_stopped_containers);
        llTotalImages = findViewById(R.id.ll_total_images);
        layoutDockerVersion = findViewById(R.id.layout_docker_version);
        tvTotalContainers = findViewById(R.id.tv_total_containers);
        tvRunningContainers = findViewById(R.id.tv_running_containers);
        tvStoppedContainers = findViewById(R.id.tv_stopped_containers);
        tvTotalImages = findViewById(R.id.tv_total_images);
        tvDockerVersion = findViewById(R.id.tv_docker_version);
        tvEmptyHint = findViewById(R.id.tv_empty_hint);
        
        setupRecyclers();
        setupTabs();
        setupOverview();
        
        getIntentData();

        sshNative = new SshNative();
        
        executor.execute(() -> {
            try {
                connectSsh();
                fetchDockerVersion();
                fetchContainers();
                // Other fetches...
            } catch (Exception e) {
                mainHandler.post(() -> Snackbar.make(findViewById(R.id.coordinator_layout), "Connect failed: " + e.getMessage(), Snackbar.LENGTH_SHORT).show());
            }
        });
    }

    private void fetchDockerVersion() {
        if (sshHandle == 0) return;
        String version = getDockerVersionString();
        dockerVersion = version;
        final String finalVer = dockerVersion;
        mainHandler.post(() -> {
            if (!TextUtils.isEmpty(finalVer)) {
                if (getSupportActionBar() != null) {
                    getSupportActionBar().setSubtitle("v" + finalVer);
                }
                tvDockerVersion.setText(finalVer);
            }
        });
    }

    private String getDockerVersionString() {
        if (sshHandle == 0) return "";
        String version = sshNative.exec(sshHandle, getContainerCommand("version --format '{{.Server.Version}}'")).trim();
        if (version.isEmpty() || version.contains("Error")) {
            version = sshNative.exec(sshHandle, getContainerCommand("-v | awk '{print $3}' | tr -d ','")).trim();
        }
        return version == null ? "" : version;
    }
        
    private void setupRecyclers() {
        progressBar = findViewById(R.id.progress_bar);
        
        // Setup Container Adapter
        containerAdapter = new DockerAdapter(new DockerAdapter.OnContainerClickListener() {
            @Override
            public void onContainerClick(DockerContainer container) {
                openDetail(container);
            }

            @Override
            public void onContainerLongClick(DockerContainer container, View anchor) {
                showActionMenu(container, anchor);
            }
        });
        recyclerContainers.setLayoutManager(new LinearLayoutManager(this));
        recyclerContainers.setAdapter(containerAdapter);

        // Setup Image Adapter
        imageAdapter = new DockerImageAdapter(new DockerImageAdapter.OnImageClickListener() {
            @Override
            public void onImageClick(DockerImage image) {
                Snackbar.make(findViewById(R.id.coordinator_layout), image.repository + ":" + image.tag, Snackbar.LENGTH_SHORT).show();
            }

            @Override
            public void onImageLongClick(DockerImage image, View anchor) {
                showImageActionMenu(image, anchor);
            }
        });
        recyclerImages.setLayoutManager(new LinearLayoutManager(this));
        recyclerImages.setAdapter(imageAdapter);

        // Setup Network Adapter
        networkAdapter = new DockerNetworkAdapter(new DockerNetworkAdapter.OnNetworkClickListener() {
            @Override
            public void onNetworkClick(DockerNetwork network) {
                // Info?
            }

            @Override
            public void onNetworkLongClick(DockerNetwork network, View anchor) {
                showNetworkActionMenu(network, anchor);
            }
        });
        recyclerNetworks.setLayoutManager(new LinearLayoutManager(this));
        recyclerNetworks.setAdapter(networkAdapter);

        // Setup Volume Adapter
        volumeAdapter = new DockerVolumeAdapter(new DockerVolumeAdapter.OnVolumeClickListener() {
            @Override
            public void onVolumeClick(DockerVolume volume) {
                // Info?
            }

            @Override
            public void onVolumeLongClick(DockerVolume volume, View anchor) {
                showVolumeActionMenu(volume, anchor);
            }
        });
        recyclerVolumes.setLayoutManager(new LinearLayoutManager(this));
        recyclerVolumes.setAdapter(volumeAdapter);
    }
    
    private void setupTabs() {
        // Setup TabLayout Navigation
        tabLayout = findViewById(R.id.tab_layout);
        String[] tabs = {"概览", "容器", "镜像", "网络", "存储卷"};
        for (String tab : tabs) {
            tabLayout.addTab(tabLayout.newTab().setText(tab));
        }
        
        tabLayout.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                updateViewVisibility(tabs[tab.getPosition()]);
            }

            @Override
            public void onTabUnselected(TabLayout.Tab tab) {}

            @Override
            public void onTabReselected(TabLayout.Tab tab) {}
        });
        
        // Default select Overview
        TabLayout.Tab overviewTab = tabLayout.getTabAt(0);
        if (overviewTab != null) {
            overviewTab.select();
            updateViewVisibility("概览");
        }
    }

    private void setupOverview() {
        if (swipeOverview != null) {
            swipeOverview.setOnRefreshListener(this::loadOverviewData);
        }
        if (cardOverview != null) {
            cardOverview.setOnClickListener(v -> selectTab("容器"));
        }
        if (llTotalContainers != null) {
            llTotalContainers.setOnClickListener(v -> selectTab("容器"));
        }
        if (llRunningContainers != null) {
            llRunningContainers.setOnClickListener(v -> selectTab("容器"));
        }
        if (llStoppedContainers != null) {
            llStoppedContainers.setOnClickListener(v -> selectTab("容器"));
        }
        if (llTotalImages != null) {
            llTotalImages.setOnClickListener(v -> selectTab("镜像"));
        }
        if (layoutDockerVersion != null) {
            layoutDockerVersion.setOnClickListener(v -> selectTab("容器"));
        }
    }

    private void selectTab(String name) {
        if (tabLayout == null) return;
        for (int i = 0; i < tabLayout.getTabCount(); i++) {
            TabLayout.Tab tab = tabLayout.getTabAt(i);
            if (tab != null && name.equals(tab.getText())) {
                tab.select();
                return;
            }
        }
    }
    
    private void getIntentData() {
        hostname = getIntent().getStringExtra("hostname");
        port = getIntent().getIntExtra("port", 22);
        username = getIntent().getStringExtra("username");
        password = getIntent().getStringExtra("password");
        authType = getIntent().getIntExtra("auth_type", 0);
        keyPath = getIntent().getStringExtra("key_path");
        String engine = getIntent().getStringExtra("container_engine");
        if (!TextUtils.isEmpty(engine)) {
            containerEngine = engine;
        }
    }
    
    private String getContainerCommand(String args) {
        String engine = "podman".equalsIgnoreCase(containerEngine) ? "podman" : "docker";
        return engine + " " + args;
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopStatsTimer();
        if (sshHandle != 0) {
            final long handle = sshHandle;
            new Thread(() -> sshNative.disconnect(handle)).start();
        }
        executor.shutdownNow();
    }

    private void showActionMenu(DockerContainer container, View anchor) {
        PopupMenu popup = new PopupMenu(this, anchor);
        popup.getMenu().add("启动");
        popup.getMenu().add("停止");
        popup.getMenu().add("重启");
        popup.getMenu().add("日志");
        
        popup.setOnMenuItemClickListener(item -> {
            String title = item.getTitle().toString();
            String action = "";
            if ("启动".equals(title)) action = "start";
            else if ("停止".equals(title)) action = "stop";
            else if ("重启".equals(title)) action = "restart";
            else if ("日志".equals(title)) {
                openLogs(container);
                return true;
            }
            
            performDockerAction(container, action);
            return true;
        });
        popup.show();
    }
    
    private void performDockerAction(DockerContainer container, String action) {
        setProgressVisible(true);
        executor.execute(() -> {
            try {
                ensureConnected();
                String cmd = getContainerCommand(action + " " + container.id);
                sshNative.exec(sshHandle, cmd);
                // Refresh list
                mainHandler.post(this::loadContainers);
            } catch (Exception e) {
                mainHandler.post(() -> {
                    setProgressVisible(false);
                    Snackbar.make(findViewById(R.id.coordinator_layout), "操作失败: " + e.getMessage(), Snackbar.LENGTH_SHORT).show();
                });
            }
        });
    }
    
    private void openDetail(DockerContainer container) {
        Intent intent = new Intent(this, DockerContainerDetailActivity.class);
        intent.putExtra("container_id", container.id);
        intent.putExtra("container_name", container.names);
        intent.putExtra("container_image", container.image);
        intent.putExtra("container_status", container.status);
        intent.putExtra("container_state", container.state);
        intent.putExtra("container_created", container.createdAt);
        intent.putExtra("hostname", hostname);
        intent.putExtra("port", port);
        intent.putExtra("username", username);
        intent.putExtra("password", password);
        intent.putExtra("auth_type", authType);
        intent.putExtra("key_path", keyPath);
        startActivity(intent);
    }

    private void openLogs(DockerContainer container) {
        Intent intent = new Intent(this, DockerLogsActivity.class);
        intent.putExtra("container_id", container.id);
        intent.putExtra("container_name", container.names);
        // Pass SSH credentials again
        intent.putExtra("hostname", hostname);
        intent.putExtra("port", port);
        intent.putExtra("username", username);
        intent.putExtra("password", password);
        intent.putExtra("auth_type", authType);
        intent.putExtra("key_path", keyPath);
        startActivity(intent);
    }
    
    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
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

    private void loadContainers() {
        setProgressVisible(true);
        
        executor.execute(() -> {
            try {
                ensureConnected();
                
                // 3. Exec docker ps
                String cmd = getContainerCommand("ps -a --format '{{json .}}'");
                String response = sshNative.exec(sshHandle, cmd);
                
                // 4. Parse
                List<DockerContainer> list = parseContainers(response);
                
                synchronized (this) {
                    containerList = list;
                }

                // 6. Update UI
                mainHandler.post(() -> {
                    setProgressVisible(false);
                    containerAdapter.setContainers(containerList);
                });
                
            } catch (Exception e) {
                mainHandler.post(() -> {
                    setProgressVisible(false);
                    Snackbar.make(findViewById(R.id.coordinator_layout), "错误: " + e.getMessage(), Snackbar.LENGTH_LONG).show();
                });
            }
        });
    }

    private void loadStats() {
        executor.execute(() -> {
            try {
                loadStatsInternal();
                mainHandler.post(() -> {
                    // Refresh adapter to show new stats
                    if (recyclerContainers.getVisibility() == View.VISIBLE) {
                        containerAdapter.notifyDataSetChanged();
                    }
                });
            } catch (Exception e) {
                 // ignore
            }
        });
    }

    private void loadStatsInternal() throws Exception {
        ensureConnected();
        // Simply return, stats are no longer displayed on the main docker activity
    }

    private void updateViewVisibility(String tabName) {
        recyclerContainers.setVisibility(View.GONE);
        recyclerImages.setVisibility(View.GONE);
        recyclerNetworks.setVisibility(View.GONE);
        recyclerVolumes.setVisibility(View.GONE);
        if (swipeOverview != null) {
            swipeOverview.setVisibility(View.GONE);
        }
        
        stopStatsTimer();
        
        View targetView = null;

        switch (tabName) {
            case "概览":
                targetView = swipeOverview;
                loadOverviewData();
                break;
            case "容器":
                targetView = recyclerContainers;
                loadContainers();
                startStatsTimer();
                break;
            case "镜像":
                targetView = recyclerImages;
                loadImages();
                break;
            case "网络":
                targetView = recyclerNetworks;
                loadNetworks();
                break;
            case "存储卷":
                targetView = recyclerVolumes;
                loadVolumes();
                break;
        }
        
        if (targetView != null) {
            targetView.setVisibility(View.VISIBLE);
            AlphaAnimation fadeIn = new AlphaAnimation(0.0f, 1.0f);
            fadeIn.setDuration(200);
            targetView.startAnimation(fadeIn);
        }
    }

    private void loadOverviewData() {
        if (swipeOverview != null) {
            swipeOverview.setRefreshing(true);
        }
        executor.execute(() -> {
            try {
                ensureConnected();
                String version = getDockerVersionString();
                String containerResponse = sshNative.exec(sshHandle, getContainerCommand("ps -a --format '{{json .}}'"));
                List<DockerContainer> containers = parseContainers(containerResponse);
                int total = containers.size();
                int running = (int) containers.stream().filter(c -> "running".equalsIgnoreCase(c.state)).count();
                int stopped = Math.max(0, total - running);
                String imageResponse = sshNative.exec(sshHandle, getContainerCommand("images --format '{{json .}}'"));
                List<DockerImage> images = parseImages(imageResponse);
                int imageCount = images.size();
                dockerVersion = version;
                mainHandler.post(() -> {
                    updateOverviewUI(total, running, stopped, imageCount, dockerVersion);
                    if (swipeOverview != null) {
                        swipeOverview.setRefreshing(false);
                    }
                });
            } catch (Exception e) {
                mainHandler.post(() -> {
                    if (swipeOverview != null) {
                        swipeOverview.setRefreshing(false);
                    }
                    Snackbar.make(findViewById(R.id.coordinator_layout), "错误: " + e.getMessage(), Snackbar.LENGTH_LONG).show();
                });
            }
        });
    }

    private void updateOverviewUI(int total, int running, int stopped, int images, String version) {
        tvTotalContainers.setText(String.valueOf(total));
        tvRunningContainers.setText(String.valueOf(running));
        tvStoppedContainers.setText(String.valueOf(stopped));
        tvTotalImages.setText(String.valueOf(images));
        if (!TextUtils.isEmpty(version)) {
            tvDockerVersion.setText(version);
            if (getSupportActionBar() != null) {
                getSupportActionBar().setSubtitle("v" + version);
            }
        }
        boolean isEmpty = total == 0 && images == 0;
        tvEmptyHint.setVisibility(isEmpty ? View.VISIBLE : View.GONE);
    }

    private void showImageActionMenu(DockerImage image, View anchor) {
        PopupMenu popup = new PopupMenu(this, anchor);
        popup.getMenu().add("删除");
        popup.setOnMenuItemClickListener(item -> {
            if ("删除".equals(item.getTitle())) {
                performDockerImageAction(image, "rmi");
            }
            return true;
        });
        popup.show();
    }

    private void showNetworkActionMenu(DockerNetwork network, View anchor) {
        PopupMenu popup = new PopupMenu(this, anchor);
        popup.getMenu().add("删除");
        popup.setOnMenuItemClickListener(item -> {
            if ("删除".equals(item.getTitle())) {
                performSimpleDockerAction("network rm", network.id, () -> loadNetworks());
            }
            return true;
        });
        popup.show();
    }

    private void showVolumeActionMenu(DockerVolume volume, View anchor) {
        PopupMenu popup = new PopupMenu(this, anchor);
        popup.getMenu().add("删除");
        popup.setOnMenuItemClickListener(item -> {
            if ("删除".equals(item.getTitle())) {
                performSimpleDockerAction("volume rm", volume.name, () -> loadVolumes());
            }
            return true;
        });
        popup.show();
    }

    private void performSimpleDockerAction(String commandPrefix, String targetId, Runnable onSuccess) {
        setProgressVisible(true);
        executor.execute(() -> {
            try {
                ensureConnected();
                String cmd = getContainerCommand(commandPrefix + " " + targetId);
                sshNative.exec(sshHandle, cmd);
                mainHandler.post(onSuccess);
            } catch (Exception e) {
                mainHandler.post(() -> {
                    setProgressVisible(false);
                    Snackbar.make(findViewById(R.id.coordinator_layout), "操作失败: " + e.getMessage(), Snackbar.LENGTH_SHORT).show();
                });
            }
        });
    }

    private void performDockerImageAction(DockerImage image, String action) {
        performSimpleDockerAction(action, image.id, () -> loadImages());
    }

    private void loadInfo() {
        // No-op
    }

    private void loadImages() {
        setProgressVisible(true);
        executor.execute(() -> {
            try {
                ensureConnected();
                // docker images format json
                String cmd = "docker images --format '{{json .}}'";
                String response = sshNative.exec(sshHandle, cmd);
                List<DockerImage> list = parseImages(response);
                
                mainHandler.post(() -> {
                    setProgressVisible(false);
                    imageAdapter.setImages(list);
                });
            } catch (Exception e) {
                mainHandler.post(() -> {
                    setProgressVisible(false);
                    Snackbar.make(findViewById(R.id.coordinator_layout), "错误: " + e.getMessage(), Snackbar.LENGTH_LONG).show();
                });
            }
        });
    }

    private java.util.Timer statsTimer;

    private void startStatsTimer() {
        stopStatsTimer();
        statsTimer = new java.util.Timer();
        statsTimer.schedule(new java.util.TimerTask() {
            @Override
            public void run() {
                loadStats();
            }
        }, 1000, 3000);
    }

    private void stopStatsTimer() {
        if (statsTimer != null) {
            statsTimer.cancel();
            statsTimer = null;
        }
    }

    private void loadNetworks() {
        setProgressVisible(true);
        executor.execute(() -> {
            try {
                ensureConnected();
                String cmd = "docker network ls --format '{{json .}}'";
                String response = sshNative.exec(sshHandle, cmd);
                
                List<DockerNetwork> list = new ArrayList<>();
                String[] lines = response.split("\n");
                for (String line : lines) {
                    if (line.trim().isEmpty()) continue;
                    try {
                        list.add(DockerNetwork.fromJson(new JSONObject(line)));
                    } catch (Exception e) {}
                }
                
                mainHandler.post(() -> {
                    setProgressVisible(false);
                    networkAdapter.setNetworks(list);
                });
            } catch (Exception e) {
                mainHandler.post(() -> {
                    setProgressVisible(false);
                    Snackbar.make(findViewById(R.id.coordinator_layout), "错误: " + e.getMessage(), Snackbar.LENGTH_LONG).show();
                });
            }
        });
    }




    private void loadVolumes() {
        setProgressVisible(true);
        executor.execute(() -> {
            try {
                ensureConnected();
                String cmd = getContainerCommand("volume ls --format '{{json .}}'");
                String response = sshNative.exec(sshHandle, cmd);
                
                List<DockerVolume> list = new ArrayList<>();
                String[] lines = response.split("\n");
                for (String line : lines) {
                    if (line.trim().isEmpty()) continue;
                    try {
                        list.add(DockerVolume.fromJson(new JSONObject(line)));
                    } catch (Exception e) {}
                }
                
                mainHandler.post(() -> {
                    setProgressVisible(false);
                    volumeAdapter.setVolumes(list);
                });
            } catch (Exception e) {
                mainHandler.post(() -> {
                    setProgressVisible(false);
                    Snackbar.make(findViewById(R.id.coordinator_layout), "错误: " + e.getMessage(), Snackbar.LENGTH_LONG).show();
                });
            }
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

    private void connectSsh() throws Exception {
        ensureConnected();
    }

    private void fetchContainers() {
        loadContainers();
    }

    private List<DockerContainer> parseContainers(String response) {
        List<DockerContainer> list = new ArrayList<>();
        if (response == null) return list;
        String trimmed = response.trim();
        if (trimmed.isEmpty()) return list;
        if (trimmed.startsWith("[")) {
            try {
                JSONArray array = new JSONArray(trimmed);
                for (int i = 0; i < array.length(); i++) {
                    list.add(DockerContainer.fromJson(array.getJSONObject(i)));
                }
            } catch (Exception e) {}
        } else {
            String[] lines = trimmed.split("\n");
            for (String line : lines) {
                if (line.trim().isEmpty()) continue;
                try {
                    list.add(DockerContainer.fromJson(new JSONObject(line)));
                } catch (Exception e) {}
            }
        }
        return list;
    }

    private List<DockerImage> parseImages(String response) {
        List<DockerImage> list = new ArrayList<>();
        if (response == null) return list;
        String trimmed = response.trim();
        if (trimmed.isEmpty()) return list;
        if (trimmed.startsWith("[")) {
            try {
                JSONArray array = new JSONArray(trimmed);
                for (int i = 0; i < array.length(); i++) {
                    list.add(DockerImage.fromJson(array.getJSONObject(i)));
                }
            } catch (Exception e) {}
        } else {
            String[] lines = trimmed.split("\n");
            for (String line : lines) {
                if (line.trim().isEmpty()) continue;
                try {
                    list.add(DockerImage.fromJson(new JSONObject(line)));
                } catch (Exception e) {}
            }
        }
        return list;
    }
}
