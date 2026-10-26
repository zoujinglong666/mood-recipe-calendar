# -*- coding: utf-8 -*-
"""Add CannotAcquireLockException handling to WeeklyMealPlanController.coverGuarded."""
import io, sys

P = r"C:\Users\DELL\DoubaoWork\chats\2026-09-01\new-chat\mood-recipe-calendar\backend\src\main\java\com\moodrecipe\backend\controller\WeeklyMealPlanController.java"

with io.open(P, "r", encoding="utf-8") as f:
    s = f.read()

old = """    private ApiResponse<?> coverGuarded(String openid, java.util.function.Supplier<java.util.Optional<?>> call) {
        try {
            quotas.consume(openid, UsageQuotaService.Feature.COVER_GENERATE);
        } catch (IllegalStateException e) {
            return ApiResponse.error(429, e.getMessage());
        }
        try {
            var result = call.get();
            if (result.isEmpty()) quotas.release(openid, UsageQuotaService.Feature.COVER_GENERATE);
            return result.map(ApiResponse::ok).orElseGet(() -> ApiResponse.error(404, "计划不存在"));
        } catch (IllegalArgumentException e) {
            // 详情页是多层 swiper（上下滑切天、左右滑切菜），滑动/并发换菜后客户端拿到的
            // dayIndex / dishIndex 可能与服务端最新菜单不同步而越界。这属于「菜单已更新」，
            // 不是用户输入错误：归还配额并返回 404 友好提示，避免落到全局 IAE 处理器
            // 被暴露成「请求参数不合法，请检查输入」这种误导文案。
            quotas.release(openid, UsageQuotaService.Feature.COVER_GENERATE);
            return ApiResponse.error(404, "这一天的菜单已经更新，请刷新后再试");
        } catch (IllegalStateException e) {
            quotas.release(openid, UsageQuotaService.Feature.COVER_GENERATE);
            return ApiResponse.error(500, "封面生成失败，可稍后重试");
        }
    }"""

new = """    private ApiResponse<?> coverGuarded(String openid, java.util.function.Supplier<java.util.Optional<?>> call) {
        try {
            quotas.consume(openid, UsageQuotaService.Feature.COVER_GENERATE);
        } catch (IllegalStateException e) {
            return ApiResponse.error(429, e.getMessage());
        } catch (org.springframework.dao.CannotAcquireLockException e) {
            // 配额行锁竞争（同用户并发补图尖峰）：consume 已整体回滚、未扣费，转 503 让客户端重试，
            // 不再落到全局兜底误报「锅仔后端 500 告警」（UnexpectedRollbackException 同类根因）。
            return ApiResponse.error(503, "系统繁忙，请稍后重试");
        }
        try {
            var result = call.get();
            if (result.isEmpty()) quotas.release(openid, UsageQuotaService.Feature.COVER_GENERATE);
            return result.map(ApiResponse::ok).orElseGet(() -> ApiResponse.error(404, "计划不存在"));
        } catch (IllegalArgumentException e) {
            // 详情页是多层 swiper（上下滑切天、左右滑切菜），滑动/并发换菜后客户端拿到的
            // dayIndex / dishIndex 可能与服务端最新菜单不同步而越界。这属于「菜单已更新」，
            // 不是用户输入错误：归还配额并返回 404 友好提示，避免落到全局 IAE 处理器
            // 被暴露成「请求参数不合法，请检查输入」这种误导文案。
            quotas.release(openid, UsageQuotaService.Feature.COVER_GENERATE);
            return ApiResponse.error(404, "这一天的菜单已经更新，请刷新后再试");
        } catch (IllegalStateException e) {
            quotas.release(openid, UsageQuotaService.Feature.COVER_GENERATE);
            return ApiResponse.error(500, "封面生成失败，可稍后重试");
        } catch (org.springframework.dao.CannotAcquireLockException e) {
            // 菜单行保存时的锁竞争：同样归还配额并转 503，避免 500 告警误报。
            quotas.release(openid, UsageQuotaService.Feature.COVER_GENERATE);
            return ApiResponse.error(503, "系统繁忙，请稍后重试");
        }
    }"""

if old not in s:
    print("ERROR: coverGuarded block not found")
    sys.exit(1)
s = s.replace(old, new)
with io.open(P, "w", encoding="utf-8", newline="\n") as f:
    f.write(s)
print("OK: coverGuarded now handles CannotAcquireLockException")
