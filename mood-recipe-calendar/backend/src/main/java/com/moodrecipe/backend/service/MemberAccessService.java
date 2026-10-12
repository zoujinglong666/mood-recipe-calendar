package com.moodrecipe.backend.service;

import com.moodrecipe.backend.config.AppClock;
import com.moodrecipe.backend.entity.User;
import com.moodrecipe.backend.repository.FamilyGroupRepository;
import com.moodrecipe.backend.repository.FamilyMemberRepository;
import com.moodrecipe.backend.repository.UserRepository;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * 会员权益的统一判定入口（家庭共享穿透点）。
 * 规则：本人是会员，或所在家庭的创建者是会员。
 * 所有原先直接读 users 表判会员的逻辑（UsageQuotaService.member /
 * VirtualCommerceService.isActiveMember）都收敛到这里，改造只需动这一处。
 */
@Service
public class MemberAccessService {
    private final UserRepository users;
    private final FamilyMemberRepository familyMembers;
    private final FamilyGroupRepository familyGroups;

    public MemberAccessService(UserRepository users, FamilyMemberRepository familyMembers,
                               FamilyGroupRepository familyGroups) {
        this.users = users;
        this.familyMembers = familyMembers;
        this.familyGroups = familyGroups;
    }

    public boolean isActive(String openid) {
        if (openid == null || openid.isBlank()) return false;
        if (personalMember(openid)) return true;
        return familyMembers.findByOpenid(openid)
                .map(member -> familyGroups.findById(member.getGroupId()))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .map(group -> personalMember(group.getOwnerOpenid()))
                .orElse(false);
    }

    private boolean personalMember(String openid) {
        return users.findByOpenid(openid)
                .filter(user -> Integer.valueOf(1).equals(user.getIsMember()))
                .filter(user -> user.getMemberExpire() != null && user.getMemberExpire().isAfter(AppClock.now()))
                .isPresent();
    }
}
