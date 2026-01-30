package com.orcterm.ui.nav;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.fragment.app.Fragment;

import com.orcterm.R;

public class SettingsFragment extends Fragment {

    private SharedPreferences prefs;
    private View rootView;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_settings, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        prefs = requireContext().getSharedPreferences("orcterm_prefs", Context.MODE_PRIVATE);
        rootView = view;
        initSettings(view);
        updateMainSummaries(view);
        applyBackgroundFromPrefs();
    }

    @Override
    public void onResume() {
        super.onResume();
        if (rootView != null) {
            updateMainSummaries(rootView);
            applyBackgroundFromPrefs();
        }
    }

    private void initSettings(View view) {
        setupItem(view, R.id.setting_ssh_manage, R.drawable.ic_action_computer, getString(R.string.settings_main_ssh_title), getString(R.string.settings_main_ssh_summary), v -> openDetail("ssh"));

        setupItem(view, R.id.setting_cloud_sync, R.drawable.ic_action_cloud_upload, getString(R.string.settings_main_cloud_title), getString(R.string.settings_main_cloud_summary), v -> openDetail("cloud_sync"));

        setupItem(view, R.id.setting_file_management, R.drawable.ic_action_storage, getString(R.string.settings_main_file_title), getString(R.string.settings_main_file_summary), v -> openDetail("file_management"));

        setupItem(view, R.id.setting_terminal_settings, R.drawable.ic_action_terminal, getString(R.string.settings_main_terminal_title), getString(R.string.settings_main_terminal_summary), v -> openDetail("terminal"));

        setupItem(view, R.id.setting_theme_display, R.drawable.ic_action_settings, getString(R.string.settings_main_theme_display_title), getString(R.string.settings_main_theme_display_summary), v -> openDetail("theme_display"));

        setupItem(view, R.id.setting_general, R.drawable.ic_action_settings, getString(R.string.settings_main_general_title), getString(R.string.settings_main_general_summary), v -> openDetail("general"));
    }

    private void openDetail(String page) {
        Intent intent = new Intent(requireContext(), com.orcterm.ui.SettingsDetailActivity.class);
        intent.putExtra("page", page);
        startActivity(intent);
    }

    private void setupItem(View root, int itemId, int iconRes, String title, String summary, View.OnClickListener listener) {
        View item = root.findViewById(itemId);
        if (item == null) return;
        
        ImageView icon = item.findViewById(R.id.icon);
        TextView tvTitle = item.findViewById(R.id.title);
        TextView tvSummary = item.findViewById(R.id.summary);

        try {
            icon.setImageResource(iconRes);
        } catch (Exception e) {
            icon.setImageResource(android.R.drawable.ic_menu_manage);
        }
        
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
    }

    private void showFontSizeDialog() {
        String[] sizes = {"小", "标准", "大", "特大"};
        int current = prefs.getInt("font_size_index", 1);
        new AlertDialog.Builder(requireContext())
            .setTitle("终端字体大小")
            .setSingleChoiceItems(sizes, current, (dialog, which) -> {
                prefs.edit().putInt("font_size_index", which).apply();
                dialog.dismiss();
                updateTerminalSettingSummaries(rootView);
            })
            .show();
    }

    private void showColorThemeDialog() {
        String[] themes = {"Default", "Solarized Dark", "Solarized Light", "Monokai"};
        int current = prefs.getInt("terminal_theme_index", 0);
        new AlertDialog.Builder(requireContext())
            .setTitle("终端配色方案")
            .setSingleChoiceItems(themes, current, (dialog, which) -> {
                prefs.edit().putInt("terminal_theme_index", which).apply();
                dialog.dismiss();
                updateTerminalSettingSummaries(rootView);
            })
            .show();
    }
    
    private void showDensityDialog() {
        String[] densities = {"紧凑", "标准", "宽松"};
        int current = prefs.getInt("list_density", 1);
        new AlertDialog.Builder(requireContext())
            .setTitle("列表布局密度")
            .setSingleChoiceItems(densities, current, (dialog, which) -> {
                prefs.edit().putInt("list_density", which).apply();
                dialog.dismiss();
            })
            .show();
    }
    
    private void showTerminalBehaviorDialog() {
        String[] items = {"回车发送换行", "启用本地回显"};
        boolean[] checked = {
            prefs.getBoolean("terminal_enter_newline", true),
            prefs.getBoolean("terminal_local_echo", false)
        };
        new AlertDialog.Builder(requireContext())
            .setTitle("终端行为设置")
            .setMultiChoiceItems(items, checked, (dialog, which, isChecked) -> checked[which] = isChecked)
            .setPositiveButton("保存", (d, w) -> {
                prefs.edit()
                    .putBoolean("terminal_enter_newline", checked[0])
                    .putBoolean("terminal_local_echo", checked[1])
                    .apply();
            })
            .setNegativeButton("取消", null)
            .show();
    }

    private void showKeypadMappingDialog() {
        String[] labels = {"ESC", "TAB", "CTRL", "ALT", "META", "F1", "F2", "F3", "F4", "F5", "F6", "F7", "F8", "F9", "F10", "HOME", "END", "PGUP", "PGDN", "INS", "DEL", "↑", "↓", "←", "→", "CTRL+C", "CTRL+Z", "CTRL+D", "CTRL+L", "|", "/", "-", "=", "~", "[", "]", "{", "}", "<", ">", "&", "+", "*", "%", "`"};
        java.util.Map<String, String> mappings = readKeypadMapping();
        String[] values = new String[labels.length];
        for (int i = 0; i < labels.length; i++) {
            values[i] = mappings.get(labels[i]);
        }
        new AlertDialog.Builder(requireContext())
            .setTitle("键盘映射")
            .setItems(labels, (d, which) -> showEditKeypadMapping(labels[which], values[which]))
            .setNeutralButton("重置全部", (d, w) -> {
                prefs.edit().remove("terminal_keypad_mapping").apply();
                Toast.makeText(requireContext(), "已重置", Toast.LENGTH_SHORT).show();
                updateTerminalSettingSummaries(rootView);
            })
            .setNegativeButton("关闭", null)
            .show();
    }

    private void showEditKeypadMapping(String label, String current) {
        android.widget.LinearLayout layout = new android.widget.LinearLayout(requireContext());
        layout.setOrientation(android.widget.LinearLayout.VERTICAL);
        layout.setPadding(32, 24, 32, 8);
        android.widget.TextView hint = new android.widget.TextView(requireContext());
        hint.setText("输入字符串或转义序列");
        android.widget.EditText edit = new android.widget.EditText(requireContext());
        edit.setText(current != null ? current : "");
        layout.addView(hint);
        layout.addView(edit);
        new AlertDialog.Builder(requireContext())
            .setTitle("映射: " + label)
            .setView(layout)
            .setPositiveButton("保存", (d, w) -> {
                java.util.Map<String, String> mapping = readKeypadMapping();
                String value = edit.getText().toString();
                if (value.isEmpty()) {
                    mapping.remove(label);
                } else {
                    mapping.put(label, value);
                }
                persistKeypadMapping(mapping);
            })
            .setNegativeButton("取消", null)
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
        Toast.makeText(requireContext(), "已保存", Toast.LENGTH_SHORT).show();
        updateTerminalSettingSummaries(rootView);
    }

    private void updateTerminalSettingSummaries(View view) {
        if (view == null) return;
        updateMainSummaries(view);
    }

    private void updateMainSummaries(View view) {
        if (view == null) return;
        View ssh = view.findViewById(R.id.setting_ssh_manage);
        View cloud = view.findViewById(R.id.setting_cloud_sync);
        View file = view.findViewById(R.id.setting_file_management);
        View terminal = view.findViewById(R.id.setting_terminal_settings);
        View theme = view.findViewById(R.id.setting_theme_display);
        View general = view.findViewById(R.id.setting_general);
        if (ssh != null) {
            TextView summary = ssh.findViewById(R.id.summary);
            summary.setText(getString(R.string.settings_main_ssh_summary));
            summary.setVisibility(View.VISIBLE);
        }
        if (cloud != null) {
            TextView summary = cloud.findViewById(R.id.summary);
            String server = prefs.getString("cloud_sync_server", null);
            if (server == null || server.isEmpty()) {
                summary.setText(getString(R.string.settings_main_cloud_summary_unbound));
            } else {
                summary.setText(getString(R.string.settings_main_cloud_summary_bound, server));
            }
            summary.setVisibility(View.VISIBLE);
        }
        if (file != null) {
            TextView summary = file.findViewById(R.id.summary);
            summary.setText(getString(R.string.settings_main_file_summary));
            summary.setVisibility(View.VISIBLE);
        }
        if (terminal != null) {
            TextView summary = terminal.findViewById(R.id.summary);
            summary.setText(getString(R.string.settings_main_terminal_summary));
            summary.setVisibility(View.VISIBLE);
        }
        if (theme != null) {
            TextView summary = theme.findViewById(R.id.summary);
            int currentMode = AppCompatDelegate.getDefaultNightMode();
            String modeLabel = currentMode == AppCompatDelegate.MODE_NIGHT_YES
                ? getString(R.string.settings_theme_mode_dark)
                : getString(R.string.settings_theme_mode_light);
            summary.setText(getString(R.string.settings_main_theme_display_summary_format, modeLabel));
            summary.setVisibility(View.VISIBLE);
        }
        if (general != null) {
            TextView summary = general.findViewById(R.id.summary);
            String lang = prefs.getString("app_language", "system");
            String label = getString(R.string.settings_language_system);
            if ("zh".equals(lang)) label = getString(R.string.settings_language_zh);
            else if ("en".equals(lang)) label = getString(R.string.settings_language_en);
            summary.setText(getString(R.string.settings_main_general_summary_format, label));
            summary.setVisibility(View.VISIBLE);
        }
    }
    
    private void openLanguageSettings() {
        try {
            startActivity(new Intent(Settings.ACTION_LOCALE_SETTINGS));
        } catch (Exception e) {
            Toast.makeText(requireContext(), getString(R.string.settings_language_open_failed), Toast.LENGTH_SHORT).show();
        }
    }
    
    private void openNotificationSettings() {
        try {
            Intent intent;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                intent = new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, requireContext().getPackageName());
            } else {
                intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                    .setData(Uri.parse("package:" + requireContext().getPackageName()));
            }
            startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(requireContext(), getString(R.string.settings_notification_open_failed), Toast.LENGTH_SHORT).show();
        }
    }
    
    private void showSshKeyManager() {
        java.io.File filesDir = requireContext().getFilesDir();
        java.io.File[] keys = filesDir.listFiles((dir, name) -> name.endsWith(".pub") || !name.contains(".") || name.endsWith(".key"));
        java.util.ArrayList<String> list = new java.util.ArrayList<>();
        java.util.ArrayList<java.io.File> fileList = new java.util.ArrayList<>();
        if (keys != null) {
            for (java.io.File f : keys) {
                list.add(f.getName());
                fileList.add(f);
            }
        }
        android.widget.LinearLayout layout = new android.widget.LinearLayout(requireContext());
        layout.setOrientation(android.widget.LinearLayout.VERTICAL);
        android.widget.TextView hint = new android.widget.TextView(requireContext());
        hint.setText("密钥存储目录: " + filesDir.getAbsolutePath());
        hint.setPadding(24, 16, 24, 16);
        layout.addView(hint);
        android.widget.ListView lv = new android.widget.ListView(requireContext());
        lv.setAdapter(new android.widget.ArrayAdapter<>(requireContext(), android.R.layout.simple_list_item_1, list));
        lv.setOnItemClickListener((parent, view, position, id) -> {
            java.io.File target = fileList.get(position);
            new AlertDialog.Builder(requireContext())
                .setTitle(target.getName())
                .setItems(new String[]{"复制路径", "删除"}, (d, which) -> {
                    if (which == 0) {
                        ClipboardManager cb = (ClipboardManager) requireContext().getSystemService(Context.CLIPBOARD_SERVICE);
                        cb.setPrimaryClip(ClipData.newPlainText("path", target.getAbsolutePath()));
                        Toast.makeText(requireContext(), "路径已复制", Toast.LENGTH_SHORT).show();
                    } else {
                        new AlertDialog.Builder(requireContext())
                            .setTitle("删除密钥")
                            .setMessage("确定删除 " + target.getName() + " 吗？")
                            .setPositiveButton("删除", (d2, w2) -> {
                                if (target.delete()) {
                                    Toast.makeText(requireContext(), "已删除", Toast.LENGTH_SHORT).show();
                                } else {
                                    Toast.makeText(requireContext(), "删除失败", Toast.LENGTH_SHORT).show();
                                }
                            })
                            .setNegativeButton("取消", null)
                            .show();
                    }
                })
                .show();
        });
        layout.addView(lv);
        
        new AlertDialog.Builder(requireContext())
            .setTitle("SSH 密钥管理")
            .setView(layout)
            .setPositiveButton("生成新密钥", (d, w) -> {
                try {
                    com.orcterm.core.ssh.SshNative ssh = new com.orcterm.core.ssh.SshNative();
                    String alias = "key_" + System.currentTimeMillis();
                    java.io.File priv = new java.io.File(filesDir, alias);
                    int ret = ssh.generateKeyPair(priv.getAbsolutePath());
                    if (ret == 0) {
                        Toast.makeText(requireContext(), "密钥已生成: " + alias, Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(requireContext(), "密钥生成失败", Toast.LENGTH_SHORT).show();
                    }
                } catch (Exception e) {
                    Toast.makeText(requireContext(), "错误: " + e.getMessage(), Toast.LENGTH_LONG).show();
                }
            })
            .setNeutralButton("导入密钥", (d, w) -> keyPickerLauncher.launch("*/*"))
            .setNegativeButton("关闭", null)
            .show();
    }
    
    private final androidx.activity.result.ActivityResultLauncher<String> keyPickerLauncher =
        registerForActivityResult(new androidx.activity.result.contract.ActivityResultContracts.GetContent(), uri -> {
            if (uri != null) importKeyFromUri(uri);
        });
    
    private final androidx.activity.result.ActivityResultLauncher<String> bgPickerLauncher =
        registerForActivityResult(new androidx.activity.result.contract.ActivityResultContracts.GetContent(), uri -> {
            if (uri != null) {
                prefs.edit().putString("app_bg_uri", uri.toString()).apply();
                applyBackgroundFromUri(uri);
                Toast.makeText(requireContext(), "背景已设置", Toast.LENGTH_SHORT).show();
            }
        });
    
    private void pickBackgroundImage() {
        bgPickerLauncher.launch("image/*");
    }
    
    private void importKeyFromUri(Uri uri) {
        try {
            String name = "key_" + System.currentTimeMillis();
            Cursor cursor = requireContext().getContentResolver().query(uri, null, null, null, null);
            if (cursor != null) {
                int nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (cursor.moveToFirst() && nameIndex >= 0) {
                    name = cursor.getString(nameIndex);
                }
                cursor.close();
            }
            java.io.File target = new java.io.File(requireContext().getFilesDir(), name);
            try (java.io.InputStream in = requireContext().getContentResolver().openInputStream(uri);
                 java.io.OutputStream out = new java.io.FileOutputStream(target)) {
                byte[] buffer = new byte[8192];
                int len;
                while (in != null && (len = in.read(buffer)) > 0) {
                    out.write(buffer, 0, len);
                }
            }
            Toast.makeText(requireContext(), "已导入: " + name, Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(requireContext(), "导入失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }
    
    private void applyBackgroundFromPrefs() {
        String uriStr = prefs.getString("app_bg_uri", null);
        if (uriStr != null) {
            applyBackgroundFromUri(Uri.parse(uriStr));
        }
    }
    
    private void applyBackgroundFromUri(Uri uri) {
        if (rootView == null) return;
        try (java.io.InputStream in = requireContext().getContentResolver().openInputStream(uri)) {
            if (in != null) {
                android.graphics.drawable.Drawable drawable = android.graphics.drawable.Drawable.createFromStream(in, uri.toString());
                rootView.setBackground(drawable);
            }
        } catch (Exception e) {
            Toast.makeText(requireContext(), "背景加载失败", Toast.LENGTH_SHORT).show();
        }
    }
    
    private void clearCaches() {
        try {
            java.io.File cache = requireContext().getCacheDir();
            deleteDir(cache);
            java.io.File ext = requireContext().getExternalCacheDir();
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
}
