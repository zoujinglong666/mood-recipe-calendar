package com.moodrecipe.backend.repository;

import com.moodrecipe.backend.entity.FamilyMenuAssignment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface FamilyMenuAssignmentRepository extends JpaRepository<FamilyMenuAssignment, Long> {
    List<FamilyMenuAssignment> findByPlanId(Long planId);
    Optional<FamilyMenuAssignment> findByPlanIdAndDayIndex(Long planId, Integer dayIndex);
    void deleteByPlanId(Long planId);
}
