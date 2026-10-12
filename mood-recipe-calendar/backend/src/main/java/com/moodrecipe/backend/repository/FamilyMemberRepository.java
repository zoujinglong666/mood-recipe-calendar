package com.moodrecipe.backend.repository;

import com.moodrecipe.backend.entity.FamilyMember;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface FamilyMemberRepository extends JpaRepository<FamilyMember, Long> {

    Optional<FamilyMember> findByOpenid(String openid);

    List<FamilyMember> findByGroupId(Long groupId);
}
