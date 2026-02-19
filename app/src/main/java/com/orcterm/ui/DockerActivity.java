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
import com.orcterm.core.docker.DockerRepository;
import com.orcterm.core.docker.DockerVolume;
import com.orcterm.core.session.SessionConnector;
import com.orcterm.core.ssh.SshNative;
import com.orcterm.ui.common.UiStateController;
import com.orcterm.util.CommandConstants;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 容器管理主界面
 */
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
    private UiStateController uiStateController;
    private TabLayout tabLayout;
    private String currentTab = "概览";

    private DockerAdapter containerAdapter;
    private DockerImageAdapter imageAdapter;
    private DockerNetworkAdapter networkAdapter;
    private DockerVolumeAdapter volumeAdapter;
    private DockerRepository dockerRepository;
    
    private SshNative sshNative;
    private long sshHandle = 0;
    private boolean isSharedSession = false;
    
    private List<DockerContainer> containerList = new ArrayList<>();
    
    // Connection params
    private String hostname;
    private int port;
    private String username;
    private String password;
    private int authType;
    private String keyPath;
    private String containerEngine = CommandConstants.CMD_ENGINE_DOCKER;
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
        uiStateController = new UiStateController(this);
        uiStateController.setRetryAction(this::retryCurrentTab);
        
        setupRecyclers();
        setupTabs();
        setupOverview();
        
        getIntentData();

        sshNative = new SshNative();
        dockerRepository = new DockerRepository(sshNative);
        
        executor.execute(() -> {
            try {
                ensureConnected();
                fetchDockerVersion();
            } catch (Exception e) {
                mainHandler.post(() -> uiStateController.showError(e.getMessage()));
            }
        });
    }

    private void retryCurrentTab() {
        switch (currentTab) {
            case "容器":
                uiStateController.showLoading(getString(R.string.ui_state_loading_message));
                loadContainers();
                break;
            case "镜像":
                uiStateController.showLoading(getString(R.string.ui_state_loading_message));
                loadImages();
                break;
            case "网络":
                uiStateController.showLoading(getString(R.string.ui_state_loading_message));
                loadNetworks();
                break;
            case "存储卷":
                uiStateController.showLoading(getString(R.string.ui_state_loading_message));
                loadVolumes();
                break;
            default:
                uiStateController.showContent();
                loadOverviewData();
                break;
        }
    }

    private void fetchDockerVersion() {
        if (sshHandle == 0) return;
        String version = dockerRepository.fetchVersion(sshHandle, containerEngine);
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
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        releaseSsh();
        executor.shutdownNow();
    }

    private void releaseSsh() {
        final long handle = sshHandle;
        final boolean shared = isSharedSession;
        sshHandle = 0;
        isSharedSession = false;
        if (handle != 0 && !shared) {
            new Thread(() -> sshNative.disconnect(handle)).start();
        }
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
            if ("启动".equals(title)) action = CommandConstants.CMD_DOCKER_ACTION_START;
            else if ("停止".equals(title)) action = CommandConstants.CMD_DOCKER_ACTION_STOP;
            else if ("重启".equals(title)) action = CommandConstants.CMD_DOCKER_ACTION_RESTART;
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
        performSimpleDockerAction(action, container.id, this::loadContainers);
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
            dockerRepository = new DockerRepository(sshNative);
        }
        if (hostname == null || hostname.trim().isEmpty()) {
            throw new Exception("Host is empty");
        }
        if (username == null || username.trim().isEmpty()) {
            throw new Exception("User is empty");
        }
        if (sshHandle == 0) {
            SessionConnector.Connection connection = SessionConnector.acquire(
                    sshNative,
                    hostname,
                    port,
                    username,
                    password,
                    authType,
                    keyPath,
                    "Connect failed",
                    "Auth failed"
            );
            sshHandle = connection.getHandle();
            isSharedSession = connection.isShared();
        }
    }

    private void loadContainers() {
        executor.execute(() -> {
            try {
                ensureConnected();
                List<DockerContainer> list = dockerRepository.fetchContainers(sshHandle, containerEngine);
                
                synchronized (this) {
                    containerList = list;
                }

                // 6. Update UI
                mainHandler.post(() -> {
                    containerAdapter.setContainers(containerList);
                    if ("容器".equals(currentTab)) {
                        if (containerList.isEmpty()) {
                            uiStateController.showEmpty(getString(R.string.docker_empty_containers));
                        } else {
                            uiStateController.showContent();
                        }
                    }
                });
                
            } catch (Exception e) {
                mainHandler.post(() -> {
                    if ("容器".equals(currentTab)) {
                        uiStateController.showError(e.getMessage());
                    }
                });
            }
        });
    }

    private void updateViewVisibility(String tabName) {
        currentTab = tabName;
        recyclerContainers.setVisibility(View.GONE);
        recyclerImages.setVisibility(View.GONE);
        recyclerNetworks.setVisibility(View.GONE);
        recyclerVolumes.setVisibility(View.GONE);
        if (swipeOverview != null) {
            swipeOverview.setVisibility(View.GONE);
        }

        View targetView = null;

        switch (tabName) {
            case "概览":
                targetView = swipeOverview;
                uiStateController.showContent();
                loadOverviewData();
                break;
            case "容器":
                targetView = recyclerContainers;
                uiStateController.showLoading(getString(R.string.ui_state_loading_message));
                loadContainers();
                break;
            case "镜像":
                targetView = recyclerImages;
                uiStateController.showLoading(getString(R.string.ui_state_loading_message));
                loadImages();
                break;
            case "网络":
                targetView = recyclerNetworks;
                uiStateController.showLoading(getString(R.string.ui_state_loading_message));
                loadNetworks();
                break;
            case "存储卷":
                targetView = recyclerVolumes;
                uiStateController.showLoading(getString(R.string.ui_state_loading_message));
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
                DockerRepository.Overview overview = dockerRepository.fetchOverview(sshHandle, containerEngine);
                dockerVersion = overview.version;
                mainHandler.post(() -> {
                    updateOverviewUI(
                            overview.totalContainers,
                            overview.runningContainers,
                            overview.stoppedContainers,
                            overview.totalImages,
                            dockerVersion
                    );
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
                performDockerImageAction(image, CommandConstants.CMD_CONTAINER_IMAGE_RM);
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
                performSimpleDockerAction(CommandConstants.CMD_CONTAINER_NETWORK_RM, network.id, () -> loadNetworks());
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
                performSimpleDockerAction(CommandConstants.CMD_CONTAINER_VOLUME_RM, volume.name, () -> loadVolumes());
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
                dockerRepository.runAction(sshHandle, containerEngine, commandPrefix, targetId);
                mainHandler.post(() -> {
                    setProgressVisible(false);
                    onSuccess.run();
                });
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

    private void loadImages() {
        executor.execute(() -> {
            try {
                ensureConnected();
                List<DockerImage> list = dockerRepository.fetchImages(sshHandle, containerEngine);
                
                mainHandler.post(() -> {
                    imageAdapter.setImages(list);
                    if ("镜像".equals(currentTab)) {
                        if (list.isEmpty()) {
                            uiStateController.showEmpty(getString(R.string.docker_empty_images));
                        } else {
                            uiStateController.showContent();
                        }
                    }
                });
            } catch (Exception e) {
                mainHandler.post(() -> {
                    if ("镜像".equals(currentTab)) {
                        uiStateController.showError(e.getMessage());
                    }
                });
            }
        });
    }

    private void loadNetworks() {
        executor.execute(() -> {
            try {
                ensureConnected();
                List<DockerNetwork> list = dockerRepository.fetchNetworks(sshHandle, containerEngine);
                
                mainHandler.post(() -> {
                    networkAdapter.setNetworks(list);
                    if ("网络".equals(currentTab)) {
                        if (list.isEmpty()) {
                            uiStateController.showEmpty(getString(R.string.docker_empty_networks));
                        } else {
                            uiStateController.showContent();
                        }
                    }
                });
            } catch (Exception e) {
                mainHandler.post(() -> {
                    if ("网络".equals(currentTab)) {
                        uiStateController.showError(e.getMessage());
                    }
                });
            }
        });
    }




    private void loadVolumes() {
        executor.execute(() -> {
            try {
                ensureConnected();
                List<DockerVolume> list = dockerRepository.fetchVolumes(sshHandle, containerEngine);
                
                mainHandler.post(() -> {
                    volumeAdapter.setVolumes(list);
                    if ("存储卷".equals(currentTab)) {
                        if (list.isEmpty()) {
                            uiStateController.showEmpty(getString(R.string.docker_empty_volumes));
                        } else {
                            uiStateController.showContent();
                        }
                    }
                });
            } catch (Exception e) {
                mainHandler.post(() -> {
                    if ("存储卷".equals(currentTab)) {
                        uiStateController.showError(e.getMessage());
                    }
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
}
