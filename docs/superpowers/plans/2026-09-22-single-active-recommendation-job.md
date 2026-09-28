# Single Active Recommendation Job Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Ensure repeated homepage recommendation requests for one user share one running job, one model invocation, and one quota reservation.

**Architecture:** `RecommendationJobService` will key active homepage work only by `openid`, so changing mood cannot create parallel work. The recipe page will persist the returned job ID and resume polling it after a page hide/show instead of submitting a new request.

**Tech Stack:** Spring Boot/JUnit 5, Vue 3/uni-app, TypeScript.

## Global Constraints

- Keep completed and failed jobs eligible for a new explicit recommendation.
- Do not change the quota policy; only the newly created job consumes quota.
- Do not rebuild generated frontend `dist` output.

---

### Task 1: Coalesce backend tasks across moods

**Files:**
- Modify: `mood-recipe-calendar/backend/src/test/java/com/moodrecipe/backend/service/RecommendationJobServiceTest.java`
- Modify: `mood-recipe-calendar/backend/src/main/java/com/moodrecipe/backend/service/RecommendationJobService.java`

**Interfaces:**
- Consumes: `start(String openid, String mood, Runnable onNewJob, RecommendationWork work)`.
- Produces: A repeated `start` call for the same `openid` returns the active `JobView` regardless of `mood`.

- [x] **Step 1: Write the failing test**

```java
var first = service.start("user-1", "平静", reservations::incrementAndGet, work);
var reused = service.start("user-1", "疲惫", reservations::incrementAndGet, ignored -> null);
assertEquals(first.jobId(), reused.jobId());
assertEquals(1, reservations.get());
```

- [x] **Step 2: Run the test to verify it fails**

Run: `./mvnw -q -Dtest=RecommendationJobServiceTest#reusesRunningJobWhenMoodChanges test`

Expected: FAIL because the current active-task key includes `mood`.

- [x] **Step 3: Write the minimal implementation**

```java
String activeKey = openid;
```

- [x] **Step 4: Run the test to verify it passes**

Run: `./mvnw -q -Dtest=RecommendationJobServiceTest#reusesRunningJobWhenMoodChanges test`

Expected: PASS.

### Task 2: Resume polling from the client-held job ID

**Files:**
- Modify: `mood-recipe-calendar/frontend/src/pages/recipe/index.vue`

**Interfaces:**
- Consumes: `fetchRecommendationJob(jobId: string)`.
- Produces: local storage key `mrc_active_recommendation_job` holding the active job ID; it is removed on terminal job status.

- [x] **Step 1: Persist a newly returned job ID**

```ts
uni.setStorageSync(ACTIVE_JOB_KEY, created.jobId)
```

- [x] **Step 2: Resume before creating a job**

```ts
const activeJobId = uni.getStorageSync(ACTIVE_JOB_KEY)
if (activeJobId) return pollRecommendation(String(activeJobId), run)
```

- [x] **Step 3: Remove terminal job IDs**

```ts
if (job.status !== 'RUNNING') uni.removeStorageSync(ACTIVE_JOB_KEY)
```

- [ ] **Step 4: Verify TypeScript without rebuilding dist**

Run: `pnpm type-check`

Expected: no application-source errors; document any dependency-only diagnostics.
