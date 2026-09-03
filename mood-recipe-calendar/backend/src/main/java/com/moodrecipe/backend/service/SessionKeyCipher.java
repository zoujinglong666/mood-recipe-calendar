package com.moodrecipe.backend.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

/** 仅用于静态保存微信 session_key，避免将它作为明文写入数据库。 */
@Service
public class SessionKeyCipher {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int IV_LENGTH = 12;
    private static final int TAG_LENGTH = 128;

    @Value("${wechat.session-key-encryption-key:}")
    private String encryptionKey;

    public String encrypt(String plainText) {
        try {
            byte[] iv = new byte[IV_LENGTH];
            RANDOM.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key(), new GCMParameterSpec(TAG_LENGTH, iv));
            byte[] encrypted = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));
            byte[] payload = new byte[iv.length + encrypted.length];
            System.arraycopy(iv, 0, payload, 0, iv.length);
            System.arraycopy(encrypted, 0, payload, iv.length, encrypted.length);
            return Base64.getEncoder().encodeToString(payload);
        } catch (Exception exception) {
            throw new IllegalStateException("session_key 加密失败", exception);
        }
    }

    public boolean isConfigured() {
        try {
            return Base64.getDecoder().decode(encryptionKey).length == 32;
        } catch (Exception ignored) {
            return false;
        }
    }

    public String decrypt(String encryptedText) {
        try {
            byte[] payload = Base64.getDecoder().decode(encryptedText);
            if (payload.length <= IV_LENGTH) throw new IllegalArgumentException("密文格式无效");
            byte[] iv = java.util.Arrays.copyOfRange(payload, 0, IV_LENGTH);
            byte[] cipherText = java.util.Arrays.copyOfRange(payload, IV_LENGTH, payload.length);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key(), new GCMParameterSpec(TAG_LENGTH, iv));
            return new String(cipher.doFinal(cipherText), StandardCharsets.UTF_8);
        } catch (Exception exception) {
            throw new IllegalStateException("session_key 解密失败，请让用户重新登录", exception);
        }
    }

    private SecretKeySpec key() {
        try {
            byte[] raw = Base64.getDecoder().decode(encryptionKey);
            if (raw.length != 32) throw new IllegalArgumentException("长度必须为 32 字节");
            return new SecretKeySpec(raw, "AES");
        } catch (Exception exception) {
            throw new IllegalStateException("未配置有效的 WECHAT_SESSION_KEY_ENCRYPTION_KEY", exception);
        }
    }
}
