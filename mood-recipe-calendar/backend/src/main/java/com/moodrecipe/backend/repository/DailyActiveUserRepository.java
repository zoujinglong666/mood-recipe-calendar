package com.moodrecipe.backend.repository;

import com.moodrecipe.backend.entity.DailyActiveUser;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface DailyActiveUserRepository extends JpaRepository<DailyActiveUser, Long> {

    /** 某日活跃人数（去重后行数）。 */
    long countByActiveDate(LocalDate date);

    /** 某日活跃的 openid 列表，用于计算留存。 */
    List<DailyActiveUser> findByActiveDate(LocalDate date);
}
