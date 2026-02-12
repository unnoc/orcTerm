package com.orcterm.core.transport;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.Map;

/**
 * 本地 Shell 传输实现
 */
public class LocalTransport implements Transport {
    private Process process;
    private InputStream input;
    private OutputStream output;
    private volatile boolean connected = false;

    @Override
    public void connect(String host, int port, String user, String password, int authType, String keyPath) throws Exception {
        ProcessBuilder pb = new ProcessBuilder("/system/bin/sh");
        Map<String, String> env = pb.environment();
        env.put("TERM", "xterm-256color");
        // env.put("HOME", "/sdcard"); // Optional
        
        // Redirect error stream to input stream to capture stderr
        pb.redirectErrorStream(true);
        
        process = pb.start();
        input = process.getInputStream();
        output = process.getOutputStream();
        connected = true;
    }

    @Override
    public void disconnect() {
        if (process != null) {
            process.destroy();
            process = null;
        }
        connected = false;
    }

    @Override
    public void write(byte[] data) throws Exception {
        if (output != null) {
            output.write(data);
            output.flush();
        }
    }

    @Override
    public int read(byte[] buffer) throws Exception {
        if (input == null) return -1;
        return input.read(buffer);
    }

    @Override
    public void resize(int cols, int rows) {
        // Java Process 无法直接调整 PTY，当前实现忽略
    }

    @Override
    public boolean isConnected() {
        return connected && process != null && (android.os.Build.VERSION.SDK_INT >= 26 ? process.isAlive() : true);
    }
}
