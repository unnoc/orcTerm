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
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
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

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class SettingsDetailActivity extends AppCompatActivity {

    private SharedPreferences prefs;
    private LinearLayout contentContainer;
    private LayoutInflater inflater;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private ActivityResultLauncher<String> keyPickerLauncher;
    private ActivityResultLauncher<String> bgPickerLauncher;
    private TextView cloudServerValue;
    private TextView cloudLastSyncValue;
    private TextView cloudItemsCountValue;
    private LinearLayout cloudItemsContainer;
    private TextView cloudItemsEmpty;
    private View hostPreview;
    private Switch persistentNotificationSwitch;
    private TextView persistentNotificationSummary;

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
        if (page == null) page = "general";
        buildPage(page);
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateCloudStatus();
        updatePersistentNotificationState();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        executor.shutdownNow();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
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
    }

    private void buildPage(String page) {
        if ("ssh".equals(page)) {
            setTitle(getString(R.string.settings_ssh_manage_title));
            buildSshPage();
        } else if ("cloud_sync".equals(page)) {
            setTitle(getString(R.string.settings_cloud_sync_title));
            buildCloudSyncPage();
        } else if ("terminal".equals(page)) {
            setTitle(getString(R.string.settings_terminal_title));
            buildTerminalPage();
        } else if ("theme_display".equals(page)) {
            setTitle(getString(R.string.settings_theme_display_title));
            buildThemeDisplayPage();
        } else if ("file_management".equals(page)) {
            setTitle(getString(R.string.settings_file_management_title));
            buildFileManagementPage();
        } else {
            setTitle(getString(R.string.settings_general_title));
            buildGeneralPage();
        }
    }

    private void buildSshPage() {
        addItem(R.drawable.ic_action_computer, getString(R.string.settings_ssh_hosts_title), getString(R.string.settings_ssh_hosts_summary), v -> openServersTab());
        addDivider();
        addItem(R.drawable.ic_action_vpn_key, getString(R.string.settings_ssh_keys_title), getString(R.string.settings_ssh_keys_summary), v -> showSshKeyManager());
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
        addItem(R.drawable.ic_action_settings, getString(R.string.settings_terminal_font_title), getString(R.string.settings_terminal_font_summary), v -> showFontSizeDialog());
        addItem(R.drawable.ic_action_settings, getString(R.string.settings_terminal_theme_title), getString(R.string.settings_terminal_theme_summary), v -> showColorThemeDialog());
        addItem(R.drawable.ic_action_keyboard, getString(R.string.settings_terminal_keypad_title), getString(R.string.settings_terminal_keypad_summary), v -> showKeypadMappingDialog());
        addItem(R.drawable.ic_action_settings, getString(R.string.settings_terminal_behavior_title), getString(R.string.settings_terminal_behavior_summary), v -> showTerminalBehaviorDialog());
    }

    private void buildThemeDisplayPage() {
        View themeMode = addItem(R.drawable.ic_action_settings, getString(R.string.settings_theme_mode_title), "", v -> showThemeDialog());
        updateThemeSummary(themeMode);
        
        addItem(R.drawable.ic_action_settings, getString(R.string.settings_theme_bg_title), getString(R.string.settings_theme_bg_summary), v -> pickBackgroundImage());
        addItem(R.drawable.ic_action_settings, getString(R.string.settings_theme_density_title), getString(R.string.settings_theme_density_summary), v -> showDensityDialog());
        addItem(R.drawable.ic_action_settings, getString(R.string.settings_theme_list_style_title), getString(R.string.settings_theme_list_style_summary), v -> showHostDisplayStyleDialog());
        addHeader(getString(R.string.settings_theme_preview_header));
        hostPreview = inflater.inflate(R.layout.item_host, contentContainer, false);
        contentContainer.addView(hostPreview);
        updateHostPreview(prefs.getInt("host_display_style", 0));
    }

    private void buildFileManagementPage() {
        // Download Path
        String downloadPath = prefs.getString("file_download_path", "/storage/emulated/0/Download");
        addItem(R.drawable.ic_action_storage, getString(R.string.settings_file_download_path_title), downloadPath, v -> showPathDialog("file_download_path", downloadPath));

        // Sort Order
        addItem(R.drawable.ic_action_settings, getString(R.string.settings_file_sort_order_title), getString(R.string.settings_file_sort_order_summary), v -> showSortOrderDialog());

        // Show Hidden
        View hiddenItem = addItem(R.drawable.ic_action_settings, getString(R.string.settings_file_show_hidden_title), getString(R.string.settings_file_show_hidden_summary), null);
        Switch hiddenSwitch = hiddenItem.findViewById(R.id.switch_widget);
        hiddenSwitch.setVisibility(View.VISIBLE);
        hiddenItem.findViewById(R.id.chevron).setVisibility(View.GONE);
        hiddenSwitch.setChecked(prefs.getBoolean("file_show_hidden", false));
        hiddenSwitch.setOnCheckedChangeListener((btn, isChecked) -> prefs.edit().putBoolean("file_show_hidden", isChecked).apply());
        hiddenItem.setOnClickListener(v -> hiddenSwitch.toggle());

        // Icon Size
        addItem(R.drawable.ic_action_settings, getString(R.string.settings_file_icon_size_title), getString(R.string.settings_file_icon_size_summary), v -> showIconSizeDialog());

        // Unzip Path
        String unzipPath = prefs.getString("file_unzip_path", "/storage/emulated/0/Download");
        addItem(R.drawable.ic_action_storage, getString(R.string.settings_file_unzip_path_title), unzipPath, v -> showPathDialog("file_unzip_path", unzipPath));

        // Editor Font
        addItem(R.drawable.ic_action_settings, getString(R.string.settings_file_editor_font_title), getString(R.string.settings_file_editor_font_summary), v -> showEditorFontDialog());
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

    private void updateThemeSummary(View item) {
        TextView summary = item.findViewById(R.id.summary);
        int current = prefs.getInt("theme_mode", AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
        if (current == AppCompatDelegate.MODE_NIGHT_NO) summary.setText(getString(R.string.settings_theme_mode_light));
        else if (current == AppCompatDelegate.MODE_NIGHT_YES) summary.setText(getString(R.string.settings_theme_mode_dark));
        else summary.setText(getString(R.string.settings_language_system));
        summary.setVisibility(View.VISIBLE);
    }

    private void buildGeneralPage() {
        addItem(R.drawable.ic_action_settings, getString(R.string.settings_language_title), getString(R.string.settings_language_summary), v -> showLanguageDialog());
        addHeader(getString(R.string.settings_notifications_header));
        View persistent = addItem(R.drawable.ic_action_computer, getString(R.string.settings_notification_persistent_title), getString(R.string.settings_notification_persistent_summary), null);
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
        addItem(R.drawable.ic_action_settings, getString(R.string.settings_notification_system_title), getString(R.string.settings_notification_system_summary), v -> openNotificationSettings());
        addDivider();
        addItem(R.drawable.ic_action_storage, getString(R.string.settings_clear_cache_title), getString(R.string.settings_clear_cache_summary), v -> confirmClearCache());
        addItem(android.R.drawable.ic_menu_info_details, getString(R.string.settings_about_title), getString(R.string.settings_about_summary), v -> showAboutDialog());
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

    private void addHeader(String title) {
        TextView header = new TextView(new android.view.ContextThemeWrapper(this, R.style.SettingsHeader));
        header.setText(title);
        contentContainer.addView(header);
    }

    private void addDivider() {
        View divider = new View(this, null, 0, R.style.SettingsDivider);
        contentContainer.addView(divider);
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
            prefs.getBoolean("terminal_local_echo", false)
        };
        new AlertDialog.Builder(this)
            .setTitle(getString(R.string.settings_terminal_behavior_title))
            .setMultiChoiceItems(items, checked, (dialog, which, isChecked) -> checked[which] = isChecked)
            .setPositiveButton(getString(R.string.action_save), (d, w) -> {
                prefs.edit()
                    .putBoolean("terminal_enter_newline", checked[0])
                    .putBoolean("terminal_local_echo", checked[1])
                    .apply();
            })
            .setNegativeButton(getString(R.string.action_cancel), null)
            .show();
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
            String hashKey = "cloud_sync_hash_" + itemId;
            String tsKey = "cloud_sync_ts_" + itemId;
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
        return changes;
    }

    private void applyPulledItems(HostDao hostDao, JSONArray items) throws Exception {
        for (int i = 0; i < items.length(); i++) {
            JSONObject item = items.getJSONObject(i);
            String type = item.optString("type", "");
            if (!"host".equals(type)) {
                continue;
            }
            String itemId = item.optString("itemId", "");
            boolean deleted = item.optBoolean("deleted", false);
            long updatedAt = item.optLong("updatedAt", 0);
            if (deleted) {
                HostIdentity identity = parseHostItemId(itemId);
                if (identity != null) {
                    HostEntity existing = hostDao.findByIdentity(identity.hostname, identity.port, identity.username);
                    if (existing != null) {
                        hostDao.delete(existing);
                    }
                }
                prefs.edit().putLong("cloud_sync_ts_" + itemId, updatedAt).apply();
                continue;
            }
            String payload = item.optString("payload", null);
            if (payload == null) {
                continue;
            }
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
                .putString("cloud_sync_hash_" + itemId, hash)
                .putLong("cloud_sync_ts_" + itemId, updatedAt)
                .apply();
        }
    }

    private void updateAcceptedItems(JSONArray accepted) throws Exception {
        for (int i = 0; i < accepted.length(); i++) {
            JSONObject item = accepted.getJSONObject(i);
            String itemId = item.optString("itemId", "");
            long updatedAt = item.optLong("updatedAt", 0);
            String payload = item.optString("payload", "");
            if (!itemId.isEmpty() && !payload.isEmpty()) {
                prefs.edit()
                    .putString("cloud_sync_hash_" + itemId, hashPayload(payload))
                    .putLong("cloud_sync_ts_" + itemId, updatedAt)
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
        persistentNotificationSwitch.setEnabled(active);
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
