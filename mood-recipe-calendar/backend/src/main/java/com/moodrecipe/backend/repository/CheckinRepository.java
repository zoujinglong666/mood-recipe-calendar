package com.moodrecipe.backend.repository;

import com.moodrecipe.backend.entity.Checkin;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CheckinRepository extends JpaRepository<Checkin, Long> {

    /** 某用户全部签到（按日期升序） */
    List<Checkin> findByOpenidOrderByCheckinDateAsc(String openid);

    /** 某用户某天是否已签到 */
    Optional<Checkin> findByOpenidAndCheckinDate(String openid, String date);
}
