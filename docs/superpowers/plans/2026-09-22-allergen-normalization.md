# 过敏原大模型归一化 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 保存饮食偏好时用大模型把自然语言忌口转为标准拦截词，并由推荐服务确定性执行过滤。

**Architecture:** 新增一个小型归一化服务，复用 `LlmClient` 的 JSON 模式。原始偏好与模型结果分别保存；`GuozaiAgent` 合并两者作为共享过滤词，不在推荐链路新增模型调用。

**Tech Stack:** Spring Boot 4、JPA、MySQL 8、Jackson、JUnit 5、Mockito。

## Global Constraints

- 不向模型发送 openid、日记或菜谱正文。
- 模型失败不能削弱已有过敏过滤。
- 不增加依赖，不改前端交互。
- 每个模型词最多 24 字，最多保存 32 个。

---

### Task 1: 保存模型标准词

**Files:**
- Modify: `mood-recipe-calendar/backend/src/main/java/com/moodrecipe/backend/entity/UserFoodPreference.java`
- Modify: `mood-recipe-calendar/backend/src/main/java/com/moodrecipe/backend/controller/UserFoodPreferenceController.java`
- Create: `mood-recipe-calendar/backend/src/main/java/com/moodrecipe/backend/service/AllergenNormalizationService.java`
- Test: `mood-recipe-calendar/backend/src/test/java/com/moodrecipe/backend/service/AllergenNormalizationServiceTest.java`

**Interfaces:**
- Produces: `List<String> AllergenNormalizationService.normalize(String avoidIngredients, String allergens)`.
- Produces: `UserFoodPreference.normalizedBlockedTerms` as a JSON string.

- [x] **Step 1: Write failing service tests**

```java
assertEquals(List.of("虾", "虾仁"), service.normalize("", "对虾过敏"));
assertEquals(List.of("花生"), service.normalize("", "花生过敏"));
```

- [x] **Step 2: Run the service tests and verify they fail**

Run: `./mvnw -q -Dtest=AllergenNormalizationServiceTest test`

- [x] **Step 3: Implement JSON-only model normalization with local fallback**

```java
public List<String> normalize(String avoidIngredients, String allergens) {
    List<String> local = localTerms(avoidIngredients, allergens);
    return merge(local, modelTerms(avoidIngredients, allergens));
}
```

- [x] **Step 4: Persist the JSON list while saving preferences**

```java
preference.setNormalizedBlockedTerms(json.writeValueAsString(normalizer.normalize(
        preference.getAvoidIngredients(), preference.getAllergens())));
```

- [x] **Step 5: Run tests and verify they pass**

Run: `./mvnw -q -Dtest=AllergenNormalizationServiceTest test`

### Task 2: 将模型词接入共享硬过滤

**Files:**
- Modify: `mood-recipe-calendar/backend/src/main/java/com/moodrecipe/backend/service/GuozaiAgent.java`
- Modify: `mood-recipe-calendar/backend/src/test/java/com/moodrecipe/backend/service/GuozaiAgentTest.java`

**Interfaces:**
- Consumes: `UserFoodPreference.getNormalizedBlockedTerms()`.
- Produces: 所有推荐路径拒绝命中模型标准词的菜谱。

- [x] **Step 1: Write failing regression test**

```java
preference.setNormalizedBlockedTerms("[\\\"虾仁\\\"]");
assertEquals("番茄炒蛋", agent.recommend("user-1", "平静", null).getName());
```

- [x] **Step 2: Run test and verify it fails**

Run: `./mvnw -q -Dtest=GuozaiAgentTest test`

- [x] **Step 3: Parse valid model terms and merge them into `allowedByPreference`**

```java
blocked.addAll(normalizedTerms(preference.getNormalizedBlockedTerms()));
return blocked.stream().noneMatch(text::contains);
```

- [x] **Step 4: Run targeted backend tests**

Run: `./mvnw -q test`

### Task 3: Verify deployment schema and behavior

**Files:**
- Modify: `mood-recipe-calendar/backend/sql/init.sql`

- [x] **Step 1: Add the `normalized_blocked_terms` column to fresh database schema**

```sql
normalized_blocked_terms TEXT COMMENT '大模型归一化后的忌口与过敏拦截词 JSON'
```

- [x] **Step 2: Verify fresh schema and JPA column names agree**

Run: `rg -n "normalized_blocked_terms|normalizedBlockedTerms" backend/src backend/sql/init.sql`

- [x] **Step 3: Run complete backend tests**

Run: `./mvnw -q test`
