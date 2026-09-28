package com.moodrecipe.backend.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** 今日菜单缓存（TodayBoard）：一个用户一天一行，打开即有的零输入每日答案。 */
@Data
@Entity
@Table(name = "daily_menus", uniqueConstraints =
        @UniqueConstraint(name = "uk_daily_menu_openid_date", columnNames = {"openid", "menu_date"}))
public class DailyMenu {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 64)
    private String openid;

    @Column(name = "menu_date", nullable = false)
    private LocalDate menuDate;

    @Column(name = "recipe_id", nullable = false)
    private Long recipeId;

    /** 锅仔今日一句话，本地模板生成，后续可换 LLM。 */
    @Column(name = "guozai_line", length = 200)
    private String guozaiLine;

    /** POOL=菜谱池挑选；AI=生成（预留）。 */
    @Column(length = 16, nullable = false)
    private String source = "POOL";

    /** 当日换了几次，0 = 首次生成。 */
    @Column(nullable = false)
    private Integer variant = 0;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    void prePersist() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = this.createdAt;
    }

    @PreUpdate
    void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
