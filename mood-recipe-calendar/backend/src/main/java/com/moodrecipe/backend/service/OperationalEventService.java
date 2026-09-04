package com.moodrecipe.backend.service;
import com.moodrecipe.backend.entity.OperationalEvent;
import com.moodrecipe.backend.repository.OperationalEventRepository;
import org.springframework.stereotype.Service;
@Service public class OperationalEventService {
  private final OperationalEventRepository repository;
  public OperationalEventService(OperationalEventRepository repository) { this.repository = repository; }
  public void record(String type, String severity, String openid, String orderNo, String detail) {
    OperationalEvent event = new OperationalEvent(); event.setEventType(type); event.setSeverity(severity);
    event.setOpenid(openid); event.setOrderNo(orderNo); event.setDetail(detail == null ? null : detail.substring(0, Math.min(200, detail.length())));
    repository.save(event);
  }
}
