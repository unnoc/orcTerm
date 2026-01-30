package com.orcterm.data;

import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.PrimaryKey;

/**
 * 主机实体类，映射数据库中的 'hosts' 表
 */
@Entity(tableName = "hosts")
public class HostEntity {
    @PrimaryKey(autoGenerate = true)
    public long id;

    public String alias; // 别名
    public String hostname; // 主机名或IP
    public int port = 22; // 端口
    public String username; // 用户名
    
    // 密码 (实际生产环境建议加密存储，此处为演示目的直接存储)
    public String password; 
    public String keyPath; // 密钥路径
    
    // 认证类型: 0 = 密码, 1 = 密钥, 2 = 代理
    public int authType; 
    
    public long lastConnected; // 最后连接时间
    
    // New fields for OS info and Status
    public String osName;
    public String osVersion;
    public String containerEngine; // "docker" or "podman"
    public String status; // "online", "offline", "unknown"

    public HostEntity() {
    }

    @Ignore
    public HostEntity(String alias, String hostname, String username) {
        this.alias = alias;
        this.hostname = hostname;
        this.username = username;
    }
}
