# Agent Reliability Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make dialogue cards optional and free-form, then generate reliable large weekly menus with primary/fallback model routing.

**Architecture:** Add a small routed LLM adapter so structured agent calls use Agnes 3.0 first and Agnes 2.5 only after a classified failure. Keep dialogue state unchanged but allow `Turn.card` to be absent. Preserve parsed dish steps and make batch generation globally unique before persistence.

**Tech Stack:** Spring Boot, Jackson, JPA, Vue 3/uni-app, JUnit 5, Mockito.

## Global Constraints

- Primary model is `agnes-3.0-flash`; fallback is `agnes-2.5-flash`.
- Do not expose model prompts, API keys, or raw model output to clients or logs.
- Do not repeat dishes to satisfy a requested count.
- Preserve existing membership limits and allergy filtering.

---

### Task 1: Add primary/fallback model routing

**Files:**
- Modify: `backend/src/main/java/com/moodrecipe/backend/agent/AgnesLlmClient.java`
- Create: `backend/src/main/java/com/moodrecipe/backend/agent/FallbackLlmClient.java`
- Modify: `backend/src/main/resources/application.properties`
- Test: `backend/src/test/java/com/moodrecipe/backend/agent/FallbackLlmClientTest.java`

- [ ] Write tests showing a successful primary result is used and a failed primary result calls the fallback once.
- [ ] Add separate `ai.llm.primary-model` and `ai.llm.fallback-model` properties and a routing client that returns the selected result without logging model text.
- [ ] Run `./mvnw -q -Dtest=FallbackLlmClientTest test`.

### Task 2: Make dialogue cards optional and add other-input affordance

**Files:**
- Modify: `backend/src/main/java/com/moodrecipe/backend/agent/DialogueState.java`
- Modify: `backend/src/main/java/com/moodrecipe/backend/agent/DialogueAgent.java`
- Modify: `backend/src/main/java/com/moodrecipe/backend/agent/AgentCards.java`
- Modify: `frontend/src/pages/meal-agent/index.vue`
- Test: `backend/src/test/java/com/moodrecipe/backend/agent/DialogueAgentTest.java`

- [ ] Write a failing test for an understood free-text turn returning no card, and an unclear required field returning a card with `other`.
- [ ] Return `null` cards for understood non-conflicting input; attach the action card only for unresolved required fields or conflicts.
- [ ] Render `其他` as a focused composer action that submits free text instead of a fixed protocol value.
- [ ] Run `./mvnw -q -Dtest=DialogueAgentTest test` and `pnpm type-check`.

### Task 3: Preserve recipe steps and validate menu output

**Files:**
- Modify: `backend/src/main/java/com/moodrecipe/backend/agent/MenuPlannerAgent.java`
- Modify: `backend/src/main/java/com/moodrecipe/backend/agent/MenuQualityScorer.java`
- Test: `backend/src/test/java/com/moodrecipe/backend/agent/MenuPlannerAgentTest.java`

- [ ] Write failing tests that parsed dishes retain distinct steps and names containing replacement/control characters are rejected.
- [ ] Carry steps through parsed dish data into `PlannedDish`; use default steps only when a valid response omits them.
- [ ] Run `./mvnw -q -Dtest=MenuPlannerAgentTest test`.

### Task 4: Batch and globally deduplicate large menus

**Files:**
- Modify: `backend/src/main/java/com/moodrecipe/backend/agent/MenuPlannerAgent.java`
- Test: `backend/src/test/java/com/moodrecipe/backend/agent/MenuPlannerAgentTest.java`

- [ ] Write a failing 7-by-7 test that either receives 49 unique dishes or an explicit insufficient-candidate error.
- [ ] Generate requests above 12 dishes one day at a time, pass accepted names into later prompts, and reject duplicate local candidates rather than cycling them.
- [ ] Run `./mvnw -q -Dtest=MenuPlannerAgentTest test`.

### Task 5: Verify integrated behavior

**Files:**
- Test: `backend/src/test/java/com/moodrecipe/backend/service/WeeklyMealPlanServiceTest.java`

- [ ] Add an integration-style service test proving different planned dishes retain their own steps in the saved plan.
- [ ] Run `./mvnw -q test`, `git diff --check`, and `pnpm type-check`; report any third-party-only type errors separately.
