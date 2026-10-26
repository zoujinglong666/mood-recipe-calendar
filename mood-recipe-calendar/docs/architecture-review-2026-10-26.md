# 心情菜谱日历 — 后端 Agent 架构评审报告

> 评审时间：2026-10-26　评审范围：`backend/`（273 个 Java 文件，83 Service / 39 Controller / 37 Entity / 37 Repository / 37 Agent+11 Tools）+ `frontend/`（30 页面、uni-app/Vue3）
> 评审方式：源码静态阅读（Agent 核心链路、配置、定时任务、事务、异常、鉴权、前端构建），未做线上压测。

---

## 一、总体结论

**这套系统的 Agent 架构设计成熟度，明显高于一般个人/小团队小程序项目。** 已经具备了"生产级 AI 应用"的关键骨架：

- 通用 ReAct 工具循环（`AgentLoop`），原生 function calling 与 JSON 协议双通道自动降级；
- 插件化工具注册（`ToolRegistry`，11 个工具，读工具重试 / 写工具不重试的幂等策略）；
- 多层降级链路（模型不可用 → 本地规则 → 本地菜谱 → 兜底文案，用户永远有结果）；
- 确定性输出护栏（`AgentOutputGate` / `MenuQualityScorer` / `AgentAcceptanceService` / 图片质量门）；
- 带半衰期、置信度、证据链的事实记忆系统（`AgentMemoryStore`），且安全类记忆（过敏原）禁止模型推断写入；
- 餐后结果回流策略的学习闭环（`AgentLearningService`）；
- 外部模型调用的并发闸、429 Retry-After、指数退避、超时分级；
- 70 个测试文件，Agent 层 21 个，Live 测试有环境变量门禁。

**当前主要技术债不在"Agent 设计"，而在"工程化收尾"**：数据库迁移无统一工具、定时任务单线程、三个 Agent 巨型类、前端主包未分包、以及本次刚爆发的事务边界问题（已修 11 处，但模式未固化）。

---

## 二、当前架构（基于代码实测）

```
微信小程序 (uni-app/Vue3, 30 页面, 主包 1.34MB)
   │  X-Session-Token / 统一请求层(api/request.ts, ApiError, 401 恢复)
   ▼
接入层  39 Controller
   ├─ SessionAuthInterceptor（服务端 session 鉴权 + DAU 标记）
   ├─ RateLimitFilter（内存限流：general 120/min、auth 20/min）
   ├─ SecurityHeaders / RequestLogging / CORS / Jackson
   └─ GlobalExceptionHandler（分级告警：预期状态不告警、500 才推 WxPusher）
   ▼
智能体核心层
   ├─ AgentLoop（ReAct 循环，maxSteps 1-8，工具结果 2000 字预算截断）
   ├─ ToolRegistry（11 工具：搜菜谱/冰箱/记忆/历史/冲突检查/评分/食材合并/联网/指南RAG/记事实）
   ├─ DialogueAgent（975 行：对话编排、冰箱问答、事实合并）
   ├─ GuozaiAgent（1047 行：推荐、周计划、月信、心情洞察、评分、文案、安全）
   ├─ MenuPlannerAgent（1122 行：周计划生成→批判→证据修复→质量门）
   ├─ AgentOutputGate（枚举外泄/乱码/超长护栏 + 自适应纪律台账）
   ├─ AgentMemoryStore（来源分级半衰期 365/60/14/45/90 天、置信度、证据）
   ├─ AgentLearningService / ReflectionService / FeedbackLearning（结果回流）
   └─ AgentContextCompactor（上下文压缩）
   ▼
模型层  TieredLlmClient（@Primary，会员分层路由）
   ├─ AgnesLlmClient（免费层，Semaphore 3 并发闸，429 退避，JSON 修复重试）
   └─ DeepSeek（付费层，默认关闭，16 并发闸）
   ▼
数据与基础设施
   ├─ 37 Repository + JPA(Hibernate, ddl-auto=update) + Caffeine
   ├─ MySQL mood_recipe（手工 SQL 迁移 27 个，无 Flyway/Liquibase）
   ├─ 定时任务 13+（@Scheduled，默认单线程调度器）
   ├─ 异步任务（推荐/周计划：虚拟线程执行器 + 内存任务表 + 10 分钟 TTL）
   └─ 外部：腾讯 COS / 微信虚拟支付 / 订阅消息 / WxPusher 告警
```

---

## 三、问题清单（按优先级）

### P0 — 建议立即处理（稳定性 / 数据安全）

#### P0-1　定时任务默认单线程，凌晨任务会连锁延迟

**证据**：`@EnableScheduling` 已开启，但全工程没有任何 `ThreadPoolTaskScheduler` / `TaskScheduler` Bean。Spring 默认用 pool size = 1 的单线程调度器。当前 13+ 个 `@Scheduled` 任务全部串行：

| 时间 | 任务 |
|---|---|
| 02:57 | 菜谱去重 |
| 03:17 | 每日菜谱生成 |
| 03:37（周一） | 菜谱采集（调 LLM，可能跑几十分钟） |
| 04:17 | 乱码清理 |
| 04:37 | 菜-心情分类（每 30 分钟还跑一次） |
| 04:57（每月 1 日） | 时令推荐 |
| 另有 | SwapCandidate 15/30 分钟 fixedDelay、虚拟支付对账 2 个 fixedDelay、限流清理、账户删除清理 |

**影响**：03:37 的 LLM 采集任务一旦卡住，04:17 之后的任务全部排队延迟（fixedDelay 任务还会互相挤占）。某次采集异常耗时 1 小时，整个凌晨任务链顺延。

**建议**：新增一个配置类，提供 4-8 线程的 `ThreadPoolTaskScheduler`（线程名 `sched-`，优雅关闭），把 LLM 类长任务与清理类短任务分到不同池；或直接给长任务配 `@Async` 虚拟线程执行器。
**工作量**：约 0.5 天。

#### P0-2　数据库 Schema 双轨管理且无迁移工具（已实际出事故）

**证据**：`spring.jpa.hibernate.ddl-auto=update` 用于生产；同时 `backend/sql/migrations/` 有 27 个手工命名的 SQL，无 Flyway/Liquibase；`SchemaIndexInitializer` / `RecipeCatalogSeeder` / `NutritionKnowledgePackSeeder` 在代码里绕过迁移机制。本次启动期实测报错 `Table 'mood_recipe.user_profile_snapshots' doesn't exist`（非致命）。

**影响**：手工 SQL 漏执行就缺表（已发生）；ddl-auto=update 只加不减、不保证列顺序/类型、生产环境不可控。多环境部署时无法确认"这台库执行到了哪个版本"。

**建议**：
1. 引入 Flyway（`spring-boot-starter` 自带集成），把现有 27 个 SQL 按时间顺序合并为基线 `V1__baseline.sql`，之后每个 schema 变更一个新文件；
2. ddl-auto 从 `update` 改为 `validate`；
3. 启动时先补 `user_profile_snapshots` 的迁移。
**工作量**：1 天（含基线核对）。

#### P0-3　事务边界规则未固化（本次 11 处修复的同类问题还会复发）

**证据**：本次 500 告警的根因是"事务内 catch 吞掉 DB 异常 → 方法正常返回 → 提交时抛 UnexpectedRollbackException"，共修了 11 处（UsageQuotaService、AgentConversationService、GrowthService、AgentMemoryStore、FamilyService、DeadRecipeImageCleaner 等）。

**建议**：把规则固化为团队（你自己）的代码规范，避免下次写新 Service 时重蹈：
1. **`@Transactional` 方法内禁止 catch 数据库异常后正常返回**——要降级就把降级逻辑放在无事务的外层，写库操作拆成 `REQUIRES_NEW` 的独立方法（通过自注入代理调用，`@Lazy private XxxService self;`）；
2. 事务内禁止 `Thread.sleep` 重试（锁等待会一直占着连接/锁）；
3. 唯一键冲突的幂等处理：冲突后**重查**返回，而不是 catch 后返回新对象；
4. JSON 序列化等 checked 异常若要触发回滚，包装成 `RuntimeException`（Spring 默认不回滚 checked 异常）；
5. 可选：加一条 ArchUnit 测试或代码评审 checklist 守住这几条。
**工作量**：规范 1 小时；ArchUnit 可选 0.5 天。

---

### P1 — 1-2 周内处理（架构演进）

#### P1-1　三个 Agent 上帝类，职责严重混杂

**证据**：
- `GuozaiAgent`（1047 行，且放在 service 包而非 agent 包）：同时负责推荐、周计划（3 个重载）、月度信、心情洞察、评分（preferenceScore/weeklyPlanScore/recommendationMemoryScore）、文案、安全审核（contentSafe/blockedTerms）、进度上报——**至少 7 类职责**。
- `MenuPlannerAgent`（1122 行）：规划生成、批判、证据搜索、修复、质量门、DTP 提示词全在一个类。
- `DialogueAgent`（975 行）：编排、冰箱问答、事实合并、缺口/冲突分析、文本清洗、医疗拦截。

**建议**：渐进拆分（不用一次到位，按改动热度拆）：
- `GuozaiAgent` → `RecommendationService`（推荐编排）、`RecipeScorer`（三个评分方法收口）、`RecommendationInsightBuilder`（文案/洞察）、`MonthlyLetterService`（月信）；安全审核抽成共享 `ContentSafetyService`（DialogueAgent 里也有一份，目前重复）。
- `DialogueAgent` → `OrchestrationService`（LLM 编排）、`FridgeDialogueHandler`（冰箱问答）、`ReplyPolicy`（sanitize/星期/医疗拦截）。
- `MenuPlannerAgent` → 保留生成主流程，把批判解析（parseCritique）、证据检索、DTP 提示词拆成独立类。
- 拆分时配套补单测（现有 21 个 Agent 测试是安全网）。
**工作量**：每个大类 1-2 天，建议结合日常需求迭代拆分，不做纯重构冲刺。

#### P1-2　前端 30 个页面全在主包，只开了优化开关没有真分包

**证据**：`manifest.config.ts` 只开了 `optimization.subPackages: true`（这是"分包优化"开关，不是分包定义），`pages.config.ts` 无 `subPackages`，页面里也没有 route block 分包标记。当前主包 **1.34 MB**（限制 2 MB，已用 67%），随功能继续增加会超限。

**建议**：把低频、重页面挪成分包，主包只保留 tab 三页（index/record/profile）+ 核心推荐链路：
- 分包 A「内容」：album、report、timeline、gallery、calendar；
- 分包 B「规划」：weekly-plan、daily-meal-plan、meal-agent、cooking；
- 分包 C「设置」：feedback、family、membership、fridge 等。
**工作量**：0.5-1 天，注意 tabBar 页面必须在主包。

#### P1-3　胖页面：index 1519 行、recipe 1300 行、meal-agent 1251 行

**建议**：继续沿用你已经在做的 composables（已有 11 个）方向，把大页面拆成"页面（数据组装）+ 区块组件 + composable（逻辑）"。优先级 recipe > meal-agent > index（recipe 是核心转化页，维护频率最高）。
**工作量**：每页 1-2 天，随需求迭代。

#### P1-4　前端零线上错误监控

**证据**：`package.json` 没有 Sentry / fundebug / aegis 等任何监控依赖；后端有 WxPusher 500 告警，但前端白屏、JS 报错、接口失败用户只会流失，你完全无感知。

**建议**（从轻到重）：
1. 最轻：App.vue 加 `app.onError` + `uni.onUnhandledRejection`，POST 到后端新增的 `/api/client-errors`（可复用 WxPusher 告警，去重）；
2. 正式：接入微信官方「小程序运维中心 / 错误日志」（免费），或 fundebug 类服务。
**工作量**：方案 1 约 0.5 天。

#### P1-5　service 包 83 个类平铺，无领域分包

**证据**：`service/` 下 83 个类平铺，commerce、memory、cooking、family、fridge 等领域混在一起；GuozaiAgent 还混在 service 而非 agent 包。

**建议**：按业务域拆包（`service.recommendation` / `service.cooking` / `service.family` / `service.commerce` / `service.fridge` / `service.job` 等），纯移动 + 改 package 声明，IDE 一键完成，风险低。
**工作量**：0.5 天。

---

### P2 — 优化打磨（有空再做）

| # | 问题 | 建议 |
|---|---|---|
| P2-1 | LLM 客户端**无熔断器**：供应商持续 5xx 时每个请求仍完整尝试 2 次+退避，高并发下拖慢用户链路（有 Semaphore 缓解但未根治） | 加简单熔断器：连续 5 次失败 open 30-60s，open 期间直接走本地降级；半开探测 |
| P2-2 | `LlmUsageCounter`、AgentOutputGate 违规台账均为内存计数，重启清零，成本/会员对账无历史 | 若要做成本归因，每日落库一张 llm_usage_daily 表；或接每日 metrics 摘要 |
| P2-3 | 单实例内存态：推荐/周计划任务表、限流、Caffeine、LlmUsageCounter、虚拟支付对账都绑单机内存 | 当前 DAU 规模完全够用；**在代码注释/部署文档里明确"单实例约束"**，扩容时优先迁 Redis（任务表→Redis/DB、限流→Redis、配额→DB） |
| P2-4 | DeepSeek 模型名 `deepseek-v4-pro` / `deepseek-flash` 疑似占位名（DeepSeek 官方当前模型名是 deepseek-chat / deepseek-reasoner 一类） | 开通会员前务必用真实模型名核对，否则会员请求会 404 静默回落免费层 |
| P2-5 | `TieredLlmClient` 有两个冗余构造函数（一个 @Autowired 一个默认 new AgnesLlmClient） | 默认构造标 `@VisibleForTesting` 或删除，避免线上误用默认实例 |
| P2-6 | `store/`（themeStore 等 3 个）与 `stores/`（user.ts）两个目录并存 | 统一为 `stores/` |
| P2-7 | 项目根目录杂物多（zip/csv/xlsx/临时素材与代码混放） | 收纳到 `scripts/data/` 或 `archive/` |
| P2-8 | `spring.jpa.open-in-view` 默认开启（启动有警告），请求渲染期可能触发懒加载、拖慢响应 | 设为 false，事务边界显式收口到 Service |
| P2-9 | 告警只有 WxPusher 一个通道，且只在 500 时触发 | 可加每日健康摘要（任务成功率、LLM 降级率、接口 5xx 数），便于发现"没报错但一直在降级"的问题 |

---

## 四、做得好的地方（建议保持，不要重构破坏）

1. **AgentLoop 双通道**：供应商支持 function calling 走原生，400 不支持时同轮降级 JSON 协议，不浪费一次尝试；maxSteps 钳制、工具结果预算截断。
2. **工具幂等策略**：只读工具失败重试 1 次，写工具（记事实等）不重试——防止重复入库，这个细节很多团队会踩坑。
3. **层层降级**：DialogueAgent/GuozaiAgent/MenuPlannerAgent 每一层失败都有 degraded 记录与本地兜底，用户永远拿到结果，且降级原因可观测（`degradedReasons` 透传给前端）。
4. **记忆系统的安全与合规设计**：安全类 key（过敏原）禁止 INFERRED 写入；个性化开关；记忆可查看/删除/清空（`/api/agent/memory`）；value 200 字收口截断防 Data too long。
5. **输出护栏 + 自适应纪律**：OutputGate 违规 3 次后自动把纪律条目注入提示词，是个聪明的轻量对齐机制。
6. **餐后结果回流**：做了/剩了/太难/餐后心情 → 菜系亲和、难度上限、避菜、问题跳过，形成了真实的"越用越懂你"闭环。
7. **虚拟线程异步任务**：推荐/周计划用 `newVirtualThreadPerTaskExecutor` + 进度轮询，前端能看到"读取记忆→生成菜谱→制作封面"各阶段，且有进行中任务去重（避免轮询重复扣次）。
8. **安全加固齐全**：密钥全部环境变量化、错误响应不泄露堆栈、限流、安全头、上传大小限制、非法参数不回显（防枚举）。
9. **前端请求层成熟**：生产环境内网地址白名单拦截、ApiError 错误码（不靠文案匹配）、上传并发 401 连锁防护、超时分级、环境隔离。
10. **测试纪律**：Live 测试用 `AGENT_LIVE_TEST` 环境变量门禁，不会在本地/CI 误调付费 API。

---

## 五、建议的演进路线（按周）

- **本周（P0）**：① 配 `ThreadPoolTaskScheduler`（半天）；② 补 user_profile_snapshots 迁移 + 接入 Flyway 基线（1 天）；③ 把事务 4 条规则写进项目 `AGENTS.md`。
- **第 2 周（P1 起步）**：前端错误上报到 `/api/client-errors`；前端真分包（先把 album/report/timeline/gallery 分出去，主包降到 1MB 以内）。
- **第 3-4 周**：GuozaiAgent 先拆 RecipeScorer + ContentSafetyService（这两块被多个类复用，收益最高）；service 包按领域分包。
- **之后**：LLM 熔断器；胖页面随业务迭代拆；单实例边界文档化；模型名核对。

---

## 六、单实例边界（扩容前必须处理，先记录不紧急）

当前内存态组件（均不支持多实例水平扩展）：
- `RecommendationJobService` / `WeeklyPlanJobService`：内存任务表（轮询打到另一台会 404）；
- `RateLimitFilter`：内存限流（多台机器限流阈值翻倍）；
- Caffeine 缓存、`LlmUsageCounter`、AgentOutputGate 台账；
- 虚拟支付对账任务（多实例重复对账）。

扩容路线：任务表/限流/配额 → Redis；定时任务 → ShedLock 或 XXL-Job；缓存 → Redis 或保持本地+短 TTL。当前 DAU 规模下一台机器完全够用，不必提前建设，但部署文档里要写明。
