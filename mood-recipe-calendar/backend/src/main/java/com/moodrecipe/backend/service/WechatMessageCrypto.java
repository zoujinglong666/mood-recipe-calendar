package com.moodrecipe.backend.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.Map;
import java.util.TreeSet;

/** 微信消息推送的 Token 验签与 EncodingAESKey 安全模式解密。 */
@Service
public class WechatMessageCrypto {
    private final ObjectMapper objectMapper;

    @Value("${wechat.message-push.token:}") private String token;
    @Value("${wechat.message-push.encoding-aes-key:}") private String encodingAesKey;
    @Value("${wechat.appid:}") private String appid;

    public WechatMessageCrypto(ObjectMapper objectMapper) { this.objectMapper = objectMapper; }

    public boolean verify(String signature, String timestamp, String nonce, String encrypted) {
        if (token == null || token.isBlank()) return false;
        try {
            TreeSet<String> values = new TreeSet<>();
            values.add(token); values.add(timestamp); values.add(nonce);
            if (encrypted != null && !encrypted.isBlank()) values.add(encrypted);
            String digest = java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-1")
                    .digest(String.join("", values).getBytes(StandardCharsets.UTF_8)));
            return MessageDigest.isEqual(digest.getBytes(StandardCharsets.UTF_8), signature.getBytes(StandardCharsets.UTF_8));
        } catch (Exception ignored) { return false; }
    }

    public Map<String, Object> decryptJson(String encrypted) {
        if (encodingAesKey == null || encodingAesKey.length() != 43 || appid == null || appid.isBlank()) {
            throw new IllegalStateException("未配置有效的微信消息安全模式参数");
        }
        try {
            byte[] key = Base64.getDecoder().decode(encodingAesKey + "=");
            byte[] data = Base64.getDecoder().decode(encrypted);
            Cipher cipher = Cipher.getInstance("AES/CBC/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"), new IvParameterSpec(key, 0, 16));
            byte[] plain = unpad(cipher.doFinal(data));
            int length = ByteBuffer.wrap(plain, 16, 4).getInt();
            if (length < 0 || 20 + length > plain.length) throw new IllegalArgumentException("消息长度无效");
            String message = new String(plain, 20, length, StandardCharsets.UTF_8);
            String receiverAppId = new String(plain, 20 + length, plain.length - 20 - length, StandardCharsets.UTF_8);
            if (!appid.equals(receiverAppId)) throw new IllegalArgumentException("消息 AppID 不匹配");
            return objectMapper.readValue(message, new TypeReference<>() { });
        } catch (Exception exception) {
            throw new IllegalArgumentException("无法解密微信安全消息", exception);
        }
    }

    private byte[] unpad(byte[] value) {
        int pad = value[value.length - 1] & 0xff;
        if (pad < 1 || pad > 32 || pad > value.length) throw new IllegalArgumentException("填充无效");
        for (int i = value.length - pad; i < value.length; i++) if ((value[i] & 0xff) != pad) throw new IllegalArgumentException("填充无效");
        return java.util.Arrays.copyOf(value, value.length - pad);
    }
}
