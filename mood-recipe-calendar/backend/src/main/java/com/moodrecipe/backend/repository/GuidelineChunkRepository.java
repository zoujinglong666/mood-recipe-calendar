package com.moodrecipe.backend.repository;

import com.moodrecipe.backend.entity.GuidelineChunk;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface GuidelineChunkRepository extends JpaRepository<GuidelineChunk, Long> {
    List<GuidelineChunk> findByEnabledTrue();
}
