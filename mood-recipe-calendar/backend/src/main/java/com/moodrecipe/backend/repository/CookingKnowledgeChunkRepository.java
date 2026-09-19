package com.moodrecipe.backend.repository;

import com.moodrecipe.backend.entity.CookingKnowledgeChunk;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CookingKnowledgeChunkRepository extends JpaRepository<CookingKnowledgeChunk, Long> {
    List<CookingKnowledgeChunk> findByEnabledTrue();
}
