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

public class SecurityUtils {

    private static final String SALT_PREFIX = "Salted__";

    /**
     * Decrypts OpenSSL AES-256-CBC PBKDF2 encrypted string.
     * Expected format: "Salted__" (8 bytes) + Salt (8 bytes) + Ciphertext
     */
    public static String decrypt(String base64Data, String password) throws Exception {
        byte[] encryptedBytes = Base64.decode(base64Data, Base64.DEFAULT);
        
        // Check prefix
        if (encryptedBytes.length < 16) {
            throw new IllegalArgumentException("Invalid data length");
        }
        
        byte[] prefix = Arrays.copyOfRange(encryptedBytes, 0, 8);
        if (!new String(prefix, StandardCharsets.US_ASCII).equals(SALT_PREFIX)) {
            // Note: Some openssl versions/commands might not prepend Salted__, 
            // but the command we provided (openssl enc ...) DOES.
            // If strictly following the script, this check is valid.
            throw new IllegalArgumentException("Invalid encrypted format (Missing Salted__)");
        }

        byte[] salt = Arrays.copyOfRange(encryptedBytes, 8, 16);
        byte[] ciphertext = Arrays.copyOfRange(encryptedBytes, 16, encryptedBytes.length);

        // Derive Key and IV
        // PBKDF2 with SHA256, 10000 iterations
        // We need 32 bytes for Key (AES-256) + 16 bytes for IV (AES block size)
        SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
        KeySpec spec = new PBEKeySpec(password.toCharArray(), salt, 10000, 256 + 128);
        byte[] derived = factory.generateSecret(spec).getEncoded();

        // Split Key and IV
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
