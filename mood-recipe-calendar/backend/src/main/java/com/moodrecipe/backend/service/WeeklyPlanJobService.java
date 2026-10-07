package com.moodrecipe.backend.service;

import com.moodrecipe.backend.entity.WeeklyMealPlan;
import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** 单实例阶段使用的短期周菜单生成任务；终态十分钟后自动淘汰。生成在后台虚拟线程跑，POST 立即返回 jobId，前端轮询进度。 */
@Service
public class WeeklyPlanJobService {

    public enum JobStatus { RUNNING, SUCCEEDED, FAILED }
    public enum StepStatus { WAITING, RUNNING, COMPLETED, DEGRADED, FAILED }
    public enum Stage { MEMORY, PLAN, SAVE, FINALIZE }

    public record StepView(Stage stage, StepStatus status, String label, String message) { }
    public record JobView(String jobId, JobStatus status, Stage currentStage, List<StepView> steps,
                          String message, Long planId) { }

    @FunctionalInterface
    public interface WeeklyPlanWork {
        /** 返回生成成功的周菜单 id；失败抛异常。 */
        Long run(Progress progress);
    }

    public interface Progress {
        void update(Stage stage, StepStatus status, String message);
    }

    private static final Map<Stage, String> LABELS = Map.of(
            Stage.MEMORY, "读取你的口味记忆",
            Stage.PLAN, "调用锅仔规划这一周",
            Stage.SAVE, "保存周菜单",
            Stage.FINALIZE, "整理生成结果");
    private static final Map<Stage, Integer> ORDER = Map.of(
            Stage.MEMORY, 0, Stage.PLAN, 1, Stage.SAVE, 2, Stage.FINALIZE, 3);

    private final ConcurrentHashMap<String, Job> jobs = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, String> activeJobs = new ConcurrentHashMap<>();
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
    private final Duration ttl;

    public WeeklyPlanJobService() {
        this(Duration.ofMinutes(10));
    }

    WeeklyPlanJobService(Duration ttl) {
        this.ttl = ttl;
    }

    /** 仅当确实创建新任务时执行预留动作，避免轮询或复用进行中任务重复触发副作用。 */
    public synchronized JobView start(String openid, Runnable onNewJob, WeeklyPlanWork work) {
        cleanup();
        String existingId = activeJobs.get(openid);
        if (existingId != null) {
            Job existing = jobs.get(existingId);
            if (existing != null && existing.status == JobStatus.RUNNING) return existing.view();
        }
        onNewJob.run();
        Job job = new Job(UUID.randomUUID().toString(), openid);
        jobs.put(job.id, job);
        activeJobs.put(openid, job.id);
        executor.submit(() -> {
            try {
                Long planId = work.run(job);
                if (planId == null) throw new IllegalStateException("weekly plan unavailable");
                job.succeed(planId);
            } catch (Exception ignored) {
                job.fail("锅仔这次没排好这周的菜单，请再试一次");
            } finally {
                activeJobs.remove(openid, job.id);
            }
        });
        return job.view();
    }

    public Optional<JobView> find(String jobId, String openid) {
        cleanup();
        Job job = jobs.get(jobId);
        return job == null || !job.openid.equals(openid) ? Optional.empty() : Optional.of(job.view());
    }

    private void cleanup() {
        Instant cutoff = Instant.now().minus(ttl);
        jobs.entrySet().removeIf(entry -> entry.getValue().finishedAt != null
                && entry.getValue().finishedAt.isBefore(cutoff));
    }

    @PreDestroy
    void close() {
        executor.close();
    }

    private static final class Job implements Progress {
        private final String id;
        private final String openid;
        private final Map<Stage, StepView> steps = new LinkedHashMap<>();
        private volatile JobStatus status = JobStatus.RUNNING;
        private volatile Stage currentStage;
        private volatile String message = "锅仔正在准备这周的菜单";
        private volatile Long planId;
        private volatile Instant finishedAt;

        private Job(String id, String openid) {
            this.id = id;
            this.openid = openid;
            for (Stage stage : List.of(Stage.MEMORY, Stage.PLAN, Stage.SAVE, Stage.FINALIZE)) {
                steps.put(stage, new StepView(stage, StepStatus.WAITING, LABELS.get(stage), "等待开始"));
            }
        }

        @Override
        public synchronized void update(Stage stage, StepStatus stepStatus, String stepMessage) {
            if (status != JobStatus.RUNNING) return;
            steps.put(stage, new StepView(stage, stepStatus, LABELS.get(stage), stepMessage));
            if (stepStatus == StepStatus.RUNNING || stepStatus == StepStatus.FAILED) currentStage = stage;
            message = stepMessage;
        }

        private synchronized void succeed(Long resultId) {
            planId = resultId;
            status = JobStatus.SUCCEEDED;
            message = "这周的菜单准备好啦";
            finishedAt = Instant.now();
        }

        private synchronized void fail(String failureMessage) {
            status = JobStatus.FAILED;
            message = failureMessage;
            if (currentStage != null) {
                StepView current = steps.get(currentStage);
                steps.put(currentStage, new StepView(currentStage, StepStatus.FAILED,
                        LABELS.get(currentStage), failureMessage));
            }
            finishedAt = Instant.now();
        }

        private synchronized JobView view() {
            List<StepView> ordered = new ArrayList<>(steps.values());
            ordered.sort(Comparator.comparingInt(step -> ORDER.get(step.stage())));
            return new JobView(id, status, currentStage, List.copyOf(ordered), message, planId);
        }
    }
}
