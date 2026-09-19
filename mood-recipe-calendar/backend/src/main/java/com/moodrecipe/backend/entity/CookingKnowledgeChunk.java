package com.moodrecipe.backend.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "cooking_knowledge_chunks")
public class CookingKnowledgeChunk {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, length = 160) private String title;
    @Column(nullable = false, columnDefinition = "TEXT") private String content;
    @Column(nullable = false, length = 32) private String category;
    @Column(nullable = false, length = 500) private String keywords;
    @Column(name = "source_name", nullable = false, length = 160) private String sourceName;
    @Column(name = "source_url", nullable = false, length = 500) private String sourceUrl;
    @Column(name = "source_version", nullable = false, length = 64) private String sourceVersion = "1";
    @Column(name = "reviewed_at", nullable = false) private LocalDateTime reviewedAt;
    @Column(nullable = false) private Boolean enabled = true;
    @Column(name = "created_at", nullable = false) private LocalDateTime createdAt;
    @Column(name = "updated_at", nullable = false) private LocalDateTime updatedAt;

    @PrePersist @PreUpdate
    void touch() {
        if (createdAt == null) createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }
}
