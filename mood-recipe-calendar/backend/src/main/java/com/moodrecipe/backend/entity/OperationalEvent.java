package com.moodrecipe.backend.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

/** 运营漏斗与告警事件；不保存菜谱正文、Token 或支付密钥。 */
@Data @Entity @Table(name = "operational_events")
public class OperationalEvent {
  @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
  @Column(nullable = false, length = 64) private String eventType;
  @Column(nullable = false, length = 12) private String severity;
  @Column(length = 64) private String openid;
  @Column(length = 64) private String orderNo;
  @Column(length = 200) private String detail;
  @Column(name = "created_at") private LocalDateTime createdAt;
  @PrePersist void prePersist() { createdAt = LocalDateTime.now(); }
}
