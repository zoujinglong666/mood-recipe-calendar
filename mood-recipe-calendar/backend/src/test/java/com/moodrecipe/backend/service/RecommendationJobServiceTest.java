package com.moodrecipe.backend.service;

import com.moodrecipe.backend.entity.Recipe;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RecommendationJobServiceTest {

    @Test
    void reservesQuotaOnlyForANewJob() throws Exception {
        RecommendationJobService service = new RecommendationJobService(Duration.ofSeconds(1));
        CountDownLatch releaseWork = new CountDownLatch(1);
        AtomicInteger reservations = new AtomicInteger();
        try {
            var first = service.start("user-1", "平静", reservations::incrementAndGet, progress -> {
                try { releaseWork.await(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                Recipe recipe = new Recipe();
                recipe.setName("番茄炒蛋");
                return recipe;
            });
            var reused = service.start("user-1", "平静", reservations::incrementAndGet, progress -> null);

            assertEquals(first.jobId(), reused.jobId());
            assertEquals(1, reservations.get());
            releaseWork.countDown();
            waitForTerminal(service, first.jobId());
        } finally {
            releaseWork.countDown();
            service.close();
        }
    }

    @Test
    void reusesRunningJobWhenMoodChanges() throws Exception {
        RecommendationJobService service = new RecommendationJobService(Duration.ofSeconds(1));
        CountDownLatch releaseWork = new CountDownLatch(1);
        AtomicInteger reservations = new AtomicInteger();
        try {
            var first = service.start("user-1", "平静", reservations::incrementAndGet, progress -> {
                try { releaseWork.await(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                Recipe recipe = new Recipe();
                recipe.setName("番茄炒蛋");
                return recipe;
            });
            var reused = service.start("user-1", "疲惫", reservations::incrementAndGet, progress -> null);

            assertEquals(first.jobId(), reused.jobId());
            assertEquals(1, reservations.get());
            releaseWork.countDown();
            waitForTerminal(service, first.jobId());
        } finally {
            releaseWork.countDown();
            service.close();
        }
    }

    @Test
    void chargesOnlyWhenSuccessfulResultIsRead() throws Exception {
        RecommendationJobService service = new RecommendationJobService(Duration.ofSeconds(1));
        AtomicInteger charges = new AtomicInteger();
        try {
            var created = service.start("user-1", "平静", () -> { }, progress -> {
                Recipe recipe = new Recipe(); recipe.setName("番茄炒蛋"); return recipe;
            });
            waitForTerminal(service, created.jobId());
            service.chargeOnSuccess(created.jobId(), "user-1", charges::incrementAndGet);
            service.chargeOnSuccess(created.jobId(), "user-1", charges::incrementAndGet);
            assertEquals(1, charges.get());
        } finally {
            service.close();
        }
    }

    @Test
    void protectsOwnerAndKeepsFallbackDegradationUntilExpiry() throws Exception {
        RecommendationJobService service = new RecommendationJobService(Duration.ofMillis(20));
        try {
            var created = service.start("user-1", "平静", progress -> {
                progress.update(RecommendationJobService.Stage.MEMORY, RecommendationJobService.StepStatus.RUNNING, "reading");
                progress.update(RecommendationJobService.Stage.MEMORY, RecommendationJobService.StepStatus.COMPLETED, "read");
                progress.update(RecommendationJobService.Stage.TEXT, RecommendationJobService.StepStatus.DEGRADED, "text fallback");
                progress.update(RecommendationJobService.Stage.IMAGE, RecommendationJobService.StepStatus.DEGRADED, "image fallback");
                progress.usedFallback();
                progress.update(RecommendationJobService.Stage.LOCAL_FALLBACK, RecommendationJobService.StepStatus.COMPLETED, "local");
                progress.update(RecommendationJobService.Stage.FINALIZE, RecommendationJobService.StepStatus.COMPLETED, "done");
                Recipe recipe = new Recipe();
                recipe.setName("番茄炒蛋");
                return recipe;
            });

            RecommendationJobService.JobView finished = waitForTerminal(service, created.jobId());
            assertEquals(RecommendationJobService.JobStatus.SUCCEEDED, finished.status());
            assertTrue(finished.usedFallback());
            assertEquals(List.of(
                            RecommendationJobService.Stage.MEMORY,
                            RecommendationJobService.Stage.TEXT,
                            RecommendationJobService.Stage.IMAGE,
                            RecommendationJobService.Stage.LOCAL_FALLBACK,
                            RecommendationJobService.Stage.FINALIZE),
                    finished.steps().stream().map(RecommendationJobService.StepView::stage).toList());
            assertEquals(RecommendationJobService.StepStatus.DEGRADED, finished.steps().get(2).status());
            assertFalse(service.find(created.jobId(), "user-2").isPresent());

            Thread.sleep(30);
            assertTrue(service.find(created.jobId(), "user-1").isEmpty());
        } finally {
            service.close();
        }
    }

    private RecommendationJobService.JobView waitForTerminal(RecommendationJobService service, String jobId)
            throws InterruptedException {
        for (int attempt = 0; attempt < 100; attempt++) {
            var job = service.find(jobId, "user-1").orElseThrow();
            if (job.status() != RecommendationJobService.JobStatus.RUNNING) return job;
            Thread.sleep(5);
        }
        throw new AssertionError("recommendation job did not finish");
    }
}
