package com.moodrecipe.backend.controller;

import com.moodrecipe.backend.common.ApiResponse;
import com.moodrecipe.backend.config.AppClock;
import com.moodrecipe.backend.config.SessionAuthInterceptor;
import com.moodrecipe.backend.service.AdRewardService;
import com.moodrecipe.backend.service.FoodShelfLifeCatalog;
import com.moodrecipe.backend.service.FridgeExpiryNotifier;
import com.moodrecipe.backend.service.FridgeInventoryService;
import com.moodrecipe.backend.service.FridgeVisionService;
import com.moodrecipe.backend.service.UsageQuotaService;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Optional;

@RestController
@RequestMapping("/api/fridge")
public class FridgeInventoryController {
    private final FridgeInventoryService inventory;
    private final FridgeVisionService vision;
    private final FridgeExpiryNotifier expiryNotifier;
    private final FoodShelfLifeCatalog shelfLifeCatalog;
    private final UsageQuotaService quota;
    private final AdRewardService adRewards;

    public FridgeInventoryController(FridgeInventoryService inventory,
                                     FridgeVisionService vision,
                                     FridgeExpiryNotifier expiryNotifier,
                                     FoodShelfLifeCatalog shelfLifeCatalog,
                                     UsageQuotaService quota,
                                     AdRewardService adRewards) {
        this.inventory = inventory;
        this.vision = vision;
        this.expiryNotifier = expiryNotifier;
        this.shelfLifeCatalog = shelfLifeCatalog;
        this.quota = quota;
        this.adRewards = adRewards;
    }

    /**
     * 拍照识别冰箱食材（会员专享；非会员可看广告单次试用）。
     *
     * 闸门顺序（先于一切识别逻辑，避免无谓烧视觉 API）：
     * 1. 会员：直接放行（无限次）；
     * 2. 非会员：当日须有未消耗的 FRIDGE_OCR 广告凭证（前端看完广告才上报），否则 403；
     * 3. 识别成功后才消耗凭证——「看一次广告 = 用一次识别」，
     *    识别失败（网络/视觉异常）不消耗，用户可重试，不白看一次广告。
     */
    @PostMapping("/recognize")
    public ApiResponse<?> recognize(@RequestAttribute(SessionAuthInterceptor.OPENID_ATTRIBUTE) String openid,
                                    @RequestBody(required = false) RecognizeRequest request) {
        if (request == null || request.imageUrl() == null || request.imageUrl().isBlank()) {
            return ApiResponse.error(400, "请先拍摄或选择一张照片");
        }
        boolean member = quota.member(openid);
        if (!member && adRewards.countWatched(openid, AdRewardService.FRIDGE_OCR, AppClock.today()) <= 0) {
            return ApiResponse.error(403, "今日试用机会已用完：看一段广告可再试用 1 次，或开通会员无限次");
        }
        Object result = vision.recognize(request.imageUrl());
        if (!member) {
            adRewards.consumeOne(openid, AdRewardService.FRIDGE_OCR, AppClock.today());
        }
        return ApiResponse.ok(result);
    }

    public record RecognizeRequest(String imageUrl) {}

    /**
     * 查询某食材的存放提醒（是否不宜冷藏 + 正确存法）。
     *
     * 用于手动添加时的实时提示：用户输入「香蕉」即提示别放冰箱。
     * 这是基于规则库的确定性查询，非会员也可用（属于基础录入辅助，不涉及识别/保质期特权）。
     */
    @GetMapping("/storage-advice")
    public ApiResponse<?> storageAdvice(@RequestAttribute(SessionAuthInterceptor.OPENID_ATTRIBUTE) String openid,
                                        @RequestParam("name") String name) {
        FoodShelfLifeCatalog.StorageAdvice advice = shelfLifeCatalog.storageAdvice(name);
        return ApiResponse.ok(new StorageAdviceView(
                advice != null,
                advice == null ? null : advice.level(),
                advice == null ? null : advice.tip()));
    }

    public record StorageAdviceView(boolean inFridgeWarned, String level, String tip) {}

    /**
     * 发送临期提醒（会员专享）。仅对当前临期/过期食材生成一条订阅消息，
     * 由前端在用户授权订阅后调用。
     */
    @PostMapping("/notify-expiring")
    public ApiResponse<?> notifyExpiring(@RequestAttribute(SessionAuthInterceptor.OPENID_ATTRIBUTE) String openid) {
        if (!quota.member(openid)) {
            return ApiResponse.error(403, "临期提醒为会员专享，开通后即可使用");
        }
        return ApiResponse.ok(expiryNotifier.notifyFor(openid));
    }

    /** 临期提醒结果：发送了多少条、涉及哪些食材。 */
    public record ExpiryNoticeResult(boolean sent, int count, String names) {}

    @GetMapping("/items")
    public ApiResponse<?> list(@RequestAttribute(SessionAuthInterceptor.OPENID_ATTRIBUTE) String openid) {
        return ApiResponse.ok(inventory.list(openid));
    }

    @GetMapping("/summary")
    public ApiResponse<?> summary(@RequestAttribute(SessionAuthInterceptor.OPENID_ATTRIBUTE) String openid) {
        return ApiResponse.ok(inventory.summary(openid));
    }

    @PostMapping("/items")
    public ApiResponse<?> create(@RequestAttribute(SessionAuthInterceptor.OPENID_ATTRIBUTE) String openid,
                                 @RequestBody CreateItemRequest request) {
        try {
            if (request == null) return ApiResponse.error(400, "请填写食材信息");
            return ApiResponse.ok(inventory.create(openid, request.toServiceRequest()));
        } catch (IllegalArgumentException e) {
            return ApiResponse.error(400, e.getMessage());
        }
    }

    @PutMapping("/items/{id}")
    public ApiResponse<?> update(@RequestAttribute(SessionAuthInterceptor.OPENID_ATTRIBUTE) String openid,
                                 @PathVariable Long id, @RequestBody CreateItemRequest request) {
        try {
            if (request == null) return ApiResponse.error(400, "请填写食材信息");
            Optional<FridgeInventoryService.ItemView> result = inventory.update(openid, id, request.toUpdateRequest());
            return result.<ApiResponse<?>>map(ApiResponse::ok).orElseGet(() -> ApiResponse.error(404, "食材不存在"));
        } catch (IllegalArgumentException e) {
            return ApiResponse.error(400, e.getMessage());
        }
    }

    @PostMapping("/items/{id}/consume")
    public ApiResponse<?> consume(@RequestAttribute(SessionAuthInterceptor.OPENID_ATTRIBUTE) String openid,
                                  @PathVariable Long id, @RequestBody ConsumeRequest request) {
        try {
            Optional<FridgeInventoryService.ItemView> result = inventory.consume(openid, id, request == null ? null : request.amount());
            return result.<ApiResponse<?>>map(ApiResponse::ok).orElseGet(() -> ApiResponse.ok(null));
        } catch (IllegalArgumentException e) {
            return ApiResponse.error(400, e.getMessage());
        }
    }

    @DeleteMapping("/items/{id}")
    public ApiResponse<?> delete(@RequestAttribute(SessionAuthInterceptor.OPENID_ATTRIBUTE) String openid,
                                 @PathVariable Long id) {
        return inventory.delete(openid, id) ? ApiResponse.ok() : ApiResponse.error(404, "食材不存在");
    }

    public record CreateItemRequest(String name, BigDecimal quantity, String unit, String purchasedOn, String expiresOn, String note) {
        FridgeInventoryService.CreateRequest toServiceRequest() {
            return new FridgeInventoryService.CreateRequest(name, quantity, unit, date(purchasedOn), date(expiresOn), note);
        }

        FridgeInventoryService.UpdateRequest toUpdateRequest() {
            return new FridgeInventoryService.UpdateRequest(name, quantity, unit, date(purchasedOn), date(expiresOn), note);
        }

        private LocalDate date(String value) {
            if (value == null || value.isBlank()) return null;
            try { return LocalDate.parse(value); }
            catch (DateTimeParseException e) { throw new IllegalArgumentException("日期格式应为 YYYY-MM-DD"); }
        }
    }

    public record ConsumeRequest(BigDecimal amount) {}
}
