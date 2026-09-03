package com.moodrecipe.backend.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "monthly_albums")
public class MonthlyAlbum {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String openid;
    private String month;

    @Column(name = "record_ids", columnDefinition = "TEXT")
    private String recordIds;

    @Column(name = "cover_text")
    private String coverText;

    @Column(columnDefinition = "TEXT")
    private String stats;

    @Column(name = "ai_summary", columnDefinition = "TEXT")
    private String aiSummary;

    @Column(name = "generated_at")
    private LocalDateTime generatedAt;

    @PrePersist
    void prePersist() {
        this.generatedAt = LocalDateTime.now();
    }
}
