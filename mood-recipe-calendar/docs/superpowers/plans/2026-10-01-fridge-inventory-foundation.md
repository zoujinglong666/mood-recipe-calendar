# 锅仔 V2 冰箱食材基础能力实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 建立“录入食材 → 记录数量与保质期 → 计算临期状态 → 为锅仔推荐提供可用食材”的第一阶段闭环。

**Architecture:** 新增用户级 `fridge_items` 持久化表和 Spring Data 服务，后端只负责库存事实与临期计算，不让大模型决定日期或数量。前端新增冰箱库存页，支持手动录入、编辑、消耗和删除；推荐入口先通过一个只读库存摘要接口获取“优先消耗食材”，后续再接入 AI 生成，避免第一阶段改动现有推荐链路。

**Tech Stack:** Spring Boot 3 / JPA / MySQL；uni-app Vue 3；现有 `ApiResponse`、`SessionAuthInterceptor`、`GuozaiButton` 和项目设计令牌。

## Global Constraints

- 所有库存记录必须绑定当前登录用户，任何查询、修改、删除都按 `openid` 隔离。
- 日期使用 `Asia/Shanghai`，保质期按自然日计算；当天到期显示“今天到期”。
- 数量必须为正数，单位和名称不能为空；接口拒绝超过 80 字的名称和超过 20 字的单位。
- 临期状态只由后端根据 `expiresOn` 计算，前端不得自行推断业务状态。
- 不新增第三方依赖；沿用现有 API 响应结构和暖色设计系统。
- 第一阶段不引入 Neo4j、Milvus、IoT 或图片识别模型；先保证手动录入闭环可用。

## 文件结构与职责

- Create: `backend/src/main/java/com/moodrecipe/backend/entity/FridgeItem.java` — 用户食材实体。
- Create: `backend/src/main/java/com/moodrecipe/backend/repository/FridgeItemRepository.java` — 用户隔离查询与排序。
- Create: `backend/src/main/java/com/moodrecipe/backend/service/FridgeInventoryService.java` — 校验、临期计算、CRUD 和消耗。
- Create: `backend/src/main/java/com/moodrecipe/backend/controller/FridgeInventoryController.java` — `/api/fridge/items` REST 接口。
- Create: `backend/src/test/java/com/moodrecipe/backend/service/FridgeInventoryServiceTest.java` — 日期、校验、用户隔离测试。
- Modify: `backend/sql/init.sql` — 新增 `fridge_items` 表。
- Create: `backend/sql/migrations/20261001_add_fridge_items.sql` — 已有线上库迁移。
- Create: `frontend/src/api/fridge.ts` — 类型与接口调用。
- Create: `frontend/src/pages/fridge/index.vue` — 冰箱库存页，遵循现有锅仔设计系统。
- Modify: `frontend/src/pages/index/index.vue` — 首页增加“冰箱里有什么”入口和临期摘要，保持现有首页布局。

---

### Task 1: 建立库存数据模型与迁移

**Files:**
- Create: `backend/src/main/java/com/moodrecipe/backend/entity/FridgeItem.java`
- Create: `backend/src/main/java/com/moodrecipe/backend/repository/FridgeItemRepository.java`
- Modify: `backend/sql/init.sql`
- Create: `backend/sql/migrations/20261001_add_fridge_items.sql`

**Interfaces:**
- `FridgeItem` 字段：`id: Long`、`openid: String`、`name: String`、`quantity: BigDecimal`、`unit: String`、`purchasedOn: LocalDate`、`expiresOn: LocalDate`、`note: String`、`createdAt: LocalDateTime`、`updatedAt: LocalDateTime`。
- Repository 提供 `findByOpenidOrderByExpiresOnAscUpdatedAtDesc(String openid)`、`findByIdAndOpenid(Long id, String openid)`。

- [ ] **Step 1: 写实体约束测试或编译基线**

运行：`./mvnw -q -DskipTests compile`

预期：当前基线通过。

- [ ] **Step 2: 写最小实体和 repository**

使用 JPA `@Table(name = "fridge_items")`；`quantity` 使用 `DECIMAL(10,2)`；名称、单位、openid 使用非空列；`expiresOn` 可为空表示用户暂未填写日期。

- [ ] **Step 3: 更新初始化 SQL 与迁移 SQL**

`fridge_items` 增加 `(openid, expires_on)` 索引；迁移脚本使用 `information_schema` 判断表是否存在，重复执行不报错。

- [ ] **Step 4: 编译验证**

运行：`./mvnw -q -DskipTests compile`

预期：PASS。

### Task 2: 实现库存服务与临期规则

**Files:**
- Create: `backend/src/main/java/com/moodrecipe/backend/service/FridgeInventoryService.java`
- Create: `backend/src/test/java/com/moodrecipe/backend/service/FridgeInventoryServiceTest.java`

**Interfaces:**
- `list(openid): List<ItemView>`
- `create(openid, CreateRequest): ItemView`
- `update(openid, id, UpdateRequest): Optional<ItemView>`
- `consume(openid, id, BigDecimal amount): Optional<ItemView>`
- `delete(openid, id): boolean`
- `summary(openid): SummaryView`
- `ItemView` 返回 `status`：`FRESH`、`SOON`、`EXPIRED`、`NO_DATE`；同时返回 `daysLeft`。

- [ ] **Step 1: 写失败测试**

覆盖：今天到期是 `SOON` 且 `daysLeft=0`；昨天到期是 `EXPIRED`；未来 1–3 天是 `SOON`；无日期是 `NO_DATE`；数量为 0 或负数被拒绝；用别人的 id 修改返回空结果。

- [ ] **Step 2: 运行失败测试**

运行：`./mvnw -q -Dtest=FridgeInventoryServiceTest test`

预期：FAIL，因为服务尚不存在。

- [ ] **Step 3: 实现最小服务**

使用 `LocalDate.now(ZoneId.of("Asia/Shanghai"))` 计算状态；`SOON` 定义为 `0 <= daysLeft <= 3`；消耗后数量为 0 时删除记录，仍有余量则保存新数量。

- [ ] **Step 4: 运行测试**

运行：`./mvnw -q -Dtest=FridgeInventoryServiceTest test`

预期：PASS。

### Task 3: 暴露 REST 接口

**Files:**
- Create: `backend/src/main/java/com/moodrecipe/backend/controller/FridgeInventoryController.java`

**Interfaces:**
- `GET /api/fridge/items` — 当前用户库存列表。
- `GET /api/fridge/summary` — `total`、`soon`、`expired`、`priorityItems`。
- `POST /api/fridge/items` — 创建食材。
- `PUT /api/fridge/items/{id}` — 修改食材。
- `POST /api/fridge/items/{id}/consume` — 消耗指定数量。
- `DELETE /api/fridge/items/{id}` — 删除食材。

- [ ] **Step 1: 写 controller 参数校验测试**

至少验证空名称、非正数量、超过长度返回 400；跨用户 id 返回 404；接口均从 `SessionAuthInterceptor.OPENID_ATTRIBUTE` 获取用户。

- [ ] **Step 2: 实现 controller**

复用 `ApiResponse`，错误消息使用中文；请求 record：`CreateItemRequest(String name, BigDecimal quantity, String unit, String purchasedOn, String expiresOn, String note)`，日期解析失败返回 400。

- [ ] **Step 3: 运行后端完整测试**

运行：`./mvnw -q test`

预期：PASS。

### Task 4: 实现冰箱库存页

**Files:**
- Create: `frontend/src/api/fridge.ts`
- Create: `frontend/src/pages/fridge/index.vue`
- Create: `frontend/src/pages/fridge/index.vue`（页面文件中的 `definePage({ name: 'fridge', layout: 'default', ... })` 自动注册路由）

**Interfaces:**
- API 类型必须与 `ItemView`、`SummaryView` 一一对应。
- 页面状态：加载中、空库存、正常列表、提交中、接口错误、删除确认。

- [ ] **Step 1: 写页面数据流**

先实现 `fetchFridgeItems`、`fetchFridgeSummary`、`createFridgeItem`、`updateFridgeItem`、`consumeFridgeItem`、`deleteFridgeItem`，所有请求走现有 `request.ts`。

- [ ] **Step 2: 实现“冰箱总览”视觉结构**

顶部使用统一 `wd-navbar`；第一张卡显示“今天优先吃”与临期数量；列表按“今天到期 / 3 天内 / 其他 / 未填写日期”分组；状态使用文字和色块双重表达，不只依赖颜色。

- [ ] **Step 3: 实现新增/编辑表单**

字段：食材名称、数量、单位、购买日期、保质期；日期字段使用可用的 uni-app 日期选择器；保存前显示行内错误。

- [ ] **Step 4: 实现消耗与删除**

消耗按钮默认扣减 1 个单位；数量大于 1 时允许输入扣减量；删除必须二次确认；成功后刷新列表和摘要。

- [ ] **Step 5: UI 验证**

运行：`pnpm exec vue-tsc --noEmit`，确认没有新增 `frontend/src/pages/fridge/index.vue` 或 `frontend/src/api/fridge.ts` 错误；使用 `pnpm dev:h5` 检查 375px 宽度下没有横向滚动、按钮点击区域至少 44px。

### Task 5: 接入首页入口与临期摘要

**Files:**
- Modify: `frontend/src/pages/index/index.vue`
- Modify: `frontend/src/api/fridge.ts`

- [ ] **Step 1: 加载轻量摘要**

首页只调用 `/api/fridge/summary`，不拉取完整列表；接口失败时隐藏摘要，不阻塞今日推荐。

- [ ] **Step 2: 增加入口卡片**

文案使用“冰箱里还有什么？”；有临期食材时显示“今天优先消耗 X 项”；点击进入 `fridge` 页面。

- [ ] **Step 3: 回归验证**

运行：`./mvnw -q test`、`git diff --check`；前端类型检查只允许记录已有错误，不得新增 fridge/index.vue 或 fridge.ts 错误。

### Task 6: 文档与部署检查

**Files:**
- Modify: `backend/DEPLOY.md`

- [ ] **Step 1: 增加迁移命令**

补充 `sql/migrations/20261001_add_fridge_items.sql` 的执行方式与接口清单。

- [ ] **Step 2: 完成最终验证**

运行：`./mvnw -q test`、`git diff --check`；记录前端已有类型错误，不把未验证的构建结果写成已完成。
