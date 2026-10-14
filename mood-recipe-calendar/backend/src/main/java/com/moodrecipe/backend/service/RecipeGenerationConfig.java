package com.moodrecipe.backend.service;

import lombok.Data;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 每日自动生成菜谱的运行时配置（内存态，重启后回到默认值）。
 * 管理页可动态开关和调整数量，不需要改 application.properties 重启。
 */
@Data
@Component
public class RecipeGenerationConfig {

    /** 是否开启每日自动生成 */
    private boolean enabled = true;

    /** 每天自动生成几道新菜 */
    private int dailyCount = 10;

    /** 上次运行时间 */
    private LocalDateTime lastRunAt;

    /** 上次实际成功入库数量 */
    private int lastRunCount;

    /** 上次运行结果消息 */
    private String lastRunMessage;

    /** 下次预计运行时间（仅供前端展示） */
    private String nextRunAt;
}
