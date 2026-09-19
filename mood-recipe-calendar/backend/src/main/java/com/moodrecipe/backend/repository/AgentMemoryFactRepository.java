package com.moodrecipe.backend.repository;

import com.moodrecipe.backend.entity.AgentMemoryFact;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AgentMemoryFactRepository extends JpaRepository<AgentMemoryFact, Long> {

    List<AgentMemoryFact> findByOpenid(String openid);

    Optional<AgentMemoryFact> findByOpenidAndMemoryKey(String openid, String memoryKey);

    List<AgentMemoryFact> findByOpenidAndStatusOrderByUpdatedAtDesc(String openid, String status);

    Optional<AgentMemoryFact> findByOpenidAndMemoryKeyAndStatus(String openid, String memoryKey, String status);

    void deleteByOpenidAndMemoryKey(String openid, String memoryKey);

    void deleteByOpenid(String openid);
}
