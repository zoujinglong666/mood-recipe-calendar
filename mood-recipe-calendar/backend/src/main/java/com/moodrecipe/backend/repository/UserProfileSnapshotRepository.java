package com.moodrecipe.backend.repository;

import com.moodrecipe.backend.entity.UserProfileSnapshot;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface UserProfileSnapshotRepository extends JpaRepository<UserProfileSnapshot, Long> {

    /** [2026-10-20] 取某用户的画像演化历史（从新到旧），供回溯与「越用越聪明」曲线。 */
    List<UserProfileSnapshot> findByOpenidOrderBySnapshotAtDesc(String openid, Pageable pageable);
}
