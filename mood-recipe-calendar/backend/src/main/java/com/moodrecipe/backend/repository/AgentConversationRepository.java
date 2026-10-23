package com.moodrecipe.backend.repository;

import com.moodrecipe.backend.entity.AgentConversation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import jakarta.persistence.LockModeType;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface AgentConversationRepository extends JpaRepository<AgentConversation, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from AgentConversation c where c.openid=:openid and c.conversationId=:conversationId")
    Optional<AgentConversation> findByOpenidAndConversationId(@Param("openid") String openid,
                                                                @Param("conversationId") String conversationId);

    Optional<AgentConversation> findFirstByOpenidAndStatusOrderByUpdatedAtDesc(String openid, String status);

    /** 只读路径用（不带 FOR UPDATE）：纯查会话不需要行锁/间隙锁，减少热路径锁压力。 */
    Optional<AgentConversation> findOneByOpenidAndConversationId(String openid, String conversationId);

    /**
     * 原子建行（已存在则原样不动）：与 UsageQuotaRepository.insertIfAbsent 同一意图——
     * 会话首轮保存不能「先 FOR UPDATE 锁不存在的行（RR 下持间隙锁）再 INSERT」，
     * 两个并发首轮会互等对方的插入意向锁直接死锁。created_at/updated_at 由
     * @PrePersist 在 JPA 写入时补，原生插入显式给值保证 NOT NULL 约束满足。
     */
    @Modifying
    @Query(value = "insert into agent_conversations (openid, conversation_id, state_json, turn_json, transcript_json, turn_count, status, created_at, updated_at) "
            + "values (:openid, :conversationId, '{}', '{}', '[]', 0, 'ACTIVE', :now, :now) "
            + "on duplicate key update openid = openid", nativeQuery = true)
    int insertIfAbsent(@Param("openid") String openid, @Param("conversationId") String conversationId,
                       @Param("now") java.time.LocalDateTime now);
}
