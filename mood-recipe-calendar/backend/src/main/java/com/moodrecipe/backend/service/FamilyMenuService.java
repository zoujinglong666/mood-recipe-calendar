package com.moodrecipe.backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.moodrecipe.backend.entity.FamilyMenuAssignment;
import com.moodrecipe.backend.entity.WeeklyMealPlan;
import com.moodrecipe.backend.repository.FamilyGroupRepository;
import com.moodrecipe.backend.repository.FamilyMemberRepository;
import com.moodrecipe.backend.repository.FamilyMenuAssignmentRepository;
import com.moodrecipe.backend.repository.UserRepository;
import com.moodrecipe.backend.repository.WeeklyMealPlanRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 家庭/双人共享菜单：把单人周菜单升级为「家人都能看到 + 谁掌勺一点即定」。
 *
 * 只读共享 + 轻分工，不做多人共编一份计划——每人的计划仍归自己，
 * 家庭视图把成员的当前计划聚合起来，分工（family_menu_assignments）挂在计划上。
 * 这是最不容易出并发问题、也不破坏现有单人闭环的共享形态。
 */
@Service
public class FamilyMenuService {

    private final FamilyMemberRepository members;
    private final UserRepository users;
    private final WeeklyMealPlanRepository plans;
    private final FamilyMenuAssignmentRepository assignments;
    private final ObjectMapper json;

    public FamilyMenuService(FamilyMemberRepository members, UserRepository users,
                             WeeklyMealPlanRepository plans, FamilyMenuAssignmentRepository assignments,
                             ObjectMapper json) {
        this.members = members;
        this.users = users;
        this.plans = plans;
        this.assignments = assignments;
        this.json = json;
    }

    /** 我的家庭共享菜单视图：不在任何家庭时返回 inFamily=false 的空视图。 */
    public FamilyMenusView menus(String openid) {
        return members.findByOpenid(openid)
                .map(mine -> viewOfGroup(mine.getGroupId()))
                .orElse(new FamilyMenusView(false, List.of()));
    }

    /**
     * 设置某天的掌勺人：cookOpenid 传 null 表示清除（未定）。
     * 计划必须属于本家庭成员；掌勺人必须是本家庭成员。
     */
    @Transactional
    public FamilyMenusView assign(String openid, Long planId, int dayIndex, String cookOpenid) {
        var mine = members.findByOpenid(openid)
                .orElseThrow(() -> new IllegalStateException("先加入家庭，再一起安排谁的掌勺日"));
        Long groupId = mine.getGroupId();

        WeeklyMealPlan plan = plans.findById(planId)
                .orElseThrow(() -> new IllegalArgumentException("计划不存在"));
        boolean planInFamily = members.findByGroupId(groupId).stream()
                .anyMatch(m -> m.getOpenid().equals(plan.getOpenid()));
        if (!planInFamily) throw new IllegalArgumentException("只能给家庭成员的计划分工");

        if (cookOpenid != null && !cookOpenid.isBlank()) {
            boolean cookInFamily = members.findByGroupId(groupId).stream()
                    .anyMatch(m -> m.getOpenid().equals(cookOpenid));
            if (!cookInFamily) throw new IllegalArgumentException("掌勺人得是家里的人");
        }

        if (dayIndex < 0 || dayIndex > 6) throw new IllegalArgumentException("这一天不存在");

        FamilyMenuAssignment row = assignments.findByPlanIdAndDayIndex(planId, dayIndex)
                .orElseGet(() -> {
                    FamilyMenuAssignment fresh = new FamilyMenuAssignment();
                    fresh.setGroupId(groupId);
                    fresh.setPlanId(planId);
                    fresh.setOwnerOpenid(plan.getOpenid());
                    fresh.setDayIndex(dayIndex);
                    return fresh;
                });
        String cook = cookOpenid == null || cookOpenid.isBlank() ? null : cookOpenid.trim();
        if (row.getCookOpenid() == null && cook == null) {
            // 已经是未定：幂等返回
            return menus(openid);
        }
        row.setCookOpenid(cook);
        assignments.save(row);
        return menus(openid);
    }

    private FamilyMenusView viewOfGroup(Long groupId) {
        List<FamilyService.MemberView> memberViews = members.findByGroupId(groupId).stream()
                .map(m -> {
                    var user = users.findByOpenid(m.getOpenid()).orElse(null);
                    return new FamilyService.MemberView(m.getOpenid(),
                            user != null && user.getNickname() != null && !user.getNickname().isBlank() ? user.getNickname() : "锅仔饭友",
                            user != null ? user.getAvatarUrl() : null,
                            m.getRole());
                })
                .toList();
        Map<String, String> nicknames = memberViews.stream()
                .collect(Collectors.toMap(FamilyService.MemberView::openid, FamilyService.MemberView::nickname));

        List<MemberMenu> menus = memberViews.stream().map(member -> {
            WeeklyMealPlan plan = plans.findTopByOpenidOrderByCreatedAtDesc(member.openid()).orElse(null);
            if (plan == null) return new MemberMenu(member.openid(), member.nickname(), member.avatarUrl(),
                    member.role(), null, 0, List.of());
            Map<Integer, FamilyMenuAssignment> byDay = assignments.findByPlanId(plan.getId()).stream()
                    .collect(Collectors.toMap(FamilyMenuAssignment::getDayIndex, Function.identity(), (a, b) -> a));
            List<DayMenu> days = readDays(plan.getPlanJson());
            List<DayMenu> withCooks = new ArrayList<>();
            for (int i = 0; i < days.size(); i++) {
                DayMenu day = days.get(i);
                FamilyMenuAssignment row = byDay.get(i);
                String cookOpenid = row == null ? null : row.getCookOpenid();
                withCooks.add(new DayMenu(i, day.day(), day.dishName(), cookOpenid,
                        cookOpenid == null ? null : nicknames.getOrDefault(cookOpenid, "家人")));
            }
            return new MemberMenu(member.openid(), member.nickname(), member.avatarUrl(),
                    member.role(), plan.getId(), withCooks.size(), withCooks);
        }).toList();

        return new FamilyMenusView(true, menus);
    }

    /** 轻量解析计划 JSON：只要 day 与 dishName，解析失败给空天列表（不阻塞共享视图）。 */
    private List<DayMenu> readDays(String planJson) {
        List<DayMenu> result = new ArrayList<>();
        try {
            JsonNode root = json.readTree(planJson == null || planJson.isBlank() ? "[]" : planJson);
            if (!root.isArray()) return result;
            int index = 0;
            for (JsonNode dayNode : root) {
                String day = dayNode.path("day").asText("");
                String dishName = dayNode.path("dishName").asText("今晚的菜单");
                result.add(new DayMenu(index++, day, dishName, null, null));
            }
        } catch (Exception ignored) {
            // JSON 异常时返回已解析部分或空列表
        }
        return result;
    }

    public record FamilyMenusView(boolean inFamily, List<MemberMenu> members) {}
    public record MemberMenu(String openid, String nickname, String avatarUrl, String role,
                             Long planId, int daysCount, List<DayMenu> days) {}
    public record DayMenu(int dayIndex, String day, String dishName, String cookOpenid, String cookNickname) {}
}
