# Intent Brief v1: 微信话题每日发布

- 录制：`local-rec-1790956186556`
- 语义推理：`deterministic_submission_evidence`
- 主意图类型：`form_submission`
- 置信度：high（0.93）

## 主意图

微信话题每日发布

## 证据

- 连续查看/点击了 3 个不同对象：label, file, 发布。
- 业务页面出现可回溯的字段标签、卡片字段或详情入口。
- 录制操作了 3 个不同元素，但点击后页面证据不足以证明它们属于需要批量提取的同类条目。
- 关键步骤视觉证据覆盖率为 100%。
- 最后一个业务输入后记录到 `click`，优先判定为提交/发送类操作。

## 候选解释

| 排名 | 类型 | 置信度 | 解释 |
|---:|---|---|---|
| 1 | `form_submission` | high (0.93) | 微信话题每日发布 |
| 2 | `information_extraction` | low (0.18) | 读取提交前后页面中的可见信息 |

## 推断输入

- `input_text_1` (string)：示例 `"今天也要好好吃饭"`，来源 `recorded_submitted_input`，置信度 `high`，建议开放为参数。
- `input_text_2` (string)：示例 `"C:\\fakepath\\topic-bg-4x3.png"`，来源 `recorded_submitted_input`，置信度 `high`，建议开放为参数。
- `input_text_3` (string)：示例 `"选一个心情，跟锅仔做道菜，拍下今日一餐。坚持记录，月底自动生成你的专属画册！"`，来源 `recorded_submitted_input`，置信度 `high`，建议开放为参数。
- `input_text_4` (string)：示例 `"记录今日伙食"`，来源 `recorded_submitted_input`，置信度 `high`，建议开放为参数。

## 推断输出

| 字段 | 类型 | 必需 | 置信度 | 含义 |
|---|---|---:|---|---|
| `success` | boolean | 是 | medium | 录制的提交或发送动作已完成 |
| `validated` | boolean | 是 | medium | 已验证提交后的业务可见状态 |

## 待确认问题

- 暂无来自意图推理层的产品边界问题。
