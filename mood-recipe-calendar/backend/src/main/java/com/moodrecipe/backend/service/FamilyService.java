package com.moodrecipe.backend.service;

import com.moodrecipe.backend.entity.FamilyGroup;
import com.moodrecipe.backend.entity.FamilyMember;
import com.moodrecipe.backend.entity.User;
import com.moodrecipe.backend.repository.FamilyGroupRepository;
import com.moodrecipe.backend.repository.FamilyMemberRepository;
import com.moodrecipe.backend.repository.UserRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.List;
import java.util.Optional;

/**
 * 家庭共享：创建家庭 → 邀请码 → 家人加入。
 * 权益穿透由 MemberAccessService 负责，本服务只管组织关系。
 */
@Service
public class FamilyService {
    private static final char[] CODE_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789".toCharArray();
    private static final SecureRandom RANDOM = new SecureRandom();

    private final FamilyGroupRepository groups;
    private final FamilyMemberRepository members;
    private final UserRepository users;
    private final MemberAccessService memberAccess;

    public FamilyService(FamilyGroupRepository groups, FamilyMemberRepository members,
                         UserRepository users, MemberAccessService memberAccess) {
        this.groups = groups;
        this.members = members;
        this.users = users;
        this.memberAccess = memberAccess;
    }

    /** 我的家庭（不在任何家庭返回 null）。 */
    public FamilyView myFamily(String openid) {
        return members.findByOpenid(openid).map(this::viewOf).orElse(null);
    }

    /** 创建家庭；已是家庭成员则幂等返回当前家庭。 */
    @Transactional(rollbackFor = Exception.class)
    public FamilyView create(String openid) {
        Optional<FamilyMember> existing = members.findByOpenid(openid);
        if (existing.isPresent()) return viewOf(existing.get());
        FamilyGroup group = new FamilyGroup();
        group.setOwnerOpenid(openid);
        group.setInviteCode(generateCode());
        group = groups.save(group);
        FamilyMember owner = new FamilyMember();
        owner.setGroupId(group.getId());
        owner.setOpenid(openid);
        owner.setRole("OWNER");
        // 并发创建冲突（唯一键拦截第二笔）：DataIntegrityViolationException 默认回滚整个事务，
        // 不会留下无 owner 的孤儿组。异常向上抛，由 controller 重新查询返回已存在的家庭（幂等成功）。
        // 注意：不能在事务内 setRollbackOnly() 后又 return——Spring 提交时必抛
        // UnexpectedRollbackException（线上「锅仔后端 500 告警」同类根因，2026-10-26 修复）。
        members.save(owner);
        return viewOf(openid, group);
    }

    /** 凭邀请码加入；已在其他家庭需先退出。 */
    @Transactional(rollbackFor = Exception.class)
    public FamilyView join(String openid, String code) {
        String normalized = code == null ? "" : code.trim().toUpperCase();
        FamilyGroup group = groups.findByInviteCode(normalized)
                .orElseThrow(() -> new IllegalStateException("邀请码不对，再让家人核对一下"));
        if (group.getOwnerOpenid().equals(openid)) return viewOf(openid, group);
        if (members.findByOpenid(openid).isPresent())
            throw new IllegalStateException("你已在家庭中，先退出当前家庭再加入");
        FamilyMember member = new FamilyMember();
        member.setGroupId(group.getId());
        member.setOpenid(openid);
        member.setRole("MEMBER");
        // 并发加入冲突：同上抛给 controller 幂等重查；本事务无其他写入，回滚无害。
        members.save(member);
        return viewOf(member);
    }

    /** 退出家庭；owner 退出即解散家庭。 */
    @Transactional(rollbackFor = Exception.class)
    public void leave(String openid) {
        FamilyMember mine = members.findByOpenid(openid)
                .orElseThrow(() -> new IllegalStateException("你还没有加入家庭"));
        if ("OWNER".equals(mine.getRole())) {
            members.findByGroupId(mine.getGroupId()).forEach(members::delete);
            groups.deleteById(mine.getGroupId());
        }
        else {
            members.delete(mine);
        }
    }

    private FamilyView viewOf(FamilyMember mine) {
        FamilyGroup group = groups.findById(mine.getGroupId()).orElse(null);
        return group == null ? null : viewOf(mine.getOpenid(), group, mine.getRole());
    }

    private FamilyView viewOf(String openid, FamilyGroup group) {
        String role = members.findByOpenid(openid)
                .map(FamilyMember::getRole).orElse("MEMBER");
        return viewOf(openid, group, role);
    }

    private FamilyView viewOf(String openid, FamilyGroup group, String myRole) {
        List<MemberView> memberViews = members.findByGroupId(group.getId()).stream()
                .map(m -> {
                    User user = users.findByOpenid(m.getOpenid()).orElse(null);
                    return new MemberView(m.getOpenid(),
                            user != null && user.getNickname() != null && !user.getNickname().isBlank() ? user.getNickname() : "锅仔饭友",
                            user != null ? user.getAvatarUrl() : null,
                            m.getRole());
                })
                .toList();
        return new FamilyView(group.getId(), group.getName(), group.getInviteCode(), group.getOwnerOpenid(),
                memberAccess.isActive(group.getOwnerOpenid()), memberAccess.isActive(openid), myRole, memberViews);
    }

    private String generateCode() {
        for (int attempt = 0; attempt < 5; attempt++) {
            StringBuilder sb = new StringBuilder(6);
            for (int i = 0; i < 6; i++) sb.append(CODE_CHARS[RANDOM.nextInt(CODE_CHARS.length)]);
            String code = sb.toString();
            if (groups.findByInviteCode(code).isEmpty()) return code;
        }
        throw new IllegalStateException("邀请码生成失败，请重试");
    }

    public record MemberView(String openid, String nickname, String avatarUrl, String role) {}

    public record FamilyView(Long groupId, String name, String inviteCode, String ownerOpenid,
                             boolean ownerIsMember, boolean myAccessActive, String myRole,
                             List<MemberView> members) {}
}
