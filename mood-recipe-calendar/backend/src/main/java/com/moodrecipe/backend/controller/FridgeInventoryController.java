package com.moodrecipe.backend.controller;

import com.moodrecipe.backend.common.ApiResponse;
import com.moodrecipe.backend.config.SessionAuthInterceptor;
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
    private final UsageQuotaService quota;

    public FridgeInventoryController(FridgeInventoryService inventory,
                                     FridgeVisionService vision,
                                     FridgeExpiryNotifier expiryNotifier,
                                     UsageQuotaService quota) {
        this.inventory = inventory;
        this.vision = vision;
        this.expiryNotifier = expiryNotifier;
        this.quota = quota;
    }

    /**
     * 拍照识别冰箱食材（会员专享）。
     *
     * 会员闸门（第一道，先于一切识别逻辑）：非会员直接 403，
     * 使其无法访问识别、菜名展示、保质期计算等任何下游能力。
     */
    @PostMapping("/recognize")
    public ApiResponse<?> recognize(@RequestAttribute(SessionAuthInterceptor.OPENID_ATTRIBUTE) String openid,
                                    @RequestBody(required = false) RecognizeRequest request) {
        if (!quota.member(openid)) {
            return ApiResponse.error(403, "冰箱拍照识别为会员专享，开通后即可使用");
        }
        if (request == null || request.imageUrl() == null || request.imageUrl().isBlank()) {
            return ApiResponse.error(400, "请先拍摄或选择一张照片");
        }
        return ApiResponse.ok(vision.recognize(request.imageUrl()));
    }

    public record RecognizeRequest(String imageUrl) {}

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
