package com.orcterm.util;

import android.util.Base64;

import java.nio.charset.StandardCharsets;
import java.security.spec.KeySpec;
import java.util.Arrays;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * 加解密与压缩处理工具
 */
public class SecurityUtils {

    private static final String SALT_PREFIX = "Salted__";

    /**
     * 解密 OpenSSL AES-256-CBC PBKDF2 加密内容
     */
    public static String decrypt(String base64Data, String password) throws Exception {
        byte[] encryptedBytes = Base64.decode(base64Data, Base64.DEFAULT);
        
        // 校验 Salted__ 前缀
        if (encryptedBytes.length < 16) {
            throw new IllegalArgumentException("Invalid data length");
        }
        
        byte[] prefix = Arrays.copyOfRange(encryptedBytes, 0, 8);
        if (!new String(prefix, StandardCharsets.US_ASCII).equals(SALT_PREFIX)) {
            throw new IllegalArgumentException("Invalid encrypted format (Missing Salted__)");
        }

        byte[] salt = Arrays.copyOfRange(encryptedBytes, 8, 16);
        byte[] ciphertext = Arrays.copyOfRange(encryptedBytes, 16, encryptedBytes.length);

        // 推导密钥与 IV
        SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
        KeySpec spec = new PBEKeySpec(password.toCharArray(), salt, 10000, 256 + 128);
        byte[] derived = factory.generateSecret(spec).getEncoded();

        // 拆分密钥与 IV
        byte[] keyBytes = Arrays.copyOfRange(derived, 0, 32);
        byte[] ivBytes = Arrays.copyOfRange(derived, 32, 48);

        SecretKeySpec secretKey = new SecretKeySpec(keyBytes, "AES");
        IvParameterSpec iv = new IvParameterSpec(ivBytes);

        Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
        cipher.init(Cipher.DECRYPT_MODE, secretKey, iv);

        byte[] decryptedBytes = cipher.doFinal(ciphertext);
        return new String(decryptedBytes, StandardCharsets.UTF_8);
    }

    public static String decompressGzip(byte[] compressed) throws Exception {
        java.io.ByteArrayInputStream bis = new java.io.ByteArrayInputStream(compressed);
        java.util.zip.GZIPInputStream gis = new java.util.zip.GZIPInputStream(bis);
        java.io.BufferedReader br = new java.io.BufferedReader(new java.io.InputStreamReader(gis, StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder();
        String line;
        while((line = br.readLine()) != null) {
            sb.append(line);
        }
        return sb.toString();
    }
}
