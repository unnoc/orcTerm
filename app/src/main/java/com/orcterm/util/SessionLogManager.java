package com.orcterm.util;

import android.content.Context;
import android.util.Log;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 会话日志管理器
 * 负责记录、保存和管理 SSH 会话的终端输出日志
 */
public class SessionLogManager {
    
    private static final String TAG = "SessionLogManager";
    private static final String LOG_DIR = "session_logs";
    private static final long MAX_LOG_SIZE = 10 * 1024 * 1024; // 10MB 单个日志文件最大
    private static final int MAX_LOG_FILES = 50; // 最多保留 50 个会话日志
    
    public static class SessionLog {
        public final String id;
        public final String hostInfo; // 主机信息 (user@hostname:port)
        public final long startTime;
        public final long endTime;
        public final long size;
        public final String logFilePath;
        public final int lineCount;
        
        public SessionLog(String id, String hostInfo, long startTime, long endTime, 
                         long size, String logFilePath, int lineCount) {
            this.id = id;
            this.hostInfo = hostInfo;
            this.startTime = startTime;
            this.endTime = endTime;
            this.size = size;
            this.logFilePath = logFilePath;
            this.lineCount = lineCount;
        }
    }
    
    @FunctionalInterface
    public interface LogCallback {
        void onLogLoaded(List<SessionLog> logs);
    }
    
    public interface LogErrorCallback {
        void onError(String error);
    }
    
    // 保持向后兼容的包含错误回调的接口
    public interface LogCallbackWithError {
        void onLogLoaded(List<SessionLog> logs);
    }
    
    private final Context context;
    private final ExecutorService executor;
    private BufferedWriter currentWriter;
    private String currentLogFile;
    private long currentLogSize;
    private int currentLineCount;
    private long sessionStartTime;
    private String currentHostInfo;
    
    public SessionLogManager(Context context) {
        this.context = context.getApplicationContext();
        this.executor = Executors.newSingleThreadExecutor();
        ensureLogDirectory();
    }
    
    /**
     * 开始记录新会话
     */
    public void startSession(String host, int port, String username) {
        executor.execute(() -> {
            try {
                // 结束之前的会话（如果有）
                endSession();
                
                currentHostInfo = username + "@" + host + ":" + port;
                sessionStartTime = System.currentTimeMillis();
                currentLogSize = 0;
                currentLineCount = 0;
                
                String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
                    .format(new Date(sessionStartTime));
                String filename = "session_" + timestamp + "_" + host.replace(".", "_") + ".log";
                currentLogFile = getLogDir() + File.separator + filename;
                
                currentWriter = new BufferedWriter(new FileWriter(currentLogFile, true));
                
                // 写入会话开始标记
                writeLine("[会话开始] " + currentHostInfo + " - " + 
                    new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                        .format(new Date(sessionStartTime)));
                
                Log.i(TAG, "开始记录会话日志: " + currentLogFile);
                
            } catch (IOException e) {
                Log.e(TAG, "开始会话日志失败", e);
            }
        });
    }
    
    /**
     * 写入日志行
     */
    public void writeLog(String data) {
        if (currentWriter == null) return;
        
        executor.execute(() -> {
            try {
                // 检查文件大小限制
                if (currentLogSize > MAX_LOG_SIZE) {
                    writeLine("[日志截断] 达到最大文件大小限制");
                    return;
                }
                
                writeLine(data);
                
            } catch (IOException e) {
                Log.e(TAG, "写入日志失败", e);
            }
        });
    }
    
    /**
     * 结束当前会话
     */
    public void endSession() {
        executor.execute(() -> {
            try {
                if (currentWriter != null) {
                    long endTime = System.currentTimeMillis();
                    writeLine("[会话结束] " + 
                        new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                            .format(new Date(endTime)));
                    
                    currentWriter.close();
                    currentWriter = null;
                    
                    Log.i(TAG, "会话日志已保存: " + currentLogFile + 
                        " (大小: " + currentLogSize + " 字节, 行数: " + currentLineCount + ")");
                    
                    // 清理旧日志
                    cleanupOldLogs();
                }
            } catch (IOException e) {
                Log.e(TAG, "结束会话日志失败", e);
            }
        });
    }
    
    /**
     * 获取所有会话日志列表
     */
    public void getAllLogs(LogCallback callback) {
        executor.execute(() -> {
            try {
                List<SessionLog> logs = new ArrayList<>();
                File logDir = new File(getLogDir());
                File[] files = logDir.listFiles((dir, name) -> name.endsWith(".log"));
                
                if (files != null) {
                    for (File file : files) {
                        SessionLog log = parseLogFile(file);
                        if (log != null) {
                            logs.add(log);
                        }
                    }
                }
                
                // 按时间倒序排列
                Collections.sort(logs, (a, b) -> Long.compare(b.startTime, a.startTime));
                
                if (callback != null) {
                    callback.onLogLoaded(logs);
                }
                
            } catch (Exception e) {
                Log.e(TAG, "获取日志列表失败", e);
            }
        });
    }
    
    /**
     * 获取所有会话日志列表（包含错误回调）
     */
    public void getAllLogsWithError(LogCallbackWithError callback) {
        executor.execute(() -> {
            try {
                List<SessionLog> logs = new ArrayList<>();
                File logDir = new File(getLogDir());
                File[] files = logDir.listFiles((dir, name) -> name.endsWith(".log"));
                
                if (files != null) {
                    for (File file : files) {
                        SessionLog log = parseLogFile(file);
                        if (log != null) {
                            logs.add(log);
                        }
                    }
                }
                
                // 按时间倒序排列
                Collections.sort(logs, (a, b) -> Long.compare(b.startTime, a.startTime));
                
                if (callback != null) {
                    callback.onLogLoaded(logs);
                }
                
            } catch (Exception e) {
                Log.e(TAG, "获取日志列表失败", e);
                // 静默处理错误
            }
        });
    }
    
    /**
     * 读取日志文件内容
     */
    public void readLogContent(String logFilePath, int maxLines, LogContentCallback callback) {
        executor.execute(() -> {
            try {
                File file = new File(logFilePath);
                if (!file.exists()) {
                    return; // 静默处理，文件不存在时不调用回调
                }
                
                List<String> lines = new ArrayList<>();
                try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        lines.add(line);
                        if (maxLines > 0 && lines.size() >= maxLines) {
                            break;
                        }
                    }
                }
                
                if (callback != null) {
                    callback.onContentLoaded(lines);
                }
                
            } catch (IOException e) {
                Log.e(TAG, "读取日志内容失败", e);
                // 静默处理错误，不调用错误回调
            }
        });
    }
    
    /**
     * 删除指定日志文件
     */
    public void deleteLog(String logFilePath, DeleteCallback callback) {
        executor.execute(() -> {
            try {
                File file = new File(logFilePath);
                if (file.exists() && file.delete()) {
                    if (callback != null) {
                        callback.onSuccess();
                    }
                } else {
                    // 静默处理删除失败
                }
            } catch (Exception e) {
                Log.e(TAG, "删除日志失败", e);
                // 静默处理错误
            }
        });
    }
    
    /**
     * 获取日志统计信息
     */
    public void getLogStats(StatsCallback callback) {
        executor.execute(() -> {
            try {
                File logDir = new File(getLogDir());
                File[] files = logDir.listFiles((dir, name) -> name.endsWith(".log"));
                
                int totalFiles = 0;
                long totalSize = 0;
                
                if (files != null) {
                    totalFiles = files.length;
                    for (File file : files) {
                        totalSize += file.length();
                    }
                }
                
                if (callback != null) {
                    callback.onStats(totalFiles, totalSize);
                }
                
            } catch (Exception e) {
                Log.e(TAG, "获取日志统计失败", e);
            }
        });
    }
    
    /**
     * 清理所有日志
     */
    public void clearAllLogs(ClearCallback callback) {
        executor.execute(() -> {
            try {
                File logDir = new File(getLogDir());
                File[] files = logDir.listFiles((dir, name) -> name.endsWith(".log"));
                
                int deleted = 0;
                if (files != null) {
                    for (File file : files) {
                        if (file.delete()) {
                            deleted++;
                        }
                    }
                }
                
                if (callback != null) {
                    callback.onCleared(deleted);
                }
                
            } catch (Exception e) {
                Log.e(TAG, "清理日志失败", e);
                // 静默处理错误
            }
        });
    }
    
    // ==================== 回调接口 ====================
    
    @FunctionalInterface
    public interface LogContentCallback {
        void onContentLoaded(List<String> lines);
    }
    
    @FunctionalInterface
    public interface DeleteCallback {
        void onSuccess();
    }
    
    @FunctionalInterface
    public interface StatsCallback {
        void onStats(int totalFiles, long totalSize);
    }
    
    @FunctionalInterface
    public interface ClearCallback {
        void onCleared(int deletedCount);
    }
    
    // ==================== 私有方法 ====================
    
    private void writeLine(String line) throws IOException {
        currentWriter.write(line);
        currentWriter.newLine();
        currentWriter.flush();
        currentLogSize += line.getBytes().length + 1;
        currentLineCount++;
    }
    
    private String getLogDir() {
        File dir = new File(context.getFilesDir(), LOG_DIR);
        if (!dir.exists()) {
            dir.mkdirs();
        }
        return dir.getAbsolutePath();
    }
    
    private void ensureLogDirectory() {
        File dir = new File(getLogDir());
        if (!dir.exists()) {
            dir.mkdirs();
        }
    }
    
    private SessionLog parseLogFile(File file) {
        try {
            String filename = file.getName();
            long size = file.length();
            long lastModified = file.lastModified();
            
            // 解析文件名: session_yyyyMMdd_HHmmss_hostname.log
            String hostInfo = "Unknown";
            long startTime = lastModified;
            
            if (filename.startsWith("session_")) {
                String[] parts = filename.replace(".log", "").split("_");
                if (parts.length >= 3) {
                    try {
                        SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault());
                        Date date = sdf.parse(parts[1] + "_" + parts[2]);
                        if (date != null) {
                            startTime = date.getTime();
                        }
                    } catch (Exception e) {
                        // 使用文件修改时间
                    }
                    
                    // 从文件名提取主机信息
                    if (parts.length > 3) {
                        StringBuilder hostBuilder = new StringBuilder();
                        for (int i = 3; i < parts.length; i++) {
                            if (i > 3) hostBuilder.append("_");
                            hostBuilder.append(parts[i]);
                        }
                        hostInfo = hostBuilder.toString().replace("_", ".");
                    }
                }
            }
            
            // 统计行数
            int lineCount = 0;
            try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
                while (reader.readLine() != null) {
                    lineCount++;
                }
            }
            
            return new SessionLog(
                filename,
                hostInfo,
                startTime,
                lastModified,
                size,
                file.getAbsolutePath(),
                lineCount
            );
            
        } catch (Exception e) {
            Log.e(TAG, "解析日志文件失败: " + file.getName(), e);
            return null;
        }
    }
    
    private void cleanupOldLogs() {
        try {
            File logDir = new File(getLogDir());
            File[] files = logDir.listFiles((dir, name) -> name.endsWith(".log"));
            
            if (files != null && files.length > MAX_LOG_FILES) {
                // 按修改时间排序，删除最旧的
                List<File> fileList = new ArrayList<>();
                for (File f : files) {
                    fileList.add(f);
                }
                Collections.sort(fileList, (a, b) -> Long.compare(a.lastModified(), b.lastModified()));
                
                int toDelete = fileList.size() - MAX_LOG_FILES;
                for (int i = 0; i < toDelete; i++) {
                    fileList.get(i).delete();
                }
                
                Log.i(TAG, "清理了 " + toDelete + " 个旧日志文件");
            }
            
        } catch (Exception e) {
            Log.e(TAG, "清理旧日志失败", e);
        }
    }
}
