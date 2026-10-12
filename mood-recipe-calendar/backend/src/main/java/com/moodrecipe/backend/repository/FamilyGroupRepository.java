package com.moodrecipe.backend.repository;

import com.moodrecipe.backend.entity.FamilyGroup;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface FamilyGroupRepository extends JpaRepository<FamilyGroup, Long> {

    Optional<FamilyGroup> findByInviteCode(String inviteCode);
}
