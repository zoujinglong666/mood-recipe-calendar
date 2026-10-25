package com.moodrecipe.backend.service;

import com.moodrecipe.backend.config.AppClock;
import com.moodrecipe.backend.entity.Checkin;
import com.moodrecipe.backend.repository.CheckinRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 每日签到：写签到记录，供 UsageQuotaService 判定当日对话/三餐赠礼额度。
 * 周边商城（商品/订单）已下线，本服务只保留签到。
 */
@Service
public class CheckinService {

    private final CheckinRepository checkinRepository;
    /** 签到随机赠礼判定（可选依赖，setter 注入保持测试构造器不变） */
    @Autowired(required = false)
    private UsageQuotaService usageQuota;
    /** 锅仔养成：签到喂锅仔（可选依赖，setter 注入） */
    @Autowired(required = false)
    private GrowthService growth;
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final ZoneId ZONE = AppClock.ZONE;

    public CheckinService(CheckinRepository checkinRepository) {
        this.checkinRepository = checkinRepository;
    }

    /**
     * 今日签到。返回签到状态：
     * { checkedIn, streak, totalDays, giftType }
     */
    public Map<String, Object> checkin(String openid) {
        String today = AppClock.today().format(DATE_FMT);
        if (checkinRepository.findByOpenidAndCheckinDate(openid, today).isEmpty()) {
            checkinRepository.save(Checkin.builder()
                .openid(openid)
                .checkinDate(today)
                .build());
            // 签到喂锅仔 +5（重复签到幂等不计）
            if (growth != null) growth.award(openid, GrowthService.Event.CHECKIN);
        }
        return checkinStatus(openid);
    }

    /**
     * 查询签到状态
     */
    public Map<String, Object> checkinStatus(String openid) {
        String today = AppClock.today().format(DATE_FMT);
        List<Checkin> all = checkinRepository.findByOpenidOrderByCheckinDateAsc(openid);
        Set<String> dateSet = all.stream().map(Checkin::getCheckinDate).collect(Collectors.toSet());

        boolean checkedIn = dateSet.contains(today);
        int streak = calcStreak(dateSet);

        Map<String, Object> res = new HashMap<>();
        res.put("checkedIn", checkedIn);
        res.put("streak", streak);
        res.put("totalDays", dateSet.size());
        res.put("agentConversationGifted", checkedIn);
        // 今日随机赠礼类型（签到前也能预览将抽到哪种；未注入配额服务时退回对话券，兼容旧测试）
        res.put("giftType", usageQuota == null
                ? UsageQuotaService.DailyGift.CONVERSATION.name()
                : usageQuota.giftOf(openid, AppClock.today()).name());
        return res;
    }

    /**
     * 计算连续签到天数（从今天往前连续）
     */
    private int calcStreak(Set<String> dateSet) {
        if (dateSet.isEmpty()) return 0;
        int streak = 0;
        LocalDate day = AppClock.today();
        while (dateSet.contains(day.format(DATE_FMT))) {
            streak++;
            day = day.minusDays(1);
        }
        return streak;
    }
}
