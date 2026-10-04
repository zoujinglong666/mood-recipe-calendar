package com.moodrecipe.backend.entity;

import com.moodrecipe.backend.config.AppClock;
import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 《中国居民膳食指南》RAG 知识块。
 *
 * 合规边界（重要）：content 只存「经过改写的知识点」，不存指南原文逐字内容；
 * sourceName / sourceUrl 提供来源可追溯。embedding_json 为向量（JSON 浮点数组），
 * 检索时在内存做余弦相似度，规模（数千条）下足够快，无需专用向量库。
 */
@Data
@Entity
@Table(name = "guideline_chunks")
public class GuidelineChunk {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 来源 PDF 页码，便于人工复核时定位。 */
    @Column(nullable = false)
    private Integer page;

    /** 章节名，如「一般人群膳食指南」「中国居民平衡膳食宝塔」。 */
    @Column(length = 120)
    private String section;

    @Column(nullable = false, length = 40)
    private String category;

    @Column(nullable = false, length = 300)
    private String title;

    /** 改写后的知识点正文（非原文逐字复制）。 */
    @Column(nullable = false, columnDefinition = "LONGTEXT")
    private String content;

    /** 逗号分隔的关键词，便于调试与可解释性。 */
    @Column(length = 400)
    private String tags;

    @Column(name = "source_name", nullable = false, length = 120)
    private String sourceName;

    @Column(name = "source_url", length = 400)
    private String sourceUrl;

    /** 向量，JSON 浮点数组，如智谱 embedding-2（1024 维）。 */
    @Column(name = "embedding_json", columnDefinition = "LONGTEXT")
    private String embeddingJson;

    @Column(nullable = false)
    private Boolean enabled = true;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    /** 入库即视为一次自检基线；运营后续可人工复核后更新。 */
    @Column(name = "reviewed_at", nullable = false)
    private LocalDateTime reviewedAt;

    @PrePersist
    @PreUpdate
    void touch() {
        if (createdAt == null) createdAt = AppClock.now();
        if (reviewedAt == null) reviewedAt = AppClock.now();
    }
}
