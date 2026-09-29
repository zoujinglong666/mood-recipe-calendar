## Why

锅仔目前有两套并行记忆：`GuozaiMemory` 从记录、偏好和运营事件即时聚合，`AgentMemoryStore` 保存带来源、证据和置信度的事实。首页寄语、单菜推荐、周计划和智能体分别读取不同结构，用户在一个入口表达的偏好不能稳定影响其他入口，也无法在记忆页面完整解释“从哪里学到”。

## What Changes

- 将 `AgentMemoryStore` 定为锅仔饮食档案的唯一事实层，保留现有偏好表、记录表和行为表作为原始证据。
- 为记忆事实增加分类：主动偏好、行为反馈、用餐场景、短期状态、安全限制。
- 统一记忆读取快照，使首页寄语、单菜推荐、周计划、一日三餐和智能体使用相同档案。
- 统一写入规则：明确选择高可信、行为学习需累计证据、短期状态允许过期、安全限制不得由模型推断。
- 记忆接口返回分类、来源、证据、可信度和更新时间，继续支持单条删除、全部清除和关闭个性化。
- 兼容已有 `agent_memory_facts`、`user_food_preferences` 与历史记录，不删除用户数据。

## Capabilities

### New Capabilities

- `unified-food-profile`: 跨推荐入口共享、可解释且用户可控的锅仔饮食档案。

### Modified Capabilities

- `agent-memory-control`: 记忆列表增加分类与统一来源说明。
- `favorite-cuisine-memory`: 菜系偏好进入统一档案快照。

## Impact

- 后端：`AgentMemoryFact`、`AgentMemoryStore`、`GuozaiMemory`、推荐与计划服务。
- 前端：锅仔记忆页的分类展示、来源解释和删除操作。
- 数据：向现有事实表增加可空分类字段并回填，不改变现有主键。
- 验收：任一入口写入的有效记忆能被其他入口读取；关闭个性化后所有入口停止读取和学习。
