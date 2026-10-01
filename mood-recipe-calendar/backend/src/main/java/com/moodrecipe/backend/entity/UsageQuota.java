package com.moodrecipe.backend.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDate;

@Data @Entity
@Table(name = "usage_quotas", uniqueConstraints = @UniqueConstraint(columnNames = {"openid", "feature", "period_start"}))
public class UsageQuota {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable = false, length = 64) private String openid;
    @Column(nullable = false, length = 32) private String feature;
    @Column(name = "period_start", nullable = false) private LocalDate periodStart;
    @Column(name = "used_count", nullable = false) private int usedCount;
    @Column(name = "last_conversation_id", length = 64) private String lastConversationId;
}
