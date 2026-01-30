package com.orcterm.core.docker;

import org.json.JSONObject;

/**
 * Docker 镜像实体类
 */
public class DockerImage {
    public String id;
    public String repository;
    public String tag;
    public String size;
    public String createdSince;

    public static DockerImage fromJson(JSONObject json) {
        DockerImage i = new DockerImage();
        i.id = json.optString("ID");
        i.repository = json.optString("Repository");
        i.tag = json.optString("Tag");
        i.size = json.optString("Size");
        i.createdSince = json.optString("CreatedSince");
        return i;
    }
}
