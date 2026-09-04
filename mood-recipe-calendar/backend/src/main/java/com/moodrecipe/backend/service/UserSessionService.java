package com.moodrecipe.backend.service;

import com.moodrecipe.backend.entity.User;
import com.moodrecipe.backend.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Optional;

/** 不把 openid 当作身份凭据：客户端仅持有随机会话 Token，数据库只保存其摘要。 */
@Service
public class UserSessionService {
    private static final SecureRandom RANDOM = new SecureRandom();
    private final UserRepository userRepository;

    @Value("${security.session.days:30}")
    private long sessionDays;

    public UserSessionService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public String issue(User user) {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        user.setSessionTokenHash(hash(token));
        user.setSessionTokenExpiresAt(LocalDateTime.now().plusDays(sessionDays));
        userRepository.save(user);
        return token;
    }

    public Optional<User> authenticate(String token) {
        if (token == null || token.isBlank()) return Optional.empty();
        return userRepository.findBySessionTokenHashAndSessionTokenExpiresAtAfter(hash(token), LocalDateTime.now());
    }

    public void revoke(User user) {
        user.setSessionTokenHash(null);
        user.setSessionTokenExpiresAt(null);
        userRepository.save(user);
    }

    private String hash(String value) {
        try {
            return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("会话摘要生成失败", exception);
        }
    }
}
