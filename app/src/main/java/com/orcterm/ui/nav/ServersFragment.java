package com.orcterm.ui.nav;

import android.content.SharedPreferences;
import android.content.Context;
import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.PopupMenu;
import android.widget.Toast;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.appcompat.widget.SearchView;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.snackbar.Snackbar;
import com.orcterm.core.ssh.SshNative;
import com.orcterm.R;
import com.orcterm.data.HostEntity;
import com.orcterm.ui.AddHostActivity;
import com.orcterm.ui.HostAdapter;
import com.orcterm.ui.HostDetailActivity;
import com.orcterm.ui.DockerActivity;
import com.orcterm.ui.SftpActivity;
import com.orcterm.ui.MainViewModel;
import com.orcterm.ui.nav.NavViewModel;
import com.orcterm.util.AppBackgroundHelper;
import com.orcterm.util.CommandConstants;

import java.util.Collections;
import java.util.Comparator;
import java.util.ArrayList;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import android.graphics.Bitmap;
import android.graphics.Color;
import com.google.zxing.WriterException;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.common.BitMatrix;
import android.util.Log;

import com.orcterm.data.HostStatus;

public class ServersFragment extends Fragment {

    private MainViewModel mHostViewModel;
    private HostAdapter mAdapter;
    private String mCurrentSearchQuery = "";
    private Comparator<HostEntity> mCurrentSort = null;
    private final ExecutorService executor = Executors.newCachedThreadPool();
    private List<HostEntity> mAllHosts = new ArrayList<>();
    private NavViewModel navViewModel;
    
    private ScheduledExecutorService monitorScheduler;
    private final Map<Long, Long> monitorHandles = new ConcurrentHashMap<>();
    // Store previous CPU values: [user, nice, system, idle, iowait, irq, softirq, steal]
    private final Map<Long, long[]> prevCpuStats = new ConcurrentHashMap<>();
    // Store previous Net values: [timestamp, rx_bytes, tx_bytes]
    private final Map<Long, long[]> prevIoStats = new ConcurrentHashMap<>();
    // Store previous Disk values: [timestamp, read_sectors, write_sectors]
    private final Map<Long, long[]> prevDiskStats = new ConcurrentHashMap<>();
    
    private SharedPreferences prefs;
    private SharedPreferences.OnSharedPreferenceChangeListener prefListener;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
        prefs = requireContext().getSharedPreferences("orcterm_prefs", Context.MODE_PRIVATE);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_servers, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        AppBackgroundHelper.applyFromPrefs(requireContext(), view);
        navViewModel = new ViewModelProvider(requireActivity()).get(NavViewModel.class);
        RecyclerView recyclerView = view.findViewById(R.id.recyclerview);
        mAdapter = new HostAdapter(new HostAdapter.HostDiff(),
                new HostAdapter.OnItemClickListener() {
                    @Override
                    public void onItemClick(HostEntity host) {
                        openHostDetail(host);
                    }

                    @Override
                    public void onItemLongClick(View anchor, HostEntity host) {
                        showHostActions(anchor, host);
                    }
                },
                this::showHostActions
        );
        recyclerView.setAdapter(mAdapter);
        mAdapter.setDisplayStyle(prefs.getInt("host_display_style", 0));
        mAdapter.setLayoutDensity(prefs.getInt("list_density", 1));
        
        prefListener = (sharedPreferences, key) -> {
            if ("host_display_style".equals(key)) {
                mAdapter.setDisplayStyle(sharedPreferences.getInt(key, 0));
            }
            if ("list_density".equals(key)) {
                mAdapter.setLayoutDensity(sharedPreferences.getInt(key, 1));
            }
            if ("home_host_list_auto_fetch_enabled".equals(key)) {
                // 实时响应首页主机列表自动获取信息开关
                boolean enabled = sharedPreferences.getBoolean(key, false);
                if (enabled) {
                    startMonitoring();
                } else {
                    stopMonitoring();
                    if (mAdapter != null) {
                        mAdapter.clearStatus();
                    }
                }
            }
        };
        prefs.registerOnSharedPreferenceChangeListener(prefListener);
        
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));

        mHostViewModel = new ViewModelProvider(requireActivity()).get(MainViewModel.class);
        mHostViewModel.getAllHosts().observe(getViewLifecycleOwner(), hosts -> {
            mAllHosts = new ArrayList<>(hosts);
            filterAndSort();
            // 如果当前未选择主机且列表非空，默认选择第一个
            if (navViewModel.getCurrentHostId().getValue() == null && !mAllHosts.isEmpty()) {
                long savedId = prefs.getLong("current_host_id", -1L);
                HostEntity selected = null;
                if (savedId > 0) {
                    for (HostEntity host : mAllHosts) {
                        if (host.id == savedId) {
                            selected = host;
                            break;
                        }
                    }
                }
                if (selected == null) {
                    selected = mAllHosts.get(0);
                    persistCurrentHost(selected);
                }
                navViewModel.setCurrentHostId(selected.id);
            }
        });
        navViewModel.getCurrentHostId().observe(getViewLifecycleOwner(), id -> {
            mAdapter.setCurrentHostId(id);
        });

        FloatingActionButton fab = view.findViewById(R.id.fab);
        fab.setOnClickListener(this::showAddOptions);

        View addButton = view.findViewById(R.id.btn_empty_add);
        if (addButton != null) {
            addButton.setOnClickListener(v -> {
                Intent intent = new Intent(requireContext(), AddHostActivity.class);
                startActivity(intent);
            });
        }
        View scanButton = view.findViewById(R.id.btn_empty_scan);
        if (scanButton != null) {
            scanButton.setOnClickListener(v -> {
                if (getActivity() instanceof com.orcterm.ui.MainActivity) {
                    ((com.orcterm.ui.MainActivity) getActivity()).startQrScan();
                } else {
                    showAddOptions(v);
                }
            });
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (prefs != null && prefListener != null) {
            prefs.unregisterOnSharedPreferenceChangeListener(prefListener);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        if (getView() != null) {
            AppBackgroundHelper.applyFromPrefs(requireContext(), getView());
        }
        // 首页主机列表是否自动获取信息
        if (isHomeHostListAutoFetchEnabled()) {
            startMonitoring();
        } else {
            stopMonitoring();
            if (mAdapter != null) {
                mAdapter.clearStatus();
            }
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        stopMonitoring();
    }

    private void startMonitoring() {
        if (monitorScheduler != null && !monitorScheduler.isShutdown()) return;
        monitorScheduler = Executors.newSingleThreadScheduledExecutor();
        monitorScheduler.scheduleWithFixedDelay(this::refreshStats, 0, 5000, TimeUnit.MILLISECONDS);
    }

    // 判断首页主机列表是否启用自动获取信息
    private boolean isHomeHostListAutoFetchEnabled() {
        return prefs != null && prefs.getBoolean("home_host_list_auto_fetch_enabled", false);
    }

    private void stopMonitoring() {
        if (monitorScheduler != null) {
            monitorScheduler.shutdownNow();
            monitorScheduler = null;
        }
        executor.execute(() -> {
            SshNative ssh = new SshNative();
            for (Long handle : monitorHandles.values()) {
                if (handle != 0) ssh.disconnect(handle);
            }
            monitorHandles.clear();
            prevCpuStats.clear();
            prevIoStats.clear();
            prevDiskStats.clear();
        });
    }

    private void refreshStats() {
        if (mAdapter == null || mAllHosts == null || mAllHosts.isEmpty()) return;
        for (HostEntity host : mAllHosts) {
             executor.execute(() -> updateHostStats(host));
        }
    }

    private void updateHostStats(HostEntity host) {
        long handle = monitorHandles.getOrDefault(host.id, 0L);
        SshNative ssh = new SshNative();
        long start = System.currentTimeMillis();
        
        try {
            if (handle == 0) {
                handle = ssh.connect(host.hostname, host.port);
                if (handle == 0) throw new Exception("Connect failed");
                
                int auth;
                if (host.authType == 1 && host.keyPath != null) {
                    auth = ssh.authKey(handle, host.username, host.keyPath);
                } else {
                    auth = ssh.authPassword(handle, host.username, host.password);
                }
                
                if (auth != 0) {
                    ssh.disconnect(handle);
                    throw new Exception("Auth failed");
                }
                monitorHandles.put(host.id, handle);
            }

            // Execute composite command
            String cmd = CommandConstants.CMD_HOST_STATS_COMPOSITE;
            String output = ssh.exec(handle, cmd);
            long latency = System.currentTimeMillis() - start;
            
            HostStatus status = parseStats(host.id, output, latency);
            if (getActivity() != null) {
                getActivity().runOnUiThread(() -> mAdapter.updateStatus(host.id, status));
            }
            
        } catch (Exception e) {
            monitorHandles.remove(host.id); // Remove invalid handle
            HostStatus errorStatus = new HostStatus();
            errorStatus.isOnline = false;
            if (getActivity() != null) {
                getActivity().runOnUiThread(() -> mAdapter.updateStatus(host.id, errorStatus));
            }
        }
    }
    
    private HostStatus parseStats(long hostId, String output, long latency) {
        HostStatus status = new HostStatus();
        status.isOnline = true;
        status.latency = latency;
        status.timestamp = System.currentTimeMillis();
        
        String[] sections = output.split("SECTION_");
        
        // 1. Uptime (First part)
        if (sections.length > 0) {
            String[] lines = sections[0].split("\n");
            if (lines.length > 0) {
                try {
                    double uptimeSec = Double.parseDouble(lines[0].trim().split(" ")[0]);
                    status.uptime = formatUptime((long)uptimeSec);
                } catch (Exception e) {}
            }
        }
        
        // 2. CPU
        if (sections.length > 1 && sections[1].startsWith("CPU")) {
             // cpu  2255 34 2290 22625563 ...
             String line = sections[1].substring(3).trim(); // Remove "CPU" prefix from split
             // Actually split keeps separator? No. "SECTION_CPU" -> split by "SECTION_" -> "CPU..."
             // split by "SECTION_" results in:
             // [0]: uptime...
             // [1]: CPU\n...
             // [2]: MEM\n...
             
             String[] lines = sections[1].split("\n");
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
                         
                         long[] prev = prevCpuStats.get(hostId);
                         if (prev != null) {
                             long totalDelta = total - prev[0];
                             long workDelta = work - prev[1];
                             if (totalDelta > 0) {
                                 status.cpuUsagePercent = (int) (workDelta * 100 / totalDelta);
                             }
                         }
                         status.cpuUsage = status.cpuUsagePercent + "%";
                          prevCpuStats.put(hostId, new long[]{total, work});
                      }
                  }
              }
         }
         
         // 2.5 Cores
         if (sections.length > 2 && sections[2].startsWith("CORES")) {
             String[] lines = sections[2].split("\n");
             if (lines.length > 1) {
                 status.cpuCores = lines[1].trim();
             }
         }
         
         // 3. Mem
         if (sections.length > 3 && sections[3].startsWith("MEM")) {
             // MemTotal:        16306508 kB
             // MemAvailable:    ...
             String[] lines = sections[3].split("\n");
             long total = 0;
             long available = 0;
             
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
                 status.totalMem = formatSize(total * 1024);
                 if (available > 0) {
                     long used = total - available;
                     status.memUsagePercent = (int) (used * 100 / total);
                     status.memUsage = status.memUsagePercent + "%";
                 }
             }
         }
         
         // 4. Net
         if (sections.length > 4 && sections[4].startsWith("NET")) {
             // Inter-|   Receive ...
             // eth0: 123 456 ...
             long rx = 0;
             long tx = 0;
             String[] lines = sections[4].split("\n");
             for (String l : lines) {
                 if (l.contains(":")) {
                     String[] parts = l.split(":")[1].trim().split("\\s+");
                     if (parts.length >= 9) {
                         rx += Long.parseLong(parts[0]);
                         tx += Long.parseLong(parts[8]); // usually 9th col is tx bytes
                     }
                 }
             }
             
             long[] prev = prevIoStats.get(hostId);
             if (prev != null) {
                 long timeDelta = status.timestamp - prev[0];
                 if (timeDelta > 0) {
                     long rxDelta = rx - prev[1];
                     long txDelta = tx - prev[2];
                     // B/ms * 1000 = B/s
                     status.netDownload = formatSize(rxDelta * 1000 / timeDelta) + "/s";
                     status.netUpload = formatSize(txDelta * 1000 / timeDelta) + "/s";
                 }
             }
             // Store for next [ts, rx, tx, r, w]
             long[] newStats = new long[]{status.timestamp, rx, tx, 0, 0};
             if (prev != null && prev.length >= 5) {
                 newStats[3] = prev[3];
                 newStats[4] = prev[4];
             }
             prevIoStats.put(hostId, newStats);
        }
        
        // 5. Disk Usage
        if (sections.length > 5 && sections[5].startsWith("DISK_USAGE")) {
             // /dev/root        12345678  ...
             String[] lines = sections[5].split("\n");
             for (String l : lines) {
                 if (l.startsWith("/dev/") || l.trim().startsWith("/")) {
                     String[] parts = l.split("\\s+");
                     if (parts.length >= 2) {
                         try {
                             long total = Long.parseLong(parts[1]);
                             status.totalDisk = formatSize(total);
                         } catch (Exception e) {}
                     }
                 }
             }
        }
        
        // 6. Disk IO
        if (sections.length > 6 && sections[6].startsWith("DISK_IO")) {
             // 8       0 sda 1165 419 66346 362 0 0 0 0 0 324 362 ...
             // Field 6 (index 5) = sectors read
             // Field 10 (index 9) = sectors written
             long readSectors = 0;
             long writeSectors = 0;
             boolean matched = false;
             
             String[] lines = sections[6].split("\n");
             for (String l : lines) {
                 String[] parts = l.trim().split("\\s+");
                 if (parts.length >= 10) {
                     String dev = parts[2];
                     if (isPhysicalDisk(dev)) {
                         matched = true;
                         try {
                             readSectors += Long.parseLong(parts[5]);
                             writeSectors += Long.parseLong(parts[9]);
                         } catch (Exception e) {}
                     }
                 }
             }

             if (!matched) {
                 for (String l : lines) {
                     String[] parts = l.trim().split("\\s+");
                     if (parts.length >= 10) {
                         String dev = parts[2];
                         if (isFallbackDisk(dev)) {
                             matched = true;
                             try {
                                 readSectors += Long.parseLong(parts[5]);
                                 writeSectors += Long.parseLong(parts[9]);
                             } catch (Exception e) {}
                         }
                     }
                 }
             }
             
             long[] prev = prevDiskStats.get(hostId);
             if (prev != null) {
                 long timeDelta = status.timestamp - prev[0];
                 if (timeDelta > 0) {
                     // Sector = 512 bytes
                     long readDelta = (readSectors - prev[1]) * 512;
                     long writeDelta = (writeSectors - prev[2]) * 512;
                     
                     status.diskRead = formatSize(readDelta * 1000 / timeDelta) + "/s";
                     status.diskWrite = formatSize(writeDelta * 1000 / timeDelta) + "/s";
                 }
             }
             
             prevDiskStats.put(hostId, new long[]{status.timestamp, readSectors, writeSectors});
        }
        
        return status;
    }
    
    private boolean isPhysicalDisk(String dev) {
        return dev.matches("^(sd[a-z]|vd[a-z]|xvd[a-z]|nvme\\d+n\\d+|mmcblk\\d+)$");
    }

    private boolean isFallbackDisk(String dev) {
        return dev.matches("^(dm-\\d+|md\\d+)$");
    }
    
    private String formatUptime(long sec) {
        if (sec < 60) return sec + "s";
        if (sec < 3600) return (sec/60) + "m";
        if (sec < 86400) return (sec/3600) + " Hours";
        return (sec/86400) + " Days";
    }
    
    private String formatSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f K", bytes / 1024.0);
        if (bytes < 1024 * 1024 * 1024) return String.format("%.1f M", bytes / (1024.0 * 1024));
        return String.format("%.1f G", bytes / (1024.0 * 1024 * 1024));
    }

    @Override
    public void onCreateOptionsMenu(@NonNull Menu menu, @NonNull MenuInflater inflater) {
        inflater.inflate(R.menu.menu_main, menu);
        MenuItem searchItem = menu.findItem(R.id.action_search);
        SearchView searchView = (SearchView) searchItem.getActionView();
        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                return false;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                mCurrentSearchQuery = newText;
                filterAndSort();
                return true;
            }
        });
        MenuItem profileItem = menu.findItem(R.id.action_profile);
        if (profileItem != null) profileItem.setVisible(false);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.sort_name) {
            mCurrentSort = Comparator.comparing(h -> h.alias);
        } else if (id == R.id.sort_ip) {
            mCurrentSort = Comparator.comparing(h -> h.hostname);
        } else if (id == R.id.sort_os) {
            mCurrentSort = Comparator.comparing(h -> h.osName != null ? h.osName : "");
        } else if (id == R.id.sort_status) {
            mCurrentSort = Comparator.comparing(h -> h.status != null ? h.status : "");
        }
        if (id == R.id.sort_name || id == R.id.sort_ip || id == R.id.sort_os || id == R.id.sort_status) {
            filterAndSort();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void filterAndSort() {
        List<HostEntity> filtered = new ArrayList<>();
        if (TextUtils.isEmpty(mCurrentSearchQuery)) {
            filtered.addAll(mAllHosts);
        } else {
            String query = mCurrentSearchQuery.toLowerCase();
            for (HostEntity host : mAllHosts) {
                if (host.alias.toLowerCase().contains(query) ||
                    host.hostname.toLowerCase().contains(query) ||
                    (host.osName != null && host.osName.toLowerCase().contains(query))) {
                    filtered.add(host);
                }
            }
        }

        if (mCurrentSort != null) {
            Collections.sort(filtered, mCurrentSort);
        }
        mAdapter.submitList(filtered);
        View root = getView();
        if (root != null) {
            View empty = root.findViewById(R.id.text_empty);
            empty.setVisibility(filtered.isEmpty() ? View.VISIBLE : View.GONE);
        }
    }

    private void showAddOptions(View view) {
        PopupMenu popup = new PopupMenu(requireContext(), view);
        popup.getMenu().add("手动添加");
        popup.getMenu().add("扫码导入");
        popup.setOnMenuItemClickListener(item -> {
            if ("手动添加".equals(item.getTitle())) {
                Intent intent = new Intent(requireContext(), AddHostActivity.class);
                startActivity(intent);
            } else {
                if (getActivity() instanceof com.orcterm.ui.MainActivity) {
                    ((com.orcterm.ui.MainActivity) getActivity()).startQrScan();
                }
            }
            return true;
        });
        popup.show();
    }

    private void showHostActions(View view, HostEntity host) {
        PopupMenu popup = new PopupMenu(requireContext(), view);
        popup.getMenu().add(0, 0, 0, "设为当前服务器");
        popup.getMenu().add(0, 1, 0, "编辑");
        popup.getMenu().add(0, 2, 0, "容器");
        popup.getMenu().add(0, 3, 0, "文件");
        popup.getMenu().add(0, 4, 0, "监控");
        popup.getMenu().add(0, 5, 0, "诊断/检测");
        popup.getMenu().add(0, 6, 0, "重启");
        popup.getMenu().add(0, 7, 0, "关机");
        popup.getMenu().add(0, 8, 0, "删除");
        popup.getMenu().add(0, 9, 0, "生成二维码");
        popup.getMenu().add(0, 10, 0, "切换视图");
        popup.getMenu().add(0, 11, 0, "WOL 唤醒");
        popup.setOnMenuItemClickListener(item -> {
            switch (item.getItemId()) {
                case 0:
                    // 保存当前主机偏好
                    persistCurrentHost(host);
                    // 兼容：继续作为首页主机来源
                    String homeLabel = buildHomeHostLabel(host);
                    prefs.edit()
                        .putLong("home_host_id", host.id)
                        .putString("home_host_label", homeLabel)
                        .apply();
                    navViewModel.setCurrentHostId(host.id);
                    View root = getView();
                    if (root != null) {
                        Snackbar.make(root, "已切换至 " + homeLabel, Snackbar.LENGTH_SHORT).show();
                    }
                    break;
                case 1: editHost(host); break;
                case 2: openTerminal(host); break;
                case 3: openSftp(host); break;
                case 4: openMonitor(host); break;
                case 5: diagnoseHost(host); break;
                case 6: confirmPowerAction(host, "reboot"); break;
                case 7: confirmPowerAction(host, "shutdown"); break;
                case 8: deleteHost(host); break;
                case 9: 
                    generateQRCode(host);
                    break;
                case 10: 
                    int currentStyle = mAdapter.getDisplayStyle();
                    int newStyle = (currentStyle + 1) % 4;
                    prefs.edit().putInt("host_display_style", newStyle).apply();
                    break;
                case 11:
                    showWakeOnLanDialog(host);
                    break;
            }
            return true;
        });
        popup.show();
    }

    private void showWakeOnLanDialog(HostEntity host) {
        String macKey = "wol_mac_" + host.id;
        String broadcastKey = "wol_broadcast_" + host.id;
        String portKey = "wol_port_" + host.id;
        String identityKey = buildWolIdentityKey(host.hostname, host.port, host.username);
        String savedMac = prefs.getString(macKey, "");
        if (TextUtils.isEmpty(savedMac)) {
            savedMac = prefs.getString(identityKey, "");
        }
        String savedBroadcast = prefs.getString(broadcastKey, "255.255.255.255");
        int savedPort = prefs.getInt(portKey, 9);

        android.widget.LinearLayout layout = new android.widget.LinearLayout(requireContext());
        layout.setOrientation(android.widget.LinearLayout.VERTICAL);
        layout.setPadding(32, 32, 32, 16);

        android.widget.TextView hint = new android.widget.TextView(requireContext());
        hint.setText("输入 MAC 地址与广播地址");
        layout.addView(hint);

        android.widget.EditText macInput = new android.widget.EditText(requireContext());
        macInput.setHint("MAC 地址 (如 AA:BB:CC:DD:EE:FF)");
        macInput.setText(savedMac);
        layout.addView(macInput);

        android.widget.EditText broadcastInput = new android.widget.EditText(requireContext());
        broadcastInput.setHint("广播地址 (如 255.255.255.255)");
        broadcastInput.setText(savedBroadcast);
        layout.addView(broadcastInput);

        android.widget.EditText portInput = new android.widget.EditText(requireContext());
        portInput.setHint("端口 (默认 9)");
        portInput.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        portInput.setText(String.valueOf(savedPort));
        layout.addView(portInput);

        new AlertDialog.Builder(requireContext())
            .setTitle("WOL 唤醒: " + host.alias)
            .setView(layout)
            .setPositiveButton("唤醒", (d, w) -> {
                String mac = macInput.getText().toString().trim();
                String broadcast = broadcastInput.getText().toString().trim();
                String portStr = portInput.getText().toString().trim();
                if (TextUtils.isEmpty(mac)) {
                    Toast.makeText(requireContext(), "MAC 地址不能为空", Toast.LENGTH_SHORT).show();
                    return;
                }
                if (TextUtils.isEmpty(broadcast)) {
                    broadcast = "255.255.255.255";
                }
                int port = 9;
                try {
                    port = Integer.parseInt(portStr);
                } catch (Exception ignored) {
                }
                prefs.edit()
                    .putString(macKey, mac)
                    .putString(identityKey, mac)
                    .putString(broadcastKey, broadcast)
                    .putInt(portKey, port)
                    .apply();
                sendWakeOnLan(host, mac, broadcast, port);
            })
            .setNegativeButton("取消", null)
            .show();
    }

    private String buildWolIdentityKey(String hostname, int port, String username) {
        return "wol_mac_identity_" + hostname + "|" + port + "|" + username;
    }

    private void sendWakeOnLan(HostEntity host, String mac, String broadcast, int port) {
        executor.execute(() -> {
            try {
                byte[] packet = buildMagicPacket(mac);
                InetAddress address = InetAddress.getByName(broadcast);
                DatagramPacket datagram = new DatagramPacket(packet, packet.length, address, port);
                try (DatagramSocket socket = new DatagramSocket()) {
                    socket.setBroadcast(true);
                    socket.send(datagram);
                }
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() ->
                        Toast.makeText(requireContext(), "已发送唤醒包: " + host.alias, Toast.LENGTH_SHORT).show()
                    );
                }
            } catch (Exception e) {
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() ->
                        Toast.makeText(requireContext(), "唤醒失败: " + e.getMessage(), Toast.LENGTH_SHORT).show()
                    );
                }
            }
        });
    }

    private byte[] buildMagicPacket(String mac) {
        String clean = mac.replace(":", "").replace("-", "").replace(".", "").replace(" ", "");
        if (clean.length() != 12) {
            throw new IllegalArgumentException("MAC 地址格式不正确");
        }
        byte[] macBytes = new byte[6];
        for (int i = 0; i < 6; i++) {
            int idx = i * 2;
            macBytes[i] = (byte) Integer.parseInt(clean.substring(idx, idx + 2), 16);
        }
        byte[] packet = new byte[6 + 16 * 6];
        for (int i = 0; i < 6; i++) {
            packet[i] = (byte) 0xFF;
        }
        for (int i = 6; i < packet.length; i += 6) {
            System.arraycopy(macBytes, 0, packet, i, macBytes.length);
        }
        return packet;
    }

    private void editHost(HostEntity host) {
        Intent intent = new Intent(requireContext(), AddHostActivity.class);
        intent.putExtra("is_edit", true);
        intent.putExtra("host_id", host.id);
        intent.putExtra("alias", host.alias);
        intent.putExtra("hostname", host.hostname);
        intent.putExtra("port", host.port);
        intent.putExtra("username", host.username);
        intent.putExtra("password", host.password);
        intent.putExtra("auth_type", host.authType);
        intent.putExtra("key_path", host.keyPath);
        intent.putExtra("os_name", host.osName);
        intent.putExtra("os_version", host.osVersion);
        intent.putExtra("container_engine", host.containerEngine);
        intent.putExtra("status", host.status);
        intent.putExtra("last_connected", host.lastConnected);
        intent.putExtra("connect_timeout_sec", host.connectTimeoutSec);
        intent.putExtra("keepalive_interval_sec", host.keepAliveIntervalSec);
        intent.putExtra("hostkey_policy", host.hostKeyPolicy);
        intent.putExtra("environment_type", host.environmentType);
        intent.putExtra("terminal_theme_preset", host.terminalThemePreset);
        startActivity(intent);
    }

    private void deleteHost(HostEntity host) {
        new AlertDialog.Builder(requireContext())
            .setTitle("确认删除")
            .setMessage("确定要删除主机 " + host.alias + " 吗？")
            .setPositiveButton("删除", (d, w) -> mHostViewModel.delete(host))
            .setNegativeButton("取消", null)
            .show();
    }

    private void openTerminal(HostEntity host) {
        openActivity(DockerActivity.class, host);
    }

    private void openSftp(HostEntity host) {
        openActivity(SftpActivity.class, host);
    }

    private void openMonitor(HostEntity host) {
        openActivity(HostDetailActivity.class, host);
    }

    private String buildHomeHostLabel(HostEntity host) {
        if (host == null) return "";
        if (!TextUtils.isEmpty(host.alias)) return host.alias;
        String hostname = host.hostname == null ? "" : host.hostname;
        String username = host.username == null ? "" : host.username;
        if (TextUtils.isEmpty(username)) return hostname;
        if (TextUtils.isEmpty(hostname)) return username;
        return username + "@" + hostname;
    }

    private void persistCurrentHost(HostEntity host) {
        if (host == null) return;
        String label = buildHomeHostLabel(host);
        prefs.edit()
            .putLong("current_host_id", host.id)
            .putString("current_host_label", label)
            .putString("current_host_hostname", host.hostname)
            .putString("current_host_username", host.username)
            .putInt("current_host_port", host.port)
            .apply();
    }

    private void diagnoseHost(HostEntity host) {
        Toast.makeText(requireContext(), "正在诊断 " + host.alias + "...", Toast.LENGTH_SHORT).show();
        executor.execute(() -> {
            SshNative ssh = new SshNative();
            long handle = 0;
            try {
                handle = ssh.connect(host.hostname, host.port);
                if (handle == 0) throw new Exception("连接失败");

                int auth;
                if (host.authType == 1 && host.keyPath != null) {
                    auth = ssh.authKey(handle, host.username, host.keyPath);
                } else {
                    auth = ssh.authPassword(handle, host.username, host.password);
                }

                if (auth != 0) throw new Exception("认证失败");

                String osRelease = ssh.exec(handle, CommandConstants.CMD_OS_RELEASE);
                String uname = ssh.exec(handle, CommandConstants.CMD_UNAME_A);
                String uptime = ssh.exec(handle, CommandConstants.CMD_UPTIME);

                String osName = "Linux";
                String osVersion = "";

                String[] lines = osRelease.split("\n");
                for (String line : lines) {
                    if (line.startsWith("PRETTY_NAME=")) {
                        osName = line.substring(12).replace("\"", "").trim();
                    } else if (line.startsWith("ID=") && osName.equals("Linux")) {
                        osName = line.substring(3).replace("\"", "").trim();
                        if (osName.length() > 0) osName = osName.substring(0, 1).toUpperCase() + osName.substring(1);
                    } else if (line.startsWith("VERSION_ID=")) {
                        osVersion = line.substring(11).replace("\"", "").trim();
                    }
                }

                host.osName = osName;
                host.osVersion = osVersion;
                host.status = "online";
                host.lastConnected = System.currentTimeMillis();
                mHostViewModel.update(host);

                String result = "主机: " + host.alias + "\n" +
                                "系统: " + osName + " " + osVersion + "\n" +
                                "内核: " + uname.trim() + "\n" +
                                "运行时间: " + uptime.trim();

                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> {
                        new AlertDialog.Builder(requireContext())
                            .setTitle("诊断结果")
                            .setMessage(result)
                            .setPositiveButton("确定", null)
                            .show();
                    });
                }
            } catch (Exception e) {
                host.status = "offline";
                mHostViewModel.update(host);
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> Toast.makeText(requireContext(), host.alias + " 诊断失败: " + e.getMessage(), Toast.LENGTH_LONG).show());
                }
            } finally {
                if (handle != 0) ssh.disconnect(handle);
            }
        });
    }

    private void confirmPowerAction(HostEntity host, String action) {
        String actionName = action.equals("reboot") ? "重启" : "关机";
        new AlertDialog.Builder(requireContext())
            .setTitle("确认" + actionName)
            .setMessage("确定要" + actionName + "主机 " + host.alias + " 吗？\n需要 sudo 权限。")
            .setPositiveButton("执行", (d, w) -> powerAction(host, action))
            .setNegativeButton("取消", null)
            .show();
    }

    private void powerAction(HostEntity host, String action) {
        String cmd = action.equals("reboot") ? CommandConstants.CMD_SUDO_REBOOT : CommandConstants.CMD_SUDO_SHUTDOWN;
        executor.execute(() -> {
            SshNative ssh = new SshNative();
            long handle = 0;
            try {
                handle = ssh.connect(host.hostname, host.port);
                if (handle == 0) throw new Exception("连接失败");

                int auth;
                if (host.authType == 1) auth = ssh.authKey(handle, host.username, host.keyPath);
                else auth = ssh.authPassword(handle, host.username, host.password);

                if (auth != 0) throw new Exception("认证失败");

                ssh.exec(handle, cmd);

                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> Toast.makeText(requireContext(), host.alias + ": 命令已发送", Toast.LENGTH_SHORT).show());
                }
            } catch (Exception e) {
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> Toast.makeText(requireContext(), host.alias + " 执行失败: " + e.getMessage(), Toast.LENGTH_LONG).show());
                }
            } finally {
                if (handle != 0) ssh.disconnect(handle);
            }
        });
    }

    private void openHostDetail(HostEntity host) {
        Intent intent = new Intent(requireContext(), HostDetailActivity.class);
        intent.putExtra("host_id", host.id);
        intent.putExtra("hostname", host.hostname);
        intent.putExtra("username", host.username);
        intent.putExtra("port", host.port);
        intent.putExtra("password", host.password);
        intent.putExtra("auth_type", host.authType);
        intent.putExtra("key_path", host.keyPath);
        intent.putExtra("os_version", host.osVersion);
        intent.putExtra("container_engine", host.containerEngine);
        startActivity(intent);
    }

    private void openActivity(Class<?> cls, HostEntity host) {
        Intent intent = new Intent(requireContext(), cls);
        intent.putExtra("host_id", host.id);
        intent.putExtra("hostname", host.hostname);
        intent.putExtra("username", host.username);
        intent.putExtra("port", host.port);
        intent.putExtra("password", host.password);
        intent.putExtra("auth_type", host.authType);
        intent.putExtra("key_path", host.keyPath);
        intent.putExtra("container_engine", host.containerEngine);
        startActivity(intent);
    }

    private void generateQRCode(HostEntity host) {
        // 构建主机信息的JSON字符串
        String hostJson = buildHostJson(host);
        android.util.Log.d("QR_GENERATE", "Generated JSON: " + hostJson);
        
        try {
            // 生成二维码
            QRCodeWriter writer = new QRCodeWriter();
            BitMatrix bitMatrix = writer.encode(hostJson, BarcodeFormat.QR_CODE, 512, 512);
            int width = bitMatrix.getWidth();
            int height = bitMatrix.getHeight();
            Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565);
            
            for (int x = 0; x < width; x++) {
                for (int y = 0; y < height; y++) {
                    bitmap.setPixel(x, y, bitMatrix.get(x, y) ? Color.BLACK : Color.WHITE);
                }
            }

            // 显示二维码对话框
            showQRCodeDialog(host, bitmap, hostJson);
            
        } catch (WriterException e) {
            Toast.makeText(requireContext(), "生成二维码失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private String buildHostJson(HostEntity host) {
        StringBuilder json = new StringBuilder();
        json.append("{");
        json.append("\"type\":\"orcterm_host\",");
        json.append("\"version\":\"1.0\",");
        json.append("\"alias\":\"").append(escapeJson(host.alias)).append("\",");
        json.append("\"hostname\":\"").append(escapeJson(host.hostname)).append("\",");
        json.append("\"port\":").append(host.port).append(",");
        json.append("\"username\":\"").append(escapeJson(host.username)).append("\",");
        json.append("\"auth_type\":").append(host.authType).append(",");
        
        // 只在密钥认证时包含密钥路径
        if (host.authType == 1 && host.keyPath != null && !host.keyPath.isEmpty()) {
            json.append("\"key_path\":\"").append(escapeJson(host.keyPath)).append("\",");
        }
        
        // 可选字段
        if (host.osName != null) {
            json.append("\"os_name\":\"").append(escapeJson(host.osName)).append("\",");
        }
        if (host.osVersion != null) {
            json.append("\"os_version\":\"").append(escapeJson(host.osVersion)).append("\",");
        }
        if (host.containerEngine != null) {
            json.append("\"container_engine\":\"").append(escapeJson(host.containerEngine)).append("\",");
        }
        
        // 包含密码（如果存在）
        if (host.password != null && !host.password.isEmpty()) {
            json.append(",\"password\":\"").append(escapeJson(host.password)).append("\"");
            json.append(",\"password_required\":false");
        } else {
            json.append(",\"password_required\":true");
        }
        json.append("}");
        
        return json.toString();
    }

    private String escapeJson(String str) {
        if (str == null) return "";
        return str.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    private void showQRCodeDialog(HostEntity host, Bitmap qrBitmap, String hostJson) {
        // 创建二维码显示布局
        android.widget.LinearLayout layout = new android.widget.LinearLayout(requireContext());
        layout.setOrientation(android.widget.LinearLayout.VERTICAL);
        layout.setPadding(32, 32, 32, 32);
        layout.setGravity(android.view.Gravity.CENTER);

        // 标题
        android.widget.TextView title = new android.widget.TextView(requireContext());
        title.setText("主机二维码 - " + host.alias);
        title.setTextSize(16);
        title.setPadding(0, 0, 0, 16);
        layout.addView(title);

        // 二维码图片
        android.widget.ImageView qrImage = new android.widget.ImageView(requireContext());
        qrImage.setImageBitmap(qrBitmap);
        qrImage.setScaleType(android.widget.ImageView.ScaleType.FIT_CENTER);
        qrImage.setPadding(16, 16, 16, 16);
        layout.addView(qrImage);

        // 说明文字
        android.widget.TextView desc = new android.widget.TextView(requireContext());
        String descText = host.password != null && !host.password.isEmpty() ? 
                "其他设备扫描此二维码可直接添加主机" : 
                "其他设备扫描此二维码可添加主机\n（密码需要手动输入）";
        desc.setText(descText);
        desc.setTextSize(12);
        desc.setGravity(android.view.Gravity.CENTER);
        desc.setPadding(0, 8, 0, 16);
        layout.addView(desc);

        // 密码状态提示
        if (host.password != null && !host.password.isEmpty()) {
            android.widget.TextView passHint = new android.widget.TextView(requireContext());
            passHint.setText("⚠️ 此二维码包含密码信息，请妥善保管");
            passHint.setTextSize(11);
            passHint.setTextColor(Color.parseColor("#FF6B35"));
            passHint.setPadding(0, 4, 0, 16);
            layout.addView(passHint);
        }

        // 复制JSON按钮
        android.widget.Button copyButton = new android.widget.Button(requireContext());
        copyButton.setText("复制配置JSON");
        copyButton.setOnClickListener(v -> {
            android.content.ClipboardManager clipboard = (android.content.ClipboardManager) 
                requireContext().getSystemService(android.content.Context.CLIPBOARD_SERVICE);
            android.content.ClipData clip = android.content.ClipData.newPlainText("Host Config", hostJson);
            clipboard.setPrimaryClip(clip);
            Toast.makeText(requireContext(), "配置已复制到剪贴板", Toast.LENGTH_SHORT).show();
        });
        layout.addView(copyButton);

        // 保存图片按钮
        android.widget.Button saveButton = new android.widget.Button(requireContext());
        saveButton.setText("保存二维码图片");
        saveButton.setOnClickListener(v -> saveQRCodeImage(qrBitmap, host.alias));
        layout.addView(saveButton);

        // 创建对话框
        new AlertDialog.Builder(requireContext())
            .setView(layout)
            .setPositiveButton("关闭", null)
            .show();
    }

    private void saveQRCodeImage(Bitmap bitmap, String alias) {
        try {
            // 生成文件名
            String fileName = "orcterm_qr_" + alias.replaceAll("[^a-zA-Z0-9]", "_") + "_" + System.currentTimeMillis() + ".png";
            
            // 保存到Pictures目录
            android.content.ContentValues values = new android.content.ContentValues();
            values.put(android.provider.MediaStore.Images.Media.DISPLAY_NAME, fileName);
            values.put(android.provider.MediaStore.Images.Media.MIME_TYPE, "image/png");
            values.put(android.provider.MediaStore.Images.Media.RELATIVE_PATH, android.os.Environment.DIRECTORY_PICTURES + "/OrcTerm");
            
            android.net.Uri uri = requireContext().getContentResolver().insert(
                android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
            
            if (uri != null) {
                try (java.io.OutputStream out = requireContext().getContentResolver().openOutputStream(uri)) {
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
                }
                Toast.makeText(requireContext(), "二维码已保存到相册", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(requireContext(), "保存失败", Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            Toast.makeText(requireContext(), "保存失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        executor.shutdownNow();
    }
}
