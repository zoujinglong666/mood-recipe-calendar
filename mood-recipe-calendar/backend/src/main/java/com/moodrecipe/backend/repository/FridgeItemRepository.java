package com.moodrecipe.backend.repository;

import com.moodrecipe.backend.entity.FridgeItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface FridgeItemRepository extends JpaRepository<FridgeItem, Long> {
    List<FridgeItem> findByOpenidOrderByExpiresOnAscUpdatedAtDesc(String openid);
    Optional<FridgeItem> findByIdAndOpenid(Long id, String openid);
}
