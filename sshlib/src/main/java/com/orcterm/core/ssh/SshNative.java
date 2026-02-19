package com.orcterm.core.ssh;

/**
 * SSH 本地接口类
 * 负责调用 JNI 层实现 SSH 连接、Shell 会话、SFTP 文件操作及端口转发功能。
 * 通过加载 'orcterm-jni' 动态库与底层 C 代码交互。
 */
public class SshNative {
    static {
        System.loadLibrary("orcterm-jni");
    }

    // Native methods
    
    /**
     * 连接到 SSH 服务器
     * 建立 TCP 连接并完成 SSH 握手。
     *
     * @param host 主机名或 IP 地址
     * @param port 端口号 (通常为 22)
     * @return 会话上下文句柄 (SshContext 指针地址)，连接失败返回 0
     */
    public native long connect(String host, int port);

    /**
     * 使用密码验证身份
     *
     * @param handle 会话句柄
     * @param user   用户名
     * @param pwd    密码
     * @return 0 表示验证成功，非 0 表示失败
     */
    public native int authPassword(long handle, String user, String pwd);

    /**
     * 使用私钥验证身份
     *
     * @param handle         会话句柄
     * @param user           用户名
     * @param privateKeyPath 私钥文件绝对路径
     * @return 0 表示验证成功，非 0 表示失败
     */
    public native int authKey(long handle, String user, String privateKeyPath);

    public native int authKeyWithPassphrase(long handle, String user, String privateKeyPath, String passphrase);

    /**
     * 打开交互式 Shell 通道
     * 请求 PTY 并启动 Shell。
     *
     * @param handle 会话句柄
     * @param cols   初始终端列数
     * @param rows   初始终端行数
     * @return 0 表示成功，非 0 表示失败
     */
    public native int openShell(long handle, int cols, int rows);

    /**
     * 调整终端窗口大小
     * 向服务器发送 Window Change 请求。
     *
     * @param handle 会话句柄
     * @param cols   新列数
     * @param rows   新行数
     * @return 0 表示成功
     */
    public native int resize(long handle, int cols, int rows);

    /**
     * 执行单条命令 (Exec 模式)
     * 用于非交互式命令执行。
     *
     * @param handle  会话句柄
     * @param command 要执行的命令字符串
     * @return 命令的标准输出结果
     */
    public native String exec(long handle, String command);

    public native String execWithResult(long handle, String command);

    /**
     * SFTP: 列出目录内容
     *
     * @param handle 会话句柄
     * @param path   远程目录路径
     * @return JSON 格式的文件列表字符串
     */
    public native String sftpList(long handle, String path);

    public native int sftpUpload(long handle, String localPath, String remotePath);

    public native int sftpDownload(long handle, String remotePath, String localPath);

    /**
     * 写入数据到 Shell 通道
     *
     * @param handle 会话句柄
     * @param data   要发送的字节数据
     * @return 实际写入的字节数
     */
    public native int write(long handle, byte[] data);

    /**
     * 从 Shell 通道读取数据
     *
     * @param handle 会话句柄
     * @return 读取到的字节数组，如果没有数据返回空数组
     */
    public native byte[] read(long handle);

    /**
     * 断开连接并释放资源
     * 关闭通道、会话和 Socket，释放本地内存。
     *
     * @param handle 会话句柄
     */
    public native void disconnect(long handle);

    public native void setSessionTimeout(long handle, int timeoutMs);

    public native void setSessionReadTimeout(long handle, int timeoutSec);

    public native void setKeepaliveConfig(long handle, boolean wantReply, int intervalSec);

    public native int sendKeepalive(long handle);

    public native int knownHostsCheck(long handle, String host, int port, String knownHostsPath);

    public native int knownHostsAdd(long handle, String host, int port, String knownHostsPath, String comment);

    public native String getHostKeyInfo(long handle);

    /**
     * 生成 Ed25519 密钥对
     *
     * @param privateKeyPath 私钥保存路径 (公钥会自动添加 .pub 后缀)
     * @return 0 表示成功
     */
    public native int generateKeyPair(String privateKeyPath);

    /**
     * 发送绑定请求 (Scan to Connect)
     * 通过 SSH 隧道发送 HTTP 请求到本地服务器。
     *
     * @param handle  会话句柄
     * @param apiPort API 端口
     * @param token   验证令牌
     * @param pubKey  公钥内容
     * @return 响应字符串 (OK 或错误信息)
     */
    public native String sendBindRequest(long handle, int apiPort, String token, String pubKey);

    // --- 端口转发 API (Port Forwarding) ---

    /**
     * 打开直接 TCP/IP 通道 (本地端口转发)
     * 请求服务器打开一个连接到目标主机和端口的通道。
     *
     * @param handle     会话句柄
     * @param targetHost 目标主机地址
     * @param targetPort 目标端口
     * @return 通道句柄 (Channel 指针)，失败返回 0
     */
    public native long openDirectTcpIp(long handle, String targetHost, int targetPort);

    /**
     * 写入数据到指定通道
     * 用于端口转发的数据发送。
     *
     * @param handle        会话句柄
     * @param channelHandle 通道句柄
     * @param data          数据
     * @return 写入字节数
     */
    public native int writeChannel(long handle, long channelHandle, byte[] data);

    /**
     * 从指定通道读取数据
     * 用于端口转发的数据接收。
     *
     * @param handle        会话句柄
     * @param channelHandle 通道句柄
     * @return 读取到的数据，返回 NULL 表示 EOF 或错误
     */
    public native byte[] readChannel(long handle, long channelHandle);

    /**
     * 关闭指定通道
     *
     * @param channelHandle 通道句柄
     */
    public native void closeChannel(long channelHandle);
}
