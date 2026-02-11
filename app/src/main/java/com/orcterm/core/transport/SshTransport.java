package com.orcterm.core.transport;

import android.content.Context;
import android.content.SharedPreferences;

import com.orcterm.OrcTermApplication;
import com.orcterm.core.ssh.SshNative;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * SSH 传输层实现
 * 使用 JNI (SshNative) 实现 SSH 连接、认证、读写和端口转发功能。
 * 实现了 Transport 接口，提供标准的终端连接能力。
 */
public class SshTransport implements Transport {
    private final SshNative sshNative;
    private long sshHandle = 0; // 本地 SSH 句柄
    private volatile boolean connected = false;
    
    // 端口转发相关资源
    private final List<ServerSocket> forwardingSockets = new ArrayList<>();
    private final ExecutorService forwardExecutor = Executors.newCachedThreadPool();
    private final ConcurrentHashMap<String, Boolean> activeForwards = new ConcurrentHashMap<>();
    private HostKeyVerifier hostKeyVerifier;
    private int keepaliveIntervalSec = 0;

    public SshTransport() {
        this.sshNative = new SshNative();
    }

    public void setHostKeyVerifier(HostKeyVerifier verifier) {
        this.hostKeyVerifier = verifier;
    }
    
    // 使用已建立的 SSH 句柄接管连接，用于跨界面复用
    public void attachExistingHandle(long handle, int cols, int rows) throws Exception {
        if (handle == 0) {
            throw new Exception("Invalid handle");
        }
        this.sshHandle = handle;
        this.connected = true;
        sshNative.openShell(sshHandle, cols, rows);
    }

    @Override
    public void connect(String host, int port, String user, String password, int authType, String keyPath) throws Exception {
        android.util.Log.i("SSH_SESSION", "ssh connect host=" + host + " port=" + port + " user=" + user + " auth=" + authType);
        if (host == null || host.trim().isEmpty()) {
            throw new Exception("Host is empty");
        }
        if (user == null || user.trim().isEmpty()) {
            throw new Exception("User is empty");
        }
        if (authType == 1) {
            if (keyPath == null || keyPath.trim().isEmpty()) {
                throw new Exception("Key path is empty");
            }
        } else {
            if (password == null) {
                throw new Exception("Password is empty");
            }
        }
        // 建立 SSH 连接
        sshHandle = sshNative.connect(host, port);
        if (sshHandle == 0) {
            throw new Exception("Connection failed");
        }

        Context context = OrcTermApplication.getAppContext();
        if (context != null) {
            SharedPreferences prefs = context.getSharedPreferences("orcterm_prefs", Context.MODE_PRIVATE);
            int connectTimeoutSec = prefs.getInt("ssh_connect_timeout_sec", 10);
            int readTimeoutSec = prefs.getInt("ssh_read_timeout_sec", 60);
            int keepaliveIntervalSec = prefs.getInt("ssh_keepalive_interval_sec", 0);
            boolean keepaliveReply = prefs.getBoolean("ssh_keepalive_reply", true);
            if (connectTimeoutSec > 0) {
                sshNative.setSessionTimeout(sshHandle, connectTimeoutSec * 1000);
            }
            sshNative.setSessionReadTimeout(sshHandle, readTimeoutSec);
            sshNative.setKeepaliveConfig(sshHandle, keepaliveReply, Math.max(0, keepaliveIntervalSec));
            this.keepaliveIntervalSec = Math.max(0, keepaliveIntervalSec);
        }

        // Host Key Verification
        if (context != null) {
            java.io.File knownHostsFile = new java.io.File(context.getFilesDir(), "known_hosts");
            String knownHostsPath = knownHostsFile.getAbsolutePath();
            
            // Check host key
            int checkResult = sshNative.knownHostsCheck(sshHandle, host, port, knownHostsPath);
            
            // 0: MATCH, 1: MISMATCH, 2: NOTFOUND, 3: FAILURE
            if (checkResult != 0) {
                String fingerprint = sshNative.getHostKeyInfo(sshHandle);
                boolean trusted = false;
                
                if (hostKeyVerifier != null) {
                    trusted = hostKeyVerifier.verify(host, port, fingerprint, checkResult);
                }
                
                if (!trusted) {
                    sshNative.disconnect(sshHandle);
                    sshHandle = 0;
                    throw new Exception("Host key verification failed");
                }
                
                // If trusted and was not found or mismatch, update known_hosts
                if (checkResult == 2 || checkResult == 1) { 
                    sshNative.knownHostsAdd(sshHandle, host, port, knownHostsPath, "");
                }
            }
        }

        int authResult;
        // 根据认证类型进行认证 (1: 密钥, 0: 密码)
        if (authType == 1 && keyPath != null) {
            authResult = sshNative.authKey(sshHandle, user, keyPath);
        } else {
            authResult = sshNative.authPassword(sshHandle, user, password);
        }

        if (authResult != 0) {
            sshNative.disconnect(sshHandle);
            sshHandle = 0;
            throw new Exception("Authentication failed");
        }

        // 打开 Shell，默认大小，稍后会调整
        sshNative.openShell(sshHandle, 80, 24);
        connected = true;
        android.util.Log.i("SSH_SESSION", "ssh connected handle=" + sshHandle);
    }

    @Override
    public void disconnect() {
        stopAllForwards();
        if (sshHandle != 0) {
            sshNative.disconnect(sshHandle);
            sshHandle = 0;
        }
        connected = false;
        forwardExecutor.shutdownNow();
        android.util.Log.i("SSH_SESSION", "ssh disconnected");
    }

    @Override
    public void write(byte[] data) throws Exception {
        if (!connected || sshHandle == 0) return;
        sshNative.write(sshHandle, data);
    }

    @Override
    public int read(byte[] buffer) throws Exception {
        if (!connected || sshHandle == 0) return -1;
        byte[] data = sshNative.read(sshHandle);
        if (data == null || data.length == 0) return 0;
        
        // 将读取的数据复制到缓冲区
        System.arraycopy(data, 0, buffer, 0, Math.min(data.length, buffer.length));
        return Math.min(data.length, buffer.length);
    }

    @Override
    public void resize(int cols, int rows) {
        if (connected && sshHandle != 0) {
            sshNative.resize(sshHandle, cols, rows);
        }
    }

    @Override
    public boolean isConnected() {
        return connected;
    }

    @Override
    public long getHandle() {
        return sshHandle;
    }

    public void sendKeepalive() {
        if (connected && sshHandle != 0) {
            sshNative.sendKeepalive(sshHandle);
            android.util.Log.d("SSH_SESSION", "keepalive sent");
        }
    }

    public int getKeepaliveIntervalSec() {
        return keepaliveIntervalSec;
    }

    // --- 端口转发功能 ---

    /**
     * 开启本地端口转发
     * 将本地端口流量转发到远程目标主机。
     *
     * @param localPort  本地监听端口
     * @param targetHost 目标主机地址
     * @param targetPort 目标端口
     * @throws IOException 开启失败时抛出异常
     */
    public void startLocalForwarding(int localPort, String targetHost, int targetPort) throws IOException {
        if (!connected || sshHandle == 0) {
            throw new IOException("SSH not connected");
        }
        
        String key = localPort + ":" + targetHost + ":" + targetPort;
        if (activeForwards.containsKey(key)) {
            return; // 转发已存在
        }

        ServerSocket serverSocket = new ServerSocket(localPort);
        forwardingSockets.add(serverSocket);
        activeForwards.put(key, true);

        // 异步处理转发连接
        forwardExecutor.execute(() -> {
            try {
                while (connected && !serverSocket.isClosed()) {
                    Socket clientSocket = serverSocket.accept();
                    handleForwardConnection(clientSocket, targetHost, targetPort);
                }
            } catch (IOException e) {
                // Socket 关闭或出错
            } finally {
                activeForwards.remove(key);
            }
        });
    }

    /**
     * 处理单个转发连接
     * 在本地 Socket 和 SSH 通道之间建立数据传输。
     */
    private void handleForwardConnection(Socket clientSocket, String targetHost, int targetPort) {
        forwardExecutor.execute(() -> {
            long channel = 0;
            try {
                // 打开直接 TCP/IP 通道
                channel = sshNative.openDirectTcpIp(sshHandle, targetHost, targetPort);
                if (channel == 0) {
                    clientSocket.close();
                    return;
                }

                final long channelHandle = channel;
                final InputStream socketIn = clientSocket.getInputStream();
                final OutputStream socketOut = clientSocket.getOutputStream();

                // 线程: Socket -> SSH Channel
                forwardExecutor.execute(() -> {
                    try {
                        byte[] buffer = new byte[8192];
                        int read;
                        while ((read = socketIn.read(buffer)) != -1) {
                            if (sshNative.writeChannel(sshHandle, channelHandle, java.util.Arrays.copyOf(buffer, read)) < 0) {
                                break;
                            }
                        }
                    } catch (Exception e) {
                        // 忽略异常
                    } finally {
                        try { clientSocket.shutdownInput(); } catch (Exception ignored) {}
                    }
                });

                // 线程: SSH Channel -> Socket
                // 使用当前线程处理读取
                try {
                    while (connected && !clientSocket.isClosed()) {
                        byte[] data = sshNative.readChannel(sshHandle, channelHandle);
                        if (data == null) {
                             // EOF 或 错误
                             break;
                        }
                        if (data.length > 0) {
                            socketOut.write(data);
                            socketOut.flush();
                        } else {
                            // 简单的轮询，如果 JNI 支持阻塞读取会更好
                            Thread.sleep(10);
                        }
                    }
                } catch (Exception e) {
                   // 忽略异常
                } finally {
                     sshNative.closeChannel(channelHandle);
                     try { clientSocket.close(); } catch (Exception ignored) {}
                }

            } catch (Exception e) {
                if (channel != 0) sshNative.closeChannel(channel);
                try { clientSocket.close(); } catch (Exception ignored) {}
            }
        });
    }

    /**
     * 停止所有端口转发
     */
    public void stopAllForwards() {
        for (ServerSocket ss : forwardingSockets) {
            try {
                ss.close();
            } catch (IOException ignored) {}
        }
        forwardingSockets.clear();
        activeForwards.clear();
    }
}
