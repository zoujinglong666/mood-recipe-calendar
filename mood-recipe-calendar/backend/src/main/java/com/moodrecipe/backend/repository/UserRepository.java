package com.moodrecipe.backend.repository;

import com.moodrecipe.backend.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.time.LocalDateTime;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByOpenid(String openid);
    Optional<User> findBySessionTokenHashAndSessionTokenExpiresAtAfter(String tokenHash, LocalDateTime now);
}
