package com.moodrecipe.backend.controller;

import com.moodrecipe.backend.common.ApiResponse;
import com.moodrecipe.backend.config.SessionAuthInterceptor;
import com.moodrecipe.backend.service.FamilyMenuService;
import com.moodrecipe.backend.service.FamilyService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

/** 家庭共享：创建 / 加入 / 退出 / 我的家庭 / 共享菜单与分工。 */
@RestController
@RequestMapping("/api/family")
public class FamilyController {
    private final FamilyService family;
    private final FamilyMenuService familyMenus;

    @Autowired
    public FamilyController(FamilyService family, FamilyMenuService familyMenus) {
        this.family = family;
        this.familyMenus = familyMenus;
    }

    @GetMapping
    public ApiResponse<?> myFamily(@RequestAttribute(SessionAuthInterceptor.OPENID_ATTRIBUTE) String openid) {
        return ApiResponse.ok(family.myFamily(openid));
    }

    @PostMapping
    public ApiResponse<?> create(@RequestAttribute(SessionAuthInterceptor.OPENID_ATTRIBUTE) String openid) {
        try {
            return ApiResponse.ok(family.create(openid));
        }
        catch (IllegalStateException e) {
            return ApiResponse.error(400, e.getMessage());
        }
        catch (org.springframework.dao.DataIntegrityViolationException e) {
            // 并发创建唯一键冲突：事务已回滚（无孤儿组），重新查询返回已存在的家庭（幂等成功）
            return ApiResponse.ok(family.myFamily(openid));
        }
    }

    @PostMapping("/join")
    public ApiResponse<?> join(@RequestAttribute(SessionAuthInterceptor.OPENID_ATTRIBUTE) String openid, @RequestBody JoinRequest request) {
        try {
            return ApiResponse.ok(family.join(openid, request == null ? null : request.code()));
        }
        catch (IllegalStateException e) {
            return ApiResponse.error(400, e.getMessage());
        }
        catch (org.springframework.dao.DataIntegrityViolationException e) {
            // 并发加入唯一键冲突：事务已回滚，重新查询返回当前家庭（幂等成功）
            return ApiResponse.ok(family.myFamily(openid));
        }
    }

    @PostMapping("/leave")
    public ApiResponse<?> leave(@RequestAttribute(SessionAuthInterceptor.OPENID_ATTRIBUTE) String openid) {
        try {
            family.leave(openid);
            return ApiResponse.ok();
        }
        catch (IllegalStateException e) {
            return ApiResponse.error(400, e.getMessage());
        }
    }

    /** 家庭共享菜单：成员各自的当前周计划 + 每天谁掌勺。 */
    @GetMapping("/menus")
    public ApiResponse<?> menus(@RequestAttribute(SessionAuthInterceptor.OPENID_ATTRIBUTE) String openid) {
        return ApiResponse.ok(familyMenus.menus(openid));
    }

    /** 设置某天的掌勺人（cookOpenid 传空 = 未定）；家庭成员均可操作。 */
    @PostMapping("/assign")
    public ApiResponse<?> assign(@RequestAttribute(SessionAuthInterceptor.OPENID_ATTRIBUTE) String openid,
                                 @RequestBody AssignRequest request) {
        try {
            if (request == null || request.planId() == null || request.dayIndex() == null)
                return ApiResponse.error(400, "请选择要分工的那一天");
            return ApiResponse.ok(familyMenus.assign(openid, request.planId(), request.dayIndex(), request.cookOpenid()));
        }
        catch (IllegalStateException | IllegalArgumentException e) {
            return ApiResponse.error(400, e.getMessage());
        }
    }

    public record JoinRequest(String code) {}
    public record AssignRequest(Long planId, Integer dayIndex, String cookOpenid) {}
}
