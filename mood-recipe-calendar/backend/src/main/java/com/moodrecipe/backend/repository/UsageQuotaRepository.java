package com.moodrecipe.backend.repository;

import com.moodrecipe.backend.entity.UsageQuota;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.time.LocalDate;
import java.util.Optional;

public interface UsageQuotaRepository extends JpaRepository<UsageQuota, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select q from UsageQuota q where q.openid=:openid and q.feature=:feature and q.periodStart=:periodStart")
    Optional<UsageQuota> findForUpdate(@Param("openid") String openid, @Param("feature") String feature, @Param("periodStart") LocalDate periodStart);

    Optional<UsageQuota> findByOpenidAndFeatureAndPeriodStart(String openid, String feature, LocalDate periodStart);

    /**
     * 原子建行（行已存在则原样不动）：并发首扣时用它替代「先 SELECT FOR UPDATE 再 INSERT」。
     * 为什么不能先锁再插：RR 隔离下对「不存在的行」执行 SELECT ... FOR UPDATE 会持间隙锁，
     * 两个并发请求各握对方插入意向锁所需的间隙锁 → 互相等待 → InnoDB 判死锁杀一个（500 告警根因）。
     * 单条 INSERT ... ON DUPLICATE KEY UPDATE 只有插入意向锁，同键并发时一方插入一方空转，不成环。
     * used_count 一律建为 0：计数由业务层的锁内自增完成，避免与自减路径（release/adReward）耦合。
     */
    @Modifying
    @Query(value = "insert into usage_quotas (openid, feature, period_start, used_count) "
            + "values (:openid, :feature, :periodStart, 0) "
            + "on duplicate key update openid = openid", nativeQuery = true)
    int insertIfAbsent(@Param("openid") String openid, @Param("feature") String feature, @Param("periodStart") LocalDate periodStart);
}
