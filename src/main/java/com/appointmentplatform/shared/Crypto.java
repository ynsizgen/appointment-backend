package com.appointmentplatform.shared;

import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;
import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class Crypto {
    private final SecretKeySpec key;
    private final SecureRandom random = new SecureRandom();
    public Crypto(@Value("${app.security.encryption-key}") String encodedKey) {
        byte[] bytes;
        try { bytes = Base64.getDecoder().decode(encodedKey); }
        catch (IllegalArgumentException e) {
            throw new IllegalStateException("APP_ENCRYPTION_KEY must be a base64-encoded 32-byte key"); }
        if (bytes.length != 32) throw new IllegalStateException("APP_ENCRYPTION_KEY must be a base64-encoded 32-byte key");
        key = new SecretKeySpec(bytes, "AES");
    }
    public String keyedHash(String value) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key.getEncoded(), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Unable to hash verification data", exception);
        }
    }

    public String token() {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
    public static String hash(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (GeneralSecurityException e) { throw new IllegalStateException(e); }
    }
    public String encrypt(String plaintext) {
        try {
            byte[] iv = new byte[12]; random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(128, iv));
            byte[] encrypted = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            byte[] combined = new byte[iv.length + encrypted.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(encrypted, 0, combined, iv.length, encrypted.length);
            return Base64.getEncoder().encodeToString(combined);
        } catch (GeneralSecurityException e) { throw new IllegalStateException("Encryption failed", e); }
    }
    public String decrypt(String encoded) {
        try {
            byte[] bytes = Base64.getDecoder().decode(encoded);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(128, Arrays.copyOf(bytes, 12)));
            return new String(cipher.doFinal(bytes, 12, bytes.length - 12), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException e) { throw new IllegalStateException("Decryption failed"); }
    }
}
