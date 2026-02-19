package com.orcterm.core.session;

import android.text.TextUtils;

import com.orcterm.core.ssh.SshNative;
import com.orcterm.core.terminal.TerminalSession;

/**
 * Shared helper for acquiring SSH handles:
 * 1) Reuse an existing connected TerminalSession handle when available.
 * 2) Fall back to creating a new SSH connection and authenticating.
 */
public final class SessionConnector {

    private SessionConnector() {
    }

    public static final class Connection {
        private final long handle;
        private final boolean shared;

        public Connection(long handle, boolean shared) {
            this.handle = handle;
            this.shared = shared;
        }

        public long getHandle() {
            return handle;
        }

        public boolean isShared() {
            return shared;
        }
    }

    public static Connection acquire(
            SshNative sshNative,
            String hostname,
            int port,
            String username,
            String password,
            int authType,
            String keyPath,
            String connectFailMessage,
            String authFailMessage
    ) throws Exception {
        TerminalSession session = SessionManager.getInstance().findConnectedSession(hostname, port, username);
        if (session != null) {
            long handle = session.getHandle();
            if (handle != 0) {
                return new Connection(handle, true);
            }
        }

        return connectFresh(
                sshNative,
                hostname,
                port,
                username,
                password,
                authType,
                keyPath,
                connectFailMessage,
                authFailMessage
        );
    }

    public static Connection connectFresh(
            SshNative sshNative,
            String hostname,
            int port,
            String username,
            String password,
            int authType,
            String keyPath,
            String connectFailMessage,
            String authFailMessage
    ) throws Exception {
        long handle = connectAndAuthenticate(
                sshNative,
                hostname,
                port,
                username,
                password,
                authType,
                keyPath,
                connectFailMessage,
                authFailMessage
        );
        return new Connection(handle, false);
    }

    public static long connectOnly(
            SshNative sshNative,
            String hostname,
            int port,
            String connectFailMessage
    ) throws Exception {
        long handle = sshNative.connect(hostname, port);
        if (handle == 0) {
            throw new Exception(connectFailMessage);
        }
        return handle;
    }

    public static void authenticate(
            SshNative sshNative,
            long handle,
            String username,
            String password,
            int authType,
            String keyPath,
            String authFailMessage
    ) throws Exception {
        int auth;
        if (authType == 1 && !TextUtils.isEmpty(keyPath)) {
            auth = sshNative.authKey(handle, username, keyPath);
        } else {
            auth = sshNative.authPassword(handle, username, password);
        }
        if (auth != 0) {
            throw new Exception(authFailMessage);
        }
    }

    private static long connectAndAuthenticate(
            SshNative sshNative,
            String hostname,
            int port,
            String username,
            String password,
            int authType,
            String keyPath,
            String connectFailMessage,
            String authFailMessage
    ) throws Exception {
        long handle = connectOnly(sshNative, hostname, port, connectFailMessage);
        try {
            authenticate(sshNative, handle, username, password, authType, keyPath, authFailMessage);
        } catch (Exception e) {
            sshNative.disconnect(handle);
            throw e;
        }
        return handle;
    }
}
