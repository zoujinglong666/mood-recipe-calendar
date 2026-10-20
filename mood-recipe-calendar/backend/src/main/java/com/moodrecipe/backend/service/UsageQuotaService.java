package com.moodrecipe.backend.service;

import com.moodrecipe.backend.entity.UsageQuota;
import com.moodrecipe.backend.config.AppClock;
import com.moodrecipe.backend.repository.UsageQuotaRepository;
import com.moodrecipe.backend.repository.UserRepository;
import com.moodrecipe.backend.repository.CheckinRepository;
import com.moodrecipe.backend.repository.ShareRecordRepository;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.time.*;

@Service
public class UsageQuotaService {
    public enum Feature { HOME_RECOMMEND, SIMPLE_WEEKLY_PLAN, AGENT_CONVERSATION, DAILY_MEAL_PLAN, LLM_LIGHT, COVER_GENERATE, DAILY_MEAL_PLAN_RESHUFFLE }
    /** 签到随机赠礼：当天确定性二选一（对用户即随机），跨日自然过期重抽 */
    public enum DailyGift { CONVERSATION, MEAL_PLAN }
    public record View(boolean member, int limit, int used, int remaining, String resetsAt) {}
    private static final ZoneId ZONE = AppClock.ZONE;
    private final UsageQuotaRepository quotas;
    private final UserRepository users;
    private final CheckinRepository checkins;
    private final ShareRecordRepository shareRecords;
    /** 家庭共享穿透：字段注入，避免破坏既有测试用的多个构造器；为空时退回本人判定。 */
    @Autowired(required = false)
    private MemberAccessService memberAccess;

    /** 看广告解锁（如当日无限换一整天）：可选注入，为空时按普通/会员规则计。 */
    @Autowired(required = false)
    private AdRewardService adReward;

    public UsageQuotaService(UsageQuotaRepository quotas, UserRepository users) {
        this(quotas, users, null, null);
    }

    public UsageQuotaService(UsageQuotaRepository quotas, UserRepository users, CheckinRepository checkins) {
        this(quotas, users, checkins, null);
    }

    @Autowired
    public UsageQuotaService(UsageQuotaRepository quotas, UserRepository users, CheckinRepository checkins, ShareRecordRepository shareRecords) {
        this.quotas = quotas;
        this.users = users;
        this.checkins = checkins;
        this.shareRecords = shareRecords;
    }
    /** 会员判定：本人会员，或所在家庭的创建者是会员（家庭共享）。 */
    public boolean member(String openid) {
        if (memberAccess != null) return memberAccess.isActive(openid);
        return users.findByOpenid(openid).filter(u -> Integer.valueOf(1).equals(u.getIsMember()) && u.getMemberExpire() != null && u.getMemberExpire().isAfter(AppClock.now())).isPresent();
    }
    @Transactional public View consume(String openid, Feature feature) {
        return consume(openid, feature, null, null);
    }
    @Transactional public View consume(String openid, Feature feature, String conversationId) {
        return consume(openid, feature, conversationId, null);
    }
    /** Idempotent consumption: a retried request with the same requestId cannot charge twice. */
    @Transactional public View consume(String openid, Feature feature, String conversationId, String requestId) {
        boolean isMember = member(openid);
        LocalDate today = AppClock.today();
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
        LocalDate today = AppClock.today();
        LocalDate start = periodStart(feature, today);
        int limit = limit(openid, feature, isMember, today);
        int used = quotas.findByOpenidAndFeatureAndPeriodStart(openid, feature.name(), start).map(UsageQuota::getUsedCount).orElse(0);
        return viewOf(isMember, limit, used, today, feature, start);
    }

    @Transactional public void release(String openid, Feature feature) {
        LocalDate today = AppClock.today();
        LocalDate start = periodStart(feature, today);
        quotas.findForUpdate(openid, feature.name(), start).ifPresent(quota -> {
            if (quota.getUsedCount() > 0) {
                quota.setUsedCount(quota.getUsedCount() - 1);
                quotas.save(quota);
            }
        });
    }

    /** 看广告换额度：AD_REWARD 伪行按天计数做每日上限；目标功能必须已耗尽才发。
     *  发放语义同 release（已用量 -1 = 净增 1 次可用）；limit=0（如未抽中对话券）时
     *  允许 usedCount 到 -1，即正好多得 1 次。requestId 保证网络重放不重复发放。 */
    public static final int DAILY_AD_REWARD_LIMIT = 3;
    private static final String AD_REWARD_FEATURE = "AD_REWARD";

    @Transactional public View adReward(String openid, Feature feature, String requestId) {
        if (member(openid))
            throw new IllegalStateException("会员权益已全覆盖，不需要看广告");
        LocalDate today = AppClock.today();
        // 广告计数行按天、先行加锁：同一用户的并发发放被串行化，杜绝并发双发
        UsageQuota adRow = quotas.findForUpdate(openid, AD_REWARD_FEATURE, today)
                .orElseGet(() -> newQuotaRow(openid, AD_REWARD_FEATURE, today));
        if (requestId != null && !requestId.isBlank() && requestId.equals(adRow.getLastRequestId()))
            return view(openid, feature);
        if (adRow.getUsedCount() >= DAILY_AD_REWARD_LIMIT)
            throw new IllegalStateException("今天看广告换额度的次数用完了，明天再来");
        LocalDate start = periodStart(feature, today);
        int limit = limit(openid, feature, false, today);
        UsageQuota target = quotas.findForUpdate(openid, feature.name(), start).orElse(null);
        int used = target == null ? 0 : target.getUsedCount();
        if (used < limit)
            throw new IllegalStateException("额度还没用完，先把今天的次数用掉再来看广告");
        if (target == null) {
            target = newQuotaRow(openid, feature.name(), start);
            target.setUsedCount(limit - 1);
        }
        else {
            target.setUsedCount(Math.max(-1, used - 1));
        }
        quotas.save(target);
        adRow.setUsedCount(adRow.getUsedCount() + 1);
        if (requestId != null && !requestId.isBlank()) adRow.setLastRequestId(requestId.trim());
        quotas.save(adRow);
        return view(openid, feature);
    }

    private UsageQuota newQuotaRow(String openid, String feature, LocalDate start) {
        UsageQuota q = new UsageQuota();
        q.setOpenid(openid);
        q.setFeature(feature);
        q.setPeriodStart(start);
        return q;
    }

    private LocalDate periodStart(Feature feature, LocalDate today) {
        return feature == Feature.SIMPLE_WEEKLY_PLAN
            ? today.minusDays(today.getDayOfWeek().getValue() - 1L) : today;
    }

    /** 签到随机赠礼类型：openid+日期 确定性哈希二选一（用户感知随机，系统幂等，跨日重抽） */
    public DailyGift giftOf(String openid, LocalDate today) {
        long seed = Math.abs((openid + "|" + today).hashCode());
        return seed % 2 == 0 ? DailyGift.CONVERSATION : DailyGift.MEAL_PLAN;
    }

    private boolean checkedInToday(String openid, LocalDate today) {
        return checkins != null && checkins.findByOpenidAndCheckinDate(openid, today.toString()).isPresent();
    }

    private int limit(String openid, Feature feature, boolean isMember, LocalDate today) {
        if (feature == Feature.AGENT_CONVERSATION) {
            if (isMember) return Integer.MAX_VALUE;
            // 签到随机赠礼二选一：抽中对话券才有 3 次；分享/回流激励不受赠礼类型影响
            int base = checkedInToday(openid, today) && giftOf(openid, today) == DailyGift.CONVERSATION ? 3 : 0;
            if (base > 0 && shareRecords != null && shareRecords.existsByOpenidAndShareDate(openid, today)) base += 1;
            if (shareRecords != null && shareRecords.existsByOpenidAndShareDateAndScene(openid, today, "ACCEPT")) base += 1;
            return base;
        }
        if (feature == Feature.DAILY_MEAL_PLAN) {
            if (isMember) return Integer.MAX_VALUE;
            // 未签到也有 1 次基础体验；签到且抽中三餐券 → 2 次。当日有效，跨日按新周期自然过期
            return checkedInToday(openid, today) && giftOf(openid, today) == DailyGift.MEAL_PLAN ? 2 : 1;
        }
        if (feature == Feature.DAILY_MEAL_PLAN_RESHUFFLE) {
            // 基础额度：普通 1 次 / 会员 3 次；每看一次激励视频再 +1 次（看一次规划一次）
            int base = isMember ? 3 : 1;
            int adBonus = adReward != null
                    ? adReward.countWatched(openid, AdRewardService.RESHUFFLE_UNLIMITED, today) : 0;
            return base + adBonus;
        }
        // 轻量 LLM 入口（陪伴寄语/营养分析/人格判型/做菜助手/月度画册寄语）共享一个每日桶，
        // 合并计算防「多入口叠加绕过」；宽限额不影响正常使用，只封刷量
        if (feature == Feature.LLM_LIGHT) return isMember ? 100 : 50;
        // 生图懒补：每用户每日上限（hasRealImage 幂等防重复，这里防批量触发缺图菜谱代付生图）
        if (feature == Feature.COVER_GENERATE) return isMember ? 50 : 20;
        return isMember ? (feature == Feature.HOME_RECOMMEND ? 20 : Integer.MAX_VALUE)
            : (feature == Feature.HOME_RECOMMEND ? 3 : 1);
    }

    private String message(Feature feature) {
        if (feature == Feature.HOME_RECOMMEND) return "今日免费推荐次数已用完，开通会员可享每日20次";
        if (feature == Feature.AGENT_CONVERSATION) return "今日对话券已用完或未抽中，明天签到随机领对话券或三餐券";
        if (feature == Feature.DAILY_MEAL_PLAN) return "今日三餐券已用完，明天签到随机再领三餐券或对话券";
        if (feature == Feature.DAILY_MEAL_PLAN_RESHUFFLE) return "今日换一整天次数已用完，看广告可再换一次";
        if (feature == Feature.LLM_LIGHT) return "今天锅仔的灵感额度用完啦，明天再来找锅仔聊";
        if (feature == Feature.COVER_GENERATE) return "今天的补图次数已用完，明天再试";
        return "本周简单周菜单已使用，开通会员可不限次使用锅仔智能体";
    }

    private View viewOf(boolean isMember, int limit, int used, LocalDate today, Feature feature, LocalDate start) {
        LocalDate next = feature == Feature.SIMPLE_WEEKLY_PLAN ? start.plusWeeks(1) : today.plusDays(1);
        return new View(isMember, limit == Integer.MAX_VALUE ? -1 : limit, used,
            limit == Integer.MAX_VALUE ? -1 : Math.max(0, limit - used),
            next.atStartOfDay(ZONE).toOffsetDateTime().toString());
    }
    public void requireMember(String openid) { if (!member(openid)) throw new IllegalStateException("锅仔智能体为会员专享功能"); }

    /** 当日是否已分享（享当日 +1 次对话激励） */
    public boolean hasSharedToday(String openid) {
        return shareRecords != null && shareRecords.existsByOpenidAndShareDate(openid, AppClock.today());
    }

    /** 是否分享过（决定「分享家」徽章） */
    public boolean isSharer(String openid) {
        return shareRecords != null && shareRecords.existsByOpenid(openid);
    }
}
