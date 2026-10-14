package com.moodrecipe.backend.controller;

import com.moodrecipe.backend.common.ApiResponse;
import com.moodrecipe.backend.config.AppClock;
import com.moodrecipe.backend.config.SessionAuthInterceptor;
import com.moodrecipe.backend.entity.ShareRecord;
import com.moodrecipe.backend.repository.ShareRecordRepository;
import com.moodrecipe.backend.service.AchievementService;
import org.springframework.beans.factory.annotation.Autowired;
import java.time.LocalDate;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/share")
public class ShareController {

    private final ShareRecordRepository shareRecords;
    /** 分享类成就（分享家/传菜大使）绑定分享行为；可选依赖，缺省跳过。 */
    private AchievementService achievements;

    @Autowired
    public ShareController(ShareRecordRepository shareRecords) {
        this.shareRecords = shareRecords;
    }

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    void setAchievementService(AchievementService achievements) {
        this.achievements = achievements;
    }

    public static class RecordRequest {
        /** 分享场景：recipe / album / meal-agent */
        public String scene;
    }

    public record ShareResult(boolean grantedBonus, boolean isSharer) {}
    public record ShareStatus(boolean isSharer, boolean sharedToday) {}

    /**
     * 记录一次分享。幂等：同一用户同一天仍会落多条记录（用于统计），
     * 但「当日 +1 次对话」激励仅在该用户当天首次分享时 grantedBonus=true。
     */
    @PostMapping("/record")
    public ApiResponse<?> record(
            @RequestAttribute(SessionAuthInterceptor.OPENID_ATTRIBUTE) String openid,
            @RequestBody RecordRequest req) {
        if (req == null || req.scene == null || req.scene.isBlank())
            return ApiResponse.error(400, "缺少分享场景");
        String scene = req.scene.trim();
        LocalDate today = AppClock.today();
        boolean firstToday = !shareRecords.existsByOpenidAndShareDate(openid, today);

        ShareRecord record = new ShareRecord();
        record.setOpenid(openid);
        record.setScene(scene);
        shareRecords.save(record);

        // 分享本身即触发分享类成就检测：分享家/传菜大使徽章真正绑定分享行为
        if (achievements != null) {
            try { achievements.checkAndUnlock(openid); } catch (RuntimeException ignored) { }
        }

        return ApiResponse.ok(new ShareResult(firstToday, true));
    }

    /**
     * 分享回流承接：好友通过分享卡打开小程序时调用。
     * 落一条 ACCEPT 记录 → 当日对话额度 +1（UsageQuotaService.limit 识别 ACCEPT 场景）；
     * 幂等：同一天重复打开不再重复奖励。禁止自己邀请自己。
     */
    @PostMapping("/accept")
    public ApiResponse<?> accept(
            @RequestAttribute(SessionAuthInterceptor.OPENID_ATTRIBUTE) String openid,
            @RequestBody RecordRequest req) {
        String sharer = req == null ? null : (req.scene == null ? "" : req.scene.trim());
        if (sharer == null || sharer.isBlank())
            return ApiResponse.error(400, "缺少邀请人");
        if (sharer.equals(openid))
            return ApiResponse.ok(new ShareResult(false, false));
        LocalDate today = AppClock.today();
        // 当日已有任何分享/回流记录则不再重复激励（与分享激励共用每日一次上限）
        boolean granted = !shareRecords.existsByOpenidAndShareDate(openid, today);
        ShareRecord record = new ShareRecord();
        record.setOpenid(openid);
        record.setScene("ACCEPT");
        shareRecords.save(record);
        return ApiResponse.ok(new ShareResult(granted, true));
    }

    /** 分享状态：用于前端展示「分享家」徽章与当日激励是否已领取（被邀请打开不算分享过）。 */
    @GetMapping("/status")
    public ApiResponse<?> status(@RequestAttribute(SessionAuthInterceptor.OPENID_ATTRIBUTE) String openid) {
        return ApiResponse.ok(new ShareStatus(
                shareRecords.existsByOpenidAndSceneNot(openid, "ACCEPT"),
                shareRecords.existsByOpenidAndShareDate(openid, AppClock.today())));
    }
}
