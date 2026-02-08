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
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
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

/**
 * 设置主页入口与概览
 */
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
        EditText searchInput = view.findViewById(R.id.settings_search_input);
        if (searchInput != null) {
            searchInput.setOnFocusChangeListener((v, hasFocus) -> {
                if (hasFocus) {
                    searchInput.clearFocus();
                    openSearch("");
                }
            });
            searchInput.setOnClickListener(v -> {
                searchInput.clearFocus();
                openSearch(searchInput.getText() == null ? "" : searchInput.getText().toString());
            });
            searchInput.setOnEditorActionListener((v, actionId, event) -> {
                if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                    openSearch(v.getText() == null ? "" : v.getText().toString());
                    return true;
                }
                return false;
            });
        }

        setupItem(view, R.id.setting_ssh_manage, R.drawable.ic_action_computer, getString(R.string.settings_main_ssh_title), getString(R.string.settings_main_ssh_summary), v -> openDetail("ssh"));

        setupItem(view, R.id.setting_terminal_settings, R.drawable.ic_action_terminal, getString(R.string.settings_main_terminal_title), getString(R.string.settings_main_terminal_summary), v -> openDetail("terminal"));

        setupItem(view, R.id.setting_theme_display, R.drawable.ic_action_settings, getString(R.string.settings_main_theme_display_title), getString(R.string.settings_main_theme_display_summary), v -> openDetail("theme_display"));

        setupItem(view, R.id.setting_session_policy, R.drawable.ic_action_computer, getString(R.string.settings_main_session_policy_title), getString(R.string.settings_main_session_policy_summary), v -> openDetail("session_policy"));

        setupItem(view, R.id.setting_cloud_sync, R.drawable.ic_action_cloud_upload, getString(R.string.settings_main_cloud_title), getString(R.string.settings_main_cloud_summary), v -> openDetail("cloud_sync"));

        setupItem(view, R.id.setting_file_management, R.drawable.ic_action_storage, getString(R.string.settings_main_file_title), getString(R.string.settings_main_file_summary), v -> openDetail("file_management"));

        setupItem(view, R.id.setting_backup, R.drawable.ic_action_storage, getString(R.string.settings_main_backup_title), getString(R.string.settings_main_backup_summary), v -> openDetail("backup"));

        setupItem(view, R.id.setting_general, R.drawable.ic_action_settings, getString(R.string.settings_main_general_title), getString(R.string.settings_main_general_summary), v -> openDetail("general"));
    }

    private void openDetail(String page) {
        Intent intent = new Intent(requireContext(), com.orcterm.ui.SettingsDetailActivity.class);
        intent.putExtra("page", page);
        startActivity(intent);
    }

    private void openSearch(String query) {
        Intent intent = new Intent(requireContext(), com.orcterm.ui.SettingsDetailActivity.class);
        intent.putExtra("page", "search");
        intent.putExtra("query", query);
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
        String[] sizes = getResources().getStringArray(R.array.font_size_options);
        int current = prefs.getInt("font_size_index", 1);
        new AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.settings_terminal_font_title))
            .setSingleChoiceItems(sizes, current, (dialog, which) -> {
                prefs.edit().putInt("font_size_index", which).apply();
                dialog.dismiss();
                updateTerminalSettingSummaries(rootView);
            })
            .show();
    }

    private void showColorThemeDialog() {
        String[] themes = getResources().getStringArray(R.array.terminal_themes);
        int current = prefs.getInt("terminal_theme_index", 0);
        new AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.settings_terminal_theme_title))
            .setSingleChoiceItems(themes, current, (dialog, which) -> {
                prefs.edit().putInt("terminal_theme_index", which).apply();
                dialog.dismiss();
                updateTerminalSettingSummaries(rootView);
            })
            .show();
    }
    
    private void showDensityDialog() {
        String[] densities = getResources().getStringArray(R.array.list_density_options);
        int current = prefs.getInt("list_density", 1);
        new AlertDialog.Builder(requireContext())
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
        new AlertDialog.Builder(requireContext())
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

    private void showKeypadMappingDialog() {
        String[] labels = getResources().getStringArray(R.array.keypad_mapping_labels);
        java.util.Map<String, String> mappings = readKeypadMapping();
        String[] values = new String[labels.length];
        for (int i = 0; i < labels.length; i++) {
            values[i] = mappings.get(labels[i]);
        }
        new AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.settings_terminal_keypad_title))
            .setItems(labels, (d, which) -> showEditKeypadMapping(labels[which], values[which]))
            .setNeutralButton(getString(R.string.action_reset_all), (d, w) -> {
                prefs.edit().remove("terminal_keypad_mapping").apply();
                Toast.makeText(requireContext(), getString(R.string.action_reset_done), Toast.LENGTH_SHORT).show();
                updateTerminalSettingSummaries(rootView);
            })
            .setNegativeButton(getString(R.string.action_close), null)
            .show();
    }

    private void showEditKeypadMapping(String label, String current) {
        android.widget.LinearLayout layout = new android.widget.LinearLayout(requireContext());
        layout.setOrientation(android.widget.LinearLayout.VERTICAL);
        layout.setPadding(32, 24, 32, 8);
        android.widget.TextView hint = new android.widget.TextView(requireContext());
        hint.setText(getString(R.string.settings_terminal_keypad_mapping_hint));
        android.widget.EditText edit = new android.widget.EditText(requireContext());
        edit.setText(current != null ? current : "");
        layout.addView(hint);
        layout.addView(edit);
        new AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.settings_terminal_keypad_mapping_edit_title, label))
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
        Toast.makeText(requireContext(), getString(R.string.action_saved), Toast.LENGTH_SHORT).show();
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
        View sessionPolicy = view.findViewById(R.id.setting_session_policy);
        View backup = view.findViewById(R.id.setting_backup);
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
        if (sessionPolicy != null) {
            TextView summary = sessionPolicy.findViewById(R.id.summary);
            com.orcterm.util.SessionPersistenceManager manager = com.orcterm.util.SessionPersistenceManager.getInstance(requireContext());
            summary.setText(manager.getPolicyDescription(manager.getSessionPolicy()));
            summary.setVisibility(View.VISIBLE);
        }
        if (backup != null) {
            TextView summary = backup.findViewById(R.id.summary);
            summary.setText(getString(R.string.settings_main_backup_summary));
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
        hint.setText(getString(R.string.settings_ssh_key_dir, filesDir.getAbsolutePath()));
        hint.setPadding(24, 16, 24, 16);
        layout.addView(hint);
        android.widget.ListView lv = new android.widget.ListView(requireContext());
        lv.setAdapter(new android.widget.ArrayAdapter<>(requireContext(), android.R.layout.simple_list_item_1, list));
        lv.setOnItemClickListener((parent, view, position, id) -> {
            java.io.File target = fileList.get(position);
            new AlertDialog.Builder(requireContext())
                .setTitle(target.getName())
                .setItems(new String[]{getString(R.string.action_copy_path), getString(R.string.action_delete)}, (d, which) -> {
                    if (which == 0) {
                        ClipboardManager cb = (ClipboardManager) requireContext().getSystemService(Context.CLIPBOARD_SERVICE);
                        cb.setPrimaryClip(ClipData.newPlainText("path", target.getAbsolutePath()));
                        Toast.makeText(requireContext(), getString(R.string.msg_path_copied, target.getAbsolutePath()), Toast.LENGTH_SHORT).show();
                    } else {
                        new AlertDialog.Builder(requireContext())
                            .setTitle(getString(R.string.action_delete_key))
                            .setMessage(getString(R.string.settings_ssh_key_delete_confirm, target.getName()))
                            .setPositiveButton(getString(R.string.action_delete), (d2, w2) -> {
                                if (target.delete()) {
                                    Toast.makeText(requireContext(), getString(R.string.action_deleted), Toast.LENGTH_SHORT).show();
                                } else {
                                    Toast.makeText(requireContext(), getString(R.string.action_delete_failed), Toast.LENGTH_SHORT).show();
                                }
                            })
                            .setNegativeButton(getString(R.string.action_cancel), null)
                            .show();
                    }
                })
                .show();
        });
        layout.addView(lv);
        
        new AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.settings_ssh_keys_title))
            .setView(layout)
            .setPositiveButton(getString(R.string.action_generate_key), (d, w) -> {
                try {
                    com.orcterm.core.ssh.SshNative ssh = new com.orcterm.core.ssh.SshNative();
                    String alias = "key_" + System.currentTimeMillis();
                    java.io.File priv = new java.io.File(filesDir, alias);
                    int ret = ssh.generateKeyPair(priv.getAbsolutePath());
                    if (ret == 0) {
                        Toast.makeText(requireContext(), getString(R.string.settings_ssh_key_generated, alias), Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(requireContext(), getString(R.string.settings_ssh_key_generate_failed), Toast.LENGTH_SHORT).show();
                    }
                } catch (Exception e) {
                    Toast.makeText(requireContext(), getString(R.string.msg_error, e.getMessage()), Toast.LENGTH_LONG).show();
                }
            })
            .setNeutralButton(getString(R.string.action_import_key), (d, w) -> keyPickerLauncher.launch("*/*"))
            .setNegativeButton(getString(R.string.action_close), null)
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
                Toast.makeText(requireContext(), getString(R.string.background_set), Toast.LENGTH_SHORT).show();
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
            Toast.makeText(requireContext(), getString(R.string.settings_ssh_key_imported, name), Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(requireContext(), getString(R.string.settings_ssh_key_import_failed, e.getMessage()), Toast.LENGTH_SHORT).show();
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
            Toast.makeText(requireContext(), getString(R.string.background_load_failed), Toast.LENGTH_SHORT).show();
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
