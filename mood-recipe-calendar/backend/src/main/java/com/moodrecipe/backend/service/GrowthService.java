package com.moodrecipe.backend.service;

import com.moodrecipe.backend.config.AppClock;
import com.moodrecipe.backend.entity.GrowthEvent;
import com.moodrecipe.backend.entity.GrowthState;
import com.moodrecipe.backend.repository.GrowthEventRepository;
import com.moodrecipe.backend.repository.GrowthStateRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.IntStream;

/**
 * 锅仔养成（游戏化整合层）：把分散的「记录 / 做菜 / 消耗临期食材 / 签到 / 成就」
 * 统一成「喂锅仔 → 成长值 → 升级解锁新形象」的一条主线。
 *
 * 冰箱临期消耗挂钩养成是刻意设计：防浪费的行为得到即时正反馈，一举两得。
 * 每日自动事件有喂食上限（签到除外），防止刷分；等级与皮肤由成长值确定性推导。
 */
@Service
public class GrowthService {

    /** 等级阈值（累计成长值起点的闭区间），7 级。 */
    private static final int[] THRESHOLDS = {0, 60, 180, 380, 650, 1000, 1500};
    private static final String[] LEVEL_NAMES = {"小奶锅", "汤锅", "炒锅", "砂锅", "老铁锅", "鎏金锅", "锅仙"};
    /** 每级解锁的锅仔形象（复用 COS 现有素材，不新增资源）。 */
    private static final String[] SKINS = {
            "mood_01_happy", "action_01_bowl", "action_09_celebrate", "action_14_clap",
            "action_18_cheer", "action_19_kungfu", "action_20_panda"};

    /** 自动事件的每日喂食上限（签到不在限额内）；防刷分。 */
    private static final int DAILY_AUTO_CAP = 60;

    public enum Event {
        RECORD_MEAL(10, "认真记录了一餐"),
        CHECKIN(5, "签到打卡"),
        WEEKLY_PLAN(15, "让锅仔排了一周菜单"),
        EXPIRING_CONSUMED(15, "消灭了临期食材"),
        ACHIEVEMENT(20, "解锁了一枚成就");

        private final int points;
        private final String label;

        Event(int points, String label) {
            this.points = points;
            this.label = label;
        }

        public int points() { return points; }
        public String label() { return label; }
    }

    private final GrowthStateRepository states;
    private final GrowthEventRepository events;

    public GrowthService(GrowthStateRepository states, GrowthEventRepository events) {
        this.states = states;
        this.events = events;
    }

    /** 自注入：award() 降级捕获需要调用带 REQUIRES_NEW 事务的代理方法。 */
    @Autowired @Lazy private GrowthService self;

    /** 记一笔成长值；超出每日上限的自动事件静默丢弃（签到不受限）。返回最新状态视图。 */
    public StatusView award(String openid, Event event) {
        try {
            return self.awardTx(openid, event);
        } catch (RuntimeException ignored) {
            // 养成是增益行为，任何失败都不能拖垮主流程（记录/签到/消耗各自照常完成）。
            // awardTx 为 REQUIRES_NEW 独立事务：失败只回滚本次成长记录，
            // 不会在"事务内吞异常后正常返回"时抛 UnexpectedRollbackException（2026-10-26 修复）。
            return status(openid);
        }
    }

    /** 事务内写成长值：REQUIRES_NEW 独立事务，避免被外层调用事务（如周菜单生成）连带污染。 */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public StatusView awardTx(String openid, Event event) {
        if (event != Event.CHECKIN && todayAutoPoints(openid) + event.points() > DAILY_AUTO_CAP) {
            return status(openid);
        }
        GrowthEvent row = new GrowthEvent();
        row.setOpenid(openid);
        row.setType(event.name());
        row.setPoints(event.points());
        events.save(row);

        GrowthState state = states.findByOpenid(openid).orElseGet(() -> {
            GrowthState fresh = new GrowthState();
            fresh.setOpenid(openid);
            return fresh;
        });
        state.setPoints(state.getPoints() + event.points());
        state.setLevel(levelOf(state.getPoints()));
        states.save(state);
        return status(openid);
    }

    /** 养成状态：等级、进度、皮肤解锁、今日投喂、最近动态。 */
    public StatusView status(String openid) {
        int points = states.findByOpenid(openid).map(GrowthState::getPoints).orElse(0);
        int level = levelOf(points);
        int nextThreshold = level < THRESHOLDS.length ? THRESHOLDS[level] : THRESHOLDS[THRESHOLDS.length - 1];
        int currentFloor = THRESHOLDS[level - 1];
        int progress = level >= THRESHOLDS.length ? 100
                : Math.round((points - currentFloor) * 100f / Math.max(1, nextThreshold - currentFloor));

        List<SkinView> skins = IntStream.range(0, SKINS.length)
                .mapToObj(i -> new SkinView(i + 1, LEVEL_NAMES[i] + "形态",
                        "/static/guozai/" + SKINS[i] + ".png", points >= THRESHOLDS[i], THRESHOLDS[i]))
                .toList();

        List<EventView> recent = events.findTop10ByOpenidOrderByCreatedAtDesc(openid).stream()
                .map(row -> new EventView(row.getType(),
                        labelOf(row.getType()),
                        row.getPoints(),
                        row.getCreatedAt() == null ? null : row.getCreatedAt().toString()))
                .toList();

        return new StatusView(points, level, LEVEL_NAMES[level - 1], level < THRESHOLDS.length,
                nextThreshold, Math.max(0, nextThreshold - points), progress, skins,
                todayPoints(openid), recent);
    }

    private int todayAutoPoints(String openid) {
        return todayPoints(openid);
    }

    private int todayPoints(String openid) {
        LocalDateTime dayStart = AppClock.today().atStartOfDay();
        return events.findByOpenidAndCreatedAtAfter(openid, dayStart).stream()
                .mapToInt(GrowthEvent::getPoints)
                .sum();
    }

    private String labelOf(String type) {
        try {
            return Event.valueOf(type).label();
        } catch (IllegalArgumentException e) {
            return "喂了锅仔";
        }
    }

    private int levelOf(int points) {
        int level = 1;
        for (int i = 0; i < THRESHOLDS.length; i++) {
            if (points >= THRESHOLDS[i]) level = i + 1;
        }
        return level;
    }

    public record StatusView(int points, int level, String levelName, boolean hasNextLevel,
                             int nextLevelPoints, int pointsToNext, int progressPercent,
                             List<SkinView> skins, int todayPoints, List<EventView> recent) {}
    public record SkinView(int level, String name, String image, boolean unlocked, int unlockAtPoints) {}
    public record EventView(String type, String label, int points, String createdAt) {}
}
