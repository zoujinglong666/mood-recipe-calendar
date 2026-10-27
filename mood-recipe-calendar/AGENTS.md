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

## 限流与会话（P0 设计红线，2026-10-27 事故后固化）

背景：会话过期 + 前端 900ms 轮询曾把出口 IP 刷进 401 封禁（13.5 秒凑满 15 次），
校园/公司 WiFi 下全体用户连坐 10 分钟——P0 级体验事故。以下为不可回退的红线：

1. **正常用户永远不应看到 429，任何 IP 永不被完全锁死（不封禁）。**
   限流拦截次数进每日健康摘要（红线=0），非 0 即误伤，必须查限流配置而不是让用户"稍后再试"。
2. **防爆破只做渐进限速**：/auth/ 路径每 401 一次扣减该 IP 登录配额（下限 6/分钟），
   60 秒无失败自然恢复、无需解封。禁止重新引入"封 IP N 分钟"这类硬锁。
3. **业务接口的 401（会话过期）永不参与任何惩罚**——它是恢复流程的正常伴生物。
4. **幂等高频 GET（任务状态轮询类）走独立桶**，不挤占用户普通预算。
5. **会话过期必须静默恢复**：request 层 401 → 静默 wx.login 重登 → 原请求重试一次；
   只有重登失败（真断网）才跳登录页。禁止让轮询带着过期 token 连击。
6. **登录接口按 IP 的配额必须容纳 NAT 场景**（多人共享 WiFi 同时冷启动），
   爆破防护靠渐进限速，不靠压低成功登录配额。
7. **XFF 只信可信代理网段**（`security.ratelimit.trusted-proxies`，默认内网段+回环；
   生产收紧为 nginx 实际回源地址）：对端不可信时一律用 remoteAddr，伪造 XFF 不生效。
8. **未验证的 token 不是限流身份**：鉴权/管理路径一律按 IP；业务路径 token 分桶但有
   单 IP 桶数上限（64，超限塌缩回 IP 桶）+ 单 IP 总吞吐硬顶（general×4，轮询除外——
   伪造 token 的轮询在会话校验即 401，成本极低）。新增限流维度前先想伪造成本。

## 日志与告警通道（2026-10-27 起）

ERROR 级日志经 `WxPusherErrorAppender`（挂 root logger）自动推 WxPusher：10 分钟窗口聚合、
同指纹计数、排除已有专属通道的 logger（GlobalExceptionHandler/限流/WxPusherNotifier）。
新增告警通道前先看这个排除清单——**一个故障只允许一条告警路径**，否则就是双重推送。
WARN 不推送（LLM 超时等属正常噪音），只有 ERROR 才值得半夜叫人。

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
