package com.moodrecipe.backend.repository;

import com.moodrecipe.backend.entity.DailyMenu;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.Optional;

public interface DailyMenuRepository extends JpaRepository<DailyMenu, Long> {

    /** 当日缓存行（每用户每天至多一行，唯一键保证）。 */
    Optional<DailyMenu> findByOpenidAndMenuDate(String openid, LocalDate menuDate);

    /** 近 N 天（含当天）的今日卡缓存行：挑选时排除近几天推荐过的菜，避免连续多天同一道。 */
    java.util.List<DailyMenu> findByOpenidAndMenuDateGreaterThanEqual(String openid, LocalDate menuDate);

    /** 沉睡召回：某天打开过首页今日菜牌的用户集合。 */
    @org.springframework.data.jpa.repository.Query("SELECT DISTINCT d.openid FROM DailyMenu d WHERE d.menuDate = :menuDate")
    java.util.List<String> findOpenidsByMenuDate(@org.springframework.data.repository.query.Param("menuDate") LocalDate menuDate);

    /** [2026-10-27] 召回消息个性化：用户最近一次的今日菜牌行（取其中的菜谱做钩子）。 */
    java.util.Optional<DailyMenu> findTopByOpenidOrderByMenuDateDesc(String openid);
}
