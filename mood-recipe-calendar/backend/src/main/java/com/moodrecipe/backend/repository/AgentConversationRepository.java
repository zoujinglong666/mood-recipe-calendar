package com.moodrecipe.backend.repository;

import com.moodrecipe.backend.entity.AgentConversation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AgentConversationRepository extends JpaRepository<AgentConversation, Long> {
    Optional<AgentConversation> findByOpenidAndConversationId(String openid, String conversationId);
}
