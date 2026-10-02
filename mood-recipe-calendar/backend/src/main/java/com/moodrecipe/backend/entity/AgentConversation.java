package com.moodrecipe.backend.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

/** Server-owned state for one meal-planning conversation. */
@Data
@Entity
@Table(name = "agent_conversations", uniqueConstraints = @UniqueConstraint(columnNames = {"openid", "conversation_id"}))
public class AgentConversation {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 64)
    private String openid;

    @Column(name = "conversation_id", nullable = false, length = 96)
    private String conversationId;

    @Column(name = "state_json", nullable = false, columnDefinition = "TEXT")
    private String stateJson = "{}";

    @Column(name = "turn_json", nullable = false, columnDefinition = "TEXT")
    private String turnJson = "{}";

    @Column(name = "transcript_json", nullable = false, columnDefinition = "TEXT")
    private String transcriptJson = "[]";

    @Column(name = "last_action", length = 32)
    private String lastAction;

    @Column(name = "turn_count", nullable = false)
    private int turnCount;

    @Column(nullable = false, length = 16)
    private String status = "ACTIVE";

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    void create() {
        LocalDateTime now = LocalDateTime.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void touch() {
        updatedAt = LocalDateTime.now();
    }
}
