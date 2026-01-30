package com.orcterm.core.transport;

/**
 * 传输层接口
 * 定义了终端连接的基本操作，如连接、断开、读写数据和调整大小。
 * 支持多种协议（如 SSH, Telnet, Local）的实现。
 */
public interface Transport {
    /**
     * 建立连接
     *
     * @param host     目标主机地址
     * @param port     目标端口
     * @param user     用户名
     * @param password 密码
     * @param authType 认证类型 (0: 密码, 1: 密钥)
     * @param keyPath  私钥文件路径 (仅密钥认证时需要)
     * @throws Exception 连接失败时抛出异常
     */
    void connect(String host, int port, String user, String password, int authType, String keyPath) throws Exception;

    /**
     * 断开连接
     * 释放相关资源。
     */
    void disconnect();

    /**
     * 写入数据
     * 将数据发送到远程主机。
     *
     * @param data 要发送的字节数组
     * @throws Exception 写入失败时抛出异常
     */
    void write(byte[] data) throws Exception;

    /**
     * 读取数据
     * 从远程主机读取数据到缓冲区。
     *
     * @param buffer 用于存储读取数据的缓冲区
     * @return 读取的字节数，如果到达流末尾则返回 -1
     * @throws Exception 读取失败时抛出异常
     */
    int read(byte[] buffer) throws Exception;

    /**
     * 调整终端大小
     * 通知远程主机终端窗口大小发生变化。
     *
     * @param cols 列数
     * @param rows 行数
     */
    void resize(int cols, int rows);

    /**
     * 检查连接状态
     *
     * @return 如果已连接返回 true，否则返回 false
     */
    boolean isConnected();
}
