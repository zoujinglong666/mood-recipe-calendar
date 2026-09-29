# Guideline-Informed Meal Planning Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a safe, traceable healthy-adult daily meal-planning foundation using versioned nutrition knowledge packs and seasonal ingredient data.

**Architecture:** The backend owns source provenance and deterministic eligibility checks. A daily-plan service selects only eligible local recipes and returns a complete three-meal view; an LLM may later rank eligible candidates but never bypasses validation. The existing `DailyMenuService` stays the single-dish TodayBoard and is not repurposed.

**Tech Stack:** Java 21, Spring Boot, Spring Data JPA, MySQL, JUnit 5/Mockito, uni-app/Vue 3.

## Global Constraints

- Target healthy adults only; display non-medical guidance and do not implement disease meal plans.
- Do not ingest, embed, show, or derive content from the unlicensed Guide 2022 PDF.
- A knowledge pack or seasonal ingredient is selectable only when enabled and `licenseStatus` is `commercially-usable`.
- Do not return a partial, malformed, unreadable, allergen-conflicting, taboo-conflicting, or cross-meal core-ingredient-repeating plan.
- Retain `nutrition-skill-methodology` author attribution and its MIT license in the third-party notices.

---

### Task 1: Add traceable nutrition knowledge packs and seasonal data

**Files:**
- Create: `backend/src/main/java/com/moodrecipe/backend/entity/NutritionKnowledgePack.java`
- Create: `backend/src/main/java/com/moodrecipe/backend/entity/SeasonalIngredient.java`
- Create: `backend/src/main/java/com/moodrecipe/backend/repository/NutritionKnowledgePackRepository.java`
- Create: `backend/src/main/java/com/moodrecipe/backend/repository/SeasonalIngredientRepository.java`
- Create: `backend/sql/migrations/20260928_add_nutrition_knowledge_packs.sql`
- Create: `backend/THIRD_PARTY_NOTICES.md`
- Test: `backend/src/test/java/com/moodrecipe/backend/service/NutritionKnowledgePackServiceTest.java`

**Interfaces:**
- Produces: `NutritionKnowledgePack.isEligible()` and `SeasonalIngredient.isEligible()`.
- Produces: repositories for enabled, commercially usable records.

- [ ] **Step 1: Write failing repository/domain tests**

```java
assertTrue(eligiblePack.isEligible());
assertFalse(disabledPack.isEligible());
assertFalse(unlicensedIngredient.isEligible());
```

- [ ] **Step 2: Run the focused test to verify it fails**

Run: `./mvnw -Dtest=NutritionKnowledgePackServiceTest test`

Expected: FAIL because the entities and eligibility methods do not exist.

- [ ] **Step 3: Add the minimal entities, repositories, migration, and MIT attribution notice**

```java
public boolean isEligible() {
    return enabled && "commercially-usable".equals(licenseStatus);
}
```

- [ ] **Step 4: Run the focused test to verify it passes**

Run: `./mvnw -Dtest=NutritionKnowledgePackServiceTest test`

Expected: PASS.

### Task 2: Implement deterministic meal-plan validation

**Files:**
- Create: `backend/src/main/java/com/moodrecipe/backend/service/DailyMealPlanValidator.java`
- Test: `backend/src/test/java/com/moodrecipe/backend/service/DailyMealPlanValidatorTest.java`

**Interfaces:**
- Consumes: `List<Recipe>`, `UserFoodPreference`, selected `NutritionKnowledgePack` records.
- Produces: `ValidationResult(boolean valid, List<String> reasons)`.

- [ ] **Step 1: Write failing tests for shrimp allergy, duplicate protein, and mojibake titles**

```java
assertFalse(validator.validate(List.of(shrimpDish, lunch, dinner), preference, packs).valid());
assertFalse(validator.validate(List.of(chickenBreakfast, chickenLunch, dinner), preference, packs).valid());
assertFalse(validator.validate(List.of(mojibakeDish, lunch, dinner), preference, packs).valid());
```

- [ ] **Step 2: Run the focused test to verify it fails**

Run: `./mvnw -Dtest=DailyMealPlanValidatorTest test`

Expected: FAIL because the validator does not exist.

- [ ] **Step 3: Add the minimal validator**

```java
public ValidationResult validate(List<Recipe> meals, UserFoodPreference preference,
                                 List<NutritionKnowledgePack> packs) {
    // require exactly three displayable, safe, distinct eligible meals
}
```

- [ ] **Step 4: Run the focused test to verify it passes**

Run: `./mvnw -Dtest=DailyMealPlanValidatorTest test`

Expected: PASS.

### Task 3: Add the daily meal-plan API and persistence snapshot

**Files:**
- Create: `backend/src/main/java/com/moodrecipe/backend/entity/DailyMealPlan.java`
- Create: `backend/src/main/java/com/moodrecipe/backend/repository/DailyMealPlanRepository.java`
- Create: `backend/src/main/java/com/moodrecipe/backend/service/DailyMealPlanService.java`
- Create: `backend/src/main/java/com/moodrecipe/backend/controller/DailyMealPlanController.java`
- Modify: `backend/sql/migrations/20260928_add_nutrition_knowledge_packs.sql`
- Test: `backend/src/test/java/com/moodrecipe/backend/service/DailyMealPlanServiceTest.java`

**Interfaces:**
- Produces: `GET /api/daily-meal-plan?date=YYYY-MM-DD` and `POST /api/daily-meal-plan/{meal}/replace`.
- Produces: a snapshot of recipe ids, ingredient data, knowledge-pack versions, rule hits, and generated explanation.

- [ ] **Step 1: Write failing service tests**

```java
DailyMealPlanService.PlanView plan = service.plan("user-1", LocalDate.of(2026, 9, 28));
assertEquals(3, plan.meals().size());
assertEquals("commercially-usable", plan.knowledgePackLicenseStatus());
```

- [ ] **Step 2: Run the focused test to verify it fails**

Run: `./mvnw -Dtest=DailyMealPlanServiceTest test`

Expected: FAIL because the service and API contract do not exist.

- [ ] **Step 3: Select local candidates, validate before save and return a complete snapshot**

```java
ValidationResult result = validator.validate(candidate, preference, packs);
if (!result.valid()) return Optional.empty();
```

- [ ] **Step 4: Run focused tests to verify they pass**

Run: `./mvnw -Dtest=DailyMealPlanServiceTest,DailyMealPlanValidatorTest test`

Expected: PASS.

### Task 4: Add the front-end daily-plan entry and safe states

**Files:**
- Create: `frontend/src/api/dailyMealPlan.ts`
- Create: `frontend/src/pages/daily-meal-plan/index.vue`
- Modify: `frontend/src/pages.config.ts`
- Test: manual H5 and WeChat Mini Program state verification.

**Interfaces:**
- Consumes: the daily meal-plan API contract from Task 3.
- Produces: loading, complete-result, retryable-empty, and failure UI states; no partial plan card.

- [ ] **Step 1: Add the API type and a failing UI-level contract test if the project test harness supports it**
- [ ] **Step 2: Implement the smallest page with three meal cards, explanation, version and non-medical disclaimer**
- [ ] **Step 3: Verify builds and state behavior**

Run: `pnpm type-check && pnpm build:h5`

Expected: no new type or build errors; record any existing vendor-only error separately.

### Task 5: Verify OpenSpec and documentation

**Files:**
- Modify: `frontend/openspec/changes/add-guideline-informed-meal-planning/tasks.md`

- [ ] **Step 1: Mark only implemented tasks complete**
- [ ] **Step 2: Run regression checks**

Run: `./mvnw test`, `pnpm exec openspec validate add-guideline-informed-meal-planning --strict`, and `git diff --check`

Expected: backend tests pass, OpenSpec strict validation passes, and no whitespace errors.
