package com.moodrecipe.backend.repository;

import com.moodrecipe.backend.entity.User;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.Optional;
import java.time.LocalDateTime;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByOpenid(String openid);

    /** 会员时长累加等写路径：行锁避免两笔并发支付互相覆盖 member_expire。 */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT u FROM User u WHERE u.openid = :openid")
    Optional<User> findByOpenidForUpdate(@Param("openid") String openid);

    Optional<User> findBySessionTokenHashAndSessionTokenExpiresAtAfter(String tokenHash, LocalDateTime now);
}
