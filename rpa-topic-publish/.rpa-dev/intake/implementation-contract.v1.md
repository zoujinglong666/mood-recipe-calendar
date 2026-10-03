# Implementation Contract v1

- User PRD: `user-prd.v1.md`
- Recording: `local-rec-1790956186556`
- Quality: `ready_for_prd`

## Runtime contract

- Project runtime: `local_chrome`.
- Business handler root: `src/local-rpa.js`.
- Development validation: local run twice + isolated exported Skill invocation.
- Development: named cloud Session with global proxy/profile.
- Deployed: task-supplied per-Tab proxy and explicit auth; never reuse Session state.
- Viewport baseline: 1920×1080; verify the recorded viewport, locale, and responsive drift before selector changes.
- Workflow class: `operation_submission`; side effects: `false`.

## Recording evidence index

- Raw events: `/Users/zou/WebstormProjects/mood-recipe-calendar/rpa-topic-publish/.rpa-dev/recordings/local-rec-1790956186556/events.ndjson`
- Normalized steps: `/Users/zou/WebstormProjects/mood-recipe-calendar/rpa-topic-publish/.rpa-dev/recordings/local-rec-1790956186556/steps.json`
- Chrome Recorder JSON: `/Users/zou/WebstormProjects/mood-recipe-calendar/rpa-topic-publish/.rpa-dev/recordings/local-rec-1790956186556/recording.json`
- Native Puppeteer reference: `/Users/zou/WebstormProjects/mood-recipe-calendar/rpa-topic-publish/.rpa-dev/recordings/local-rec-1790956186556/recording.puppeteer.js`
- Step evidence: `/Users/zou/WebstormProjects/mood-recipe-calendar/rpa-topic-publish/.rpa-dev/recordings/local-rec-1790956186556/step-evidence`
- Structure snapshots: `/Users/zou/WebstormProjects/mood-recipe-calendar/rpa-topic-publish/.rpa-dev/recordings/local-rec-1790956186556/structure-snapshots`
- Screenshots: `/Users/zou/WebstormProjects/mood-recipe-calendar/rpa-topic-publish/.rpa-dev/recordings/local-rec-1790956186556/screenshots`

## Quality gates

| Gate | Required | Passed | Detail |
|---|---:|---:|---|
| `normalized_business_steps` | True | True | 9 business steps after authentication stripping |
| `recording_gaps_absent` | True | True | 0 event-stream gaps |
| `step_evidence_capture_gaps_absent` | True | True | 0 step-evidence capture gaps |
| `meaningful_step_visual_coverage` | True | True | 9/9 meaningful steps have post-action evidence; required >=80% |
| `terminal_checkpoint` | True | True | final checkpoint contains page state and screenshot or validated Windows DOM fallback |
| `chrome_recorder_exports` | True | True | 10 Chrome Recorder steps |

## Input contract

| Name | Type | Required | Default | Example | Evidence steps |
|---|---|---:|---|---|---|
| `topic_name` | string | True | `None` | `今天也要好好吃饭` | 1 |
| `topic_bg_image` | string | True | `None` | `topic-bg-4x3.png` | 4 |
| `topic_description` | string | True | `None` | `选一个心情，跟锅仔做道菜，拍下今日一餐。坚持记录，月底自动生成你的专属画册！` | 5 |
| `link_title` | string | True | `None` | `记录今日伙食` | 6 |

## Output and completion contract

- Completion: 每个录制动作均达到对应业务后置状态，并返回页面身份、可见摘要和处理状态等可验证结果。
- Source: `confirmed_user_prd`.

| Name | Type | Required | Source | Invariant/meaning |
|---|---|---:|---|---|
| `items` | array | True | `confirmed_user_prd` | 每个选中条目的结构化处理结果集合 |
| `selected_item_label` | string | True | `confirmed_user_prd` | 触发页面处理的原始条目标题或控件名称 |
| `page_url` | string | True | `confirmed_user_prd` | 实际处理的业务页面 URL |
| `page_title` | string | True | `confirmed_user_prd` | 业务页面标题，用于识别处理对象和验证页面状态 |
| `page_summary` | string | False | `confirmed_user_prd` | 页面可见文本摘要，供用户确认处理内容；无稳定正文时允许为空 |
| `processing_status` | string | True | `confirmed_user_prd` | 该页面或条目是否完成录制所示的处理与语义校验 |

## Output evidence coverage

- Extraction evidence required: `true`.
- Output extraction ready: `false`.
- Missing required fields: `['items', 'selected_item_label', 'page_url', 'page_title', 'processing_status']`.

| Field | Required | Coverage | Candidate evidence |
|---|---:|---|---|
| `items` | True | `missing` |  |
| `selected_item_label` | True | `candidate` | semantic_landmark:a.header_logo; semantic_landmark:[data-doubao-translate-traverse-mark="\…eader_logo; semantic_landmark:[data-doubao-translate-traverse-mark="\31 "], a.header_logo |
| `page_url` | True | `candidate` | semantic_landmark:[data-doubao-translate-traverse-mark="\31 "], div.topic-form__label; sem…rm__label; semantic_landmark:[data-doubao-translate-traverse-mark="\31 "], div.topic-form |
| `page_title` | True | `candidate` | semantic_landmark:[data-doubao-translate-traverse-mark="\31 "], div.topic-form__label; sem…a-doubao-translate-traverse-mark="\31 "], div.topic-form__row.topic-form__row--wide-label |
| `page_summary` | False | `candidate` | semantic_landmark:[data-doubao-translate-traverse-mark="\31 "], div.topic-form__poi-desc; …a-doubao-translate-traverse-mark="\31 "], div.topic-form__row.topic-form__row--wide-label |
| `processing_status` | True | `missing` |  |

## Execution evidence

| Step | Intent | Action | Tab | Raw events | Postcondition | Screenshot | Risk |
|---:|---|---|---|---|---|---|---|
| 0 | 打开页面 `https://mp.weixin.qq.com/wxamp/appmsgtopic/create_topic_page?token=766765003&lang=zh_CN` | `navigate` | `tab-1` | 2 | `navigation_ready` | `screenshots/step-000002-document-tab-1.jpg` |  |
| 1 | 在 `今天也要好好吃饭` 中输入录制文本（input） | `change` | `tab-1` | 3 | `input_value_applied` | `screenshots/step-000003-change-tab-1.jpg` | selector_scoped_disambiguation_required, selector_semantic_identity_required |
| 2 | 点击 `录制目标控件` | `click` | `tab-1` | 4 | `recorded_page_state` | `screenshots/step-000004-click-tab-1.jpg` | scope_missing, selector_non_actionable_target |
| 3 | 点击 `file` | `click` | `tab-1` | 5 | `recorded_page_state` | `screenshots/step-000005-click-tab-1.jpg` | scope_missing |
| 4 | 在 `file` 中输入录制文本（input） | `change` | `tab-1` | 6 | `input_value_applied` | `screenshots/step-000006-change-tab-1.jpg` | scope_missing |
| 5 | 在 `选一个心情，跟锅仔做道菜，拍下今日一餐。坚持记录，月底自动生成你的专属画册！` 中输入录制文本（textarea） | `change` | `tab-1` | 7 | `input_value_applied` | `screenshots/step-000007-change-tab-1.jpg` |  |
| 6 | 在 `记录今日伙食` 中输入录制文本（input） | `change` | `tab-1` | 8, 9 | `input_value_applied` | `screenshots/step-000009-change-tab-1.jpg` | scope_missing, selector_scoped_disambiguation_required, selector_semantic_identity_required |
| 7 | 点击 `发布` | `click` | `tab-1` | 10 | `recorded_page_state` | `screenshots/step-000010-click-tab-1.jpg` |  |
| 8 | 点击 `发布` | `click` | `tab-1` | 11 | `recorded_page_state` | `screenshots/step-000011-click-tab-1.jpg` |  |

## Selector preflight

- Action replay ready: `true`.
- Overall implementation ready: `false`.
- Grades: stable=5, conditional=3, fragile=0, blocked=0.
- Blocked steps: `[]`.
- Fragile steps: `[]`.
- This is an evidence-state preflight. Runtime actions still revalidate uniqueness, visibility, and obstruction in sequence.

Selectors are recording evidence only. Do not copy FRAGILE selectors without review; never copy BLOCKED selectors into executable business code.

## Retry and side-effect policy

- bounded retry is allowed only for proven readiness and selection steps.
- Ambiguous result: return a classified verification error.

## Required implementation guards

- `semantic_locator_validation`
- `bounded_waits`
- `step_postconditions`
- `classified_errors`
- `resource_cleanup`

## Error contract

- `auth_required_or_expired`: authentication precondition is not satisfied; retryable=`false`.
- `target_not_found`: required business control or result is absent after bounded waits; retryable=`false`.
- `target_identity_mismatch`: selected/opened target does not satisfy the confirmed identity rule; retryable=`false`.
- `recorded_postcondition_failed`: an action completed but its semantic postcondition did not; retryable=`false`.
- `output_invariant_failed`: a required output is missing or invalid; retryable=`false`.

## Acceptance

- `development_run_twice` (required): two structured results and trace URLs in the same Session.
- `run_contract` (required): explicit deployed proxy/auth contract succeeds in the named Session.
- `trace_review` (required): every meaningful execution step has action and postcondition trace evidence.
- `output_invariants` (required): all required output and completion invariants pass.
- `deployed_curl_when_requested` (conditional): standalone deployed curl succeeds when deployment is in scope.
