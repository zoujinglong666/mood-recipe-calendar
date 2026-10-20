package com.moodrecipe.backend.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

/**
 * [2026-10-20] 进程内缓存（Caffeine）配置。
 *
 * 为什么加：此前全应用没有任何 @Cacheable / Redis，每次推荐都要反复查用户画像 / 互动 / 收藏 / 打卡
 * （DailyMenuService.pick 一次请求 5~6 个独立查询，GuozaiAgent 排序循环甚至有 N+1 曝光查询）。
 * 引入轻量进程内缓存，把这类「读多写少、短时陈旧无碍」的数据收敛掉，避免每个请求都打穿到 MySQL。
 *
 * 为什么是 Caffeine 而非 Redis：当前部署是单实例，Caffeine 零外部依赖、启动即用、命中极快；
 * 若日后扩成多副本，只需把本类的 cacheManager 换成 RedisCacheManager（缓存名保持一致）即可，
 * 业务代码（各 @Cacheable 的 cacheNames）无需改动——这也是为什么统一用语义化 cacheNames 而非默认名。
 */
@Configuration
@EnableCaching
public class CacheConfig {

    @Bean
    public CacheManager cacheManager() {
        // [2026-10-20] 全局默认策略：写后 60s 过期、最多 2000 条。
        // 用户画像 / 互动这类数据写极少，60s 陈旧对推荐结果无实质影响；
        // 个别需要更强一致性的缓存可后续针对 cacheName 单独定制 expireAfterWrite。
        CaffeineCacheManager manager = new CaffeineCacheManager();
        manager.setCaffeine(Caffeine.newBuilder()
                .expireAfterWrite(60, TimeUnit.SECONDS)
                .maximumSize(2000));
        return manager;
    }
}
