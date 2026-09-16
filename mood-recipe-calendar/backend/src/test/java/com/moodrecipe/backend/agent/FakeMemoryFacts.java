package com.moodrecipe.backend.agent;

import com.moodrecipe.backend.entity.AgentMemoryFact;
import com.moodrecipe.backend.repository.AgentMemoryFactRepository;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** 内存版事实表：让"记住—想起—遗忘—学习"的闭环能在没有数据库的情况下被验证。 */
final class FakeMemoryFacts {

    private final Map<String, AgentMemoryFact> rows = new LinkedHashMap<>();
    private final AgentMemoryFactRepository repository = mock(AgentMemoryFactRepository.class);

    FakeMemoryFacts() {
        when(repository.findByOpenidAndMemoryKey(anyString(), anyString()))
                .thenAnswer(invocation -> Optional.ofNullable(
                        rows.get(key(invocation.getArgument(0), invocation.getArgument(1)))));
        when(repository.findByOpenidAndMemoryKeyAndStatus(anyString(), anyString(), anyString()))
                .thenAnswer(invocation -> {
                    AgentMemoryFact fact = rows.get(key(invocation.getArgument(0), invocation.getArgument(1)));
                    String status = invocation.getArgument(2);
                    return fact != null && status.equals(fact.getStatus()) ? Optional.of(fact) : Optional.empty();
                });
        when(repository.findByOpenidAndStatusOrderByUpdatedAtDesc(anyString(), anyString()))
                .thenAnswer(invocation -> active(invocation.getArgument(0), invocation.getArgument(1)));
        when(repository.save(any(AgentMemoryFact.class))).thenAnswer(invocation -> {
            AgentMemoryFact fact = invocation.getArgument(0);
            rows.put(key(fact.getOpenid(), fact.getMemoryKey()), fact);
            return fact;
        });
    }

    AgentMemoryFactRepository repository() {
        return repository;
    }

    AgentMemoryFact seed(AgentMemoryFact fact) {
        rows.put(key(fact.getOpenid(), fact.getMemoryKey()), fact);
        return fact;
    }

    AgentMemoryFact get(String openid, String memoryKey) {
        return rows.get(key(openid, memoryKey));
    }

    static AgentMemoryFact fact(String openid, String memoryKey, String value, double confidence, String source) {
        AgentMemoryFact fact = new AgentMemoryFact();
        fact.setOpenid(openid);
        fact.setMemoryKey(memoryKey);
        fact.setMemoryValue(value);
        fact.setSource(source);
        fact.setConfidence(confidence);
        fact.setStatus(AgentMemoryFact.STATUS_ACTIVE);
        fact.setHitCount(0);
        fact.setUpdatedAt(LocalDateTime.now());
        return fact;
    }

    private List<AgentMemoryFact> active(String openid, String status) {
        List<AgentMemoryFact> result = new ArrayList<>();
        rows.values().stream()
                .filter(row -> openid.equals(row.getOpenid()) && status.equals(row.getStatus()))
                .sorted(Comparator.comparing(AgentMemoryFact::getUpdatedAt,
                        Comparator.nullsLast(Comparator.<LocalDateTime>reverseOrder())))
                .forEach(result::add);
        return result;
    }

    private static String key(String openid, String memoryKey) {
        return openid + "|" + memoryKey;
    }
}
