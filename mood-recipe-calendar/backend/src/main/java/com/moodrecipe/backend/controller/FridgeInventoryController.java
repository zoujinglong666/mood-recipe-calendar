package com.moodrecipe.backend.controller;

import com.moodrecipe.backend.common.ApiResponse;
import com.moodrecipe.backend.config.SessionAuthInterceptor;
import com.moodrecipe.backend.service.FridgeInventoryService;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Optional;

@RestController
@RequestMapping("/api/fridge")
public class FridgeInventoryController {
    private final FridgeInventoryService inventory;

    public FridgeInventoryController(FridgeInventoryService inventory) {
        this.inventory = inventory;
    }

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
