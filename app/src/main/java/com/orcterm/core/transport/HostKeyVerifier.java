package com.orcterm.core.transport;

/**
 * SSH 主机指纹校验接口
 */
public interface HostKeyVerifier {
    /**
     * 校验主机指纹
     *
     * @param host 主机名
     * @param port 端口
     * @param fingerprint 指纹（SHA256）
     * @param status 校验状态（0: 匹配, 1: 不匹配, 2: 未找到, 3: 失败）
     * @return 是否信任
     */
    boolean verify(String host, int port, String fingerprint, int status);
}
