package com.orcterm.core.docker;

import java.io.Serializable;
import org.json.JSONObject;

/**
 * Docker 容器实体类
 */
public class DockerContainer implements Serializable {
    private static final long serialVersionUID = 1L;

    public String id;
    public String image;
    public String status;
    public String names;
    public String state; // running, exited
    public String createdAt;
    public String cpuUsage;
    public String memUsage;
    public String netIO;   // e.g. "1.2kB / 0B"
    public String blockIO; // e.g. "0B / 0B"

    public static DockerContainer fromJson(JSONObject json) {
        DockerContainer c = new DockerContainer();
        c.id = json.optString("ID");
        c.image = json.optString("Image");
        c.status = json.optString("Status");
        c.names = json.optString("Names");
        c.createdAt = json.optString("CreatedAt");
        // 根据状态字符串简单判断运行状态
        c.state = c.status.toLowerCase().contains("up") ? "running" : "exited";
        return c;
    }
}
