# Cooking Text Normalization Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Prevent unclear cooking wording and repeated ingredients from reaching users in weekly plans and recipe cards.

**Architecture:** Add one small pure normalizer for ingredient and step text. Apply it at both AI-plan parsing and legacy recipe mapping, while tightening prompts so new output is correct at the source.

**Tech Stack:** Java 17, Spring Boot, JUnit 5, Jackson.

## Global Constraints

- Keep the existing response shape and frontend unchanged.
- Do not reject a whole menu for a wording issue that can be safely normalized.
- Preserve unrelated generated frontend changes already present in the worktree.

---

### Task 1: Normalize cooking ingredients and steps

**Files:**
- Create: `backend/src/main/java/com/moodrecipe/backend/service/CookingTextNormalizer.java`
- Test: `backend/src/test/java/com/moodrecipe/backend/service/CookingTextNormalizerTest.java`

**Interfaces:**
- `normalizeIngredients(List<String>)` removes duplicate ingredient entries after action-prefix normalization.
- `normalizeSteps(List<String>)` replaces unclear `切朵` phrasing and collapses exact repeated ingredients in one action.

- [ ] Write tests for `切朵`, repeated `撒枸杞和枸杞`, and duplicate `枸杞`/`撒枸杞` entries.
- [ ] Run the targeted test and verify it fails before the class exists.
- [ ] Implement the smallest pure normalizer with stable ordering.
- [ ] Run the targeted test and verify it passes.

### Task 2: Apply normalization at all recipe boundaries

**Files:**
- Modify: `backend/src/main/java/com/moodrecipe/backend/agent/MenuPlannerAgent.java`
- Modify: `backend/src/main/java/com/moodrecipe/backend/service/WeeklyMealPlanService.java`

- [ ] Normalize AI JSON ingredients and steps before constructing planner inputs.
- [ ] Normalize legacy recipe ingredients and steps before exposing them in plan views.
- [ ] Run planner/service tests and then the full backend test suite.

### Task 3: Constrain future model output

**Files:**
- Modify: `backend/src/main/java/com/moodrecipe/backend/agent/AgentPrompts.java`
- Modify: `backend/src/main/java/com/moodrecipe/backend/service/GuozaiPersona.java`

- [ ] Require simple Chinese culinary wording, one action per step, no duplicate ingredient phrase, and explicit alternatives such as `掰成小朵`.
- [ ] Run `git diff --check` and the full backend test suite.

