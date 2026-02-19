package com.orcterm.core.transport;

import android.content.Context;
import android.content.SharedPreferences;

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
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

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
    private final int forwardPoolSize = Math.max(2, Runtime.getRuntime().availableProcessors());
    private final ExecutorService forwardAcceptExecutor = Executors.newSingleThreadExecutor();
    private final ExecutorService forwardWorkerExecutor = Executors.newFixedThreadPool(forwardPoolSize);
    private final ConcurrentHashMap<String, Boolean> activeForwards = new ConcurrentHashMap<>();
    private HostKeyVerifier hostKeyVerifier;
    private int keepaliveIntervalSec = 0;
    private HostKeyPolicy hostKeyPolicy = HostKeyPolicy.TRUST_ON_FIRST_USE;

    public enum HostKeyPolicy {
        TRUST_ON_FIRST_USE,
        STRICT,
        ACCEPT_MISMATCH
    }

    public SshTransport() {
        this.sshNative = new SshNative();
        if (forwardWorkerExecutor instanceof ThreadPoolExecutor) {
            ThreadPoolExecutor pool = (ThreadPoolExecutor) forwardWorkerExecutor;
            pool.setKeepAliveTime(30, TimeUnit.SECONDS);
            pool.allowCoreThreadTimeOut(true);
        }
    }

    public void setHostKeyVerifier(HostKeyVerifier verifier) {
        this.hostKeyVerifier = verifier;
    }

    public void setHostKeyPolicy(HostKeyPolicy policy) {
        if (policy != null) {
            this.hostKeyPolicy = policy;
        }
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
        connectInternal(host, port, user, password, authType, keyPath, null);
    }

    public void connectWithPassphrase(String host, int port, String user, String passphrase, String keyPath) throws Exception {
        connectInternal(host, port, user, null, 1, keyPath, passphrase);
    }

    private void connectInternal(String host, int port, String user, String password, int authType, String keyPath, String passphrase) throws Exception {
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
        sshHandle = sshNative.connect(host, port);
        if (sshHandle == 0) {
            throw new Exception("Connection failed");
        }

        Context context = null;
        try {
            Object app = Class.forName("android.app.ActivityThread").getMethod("currentApplication").invoke(null);
            if (app instanceof Context) {
                context = (Context) app;
            }
        } catch (Exception ignored) {}
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

        if (context != null) {
            java.io.File knownHostsFile = new java.io.File(context.getFilesDir(), "known_hosts");
            String knownHostsPath = knownHostsFile.getAbsolutePath();

            int checkResult = sshNative.knownHostsCheck(sshHandle, host, port, knownHostsPath);

            if (checkResult != 0) {
                String fingerprint = sshNative.getHostKeyInfo(sshHandle);
                boolean trusted = false;

                if (hostKeyVerifier != null) {
                    trusted = hostKeyVerifier.verify(host, port, fingerprint, checkResult);
                }

                if (checkResult == 1 && hostKeyPolicy == HostKeyPolicy.STRICT) {
                    trusted = false;
                } else if (checkResult == 2 && hostKeyPolicy == HostKeyPolicy.STRICT) {
                    trusted = false;
                } else if (checkResult == 1 && hostKeyPolicy == HostKeyPolicy.TRUST_ON_FIRST_USE) {
                    trusted = false;
                }

                if (!trusted) {
                    sshNative.disconnect(sshHandle);
                    sshHandle = 0;
                    throw new Exception("Host key verification failed");
                }

                if (checkResult == 2 || checkResult == 1) {
                    sshNative.knownHostsAdd(sshHandle, host, port, knownHostsPath, "");
                }
            }
        }

        int authResult;
        if (authType == 1 && keyPath != null) {
            if (passphrase != null) {
                authResult = sshNative.authKeyWithPassphrase(sshHandle, user, keyPath, passphrase);
            } else {
                authResult = sshNative.authKey(sshHandle, user, keyPath);
            }
        } else {
            authResult = sshNative.authPassword(sshHandle, user, password);
        }

        if (authResult != 0) {
            sshNative.disconnect(sshHandle);
            sshHandle = 0;
            throw new Exception("Authentication failed");
        }

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
        forwardAcceptExecutor.shutdownNow();
        forwardWorkerExecutor.shutdownNow();
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

    public String execWithResult(String command) throws Exception {
        if (!connected || sshHandle == 0) {
            throw new Exception("SSH not connected");
        }
        return sshNative.execWithResult(sshHandle, command);
    }

    public int sftpUpload(String localPath, String remotePath) throws Exception {
        if (!connected || sshHandle == 0) {
            throw new Exception("SSH not connected");
        }
        return sshNative.sftpUpload(sshHandle, localPath, remotePath);
    }

    public int sftpDownload(String remotePath, String localPath) throws Exception {
        if (!connected || sshHandle == 0) {
            throw new Exception("SSH not connected");
        }
        return sshNative.sftpDownload(sshHandle, remotePath, localPath);
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

        forwardAcceptExecutor.execute(() -> {
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
        forwardWorkerExecutor.execute(() -> {
            long channel = 0;
            try {
                channel = sshNative.openDirectTcpIp(sshHandle, targetHost, targetPort);
                if (channel == 0) {
                    clientSocket.close();
                    return;
                }

                final long channelHandle = channel;
                final InputStream socketIn = clientSocket.getInputStream();
                final OutputStream socketOut = clientSocket.getOutputStream();

                forwardWorkerExecutor.execute(() -> {
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

    public void startDynamicForwarding(int localPort) throws IOException {
        if (!connected || sshHandle == 0) {
            throw new IOException("SSH not connected");
        }
        String key = "dynamic:" + localPort;
        if (activeForwards.containsKey(key)) {
            return;
        }
        ServerSocket serverSocket = new ServerSocket(localPort);
        forwardingSockets.add(serverSocket);
        activeForwards.put(key, true);
        forwardAcceptExecutor.execute(() -> {
            try {
                while (connected && !serverSocket.isClosed()) {
                    Socket clientSocket = serverSocket.accept();
                    handleDynamicForwardConnection(clientSocket);
                }
            } catch (IOException e) {
            } finally {
                activeForwards.remove(key);
            }
        });
    }

    private void handleDynamicForwardConnection(Socket clientSocket) {
        forwardWorkerExecutor.execute(() -> {
            long channel = 0;
            try {
                InputStream socketIn = clientSocket.getInputStream();
                OutputStream socketOut = clientSocket.getOutputStream();

                int version = socketIn.read();
                if (version != 0x05) {
                    clientSocket.close();
                    return;
                }
                int nMethods = socketIn.read();
                if (nMethods <= 0) {
                    clientSocket.close();
                    return;
                }
                byte[] methods = readFully(socketIn, nMethods);
                if (methods == null) {
                    clientSocket.close();
                    return;
                }
                socketOut.write(new byte[] {0x05, 0x00});
                socketOut.flush();

                int reqVer = socketIn.read();
                int cmd = socketIn.read();
                int rsv = socketIn.read();
                int atyp = socketIn.read();
                if (reqVer != 0x05 || rsv != 0x00) {
                    sendSocksFail(socketOut);
                    clientSocket.close();
                    return;
                }
                if (cmd != 0x01) {
                    sendSocksFail(socketOut);
                    clientSocket.close();
                    return;
                }

                String host;
                if (atyp == 0x01) {
                    byte[] addr = readFully(socketIn, 4);
                    if (addr == null) {
                        sendSocksFail(socketOut);
                        clientSocket.close();
                        return;
                    }
                    host = (addr[0] & 0xff) + "." + (addr[1] & 0xff) + "." + (addr[2] & 0xff) + "." + (addr[3] & 0xff);
                } else if (atyp == 0x03) {
                    int len = socketIn.read();
                    if (len <= 0) {
                        sendSocksFail(socketOut);
                        clientSocket.close();
                        return;
                    }
                    byte[] addr = readFully(socketIn, len);
                    if (addr == null) {
                        sendSocksFail(socketOut);
                        clientSocket.close();
                        return;
                    }
                    host = new String(addr);
                } else if (atyp == 0x04) {
                    byte[] addr = readFully(socketIn, 16);
                    if (addr == null) {
                        sendSocksFail(socketOut);
                        clientSocket.close();
                        return;
                    }
                    java.net.InetAddress inetAddress = java.net.InetAddress.getByAddress(addr);
                    host = inetAddress.getHostAddress();
                } else {
                    sendSocksFail(socketOut);
                    clientSocket.close();
                    return;
                }

                byte[] portBytes = readFully(socketIn, 2);
                if (portBytes == null) {
                    sendSocksFail(socketOut);
                    clientSocket.close();
                    return;
                }
                int targetPort = ((portBytes[0] & 0xff) << 8) | (portBytes[1] & 0xff);

                channel = sshNative.openDirectTcpIp(sshHandle, host, targetPort);
                if (channel == 0) {
                    sendSocksFail(socketOut);
                    clientSocket.close();
                    return;
                }

                socketOut.write(new byte[] {0x05, 0x00, 0x00, 0x01, 0, 0, 0, 0, 0, 0});
                socketOut.flush();

                final long channelHandle = channel;

                forwardWorkerExecutor.execute(() -> {
                    try {
                        byte[] buffer = new byte[8192];
                        int read;
                        while ((read = socketIn.read(buffer)) != -1) {
                            if (sshNative.writeChannel(sshHandle, channelHandle, java.util.Arrays.copyOf(buffer, read)) < 0) {
                                break;
                            }
                        }
                    } catch (Exception e) {
                    } finally {
                        try { clientSocket.shutdownInput(); } catch (Exception ignored) {}
                    }
                });

                try {
                    while (connected && !clientSocket.isClosed()) {
                        byte[] data = sshNative.readChannel(sshHandle, channelHandle);
                        if (data == null) {
                            break;
                        }
                        if (data.length > 0) {
                            socketOut.write(data);
                            socketOut.flush();
                        } else {
                            Thread.sleep(10);
                        }
                    }
                } catch (Exception e) {
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

    private static byte[] readFully(InputStream in, int len) throws IOException {
        byte[] data = new byte[len];
        int read = 0;
        while (read < len) {
            int r = in.read(data, read, len - read);
            if (r == -1) return null;
            read += r;
        }
        return data;
    }

    private static void sendSocksFail(OutputStream out) {
        try {
            out.write(new byte[] {0x05, 0x01, 0x00, 0x01, 0, 0, 0, 0, 0, 0});
            out.flush();
        } catch (Exception e) {
        }
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
