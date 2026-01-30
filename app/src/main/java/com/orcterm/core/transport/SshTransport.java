package com.orcterm.core.transport;

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

    public SshTransport() {
        this.sshNative = new SshNative();
    }

    @Override
    public void connect(String host, int port, String user, String password, int authType, String keyPath) throws Exception {
        // 建立 SSH 连接
        sshHandle = sshNative.connect(host, port);
        if (sshHandle == 0) {
            throw new Exception("Connection failed");
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
        return data.length;
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
