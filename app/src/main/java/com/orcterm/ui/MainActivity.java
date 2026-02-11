package com.orcterm.ui;

import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Toast;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;
import androidx.viewpager2.widget.ViewPager2;
import androidx.fragment.app.Fragment;
import androidx.viewpager2.adapter.FragmentStateAdapter;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.snackbar.Snackbar;
import com.google.zxing.integration.android.IntentIntegrator;
import com.google.zxing.integration.android.IntentResult;
import org.json.JSONObject;

import com.orcterm.R;
import com.orcterm.core.ssh.SshNative;
import com.orcterm.data.AppDatabase;
import com.orcterm.data.HostDao;
import com.orcterm.data.HostEntity;
import com.orcterm.databinding.ActivityMainBinding;
import com.orcterm.ui.AddHostActivity;
import com.orcterm.ui.nav.NavViewModel;
import com.orcterm.ui.nav.ServersFragment;
import com.orcterm.ui.nav.TerminalFragment;
import com.orcterm.ui.nav.FilesFragment;
import com.orcterm.ui.nav.SettingsFragment;
import com.orcterm.util.AppBackgroundHelper;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 应用主界面，管理底部导航与页面切换
 */
public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";
    private ActivityMainBinding binding;
    private ViewPager2 viewPager;
    private BottomNavigationView bottomNavigationView;
    private NavViewModel navViewModel;
    private HostDao hostDao;
    private ExecutorService executor = Executors.newCachedThreadPool();
    private SharedPreferences prefs;
    private boolean autoConnectHandled = false;
    private int currentPage = 0;
    private String currentHostLabel = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        // Initialize database
        hostDao = AppDatabase.getDatabase(this).hostDao();
        prefs = getSharedPreferences("orcterm_prefs", Context.MODE_PRIVATE);

        // Setup ViewPager
        viewPager = binding.viewPager;
        bottomNavigationView = binding.bottomNav;

        // Create adapter
        ViewPagerAdapter adapter = new ViewPagerAdapter(this);
        viewPager.setAdapter(adapter);
        AppBackgroundHelper.applyFromPrefs(this, binding.getRoot());

        // Setup navigation
        bottomNavigationView.setOnItemSelectedListener(item -> {
            Long currentHostId = navViewModel.getCurrentHostId().getValue();
            if ((item.getItemId() == R.id.nav_terminal || item.getItemId() == R.id.nav_files) && currentHostId == null) {
                Snackbar.make(binding.getRoot(), "请先选择主机", Snackbar.LENGTH_SHORT).show();
                return false;
            }
            int itemId = item.getItemId();
            if (itemId == R.id.nav_servers) {
                viewPager.setCurrentItem(0);
            } else if (itemId == R.id.nav_terminal) {
                viewPager.setCurrentItem(1);
            } else if (itemId == R.id.nav_files) {
                viewPager.setCurrentItem(2);
            } else if (itemId == R.id.nav_profile) {
                viewPager.setCurrentItem(3);
            }
            return true;
        });

        // Sync ViewPager with Bottom Navigation
        viewPager.registerOnPageChangeCallback(new androidx.viewpager2.widget.ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                currentPage = position;
                switch (position) {
                    case 0:
                        bottomNavigationView.setSelectedItemId(R.id.nav_servers);
                        break;
                    case 1:
                        bottomNavigationView.setSelectedItemId(R.id.nav_terminal);
                        break;
                    case 2:
                        bottomNavigationView.setSelectedItemId(R.id.nav_files);
                        break;
                    case 3:
                        bottomNavigationView.setSelectedItemId(R.id.nav_profile);
                        break;
                }
                updateToolbarForPage(position);
            }
        });

        // Initialize ViewModel
        navViewModel = new ViewModelProvider(this).get(NavViewModel.class);
        navViewModel.getCurrentHostId().observe(this, this::updateBottomNavHost);
        long currentHostId = prefs.getLong("current_host_id", -1L);
        if (currentHostId > 0) {
            navViewModel.setCurrentHostId(currentHostId);
        }

        // Handle intent
        handleIntent(getIntent());

        // 启动后自动连接首页主机
        maybeAutoConnectHomeHost(savedInstanceState);

        updateToolbarForPage(0);
    }

    @Override
    protected void onResume() {
        super.onResume();
        AppBackgroundHelper.applyFromPrefs(this, binding.getRoot());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleIntent(intent);
    }

    private void handleIntent(Intent intent) {
        if (intent != null && intent.hasExtra("open_tab")) {
            int tab = intent.getIntExtra("open_tab", 0);
            int target = Math.max(0, Math.min(3, tab));
            viewPager.setCurrentItem(target);
            if (target == 0) {
                bottomNavigationView.setSelectedItemId(R.id.nav_servers);
            } else if (target == 1) {
                bottomNavigationView.setSelectedItemId(R.id.nav_terminal);
            } else if (target == 2) {
                bottomNavigationView.setSelectedItemId(R.id.nav_files);
            } else {
                bottomNavigationView.setSelectedItemId(R.id.nav_profile);
            }
        }
        if (intent != null && intent.getData() != null) {
            Uri uri = intent.getData();
            if ("orcterm".equals(uri.getScheme())) {
                String host = uri.getHost();
                if ("import".equals(host)) {
                    String dataParam = uri.getQueryParameter("data");
                    if (dataParam != null) {
                        showPasswordDialog(dataParam);
                    }
                }
            }
        }
    }

    private void maybeAutoConnectHomeHost(Bundle savedInstanceState) {
        if (autoConnectHandled) return;
        autoConnectHandled = true;
        if (savedInstanceState != null) return;
        if (!prefs.getBoolean("home_host_auto_connect_enabled", false)) return;
        long homeHostId = prefs.getLong("home_host_id", -1L);
        if (homeHostId <= 0) return;
        executor.execute(() -> {
            HostEntity host = hostDao.findById(homeHostId);
            if (host == null) return;
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) return;
                navViewModel.setCurrentHostId(host.id);
                // Auto-connect should not force navigation to Terminal on app launch.
            });
        });
    }

    private void updateBottomNavHost(Long hostId) {
        Menu menu = bottomNavigationView.getMenu();
        MenuItem terminalItem = menu.findItem(R.id.nav_terminal);
        MenuItem filesItem = menu.findItem(R.id.nav_files);
        boolean enabled = hostId != null;
        terminalItem.setEnabled(enabled);
        filesItem.setEnabled(enabled);
        if (hostId == null) {
            terminalItem.setTitle(getString(R.string.nav_terminal_title));
            filesItem.setTitle(getString(R.string.nav_files_title));
            currentHostLabel = null;
            updateToolbarForPage(currentPage);
            return;
        }
        executor.execute(() -> {
            HostEntity host = hostDao.findById(hostId);
            runOnUiThread(() -> {
                if (host == null) {
                    terminalItem.setTitle(getString(R.string.nav_terminal_title));
                    filesItem.setTitle(getString(R.string.nav_files_title));
                    currentHostLabel = null;
                    updateToolbarForPage(currentPage);
                    prefs.edit()
                        .remove("current_host_id")
                        .remove("current_host_label")
                        .remove("current_host_hostname")
                        .remove("current_host_username")
                        .remove("current_host_port")
                        .apply();
                    navViewModel.setCurrentHostId(null);
                    return;
                }
                String label = buildHostLabel(host);
                terminalItem.setTitle(getString(R.string.nav_terminal_title));
                filesItem.setTitle(getString(R.string.nav_files_title));
                currentHostLabel = label;
                updateToolbarForPage(currentPage);
            });
        });
    }

    private String buildHostLabel(HostEntity host) {
        if (host == null) return "";
        if (!TextUtils.isEmpty(host.alias)) return host.alias;
        String hostname = host.hostname == null ? "" : host.hostname;
        String username = host.username == null ? "" : host.username;
        if (TextUtils.isEmpty(username)) return hostname;
        if (TextUtils.isEmpty(hostname)) return username;
        return username + "@" + hostname;
    }

    private void updateToolbarForPage(int position) {
        if (binding == null) return;
        String title;
        String subtitle = null;
        boolean showSubtitle = false;
        boolean showServerChip = false;

        switch (position) {
            case 0:
                title = getString(R.string.nav_servers_title);
                subtitle = getString(R.string.toolbar_subtitle_placeholder);
                showSubtitle = true;
                break;
            case 1:
                title = getString(R.string.nav_terminal_title);
                if (!TextUtils.isEmpty(currentHostLabel)) {
                    showServerChip = true;
                } else {
                    subtitle = getString(R.string.toolbar_subtitle_no_host);
                    showSubtitle = true;
                }
                break;
            case 2:
                title = getString(R.string.nav_files_title);
                if (!TextUtils.isEmpty(currentHostLabel)) {
                    showServerChip = true;
                } else {
                    subtitle = getString(R.string.toolbar_subtitle_no_host);
                    showSubtitle = true;
                }
                break;
            case 3:
            default:
                title = getString(R.string.nav_settings_title);
                showSubtitle = false;
                break;
        }

        binding.toolbarTitle.setText(title);
        binding.toolbarSubtitle.setVisibility(showSubtitle ? View.VISIBLE : View.GONE);
        if (showSubtitle && subtitle != null) {
            binding.toolbarSubtitle.setText(subtitle);
        }

        if (showServerChip && !TextUtils.isEmpty(currentHostLabel)) {
            binding.currentServerText.setText(currentHostLabel);
            binding.currentServerContainer.setVisibility(View.VISIBLE);
        } else {
            binding.currentServerContainer.setVisibility(View.GONE);
        }
    }

    public void startQrScan() {
        IntentIntegrator integrator = new IntentIntegrator(this);
        integrator.setOrientationLocked(false);
        integrator.setPrompt("Scan OrcTerm QR Code");
        integrator.initiateScan();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        IntentResult result = IntentIntegrator.parseActivityResult(requestCode, resultCode, data);
        if (result != null) {
            if (result.getContents() != null) {
                handleScanResult(result.getContents());
            }
        } else {
            super.onActivityResult(requestCode, resultCode, data);
        }
    }

    private void handleScanResult(String content) {
        // Clean and debug the scanned content
        if (content == null) {
            Toast.makeText(this, "扫描内容为空", Toast.LENGTH_SHORT).show();
            return;
        }
        
        content = content.trim();
        Log.d("QR_SCAN", "Scanned content: " + content);
        Log.d("QR_SCAN", "Content length: " + content.length());
        
        if (content.startsWith("orcterm://import")) {
            // Handle existing import logic
            String dataParam = null;
            try {
                Uri uri = Uri.parse(content);
                dataParam = uri.getQueryParameter("data");
            } catch (Exception e) {
                Toast.makeText(this, "Invalid URI format", Toast.LENGTH_SHORT).show();
                return;
            }

            if (dataParam == null) {
                Toast.makeText(this, "No data found in QR", Toast.LENGTH_SHORT).show();
                return;
            }

            // Fix: URL decoding might convert '+' to ' ', restoring it for Base64
            if (dataParam.contains(" ")) {
                dataParam = dataParam.replace(" ", "+");
            }

            showPasswordDialog(dataParam);

        } else if (content.trim().startsWith("{") || content.contains("orcterm_host")) {
            // Handle new JSON QR code format
            Log.d("QR_SCAN", "Handling JSON format");
            handleHostJson(content);
        } else if (content.startsWith("orcterm://bind/")) {
            // Handle Secure Binding Mode
            // ... existing secure bind logic
        } else {
            Log.d("QR_SCAN", "Unknown QR format");
            Toast.makeText(this, "Not an OrcTerm QR code", Toast.LENGTH_SHORT).show();
        }
    }

    private void handleHostJson(String jsonStr) {
        Log.d("QR_SCAN", "JSON string: " + jsonStr);
        try {
            // Parse JSON
            JSONObject json = new JSONObject(jsonStr);
            Log.d("QR_SCAN", "Parsed JSON: " + json.toString());

            // Validate format
            String type = json.optString("type");
            Log.d("QR_SCAN", "QR type: " + type);
            if (!"orcterm_host".equals(type)) {
                Log.e("QR_SCAN", "Invalid type: " + type);
                Toast.makeText(this, "无效的主机二维码格式", Toast.LENGTH_SHORT).show();
                return;
            }
            
            // Continue processing
            processHostJson(json);
        } catch (org.json.JSONException e) {
            Log.e("QR_SCAN", "JSON parsing error: " + e.getMessage());
            Toast.makeText(this, "二维码JSON解析失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void processHostJson(JSONObject json) throws org.json.JSONException {
        Log.d("QR_SCAN", "Processing host JSON data");
        
        // Extract host information
        String alias = json.optString("alias", "未知主机");
        String hostname = json.optString("hostname", "");
        String username = json.optString("username", "");
        int port = json.optInt("port", 22);
        int authType = json.optInt("auth_type", 0);
        String keyPath = json.optString("key_path", "");
        String osName = json.optString("os_name", "");
        String osVersion = json.optString("os_version", "");
        String containerEngine = json.optString("container_engine", "");
        String password = json.optString("password", "");
        boolean passwordRequired = json.optBoolean("password_required", true);

        Log.d("QR_SCAN", "Extracted hostname: " + hostname);
        Log.d("QR_SCAN", "Extracted username: " + username);
        Log.d("QR_SCAN", "Extracted password: " + (!password.isEmpty() ? "YES" : "NO"));

        // Validate required fields
        if (hostname.isEmpty() || username.isEmpty()) {
            Toast.makeText(this, "二维码信息不完整", Toast.LENGTH_SHORT).show();
            return;
        }

        // Create host entity
        HostEntity host = new HostEntity();
        host.alias = alias;
        host.hostname = hostname;
        host.username = username;
        host.port = port;
        host.authType = authType;
        host.keyPath = keyPath;
        host.osName = osName;
        host.osVersion = osVersion;
        host.containerEngine = containerEngine;
        host.status = "new";
        host.lastConnected = 0;

        // Handle password
        if (!password.isEmpty()) {
            // Password is included in QR code
            host.password = password;
            saveHostFromQR(host);
        } else if (passwordRequired) {
            // Password required but not included in QR code
            showPasswordInputDialog(host);
        } else {
            // No password required
            saveHostFromQR(host);
        }
    }

    private void showPasswordInputDialog(HostEntity host) {
        android.widget.LinearLayout layout = new android.widget.LinearLayout(this);
        layout.setOrientation(android.widget.LinearLayout.VERTICAL);
        layout.setPadding(32, 32, 32, 16);

        android.widget.TextView title = new android.widget.TextView(this);
        title.setText("输入密码以添加主机:\n" + host.alias + " (" + host.username + "@" + host.hostname + ")");
        title.setPadding(0, 0, 0, 16);
        layout.addView(title);

        android.widget.EditText passwordInput = new android.widget.EditText(this);
        passwordInput.setHint("密码");
        passwordInput.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
        layout.addView(passwordInput);

        new AlertDialog.Builder(this)
                .setTitle("添加主机")
                .setView(layout)
                .setPositiveButton("添加", (dialog, which) -> {
                    String password = passwordInput.getText().toString().trim();
                    host.password = password;
                    saveHostFromQR(host);
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void saveHostFromQR(HostEntity host) {
        executor.execute(() -> {
            try {
                // Check if host already exists
                androidx.lifecycle.LiveData<java.util.List<HostEntity>> liveData = hostDao.getAllHosts();
                java.util.List<HostEntity> existingHosts = liveData.getValue();
                if (existingHosts == null) existingHosts = new java.util.ArrayList<>();
                for (HostEntity existing : existingHosts) {
                    if (existing.hostname.equals(host.hostname) &&
                            existing.port == host.port &&
                            existing.username.equals(host.username)) {
                        runOnUiThread(() -> Toast.makeText(this, "该主机已存在", Toast.LENGTH_SHORT).show());
                        return;
                    }
                }

                // Save new host
                hostDao.insert(host);
                runOnUiThread(() -> {
                    Toast.makeText(this, "主机添加成功: " + host.alias, Toast.LENGTH_SHORT).show();
                    // Switch to servers page
                    if (bottomNavigationView != null) {
                        bottomNavigationView.setSelectedItemId(R.id.nav_servers);
                    }
                });

            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(this, "保存失败: " + e.getMessage(), Toast.LENGTH_SHORT).show());
            }
        });
    }

    private void showPasswordDialog(String data) {
        // ... existing password dialog logic for import
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_profile) {
            viewPager.setCurrentItem(3);
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private static class ViewPagerAdapter extends FragmentStateAdapter {
        public ViewPagerAdapter(MainActivity activity) {
            super(activity);
        }

        @NonNull
        @Override
        public Fragment createFragment(int position) {
            switch (position) {
                case 0:
                    return new ServersFragment();
                case 1:
                    return new TerminalFragment();
                case 2:
                    return new FilesFragment();
                case 3:
                default:
                    return new SettingsFragment();
            }
        }

        @Override
        public int getItemCount() {
            return 4;
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        executor.shutdownNow();
    }
}
