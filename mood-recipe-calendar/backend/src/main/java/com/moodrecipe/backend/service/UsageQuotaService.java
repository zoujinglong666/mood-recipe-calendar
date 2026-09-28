package com.moodrecipe.backend.service;

import com.moodrecipe.backend.entity.UsageQuota;
import com.moodrecipe.backend.repository.UsageQuotaRepository;
import com.moodrecipe.backend.repository.UserRepository;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;
import java.time.*;

@Service
public class UsageQuotaService {
    public enum Feature { HOME_RECOMMEND, SIMPLE_WEEKLY_PLAN }
    public record View(boolean member, int limit, int used, int remaining, String resetsAt) {}
    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    private final UsageQuotaRepository quotas; private final UserRepository users;
    public UsageQuotaService(UsageQuotaRepository quotas, UserRepository users) { this.quotas = quotas; this.users = users; }
    public boolean member(String openid) { return users.findByOpenid(openid).filter(u -> Integer.valueOf(1).equals(u.getIsMember()) && u.getMemberExpire() != null && u.getMemberExpire().isAfter(LocalDateTime.now(ZONE))).isPresent(); }
    @Transactional public View consume(String openid, Feature feature) {
        boolean member = member(openid); LocalDate today = LocalDate.now(ZONE); LocalDate start = feature == Feature.SIMPLE_WEEKLY_PLAN ? today.minusDays(today.getDayOfWeek().getValue() - 1L) : today;
        int limit = member ? (feature == Feature.HOME_RECOMMEND ? 20 : Integer.MAX_VALUE) : (feature == Feature.HOME_RECOMMEND ? 3 : 1);
        UsageQuota quota = quotas.findForUpdate(openid, feature.name(), start).orElseGet(() -> { UsageQuota q = new UsageQuota(); q.setOpenid(openid); q.setFeature(feature.name()); q.setPeriodStart(start); return q; });
        if (quota.getUsedCount() >= limit) throw new IllegalStateException(feature == Feature.HOME_RECOMMEND ? "今日免费推荐次数已用完，开通会员可享每日20次" : "本周简单周菜单已使用，开通会员可不限次使用锅仔智能体");
        quota.setUsedCount(quota.getUsedCount() + 1); quotas.save(quota);
        LocalDate next = feature == Feature.SIMPLE_WEEKLY_PLAN ? start.plusWeeks(1) : today.plusDays(1);
        return new View(member, limit == Integer.MAX_VALUE ? -1 : limit, quota.getUsedCount(), limit == Integer.MAX_VALUE ? -1 : limit - quota.getUsedCount(), next.atStartOfDay(ZONE).toOffsetDateTime().toString());
    }
    public View view(String openid, Feature feature) { boolean member = member(openid); LocalDate today=LocalDate.now(ZONE); LocalDate start=feature==Feature.SIMPLE_WEEKLY_PLAN?today.minusDays(today.getDayOfWeek().getValue()-1L):today; int limit=member?(feature==Feature.HOME_RECOMMEND?20:Integer.MAX_VALUE):(feature==Feature.HOME_RECOMMEND?3:1); int used=quotas.findByOpenidAndFeatureAndPeriodStart(openid, feature.name(), start).map(UsageQuota::getUsedCount).orElse(0); LocalDate next=feature==Feature.SIMPLE_WEEKLY_PLAN?start.plusWeeks(1):today.plusDays(1); return new View(member, limit==Integer.MAX_VALUE?-1:limit, used, limit==Integer.MAX_VALUE?-1:Math.max(0,limit-used), next.atStartOfDay(ZONE).toOffsetDateTime().toString()); }
    @Transactional public void release(String openid, Feature feature) { LocalDate today=LocalDate.now(ZONE); LocalDate start=feature==Feature.SIMPLE_WEEKLY_PLAN?today.minusDays(today.getDayOfWeek().getValue()-1L):today; quotas.findForUpdate(openid, feature.name(), start).ifPresent(quota -> { if (quota.getUsedCount() > 0) { quota.setUsedCount(quota.getUsedCount()-1); quotas.save(quota); } }); }
    public void requireMember(String openid) { if (!member(openid)) throw new IllegalStateException("锅仔智能体为会员专享功能"); }
}
