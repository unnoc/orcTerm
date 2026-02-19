package com.orcterm.ui.nav;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.fragment.app.Fragment;

import com.orcterm.R;
import com.orcterm.util.AppBackgroundHelper;

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
        setupItem(view, R.id.setting_monitor_settings, R.drawable.ic_action_memory, getString(R.string.settings_main_monitor_title), getString(R.string.settings_main_monitor_summary), v -> openDetail("monitor"));
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

    private void updateMainSummaries(View view) {
        if (view == null) return;
        View ssh = view.findViewById(R.id.setting_ssh_manage);
        View cloud = view.findViewById(R.id.setting_cloud_sync);
        View file = view.findViewById(R.id.setting_file_management);
        View terminal = view.findViewById(R.id.setting_terminal_settings);
        View monitor = view.findViewById(R.id.setting_monitor_settings);
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
        if (monitor != null) {
            TextView summary = monitor.findViewById(R.id.summary);
            int refresh = prefs.getInt("monitor_refresh_interval_sec", 3);
            boolean showTotal = prefs.getBoolean("monitor_show_total_traffic", true);
            String scope = prefs.getString("monitor_traffic_scope", "session");
            String scopeLabel = "boot".equals(scope)
                    ? getString(R.string.settings_monitor_scope_boot)
                    : getString(R.string.settings_monitor_scope_session);
            String totalLabel = showTotal
                    ? getString(R.string.settings_enabled)
                    : getString(R.string.settings_disabled);
            summary.setText(getString(R.string.settings_main_monitor_summary_format, refresh, totalLabel, scopeLabel));
            summary.setVisibility(View.VISIBLE);
        }
        if (theme != null) {
            TextView summary = theme.findViewById(R.id.summary);
            int currentMode = prefs.getInt("theme_mode", AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
            String modeLabel;
            if (currentMode == AppCompatDelegate.MODE_NIGHT_YES) {
                modeLabel = getString(R.string.settings_theme_mode_dark);
            } else if (currentMode == AppCompatDelegate.MODE_NIGHT_NO) {
                modeLabel = getString(R.string.settings_theme_mode_light);
            } else {
                modeLabel = getString(R.string.settings_language_system);
            }
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

    private void applyBackgroundFromPrefs() {
        String uriStr = prefs.getString("app_bg_uri", null);
        if (uriStr == null || uriStr.isEmpty()) return;
        boolean applied = AppBackgroundHelper.applyUri(requireContext(), rootView, Uri.parse(uriStr));
        if (!applied) {
            Toast.makeText(requireContext(), getString(R.string.background_load_failed), Toast.LENGTH_SHORT).show();
        }
    }
}
