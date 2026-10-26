package com.moodrecipe.backend.agent;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * [2026-10-26] LLM 供应商简易熔断器（架构评审 P2-1）。
 *
 * <p>此前供应商持续 5xx 时，每个请求仍要完整走 2 次尝试 + 指数退避（最长 8s/次），
 * 高并发下把用户链路拖到超时。引入熔断后：
 * <ul>
 *   <li>连续 {@code failureThreshold} 次 SERVER_ERROR / TIMEOUT → open；</li>
 *   <li>open 期间 {@link #allowRequest()} 直接返回 false，{@code complete()} 快速失败，
 *       上层 Agent 的本地降级链路立即接管（用户仍拿得到结果）；</li>
 *   <li>冷却 {@code openMillis} 后进入半开，放行 1 个探测请求：成功 → close，失败 → 重新 open。</li>
 * </ul>
 *
 * <p>实例级：AgnesLlmClient（免费层）与 DeepSeek（付费层）各自持有独立熔断器，互不传染。
 * 429（限流）不计入熔断——那是配额问题不是故障问题，由 Retry-After 退避处理。
 */
public class LlmCircuitBreaker {

    private enum State { CLOSED, OPEN, HALF_OPEN }

    private final int failureThreshold;
    private final long openMillis;

    private final AtomicReference<State> state = new AtomicReference<>(State.CLOSED);
    private final AtomicInteger consecutiveFailures = new AtomicInteger();
    private final AtomicLong openedAt = new AtomicLong();

    LlmCircuitBreaker() {
        this(5, 45_000L);
    }

    LlmCircuitBreaker(int failureThreshold, long openMillis) {
        this.failureThreshold = Math.max(1, failureThreshold);
        this.openMillis = Math.max(1_000L, openMillis);
    }

    /**
     * 是否放行本次调用。CLOSED 放行；OPEN 且已过冷却期时用 CAS 竞争唯一的半开探测名额
     * （竞争成功者放行，其余请求仍拒绝）；HALF_OPEN 期间非探测请求一律拒绝。
     */
    boolean allowRequest() {
        State current = state.get();
        if (current == State.CLOSED) return true;
        if (current == State.OPEN) {
            if (System.currentTimeMillis() - openedAt.get() < openMillis) return false;
            return state.compareAndSet(State.OPEN, State.HALF_OPEN);
        }
        return false;
    }

    void onSuccess() {
        consecutiveFailures.set(0);
        state.set(State.CLOSED);
    }

    void onFailure() {
        if (state.get() == State.HALF_OPEN) {
            // 半开探测失败：重新 open，冷却期重新计时
            openedAt.set(System.currentTimeMillis());
            state.set(State.OPEN);
            return;
        }
        if (consecutiveFailures.incrementAndGet() >= failureThreshold) {
            openedAt.set(System.currentTimeMillis());
            state.set(State.OPEN);
        }
    }

    boolean isOpen() {
        return state.get() == State.OPEN;
    }
}
