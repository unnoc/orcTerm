package com.orcterm.core.transport;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;

public class TelnetTransport implements Transport {
    private Socket socket;
    private InputStream input;
    private OutputStream output;
    private volatile boolean connected = false;

    @Override
    public void connect(String host, int port, String user, String password, int authType, String keyPath) throws Exception {
        socket = new Socket(host, port);
        socket.setKeepAlive(true);
        input = socket.getInputStream();
        output = socket.getOutputStream();
        connected = true;
    }

    @Override
    public void disconnect() {
        try {
            if (socket != null) {
                socket.close();
            }
        } catch (Exception ignored) {}
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
        // Send NAWS (Negotiate About Window Size) if supported
        // Requires Telnet protocol implementation. Skipped for now.
    }

    @Override
    public boolean isConnected() {
        return connected && socket != null && !socket.isClosed() && socket.isConnected();
    }
}
