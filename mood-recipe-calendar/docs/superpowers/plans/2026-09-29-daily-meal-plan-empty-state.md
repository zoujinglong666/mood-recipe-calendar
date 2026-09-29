# 一日三餐空状态升级 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 把一日三餐无合格结果页升级为与首页、食谱页和正常三餐页一致的锅仔品牌空状态。

**Architecture:** 保留现有 `load()`、导航和服务端安全拦截，仅在 `daily-meal-plan/index.vue` 内重组空状态模板与样式，并用独立的首次加载、重新生成状态阻止重复请求。复用现有锅仔图片、`mrc-btn-primary` 和页面暖色视觉，不新增组件或依赖。

**Tech Stack:** Vue 3、uni-app、TypeScript、SCSS/CSS、Wot UI、微信小程序构建。

## Global Constraints

- 只修改 `frontend/src/pages/daily-meal-plan/index.vue` 及构建生成物。
- 不改变 API 契约，不降低“不展示不合格菜单”的安全规则。
- H5 与微信小程序均不得依赖浏览器专属 API。
- 重新生成期间禁止重复请求，按钮必须有可见状态和无障碍标签。
- 复用现有锅仔资产和全局按钮类，不增加依赖或新设计体系。

---

### Task 1: 重做无合格结果状态

**Files:**
- Modify: `frontend/src/pages/daily-meal-plan/index.vue`
- Generated: `frontend/dist/build/mp-weixin/pages/daily-meal-plan/*`

**Interfaces:**
- Consumes: `load(): Promise<void>`、`loading: Ref<boolean>`、`retrying: Ref<boolean>`、`STATIC_BASE_URL`、全局 `mrc-btn-primary`。
- Produces: `.empty-state`、`.empty-hero`、`.empty-checks` 和首次加载/重新生成防重复行为。

- [x] **Step 1: 运行结构验收并确认旧页面失败**

```bash
rg -n "empty-hero|empty-checks|正在重新搭配" frontend/src/pages/daily-meal-plan/index.vue
```

Expected: 无匹配，命令退出码为 1。

- [x] **Step 2: 给加载函数增加重复请求保护**

增加 `retrying`，并在 `load()` 中区分首次加载与空状态重试：

```ts
async function load() {
  if (loading.value || retrying.value)
    return
  const isRetry = empty.value
  if (isRetry)
    retrying.value = true
  else
    loading.value = true
```

把 `loading` 初始值设为 `false`，保证首次 `onShow(load)` 仍会执行；成功后清除 `empty`，失败时保持空状态，`finally` 分别清理两个加载标记。

- [x] **Step 3: 替换空状态模板**

用以下层级替代旧 `.state`：

```vue
<view v-else-if="empty" class="empty-state">
  <view class="empty-hero">
    <view class="empty-hero__copy">
      <text class="empty-hero__eyebrow">GUOZAI · DAILY MENU</text>
      <text class="empty-hero__title">这次还没配出\n放心的一日三餐</text>
      <text class="empty-hero__description">锅仔宁可多想一会儿，也不会把没通过检查的菜单端上来。</text>
    </view>
    <image class="empty-hero__image" :src="`${STATIC_BASE_URL}/static/guozai/action_10_thinking.png`" mode="aspectFit" />
  </view>
  <view class="empty-checks">
    <text class="empty-checks__label">再试一次，锅仔会重新核对</text>
    <view class="empty-checks__items">
      <text>忌口安全</text><text>当季候选</text><text>三餐不重复</text>
    </view>
    <view class="empty-checks__note">只展示完整且通过检查的三餐计划</view>
  </view>
  <view class="empty-action mrc-btn-primary pressable" role="button" :aria-label="retrying ? '正在重新搭配一日三餐' : '重新生成一日三餐'" :class="{ 'empty-action--disabled': retrying }" @click="load">
    {{ retrying ? '正在重新搭配…' : '重新生成一日三餐' }}
  </view>
</view>
```

- [x] **Step 4: 添加与现有页面一致的响应式样式**

使用现有暖杏色页面背景、40rpx Hero 圆角、30rpx 内容卡圆角、珊瑚色强调和项目阴影；限制文字宽度，锅仔图片叠在 Hero 右下方，移除旧 `.state` 与 `.retry` 样式。按钮使用全局 `mrc-btn-primary`，仅补充布局和禁用态。

- [x] **Step 5: 运行结构、静态检查与构建验收**

```bash
rg -n "empty-hero|empty-checks|正在重新搭配" frontend/src/pages/daily-meal-plan/index.vue
corepack pnpm@10 exec eslint src/pages/daily-meal-plan/index.vue --quiet
corepack pnpm@10 run type-check
env CHOKIDAR_USEPOLLING=true corepack pnpm@10 run build:mp-weixin
git diff --check
```

Expected: 结构检查有匹配；页面 ESLint 和微信小程序构建退出码为 0；`git diff --check` 无输出。若类型检查只报告 `@wot-ui/ui/components/wd-img/wd-img.vue` 的既有 TS2774，则记录为第三方阻塞，不归因于本次页面。

- [x] **Step 6: 保留实现，等待三餐功能整体提交**

当前页面与尚未提交的 `dailyMealPlan` API、后端服务和路由生成物共同工作。为避免创建一个单独检出后无法构建的残缺提交，本次不单独提交页面，等待三餐功能整体提交。

```bash
git add frontend/src/pages/daily-meal-plan/index.vue frontend/src/api/dailyMealPlan.ts frontend/dist/build/mp-weixin/pages/daily-meal-plan docs/superpowers/plans/2026-09-29-daily-meal-plan-empty-state.md
# 与三餐计划服务端和路由文件一起提交
```
