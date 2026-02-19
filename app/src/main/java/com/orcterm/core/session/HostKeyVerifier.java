package com.orcterm.core.session;

import com.orcterm.core.ssh.SshNative;

/**
 * Host key verification helper that is independent from UI.
 */
public final class HostKeyVerifier {

    public static final int CHECK_OK = 0;
    public static final int CHECK_CHANGED = 1;
    public static final int CHECK_UNKNOWN = 2;

    public static final int DECISION_REJECT = -1;
    public static final int DECISION_TRUST_AND_SAVE = 1;
    public static final int DECISION_TRUST_ONCE = 2;

    private HostKeyVerifier() {
    }

    public interface ConfirmationHandler {
        int confirm(Challenge challenge) throws Exception;
    }

    public static final class HostKeyInfo {
        private final String keyType;
        private final String fingerprint;

        public HostKeyInfo(String keyType, String fingerprint) {
            this.keyType = keyType;
            this.fingerprint = fingerprint;
        }

        public String getKeyType() {
            return keyType;
        }

        public String getFingerprint() {
            return fingerprint;
        }
    }

    public static final class Challenge {
        private final String hostname;
        private final int port;
        private final String username;
        private final String keyType;
        private final String fingerprint;
        private final int reason;

        public Challenge(String hostname, int port, String username, String keyType, String fingerprint, int reason) {
            this.hostname = hostname;
            this.port = port;
            this.username = username;
            this.keyType = keyType;
            this.fingerprint = fingerprint;
            this.reason = reason;
        }

        public String getHostname() {
            return hostname;
        }

        public int getPort() {
            return port;
        }

        public String getUsername() {
            return username;
        }

        public String getKeyType() {
            return keyType;
        }

        public String getFingerprint() {
            return fingerprint;
        }

        public int getReason() {
            return reason;
        }
    }

    public static final class VerifyResult {
        private final int checkCode;
        private final boolean trusted;

        public VerifyResult(int checkCode, boolean trusted) {
            this.checkCode = checkCode;
            this.trusted = trusted;
        }

        public int getCheckCode() {
            return checkCode;
        }

        public boolean isTrusted() {
            return trusted;
        }
    }

    public static HostKeyInfo parseHostKeyInfo(String info) {
        if (info == null || info.isEmpty()) {
            return new HostKeyInfo("-", "-");
        }
        String[] parts = info.split("\\|");
        if (parts.length >= 2) {
            return new HostKeyInfo(parts[0], parts[1]);
        }
        return new HostKeyInfo(info, "-");
    }

    public static VerifyResult verify(
            SshNative sshNative,
            long handle,
            String hostname,
            int port,
            String username,
            String knownHostsPath,
            int policy,
            HostKeyInfo hostKeyInfo,
            ConfirmationHandler confirmationHandler
    ) throws Exception {
        int checkCode = sshNative.knownHostsCheck(handle, hostname, port, knownHostsPath);
        if (checkCode == CHECK_OK) {
            return new VerifyResult(checkCode, true);
        }
        if (checkCode != CHECK_CHANGED && checkCode != CHECK_UNKNOWN) {
            throw new Exception("Host Key 校验失败");
        }
        if (policy == 0) {
            throw new Exception(checkCode == CHECK_CHANGED ? "Host Key 不匹配" : "Host Key 未知");
        }
        if (confirmationHandler == null) {
            throw new Exception("Host Key 校验需要确认");
        }

        int decision = confirmationHandler.confirm(new Challenge(
                hostname,
                port,
                username,
                hostKeyInfo == null ? "-" : hostKeyInfo.getKeyType(),
                hostKeyInfo == null ? "-" : hostKeyInfo.getFingerprint(),
                checkCode
        ));
        if (decision == DECISION_REJECT) {
            throw new Exception("已拒绝连接");
        }
        if (decision == DECISION_TRUST_AND_SAVE) {
            int addRc = sshNative.knownHostsAdd(handle, hostname, port, knownHostsPath, "orcterm");
            if (addRc != 0) {
                throw new Exception("写入 known_hosts 失败");
            }
        }
        return new VerifyResult(checkCode, true);
    }
}

