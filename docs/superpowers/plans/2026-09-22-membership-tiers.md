# 会员分级与配额 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 用后端统一配额实现免费与会员的推荐、周菜单和锅仔智能体分级。

**Architecture:** `UsageQuotaService` 作为唯一授权入口，按中国时区的日/周窗口记录用量。控制器在调用业务服务前检查配额，前端通过配额摘要渲染余量和会员引导。

**Tech Stack:** Spring Boot、JPA、MySQL、Vue 3、uni-app、JUnit 5。

## Global Constraints

- 普通用户推荐 3 次/日，会员 20 次/日。
- 普通用户简单周菜单 1 次/自然周，会员不限。
- 智能体、重排和替换仅会员可用。
- 后端是唯一可信计数来源。

### Task 1: 服务端配额与会员授权

**Files:**
- Create: `backend/.../entity/UsageQuota.java`, `backend/.../repository/UsageQuotaRepository.java`, `backend/.../service/UsageQuotaService.java`
- Modify: `backend/.../controller/RecipeController.java`, `backend/.../controller/WeeklyMealPlanController.java`, `backend/sql/init.sql`
- Test: `backend/src/test/.../UsageQuotaServiceTest.java`

- [ ] 写出普通用户日推荐第四次拒绝、会员第二十一次拒绝、普通用户周菜单第二次拒绝的测试。
- [ ] 运行测试确认失败。
- [ ] 实现事务内配额检查、原子计数及会员判断。
- [ ] 将推荐、周菜单和智能体接口接入授权；推荐失败归还次数。
- [ ] 运行后端测试。

### Task 2: 前端余量与会员拦截

**Files:**
- Modify: `frontend/src/api/recipes.ts`, `frontend/src/api/weeklyPlans.ts`, `frontend/src/pages/index/index.vue`, `frontend/src/pages/meal-agent/index.vue`, `frontend/src/pages/membership/index.vue`

- [ ] 新增配额摘要请求类型和方法。
- [ ] 首页显示推荐余量，触顶时打开会员页。
- [ ] 普通用户智能体页显示会员拦截并导向简单周菜单。
- [ ] 更新会员权益文案。
- [ ] 构建前端并运行后端完整测试。
