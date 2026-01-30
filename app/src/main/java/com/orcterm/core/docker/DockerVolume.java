package com.orcterm.core.docker;

import org.json.JSONObject;

/**
 * Docker 存储卷实体类
 */
public class DockerVolume {
    public String name;
    public String driver;
    public String mountpoint;

    public static DockerVolume fromJson(JSONObject json) {
        DockerVolume v = new DockerVolume();
        v.name = json.optString("Name");
        v.driver = json.optString("Driver");
        v.mountpoint = json.optString("Mountpoint");
        return v;
    }
}
