# AGENTS.md — 项目编码规范（AI 与人均须遵守）

> [2026-10-26] 依据架构评审（`docs/architecture-review-2026-10-26.md`）固化。
> 前端规范见 `frontend/AGENTS.md`。

## 后端事务边界 4 条铁律（P0-3）

背景：本次 500 告警根因是「事务内 catch 吞掉 DB 异常 → 方法正常返回 → 提交时抛
`UnexpectedRollbackException`」，同类问题已修 11 处。以下规则为防复发而固化：

1. **`@Transactional` 方法内禁止 catch 数据库异常后正常返回。**
   要降级，就把降级逻辑放在无事务的外层；写库操作拆成 `REQUIRES_NEW` 的独立方法，
   并通过自注入代理调用（`@Lazy private XxxService self;`），避免同类内调用代理失效。
2. **事务内禁止 `Thread.sleep` 重试。** 锁等待期间会一直占用连接与行锁。
3. **唯一键冲突的幂等处理：冲突后重查返回既有记录**，而不是 catch 后返回新对象。
4. **JSON 序列化等 checked 异常若需触发回滚，包装成 `RuntimeException`**
   （Spring 默认只对 unchecked 异常回滚）。

守住方式：代码评审 checklist 逐条对照；新增 Service 的写路径必查第 1 条。
（可选加固：ArchUnit 测试禁 `Thread.sleep` 出现在事务方法内。）

## 数据库 Schema 变更（P0-2，2026-10-26 起）

- 所有 schema 变更**只**写 `backend/src/main/resources/db/migration/V{date}.{seq}__{desc}.sql`，
  由 Flyway 启动时自动执行（幂等写法：`IF NOT EXISTS` / `IF EXISTS`）。
- `backend/sql/migrations/`（27 个手工 SQL）已**冻结存档**，只读不改。
- 迁移 SQL 里的列名若撞 MySQL 保留字（如 `trigger`、`order`、`group`），必须加反引号。
- `ddl-auto` 过渡期仍为 `update`，目标库跑通 Flyway 后改为 `validate`
  （环境变量 `JPA_DDL_AUTO=validate`）。

## 定时任务（P0-1，2026-10-26 起）

- 调度已改为多线程 `ThreadPoolTaskScheduler`（`SchedulingConfig`，默认 6 线程，
  线程名 `sched-`，可用 `app.scheduling.pool-size` 调整）。
- 新增 `@Scheduled` 任务时注意：长任务（LLM/批量）与短清理任务可并行，但单个任务
  内部仍需自防重入；不要依赖「任务串行」这一旧假设。

## 单实例边界（评审 P2-3，扩容前必须处理）

以下组件均为单机内存态，多实例部署会失效：`RecommendationJobService` /
`WeeklyPlanJobService` 内存任务表、`RateLimitFilter`、Caffeine 缓存、
`LlmUsageCounter`、AgentOutputGate 台账、虚拟支付对账任务。
当前规模单实例够用；扩容路线：任务表/限流/配额 → Redis，定时任务 → ShedLock。
