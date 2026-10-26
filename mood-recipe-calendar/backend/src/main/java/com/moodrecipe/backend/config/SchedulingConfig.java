package com.moodrecipe.backend.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

/**
 * [2026-10-26] 定时任务调度线程池（架构评审 P0-1）。
 *
 * <p>此前项目只有 {@code @EnableScheduling}，Spring 默认用单线程（pool size = 1）调度器，
 * 13+ 个 {@code @Scheduled} 任务全部串行：凌晨 03:37 的 LLM 菜谱采集一旦耗时过长，
 * 04:17 之后的清理/分类/时令任务会连锁顺延（fixedDelay 任务还会互相挤占）。
 *
 * <p>这里显式提供多线程 {@link ThreadPoolTaskScheduler}，让长任务与短清理任务可以并行，
 * 单个任务卡住不再阻塞整条凌晨任务链。长任务与短任务是否分池，等有真实争抢证据再拆，
 * 先避免过度设计。
 */
@Configuration
public class SchedulingConfig {

    /** 调度线程数：覆盖 13+ 个定时任务（多数为分钟级低频任务），默认 6，可用环境变量调整。 */
    @Value("${app.scheduling.pool-size:6}")
    private int poolSize;

    @Bean
    public ThreadPoolTaskScheduler taskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(poolSize);
        scheduler.setThreadNamePrefix("sched-");
        // 优雅关闭：等正在跑的任务收尾（最多 30 秒），避免停机时采集/对账任务被硬切。
        scheduler.setWaitForTasksToCompleteOnShutdown(true);
        scheduler.setAwaitTerminationSeconds(30);
        return scheduler;
    }
}
