# 锅仔可视化学习闭环 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 让用户完成“推荐 → 跟做 → 上传自己的照片记录 → 反馈 → 看见锅仔学到了什么 → 下一次推荐看见变化”的真实闭环。

**Architecture:** 复用现有 `RecipeInteraction`、`RecommendationExposureService` 和 `AgentMemoryStore`，新增一个小型 `RecordLearningService` 统一写回并返回学习回执；推荐结果通过 `Recipe` 的瞬态字段携带由真实档案命中生成的解释。前端只增加反馈选择器和洞察卡两个共享组件，记录页与推荐页共同复用。

**Tech Stack:** Java 21、Spring Boot、JUnit 5、Mockito、Vue 3、TypeScript、uni-app、SCSS、OpenSpec 1.9。

## Global Constraints

- 不新增数据库表和第三方依赖。
- 用户记录只接受用户上传的照片，不携带 AI 菜谱图片。
- `liked`、`tooHard`、`leftover` 全部可选，可多选，可全部跳过。
- 没有真实命中依据时不展示学习或推荐解释。
- 关闭个性化后保存记录，但不写入普通长期偏好。
- 记录成功优先；学习失败不得导致照片和记录回滚或要求重复提交。
- 同类按钮、反馈标签、洞察卡必须复用共享组件或全局设计变量。
- 每个后端行为先写失败测试；每个前端页面改动至少通过针对性 ESLint、类型检查和微信小程序构建。

---

## File Structure

**Create**

- `frontend/openspec/changes/add-visible-learning-loop/proposal.md`：变更动机和范围。
- `frontend/openspec/changes/add-visible-learning-loop/design.md`：数据流、降级与组件复用约束。
- `frontend/openspec/changes/add-visible-learning-loop/specs/visible-learning-loop/spec.md`：可验证需求。
- `frontend/openspec/changes/add-visible-learning-loop/tasks.md`：实施清单。
- `backend/src/main/java/com/moodrecipe/backend/service/RecordLearningService.java`：记录后的幂等学习写回与回执生成。
- `backend/src/test/java/com/moodrecipe/backend/service/RecordLearningServiceTest.java`：喜欢、太难、剩菜、关闭个性化和降级测试。
- `backend/src/main/java/com/moodrecipe/backend/model/RecommendationInsight.java`：推荐解释契约。
- `backend/src/main/java/com/moodrecipe/backend/service/RecommendationInsightService.java`：从真实使用档案生成最多两条解释。
- `backend/src/test/java/com/moodrecipe/backend/service/RecommendationInsightServiceTest.java`：真实命中、空档案和删除后不引用测试。
- `frontend/src/components/guozai/GuozaiChoiceChips.vue`：统一多选反馈组件。
- `frontend/src/components/guozai/GuozaiInsightCard.vue`：统一学习/推荐洞察卡。

**Modify**

- `backend/src/main/java/com/moodrecipe/backend/model/RecordRequest.java`：增加三个可选反馈字段。
- `backend/src/main/java/com/moodrecipe/backend/controller/RecordController.java`：返回记录与学习回执的组合响应。
- `backend/src/test/java/com/moodrecipe/backend/controller/RecordControllerTest.java`：锁住保存、重复请求和学习降级契约。
- `backend/src/main/java/com/moodrecipe/backend/entity/Recipe.java`：增加瞬态 `recommendationInsights`。
- `backend/src/main/java/com/moodrecipe/backend/service/GuozaiMemory.java`：将统一档案中的简单做法/时长限制放入推荐快照。
- `backend/src/main/java/com/moodrecipe/backend/service/GuozaiAgent.java`：让学习结果参与 AI 提示、本地候选排序并附加解释。
- `backend/src/test/java/com/moodrecipe/backend/service/GuozaiMemoryTest.java`：验证难度反馈进入快照。
- `backend/src/test/java/com/moodrecipe/backend/service/GuozaiAgentTest.java`：验证简单做法信号改变推荐并产生解释。
- `frontend/src/api/records.ts`：请求反馈字段并解析组合响应。
- `frontend/src/api/recipes.ts`：增加推荐解释类型。
- `frontend/src/components/guozai/SuccessModal.vue`：以插槽和统一主次按钮承载学习回执。
- `frontend/src/pages/record/index.vue`：采集反馈并展示真实学习回执。
- `frontend/src/pages/recipe/index.vue`：展示“锅仔为什么选它”。

---

### Task 1: 建立 OpenSpec 变更

**Files:**

- Create: `frontend/openspec/changes/add-visible-learning-loop/proposal.md`
- Create: `frontend/openspec/changes/add-visible-learning-loop/design.md`
- Create: `frontend/openspec/changes/add-visible-learning-loop/specs/visible-learning-loop/spec.md`
- Create: `frontend/openspec/changes/add-visible-learning-loop/tasks.md`

**Interfaces:**

- Consumes: `docs/superpowers/specs/2026-09-29-visible-learning-loop-design.md`
- Produces: OpenSpec change `add-visible-learning-loop`

- [ ] **Step 1: 写 proposal**

```markdown
# Change: Add visible learning loop

## Why
用户行为已经写回，但记录成功和下一次推荐没有显示真实学习结果，闭环不可见。

## What Changes
- 记录请求增加喜欢、太难、有剩菜三个可选反馈。
- 记录响应增加学习回执。
- 推荐响应增加基于真实档案命中的解释。
- 记录页和推荐页复用统一反馈与洞察组件。
```

- [ ] **Step 2: 写 design、spec 和 tasks**

`design.md` 明确以下决定：

```markdown
## Decisions
- 复用 RecipeInteraction、RecommendationExposureService 和 AgentMemoryStore，不新增事件表。
- 记录先保存；学习逐项降级并返回实际成功项。
- Recipe 使用瞬态 recommendationInsights，不持久化展示文案。
- 前端只增加 GuozaiChoiceChips 和 GuozaiInsightCard 两个共享组件。
```

`spec.md` 写入：

```markdown
### Requirement: 记录后返回真实学习回执
系统 SHALL 在记录保存成功后返回本次实际写入的学习结果；学习失败 SHALL NOT 回滚记录。

#### Scenario: 用户反馈太难
- **WHEN** 用户保存记录并选择“做起来太难”
- **THEN** 记录保存成功，统一档案写入简单做法偏好，响应包含对应学习回执

### Requirement: 下一次推荐解释真实变化
系统 SHALL 只展示本次推荐实际使用的普通偏好或行为信号。

#### Scenario: 没有可用依据
- **WHEN** 本次推荐没有使用任何可解释普通记忆
- **THEN** 响应不返回解释，前端不渲染空卡片
```

`tasks.md` 按本计划列出六组任务：OpenSpec、记录学习回执、推荐解释、共享组件、页面接通、全链路验证，初始均为 `[ ]`。

- [ ] **Step 3: 严格校验**

Run: `cd frontend && pnpm exec openspec validate add-visible-learning-loop --strict`

Expected: `Change 'add-visible-learning-loop' is valid`

- [ ] **Step 4: 提交规范**

```bash
git add frontend/openspec/changes/add-visible-learning-loop
git commit -m "spec: add visible learning loop"
```

---

### Task 2: 实现记录学习回执

**Files:**

- Create: `backend/src/main/java/com/moodrecipe/backend/service/RecordLearningService.java`
- Create: `backend/src/test/java/com/moodrecipe/backend/service/RecordLearningServiceTest.java`
- Modify: `backend/src/main/java/com/moodrecipe/backend/model/RecordRequest.java`
- Modify: `backend/src/main/java/com/moodrecipe/backend/controller/RecordController.java`
- Modify: `backend/src/test/java/com/moodrecipe/backend/controller/RecordControllerTest.java`

**Interfaces:**

- Consumes: `AgentMemoryStore.remember(...)`, `RecommendationExposureService.feedback(...)`, `RecipeInteractionRepository`
- Produces: `RecordLearningService.LearningInput`, `RecordLearningService.LearningReceipt`, `RecordController.SaveResult`

- [ ] **Step 1: 写失败测试——三种反馈只返回真实成功项**

```java
@Test
void returnsOnlyLearningItemsThatWereWritten() {
    when(memory.personalizationEnabled("u")).thenReturn(true);
    LearningReceipt receipt = service.learn("u", record(7L, "番茄炒蛋", "11", "exp-1"),
            new LearningInput(true, true, true));

    assertEquals(LearningStatus.LEARNED, receipt.status());
    assertEquals(List.of("以后多推荐这类菜", "下次优先更简单的步骤", "下次会控制分量"), receipt.items());
    verify(interactions).save(argThat(item -> "LIKE".equals(item.getAction())));
    verify(memory).remember(argThat(command -> AgentMemoryStore.KEY_SIMPLE.equals(command.key())));
    verify(memory).remember(argThat(command -> "preference.avoidLeftover".equals(command.key())));
}
```

- [ ] **Step 2: 运行并确认因类不存在而失败**

Run: `cd backend && ./mvnw -Dtest=RecordLearningServiceTest test`

Expected: FAIL，`RecordLearningService` 未定义。

- [ ] **Step 3: 最小实现学习服务**

```java
@Service
public class RecordLearningService {
    public enum LearningStatus { LEARNED, SAVED_ONLY }
    public record LearningInput(Boolean liked, Boolean tooHard, Boolean leftover) { }
    public record LearningReceipt(LearningStatus status, String title, List<String> items) {
        static LearningReceipt savedOnly() {
            return new LearningReceipt(LearningStatus.SAVED_ONLY, "记录已保存，锅仔稍后再整理", List.of());
        }
    }

    public LearningReceipt learn(String openid, UserRecord record, LearningInput input) {
        if (!memory.personalizationEnabled(openid)) return LearningReceipt.savedOnly();
        List<String> items = new ArrayList<>();
        rememberMade(openid, record);
        if (Boolean.TRUE.equals(input.liked()) && rememberLike(openid, record)) items.add("以后多推荐这类菜");
        if (Boolean.TRUE.equals(input.tooHard()) && rememberTooHard(openid, record)) items.add("下次优先更简单的步骤");
        if (Boolean.TRUE.equals(input.leftover()) && rememberLeftover(openid, record)) items.add("下次会控制分量");
        return new LearningReceipt(LearningStatus.LEARNED, "锅仔记住这顿了", List.copyOf(items));
    }
}
```

每个 `remember*` 内部捕获自己的 `RuntimeException`，返回 `false`；`rememberMade` 使用已有 `MADE` 去重和曝光反馈，`rememberLike` 使用已有 `LIKE` 去重，难度与剩菜通过 `AgentMemoryStore.RememberCommand.learned(...)` 写入 `KEY_SIMPLE`、`KEY_MAX_MINUTES` 和 `preference.avoidLeftover`。

- [ ] **Step 4: 运行学习服务测试**

Run: `cd backend && ./mvnw -Dtest=RecordLearningServiceTest test`

Expected: PASS。

- [ ] **Step 5: 写失败测试——记录保存返回组合结果且重复请求不重复学习**

```java
@Test
void savesRecordAndReturnsLearningReceipt() {
    when(records.save(any())).thenAnswer(call -> { UserRecord row = call.getArgument(0); row.setId(9L); return row; });
    when(learning.learn(eq("user-1"), any(), any())).thenReturn(new LearningReceipt(
            LearningStatus.LEARNED, "锅仔记住这顿了", List.of("以后多推荐这类菜")));

    ApiResponse<RecordController.SaveResult> response = controller.save("user-1", request(true, false, false));

    assertEquals(9L, response.getData().record().getId());
    assertEquals(List.of("以后多推荐这类菜"), response.getData().learningReceipt().items());
}

@Test
void repeatedClientRequestDoesNotLearnTwice() {
    when(records.findByOpenidAndClientRequestId("user-1", "request-1")).thenReturn(Optional.of(existing));
    controller.save("user-1", request(true, true, true));
    verifyNoInteractions(learning);
}
```

- [ ] **Step 6: 扩展请求与控制器**

`RecordRequest` 追加：

```java
Boolean liked,
Boolean tooHard,
Boolean leftover
```

`RecordController` 返回：

```java
public record SaveResult(UserRecord record, RecordLearningService.LearningReceipt learningReceipt) { }
```

保存后调用：

```java
LearningReceipt receipt;
try {
    receipt = learning.learn(openid, saved, new LearningInput(req.liked(), req.tooHard(), req.leftover()));
} catch (RuntimeException ignored) {
    receipt = LearningReceipt.savedOnly();
}
return ApiResponse.ok(new SaveResult(saved, receipt));
```

- [ ] **Step 7: 运行记录相关测试**

Run: `cd backend && ./mvnw -Dtest=RecordLearningServiceTest,RecordControllerTest test`

Expected: PASS。

- [ ] **Step 8: 提交后端写回**

```bash
git add backend/src/main/java/com/moodrecipe/backend/model/RecordRequest.java backend/src/main/java/com/moodrecipe/backend/controller/RecordController.java backend/src/main/java/com/moodrecipe/backend/service/RecordLearningService.java backend/src/test/java/com/moodrecipe/backend/controller/RecordControllerTest.java backend/src/test/java/com/moodrecipe/backend/service/RecordLearningServiceTest.java
git commit -m "feat: return record learning receipts"
```

---

### Task 3: 让下一次推荐发生变化并返回解释

**Files:**

- Create: `backend/src/main/java/com/moodrecipe/backend/model/RecommendationInsight.java`
- Create: `backend/src/main/java/com/moodrecipe/backend/service/RecommendationInsightService.java`
- Create: `backend/src/test/java/com/moodrecipe/backend/service/RecommendationInsightServiceTest.java`
- Modify: `backend/src/main/java/com/moodrecipe/backend/entity/Recipe.java`
- Modify: `backend/src/main/java/com/moodrecipe/backend/service/GuozaiMemory.java`
- Modify: `backend/src/main/java/com/moodrecipe/backend/service/GuozaiAgent.java`
- Modify: `backend/src/test/java/com/moodrecipe/backend/service/GuozaiMemoryTest.java`
- Modify: `backend/src/test/java/com/moodrecipe/backend/service/GuozaiAgentTest.java`

**Interfaces:**

- Consumes: `AgentMemoryStore.profile(openid, SINGLE_RECIPE)` and the final selected `Recipe`
- Produces: `RecommendationInsight(Type type, String text, String memoryKey)` and `Recipe.recommendationInsights`

- [ ] **Step 1: 写失败测试——只解释真实匹配事实**

```java
@Test
void explainsSimpleRecipeOnlyWhenSimplePreferenceWasUsed() {
    when(memory.profile("u", AgentMemoryStore.Scene.SINGLE_RECIPE)).thenReturn(profileWithSimplePreference());
    Recipe recipe = recipe("番茄炒蛋", 20);

    List<RecommendationInsight> result = service.explain("u", recipe);

    assertEquals(1, result.size());
    assertEquals(RecommendationInsight.Type.CHANGE, result.get(0).type());
    assertEquals(AgentMemoryStore.KEY_SIMPLE, result.get(0).memoryKey());
}

@Test
void returnsNoExplanationForEmptyProfile() {
    when(memory.profile("u", AgentMemoryStore.Scene.SINGLE_RECIPE)).thenReturn(UserProfile.empty("u"));
    assertTrue(service.explain("u", recipe("番茄炒蛋", 20)).isEmpty());
}
```

- [ ] **Step 2: 运行并确认失败**

Run: `cd backend && ./mvnw -Dtest=RecommendationInsightServiceTest test`

Expected: FAIL，解释服务不存在。

- [ ] **Step 3: 实现解释契约与最多两条的确定性规则**

```java
public record RecommendationInsight(Type type, String text, String memoryKey) {
    public enum Type { MEMORY, CHANGE }
}
```

规则按顺序取两条：

1. `preferSimple` 且菜谱不超过 `maxCookingMinutes` 或 35 分钟：`CHANGE`。
2. 菜名命中 `lovedDishes`：`MEMORY`。
3. 菜谱文本命中 `favoriteCuisines`：`MEMORY`。

不得根据空字段、已关闭个性化的空档案或未命中的事实生成解释。

- [ ] **Step 4: 让统一档案真正改变单菜推荐**

在 `GuozaiMemory.MemorySnapshot` 增加：

```java
Integer maxCookingMinutes,
boolean preferSimple,
List<String> lovedDishes
```

`buildAnalysis` 把简单做法和时长上限写入 AI 提示。`GuozaiAgent.fallbackLocalRecipe` 在本地评分中对超过时长上限的菜扣分，对 35 分钟内的简单菜加分；最终选中后调用 `insights.explain(openid, recipe)` 并设置到瞬态字段。

- [ ] **Step 5: 扩展 Recipe 的瞬态响应字段**

```java
@Transient
private List<RecommendationInsight> recommendationInsights = List.of();
```

- [ ] **Step 6: 运行推荐相关测试**

Run: `cd backend && ./mvnw -Dtest=RecommendationInsightServiceTest,GuozaiMemoryTest,GuozaiAgentTest test`

Expected: PASS，并证明难度反馈使短时简单菜优先且响应包含 `CHANGE` 解释。

- [ ] **Step 7: 提交推荐解释**

```bash
git add backend/src/main/java/com/moodrecipe/backend/model/RecommendationInsight.java backend/src/main/java/com/moodrecipe/backend/entity/Recipe.java backend/src/main/java/com/moodrecipe/backend/service/RecommendationInsightService.java backend/src/main/java/com/moodrecipe/backend/service/GuozaiMemory.java backend/src/main/java/com/moodrecipe/backend/service/GuozaiAgent.java backend/src/test/java/com/moodrecipe/backend/service/RecommendationInsightServiceTest.java backend/src/test/java/com/moodrecipe/backend/service/GuozaiMemoryTest.java backend/src/test/java/com/moodrecipe/backend/service/GuozaiAgentTest.java
git commit -m "feat: explain learned recommendation changes"
```

---

### Task 4: 建立统一反馈和洞察组件

**Files:**

- Create: `frontend/src/components/guozai/GuozaiChoiceChips.vue`
- Create: `frontend/src/components/guozai/GuozaiInsightCard.vue`
- Modify: `frontend/src/components/guozai/SuccessModal.vue`

**Interfaces:**

- Produces: `GuozaiChoiceChips` with `v-model: string[]`; `GuozaiInsightCard` with `title`, `items`, `variant`; `SuccessModal` default slot and secondary action

- [ ] **Step 1: 实现统一多选组件**

```ts
interface Choice { value: string; label: string; hint?: string }
const props = defineProps<{ modelValue: string[]; choices: Choice[]; disabled?: boolean }>()
const emit = defineEmits<{ (event: 'update:modelValue', value: string[]): void }>()
function toggle(value: string) {
  if (props.disabled) return
  emit('update:modelValue', props.modelValue.includes(value)
    ? props.modelValue.filter(item => item !== value)
    : [...props.modelValue, value])
}
```

模板为同层级 chip 列表，选中态只使用 `--mrc-accent`、`--mrc-accent-soft` 和现有边框变量；每项设置 `role="checkbox"`、`:aria-checked` 和明确标签。

- [ ] **Step 2: 实现统一洞察卡**

```ts
interface Props {
  title: string
  items: string[]
  variant?: 'learned' | 'recommendation'
  actionText?: string
}
```

当 `items.length === 0` 时组件不渲染。两个 variant 只改变 eyebrow 文案和锅仔图片，不改变卡片布局、字体、间距与按钮。

- [ ] **Step 3: 扩展成功层而不复制按钮**

`SuccessModal` 增加：

```ts
secondaryText?: string
```

以及 `default` 插槽、`secondary` 事件。主次按钮共用 `.gz-modal__action`，仅用修饰类区分填充和描边。

- [ ] **Step 4: 针对性检查组件**

Run: `cd frontend && pnpm eslint src/components/guozai/GuozaiChoiceChips.vue src/components/guozai/GuozaiInsightCard.vue src/components/guozai/SuccessModal.vue`

Expected: 0 errors。

- [ ] **Step 5: 提交共享组件**

```bash
git add frontend/src/components/guozai/GuozaiChoiceChips.vue frontend/src/components/guozai/GuozaiInsightCard.vue frontend/src/components/guozai/SuccessModal.vue
git commit -m "feat: add unified guozai feedback components"
```

---

### Task 5: 接通记录页与推荐页

**Files:**

- Modify: `frontend/src/api/records.ts`
- Modify: `frontend/src/api/recipes.ts`
- Modify: `frontend/src/pages/record/index.vue`
- Modify: `frontend/src/pages/recipe/index.vue`

**Interfaces:**

- Consumes: `SaveRecordResult`, `RecommendationInsight[]`, `GuozaiChoiceChips`, `GuozaiInsightCard`
- Produces: 用户可见的记录回执与下一次推荐解释

- [ ] **Step 1: 更新前端 API 契约**

```ts
export interface LearningReceipt {
  status: 'LEARNED' | 'SAVED_ONLY'
  title: string
  items: string[]
}
export interface SaveRecordResult { record: RecordItem; learningReceipt: LearningReceipt }
```

`RecordPayload` 增加 `liked?`、`tooHard?`、`leftover?`。`saveRecord` 规范化 `result.record` 后保留回执。编辑接口继续返回单独 `RecordItem`，不提交新的学习信号。

`RecipeItem` 增加：

```ts
recommendationInsights?: { type: 'MEMORY' | 'CHANGE'; text: string; memoryKey?: string }[]
```

- [ ] **Step 2: 记录页采集三个可选反馈**

```ts
const feedback = ref<string[]>([])
const FEEDBACK_CHOICES = [
  { value: 'liked', label: '喜欢这道菜' },
  { value: 'tooHard', label: '做起来太难' },
  { value: 'leftover', label: '有剩菜' },
]
```

提交 payload 使用 `feedback.value.includes(...)`。只有新建记录显示反馈区，编辑记录不重复学习。

- [ ] **Step 3: 展示服务端学习回执**

保存成功后保存：

```ts
savedRecordId.value = saved.record.id
learningReceipt.value = saved.learningReceipt
```

`SuccessModal` 内嵌 `GuozaiInsightCard`；主操作进入本次记录详情，次操作回首页。`SAVED_ONLY` 时只显示降级标题，不伪造学习条目。

- [ ] **Step 4: 推荐页展示真实解释**

在推荐理由下、主操作前添加：

```vue
<GuozaiInsightCard
  v-if="recipe.recommendationInsights?.length"
  title="锅仔为什么选它"
  :items="recipe.recommendationInsights.map(item => item.text)"
  variant="recommendation"
  action-text="查看锅仔记忆"
  @action="router.push({ name: 'preferences' })"
/>
```

- [ ] **Step 5: 针对性检查页面**

Run: `cd frontend && pnpm eslint src/api/records.ts src/api/recipes.ts src/components/guozai/GuozaiChoiceChips.vue src/components/guozai/GuozaiInsightCard.vue src/components/guozai/SuccessModal.vue src/pages/record/index.vue src/pages/recipe/index.vue`

Expected: 0 errors。

- [ ] **Step 6: 类型检查与微信小程序构建**

Run: `cd frontend && pnpm type-check`

Expected: 不出现本次文件的新错误；若仍只有 `@wot-ui/ui/components/wd-img/wd-img.vue` TS2774，记录为既有依赖问题。

Run: `cd frontend && pnpm build:mp-weixin`

Expected: build exit code 0。

- [ ] **Step 7: 提交页面闭环**

```bash
git add frontend/src/api/records.ts frontend/src/api/recipes.ts frontend/src/components/guozai/SuccessModal.vue frontend/src/pages/record/index.vue frontend/src/pages/recipe/index.vue
git commit -m "feat: show guozai learning loop"
```

---

### Task 6: 全链路验证与 OpenSpec 收口

**Files:**

- Modify: `frontend/openspec/changes/add-visible-learning-loop/tasks.md`

**Interfaces:**

- Consumes: Tasks 1–5 全部实现
- Produces: 可验证、可归档的完整变更

- [ ] **Step 1: 运行全量后端测试**

Run: `cd backend && ./mvnw test`

Expected: 0 failures、0 errors。

- [ ] **Step 2: 运行前端相关检查**

Run: `cd frontend && pnpm eslint src/api/records.ts src/api/recipes.ts src/components/guozai/GuozaiChoiceChips.vue src/components/guozai/GuozaiInsightCard.vue src/components/guozai/SuccessModal.vue src/pages/record/index.vue src/pages/recipe/index.vue`

Expected: 0 errors。

- [ ] **Step 3: 严格校验 OpenSpec**

Run: `cd frontend && pnpm exec openspec validate add-visible-learning-loop --strict`

Expected: valid。

- [ ] **Step 4: 检查差异完整性**

Run: `git diff --check`

Expected: 无输出。

人工验收：从推荐页进入做菜；完成后记录页没有 AI 图片；上传自己的照片，选择“太难”并保存；成功层显示真实回执；下一次推荐优先短时简单菜并显示对应解释；关闭个性化后重复流程仅确认记录保存。

- [ ] **Step 5: 更新任务并提交验证结果**

将 `tasks.md` 的已完成项逐项标记为 `[x]`，只暂存本变更涉及的文件：

```bash
git add frontend/openspec/changes/add-visible-learning-loop/tasks.md
git commit -m "test: verify visible learning loop"
```
