package com.orcterm.core.docker;

import org.json.JSONObject;

/**
 * Docker 网络实体类
 */
public class DockerNetwork {
    public String id;
    public String name;
    public String driver;
    public String scope;

    public static DockerNetwork fromJson(JSONObject json) {
        DockerNetwork n = new DockerNetwork();
        n.id = json.optString("ID");
        n.name = json.optString("Name");
        n.driver = json.optString("Driver");
        n.scope = json.optString("Scope");
        return n;
    }
}
