package com.moodrecipe.backend.repository;

import com.moodrecipe.backend.entity.VirtualOrder;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

import java.util.Optional;
import java.util.List;
import java.time.LocalDateTime;

public interface VirtualOrderRepository extends JpaRepository<VirtualOrder, Long> {
    Optional<VirtualOrder> findByOrderNo(String orderNo);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from VirtualOrder o where o.orderNo = :orderNo")
    Optional<VirtualOrder> findByOrderNoForUpdate(@Param("orderNo") String orderNo);

    List<VirtualOrder> findByOpenidOrderByCreatedAtDesc(String openid);

    List<VirtualOrder> findTop100ByStatusAndCreatedAtBetweenOrderByCreatedAtAsc(
            String status, LocalDateTime after, LocalDateTime before);

    List<VirtualOrder> findTop100ByStatusAndWxNotifiedFalseAndNotifyAttemptsLessThanAndCreatedAtBetweenOrderByCreatedAtAsc(
            String status, int maxAttempts, LocalDateTime after, LocalDateTime before);
}
