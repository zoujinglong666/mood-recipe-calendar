package com.moodrecipe.backend.repository;

import com.moodrecipe.backend.entity.MonthlyAlbum;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface MonthlyAlbumRepository extends JpaRepository<MonthlyAlbum, Long> {
    Optional<MonthlyAlbum> findByOpenidAndMonth(String openid, String month);
}
