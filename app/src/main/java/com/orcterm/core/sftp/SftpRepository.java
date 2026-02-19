package com.orcterm.core.sftp;

import com.orcterm.core.ssh.SshNative;

import org.json.JSONArray;

import java.util.ArrayList;
import java.util.List;

/**
 * SFTP data access layer for directory listing.
 */
public final class SftpRepository {

    private final SshNative sshNative;

    public SftpRepository(SshNative sshNative) {
        this.sshNative = sshNative;
    }

    public String fetchListResponse(long sshHandle, String path) {
        return sshNative.sftpList(sshHandle, path);
    }

    public boolean isValidListResponse(String response) {
        return response != null && response.trim().startsWith("[");
    }

    public List<SftpFile> parseList(String response) {
        List<SftpFile> list = new ArrayList<>();
        if (response == null || response.trim().isEmpty()) {
            return list;
        }
        try {
            JSONArray array = new JSONArray(response);
            for (int i = 0; i < array.length(); i++) {
                list.add(SftpFile.fromJson(array.getJSONObject(i)));
            }
        } catch (Exception ignored) {
        }
        return list;
    }
}
