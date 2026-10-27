package com.moodrecipe.backend.config.logging;

import com.moodrecipe.backend.service.WxPusherNotifier;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * [2026-10-27] 把 {@link WxPusherErrorAppender} 程序化挂到 root logger 的桥接器。
 *
 * <p>为什么不用 logback-spring.xml：appender 需要拿到 Spring 管理的 {@link WxPusherNotifier}，
 * XML 里做依赖注入要绕 springProperty 扩展，程序化挂载更直接也更不易配错。
 * Spring 装配早于业务流量，@PostConstruct 时挂上即可覆盖启动期的报错。
 */
@Component
public class ErrorLogAlertBridge {

    private static final Logger log = LoggerFactory.getLogger(ErrorLogAlertBridge.class);
    private static final java.time.format.DateTimeFormatter TIME =
            java.time.format.DateTimeFormatter.ofPattern("MM-dd HH:mm:ss");
    /** 防止 Spring 上下文刷新时重复挂载同一个 appender。 */
    private static volatile boolean attached;

    private final WxPusherNotifier notifier;

    public ErrorLogAlertBridge(WxPusherNotifier notifier) {
        this.notifier = notifier;
    }

    @PostConstruct
    public void attach() {
        if (attached) return;
        try {
            WxPusherErrorAppender.setSink(this::push);
            org.slf4j.ILoggerFactory factory = LoggerFactory.getILoggerFactory();
            if (!(factory instanceof ch.qos.logback.classic.LoggerContext context)) {
                log.warn("日志实现不是 logback，ERROR→WxPusher 告警未启用");
                return;
            }
            WxPusherErrorAppender appender = new WxPusherErrorAppender();
            appender.setContext(context);
            appender.start();
            ch.qos.logback.classic.Logger root = context.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
            root.addAppender(appender);
            attached = true;
            log.info("ERROR 日志→WxPusher 告警已挂载（10 分钟窗口聚合，排除已有专属通道的 logger）");
        }
        catch (RuntimeException ex) {
            // 桥接失败只降级为普通日志，绝不影响启动
            log.warn("ERROR 日志→WxPusher 告警挂载失败: {}", ex.toString());
        }
    }

    private void push(WxPusherErrorAppender.ErrorAlert alert) {
        StringBuilder content = new StringBuilder();
        content.append("[锅仔后端 ERROR] ").append(alert.time()).append('\n')
                .append("logger: ").append(alert.loggerName()).append('\n')
                .append("线程: ").append(alert.thread()).append('\n')
                .append("信息: ").append(alert.message());
        if (!alert.stackTop().isBlank()) {
            content.append('\n').append("堆栈: ").append(alert.stackTop());
        }
        if (alert.suppressed() > 0) {
            content.append('\n').append(String.format(
                    "本窗口另有 %d 条 ERROR 已聚合（其中完全相同 %d 条），详见后端日志",
                    alert.suppressed(), Math.max(0, alert.sameDuplicate())));
        }
        notifier.send(content.toString());
    }
}
