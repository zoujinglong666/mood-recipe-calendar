package com.moodrecipe.backend.service;

import com.moodrecipe.backend.entity.UsageQuota;
import com.moodrecipe.backend.config.AppClock;
import com.moodrecipe.backend.repository.UsageQuotaRepository;
import com.moodrecipe.backend.repository.UserRepository;
import com.moodrecipe.backend.repository.CheckinRepository;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;
import java.time.*;

@Service
public class UsageQuotaService {
    public enum Feature { HOME_RECOMMEND, SIMPLE_WEEKLY_PLAN, AGENT_CONVERSATION }
    public record View(boolean member, int limit, int used, int remaining, String resetsAt) {}
    private static final ZoneId ZONE = AppClock.ZONE;
    private final UsageQuotaRepository quotas;
    private final UserRepository users;
    private final CheckinRepository checkins;

    public UsageQuotaService(UsageQuotaRepository quotas, UserRepository users) {
        this(quotas, users, null);
    }

    @org.springframework.beans.factory.annotation.Autowired
    public UsageQuotaService(UsageQuotaRepository quotas, UserRepository users, CheckinRepository checkins) {
        this.quotas = quotas;
        this.users = users;
        this.checkins = checkins;
    }
    public boolean member(String openid) { return users.findByOpenid(openid).filter(u -> Integer.valueOf(1).equals(u.getIsMember()) && u.getMemberExpire() != null && u.getMemberExpire().isAfter(LocalDateTime.now(ZONE))).isPresent(); }
    @Transactional public View consume(String openid, Feature feature) {
        return consume(openid, feature, null, null);
    }
    @Transactional public View consume(String openid, Feature feature, String conversationId) {
        return consume(openid, feature, conversationId, null);
    }
    /** Idempotent consumption: a retried request with the same requestId cannot charge twice. */
    @Transactional public View consume(String openid, Feature feature, String conversationId, String requestId) {
        boolean isMember = member(openid);
        LocalDate today = LocalDate.now(ZONE);
        LocalDate start = periodStart(feature, today);
        int limit = limit(openid, feature, isMember, today);
        UsageQuota quota = quotas.findForUpdate(openid, feature.name(), start).orElseGet(() -> {
            UsageQuota q = new UsageQuota();
            q.setOpenid(openid);
            q.setFeature(feature.name());
            q.setPeriodStart(start);
            return q;
        });
        if (requestId != null && !requestId.isBlank() && requestId.equals(quota.getLastRequestId())) {
            return viewOf(isMember, limit, quota.getUsedCount(), today, feature, start);
        }
        if (feature == Feature.AGENT_CONVERSATION && isMember) return viewOf(isMember, limit, quota.getUsedCount(), today, feature, start);
        if (feature == Feature.AGENT_CONVERSATION && conversationId != null && conversationId.equals(quota.getLastConversationId())) {
            return viewOf(isMember, limit, quota.getUsedCount(), today, feature, start);
        }
        if (quota.getUsedCount() >= limit) throw new IllegalStateException(message(feature));
        quota.setUsedCount(quota.getUsedCount() + 1);
        if (feature == Feature.AGENT_CONVERSATION) quota.setLastConversationId(conversationId);
        if (requestId != null && !requestId.isBlank()) quota.setLastRequestId(requestId.trim());
        quotas.save(quota);
        return viewOf(isMember, limit, quota.getUsedCount(), today, feature, start);
    }

    public View view(String openid, Feature feature) {
        boolean isMember = member(openid);
        LocalDate today = LocalDate.now(ZONE);
        LocalDate start = periodStart(feature, today);
        int limit = limit(openid, feature, isMember, today);
        int used = quotas.findByOpenidAndFeatureAndPeriodStart(openid, feature.name(), start).map(UsageQuota::getUsedCount).orElse(0);
        return viewOf(isMember, limit, used, today, feature, start);
    }

    @Transactional public void release(String openid, Feature feature) {
        LocalDate today = LocalDate.now(ZONE);
        LocalDate start = periodStart(feature, today);
        quotas.findForUpdate(openid, feature.name(), start).ifPresent(quota -> {
            if (quota.getUsedCount() > 0) {
                quota.setUsedCount(quota.getUsedCount() - 1);
                quotas.save(quota);
            }
        });
    }

    private LocalDate periodStart(Feature feature, LocalDate today) {
        return feature == Feature.SIMPLE_WEEKLY_PLAN
            ? today.minusDays(today.getDayOfWeek().getValue() - 1L) : today;
    }

    private int limit(String openid, Feature feature, boolean isMember, LocalDate today) {
        if (feature == Feature.AGENT_CONVERSATION) {
            if (isMember) return Integer.MAX_VALUE;
            return checkins != null && checkins.findByOpenidAndCheckinDate(openid, today.toString()).isPresent() ? 1 : 0;
        }
        return isMember ? (feature == Feature.HOME_RECOMMEND ? 20 : Integer.MAX_VALUE)
            : (feature == Feature.HOME_RECOMMEND ? 3 : 1);
    }

    private String message(Feature feature) {
        if (feature == Feature.HOME_RECOMMEND) return "今日免费推荐次数已用完，开通会员可享每日20次";
        if (feature == Feature.AGENT_CONVERSATION) return "每日签到可获得1次锅仔智能体对话，今天的赠送次数已用完";
        return "本周简单周菜单已使用，开通会员可不限次使用锅仔智能体";
    }

    private View viewOf(boolean isMember, int limit, int used, LocalDate today, Feature feature, LocalDate start) {
        LocalDate next = feature == Feature.SIMPLE_WEEKLY_PLAN ? start.plusWeeks(1) : today.plusDays(1);
        return new View(isMember, limit == Integer.MAX_VALUE ? -1 : limit, used,
            limit == Integer.MAX_VALUE ? -1 : Math.max(0, limit - used),
            next.atStartOfDay(ZONE).toOffsetDateTime().toString());
    }
    public void requireMember(String openid) { if (!member(openid)) throw new IllegalStateException("锅仔智能体为会员专享功能"); }
}
