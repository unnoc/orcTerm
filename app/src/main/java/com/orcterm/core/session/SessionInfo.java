package com.orcterm.core.session;

/**
 * 会话信息数据模型
 */
public class SessionInfo {
    public long id;
    public String name;
    public String hostname;
    public int port;
    public String username;
    public String password;
    public int authType;
    public String keyPath;
    public boolean connected;
    public long timestamp;

    public SessionInfo(long id, String name, String hostname, int port, String username, String password, int authType, String keyPath, boolean connected) {
        this.id = id;
        this.name = name;
        this.hostname = hostname;
        this.port = port;
        this.username = username;
        this.password = password;
        this.authType = authType;
        this.keyPath = keyPath;
        this.connected = connected;
        this.timestamp = System.currentTimeMillis();
    }
}
