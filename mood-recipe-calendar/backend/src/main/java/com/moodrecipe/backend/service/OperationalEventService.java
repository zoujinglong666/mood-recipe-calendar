package com.moodrecipe.backend.service;
import com.moodrecipe.backend.entity.OperationalEvent;
import com.moodrecipe.backend.repository.OperationalEventRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
@Service public class OperationalEventService {
  private final OperationalEventRepository repository;
  public OperationalEventService(OperationalEventRepository repository) { this.repository = repository; }
  /** REQUIRES_NEW：在支付回调等外层事务内落事件日志时，事件写入失败只回滚事件自身，
   *  不污染外层事务——否则 save 异常被调用方 catch 吞掉后，外层提交必抛
   *  UnexpectedRollbackException（「锅仔后端 500 告警」同类根因，2026-10-26 修复）。 */
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void record(String type, String severity, String openid, String orderNo, String detail) {
    OperationalEvent event = new OperationalEvent(); event.setEventType(type); event.setSeverity(severity);
    event.setOpenid(openid); event.setOrderNo(orderNo); event.setDetail(detail == null ? null : detail.substring(0, Math.min(200, detail.length())));
    repository.save(event);
  }
}
