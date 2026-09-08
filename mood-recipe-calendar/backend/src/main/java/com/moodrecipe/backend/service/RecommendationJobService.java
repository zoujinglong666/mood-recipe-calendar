package com.moodrecipe.backend.service;

import com.moodrecipe.backend.entity.Recipe;
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

/** 单实例阶段使用的短期推荐任务；终态十分钟后自动淘汰。 */
@Service
public class RecommendationJobService {

    public enum JobStatus { RUNNING, SUCCEEDED, FAILED }
    public enum StepStatus { WAITING, RUNNING, COMPLETED, DEGRADED, FAILED }
    public enum Stage { MEMORY, TEXT, IMAGE, LOCAL_FALLBACK, FINALIZE }

    public record StepView(Stage stage, StepStatus status, String label, String message) { }
    public record JobView(String jobId, JobStatus status, Stage currentStage, List<StepView> steps,
                          String message, boolean usedFallback, Recipe recipe) { }

    @FunctionalInterface
    public interface RecommendationWork {
        Recipe run(Progress progress);
    }

    public interface Progress {
        void update(Stage stage, StepStatus status, String message);
        void usedFallback();
    }

    private static final Map<Stage, String> LABELS = Map.of(
            Stage.MEMORY, "读取口味记忆",
            Stage.TEXT, "生成今日菜谱",
            Stage.IMAGE, "制作菜品封面",
            Stage.LOCAL_FALLBACK, "切换本地推荐",
            Stage.FINALIZE, "整理推荐结果");
    private static final Map<Stage, Integer> ORDER = Map.of(
            Stage.MEMORY, 0, Stage.TEXT, 1, Stage.IMAGE, 2,
            Stage.LOCAL_FALLBACK, 3, Stage.FINALIZE, 4);

    private final ConcurrentHashMap<String, Job> jobs = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, String> activeJobs = new ConcurrentHashMap<>();
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
    private final Duration ttl;

    public RecommendationJobService() {
        this(Duration.ofMinutes(10));
    }

    RecommendationJobService(Duration ttl) {
        this.ttl = ttl;
    }

    public synchronized JobView start(String openid, String mood, RecommendationWork work) {
        cleanup();
        String activeKey = openid + "\u0000" + mood;
        String existingId = activeJobs.get(activeKey);
        if (existingId != null) {
            Job existing = jobs.get(existingId);
            if (existing != null && existing.status == JobStatus.RUNNING) return existing.view();
        }

        Job job = new Job(UUID.randomUUID().toString(), openid);
        jobs.put(job.id, job);
        activeJobs.put(activeKey, job.id);
        executor.submit(() -> {
            try {
                Recipe recipe = work.run(job);
                if (recipe == null) throw new IllegalStateException("recommendation unavailable");
                job.succeed(recipe);
            } catch (Exception ignored) {
                job.fail("锅仔暂时没找到合适的菜，请重新试一次");
            } finally {
                activeJobs.remove(activeKey, job.id);
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
        private volatile String message = "锅仔正在准备今天的推荐";
        private volatile boolean usedFallback;
        private volatile Recipe recipe;
        private volatile Instant finishedAt;

        private Job(String id, String openid) {
            this.id = id;
            this.openid = openid;
            for (Stage stage : List.of(Stage.MEMORY, Stage.TEXT, Stage.IMAGE, Stage.FINALIZE)) {
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

        @Override
        public void usedFallback() {
            usedFallback = true;
        }

        private synchronized void succeed(Recipe result) {
            recipe = result;
            status = JobStatus.SUCCEEDED;
            message = usedFallback ? "已从锅仔菜谱库为你挑好" : "今天的推荐准备好啦";
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
            return new JobView(id, status, currentStage, List.copyOf(ordered), message,
                    usedFallback, recipe);
        }
    }
}
