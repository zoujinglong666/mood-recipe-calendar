package com.moodrecipe.backend.repository;

import com.moodrecipe.backend.entity.ShareRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;

@Repository
public interface ShareRecordRepository extends JpaRepository<ShareRecord, Long> {

    long countByOpenidAndShareDate(String openid, LocalDate shareDate);

    boolean existsByOpenidAndShareDate(String openid, LocalDate shareDate);

    boolean existsByOpenidAndShareDateAndScene(String openid, LocalDate shareDate, String scene);

    boolean existsByOpenid(String openid);
}
