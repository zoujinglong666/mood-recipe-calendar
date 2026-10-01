# 菜谱时光机记录编辑页 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** 从菜谱时光机详情进入独立记录编辑页，完成历史记录的照片与文字内容修改。

**Architecture:** 新增一个独立的 `pages/record/edit.vue` 页面，复用现有记录接口、图片上传工具和锅仔表单组件；时光机详情弹层只负责传递记录 ID 与返回刷新，不复制编辑业务。页面注册到 `pages.json`，不增加后端接口。

**Tech Stack:** uni-app + Vue 3 `<script setup>` + TypeScript + SCSS + 现有 Wot UI/锅仔组件。

## Global Constraints

- 复用现有设计 token 与上传接口，不新增依赖。
- 编辑页所有可点击控件触摸区域至少 44pt，并保留安全区内边距。
- 不修改工作区中已有的日历构建产物改动。
- 编辑保存必须调用现有 `PUT /records/{id}`，不能创建重复记录。

### Task 1: 提取可复用的记录编辑表单

**Files:**
- Create: `frontend/src/components/record/RecordEditorForm.vue`
- Modify: `frontend/src/pages/record/index.vue`

**Interfaces:**
- Consumes: `RecordItem`, `RecordPayload`, `fetchRecord`, `updateRecord`, `uploadFile`, `chooseImageFiles`。
- Produces: `RecordEditorForm` 通过 `saved` 事件返回保存后的记录；通过 `cancel` 事件请求退出。

- [ ] **Step 1: 写表单行为测试清单**

  手工验证目标：编辑态加载后菜名、心情、日期、时长、备注和全部照片与接口一致；照片删除/排序后保存 payload 的 `imageUrls` 顺序与界面一致。

- [ ] **Step 2: 提取最小表单组件**

  将现有记录页中 `loadForEdit`、`addImages`、`uploadImages`、`removePhoto`、`movePhoto`、`publish` 相关状态与模板提取到组件；保留新建页原有成功弹窗和草稿逻辑，不改变新建记录行为。

- [ ] **Step 3: 接回新建记录页**

  `pages/record/index.vue` 使用表单组件的创建模式，继续支持从烹饪页带入菜名/菜谱 ID，并保留现有保存后的成功反馈。

- [ ] **Step 4: 运行前端类型检查**

  Run: `pnpm exec vue-tsc --noEmit`

  Expected: 不新增 `RecordEditorForm` 或 `record/index.vue` 相关类型错误；若存在既有错误，记录其原始位置。

### Task 2: 新增独立编辑页面与时光机入口

**Files:**
- Create: `frontend/src/pages/record/edit.vue`
- Modify: `frontend/src/pages/timeline/index.vue`
- Modify: `frontend/src/pages.json`

**Interfaces:**
- Consumes: 路由 query `id`、`RecordEditorForm` 的 `saved/cancel` 事件。
- Produces: 成功保存后返回 `timeline`，并通过 `eventChannel` 或页面显示生命周期触发重新加载。

- [ ] **Step 1: 增加时光机详情“编辑记录”按钮**

  在 `.detail-recipe-actions` 后加入主按钮，点击时关闭弹层并执行 `router.push({ name: 'record-edit', query: { id: String(selected.id) } })`。

- [ ] **Step 2: 注册编辑页路由**

  在 `frontend/src/pages.json` 增加 `pages/record/edit`，使用 `default` layout、透明自定义导航栏和标题“编辑这顿饭”。

- [ ] **Step 3: 实现编辑页面壳层**

  页面负责读取并校验 `id`、展示导航栏、承载 `RecordEditorForm`、处理加载/保存错误，并在成功后 `router.replace({ name: 'timeline' })`。

- [ ] **Step 4: 运行页面级静态检查**

  Run: `rg -n "record-edit|RecordEditorForm|编辑记录" frontend/src/pages frontend/src/components frontend/src/pages.json`

  Expected: 入口、页面、路由注册均存在且使用同一名称。

### Task 3: 回归验证与提交

**Files:**
- Verify only: `frontend/src/pages/record/edit.vue`, `frontend/src/pages/timeline/index.vue`, `frontend/src/pages.json`, `frontend/src/components/record/RecordEditorForm.vue`

- [ ] **Step 1: 检查差异格式**

  Run: `git diff --check`

  Expected: 无空白错误。

- [ ] **Step 2: 运行前端类型检查并记录既有问题**

  Run: `pnpm exec vue-tsc --noEmit`

  Expected: 本次新增页面无错误；项目既有错误不得被误报为本次回归。

- [ ] **Step 3: 检查工作区范围**

  Run: `git status --short`

  Expected: 仅出现本计划涉及文件及开始前已存在的日历构建产物改动。

- [ ] **Step 4: 提交功能分支**

  ```bash
  git add frontend/src/components/record/RecordEditorForm.vue frontend/src/pages/record/index.vue frontend/src/pages/record/edit.vue frontend/src/pages/timeline/index.vue frontend/src/pages.json docs/superpowers/specs/2026-10-01-record-edit-page-design.md docs/superpowers/plans/2026-10-01-record-edit-page.md
  git commit -m "feat(record): add standalone history edit page"
  ```
