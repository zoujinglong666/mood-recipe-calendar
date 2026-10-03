package com.moodrecipe.backend.controller;

import com.moodrecipe.backend.common.ApiResponse;
import com.moodrecipe.backend.config.AppClock;
import com.moodrecipe.backend.config.SessionAuthInterceptor;
import com.moodrecipe.backend.entity.ShareRecord;
import com.moodrecipe.backend.repository.ShareRecordRepository;
import org.springframework.beans.factory.annotation.Autowired;
import java.time.LocalDate;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/share")
public class ShareController {

    private final ShareRecordRepository shareRecords;

    @Autowired
    public ShareController(ShareRecordRepository shareRecords) {
        this.shareRecords = shareRecords;
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

        return ApiResponse.ok(new ShareResult(firstToday, true));
    }

    /** 分享状态：用于前端展示「分享家」徽章与当日激励是否已领取。 */
    @GetMapping("/status")
    public ApiResponse<?> status(@RequestAttribute(SessionAuthInterceptor.OPENID_ATTRIBUTE) String openid) {
        return ApiResponse.ok(new ShareStatus(
                shareRecords.existsByOpenid(openid),
                shareRecords.existsByOpenidAndShareDate(openid, AppClock.today())));
    }
}
