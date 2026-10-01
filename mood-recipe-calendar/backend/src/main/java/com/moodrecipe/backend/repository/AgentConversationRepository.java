package com.moodrecipe.backend.repository;

import com.moodrecipe.backend.entity.AgentConversation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface AgentConversationRepository extends JpaRepository<AgentConversation, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from AgentConversation c where c.openid=:openid and c.conversationId=:conversationId")
    Optional<AgentConversation> findByOpenidAndConversationId(@Param("openid") String openid,
                                                                @Param("conversationId") String conversationId);
}
