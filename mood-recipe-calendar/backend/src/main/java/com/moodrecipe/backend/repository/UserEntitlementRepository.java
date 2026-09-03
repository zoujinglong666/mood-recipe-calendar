package com.moodrecipe.backend.repository;

import com.moodrecipe.backend.entity.UserEntitlement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

import java.util.List;
import java.util.Optional;

public interface UserEntitlementRepository extends JpaRepository<UserEntitlement, Long> {
    Optional<UserEntitlement> findBySourceOrderNo(String sourceOrderNo);
    List<UserEntitlement> findByOpenidAndStatus(String openid, String status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from UserEntitlement e where e.openid = :openid and e.status = :status order by e.id asc")
    List<UserEntitlement> findByOpenidAndStatusForUpdate(@Param("openid") String openid, @Param("status") String status);
}
