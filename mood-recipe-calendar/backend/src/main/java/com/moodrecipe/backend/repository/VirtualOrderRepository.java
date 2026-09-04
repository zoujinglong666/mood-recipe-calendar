package com.moodrecipe.backend.repository;

import com.moodrecipe.backend.entity.VirtualOrder;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.List;

public interface VirtualOrderRepository extends JpaRepository<VirtualOrder, Long> {
    Optional<VirtualOrder> findByOrderNo(String orderNo);
    List<VirtualOrder> findByOpenidOrderByCreatedAtDesc(String openid);
}
