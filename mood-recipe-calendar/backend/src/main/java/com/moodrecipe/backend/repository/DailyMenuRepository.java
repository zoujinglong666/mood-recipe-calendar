package com.moodrecipe.backend.repository;

import com.moodrecipe.backend.entity.DailyMenu;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.Optional;

public interface DailyMenuRepository extends JpaRepository<DailyMenu, Long> {

    /** 当日缓存行（每用户每天至多一行，唯一键保证）。 */
    Optional<DailyMenu> findByOpenidAndMenuDate(String openid, LocalDate menuDate);
}
