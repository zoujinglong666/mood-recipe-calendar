package com.moodrecipe.backend.agent;

import java.time.Duration;

/** 模型调用超时分级：短回复快速失败，规划类长任务给足时间。 */
public enum TimeoutTier {
    FAST(Duration.ofMillis(15_000)),
    STANDARD(Duration.ofMillis(40_000)),
    LONG(Duration.ofMillis(90_000));

    private final Duration timeout;

    TimeoutTier(Duration timeout) {
        this.timeout = timeout;
    }

    public Duration timeout() {
        return timeout;
    }
}
