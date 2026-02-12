package com.orcterm.core.terminal;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.orcterm.core.transport.LocalTransport;
import com.orcterm.core.transport.SshTransport;
import com.orcterm.core.transport.TelnetTransport;
import com.orcterm.core.transport.Transport;

import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicBoolean;
import com.orcterm.core.transport.HostKeyVerifier;

/**
 * 终端会话管理类
 * 管理会话生命周期和数据流。
 * 负责在网络层 (Transport) 和逻辑层 (Logic) 之间协调数据。
 */
public class TerminalSession {

    /**
     * 会话监听器接口
     * 用于接收连接状态变化和数据接收的通知。
     */
    public interface SessionListener {
        /** 连接成功 */
        void onConnected();
        /** 连接断开 */
        void onDisconnected();
        /** 接收到数据 */
        void onDataReceived(String data);
        /** 发生错误 */
        void onError(String message);
    }

    private Transport transport;
    private TerminalEmulator emulator;
    private final ExecutorService executor;
    private final ExecutorService writeExecutor;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    
    // 帧率限制相关字段
    private long lastFrameTime = 0;
    private static final long MIN_FRAME_TIME = 16; // 60fps (1000ms / 60 = 16.67ms)
    private final StringBuilder pendingData = new StringBuilder();
    private boolean pendingUpdateScheduled = false;
    private long lastKeepaliveTime = 0;
    private final StringBuilder readBuffer = new StringBuilder();
    private long lastReadDispatchTime = 0;
    private static final int READ_BATCH_SIZE = 2048;
    
    // 创建带日志功能的自定义线程池
    private static final ThreadFactory TERMINAL_THREAD_FACTORY = new ThreadFactory() {
        @Override
        public Thread newThread(Runnable r) {
            Thread t = new Thread(r, "TerminalSession-Thread");
            // 只在首次创建时记录一次日志，避免重复输出
            return t;
        }
    };
    
    // 初始化带日志功能的线程池
    {
        this.executor = Executors.newSingleThreadExecutor(TERMINAL_THREAD_FACTORY);
        this.writeExecutor = Executors.newSingleThreadExecutor(TERMINAL_THREAD_FACTORY);
    }
    private final AtomicBoolean isConnected = new AtomicBoolean(false);
    
    private final CopyOnWriteArrayList<SessionListener> listeners = new CopyOnWriteArrayList<>();
    private static final String LOG_TAG = "SSH_SESSION";
    
    // 连接配置
    private String host;
    private int port;
    private String username;
    private String password;
    private int authType;
    private String keyPath;
    private HostKeyVerifier hostKeyVerifier;

    public TerminalSession() {
    }

    /**
     * 设置会话监听器
     *
     * @param listener 监听器实例
     */
    public void setListener(SessionListener listener) {
        listeners.clear();
        if (listener != null) {
            listeners.add(listener);
        }
    }

    public void addListener(SessionListener listener) {
        if (listener != null) {
            listeners.addIfAbsent(listener);
        }
    }

    public void removeListener(SessionListener listener) {
        if (listener != null) {
            listeners.remove(listener);
        }
    }

    public void setHostKeyVerifier(HostKeyVerifier verifier) {
        this.hostKeyVerifier = verifier;
    }

    /**
     * 发起连接
     *
     * @param host     目标主机
     * @param port     目标端口
     * @param user     用户名
     * @param pass     密码
     * @param authType 认证类型
     * @param keyPath  密钥路径
     */
    public void connect(String host, int port, String user, String pass, int authType, String keyPath) {
        this.host = host;
        this.port = port;
        this.username = user;
        this.password = pass;
        this.authType = authType;
        this.keyPath = keyPath;

        Log.v(LOG_TAG, "connect requested host=" + host + " port=" + port + " user=" + user + " auth=" + authType);
        executor.execute(this::connectInternal);
    }
    
    // 接管已有 SSH 句柄并启动读取循环
    public void attachExistingSshHandle(long handle, String host, int port, String user, String pass, int authType, String keyPath) throws Exception {
        this.host = host;
        this.port = port;
        this.username = user;
        this.password = pass;
        this.authType = authType;
        this.keyPath = keyPath;
        transport = new SshTransport();
        if (hostKeyVerifier != null) {
            ((SshTransport) transport).setHostKeyVerifier(hostKeyVerifier);
        }
        ((SshTransport) transport).attachExistingHandle(handle, 80, 24);
        isConnected.set(true);
        notifyConnected();
        executor.execute(this::startReading);
    }

    /**
     * 调整终端大小
     *
     * @param cols 列数
     * @param rows 行数
     */
    public void resize(int cols, int rows) {
        if (!isConnected.get()) return;
        Transport current = transport;
        if (current == null) return;
        executor.execute(() -> {
            Log.v("TerminalSession", "在线程中调整终端大小: " + cols + "x" + rows + ", 线程: " + Thread.currentThread().getName());
            current.resize(cols, rows);
        });
    }

    public long getHandle() {
        return (transport != null) ? transport.getHandle() : 0;
    }

    /**
     * 内部连接逻辑
     * 根据配置选择合适的 Transport 实现并建立连接。
     */
    private void connectInternal() {
        Log.i(LOG_TAG, "connectInternal start host=" + host + " port=" + port);
        try {
            // 根据 Host 和 Port 判断协议类型
            if ("local".equalsIgnoreCase(host) || "localhost".equalsIgnoreCase(host) && (port == 0 || port == -1)) {
                 transport = new LocalTransport();
                 Log.i(LOG_TAG, "transport=local");
            } else if (port == 23) {
                 transport = new TelnetTransport();
                 Log.i(LOG_TAG, "transport=telnet");
            } else {
                 transport = new SshTransport();
                 if (hostKeyVerifier != null) {
                     ((SshTransport) transport).setHostKeyVerifier(hostKeyVerifier);
                 }
                 Log.i(LOG_TAG, "transport=ssh");
            }

            transport.connect(host, port, username, password, authType, keyPath);
            Log.i(LOG_TAG, "transport connected");
            
            isConnected.set(true);
            Log.i(LOG_TAG, "state=connected");
            notifyConnected();
            
            // 启动读取循环
            Log.i(LOG_TAG, "start reading loop");
            startReading();

        } catch (Exception e) {
            Log.e(LOG_TAG, "connect error: " + e.getMessage(), e);
            notifyError("Error: " + e.getMessage());
            disconnect();
        }
    }

    /**
     * 开始读取数据循环
     * 持续从 Transport 读取数据并通知监听器。
     */
    private void startReading() {
        Log.i(LOG_TAG, "read loop started");
        byte[] buffer = new byte[8192];
        while (isConnected.get()) {
            try {
                int read = transport.read(buffer);
                if (read > 0) {
                    readBuffer.append(new String(buffer, 0, read));
                    long now = System.currentTimeMillis();
                    if (readBuffer.length() >= READ_BATCH_SIZE || now - lastReadDispatchTime >= MIN_FRAME_TIME) {
                        String data = readBuffer.toString();
                        readBuffer.setLength(0);
                        lastReadDispatchTime = now;
                        notifyData(data);
                    }
                } else {
                     if (readBuffer.length() > 0) {
                         long now = System.currentTimeMillis();
                         if (now - lastReadDispatchTime >= MIN_FRAME_TIME) {
                             String data = readBuffer.toString();
                             readBuffer.setLength(0);
                             lastReadDispatchTime = now;
                             notifyData(data);
                         }
                     }
                     // 非阻塞传输没有数据，稍作休眠
                     try {
                         Thread.sleep(10); 
                     } catch (InterruptedException e) {
                         Log.d(LOG_TAG, "read loop interrupted");
                         break;
                     }
                }
                if (transport instanceof SshTransport) {
                    SshTransport ssh = (SshTransport) transport;
                    int intervalSec = ssh.getKeepaliveIntervalSec();
                    if (intervalSec > 0) {
                        long now = System.currentTimeMillis();
                        if (now - lastKeepaliveTime >= intervalSec * 1000L) {
                            ssh.sendKeepalive();
                            lastKeepaliveTime = now;
                        }
                    }
                }
            } catch (Exception e) {
                Log.e(LOG_TAG, "read error: " + e.getMessage(), e);
                if (isConnected.get()) {
                    notifyError("Read error: " + e.getMessage());
                }
                break;
            }
        }
        if (readBuffer.length() > 0) {
            String data = readBuffer.toString();
            readBuffer.setLength(0);
            notifyData(data);
        }
        Log.i(LOG_TAG, "read loop exit -> disconnect");
        disconnect();
    }

    /**
     * 发送数据
     *
     * @param data 要发送的字符串数据
     */
    public void write(String data) {
        if (!isConnected.get() || transport == null) {
            Log.w("TerminalSession", "连接未就绪或transport为null，跳过发送");
            return;
        }
        writeExecutor.execute(() -> {
            Log.v(LOG_TAG, "write bytes=" + data.length());
            try {
                transport.write(data.getBytes());
            } catch (Exception e) {
                Log.e(LOG_TAG, "write error: " + e.getMessage(), e);
                notifyError("Write error: " + e.getMessage());
            }
        });
    }
    
    /**
     * 发送特殊按键
     * (待实现: 将特殊按键映射为 ANSI 序列)
     *
     * @param key 按键代码
     */
    public void sendSpecialKey(int key) {
        // Map special keys to ANSI sequences
        // TODO: Implement full mapping
    }

    /**
     * 开启本地端口转发 (仅 SSH)
     *
     * @param localPort  本地端口
     * @param targetHost 目标主机
     * @param targetPort 目标端口
     * @throws Exception 如果不支持转发或开启失败
     */
    public void startLocalForwarding(int localPort, String targetHost, int targetPort) throws Exception {
        if (transport instanceof SshTransport) {
            ((SshTransport) transport).startLocalForwarding(localPort, targetHost, targetPort);
        } else {
            throw new UnsupportedOperationException("Only SSH supports port forwarding");
        }
    }

    /**
     * 断开连接
     * 关闭 Transport 并释放资源。
     */
    public void disconnect() {
        isConnected.set(false);
        Log.v(LOG_TAG, "disconnect requested");
        Transport current = transport;
        transport = null;
        if (current != null) {
            current.disconnect();
        }
        writeExecutor.shutdownNow();
        notifyDisconnected();
    }

    // --- 通知辅助方法 ---

    private void notifyConnected() {
        if (listeners.isEmpty()) return;
        mainHandler.post(() -> {
            for (SessionListener l : listeners) {
                l.onConnected();
            }
        });
    }

    private void notifyDisconnected() {
        if (listeners.isEmpty()) return;
        mainHandler.post(() -> {
            for (SessionListener l : listeners) {
                l.onDisconnected();
            }
        });
    }

    private void notifyData(String data) {
        long now = System.currentTimeMillis();
        if (now - lastFrameTime >= MIN_FRAME_TIME) {
            // 立即发送数据
            lastFrameTime = now;
            mainHandler.post(() -> {
                if (!listeners.isEmpty()) {
                    for (SessionListener l : listeners) {
                        l.onDataReceived(data);
                    }
                }
            });
        } else {
            // 批量处理数据以减少渲染频率
            synchronized (pendingData) {
                pendingData.append(data);
                if (!pendingUpdateScheduled) {
                    pendingUpdateScheduled = true;
                    long delay = MIN_FRAME_TIME - (now - lastFrameTime);
                        mainHandler.postDelayed(() -> {
                            synchronized (pendingData) {
                                if (pendingData.length() > 0) {
                                    String batchData = pendingData.toString();
                                    pendingData.setLength(0);
                                    if (!listeners.isEmpty()) {
                                        for (SessionListener l : listeners) {
                                            l.onDataReceived(batchData);
                                        }
                                    }
                                }
                                pendingUpdateScheduled = false;
                            }
                        }, delay);
                }
            }
        }
    }

    private void notifyError(String msg) {
        if (listeners.isEmpty()) return;
        mainHandler.post(() -> {
            for (SessionListener l : listeners) {
                l.onError(msg);
            }
        });
    }

    public boolean isConnected() {
        return isConnected.get();
    }
    
    /**
     * 获取主机名
     */
    public String getHost() {
        return host;
    }
    
    /**
     * 获取端口
     */
    public int getPort() {
        return port;
    }
    
    /**
     * 获取用户名
     */
    public String getUsername() {
        return username;
    }

    /**
     * 获取认证类型
     */
    public int getAuthType() {
        return authType;
    }

    /**
     * 获取密钥路径
     */
    public String getKeyPath() {
        return keyPath;
    }

    /**
     * 获取密码
     */
    public String getPassword() {
        return password;
    }

    public void setEmulator(TerminalEmulator emulator) {
        this.emulator = emulator;
    }

    public TerminalEmulator getEmulator() {
        return emulator;
    }
}
