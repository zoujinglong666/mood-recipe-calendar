package com.moodrecipe.backend.repository;

import com.moodrecipe.backend.entity.FridgeItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface FridgeItemRepository extends JpaRepository<FridgeItem, Long> {
    List<FridgeItem> findByOpenidOrderByExpiresOnAscUpdatedAtDesc(String openid);
    Optional<FridgeItem> findByIdAndOpenid(Long id, String openid);

    /** 定时临期提醒：某日期之前（含当天）到期的全部食材，按用户分组后逐一判断。 */
    List<FridgeItem> findByExpiresOnLessThanEqual(LocalDate date);
}
