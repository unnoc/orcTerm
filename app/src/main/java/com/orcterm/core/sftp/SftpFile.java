package com.orcterm.core.sftp;

import org.json.JSONObject;

public class SftpFile {
    public String name;
    public boolean isDir;
    public long size;
    public String perm;
    public long mtime;

    public static SftpFile fromJson(JSONObject json) {
        SftpFile f = new SftpFile();
        f.name = json.optString("name");
        f.isDir = json.optBoolean("isDir");
        f.size = json.optLong("size");
        f.perm = json.optString("perm");
        f.mtime = json.optLong("mtime");
        return f;
    }
}
