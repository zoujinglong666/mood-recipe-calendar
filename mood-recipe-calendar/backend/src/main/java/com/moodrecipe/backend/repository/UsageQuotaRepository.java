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
}
