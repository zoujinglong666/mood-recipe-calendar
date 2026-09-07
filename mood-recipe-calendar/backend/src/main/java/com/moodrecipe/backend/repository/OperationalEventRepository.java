package com.moodrecipe.backend.repository;
import com.moodrecipe.backend.entity.OperationalEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDateTime;
import java.util.List;

public interface OperationalEventRepository extends JpaRepository<OperationalEvent, Long> {
  List<OperationalEvent> findTop60ByOpenidAndEventTypeStartingWithOrderByCreatedAtDesc(String openid, String eventTypePrefix);
  boolean existsByOpenidAndEventTypeAndCreatedAtBetween(String openid, String eventType, LocalDateTime start, LocalDateTime end);
}
