package com.orcterm.util;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.orcterm.data.AppDatabase;
import com.orcterm.data.HostDao;
import com.orcterm.data.HostEntity;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 备份与恢复管理器
 * 负责应用数据的导出和导入（主机配置、设置、密钥等）
 */
public class BackupRestoreManager {
    
    private static final String TAG = "BackupRestoreManager";
    private static final String BACKUP_VERSION = "1.0";
    private static final String BACKUP_FILENAME_PREFIX = "orcterm_backup_";
    private static final String BACKUP_FILENAME_SUFFIX = ".json";
    
    public interface BackupRestoreCallback {
        void onProgress(int current, int total, String message);
        void onSuccess(String message);
        void onError(String error);
    }
    
    private final Context context;
    private final HostDao hostDao;
    
    public BackupRestoreManager(@NonNull Context context) {
        this.context = context.getApplicationContext();
        this.hostDao = AppDatabase.getDatabase(this.context).hostDao();
    }
    
    /**
     * 创建完整备份
     * @param destinationUri 备份文件保存位置（可为null，使用默认位置）
     * @param callback 回调接口
     */
    public void createBackup(@Nullable Uri destinationUri, @Nullable BackupRestoreCallback callback) {
        new Thread(() -> {
            try {
                if (callback != null) {
                    callback.onProgress(0, 100, "正在收集数据...");
                }
                
                // 创建备份JSON对象
                JSONObject backup = new JSONObject();
                backup.put("version", BACKUP_VERSION);
                backup.put("created_at", System.currentTimeMillis());
                backup.put("device", Build.MODEL);
                backup.put("android_version", Build.VERSION.RELEASE);
                
                // 备份主机配置
                if (callback != null) {
                    callback.onProgress(20, 100, "备份主机配置...");
                }
                backup.put("hosts", backupHosts());
                
                // 备份应用设置
                if (callback != null) {
                    callback.onProgress(50, 100, "备份应用设置...");
                }
                backup.put("settings", backupSettings());
                
                // 备份会话保持策略设置
                backup.put("session_policy", backupSessionPolicy());
                
                // 备份密钥信息（仅记录密钥文件名，不包含密钥内容）
                if (callback != null) {
                    callback.onProgress(80, 100, "备份密钥信息...");
                }
                backup.put("keys", backupKeyInfo());
                
                // 保存到文件
                if (callback != null) {
                    callback.onProgress(90, 100, "写入备份文件...");
                }
                
                String backupJson = backup.toString(2);
                String filename = generateBackupFilename();
                
                Uri savedUri;
                if (destinationUri != null) {
                    savedUri = writeToUri(destinationUri, backupJson);
                } else {
                    savedUri = saveToDownloads(filename, backupJson);
                }
                
                if (callback != null) {
                    callback.onProgress(100, 100, "备份完成");
                    callback.onSuccess("备份已保存: " + filename);
                }
                
            } catch (Exception e) {
                Log.e(TAG, "备份失败", e);
                if (callback != null) {
                    callback.onError("备份失败: " + e.getMessage());
                }
            }
        }).start();
    }
    
    /**
     * 从备份文件恢复数据
     * @param sourceUri 备份文件URI
     * @param callback 回调接口
     */
    public void restoreBackup(@NonNull Uri sourceUri, @Nullable BackupRestoreCallback callback) {
        new Thread(() -> {
            try {
                if (callback != null) {
                    callback.onProgress(0, 100, "正在读取备份文件...");
                }
                
                // 读取备份文件
                String backupJson = readFromUri(sourceUri);
                JSONObject backup = new JSONObject(backupJson);
                
                // 验证备份版本
                String version = backup.optString("version", "1.0");
                if (callback != null) {
                    callback.onProgress(10, 100, "备份版本: " + version);
                }
                
                // 恢复主机配置
                if (backup.has("hosts")) {
                    if (callback != null) {
                        callback.onProgress(30, 100, "恢复主机配置...");
                    }
                    restoreHosts(backup.getJSONArray("hosts"));
                }
                
                // 恢复设置
                if (backup.has("settings")) {
                    if (callback != null) {
                        callback.onProgress(60, 100, "恢复应用设置...");
                    }
                    restoreSettings(backup.getJSONObject("settings"));
                }
                
                // 恢复会话保持策略
                if (backup.has("session_policy")) {
                    restoreSessionPolicy(backup.getJSONObject("session_policy"));
                }
                
                if (callback != null) {
                    callback.onProgress(100, 100, "恢复完成");
                    callback.onSuccess("数据恢复成功");
                }
                
            } catch (Exception e) {
                Log.e(TAG, "恢复失败", e);
                if (callback != null) {
                    callback.onError("恢复失败: " + e.getMessage());
                }
            }
        }).start();
    }
    
    /**
     * 导出主机配置为 CSV 格式（便于编辑）
     */
    public void exportHostsAsCsv(@Nullable Uri destinationUri, @Nullable BackupRestoreCallback callback) {
        new Thread(() -> {
            try {
                List<HostEntity> hosts = hostDao.getAllHostsNow();
                if (hosts.isEmpty()) {
                    if (callback != null) {
                        callback.onError("没有可导出的主机");
                    }
                    return;
                }
                
                StringBuilder csv = new StringBuilder();
                csv.append("alias,hostname,port,username,authType,keyPath,groupName\n");
                
                for (HostEntity host : hosts) {
                    csv.append(String.format(Locale.US, "%s,%s,%d,%s,%d,%s,%s\n",
                        escapeCsv(host.alias),
                        escapeCsv(host.hostname),
                        host.port,
                        escapeCsv(host.username),
                        host.authType,
                        escapeCsv(host.keyPath),
                        escapeCsv(host.containerEngine)
                    ));
                }
                
                String filename = "orcterm_hosts_" + getTimestamp() + ".csv";
                if (destinationUri != null) {
                    writeToUri(destinationUri, csv.toString());
                } else {
                    saveToDownloads(filename, csv.toString());
                }
                
                if (callback != null) {
                    callback.onSuccess("已导出 " + hosts.size() + " 个主机");
                }
                
            } catch (Exception e) {
                Log.e(TAG, "CSV导出失败", e);
                if (callback != null) {
                    callback.onError("导出失败: " + e.getMessage());
                }
            }
        }).start();
    }
    
    /**
     * 从 CSV 导入主机配置
     */
    public void importHostsFromCsv(@NonNull Uri sourceUri, @Nullable BackupRestoreCallback callback) {
        new Thread(() -> {
            try {
                String csv = readFromUri(sourceUri);
                String[] lines = csv.split("\n");
                
                if (lines.length < 2) {
                    if (callback != null) {
                        callback.onError("CSV文件格式错误");
                    }
                    return;
                }
                
                int imported = 0;
                for (int i = 1; i < lines.length; i++) {
                    String line = lines[i].trim();
                    if (line.isEmpty()) continue;
                    
                    String[] fields = parseCsvLine(line);
                    if (fields.length >= 4) {
                        HostEntity host = new HostEntity();
                        host.alias = fields[0];
                        host.hostname = fields[1];
                        host.port = parseInt(fields[2], 22);
                        host.username = fields[3];
                        if (fields.length > 4) host.authType = parseInt(fields[4], 0);
                        if (fields.length > 5) host.keyPath = fields[5];
                        if (fields.length > 6) host.containerEngine = fields[6];
                        
                        hostDao.insert(host);
                        imported++;
                    }
                    
                    if (callback != null) {
                        callback.onProgress(i, lines.length, "导入: " + i + "/" + lines.length);
                    }
                }
                
                if (callback != null) {
                    callback.onSuccess("成功导入 " + imported + " 个主机");
                }
                
            } catch (Exception e) {
                Log.e(TAG, "CSV导入失败", e);
                if (callback != null) {
                    callback.onError("导入失败: " + e.getMessage());
                }
            }
        }).start();
    }
    
    // ==================== 私有方法 ====================
    
    private JSONArray backupHosts() throws JSONException {
        List<HostEntity> hosts = hostDao.getAllHostsNow();
        JSONArray array = new JSONArray();
        for (HostEntity host : hosts) {
            JSONObject obj = new JSONObject();
            obj.put("id", host.id);
            obj.put("alias", host.alias);
            obj.put("hostname", host.hostname);
            obj.put("port", host.port);
            obj.put("username", host.username);
            obj.put("authType", host.authType);
            obj.put("keyPath", host.keyPath);
            obj.put("containerEngine", host.containerEngine);
            obj.put("osName", host.osName);
            obj.put("osVersion", host.osVersion);
            obj.put("status", host.status);
            obj.put("terminalThemePreset", host.terminalThemePreset);
            array.put(obj);
        }
        return array;
    }
    
    private JSONObject backupSettings() throws JSONException {
        JSONObject settings = new JSONObject();
        SharedPreferences orcPrefs = context.getSharedPreferences("orcterm_prefs", Context.MODE_PRIVATE);
        SharedPreferences sessionPrefs = context.getSharedPreferences("session_persistence_prefs", Context.MODE_PRIVATE);
        
        // 备份主要设置项
        settings.put("font_size_index", orcPrefs.getInt("font_size_index", 1));
        settings.put("terminal_theme_index", orcPrefs.getInt("terminal_theme_index", 0));
        settings.put("theme_mode", orcPrefs.getInt("theme_mode", 2));
        settings.put("list_density", orcPrefs.getInt("list_density", 1));
        settings.put("host_display_style", orcPrefs.getInt("host_display_style", 0));
        settings.put("file_sort_order", orcPrefs.getInt("file_sort_order", 0));
        settings.put("file_show_hidden", orcPrefs.getBoolean("file_show_hidden", false));
        settings.put("app_language", orcPrefs.getString("app_language", "system"));
        
        return settings;
    }
    
    private JSONObject backupSessionPolicy() throws JSONException {
        SessionPersistenceManager manager = SessionPersistenceManager.getInstance(context);
        JSONObject policy = new JSONObject();
        policy.put("session_policy", manager.getSessionPolicy().getValue());
        policy.put("timeout_minutes", manager.getTimeoutMinutes());
        policy.put("reconnect_on_resume", manager.isReconnectOnResume());
        policy.put("auto_disconnect_on_background", manager.isAutoDisconnectOnBackground());
        policy.put("bg_disconnect_delay", manager.getBackgroundDisconnectDelay());
        policy.put("keep_alive_enabled", manager.isKeepAliveEnabled());
        policy.put("keep_alive_interval", manager.getKeepAliveInterval());
        return policy;
    }
    
    private JSONArray backupKeyInfo() {
        JSONArray keys = new JSONArray();
        File filesDir = context.getFilesDir();
        File[] keyFiles = filesDir.listFiles((dir, name) -> 
            name.endsWith(".pub") || name.endsWith(".key") || 
            (!name.contains(".") && !name.startsWith("."))
        );
        
        if (keyFiles != null) {
            for (File key : keyFiles) {
                keys.put(key.getName());
            }
        }
        return keys;
    }
    
    private void restoreHosts(JSONArray hostsArray) throws JSONException {
        for (int i = 0; i < hostsArray.length(); i++) {
            JSONObject obj = hostsArray.getJSONObject(i);
            HostEntity host = new HostEntity();
            host.alias = obj.optString("alias", "");
            host.hostname = obj.optString("hostname", "");
            host.port = obj.optInt("port", 22);
            host.username = obj.optString("username", "");
            host.authType = obj.optInt("authType", 0);
            host.keyPath = obj.optString("keyPath", null);
            host.containerEngine = obj.optString("containerEngine", null);
            host.osName = obj.optString("osName", null);
            host.osVersion = obj.optString("osVersion", null);
            host.status = obj.optString("status", "unknown");
            host.terminalThemePreset = obj.optString("terminalThemePreset", "default");
            hostDao.insert(host);
        }
    }
    
    private void restoreSettings(JSONObject settings) {
        SharedPreferences prefs = context.getSharedPreferences("orcterm_prefs", Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        
        if (settings.has("font_size_index")) {
            editor.putInt("font_size_index", settings.optInt("font_size_index", 1));
        }
        if (settings.has("terminal_theme_index")) {
            editor.putInt("terminal_theme_index", settings.optInt("terminal_theme_index", 0));
        }
        if (settings.has("theme_mode")) {
            editor.putInt("theme_mode", settings.optInt("theme_mode", 2));
        }
        if (settings.has("list_density")) {
            editor.putInt("list_density", settings.optInt("list_density", 1));
        }
        if (settings.has("host_display_style")) {
            editor.putInt("host_display_style", settings.optInt("host_display_style", 0));
        }
        if (settings.has("app_language")) {
            editor.putString("app_language", settings.optString("app_language", "system"));
        }
        
        editor.apply();
    }
    
    private void restoreSessionPolicy(JSONObject policy) {
        SessionPersistenceManager manager = SessionPersistenceManager.getInstance(context);
        
        if (policy.has("session_policy")) {
            manager.setSessionPolicy(SessionPersistenceManager.SessionPolicy.fromValue(
                policy.optInt("session_policy", 1)));
        }
        if (policy.has("timeout_minutes")) {
            manager.setTimeoutMinutes(policy.optInt("timeout_minutes", 30));
        }
        if (policy.has("reconnect_on_resume")) {
            manager.setReconnectOnResume(policy.optBoolean("reconnect_on_resume", true));
        }
        if (policy.has("auto_disconnect_on_background")) {
            manager.setAutoDisconnectOnBackground(policy.optBoolean("auto_disconnect_on_background", false));
        }
        if (policy.has("bg_disconnect_delay")) {
            manager.setBackgroundDisconnectDelay(policy.optInt("bg_disconnect_delay", 60));
        }
        if (policy.has("keep_alive_enabled")) {
            manager.setKeepAliveEnabled(policy.optBoolean("keep_alive_enabled", true));
        }
        if (policy.has("keep_alive_interval")) {
            manager.setKeepAliveInterval(policy.optInt("keep_alive_interval", 30));
        }
    }
    
    private Uri saveToDownloads(String filename, String content) throws IOException {
        File downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
        File backupFile = new File(downloadsDir, filename);
        
        try (FileOutputStream fos = new FileOutputStream(backupFile);
             OutputStreamWriter writer = new OutputStreamWriter(fos, StandardCharsets.UTF_8)) {
            writer.write(content);
        }
        
        return Uri.fromFile(backupFile);
    }
    
    private Uri writeToUri(Uri uri, String content) throws IOException {
        try (OutputStream os = context.getContentResolver().openOutputStream(uri);
             OutputStreamWriter writer = new OutputStreamWriter(os, StandardCharsets.UTF_8)) {
            writer.write(content);
        }
        return uri;
    }
    
    private String readFromUri(Uri uri) throws IOException {
        StringBuilder sb = new StringBuilder();
        try (InputStream is = context.getContentResolver().openInputStream(uri);
             BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append("\n");
            }
        }
        return sb.toString();
    }
    
    private String generateBackupFilename() {
        return BACKUP_FILENAME_PREFIX + getTimestamp() + BACKUP_FILENAME_SUFFIX;
    }
    
    private String getTimestamp() {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault());
        return sdf.format(new Date());
    }
    
    private String escapeCsv(String value) {
        if (value == null) return "";
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }
    
    private String[] parseCsvLine(String line) {
        List<String> fields = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;
        
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') {
                if (inQuotes && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    current.append('"');
                    i++;
                } else {
                    inQuotes = !inQuotes;
                }
            } else if (c == ',' && !inQuotes) {
                fields.add(current.toString());
                current = new StringBuilder();
            } else {
                current.append(c);
            }
        }
        fields.add(current.toString());
        
        return fields.toArray(new String[0]);
    }
    
    private int parseInt(String value, int defaultValue) {
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }
}
