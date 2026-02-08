package com.orcterm.util;

import android.content.Context;
import com.orcterm.data.HostEntity;
import org.json.JSONObject;
import java.io.File;
import java.io.FileOutputStream;
import java.util.UUID;

/**
 * 导入工具方法
 */
public class ImportUtils {

    public static HostEntity parseAndSave(Context context, String jsonString) throws Exception {
        JSONObject json = new JSONObject(jsonString);
        
        String host = json.optString("h");
        int port = json.optInt("p", 22);
        String user = json.optString("u");
        String keyContent = json.optString("k");

        if (host.isEmpty() || user.isEmpty() || keyContent.isEmpty()) {
            throw new IllegalArgumentException("Missing required fields (h, u, k)");
        }

        // Save Key to file
        String filename = "key_" + UUID.randomUUID().toString() + ".pem";
        File keyDir = new File(context.getFilesDir(), "keys");
        if (!keyDir.exists()) {
            keyDir.mkdirs();
        }
        
        File keyFile = new File(keyDir, filename);
        try (FileOutputStream fos = new FileOutputStream(keyFile)) {
            fos.write(keyContent.getBytes());
        }

        // Create HostEntity
        HostEntity entity = new HostEntity(user + "@" + host, host, user);
        entity.port = port;
        entity.keyPath = keyFile.getAbsolutePath();
        entity.authType = 1; // Key Auth
        
        return entity;
    }
}
