package com.orcterm.ui;

import android.app.AlertDialog;
import android.app.NotificationManager;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.OpenableColumns;
import android.provider.Settings;
import android.text.InputType;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.appcompat.widget.Toolbar;
import androidx.core.os.LocaleListCompat;

import com.github.mikephil.charting.charts.PieChart;
import com.github.mikephil.charting.data.PieData;
import com.github.mikephil.charting.data.PieDataSet;
import com.github.mikephil.charting.data.PieEntry;
import com.google.zxing.integration.android.IntentIntegrator;
import com.google.zxing.integration.android.IntentResult;
import com.orcterm.R;
import com.orcterm.data.AppDatabase;
import com.orcterm.data.HostDao;
import com.orcterm.data.HostEntity;
import com.orcterm.util.PersistentNotificationHelper;
import com.orcterm.util.SessionPersistenceManager;
import com.orcterm.util.BackupRestoreManager;
import com.orcterm.util.SessionLogManager;

import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONTokener;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

public class SettingsDetailActivity extends AppCompatActivity {
    private static final String DEFAULT_MONITOR_CARD_ORDER = "load,cpu,memory,network,disk,process";
    private static final String[] MONITOR_CARD_KEYS = {"load", "cpu", "memory", "network", "disk", "process"};

    private SharedPreferences prefs;
    private LinearLayout contentContainer;
    private LayoutInflater inflater;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private ActivityResultLauncher<String> keyPickerLauncher;
    private ActivityResultLauncher<String> bgPickerLauncher;
    private ActivityResultLauncher<Intent> backupCreateLauncher;
    private ActivityResultLauncher<Intent> backupRestoreLauncher;
    private ActivityResultLauncher<Intent> csvExportLauncher;
    private ActivityResultLauncher<Intent> csvImportLauncher;
    private TextView cloudServerValue;
    private TextView cloudLastSyncValue;
    private TextView cloudItemsCountValue;
    private LinearLayout cloudItemsContainer;
    private TextView cloudItemsEmpty;
    private View hostPreview;
    private Switch persistentNotificationSwitch;
    private TextView persistentNotificationSummary;
    private SharedPreferences.OnSharedPreferenceChangeListener prefChangeListener;
    private final Map<String, Object> prefSnapshot = new HashMap<>();
    private String focusKey;
    private static class SettingEntry {
        final String key;
        final String title;
        final String summary;
        final String page;
        final String focus;
        final int iconRes;
        SettingEntry(String key, String title, String summary, String page, String focus, int iconRes) {
            this.key = key;
            this.title = title;
            this.summary = summary;
            this.page = page;
            this.focus = focus;
            this.iconRes = iconRes;
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings_detail);
        prefs = getSharedPreferences("orcterm_prefs", Context.MODE_PRIVATE);
        inflater = LayoutInflater.from(this);
        contentContainer = findViewById(R.id.content_container);
        setupToolbar();
        setupLaunchers();
        String page = getIntent().getStringExtra("page");
        focusKey = getIntent().getStringExtra("focus_key");
        if (page == null) page = "general";
        buildPage(page);
    }

    @Override
    protected void onResume() {
        super.onResume();
        ensurePrefListener();
        updateCloudStatus();
        updatePersistentNotificationState();
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (prefChangeListener != null) {
            prefs.unregisterOnSharedPreferenceChangeListener(prefChangeListener);
        }
    }

    private void ensurePrefListener() {
        if (prefChangeListener != null) {
            prefs.unregisterOnSharedPreferenceChangeListener(prefChangeListener);
        }
        prefSnapshot.clear();
        for (String key : getTrackedKeys()) {
            prefSnapshot.put(key, readPrefValue(key));
        }
        prefChangeListener = (sharedPreferences, key) -> {
            if (!getTrackedKeys().contains(key)) return;
            Object oldValue = prefSnapshot.get(key);
            Object newValue = readPrefValue(key);
            if (oldValue == null && newValue == null) return;
            if (oldValue != null && oldValue.equals(newValue)) return;
            appendSettingChangeLog(key, oldValue, newValue);
            prefSnapshot.put(key, newValue);
        };
        prefs.registerOnSharedPreferenceChangeListener(prefChangeListener);
    }

    private List<String> getTrackedKeys() {
        ArrayList<String> keys = new ArrayList<>();
        keys.add("theme_mode");
        keys.add("app_bg_uri");
        keys.add("list_density");
        keys.add("host_display_style");
        keys.add("font_size_index");
        keys.add("terminal_theme_index");
        keys.add("terminal_font_family");
        keys.add("terminal_keypad_mapping");
        keys.add("terminal_scrollback_lines");
        keys.add("keyboard_height_option");
        keys.add("keyboard_layout_option");
        keys.add("terminal_enter_newline");
        keys.add("terminal_local_echo");
        keys.add("terminal_auto_scroll_output");
        keys.add("terminal_smooth_scroll");
        keys.add("terminal_copy_on_select");
        keys.add("terminal_paste_on_tap");
        keys.add("terminal_bell_audio");
        keys.add("terminal_bell_visual");
        keys.add("monitor_refresh_interval_sec");
        keys.add("monitor_process_limit");
        keys.add("monitor_show_total_traffic");
        keys.add("monitor_traffic_scope");
        keys.add("monitor_card_order");
        keys.add("file_download_path");
        keys.add("file_sort_order");
        keys.add("file_show_hidden");
        keys.add("file_icon_size");
        keys.add("file_unzip_path");
        keys.add("file_editor_font_size");
        keys.add("ssh_connect_timeout_sec");
        keys.add("ssh_read_timeout_sec");
        keys.add("ssh_keepalive_interval_sec");
        keys.add("ssh_keepalive_reply");
        keys.add("sftp_default_path");
        keys.add("home_host_auto_connect_enabled");
        keys.add("home_host_list_auto_fetch_enabled");
        keys.add("app_language");
        keys.add("persistent_notification_enabled");
        return keys;
    }

    private Object readPrefValue(String key) {
        if (key == null) return null;
        if ("theme_mode".equals(key)) return prefs.getInt(key, AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
        if ("list_density".equals(key)) return prefs.getInt(key, 1);
        if ("host_display_style".equals(key)) return prefs.getInt(key, 0);
        if ("font_size_index".equals(key)) return prefs.getInt(key, 1);
        if ("terminal_theme_index".equals(key)) return prefs.getInt(key, 0);
        if ("terminal_font_family".equals(key)) return prefs.getInt(key, 0);
        if ("keyboard_height_option".equals(key)) return prefs.getInt(key, 1);
        if ("keyboard_layout_option".equals(key)) return prefs.getInt(key, 0);
        if ("file_sort_order".equals(key)) return prefs.getInt(key, 0);
        if ("file_icon_size".equals(key)) return prefs.getInt(key, 0);
        if ("file_editor_font_size".equals(key)) return prefs.getInt(key, 1);
        if ("ssh_connect_timeout_sec".equals(key)) return prefs.getInt(key, 10);
        if ("ssh_read_timeout_sec".equals(key)) return prefs.getInt(key, 60);
        if ("ssh_keepalive_interval_sec".equals(key)) return prefs.getInt(key, 0);
        if ("monitor_refresh_interval_sec".equals(key)) return prefs.getInt(key, 3);
        if ("monitor_process_limit".equals(key)) return prefs.getInt(key, 80);
        if ("terminal_enter_newline".equals(key)) return prefs.getBoolean(key, true);
        if ("terminal_local_echo".equals(key)) return prefs.getBoolean(key, false);
        if ("terminal_auto_scroll_output".equals(key)) return prefs.getBoolean(key, true);
        if ("terminal_smooth_scroll".equals(key)) return prefs.getBoolean(key, true);
        if ("terminal_copy_on_select".equals(key)) return prefs.getBoolean(key, true);
        if ("terminal_paste_on_tap".equals(key)) return prefs.getBoolean(key, false);
        if ("terminal_bell_audio".equals(key)) return prefs.getBoolean(key, false);
        if ("terminal_bell_visual".equals(key)) return prefs.getBoolean(key, true);
        if ("file_show_hidden".equals(key)) return prefs.getBoolean(key, false);
        if ("ssh_keepalive_reply".equals(key)) return prefs.getBoolean(key, true);
        if ("monitor_show_total_traffic".equals(key)) return prefs.getBoolean(key, true);
        if ("persistent_notification_enabled".equals(key)) return prefs.getBoolean(key, false);
        if ("home_host_auto_connect_enabled".equals(key)) return prefs.getBoolean(key, false);
        if ("home_host_list_auto_fetch_enabled".equals(key)) return prefs.getBoolean(key, false);
        if ("app_bg_uri".equals(key)) return prefs.getString(key, "");
        if ("file_download_path".equals(key)) return prefs.getString(key, "");
        if ("file_unzip_path".equals(key)) return prefs.getString(key, "");
        if ("sftp_default_path".equals(key)) return prefs.getString(key, "");
        if ("app_language".equals(key)) return prefs.getString(key, "system");
        if ("monitor_traffic_scope".equals(key)) return prefs.getString(key, "session");
        if ("monitor_card_order".equals(key)) return prefs.getString(key, "load,cpu,memory,network,disk,process");
        if ("terminal_keypad_mapping".equals(key)) return prefs.getString(key, "");
        if ("terminal_scrollback_lines".equals(key)) return prefs.getInt(key, 2000);
        return prefs.getString(key, "");
    }

    private void appendSettingChangeLog(String key, Object oldValue, Object newValue) {
        try {
            String raw = prefs.getString("settings_change_logs", "[]");
            JSONArray array = new JSONArray(raw == null ? "[]" : raw);
            JSONObject entry = new JSONObject();
            entry.put("time", System.currentTimeMillis());
            entry.put("key", key);
            entry.put("label", resolveSettingLabel(key));
            entry.put("old", oldValue == null ? "" : String.valueOf(oldValue));
            entry.put("new", newValue == null ? "" : String.valueOf(newValue));
            array.put(entry);
            prefs.edit().putString("settings_change_logs", array.toString()).apply();
        } catch (Exception ignored) {}
    }

    private String resolveSettingLabel(String key) {
        if ("theme_mode".equals(key)) return getString(R.string.settings_theme_mode_title);
        if ("app_bg_uri".equals(key)) return getString(R.string.settings_theme_bg_title);
        if ("list_density".equals(key)) return getString(R.string.settings_theme_density_title);
        if ("host_display_style".equals(key)) return getString(R.string.settings_theme_list_style_title);
        if ("font_size_index".equals(key)) return getString(R.string.settings_terminal_font_title);
        if ("terminal_theme_index".equals(key)) return getString(R.string.settings_terminal_theme_title);
        if ("terminal_font_family".equals(key)) return getString(R.string.settings_terminal_font_family_title);
        if ("terminal_keypad_mapping".equals(key)) return getString(R.string.settings_terminal_keypad_title);
        if ("terminal_scrollback_lines".equals(key)) return getString(R.string.settings_terminal_scrollback_title);
        if ("keyboard_height_option".equals(key)) return getString(R.string.settings_terminal_keyboard_height_title);
        if ("keyboard_layout_option".equals(key)) return getString(R.string.settings_terminal_keyboard_layout_title);
        if ("terminal_enter_newline".equals(key)) return getString(R.string.settings_terminal_behavior_enter_title);
        if ("terminal_local_echo".equals(key)) return getString(R.string.settings_terminal_behavior_echo_title);
        if ("terminal_auto_scroll_output".equals(key)) return getString(R.string.settings_terminal_behavior_autoscroll_title);
        if ("terminal_smooth_scroll".equals(key)) return getString(R.string.settings_terminal_behavior_smooth_title);
        if ("terminal_copy_on_select".equals(key)) return getString(R.string.settings_terminal_behavior_copy_title);
        if ("terminal_paste_on_tap".equals(key)) return getString(R.string.settings_terminal_behavior_paste_title);
        if ("terminal_bell_audio".equals(key)) return getString(R.string.settings_terminal_behavior_bell_audio_title);
        if ("terminal_bell_visual".equals(key)) return getString(R.string.settings_terminal_behavior_bell_visual_title);
        if ("monitor_refresh_interval_sec".equals(key)) return getString(R.string.settings_monitor_refresh_interval_title);
        if ("monitor_process_limit".equals(key)) return getString(R.string.settings_monitor_process_limit_title);
        if ("monitor_show_total_traffic".equals(key)) return getString(R.string.settings_monitor_show_total_traffic_title);
        if ("monitor_traffic_scope".equals(key)) return getString(R.string.settings_monitor_traffic_scope_title);
        if ("monitor_card_order".equals(key)) return getString(R.string.settings_monitor_card_order_title);
        if ("file_download_path".equals(key)) return getString(R.string.settings_file_download_path_title);
        if ("file_sort_order".equals(key)) return getString(R.string.settings_file_sort_order_title);
        if ("file_show_hidden".equals(key)) return getString(R.string.settings_file_show_hidden_title);
        if ("file_icon_size".equals(key)) return getString(R.string.settings_file_icon_size_title);
        if ("file_unzip_path".equals(key)) return getString(R.string.settings_file_unzip_path_title);
        if ("file_editor_font_size".equals(key)) return getString(R.string.settings_file_editor_font_title);
        if ("ssh_connect_timeout_sec".equals(key)) return getString(R.string.settings_ssh_connect_timeout_title);
        if ("ssh_read_timeout_sec".equals(key)) return getString(R.string.settings_ssh_read_timeout_title);
        if ("ssh_keepalive_interval_sec".equals(key)) return getString(R.string.settings_ssh_keepalive_interval_title);
        if ("ssh_keepalive_reply".equals(key)) return getString(R.string.settings_ssh_keepalive_reply_title);
        if ("sftp_default_path".equals(key)) return getString(R.string.settings_ssh_sftp_default_path_title);
        if ("home_host_auto_connect_enabled".equals(key)) return getString(R.string.settings_ssh_home_auto_connect_title);
        if ("home_host_list_auto_fetch_enabled".equals(key)) return getString(R.string.settings_ssh_home_list_auto_fetch_title);
        if ("app_language".equals(key)) return getString(R.string.settings_language_title);
        if ("persistent_notification_enabled".equals(key)) return getString(R.string.settings_notification_persistent_title);
        return key == null ? "" : key;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        executor.shutdownNow();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            navigateBack();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onBackPressed() {
        navigateBack();
    }

    private void navigateBack() {
        if (!isTaskRoot()) {
            finish();
            return;
        }
        Intent intent = new Intent(this, MainActivity.class);
        intent.putExtra("open_tab", 3);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(intent);
        finish();
    }

    private void setupToolbar() {
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
    }

    private void setupLaunchers() {
        keyPickerLauncher = registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
            if (uri != null) importKeyFromUri(uri);
        });
        bgPickerLauncher = registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
            if (uri != null) {
                prefs.edit().putString("app_bg_uri", uri.toString()).apply();
                applyBackgroundFromUri(uri);
                Toast.makeText(this, getString(R.string.background_set), Toast.LENGTH_SHORT).show();
            }
        });
        
        // 备份创建 Launcher
        backupCreateLauncher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
            if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                Uri uri = result.getData().getData();
                if (uri != null) {
                    BackupRestoreManager manager = new BackupRestoreManager(this);
                    manager.createBackup(uri, new BackupRestoreManager.BackupRestoreCallback() {
                        @Override
                        public void onProgress(int current, int total, String message) {
                            // 可以在这里显示进度
                        }

                        @Override
                        public void onSuccess(String message) {
                            mainHandler.post(() -> showToastMessage(message, Toast.LENGTH_SHORT));
                        }

                        @Override
                        public void onError(String error) {
                            mainHandler.post(() -> showToastMessage(error, Toast.LENGTH_LONG));
                        }
                    });
                }
            }
        });
        
        // 备份恢复 Launcher
        backupRestoreLauncher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
            if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                Uri uri = result.getData().getData();
                if (uri != null) {
                    new AlertDialog.Builder(this)
                        .setTitle(getString(R.string.settings_backup_restore_confirm_title))
                        .setMessage(getString(R.string.settings_backup_restore_confirm_message))
                        .setPositiveButton(getString(R.string.action_restore), (dialog, which) -> {
                            BackupRestoreManager manager = new BackupRestoreManager(this);
                            manager.restoreBackup(uri, new BackupRestoreManager.BackupRestoreCallback() {
                                @Override
                                public void onProgress(int current, int total, String message) {
                                    // 可以在这里显示进度
                                }

                                @Override
                                public void onSuccess(String message) {
                                    mainHandler.post(() -> {
                                        showToastMessage(message, Toast.LENGTH_SHORT);
                                        // 恢复完成后刷新页面
                                        recreate();
                                    });
                                }

                                @Override
                                public void onError(String error) {
                                    mainHandler.post(() -> showToastMessage(error, Toast.LENGTH_LONG));
                                }
                            });
                        })
                        .setNegativeButton(getString(R.string.action_cancel), null)
                        .show();
                }
            }
        });
        
        // CSV 导出 Launcher
        csvExportLauncher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
            if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                Uri uri = result.getData().getData();
                if (uri != null) {
                    BackupRestoreManager manager = new BackupRestoreManager(this);
                    manager.exportHostsAsCsv(uri, new BackupRestoreManager.BackupRestoreCallback() {
                        @Override
                        public void onProgress(int current, int total, String message) {}

                        @Override
                        public void onSuccess(String message) {
                            mainHandler.post(() -> showToastMessage(message, Toast.LENGTH_SHORT));
                        }

                        @Override
                        public void onError(String error) {
                            mainHandler.post(() -> showToastMessage(error, Toast.LENGTH_LONG));
                        }
                    });
                }
            }
        });
        
        // CSV 导入 Launcher
        csvImportLauncher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
            if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                Uri uri = result.getData().getData();
                if (uri != null) {
                    new AlertDialog.Builder(this)
                        .setTitle(getString(R.string.settings_backup_import_confirm_title))
                        .setMessage(getString(R.string.settings_backup_import_confirm_message))
                        .setPositiveButton(getString(R.string.action_import), (dialog, which) -> {
                            BackupRestoreManager manager = new BackupRestoreManager(this);
                            manager.importHostsFromCsv(uri, new BackupRestoreManager.BackupRestoreCallback() {
                                @Override
                                public void onProgress(int current, int total, String message) {}

                                @Override
                                public void onSuccess(String message) {
                                    mainHandler.post(() -> {
                                        showToastMessage(message, Toast.LENGTH_SHORT);
                                        // 导入完成后刷新页面
                                        recreate();
                                    });
                                }

                                @Override
                                public void onError(String error) {
                                    mainHandler.post(() -> showToastMessage(error, Toast.LENGTH_LONG));
                                }
                            });
                        })
                        .setNegativeButton(getString(R.string.action_cancel), null)
                        .show();
                }
            }
        });
    }

    private void buildPage(String page) {
        if ("search".equals(page)) {
            setTitle(getString(R.string.settings_search_title));
            buildSearchPage(getIntent().getStringExtra("query"));
        } else if ("ssh".equals(page)) {
            setTitle(getString(R.string.settings_ssh_manage_title));
            buildSshPage();
        } else if ("cloud_sync".equals(page)) {
            setTitle(getString(R.string.settings_cloud_sync_title));
            buildCloudSyncPage();
        } else if ("terminal".equals(page)) {
            setTitle(getString(R.string.settings_terminal_title));
            buildTerminalPage();
        } else if ("monitor".equals(page)) {
            setTitle(getString(R.string.settings_monitor_title));
            buildMonitorPage();
        } else if ("theme_display".equals(page)) {
            setTitle(getString(R.string.settings_theme_display_title));
            buildThemeDisplayPage();
        } else if ("file_management".equals(page)) {
            setTitle(getString(R.string.settings_file_management_title));
            buildFileManagementPage();
        } else if ("session_policy".equals(page)) {
            setTitle(getString(R.string.settings_session_policy_title));
            buildSessionPolicyPage();
        } else if ("backup".equals(page)) {
            setTitle(getString(R.string.settings_backup_title));
            buildBackupPage();
        } else if ("settings_log".equals(page)) {
            setTitle(getString(R.string.settings_change_log_title));
            buildSettingsChangeLogPage();
        } else if ("ssh_logs".equals(page)) {
            setTitle(getString(R.string.settings_ssh_logs_title));
            buildSshLogsPage();
        } else {
            setTitle(getString(R.string.settings_general_title));
            buildGeneralPage();
        }
        applyFocusHighlight();
    }

    // 统一处理空提示，避免显示 null
    private void showToastMessage(String message, int duration) {
        if (TextUtils.isEmpty(message)) {
            return;
        }
        Toast.makeText(this, message, duration).show();
    }

    private void buildSshLogsPage() {
        SessionLogManager logManager = new SessionLogManager(this);
        
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        contentContainer.addView(list);
        
        TextView loading = new TextView(this);
        loading.setText(getString(R.string.settings_ssh_logs_loading));
        loading.setPadding(32, 32, 32, 32);
        list.addView(loading);
        
        logManager.getAllLogsWithError(logs -> {
            runOnUiThread(() -> {
                list.removeView(loading);
                if (logs == null || logs.isEmpty()) {
                    TextView empty = new TextView(this);
                    empty.setText(getString(R.string.settings_ssh_logs_empty));
                    empty.setPadding(32, 32, 32, 32);
                    list.addView(empty);
                    return;
                }
                
                for (SessionLogManager.SessionLog log : logs) {
                    View itemView = inflater.inflate(R.layout.item_setting, list, false);
                    ImageView icon = itemView.findViewById(R.id.icon);
                    TextView title = itemView.findViewById(R.id.title);
                    TextView summary = itemView.findViewById(R.id.summary);
                    
                    icon.setImageResource(R.drawable.ic_action_view_list);
                    title.setText(log.hostInfo);
                    
                    String dateStr = DateFormat.getDateTimeInstance().format(new Date(log.startTime));
                    String sizeStr = android.text.format.Formatter.formatFileSize(this, log.size);
                    summary.setText(dateStr + " • " + sizeStr);
                    summary.setVisibility(View.VISIBLE);
                    
                    itemView.setOnClickListener(v -> showLogContent(log));
                    
                    list.addView(itemView);
                }
            });
        });
    }

    private void showLogContent(SessionLogManager.SessionLog log) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(getString(R.string.settings_ssh_log_title, log.hostInfo));
        
        final TextView content = new TextView(this);
        content.setPadding(32, 32, 32, 32);
        content.setText(getString(R.string.settings_ssh_logs_loading));
        content.setTextIsSelectable(true);
        content.setTypeface(android.graphics.Typeface.MONOSPACE);
        
        android.widget.ScrollView scrollView = new android.widget.ScrollView(this);
        scrollView.addView(content);
        builder.setView(scrollView);
        
        builder.setPositiveButton(getString(R.string.action_close), null);
        builder.setNeutralButton(getString(R.string.action_delete), (d, w) -> {
            new SessionLogManager(this).deleteLog(log.logFilePath, () -> {
                 runOnUiThread(() -> {
                     Toast.makeText(this, getString(R.string.settings_ssh_log_deleted), Toast.LENGTH_SHORT).show();
                     contentContainer.removeAllViews();
                     buildSshLogsPage(); // Refresh
                 });
            });
        });
        
        AlertDialog dialog = builder.show();
        
        new SessionLogManager(this).readLogContent(log.logFilePath, 1000, lines -> {
            runOnUiThread(() -> {
                StringBuilder sb = new StringBuilder();
                for (String line : lines) {
                    sb.append(line).append("\n");
                }
                content.setText(sb.toString());
            });
        });
    }

    private void buildSshPage() {
        addHeader(getString(R.string.settings_ssh_quick_header));
        addItem(R.drawable.ic_action_computer, getString(R.string.settings_ssh_quick_terminal_title), getString(R.string.settings_ssh_quick_terminal_summary), "ssh_quick_terminal", v -> showSshQuickPicker(false));
        addItem(R.drawable.ic_action_storage, getString(R.string.settings_ssh_quick_sftp_title), getString(R.string.settings_ssh_quick_sftp_summary), "ssh_quick_sftp", v -> showSshQuickPicker(true));
        addDivider();
        addItem(R.drawable.ic_action_computer, getString(R.string.settings_ssh_hosts_title), getString(R.string.settings_ssh_hosts_summary), "ssh_hosts", v -> openServersTab());
        addDivider();
        addItem(R.drawable.ic_action_vpn_key, getString(R.string.settings_ssh_keys_title), getString(R.string.settings_ssh_keys_summary), "ssh_keys", v -> showSshKeyManager());
        addDivider();
        addHeader(getString(R.string.settings_ssh_logs_header));
        addItem(R.drawable.ic_action_view_list, getString(R.string.settings_ssh_logs_title), getString(R.string.settings_ssh_logs_summary), "ssh_logs", v -> {
            contentContainer.removeAllViews();
            setTitle(getString(R.string.settings_ssh_logs_title));
            buildSshLogsPage();
        });
        addDivider();
        addHeader(getString(R.string.settings_ssh_home_header));
        View autoConnectItem = addItem(R.drawable.ic_action_refresh, getString(R.string.settings_ssh_home_auto_connect_title), "", "home_host_auto_connect", null);
        Switch autoConnectSwitch = autoConnectItem.findViewById(R.id.switch_widget);
        autoConnectSwitch.setVisibility(View.VISIBLE);
        autoConnectItem.findViewById(R.id.chevron).setVisibility(View.GONE);
        boolean autoConnectEnabled = prefs.getBoolean("home_host_auto_connect_enabled", false);
        autoConnectSwitch.setChecked(autoConnectEnabled);
        updateHomeAutoConnectSummary(autoConnectItem);
        autoConnectSwitch.setOnCheckedChangeListener((btn, isChecked) -> {
            prefs.edit().putBoolean("home_host_auto_connect_enabled", isChecked).apply();
            updateHomeAutoConnectSummary(autoConnectItem);
        });
        autoConnectItem.setOnClickListener(v -> autoConnectSwitch.toggle());
        View autoFetchItem = addItem(R.drawable.ic_action_view_list, getString(R.string.settings_ssh_home_list_auto_fetch_title), "", "home_host_list_auto_fetch", null);
        Switch autoFetchSwitch = autoFetchItem.findViewById(R.id.switch_widget);
        autoFetchSwitch.setVisibility(View.VISIBLE);
        autoFetchItem.findViewById(R.id.chevron).setVisibility(View.GONE);
        boolean autoFetchEnabled = prefs.getBoolean("home_host_list_auto_fetch_enabled", false);
        autoFetchSwitch.setChecked(autoFetchEnabled);
        updateHomeListAutoFetchSummary(autoFetchItem);
        autoFetchSwitch.setOnCheckedChangeListener((btn, isChecked) -> {
            prefs.edit().putBoolean("home_host_list_auto_fetch_enabled", isChecked).apply();
            updateHomeListAutoFetchSummary(autoFetchItem);
        });
        autoFetchItem.setOnClickListener(v -> autoFetchSwitch.toggle());
        addDivider();
        addHeader(getString(R.string.settings_ssh_config_header));
        View connectTimeout = addItem(R.drawable.ic_action_info, getString(R.string.settings_ssh_connect_timeout_title), "", "ssh_connect_timeout", v -> showSshConnectTimeoutDialog());
        updateSshTimeoutSummary(connectTimeout, "ssh_connect_timeout_sec", 10, R.string.settings_ssh_connect_timeout_summary);
        View readTimeout = addItem(R.drawable.ic_action_info, getString(R.string.settings_ssh_read_timeout_title), "", "ssh_read_timeout", v -> showSshReadTimeoutDialog());
        updateSshTimeoutSummary(readTimeout, "ssh_read_timeout_sec", 60, R.string.settings_ssh_read_timeout_summary);
        View keepaliveInterval = addItem(R.drawable.ic_action_info, getString(R.string.settings_ssh_keepalive_interval_title), "", "ssh_keepalive_interval", v -> showSshKeepaliveIntervalDialog());
        updateSshTimeoutSummary(keepaliveInterval, "ssh_keepalive_interval_sec", 0, R.string.settings_ssh_keepalive_interval_summary);
        View keepaliveReply = addItem(R.drawable.ic_action_info, getString(R.string.settings_ssh_keepalive_reply_title), "", "ssh_keepalive_reply", null);
        Switch keepaliveReplySwitch = keepaliveReply.findViewById(R.id.switch_widget);
        keepaliveReplySwitch.setVisibility(View.VISIBLE);
        keepaliveReply.findViewById(R.id.chevron).setVisibility(View.GONE);
        boolean replyEnabled = prefs.getBoolean("ssh_keepalive_reply", true);
        keepaliveReplySwitch.setChecked(replyEnabled);
        updateSshKeepaliveReplySummary(keepaliveReply);
        keepaliveReplySwitch.setOnCheckedChangeListener((btn, isChecked) -> {
            prefs.edit().putBoolean("ssh_keepalive_reply", isChecked).apply();
            updateSshKeepaliveReplySummary(keepaliveReply);
        });
        keepaliveReply.setOnClickListener(v -> keepaliveReplySwitch.toggle());
        View sftpDefaultPath = addItem(R.drawable.ic_action_storage, getString(R.string.settings_ssh_sftp_default_path_title), "", "sftp_default_path", v -> showSftpDefaultPathDialog());
        updateSftpDefaultPathSummary(sftpDefaultPath);
    }

    private void buildCloudSyncPage() {
        addHeader(getString(R.string.settings_cloud_status_header));
        cloudServerValue = addInfoRow(getString(R.string.settings_cloud_server_label), "");
        cloudLastSyncValue = addInfoRow(getString(R.string.settings_cloud_last_sync_label), "");
        cloudItemsCountValue = addInfoRow(getString(R.string.settings_cloud_items_label), "");
        addDivider();
        addHeader(getString(R.string.settings_cloud_actions_header));
        addItem(R.drawable.ic_action_link, getString(R.string.settings_cloud_bind_title), getString(R.string.settings_cloud_bind_summary), v -> startQrScan());
        addItem(R.drawable.ic_action_refresh, getString(R.string.settings_cloud_sync_title), getString(R.string.settings_cloud_sync_summary), v -> runCloudSync());
        addDivider();
        addHeader(getString(R.string.settings_cloud_items_header));
        cloudItemsContainer = new LinearLayout(this);
        cloudItemsContainer.setOrientation(LinearLayout.VERTICAL);
        contentContainer.addView(cloudItemsContainer);
        cloudItemsEmpty = new TextView(this);
        cloudItemsEmpty.setText(getString(R.string.settings_cloud_items_empty));
        cloudItemsEmpty.setPadding(16, 16, 16, 16);
        contentContainer.addView(cloudItemsEmpty);
        updateCloudStatus();
        refreshCloudItems();
    }

    private void buildTerminalPage() {
        String title = getString(R.string.settings_terminal_font_title) + " / " + getString(R.string.settings_terminal_theme_title);
        String summary = getString(R.string.settings_terminal_font_summary) + " · " + getString(R.string.settings_terminal_theme_summary);
        addItem(R.drawable.ic_action_appearance, title, summary, "terminal_font_theme", v -> showFontAndThemeDialog());
        addHeader(getString(R.string.settings_terminal_theme_manage_header));
        addItem(R.drawable.ic_action_appearance, getString(R.string.settings_terminal_theme_editor_title), getString(R.string.settings_terminal_theme_editor_summary), "terminal_theme_editor", v -> openTerminalThemeAction("editor"));
        addItem(R.drawable.ic_action_import_export, getString(R.string.settings_terminal_theme_import_title), getString(R.string.settings_terminal_theme_import_summary), "terminal_theme_import", v -> openTerminalThemeAction("import"));
        addItem(R.drawable.ic_action_import_export, getString(R.string.settings_terminal_theme_export_title), getString(R.string.settings_terminal_theme_export_summary), "terminal_theme_export", v -> openTerminalThemeAction("export"));
        addItem(R.drawable.ic_action_keyboard, getString(R.string.settings_terminal_keypad_title), getString(R.string.settings_terminal_keypad_summary), "terminal_keypad", v -> showKeypadMappingDialog());
        addItem(R.drawable.ic_action_format_size, getString(R.string.settings_terminal_keyboard_height_title), getString(R.string.settings_terminal_keyboard_height_summary), "terminal_keyboard_height", v -> showKeyboardHeightDialog());
        addItem(R.drawable.ic_action_grid, getString(R.string.settings_terminal_keyboard_layout_title), getString(R.string.settings_terminal_keyboard_layout_summary), "terminal_keyboard_layout", v -> showKeyboardLayoutDialog());
        View scrollbackItem = addItem(R.drawable.ic_action_view_list, getString(R.string.settings_terminal_scrollback_title), "", "terminal_scrollback", v -> showScrollbackDialog());
        updateScrollbackSummary(scrollbackItem);
        addItem(R.drawable.ic_action_refresh, getString(R.string.settings_terminal_keyboard_reset_title), getString(R.string.settings_terminal_keyboard_reset_summary), "terminal_keyboard_reset", v -> resetKeyboardLayout());
        addItem(R.drawable.ic_action_settings, getString(R.string.settings_terminal_behavior_title), getString(R.string.settings_terminal_behavior_summary), "terminal_behavior", v -> showTerminalBehaviorDialog());
        addDivider();
        addHeader(getString(R.string.settings_preview_header));
        contentContainer.addView(createTerminalPreview());
    }

    private void buildMonitorPage() {
        View refreshItem = addItem(
                R.drawable.ic_action_refresh,
                getString(R.string.settings_monitor_refresh_interval_title),
                "",
                "monitor_refresh_interval",
                v -> showMonitorRefreshIntervalDialog());
        updateMonitorRefreshIntervalSummary(refreshItem);

        View processLimitItem = addItem(
                R.drawable.ic_action_view_list,
                getString(R.string.settings_monitor_process_limit_title),
                "",
                "monitor_process_limit",
                v -> showMonitorProcessLimitDialog());
        updateMonitorProcessLimitSummary(processLimitItem);

        View totalTrafficItem = addItem(
                R.drawable.ic_dashboard_network,
                getString(R.string.settings_monitor_show_total_traffic_title),
                "",
                "monitor_show_total_traffic",
                null);
        Switch totalTrafficSwitch = totalTrafficItem.findViewById(R.id.switch_widget);
        totalTrafficSwitch.setVisibility(View.VISIBLE);
        totalTrafficItem.findViewById(R.id.chevron).setVisibility(View.GONE);
        totalTrafficSwitch.setChecked(prefs.getBoolean("monitor_show_total_traffic", true));
        updateMonitorShowTotalTrafficSummary(totalTrafficItem);
        totalTrafficSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            prefs.edit().putBoolean("monitor_show_total_traffic", isChecked).apply();
            updateMonitorShowTotalTrafficSummary(totalTrafficItem);
        });
        totalTrafficItem.setOnClickListener(v -> totalTrafficSwitch.toggle());

        View scopeItem = addItem(
                R.drawable.ic_dashboard_network,
                getString(R.string.settings_monitor_traffic_scope_title),
                "",
                "monitor_traffic_scope",
                v -> showMonitorTrafficScopeDialog());
        updateMonitorTrafficScopeSummary(scopeItem);

        View orderItem = addItem(
                R.drawable.ic_action_sort,
                getString(R.string.settings_monitor_card_order_title),
                "",
                "monitor_card_order",
                v -> showMonitorCardOrderDialog());
        updateMonitorCardOrderSummary(orderItem);
    }

    private void updateMonitorRefreshIntervalSummary(View item) {
        TextView summary = item.findViewById(R.id.summary);
        int sec = prefs.getInt("monitor_refresh_interval_sec", 3);
        summary.setText(getString(R.string.settings_monitor_refresh_interval_summary, sec));
        summary.setVisibility(View.VISIBLE);
    }

    private void updateMonitorProcessLimitSummary(View item) {
        TextView summary = item.findViewById(R.id.summary);
        int limit = prefs.getInt("monitor_process_limit", 80);
        summary.setText(getString(R.string.settings_monitor_process_limit_summary, limit));
        summary.setVisibility(View.VISIBLE);
    }

    private void updateMonitorShowTotalTrafficSummary(View item) {
        TextView summary = item.findViewById(R.id.summary);
        boolean show = prefs.getBoolean("monitor_show_total_traffic", true);
        summary.setText(getString(show
                ? R.string.settings_monitor_show_total_traffic_on
                : R.string.settings_monitor_show_total_traffic_off));
        summary.setVisibility(View.VISIBLE);
    }

    private void updateMonitorTrafficScopeSummary(View item) {
        TextView summary = item.findViewById(R.id.summary);
        String scope = prefs.getString("monitor_traffic_scope", "session");
        String label = "boot".equals(scope)
                ? getString(R.string.settings_monitor_scope_boot)
                : getString(R.string.settings_monitor_scope_session);
        summary.setText(label);
        summary.setVisibility(View.VISIBLE);
    }

    private void updateMonitorCardOrderSummary(View item) {
        TextView summary = item.findViewById(R.id.summary);
        String order = sanitizeMonitorCardOrder(prefs.getString("monitor_card_order", DEFAULT_MONITOR_CARD_ORDER));
        String[] keys = order.split(",");
        ArrayList<String> labels = new ArrayList<>();
        for (String key : keys) {
            labels.add(resolveMonitorCardLabel(key));
        }
        summary.setText(TextUtils.join(" > ", labels));
        summary.setVisibility(View.VISIBLE);
    }

    private void showMonitorRefreshIntervalDialog() {
        showSshNumberDialog(
                getString(R.string.settings_monitor_refresh_interval_title),
                "monitor_refresh_interval_sec",
                3,
                1,
                60);
    }

    private void showMonitorProcessLimitDialog() {
        showSshNumberDialog(
                getString(R.string.settings_monitor_process_limit_title),
                "monitor_process_limit",
                80,
                10,
                500);
    }

    private void showMonitorTrafficScopeDialog() {
        String[] options = {
                getString(R.string.settings_monitor_scope_session),
                getString(R.string.settings_monitor_scope_boot)
        };
        String current = prefs.getString("monitor_traffic_scope", "session");
        int checked = "boot".equals(current) ? 1 : 0;
        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.settings_monitor_traffic_scope_title))
                .setSingleChoiceItems(options, checked, (dialog, which) -> {
                    prefs.edit().putString("monitor_traffic_scope", which == 1 ? "boot" : "session").apply();
                    dialog.dismiss();
                    recreate();
                })
                .setNegativeButton(getString(R.string.action_cancel), null)
                .show();
    }

    private void showMonitorCardOrderDialog() {
        String[] options = getResources().getStringArray(R.array.monitor_card_order_options);
        String current = sanitizeMonitorCardOrder(prefs.getString("monitor_card_order", DEFAULT_MONITOR_CARD_ORDER));
        int checked = resolveMonitorCardOrderIndex(current);
        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.settings_monitor_card_order_title))
                .setSingleChoiceItems(options, checked, (dialog, which) -> {
                    prefs.edit().putString("monitor_card_order", resolveMonitorCardOrderValue(which)).apply();
                    dialog.dismiss();
                    recreate();
                })
                .setNegativeButton(getString(R.string.action_cancel), null)
                .show();
    }

    private int resolveMonitorCardOrderIndex(String order) {
        String target = sanitizeMonitorCardOrder(order);
        for (int i = 0; i < 4; i++) {
            if (target.equals(resolveMonitorCardOrderValue(i))) {
                return i;
            }
        }
        return 0;
    }

    private String resolveMonitorCardOrderValue(int index) {
        if (index == 1) return "cpu,memory,load,network,disk,process";
        if (index == 2) return "network,load,cpu,memory,disk,process";
        if (index == 3) return "process,cpu,memory,network,disk,load";
        return DEFAULT_MONITOR_CARD_ORDER;
    }

    private String sanitizeMonitorCardOrder(String raw) {
        Set<String> keys = new LinkedHashSet<>();
        if (raw != null && !raw.trim().isEmpty()) {
            String[] parts = raw.split(",");
            for (String part : parts) {
                String key = part == null ? "" : part.trim().toLowerCase(Locale.US);
                for (String allow : MONITOR_CARD_KEYS) {
                    if (allow.equals(key)) {
                        keys.add(key);
                        break;
                    }
                }
            }
        }
        for (String key : MONITOR_CARD_KEYS) {
            keys.add(key);
        }
        return TextUtils.join(",", keys);
    }

    private String resolveMonitorCardLabel(String key) {
        if ("load".equals(key)) return getString(R.string.monitor_system_load);
        if ("cpu".equals(key)) return getString(R.string.monitor_cpu_load);
        if ("memory".equals(key)) return getString(R.string.monitor_memory);
        if ("network".equals(key)) return getString(R.string.monitor_network);
        if ("disk".equals(key)) return getString(R.string.monitor_disk);
        if ("process".equals(key)) return getString(R.string.monitor_process);
        return key;
    }

    private void showKeyboardHeightDialog() {
        String[] options = getResources().getStringArray(R.array.keyboard_height_options);
        int currentHeight = prefs.getInt("keyboard_height_option", 1);
        new AlertDialog.Builder(this)
            .setTitle(getString(R.string.settings_terminal_keyboard_height_title))
            .setSingleChoiceItems(options, currentHeight, (dialog, which) -> {
                prefs.edit().putInt("keyboard_height_option", which).apply();
                dialog.dismiss();
                Toast.makeText(this, getString(R.string.settings_terminal_keyboard_height_updated), Toast.LENGTH_SHORT).show();
            })
            .setNegativeButton(getString(R.string.action_cancel), null)
            .show();
    }
    
    private void showKeyboardLayoutDialog() {
        String[] layouts = getResources().getStringArray(R.array.keyboard_layout_options);
        int currentLayout = prefs.getInt("keyboard_layout_option", 0);
        
        new AlertDialog.Builder(this)
            .setTitle(getString(R.string.settings_terminal_keyboard_layout_title))
            .setSingleChoiceItems(layouts, currentLayout, (dialog, which) -> {
                prefs.edit().putInt("keyboard_layout_option", which).apply();
                dialog.dismiss();
                Toast.makeText(this, getString(R.string.settings_terminal_keyboard_layout_updated, layouts[which]), Toast.LENGTH_SHORT).show();
            })
            .setNegativeButton(getString(R.string.action_cancel), null)
            .show();
    }

    private void showScrollbackDialog() {
        showSshNumberDialog(getString(R.string.settings_terminal_scrollback_title), "terminal_scrollback_lines", 2000, 100, 20000);
    }
    
    private void resetKeyboardLayout() {
        new AlertDialog.Builder(this)
            .setTitle(getString(R.string.settings_terminal_keyboard_reset_confirm_title))
            .setMessage(getString(R.string.settings_terminal_keyboard_reset_confirm_message))
            .setPositiveButton(getString(R.string.action_ok), (dialog, which) -> {
                prefs.edit().remove("keyboard_height_option")
                           .remove("keyboard_layout_option")
                           .apply();
                Toast.makeText(this, getString(R.string.settings_terminal_keyboard_reset_done), Toast.LENGTH_SHORT).show();
            })
            .setNegativeButton(getString(R.string.action_cancel), null)
            .show();
    }

    private void buildThemeDisplayPage() {
        View themeMode = addItem(R.drawable.ic_action_appearance, getString(R.string.settings_theme_mode_title), "", "theme_mode", v -> showThemeDialog());
        updateThemeSummary(themeMode);
        
        addItem(R.drawable.ic_action_image, getString(R.string.settings_theme_bg_title), getString(R.string.settings_theme_bg_summary), "theme_background", v -> pickBackgroundImage());
        addItem(R.drawable.ic_action_grid, getString(R.string.settings_theme_density_title), getString(R.string.settings_theme_density_summary), "theme_density", v -> showDensityDialog());
        addItem(R.drawable.ic_action_view_list, getString(R.string.settings_theme_list_style_title), getString(R.string.settings_theme_list_style_summary), "theme_list_style", v -> showHostDisplayStyleDialog());
        addHeader(getString(R.string.settings_preview_header));
        hostPreview = inflater.inflate(R.layout.item_host, contentContainer, false);
        contentContainer.addView(hostPreview);
        updateHostPreview(prefs.getInt("host_display_style", 0));
    }

    private void buildFileManagementPage() {
        // Download Path
        String downloadPath = prefs.getString("file_download_path", "/storage/emulated/0/Download");
        addItem(R.drawable.ic_action_storage, getString(R.string.settings_file_download_path_title), downloadPath, "file_download_path", v -> showPathDialog("file_download_path", downloadPath));

        // Sort Order
        addItem(R.drawable.ic_action_sort, getString(R.string.settings_file_sort_order_title), getString(R.string.settings_file_sort_order_summary), "file_sort_order", v -> showSortOrderDialog());

        // Show Hidden
        View hiddenItem = addItem(R.drawable.ic_action_visibility, getString(R.string.settings_file_show_hidden_title), getString(R.string.settings_file_show_hidden_summary), "file_show_hidden", null);
        Switch hiddenSwitch = hiddenItem.findViewById(R.id.switch_widget);
        hiddenSwitch.setVisibility(View.VISIBLE);
        hiddenItem.findViewById(R.id.chevron).setVisibility(View.GONE);
        hiddenSwitch.setChecked(prefs.getBoolean("file_show_hidden", false));
        hiddenSwitch.setOnCheckedChangeListener((btn, isChecked) -> prefs.edit().putBoolean("file_show_hidden", isChecked).apply());
        hiddenItem.setOnClickListener(v -> hiddenSwitch.toggle());

        // Icon Size
        addItem(R.drawable.ic_action_format_size, getString(R.string.settings_file_icon_size_title), getString(R.string.settings_file_icon_size_summary), "file_icon_size", v -> showIconSizeDialog());

        // Unzip Path
        String unzipPath = prefs.getString("file_unzip_path", "/storage/emulated/0/Download");
        addItem(R.drawable.ic_action_storage, getString(R.string.settings_file_unzip_path_title), unzipPath, "file_unzip_path", v -> showPathDialog("file_unzip_path", unzipPath));

        // Editor Font
        addItem(R.drawable.ic_action_edit, getString(R.string.settings_file_editor_font_title), getString(R.string.settings_file_editor_font_summary), "file_editor_font", v -> showEditorFontDialog());
    }

    private void showPathDialog(String key, String currentPath) {
        EditText input = new EditText(this);
        input.setText(currentPath);
        new AlertDialog.Builder(this)
            .setTitle(getString(R.string.settings_file_path_current, ""))
            .setView(input)
            .setPositiveButton(getString(R.string.action_save), (dialog, which) -> {
                String newPath = input.getText().toString();
                prefs.edit().putString(key, newPath).apply();
                recreate();
            })
            .setNegativeButton(getString(R.string.action_cancel), null)
            .show();
    }

    private void showSortOrderDialog() {
        String[] options = getResources().getStringArray(R.array.file_sort_options);
        int current = prefs.getInt("file_sort_order", 0);
        new AlertDialog.Builder(this)
            .setTitle(getString(R.string.settings_file_sort_order_title))
            .setSingleChoiceItems(options, current, (dialog, which) -> {
                prefs.edit().putInt("file_sort_order", which).apply();
                dialog.dismiss();
            })
            .show();
    }

    private void showIconSizeDialog() {
        String[] options = getResources().getStringArray(R.array.file_icon_size_options);
        int current = prefs.getInt("file_icon_size", 0);
        new AlertDialog.Builder(this)
            .setTitle(getString(R.string.settings_file_icon_size_title))
            .setSingleChoiceItems(options, current, (dialog, which) -> {
                prefs.edit().putInt("file_icon_size", which).apply();
                dialog.dismiss();
            })
            .show();
    }

    private void showEditorFontDialog() {
        String[] sizes = getResources().getStringArray(R.array.font_size_options);
        int current = prefs.getInt("file_editor_font_size", 1);
        new AlertDialog.Builder(this)
            .setTitle(getString(R.string.settings_file_editor_font_title))
            .setSingleChoiceItems(sizes, current, (dialog, which) -> {
                prefs.edit().putInt("file_editor_font_size", which).apply();
                dialog.dismiss();
            })
            .show();
    }

    private void showThemeDialog() {
        String[] options = {getString(R.string.settings_theme_mode_light), getString(R.string.settings_theme_mode_dark), getString(R.string.settings_language_system)};
        int current = prefs.getInt("theme_mode", AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
        int selected = 2;
        if (current == AppCompatDelegate.MODE_NIGHT_NO) selected = 0;
        else if (current == AppCompatDelegate.MODE_NIGHT_YES) selected = 1;
        
        new AlertDialog.Builder(this)
            .setTitle(getString(R.string.settings_theme_mode_title))
            .setSingleChoiceItems(options, selected, (dialog, which) -> {
                int mode = AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM;
                if (which == 0) mode = AppCompatDelegate.MODE_NIGHT_NO;
                else if (which == 1) mode = AppCompatDelegate.MODE_NIGHT_YES;
                
                AppCompatDelegate.setDefaultNightMode(mode);
                prefs.edit().putInt("theme_mode", mode).apply();
                recreate();
                dialog.dismiss();
            })
            .show();
    }

    private View createTerminalPreview() {
        TextView preview = new TextView(this);
        preview.setText(getString(R.string.settings_terminal_preview_text));
        preview.setPadding(16, 12, 16, 12);
        int sizeIndex = prefs.getInt("font_size_index", 1);
        float[] sizes = new float[]{12f, 14f, 16f, 18f};
        float size = sizes[Math.max(0, Math.min(sizeIndex, sizes.length - 1))];
        preview.setTextSize(size);
        int family = prefs.getInt("terminal_font_family", 0);
        preview.setTypeface(family == 1 ? android.graphics.Typeface.DEFAULT : android.graphics.Typeface.MONOSPACE);
        return preview;
    }

    private void openSettingsChangeLog() {
        Intent intent = new Intent(this, SettingsDetailActivity.class);
        intent.putExtra("page", "settings_log");
        startActivity(intent);
    }

    private void buildSettingsChangeLogPage() {
        addHeader(getString(R.string.settings_change_log_title));
        addItem(R.drawable.ic_action_delete, getString(R.string.settings_change_log_clear_title), getString(R.string.settings_change_log_clear_summary), "settings_change_log_clear", v -> {
            prefs.edit().putString("settings_change_logs", "[]").apply();
            recreate();
        });
        addDivider();
        List<JSONObject> logs = loadSettingsChangeLogs();
        if (logs.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText(getString(R.string.settings_search_empty));
            empty.setPadding(16, 16, 16, 16);
            contentContainer.addView(empty);
            return;
        }
        for (JSONObject log : logs) {
            String label = log.optString("label", log.optString("key", ""));
            String oldValue = log.optString("old", "");
            String newValue = log.optString("new", "");
            long time = log.optLong("time", 0L);
            String timeText = time <= 0 ? "" : DateFormat.getDateTimeInstance().format(new Date(time));
            String summary = timeText + " · " + oldValue + " → " + newValue;
            View item = createItemView(R.drawable.ic_action_view_list, label, summary, v -> {});
            contentContainer.addView(item);
        }
    }

    private List<JSONObject> loadSettingsChangeLogs() {
        ArrayList<JSONObject> result = new ArrayList<>();
        String raw = prefs.getString("settings_change_logs", "[]");
        try {
            JSONArray array = new JSONArray(raw == null ? "[]" : raw);
            for (int i = array.length() - 1; i >= 0; i--) {
                Object obj = array.opt(i);
                if (obj instanceof JSONObject) result.add((JSONObject) obj);
            }
        } catch (Exception ignored) {}
        return result;
    }

    private void buildSearchPage(String initialQuery) {
        TextInputLayout inputLayout = new TextInputLayout(this);
        inputLayout.setHint(getString(R.string.settings_search_hint));
        inputLayout.setBoxBackgroundMode(TextInputLayout.BOX_BACKGROUND_OUTLINE);
        inputLayout.setStartIconDrawable(R.drawable.ic_action_search);
        TextInputEditText input = new TextInputEditText(this);
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        input.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        if (initialQuery != null) {
            input.setText(initialQuery);
            input.setSelection(initialQuery.length());
        }
        inputLayout.addView(input, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        contentContainer.addView(inputLayout);
        LinearLayout resultContainer = new LinearLayout(this);
        resultContainer.setOrientation(LinearLayout.VERTICAL);
        contentContainer.addView(resultContainer);
        Runnable update = () -> renderSearchResults(resultContainer, input.getText() == null ? "" : input.getText().toString());
        input.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override
            public void afterTextChanged(android.text.Editable s) {
                update.run();
            }
        });
        update.run();
    }

    private void renderSearchResults(LinearLayout container, String query) {
        container.removeAllViews();
        String q = query == null ? "" : query.trim().toLowerCase(Locale.getDefault());
        List<SettingEntry> entries = getSearchEntries();
        ArrayList<SettingEntry> matches = new ArrayList<>();
        for (SettingEntry entry : entries) {
            String title = entry.title == null ? "" : entry.title.toLowerCase(Locale.getDefault());
            String summary = entry.summary == null ? "" : entry.summary.toLowerCase(Locale.getDefault());
            if (q.isEmpty() || title.contains(q) || summary.contains(q)) {
                matches.add(entry);
            }
        }
        if (matches.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText(getString(R.string.settings_search_empty));
            empty.setPadding(16, 16, 16, 16);
            container.addView(empty);
            return;
        }
        for (SettingEntry entry : matches) {
            View item = createItemView(entry.iconRes, entry.title, entry.summary, v -> openDetailFromSearch(entry));
            container.addView(item);
        }
    }

    private View createItemView(int iconRes, String title, String summary, View.OnClickListener listener) {
        View item = inflater.inflate(R.layout.item_setting, contentContainer, false);
        ImageView icon = item.findViewById(R.id.icon);
        TextView tvTitle = item.findViewById(R.id.title);
        TextView tvSummary = item.findViewById(R.id.summary);
        icon.setImageResource(iconRes);
        tvTitle.setText(title);
        if (summary != null && !summary.isEmpty()) {
            tvSummary.setText(summary);
            tvSummary.setVisibility(View.VISIBLE);
        } else {
            tvSummary.setVisibility(View.GONE);
        }
        item.setOnClickListener(listener);
        return item;
    }

    private void openDetailFromSearch(SettingEntry entry) {
        Intent intent = new Intent(this, SettingsDetailActivity.class);
        intent.putExtra("page", entry.page);
        intent.putExtra("focus_key", entry.focus);
        startActivity(intent);
    }

    private List<SettingEntry> getSearchEntries() {
        ArrayList<SettingEntry> list = new ArrayList<>();
        list.add(new SettingEntry("ssh_quick_terminal", getString(R.string.settings_ssh_quick_terminal_title), getString(R.string.settings_ssh_quick_terminal_summary), "ssh", "ssh_quick_terminal", R.drawable.ic_action_computer));
        list.add(new SettingEntry("ssh_quick_sftp", getString(R.string.settings_ssh_quick_sftp_title), getString(R.string.settings_ssh_quick_sftp_summary), "ssh", "ssh_quick_sftp", R.drawable.ic_action_storage));
        list.add(new SettingEntry("home_host_auto_connect", getString(R.string.settings_ssh_home_auto_connect_title), resolveHomeAutoConnectSummary(), "ssh", "home_host_auto_connect", R.drawable.ic_action_refresh));
        list.add(new SettingEntry("home_host_list_auto_fetch", getString(R.string.settings_ssh_home_list_auto_fetch_title), resolveHomeListAutoFetchSummary(), "ssh", "home_host_list_auto_fetch", R.drawable.ic_action_view_list));
        list.add(new SettingEntry("ssh_hosts", getString(R.string.settings_ssh_hosts_title), getString(R.string.settings_ssh_hosts_summary), "ssh", "ssh_hosts", R.drawable.ic_action_computer));
        list.add(new SettingEntry("ssh_keys", getString(R.string.settings_ssh_keys_title), getString(R.string.settings_ssh_keys_summary), "ssh", "ssh_keys", R.drawable.ic_action_vpn_key));
        list.add(new SettingEntry("ssh_connect_timeout", getString(R.string.settings_ssh_connect_timeout_title), getString(R.string.settings_ssh_connect_timeout_summary, prefs.getInt("ssh_connect_timeout_sec", 10)), "ssh", "ssh_connect_timeout", R.drawable.ic_action_info));
        list.add(new SettingEntry("ssh_read_timeout", getString(R.string.settings_ssh_read_timeout_title), getString(R.string.settings_ssh_read_timeout_summary, prefs.getInt("ssh_read_timeout_sec", 60)), "ssh", "ssh_read_timeout", R.drawable.ic_action_info));
        list.add(new SettingEntry("ssh_keepalive_interval", getString(R.string.settings_ssh_keepalive_interval_title), getString(R.string.settings_ssh_keepalive_interval_summary, prefs.getInt("ssh_keepalive_interval_sec", 0)), "ssh", "ssh_keepalive_interval", R.drawable.ic_action_info));
        list.add(new SettingEntry("ssh_keepalive_reply", getString(R.string.settings_ssh_keepalive_reply_title), "", "ssh", "ssh_keepalive_reply", R.drawable.ic_action_info));
        list.add(new SettingEntry("sftp_default_path", getString(R.string.settings_ssh_sftp_default_path_title), prefs.getString("sftp_default_path", "/root"), "ssh", "sftp_default_path", R.drawable.ic_action_storage));
        list.add(new SettingEntry("terminal_font_theme", getString(R.string.settings_terminal_font_title) + " / " + getString(R.string.settings_terminal_theme_title), getString(R.string.settings_terminal_font_summary), "terminal", "terminal_font_theme", R.drawable.ic_action_appearance));
        list.add(new SettingEntry("terminal_keypad", getString(R.string.settings_terminal_keypad_title), getString(R.string.settings_terminal_keypad_summary), "terminal", "terminal_keypad", R.drawable.ic_action_keyboard));
        list.add(new SettingEntry("terminal_keyboard_height", getString(R.string.settings_terminal_keyboard_height_title), getString(R.string.settings_terminal_keyboard_height_summary), "terminal", "terminal_keyboard_height", R.drawable.ic_action_format_size));
        list.add(new SettingEntry("terminal_keyboard_layout", getString(R.string.settings_terminal_keyboard_layout_title), getString(R.string.settings_terminal_keyboard_layout_summary), "terminal", "terminal_keyboard_layout", R.drawable.ic_action_grid));
        list.add(new SettingEntry("terminal_scrollback", getString(R.string.settings_terminal_scrollback_title), getString(R.string.settings_terminal_scrollback_summary, prefs.getInt("terminal_scrollback_lines", 2000)), "terminal", "terminal_scrollback", R.drawable.ic_action_view_list));
        list.add(new SettingEntry("terminal_behavior", getString(R.string.settings_terminal_behavior_title), getString(R.string.settings_terminal_behavior_summary), "terminal", "terminal_behavior", R.drawable.ic_action_settings));
        list.add(new SettingEntry("monitor_refresh_interval", getString(R.string.settings_monitor_refresh_interval_title), getString(R.string.settings_monitor_refresh_interval_summary, prefs.getInt("monitor_refresh_interval_sec", 3)), "monitor", "monitor_refresh_interval", R.drawable.ic_action_refresh));
        list.add(new SettingEntry("monitor_process_limit", getString(R.string.settings_monitor_process_limit_title), getString(R.string.settings_monitor_process_limit_summary, prefs.getInt("monitor_process_limit", 80)), "monitor", "monitor_process_limit", R.drawable.ic_action_view_list));
        list.add(new SettingEntry("monitor_show_total_traffic", getString(R.string.settings_monitor_show_total_traffic_title), prefs.getBoolean("monitor_show_total_traffic", true) ? getString(R.string.settings_monitor_show_total_traffic_on) : getString(R.string.settings_monitor_show_total_traffic_off), "monitor", "monitor_show_total_traffic", R.drawable.ic_dashboard_network));
        String scope = prefs.getString("monitor_traffic_scope", "session");
        list.add(new SettingEntry("monitor_traffic_scope", getString(R.string.settings_monitor_traffic_scope_title), "boot".equals(scope) ? getString(R.string.settings_monitor_scope_boot) : getString(R.string.settings_monitor_scope_session), "monitor", "monitor_traffic_scope", R.drawable.ic_dashboard_network));
        list.add(new SettingEntry("monitor_card_order", getString(R.string.settings_monitor_card_order_title), getString(R.string.settings_monitor_card_order_summary), "monitor", "monitor_card_order", R.drawable.ic_action_sort));
        list.add(new SettingEntry("theme_mode", getString(R.string.settings_theme_mode_title), getString(R.string.settings_theme_mode_summary), "theme_display", "theme_mode", R.drawable.ic_action_appearance));
        list.add(new SettingEntry("theme_background", getString(R.string.settings_theme_bg_title), getString(R.string.settings_theme_bg_summary), "theme_display", "theme_background", R.drawable.ic_action_image));
        list.add(new SettingEntry("theme_density", getString(R.string.settings_theme_density_title), getString(R.string.settings_theme_density_summary), "theme_display", "theme_density", R.drawable.ic_action_grid));
        list.add(new SettingEntry("theme_list_style", getString(R.string.settings_theme_list_style_title), getString(R.string.settings_theme_list_style_summary), "theme_display", "theme_list_style", R.drawable.ic_action_view_list));
        list.add(new SettingEntry("cloud_sync", getString(R.string.settings_cloud_sync_title), getString(R.string.settings_main_cloud_summary), "cloud_sync", null, R.drawable.ic_action_cloud_upload));
        list.add(new SettingEntry("file_download_path", getString(R.string.settings_file_download_path_title), prefs.getString("file_download_path", ""), "file_management", "file_download_path", R.drawable.ic_action_storage));
        list.add(new SettingEntry("file_sort_order", getString(R.string.settings_file_sort_order_title), getString(R.string.settings_file_sort_order_summary), "file_management", "file_sort_order", R.drawable.ic_action_sort));
        list.add(new SettingEntry("file_show_hidden", getString(R.string.settings_file_show_hidden_title), getString(R.string.settings_file_show_hidden_summary), "file_management", "file_show_hidden", R.drawable.ic_action_visibility));
        list.add(new SettingEntry("file_icon_size", getString(R.string.settings_file_icon_size_title), getString(R.string.settings_file_icon_size_summary), "file_management", "file_icon_size", R.drawable.ic_action_format_size));
        list.add(new SettingEntry("file_unzip_path", getString(R.string.settings_file_unzip_path_title), prefs.getString("file_unzip_path", ""), "file_management", "file_unzip_path", R.drawable.ic_action_storage));
        list.add(new SettingEntry("file_editor_font", getString(R.string.settings_file_editor_font_title), getString(R.string.settings_file_editor_font_summary), "file_management", "file_editor_font", R.drawable.ic_action_edit));
        list.add(new SettingEntry("session_policy", getString(R.string.settings_session_policy_title), getString(R.string.settings_session_policy_summary), "session_policy", "policy_summary", R.drawable.ic_action_settings));
        list.add(new SettingEntry("backup_create_full", getString(R.string.settings_backup_create_full_title), getString(R.string.settings_backup_create_full_summary), "backup", "backup_create_full", R.drawable.ic_action_storage));
        list.add(new SettingEntry("backup_restore_full", getString(R.string.settings_backup_restore_full_title), getString(R.string.settings_backup_restore_full_summary), "backup", "backup_restore_full", R.drawable.ic_action_storage));
        list.add(new SettingEntry("general_language", getString(R.string.settings_language_title), getString(R.string.settings_language_summary), "general", "general_language", R.drawable.ic_action_language));
        list.add(new SettingEntry("general_persistent_notification", getString(R.string.settings_notification_persistent_title), getString(R.string.settings_notification_persistent_summary), "general", "general_persistent_notification", R.drawable.ic_action_computer));
        list.add(new SettingEntry("general_system_notification", getString(R.string.settings_notification_system_title), getString(R.string.settings_notification_system_summary), "general", "general_system_notification", R.drawable.ic_action_notifications));
        list.add(new SettingEntry("general_clear_cache", getString(R.string.settings_clear_cache_title), getString(R.string.settings_clear_cache_summary), "general", "general_clear_cache", R.drawable.ic_action_storage));
        list.add(new SettingEntry("general_change_log", getString(R.string.settings_change_log_title), getString(R.string.settings_change_log_summary), "settings_log", null, R.drawable.ic_action_view_list));
        list.add(new SettingEntry("general_about", getString(R.string.settings_about_title), getString(R.string.settings_about_summary), "general", "general_about", android.R.drawable.ic_menu_info_details));
        return list;
    }

    private void updateThemeSummary(View item) {
        TextView summary = item.findViewById(R.id.summary);
        int current = prefs.getInt("theme_mode", AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
        if (current == AppCompatDelegate.MODE_NIGHT_NO) summary.setText(getString(R.string.settings_theme_mode_light));
        else if (current == AppCompatDelegate.MODE_NIGHT_YES) summary.setText(getString(R.string.settings_theme_mode_dark));
        else summary.setText(getString(R.string.settings_language_system));
        summary.setVisibility(View.VISIBLE);
    }

    private void buildGeneralPage() {
        addItem(R.drawable.ic_action_language, getString(R.string.settings_language_title), getString(R.string.settings_language_summary), "general_language", v -> showLanguageDialog());
        addHeader(getString(R.string.settings_notifications_header));
        View persistent = addItem(R.drawable.ic_action_computer, getString(R.string.settings_notification_persistent_title), getString(R.string.settings_notification_persistent_summary), "general_persistent_notification", null);
        persistentNotificationSwitch = persistent.findViewById(R.id.switch_widget);
        persistentNotificationSummary = persistent.findViewById(R.id.summary);
        persistentNotificationSwitch.setVisibility(View.VISIBLE);
        persistent.findViewById(R.id.chevron).setVisibility(View.GONE);
        boolean enabled = prefs.getBoolean("persistent_notification_enabled", false);
        persistentNotificationSwitch.setChecked(enabled);
        persistentNotificationSwitch.setOnCheckedChangeListener((btn, isChecked) -> {
            prefs.edit().putBoolean("persistent_notification_enabled", isChecked).apply();
            updatePersistentNotificationState();
            PersistentNotificationHelper.refresh(this);
        });
        persistent.setOnClickListener(v -> persistentNotificationSwitch.toggle());
        addItem(R.drawable.ic_action_notifications, getString(R.string.settings_notification_system_title), getString(R.string.settings_notification_system_summary), "general_system_notification", v -> openNotificationSettings());
        addDivider();
        addItem(R.drawable.ic_action_storage, getString(R.string.settings_clear_cache_title), getString(R.string.settings_clear_cache_summary), "general_clear_cache", v -> confirmClearCache());
        addItem(R.drawable.ic_action_view_list, getString(R.string.settings_change_log_title), getString(R.string.settings_change_log_summary), "general_change_log", v -> openSettingsChangeLog());
        addItem(android.R.drawable.ic_menu_info_details, getString(R.string.settings_about_title), getString(R.string.settings_about_summary), "general_about", v -> showAboutDialog());
    }

    private void buildSessionPolicyPage() {
        SessionPersistenceManager manager = SessionPersistenceManager.getInstance(this);
        
        // 策略选择
        View policyItem = addItem(R.drawable.ic_action_settings, getString(R.string.settings_session_policy_mode_title), 
            manager.getPolicyDescription(manager.getSessionPolicy()), v -> showSessionPolicyDialog());
        policyItem.setTag("policy_summary");
        
        addDivider();
        
        // 超时设置（仅当选择 TIMEOUT_DISCONNECT 时显示）
        if (manager.getSessionPolicy() == SessionPersistenceManager.SessionPolicy.TIMEOUT_DISCONNECT) {
            View timeoutItem = addItem(R.drawable.ic_action_info, getString(R.string.settings_session_timeout_title), 
                manager.getTimeoutMinutes() + " " + getString(R.string.settings_session_timeout_unit), 
                v -> showSessionTimeoutDialog());
            timeoutItem.setTag("timeout_summary");
        }
        
        // 后台断开设置
        View bgDisconnectItem = addItem(R.drawable.ic_action_settings, getString(R.string.settings_session_bg_disconnect_title), 
            manager.isAutoDisconnectOnBackground() ? getString(R.string.settings_enabled) : getString(R.string.settings_disabled), 
            v -> showBackgroundDisconnectDialog());
        bgDisconnectItem.setTag("bg_disconnect_summary");
        
        // 后台断开延迟
        if (manager.isAutoDisconnectOnBackground()) {
            View delayItem = addItem(R.drawable.ic_action_info, getString(R.string.settings_session_bg_delay_title), 
                manager.getBackgroundDisconnectDelay() + " " + getString(R.string.settings_session_bg_delay_unit), 
                v -> showBackgroundDelayDialog());
            delayItem.setTag("bg_delay_summary");
        }
        
        addDivider();
        
        // 恢复时自动重连
        View reconnectItem = addItem(R.drawable.ic_action_refresh, getString(R.string.settings_session_reconnect_title), 
            manager.isReconnectOnResume() ? getString(R.string.settings_enabled) : getString(R.string.settings_disabled), null);
        Switch reconnectSwitch = reconnectItem.findViewById(R.id.switch_widget);
        reconnectSwitch.setVisibility(View.VISIBLE);
        reconnectItem.findViewById(R.id.chevron).setVisibility(View.GONE);
        reconnectSwitch.setChecked(manager.isReconnectOnResume());
        reconnectSwitch.setOnCheckedChangeListener((btn, isChecked) -> {
            manager.setReconnectOnResume(isChecked);
            updateSessionPolicySummaries();
        });
        reconnectItem.setOnClickListener(v -> reconnectSwitch.toggle());
        
        addDivider();
        
        // 心跳保活设置
        addHeader(getString(R.string.settings_session_keepalive_header));
        
        View keepAliveItem = addItem(R.drawable.ic_action_computer, getString(R.string.settings_session_keepalive_title), 
            manager.isKeepAliveEnabled() ? getString(R.string.settings_enabled) : getString(R.string.settings_disabled), null);
        Switch keepAliveSwitch = keepAliveItem.findViewById(R.id.switch_widget);
        keepAliveSwitch.setVisibility(View.VISIBLE);
        keepAliveItem.findViewById(R.id.chevron).setVisibility(View.GONE);
        keepAliveSwitch.setChecked(manager.isKeepAliveEnabled());
        keepAliveSwitch.setOnCheckedChangeListener((btn, isChecked) -> {
            manager.setKeepAliveEnabled(isChecked);
            updateSessionPolicySummaries();
        });
        keepAliveItem.setOnClickListener(v -> keepAliveSwitch.toggle());
        
        // 心跳间隔
        if (manager.isKeepAliveEnabled()) {
            View intervalItem = addItem(R.drawable.ic_action_info, getString(R.string.settings_session_keepalive_interval_title), 
                manager.getKeepAliveInterval() + " " + getString(R.string.settings_session_keepalive_interval_unit), 
                v -> showKeepAliveIntervalDialog());
            intervalItem.setTag("keepalive_interval_summary");
        }
        
        addDivider();
        
        // 重置按钮
        addItem(R.drawable.ic_action_settings, getString(R.string.settings_session_reset_title), 
            getString(R.string.settings_session_reset_summary), "session_policy_reset", v -> showResetSessionPolicyDialog());
    }

    private void buildBackupPage() {
        // 创建备份
        addHeader(getString(R.string.settings_backup_create_header));
        addItem(R.drawable.ic_action_storage, getString(R.string.settings_backup_create_full_title), 
            getString(R.string.settings_backup_create_full_summary), "backup_create_full", v -> createFullBackup());
        addItem(R.drawable.ic_action_import_export, getString(R.string.settings_backup_export_csv_title), 
            getString(R.string.settings_backup_export_csv_summary), "backup_export_csv", v -> exportHostsAsCsv());
        
        addDivider();
        
        // 恢复备份
        addHeader(getString(R.string.settings_backup_restore_header));
        addItem(R.drawable.ic_action_storage, getString(R.string.settings_backup_restore_full_title), 
            getString(R.string.settings_backup_restore_full_summary), "backup_restore_full", v -> restoreFullBackup());
        addItem(R.drawable.ic_action_import_export, getString(R.string.settings_backup_import_csv_title), 
            getString(R.string.settings_backup_import_csv_summary), "backup_import_csv", v -> importHostsFromCsv());
        
        addDivider();
        
        // 说明
        addHeader(getString(R.string.settings_backup_note_header));
        TextView note = new TextView(this);
        note.setText(getString(R.string.settings_backup_note_content));
        note.setPadding(16, 16, 16, 16);
        note.setTextSize(14);
        contentContainer.addView(note);
    }

    private View addItem(int iconRes, String title, String summary, View.OnClickListener listener) {
        View item = inflater.inflate(R.layout.item_setting, contentContainer, false);
        ImageView icon = item.findViewById(R.id.icon);
        TextView tvTitle = item.findViewById(R.id.title);
        TextView tvSummary = item.findViewById(R.id.summary);
        icon.setImageResource(iconRes);
        tvTitle.setText(title);
        if (summary != null) {
            tvSummary.setText(summary);
            tvSummary.setVisibility(View.VISIBLE);
        } else {
            tvSummary.setVisibility(View.GONE);
        }
        if (listener != null) {
            item.setOnClickListener(listener);
        }
        contentContainer.addView(item);
        return item;
    }

    private View addItem(int iconRes, String title, String summary, String tag, View.OnClickListener listener) {
        View item = addItem(iconRes, title, summary, listener);
        if (tag != null) {
            item.setTag(tag);
        }
        return item;
    }

    private void addHeader(String title) {
        TextView header = new TextView(new android.view.ContextThemeWrapper(this, R.style.SettingsHeader));
        header.setText(title);
        contentContainer.addView(header);
    }

    private void addDivider() {
        View divider = new View(this, null, 0, R.style.SettingsDivider);
        contentContainer.addView(divider);
    }

    private void applyFocusHighlight() {
        if (focusKey == null || focusKey.isEmpty()) return;
        View target = contentContainer.findViewWithTag(focusKey);
        if (target == null) return;
        target.requestFocus();
        View parent = (View) contentContainer.getParent();
        if (parent instanceof androidx.core.widget.NestedScrollView) {
            ((androidx.core.widget.NestedScrollView) parent).post(() -> ((androidx.core.widget.NestedScrollView) parent).smoothScrollTo(0, target.getTop()));
        }
        focusKey = null;
    }

    private TextView addInfoRow(String label, String value) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(16, 12, 16, 12);
        TextView tvLabel = new TextView(this);
        tvLabel.setText(label);
        LinearLayout.LayoutParams lpLabel = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        tvLabel.setLayoutParams(lpLabel);
        TextView tvValue = new TextView(this);
        tvValue.setText(value);
        row.addView(tvLabel);
        row.addView(tvValue);
        contentContainer.addView(row);
        return tvValue;
    }

    private void openServersTab() {
        Intent intent = new Intent(this, MainActivity.class);
        intent.putExtra("open_tab", 0);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(intent);
    }

    private void showSshQuickPicker(boolean openSftp) {
        executor.execute(() -> {
            HostDao hostDao = AppDatabase.getDatabase(this).hostDao();
            List<HostEntity> hosts = hostDao.getAllHostsNow();
            mainHandler.post(() -> {
                if (hosts == null || hosts.isEmpty()) {
                    Toast.makeText(this, getString(R.string.settings_ssh_no_hosts), Toast.LENGTH_SHORT).show();
                    return;
                }
                String[] items = new String[hosts.size()];
                for (int i = 0; i < hosts.size(); i++) {
                    HostEntity host = hosts.get(i);
                    String title = host.alias != null && !host.alias.isEmpty() ? host.alias : host.username + "@" + host.hostname;
                    String detail = host.username + "@" + host.hostname + ":" + host.port;
                    items[i] = title + "\n" + detail;
                }
                String dialogTitle = openSftp ? getString(R.string.settings_ssh_quick_sftp_title) : getString(R.string.settings_ssh_quick_terminal_title);
                new AlertDialog.Builder(this)
                    .setTitle(dialogTitle)
                    .setItems(items, (d, which) -> openHostQuickAction(hosts.get(which), openSftp))
                    .setNegativeButton(getString(R.string.action_cancel), null)
                    .show();
            });
        });
    }

    private void openHostQuickAction(HostEntity host, boolean openSftp) {
        Intent intent = new Intent(this, openSftp ? SftpActivity.class : SshTerminalActivity.class);
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

    private void showFontSizeDialog() {
        String[] sizes = getResources().getStringArray(R.array.font_size_options);
        int current = prefs.getInt("font_size_index", 1);
        new AlertDialog.Builder(this)
            .setTitle(getString(R.string.settings_terminal_font_title))
            .setSingleChoiceItems(sizes, current, (dialog, which) -> {
                prefs.edit().putInt("font_size_index", which).apply();
                dialog.dismiss();
            })
            .show();
    }

    private void showColorThemeDialog() {
        String[] themes = getResources().getStringArray(R.array.terminal_themes);
        int current = prefs.getInt("terminal_theme_index", 0);
        new AlertDialog.Builder(this)
            .setTitle(getString(R.string.settings_terminal_theme_title))
            .setSingleChoiceItems(themes, current, (dialog, which) -> {
                prefs.edit().putInt("terminal_theme_index", which).apply();
                dialog.dismiss();
            })
            .show();
    }

    private void showFontAndThemeDialog() {
        String[] sizes = getResources().getStringArray(R.array.font_size_options);
        String[] themes = getResources().getStringArray(R.array.terminal_themes);
        int currentSize = prefs.getInt("font_size_index", 1);
        int currentTheme = prefs.getInt("terminal_theme_index", 0);
        int currentFamily = prefs.getInt("terminal_font_family", 0);
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(32, 24, 32, 8);
        TextView fontLabel = new TextView(this);
        fontLabel.setText(getString(R.string.settings_terminal_font_title));
        layout.addView(fontLabel);
        android.widget.RadioGroup fontGroup = new android.widget.RadioGroup(this);
        for (int i = 0; i < sizes.length; i++) {
            android.widget.RadioButton btn = new android.widget.RadioButton(this);
            btn.setText(sizes[i]);
            btn.setId(1000 + i);
            fontGroup.addView(btn);
        }
        fontGroup.check(1000 + currentSize);
        layout.addView(fontGroup);
        TextView familyLabel = new TextView(this);
        familyLabel.setText(getString(R.string.settings_terminal_font_family_title));
        familyLabel.setPadding(0, 24, 0, 0);
        layout.addView(familyLabel);
        android.widget.RadioGroup familyGroup = new android.widget.RadioGroup(this);
        android.widget.RadioButton familyMono = new android.widget.RadioButton(this);
        familyMono.setText(getString(R.string.settings_terminal_font_family_mono));
        familyMono.setId(3000);
        familyGroup.addView(familyMono);
        android.widget.RadioButton familySystem = new android.widget.RadioButton(this);
        familySystem.setText(getString(R.string.settings_terminal_font_family_system));
        familySystem.setId(3001);
        familyGroup.addView(familySystem);
        familyGroup.check(currentFamily == 1 ? 3001 : 3000);
        layout.addView(familyGroup);
        TextView themeLabel = new TextView(this);
        themeLabel.setText(getString(R.string.settings_terminal_theme_title));
        themeLabel.setPadding(0, 24, 0, 0);
        layout.addView(themeLabel);
        android.widget.RadioGroup themeGroup = new android.widget.RadioGroup(this);
        for (int i = 0; i < themes.length; i++) {
            android.widget.RadioButton btn = new android.widget.RadioButton(this);
            btn.setText(themes[i]);
            btn.setId(2000 + i);
            themeGroup.addView(btn);
        }
        themeGroup.check(2000 + currentTheme);
        layout.addView(themeGroup);
        new AlertDialog.Builder(this)
            .setTitle(getString(R.string.settings_terminal_font_title) + " / " + getString(R.string.settings_terminal_theme_title))
            .setView(layout)
            .setPositiveButton(getString(R.string.action_save), (d, w) -> {
                int sizeIdx = fontGroup.getCheckedRadioButtonId() - 1000;
                int themeIdx = themeGroup.getCheckedRadioButtonId() - 2000;
                int familyIdx = familyGroup.getCheckedRadioButtonId() == 3001 ? 1 : 0;
                prefs.edit()
                    .putInt("font_size_index", Math.max(0, sizeIdx))
                    .putInt("terminal_theme_index", Math.max(0, themeIdx))
                    .putInt("terminal_font_family", familyIdx)
                    .apply();
            })
            .setNegativeButton(getString(R.string.action_cancel), null)
            .show();
    }

    private void openTerminalThemeAction(String action) {
        Intent intent = new Intent(this, TerminalActivity.class);
        intent.putExtra("theme_action", action);
        startActivity(intent);
    }

    private void showDensityDialog() {
        String[] densities = getResources().getStringArray(R.array.list_density_options);
        int current = prefs.getInt("list_density", 1);
        new AlertDialog.Builder(this)
            .setTitle(getString(R.string.settings_theme_density_title))
            .setSingleChoiceItems(densities, current, (dialog, which) -> {
                prefs.edit().putInt("list_density", which).apply();
                dialog.dismiss();
            })
            .show();
    }

    private void showTerminalBehaviorDialog() {
        String[] items = getResources().getStringArray(R.array.terminal_behavior_options);
        boolean[] checked = {
            prefs.getBoolean("terminal_enter_newline", true),
            prefs.getBoolean("terminal_local_echo", false),
            prefs.getBoolean("terminal_auto_scroll_output", true),
            prefs.getBoolean("terminal_smooth_scroll", true),
            prefs.getBoolean("terminal_copy_on_select", true),
            prefs.getBoolean("terminal_paste_on_tap", false),
            prefs.getBoolean("terminal_bell_audio", false),
            prefs.getBoolean("terminal_bell_visual", true)
        };
        new AlertDialog.Builder(this)
            .setTitle(getString(R.string.settings_terminal_behavior_title))
            .setMultiChoiceItems(items, checked, (dialog, which, isChecked) -> checked[which] = isChecked)
            .setPositiveButton(getString(R.string.action_save), (d, w) -> {
                prefs.edit()
                    .putBoolean("terminal_enter_newline", checked[0])
                    .putBoolean("terminal_local_echo", checked[1])
                    .putBoolean("terminal_auto_scroll_output", checked[2])
                    .putBoolean("terminal_smooth_scroll", checked[3])
                    .putBoolean("terminal_copy_on_select", checked[4])
                    .putBoolean("terminal_paste_on_tap", checked[5])
                    .putBoolean("terminal_bell_audio", checked[6])
                    .putBoolean("terminal_bell_visual", checked[7])
                    .apply();
            })
            .setNegativeButton(getString(R.string.action_cancel), null)
            .show();
    }

    private void showSshConnectTimeoutDialog() {
        showSshNumberDialog(getString(R.string.settings_ssh_connect_timeout_title), "ssh_connect_timeout_sec", 10, 0, 600);
    }

    private void showSshReadTimeoutDialog() {
        showSshNumberDialog(getString(R.string.settings_ssh_read_timeout_title), "ssh_read_timeout_sec", 60, 0, 3600);
    }

    private void showSshKeepaliveIntervalDialog() {
        showSshNumberDialog(getString(R.string.settings_ssh_keepalive_interval_title), "ssh_keepalive_interval_sec", 0, 0, 3600);
    }

    private void showSftpDefaultPathDialog() {
        EditText input = new EditText(this);
        String current = prefs.getString("sftp_default_path", "/root");
        input.setText(current == null ? "/root" : current);
        new AlertDialog.Builder(this)
            .setTitle(getString(R.string.settings_ssh_sftp_default_path_title))
            .setView(input)
            .setPositiveButton(getString(R.string.action_save), (dialog, which) -> {
                String newPath = input.getText().toString().trim();
                if (newPath.isEmpty()) {
                    Toast.makeText(this, getString(R.string.msg_path_required), Toast.LENGTH_SHORT).show();
                    return;
                }
                prefs.edit().putString("sftp_default_path", newPath).apply();
                recreate();
            })
            .setNegativeButton(getString(R.string.action_cancel), null)
            .show();
    }

    private void showSshNumberDialog(String title, String key, int defaultValue, int minValue, int maxValue) {
        EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_NUMBER);
        int current = prefs.getInt(key, defaultValue);
        input.setText(String.valueOf(current));
        new AlertDialog.Builder(this)
            .setTitle(title)
            .setView(input)
            .setPositiveButton(getString(R.string.action_save), (dialog, which) -> {
                String raw = input.getText().toString().trim();
                if (raw.isEmpty()) {
                    Toast.makeText(this, getString(R.string.msg_input_required), Toast.LENGTH_SHORT).show();
                    return;
                }
                int value;
                try {
                    value = Integer.parseInt(raw);
                } catch (NumberFormatException e) {
                    Toast.makeText(this, getString(R.string.msg_invalid_number), Toast.LENGTH_SHORT).show();
                    return;
                }
                boolean adjusted = false;
                if (value < minValue) {
                    value = minValue;
                    adjusted = true;
                }
                if (maxValue > 0 && value > maxValue) {
                    value = maxValue;
                    adjusted = true;
                }
                if (adjusted) {
                    Toast.makeText(this, getString(R.string.msg_value_adjusted_to, value), Toast.LENGTH_SHORT).show();
                }
                prefs.edit().putInt(key, value).apply();
                recreate();
            })
            .setNegativeButton(getString(R.string.action_cancel), null)
            .show();
    }

    private void updateSshTimeoutSummary(View item, String key, int defaultValue, int summaryRes) {
        TextView summary = item.findViewById(R.id.summary);
        int value = prefs.getInt(key, defaultValue);
        summary.setText(getString(summaryRes, value));
        summary.setVisibility(View.VISIBLE);
    }

    private void updateSshKeepaliveReplySummary(View item) {
        TextView summary = item.findViewById(R.id.summary);
        boolean enabled = prefs.getBoolean("ssh_keepalive_reply", true);
        summary.setText(getString(enabled ? R.string.settings_ssh_keepalive_reply_on : R.string.settings_ssh_keepalive_reply_off));
        summary.setVisibility(View.VISIBLE);
    }

    private void updateSftpDefaultPathSummary(View item) {
        TextView summary = item.findViewById(R.id.summary);
        String path = prefs.getString("sftp_default_path", "/root");
        summary.setText(path == null ? "/root" : path);
        summary.setVisibility(View.VISIBLE);
    }

    private void updateScrollbackSummary(View item) {
        TextView summary = item.findViewById(R.id.summary);
        int lines = prefs.getInt("terminal_scrollback_lines", 2000);
        summary.setText(getString(R.string.settings_terminal_scrollback_summary, lines));
        summary.setVisibility(View.VISIBLE);
    }

    // 首页主机自动连接摘要
    private void updateHomeAutoConnectSummary(View item) {
        TextView summary = item.findViewById(R.id.summary);
        summary.setText(resolveHomeAutoConnectSummary());
        summary.setVisibility(View.VISIBLE);
    }

    // 首页主机列表自动获取信息摘要
    private void updateHomeListAutoFetchSummary(View item) {
        TextView summary = item.findViewById(R.id.summary);
        summary.setText(resolveHomeListAutoFetchSummary());
        summary.setVisibility(View.VISIBLE);
    }

    private String resolveHomeAutoConnectSummary() {
        boolean enabled = prefs.getBoolean("home_host_auto_connect_enabled", false);
        String label = prefs.getString("home_host_label", "");
        if (enabled) {
            if (label == null || label.isEmpty()) {
                return getString(R.string.settings_ssh_home_auto_connect_summary_no_host);
            }
            return getString(R.string.settings_ssh_home_auto_connect_summary_on, label);
        }
        return getString(R.string.settings_ssh_home_auto_connect_summary_off);
    }

    private String resolveHomeListAutoFetchSummary() {
        boolean enabled = prefs.getBoolean("home_host_list_auto_fetch_enabled", false);
        return getString(enabled ? R.string.settings_ssh_home_list_auto_fetch_summary_on : R.string.settings_ssh_home_list_auto_fetch_summary_off);
    }

    private void showKeypadMappingDialog() {
        String[] labels = getResources().getStringArray(R.array.keypad_mapping_labels);
        java.util.Map<String, String> mappings = readKeypadMapping();
        String[] values = new String[labels.length];
        for (int i = 0; i < labels.length; i++) {
            values[i] = mappings.get(labels[i]);
        }
        new AlertDialog.Builder(this)
            .setTitle(getString(R.string.settings_terminal_keypad_title))
            .setItems(labels, (d, which) -> showEditKeypadMapping(labels[which], values[which]))
            .setNeutralButton(getString(R.string.action_reset_all), (d, w) -> {
                prefs.edit().remove("terminal_keypad_mapping").apply();
                Toast.makeText(this, getString(R.string.action_reset_done), Toast.LENGTH_SHORT).show();
            })
            .setNegativeButton(getString(R.string.action_close), null)
            .show();
    }

    private void showEditKeypadMapping(String label, String current) {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(32, 24, 32, 8);
        TextView hint = new TextView(this);
        hint.setText(getString(R.string.settings_terminal_keypad_hint));
        EditText edit = new EditText(this);
        edit.setText(current != null ? current : "");
        layout.addView(hint);
        layout.addView(edit);
        new AlertDialog.Builder(this)
            .setTitle(getString(R.string.settings_terminal_keypad_edit, label))
            .setView(layout)
            .setPositiveButton(getString(R.string.action_save), (d, w) -> {
                java.util.Map<String, String> mapping = readKeypadMapping();
                String value = edit.getText().toString();
                if (value.isEmpty()) {
                    mapping.remove(label);
                } else {
                    mapping.put(label, value);
                }
                persistKeypadMapping(mapping);
            })
            .setNegativeButton(getString(R.string.action_cancel), null)
            .show();
    }

    private java.util.Map<String, String> readKeypadMapping() {
        java.util.Map<String, String> map = new java.util.HashMap<>();
        String json = prefs.getString("terminal_keypad_mapping", null);
        if (json == null || json.isEmpty()) return map;
        try {
            org.json.JSONObject obj = new org.json.JSONObject(json);
            java.util.Iterator<String> it = obj.keys();
            while (it.hasNext()) {
                String k = it.next();
                map.put(k, obj.optString(k));
            }
        } catch (Exception ignored) {}
        return map;
    }

    private void persistKeypadMapping(java.util.Map<String, String> mapping) {
        org.json.JSONObject obj = new org.json.JSONObject();
        for (java.util.Map.Entry<String, String> e : mapping.entrySet()) {
            try {
                obj.put(e.getKey(), e.getValue());
            } catch (Exception ignored) {}
        }
        prefs.edit().putString("terminal_keypad_mapping", obj.toString()).apply();
        Toast.makeText(this, getString(R.string.action_saved), Toast.LENGTH_SHORT).show();
    }

    private void showHostDisplayStyleDialog() {
        String[] styles = getResources().getStringArray(R.array.host_display_styles);
        int current = prefs.getInt("host_display_style", 0);
        new AlertDialog.Builder(this)
            .setTitle(getString(R.string.settings_theme_list_style_title))
            .setSingleChoiceItems(styles, current, (dialog, which) -> {
                prefs.edit().putInt("host_display_style", which).apply();
                updateHostPreview(which);
                dialog.dismiss();
            })
            .show();
    }

    private void updateHostPreview(int style) {
        if (hostPreview == null) return;
        View digital = hostPreview.findViewById(R.id.ll_monitor_digital);
        View linear = hostPreview.findViewById(R.id.ll_monitor_linear);
        View ring = hostPreview.findViewById(R.id.ll_monitor_ring);
        if (digital != null) digital.setVisibility(style == 0 ? View.VISIBLE : View.GONE);
        if (linear != null) linear.setVisibility(style == 1 ? View.VISIBLE : View.GONE);
        if (ring != null) ring.setVisibility(style == 2 ? View.VISIBLE : View.GONE);
        if (style == 2) {
            PieChart cpu = hostPreview.findViewById(R.id.chart_cpu_ring);
            PieChart mem = hostPreview.findViewById(R.id.chart_mem_ring);
            setupRingChart(cpu);
            setupRingChart(mem);
            updateRingChart(cpu, 42);
            updateRingChart(mem, 68);
        }
    }

    private void setupRingChart(PieChart chart) {
        if (chart == null) return;
        if (chart.getData() != null) return;
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

    private void updateRingChart(PieChart chart, int percent) {
        if (chart == null) return;
        ArrayList<PieEntry> entries = new ArrayList<>();
        float free = 100f - percent;
        if (free < 0) free = 0;
        entries.add(new PieEntry(percent, ""));
        entries.add(new PieEntry(free, ""));
        PieDataSet dataSet = new PieDataSet(entries, "");
        int color;
        if (percent <= 50) color = 0xFF4CAF50;
        else if (percent <= 80) color = 0xFFFFC107;
        else color = 0xFFF44336;
        dataSet.setColors(color, 0xFF333333);
        dataSet.setDrawValues(false);
        PieData data = new PieData(dataSet);
        chart.setData(data);
        chart.setCenterText(percent + "%");
        chart.invalidate();
    }

    private void showLanguageDialog() {
        String[] labels = getResources().getStringArray(R.array.language_options);
        String current = prefs.getString("app_language", "system");
        int selected = 0;
        if ("zh".equals(current)) selected = 1;
        else if ("en".equals(current)) selected = 2;
        new AlertDialog.Builder(this)
            .setTitle(getString(R.string.settings_language_title))
            .setSingleChoiceItems(labels, selected, (dialog, which) -> {
                String lang = "system";
                if (which == 1) lang = "zh";
                else if (which == 2) lang = "en";
                prefs.edit().putString("app_language", lang).apply();
                applyLanguage(lang);
                dialog.dismiss();
            })
            .show();
    }

    private void applyLanguage(String lang) {
        if ("system".equals(lang)) {
            AppCompatDelegate.setApplicationLocales(LocaleListCompat.getEmptyLocaleList());
        } else if ("zh".equals(lang)) {
            AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags("zh-CN"));
        } else {
            AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags("en"));
        }
        recreate();
    }

    private void openNotificationSettings() {
        try {
            Intent intent;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                intent = new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName());
            } else {
                intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                    .setData(Uri.parse("package:" + getPackageName()));
            }
            startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(this, getString(R.string.settings_notification_open_failed), Toast.LENGTH_SHORT).show();
        }
    }

    private void confirmClearCache() {
        new AlertDialog.Builder(this)
            .setTitle(getString(R.string.settings_clear_cache_title))
            .setMessage(getString(R.string.settings_clear_cache_confirm))
            .setPositiveButton(getString(R.string.action_clear), (d, w) -> {
                clearCaches();
                Toast.makeText(this, getString(R.string.settings_clear_cache_done), Toast.LENGTH_SHORT).show();
            })
            .setNegativeButton(getString(R.string.action_cancel), null)
            .show();
    }

    private void showAboutDialog() {
        new AlertDialog.Builder(this)
            .setTitle(getString(R.string.app_name))
            .setMessage(getString(R.string.settings_about_message))
            .setPositiveButton(getString(R.string.action_ok), null)
            .show();
    }

    // ==================== 会话保持策略相关方法 ====================

    private void showSessionPolicyDialog() {
        SessionPersistenceManager manager = SessionPersistenceManager.getInstance(this);
        SessionPersistenceManager.SessionPolicy currentPolicy = manager.getSessionPolicy();
        
        String[] options = {
            getString(R.string.settings_session_policy_always),
            getString(R.string.settings_session_policy_smart),
            getString(R.string.settings_session_policy_timeout),
            getString(R.string.settings_session_policy_immediate)
        };
        
        int selected = currentPolicy.getValue();
        
        new AlertDialog.Builder(this)
            .setTitle(getString(R.string.settings_session_policy_mode_title))
            .setSingleChoiceItems(options, selected, (dialog, which) -> {
                SessionPersistenceManager.SessionPolicy newPolicy = SessionPersistenceManager.SessionPolicy.fromValue(which);
                manager.setSessionPolicy(newPolicy);
                dialog.dismiss();
                recreate(); // 重建页面以更新UI
            })
            .setNegativeButton(getString(R.string.action_cancel), null)
            .show();
    }

    private void showSessionTimeoutDialog() {
        SessionPersistenceManager manager = SessionPersistenceManager.getInstance(this);
        
        EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_NUMBER);
        input.setText(String.valueOf(manager.getTimeoutMinutes()));
        
        new AlertDialog.Builder(this)
            .setTitle(getString(R.string.settings_session_timeout_title))
            .setView(input)
            .setPositiveButton(getString(R.string.action_save), (dialog, which) -> {
                try {
                    int minutes = Integer.parseInt(input.getText().toString());
                    manager.setTimeoutMinutes(minutes);
                    updateSessionPolicySummaries();
                } catch (NumberFormatException e) {
                    Toast.makeText(this, getString(R.string.msg_invalid_number), Toast.LENGTH_SHORT).show();
                }
            })
            .setNegativeButton(getString(R.string.action_cancel), null)
            .show();
    }

    private void showBackgroundDisconnectDialog() {
        SessionPersistenceManager manager = SessionPersistenceManager.getInstance(this);
        
        new AlertDialog.Builder(this)
            .setTitle(getString(R.string.settings_session_bg_disconnect_title))
            .setMessage(getString(R.string.settings_session_bg_disconnect_message))
            .setPositiveButton(getString(R.string.settings_enabled), (dialog, which) -> {
                manager.setAutoDisconnectOnBackground(true);
                recreate();
            })
            .setNegativeButton(getString(R.string.settings_disabled), (dialog, which) -> {
                manager.setAutoDisconnectOnBackground(false);
                recreate();
            })
            .setNeutralButton(getString(R.string.action_cancel), null)
            .show();
    }

    private void showBackgroundDelayDialog() {
        SessionPersistenceManager manager = SessionPersistenceManager.getInstance(this);
        
        EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_NUMBER);
        input.setText(String.valueOf(manager.getBackgroundDisconnectDelay()));
        
        new AlertDialog.Builder(this)
            .setTitle(getString(R.string.settings_session_bg_delay_title))
            .setView(input)
            .setPositiveButton(getString(R.string.action_save), (dialog, which) -> {
                try {
                    int seconds = Integer.parseInt(input.getText().toString());
                    manager.setBackgroundDisconnectDelay(seconds);
                    updateSessionPolicySummaries();
                } catch (NumberFormatException e) {
                    Toast.makeText(this, getString(R.string.msg_invalid_number), Toast.LENGTH_SHORT).show();
                }
            })
            .setNegativeButton(getString(R.string.action_cancel), null)
            .show();
    }

    private void showKeepAliveIntervalDialog() {
        SessionPersistenceManager manager = SessionPersistenceManager.getInstance(this);
        
        EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_NUMBER);
        input.setText(String.valueOf(manager.getKeepAliveInterval()));
        
        new AlertDialog.Builder(this)
            .setTitle(getString(R.string.settings_session_keepalive_interval_title))
            .setView(input)
            .setPositiveButton(getString(R.string.action_save), (dialog, which) -> {
                try {
                    int seconds = Integer.parseInt(input.getText().toString());
                    manager.setKeepAliveInterval(seconds);
                    updateSessionPolicySummaries();
                } catch (NumberFormatException e) {
                    Toast.makeText(this, getString(R.string.msg_invalid_number), Toast.LENGTH_SHORT).show();
                }
            })
            .setNegativeButton(getString(R.string.action_cancel), null)
            .show();
    }

    private void showResetSessionPolicyDialog() {
        new AlertDialog.Builder(this)
            .setTitle(getString(R.string.settings_session_reset_title))
            .setMessage(getString(R.string.settings_session_reset_confirm))
            .setPositiveButton(getString(R.string.action_reset_all), (dialog, which) -> {
                SessionPersistenceManager manager = SessionPersistenceManager.getInstance(this);
                manager.resetToDefaults();
                Toast.makeText(this, getString(R.string.action_reset_done), Toast.LENGTH_SHORT).show();
                recreate();
            })
            .setNegativeButton(getString(R.string.action_cancel), null)
            .show();
    }

    private void updateSessionPolicySummaries() {
        // 重建页面以更新所有摘要
        recreate();
    }

    // ==================== 备份与恢复相关方法 ====================

    private void createFullBackup() {
        BackupRestoreManager manager = new BackupRestoreManager(this);
        
        // 选择保存位置
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/json");
        intent.putExtra(Intent.EXTRA_TITLE, "orcterm_backup_" + getTimestamp() + ".json");
        backupCreateLauncher.launch(intent);
    }

    private void restoreFullBackup() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/json");
        backupRestoreLauncher.launch(intent);
    }

    private void exportHostsAsCsv() {
        BackupRestoreManager manager = new BackupRestoreManager(this);
        
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("text/csv");
        intent.putExtra(Intent.EXTRA_TITLE, "orcterm_hosts_" + getTimestamp() + ".csv");
        csvExportLauncher.launch(intent);
    }

    private void importHostsFromCsv() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("text/*");
        csvImportLauncher.launch(intent);
    }

    private String getTimestamp() {
        java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.getDefault());
        return sdf.format(new java.util.Date());
    }

    private void updateCloudStatus() {
        if (cloudServerValue == null) return;
        String server = prefs.getString("cloud_sync_server", null);
        long last = prefs.getLong("cloud_sync_last_pull", 0);
        cloudServerValue.setText(server == null ? getString(R.string.settings_cloud_unbound) : server);
        if (last > 0) {
            String formatted = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(new Date(last));
            cloudLastSyncValue.setText(formatted);
        } else {
            cloudLastSyncValue.setText(getString(R.string.settings_cloud_never_sync));
        }
        int count = prefs.getInt("cloud_sync_item_count", 0);
        cloudItemsCountValue.setText(String.valueOf(count));
    }

    private void refreshCloudItems() {
        executor.execute(() -> {
            HostDao hostDao = AppDatabase.getDatabase(this).hostDao();
            List<HostEntity> hosts = hostDao.getAllHostsNow();
            mainHandler.post(() -> {
                cloudItemsContainer.removeAllViews();
                if (hosts.isEmpty()) {
                    cloudItemsEmpty.setVisibility(View.VISIBLE);
                } else {
                    cloudItemsEmpty.setVisibility(View.GONE);
                    for (HostEntity host : hosts) {
                        TextView item = new TextView(this);
                        String title = host.alias != null && !host.alias.isEmpty() ? host.alias : host.username + "@" + host.hostname;
                        String detail = host.username + "@" + host.hostname + ":" + host.port;
                        item.setText(title + "\n" + detail);
                        item.setPadding(16, 12, 16, 12);
                        cloudItemsContainer.addView(item);
                    }
                }
                prefs.edit().putInt("cloud_sync_item_count", hosts.size()).apply();
                updateCloudStatus();
            });
        });
    }

    public void startQrScan() {
        IntentIntegrator integrator = new IntentIntegrator(this);
        integrator.setOrientationLocked(false);
        integrator.setPrompt(getString(R.string.settings_cloud_scan_prompt));
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
        if (content.startsWith("orcterm://sync")) {
            handleCloudSyncUri(content);
        } else {
            Toast.makeText(this, getString(R.string.settings_cloud_scan_invalid), Toast.LENGTH_SHORT).show();
        }
    }

    private void handleCloudSyncUri(String content) {
        Uri uri = Uri.parse(content);
        String server = uri.getQueryParameter("server");
        String token = uri.getQueryParameter("token");
        if (server == null || token == null) {
            Toast.makeText(this, getString(R.string.settings_cloud_scan_invalid), Toast.LENGTH_SHORT).show();
            return;
        }
        exchangeQrToken(normalizeServer(server), token);
    }

    private void exchangeQrToken(String server, String oneTimeToken) {
        executor.execute(() -> {
            try {
                JSONObject req = new JSONObject();
                req.put("token", oneTimeToken);
                String resp = apiPost(server + "/auth/qr/exchange", null, req.toString());
                JSONObject obj = new JSONObject(resp);
                String jwt = obj.optString("token", null);
                long expiresAt = obj.optLong("expiresAt", 0);
                if (jwt == null || jwt.isEmpty()) {
                    throw new IllegalArgumentException("Invalid token response");
                }
                prefs.edit()
                    .putString("cloud_sync_server", server)
                    .putString("cloud_sync_token", jwt)
                    .putLong("cloud_sync_token_exp", expiresAt)
                    .apply();
                mainHandler.post(() -> {
                    Toast.makeText(this, getString(R.string.settings_cloud_bound), Toast.LENGTH_SHORT).show();
                    updateCloudStatus();
                });
            } catch (Exception e) {
                mainHandler.post(() -> Toast.makeText(this, getString(R.string.settings_cloud_bind_failed, e.getMessage()), Toast.LENGTH_LONG).show());
            }
        });
    }

    private String normalizeServer(String server) {
        if (server.startsWith("http://") || server.startsWith("https://")) return server;
        return "https://" + server;
    }

    public void runCloudSync() {
        executor.execute(this::runCloudSyncInternal);
    }

    private void runCloudSyncInternal() {
        String server = prefs.getString("cloud_sync_server", null);
        String token = prefs.getString("cloud_sync_token", null);
        if (server == null || token == null) {
            mainHandler.post(() -> Toast.makeText(this, getString(R.string.settings_cloud_need_bind), Toast.LENGTH_SHORT).show());
            return;
        }
        try {
            HostDao hostDao = AppDatabase.getDatabase(this).hostDao();
            long since = prefs.getLong("cloud_sync_last_pull", 0);
            String pullResp = apiGet(server + "/sync/pull?since=" + since, token);
            JSONObject pullObj = new JSONObject(pullResp);
            JSONArray items = pullObj.optJSONArray("items");
            if (items != null) {
                applyPulledItems(hostDao, items);
            }
            long serverTime = pullObj.optLong("serverTime", System.currentTimeMillis());
            prefs.edit().putLong("cloud_sync_last_pull", serverTime).apply();
            JSONArray changes = buildLocalChanges(hostDao);
            JSONObject pushReq = new JSONObject();
            pushReq.put("changes", changes);
            String pushResp = apiPost(server + "/sync/push", token, pushReq.toString());
            JSONObject pushObj = new JSONObject(pushResp);
            JSONArray accepted = pushObj.optJSONArray("accepted");
            if (accepted != null) {
                updateAcceptedItems(accepted);
            }
            JSONArray conflicts = pushObj.optJSONArray("conflicts");
            if (conflicts != null) {
                applyPulledItems(hostDao, conflicts);
            }
            mainHandler.post(() -> {
                Toast.makeText(this, getString(R.string.settings_cloud_sync_done), Toast.LENGTH_SHORT).show();
                refreshCloudItems();
            });
        } catch (Exception e) {
            mainHandler.post(() -> Toast.makeText(this, getString(R.string.settings_cloud_sync_failed, e.getMessage()), Toast.LENGTH_LONG).show());
        }
    }

    private JSONArray buildLocalChanges(HostDao hostDao) throws Exception {
        List<HostEntity> hosts = hostDao.getAllHostsNow();
        JSONArray changes = new JSONArray();
        long now = System.currentTimeMillis();
        for (HostEntity host : hosts) {
            String itemId = buildHostItemId(host.hostname, host.port, host.username);
            JSONObject payload = hostToPayload(host);
            String payloadString = payload.toString();
            String hash = hashPayload(payloadString);
            String hashKey = buildSyncHashKey("host", itemId);
            String tsKey = buildSyncTsKey("host", itemId);
            String oldHash = prefs.getString(hashKey, null);
            long updatedAt = prefs.getLong(tsKey, 0);
            if (oldHash == null || !oldHash.equals(hash)) {
                updatedAt = now;
            } else if (updatedAt == 0) {
                updatedAt = now;
            }
            JSONObject change = new JSONObject();
            change.put("type", "host");
            change.put("itemId", itemId);
            change.put("payload", payloadString);
            change.put("updatedAt", updatedAt);
            change.put("deleted", false);
            changes.put(change);
        }
        String themeJson = prefs.getString("terminal_theme_json", null);
        if (themeJson != null && !themeJson.isEmpty()) {
            String normalized = normalizeThemeJson(themeJson);
            if (normalized != null && !normalized.isEmpty()) {
                String themeId = getThemeIdFromJson(normalized);
                if (themeId == null || themeId.isEmpty()) {
                    themeId = "default";
                }
                String hash = hashPayload(normalized);
                String hashKey = buildSyncHashKey("theme", themeId);
                String tsKey = buildSyncTsKey("theme", themeId);
                String oldHash = prefs.getString(hashKey, null);
                long updatedAt = prefs.getLong(tsKey, 0);
                if (oldHash == null || !oldHash.equals(hash)) {
                    updatedAt = now;
                } else if (updatedAt == 0) {
                    updatedAt = now;
                }
                JSONObject change = new JSONObject();
                change.put("type", "theme");
                change.put("itemId", themeId);
                change.put("payload", normalized);
                change.put("updatedAt", updatedAt);
                change.put("deleted", false);
                changes.put(change);
            }
        }
        return changes;
    }

    private void applyPulledItems(HostDao hostDao, JSONArray items) throws Exception {
        for (int i = 0; i < items.length(); i++) {
            JSONObject item = items.getJSONObject(i);
            String type = item.optString("type", "");
            if (!"host".equals(type) && !"theme".equals(type)) {
                continue;
            }
            String itemId = item.optString("itemId", "");
            boolean deleted = item.optBoolean("deleted", false);
            long updatedAt = item.optLong("updatedAt", 0);
            if ("host".equals(type) && deleted) {
                HostIdentity identity = parseHostItemId(itemId);
                if (identity != null) {
                    HostEntity existing = hostDao.findByIdentity(identity.hostname, identity.port, identity.username);
                    if (existing != null) {
                        hostDao.delete(existing);
                    }
                }
                prefs.edit().putLong(buildSyncTsKey(type, itemId), updatedAt).apply();
                continue;
            }
            if ("theme".equals(type) && deleted) {
                String themeId = prefs.getString("terminal_theme_id", "");
                if (itemId.isEmpty() || itemId.equals(themeId)) {
                    prefs.edit()
                        .remove("terminal_theme_json")
                        .remove("terminal_theme_id")
                        .apply();
                }
                prefs.edit().putLong(buildSyncTsKey(type, itemId), updatedAt).apply();
                continue;
            }
            String payload = item.optString("payload", null);
            if (payload == null) {
                continue;
            }
            if ("host".equals(type)) {
                JSONObject obj = new JSONObject(payload);
                String hostname = obj.optString("hostname", "");
                int port = obj.optInt("port", 22);
                String username = obj.optString("username", "");
                HostEntity existing = hostDao.findByIdentity(hostname, port, username);
                HostEntity entity = existing != null ? existing : new HostEntity();
                entity.alias = obj.optString("alias", entity.alias);
                entity.hostname = hostname;
                entity.port = port;
                entity.username = username;
                entity.password = obj.optString("password", entity.password);
                entity.authType = obj.optInt("authType", entity.authType);
                entity.osName = obj.optString("osName", entity.osName);
                entity.osVersion = obj.optString("osVersion", entity.osVersion);
                entity.status = obj.optString("status", entity.status);
                if (existing == null) {
                    hostDao.insert(entity);
                } else {
                    hostDao.update(entity);
                }
                String hash = hashPayload(payload);
                prefs.edit()
                    .putString(buildSyncHashKey(type, itemId), hash)
                    .putLong(buildSyncTsKey(type, itemId), updatedAt)
                    .apply();
            } else if ("theme".equals(type)) {
                String normalized = normalizeThemeJson(payload);
                if (normalized == null || normalized.isEmpty()) {
                    continue;
                }
                String themeId = getThemeIdFromJson(normalized);
                if (themeId == null || themeId.isEmpty()) {
                    themeId = itemId;
                }
                prefs.edit()
                    .putString("terminal_theme_json", normalized)
                    .putString("terminal_theme_id", themeId)
                    .putString(buildSyncHashKey(type, itemId), hashPayload(normalized))
                    .putLong(buildSyncTsKey(type, itemId), updatedAt)
                    .apply();
            }
        }
    }

    private void updateAcceptedItems(JSONArray accepted) throws Exception {
        for (int i = 0; i < accepted.length(); i++) {
            JSONObject item = accepted.getJSONObject(i);
            String itemId = item.optString("itemId", "");
            long updatedAt = item.optLong("updatedAt", 0);
            String payload = item.optString("payload", "");
            String type = item.optString("type", "host");
            if (!itemId.isEmpty() && !payload.isEmpty()) {
                prefs.edit()
                    .putString(buildSyncHashKey(type, itemId), hashPayload(payload))
                    .putLong(buildSyncTsKey(type, itemId), updatedAt)
                    .apply();
            }
        }
    }

    private JSONObject hostToPayload(HostEntity host) throws Exception {
        JSONObject obj = new JSONObject();
        obj.put("alias", host.alias);
        obj.put("hostname", host.hostname);
        obj.put("port", host.port);
        obj.put("username", host.username);
        obj.put("password", host.password);
        obj.put("authType", host.authType);
        obj.put("osName", host.osName);
        obj.put("osVersion", host.osVersion);
        obj.put("status", host.status);
        return obj;
    }

    private String buildHostItemId(String hostname, int port, String username) {
        return hostname + "|" + port + "|" + username;
    }

    private HostIdentity parseHostItemId(String itemId) {
        if (itemId == null) {
            return null;
        }
        String[] parts = itemId.split("\\|", 3);
        if (parts.length < 3) {
            return null;
        }
        HostIdentity identity = new HostIdentity();
        identity.hostname = parts[0];
        try {
            identity.port = Integer.parseInt(parts[1]);
        } catch (Exception e) {
            identity.port = 22;
        }
        identity.username = parts[2];
        return identity;
    }

    private String hashPayload(String payload) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hash = digest.digest(payload.getBytes(StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder();
        for (byte b : hash) {
            String hex = Integer.toHexString(b & 0xff);
            if (hex.length() == 1) {
                sb.append('0');
            }
            sb.append(hex);
        }
        return sb.toString();
    }

    private String buildSyncHashKey(String type, String itemId) {
        if ("host".equals(type)) {
            return "cloud_sync_hash_" + itemId;
        }
        return "cloud_sync_hash_" + type + "_" + itemId;
    }

    private String buildSyncTsKey(String type, String itemId) {
        if ("host".equals(type)) {
            return "cloud_sync_ts_" + itemId;
        }
        return "cloud_sync_ts_" + type + "_" + itemId;
    }

    private String normalizeThemeJson(String json) {
        try {
            Object parsed = new JSONTokener(json).nextValue();
            if (!(parsed instanceof JSONObject)) {
                return null;
            }
            JSONObject obj = (JSONObject) parsed;
            String id = obj.optString("id", "");
            if (id.isEmpty()) {
                id = prefs.getString("terminal_theme_id", "");
                if (id == null || id.isEmpty()) {
                    id = java.util.UUID.randomUUID().toString();
                }
                obj.put("id", id);
            }
            prefs.edit().putString("terminal_theme_id", id).apply();
            return obj.toString();
        } catch (Exception e) {
            return null;
        }
    }

    private String getThemeIdFromJson(String json) {
        try {
            Object parsed = new JSONTokener(json).nextValue();
            if (parsed instanceof JSONObject) {
                return ((JSONObject) parsed).optString("id", "");
            }
        } catch (Exception ignored) {
        }
        return "";
    }

    private String apiGet(String url, String token) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setRequestMethod("GET");
        conn.setConnectTimeout(15000);
        conn.setReadTimeout(20000);
        if (token != null) {
            conn.setRequestProperty("Authorization", "Bearer " + token);
        }
        int code = conn.getResponseCode();
        InputStream in = code >= 200 && code < 300 ? conn.getInputStream() : conn.getErrorStream();
        String resp = readAll(in);
        if (code < 200 || code >= 300) throw new java.io.IOException(code + ": " + resp);
        return resp;
    }

    private String apiPost(String url, String token, String body) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setRequestMethod("POST");
        conn.setConnectTimeout(15000);
        conn.setReadTimeout(20000);
        conn.setDoOutput(true);
        conn.setRequestProperty("Content-Type", "application/json");
        if (token != null) {
            conn.setRequestProperty("Authorization", "Bearer " + token);
        }
        try (OutputStream out = conn.getOutputStream()) {
            out.write(body.getBytes(StandardCharsets.UTF_8));
        }
        int code = conn.getResponseCode();
        InputStream in = code >= 200 && code < 300 ? conn.getInputStream() : conn.getErrorStream();
        String resp = readAll(in);
        if (code < 200 || code >= 300) throw new java.io.IOException(code + ": " + resp);
        return resp;
    }

    private String readAll(InputStream in) throws Exception {
        if (in == null) return "";
        java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.InputStreamReader(in));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            sb.append(line);
        }
        return sb.toString();
    }

    private void updatePersistentNotificationState() {
        if (persistentNotificationSwitch == null) return;
        boolean active = prefs.getBoolean("persistent_notification_active", false);
        boolean enabled = prefs.getBoolean("persistent_notification_enabled", false);
        persistentNotificationSwitch.setChecked(enabled);
        if (!active) {
            persistentNotificationSummary.setText(getString(R.string.settings_notification_persistent_inactive));
        } else {
            persistentNotificationSummary.setText(getString(R.string.settings_notification_persistent_summary));
        }
    }

    private void showSshKeyManager() {
        java.io.File filesDir = getFilesDir();
        java.io.File[] keys = filesDir.listFiles((dir, name) -> name.endsWith(".pub") || !name.contains(".") || name.endsWith(".key"));
        ArrayList<String> list = new ArrayList<>();
        ArrayList<java.io.File> fileList = new ArrayList<>();
        if (keys != null) {
            for (java.io.File f : keys) {
                list.add(f.getName());
                fileList.add(f);
            }
        }
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        TextView hint = new TextView(this);
        hint.setText(getString(R.string.settings_ssh_key_dir, filesDir.getAbsolutePath()));
        hint.setPadding(24, 16, 24, 16);
        layout.addView(hint);
        android.widget.ListView lv = new android.widget.ListView(this);
        lv.setAdapter(new android.widget.ArrayAdapter<>(this, android.R.layout.simple_list_item_1, list));
        lv.setOnItemClickListener((parent, view, position, id) -> {
            java.io.File target = fileList.get(position);
            new AlertDialog.Builder(this)
                .setTitle(target.getName())
                .setItems(new String[]{getString(R.string.action_copy_path), getString(R.string.action_delete)}, (d, which) -> {
                    if (which == 0) {
                        ClipboardManager cb = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                        cb.setPrimaryClip(ClipData.newPlainText("path", target.getAbsolutePath()));
                        Toast.makeText(this, getString(R.string.action_copied), Toast.LENGTH_SHORT).show();
                    } else {
                        new AlertDialog.Builder(this)
                            .setTitle(getString(R.string.action_delete_key))
                            .setMessage(getString(R.string.settings_ssh_key_delete_confirm, target.getName()))
                            .setPositiveButton(getString(R.string.action_delete), (d2, w2) -> {
                                if (target.delete()) {
                                    Toast.makeText(this, getString(R.string.action_deleted), Toast.LENGTH_SHORT).show();
                                } else {
                                    Toast.makeText(this, getString(R.string.action_delete_failed), Toast.LENGTH_SHORT).show();
                                }
                            })
                            .setNegativeButton(getString(R.string.action_cancel), null)
                            .show();
                    }
                })
                .show();
        });
        layout.addView(lv);
        new AlertDialog.Builder(this)
            .setTitle(getString(R.string.settings_ssh_keys_title))
            .setView(layout)
            .setPositiveButton(getString(R.string.action_generate_key), (d, w) -> {
                try {
                    com.orcterm.core.ssh.SshNative ssh = new com.orcterm.core.ssh.SshNative();
                    String alias = "key_" + System.currentTimeMillis();
                    java.io.File priv = new java.io.File(filesDir, alias);
                    int ret = ssh.generateKeyPair(priv.getAbsolutePath());
                    if (ret == 0) {
                        Toast.makeText(this, getString(R.string.settings_ssh_key_generated, alias), Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(this, getString(R.string.settings_ssh_key_generate_failed), Toast.LENGTH_SHORT).show();
                    }
                } catch (Exception e) {
                    Toast.makeText(this, getString(R.string.error_prefix, e.getMessage()), Toast.LENGTH_LONG).show();
                }
            })
            .setNeutralButton(getString(R.string.action_import_key), (d, w) -> keyPickerLauncher.launch("*/*"))
            .setNegativeButton(getString(R.string.action_close), null)
            .show();
    }

    private void importKeyFromUri(Uri uri) {
        try {
            String name = "key_" + System.currentTimeMillis();
            Cursor cursor = getContentResolver().query(uri, null, null, null, null);
            if (cursor != null) {
                int nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (cursor.moveToFirst() && nameIndex >= 0) {
                    name = cursor.getString(nameIndex);
                }
                cursor.close();
            }
            java.io.File target = new java.io.File(getFilesDir(), name);
            try (InputStream in = getContentResolver().openInputStream(uri);
                 OutputStream out = new java.io.FileOutputStream(target)) {
                byte[] buffer = new byte[8192];
                int len;
                while (in != null && (len = in.read(buffer)) > 0) {
                    out.write(buffer, 0, len);
                }
            }
            Toast.makeText(this, getString(R.string.settings_ssh_key_imported, name), Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(this, getString(R.string.settings_ssh_key_import_failed, e.getMessage()), Toast.LENGTH_SHORT).show();
        }
    }

    private void pickBackgroundImage() {
        bgPickerLauncher.launch("image/*");
    }

    private void applyBackgroundFromUri(Uri uri) {
        try (InputStream in = getContentResolver().openInputStream(uri)) {
            if (in != null) {
                android.graphics.drawable.Drawable drawable = android.graphics.drawable.Drawable.createFromStream(in, uri.toString());
                View root = findViewById(android.R.id.content);
                root.setBackground(drawable);
            }
        } catch (Exception e) {
            Toast.makeText(this, getString(R.string.background_load_failed), Toast.LENGTH_SHORT).show();
        }
    }

    private void clearCaches() {
        try {
            java.io.File cache = getCacheDir();
            deleteDir(cache);
            java.io.File ext = getExternalCacheDir();
            if (ext != null) deleteDir(ext);
        } catch (Exception ignored) {}
    }

    private void deleteDir(java.io.File dir) {
        if (dir == null || !dir.exists()) return;
        java.io.File[] files = dir.listFiles();
        if (files != null) {
            for (java.io.File f : files) {
                if (f.isDirectory()) deleteDir(f);
                else f.delete();
            }
        }
        dir.delete();
    }

    private static class HostIdentity {
        String hostname;
        int port;
        String username;
    }
}
