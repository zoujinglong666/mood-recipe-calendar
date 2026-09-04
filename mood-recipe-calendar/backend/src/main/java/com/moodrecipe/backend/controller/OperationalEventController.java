package com.moodrecipe.backend.controller;
import com.moodrecipe.backend.common.ApiResponse;
import com.moodrecipe.backend.config.SessionAuthInterceptor;
import com.moodrecipe.backend.service.OperationalEventService;
import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/operations/events")
public class OperationalEventController {
  private final OperationalEventService events;
  public OperationalEventController(OperationalEventService events) { this.events = events; }
  @PostMapping public ApiResponse<Void> record(@RequestAttribute(SessionAuthInterceptor.OPENID_ATTRIBUTE) String openid, @RequestBody EventRequest request) {
    if (!request.eventType().matches("[A-Z0-9_]{3,64}")) return ApiResponse.error(400, "事件类型无效");
    events.record(request.eventType(), "INFO", openid, request.orderNo(), null); return ApiResponse.ok();
  }
  public record EventRequest(String eventType, String orderNo) { }
}
