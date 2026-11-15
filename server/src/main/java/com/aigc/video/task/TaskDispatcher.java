package com.aigc.video.task;

import com.aigc.video.ai.ModelCatalog;
import com.aigc.video.api.dto.TaskNode;
import com.aigc.video.domain.GenerationTask;
import com.aigc.video.mapper.GenerationTaskMapper;
import com.aigc.video.provider.PollResult;
import com.aigc.video.provider.ProviderRegistry;
import com.aigc.video.provider.SubmitCommand;
import com.aigc.video.provider.SubmitResult;
import com.aigc.video.provider.VideoContext;
import com.aigc.video.provider.VideoProvider;
import com.aigc.video.storage.AssetService;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientResponseException;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** 按本地并发配额提交任务，并轮询供应商状态。 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TaskDispatcher {

    private enum SubmitDisposition { RESERVED, RETRY_LATER, STOP_MODEL }

    private static final int[] BACKOFF_SECONDS = {2, 3, 5, 8, 13, 20, 30};
    private static final int SUBMIT_BATCH = 5;
    private static final int CANDIDATE_BATCH = 1000;
    private static final int POLL_BATCH = 10;
    private static final List<String> CAPACITY_STATUSES = List.of(
            TaskStatus.SUBMITTING.name(), TaskStatus.SUBMITTED.name(),
            TaskStatus.PROVIDER_QUEUED.name(), TaskStatus.RUNNING.name(),
            TaskStatus.SUBMIT_UNKNOWN.name());

    private final GenerationTaskMapper taskMapper;
    private final ModelCatalog modelCatalog;
    private final ProviderRegistry providerRegistry;
    private final AssetService assetService;
    private final TaskService taskService;
    private final DispatchLock dispatchLock;

    @Value("${aigc.poll-timeout-seconds:900}")
    private long pollTimeoutSeconds;

    @Value("${aigc.dispatch.account-type:enterprise}")
    private String accountType;

    @Value("${aigc.dispatch.enterprise-max-concurrent:10}")
    private int enterpriseMaxConcurrent;

    @Value("${aigc.dispatch.personal-max-concurrent:3}")
    private int personalMaxConcurrent;

    @Value("${aigc.dispatch.max-concurrent-4k:1}")
    private int maxConcurrent4k;

    @Value("${aigc.dispatch.max-submit-retries:8}")
    private int maxSubmitRetries;

    @Value("${aigc.dispatch.submit-unknown-timeout-seconds:120}")
    private long submitUnknownTimeoutSeconds;

    @Scheduled(initialDelay = 2000, fixedDelayString = "${aigc.dispatch-interval-ms:800}")
    public void tick() {
        String lockToken = dispatchLock.tryAcquire();
        if (lockToken == null) return;
        try {
            try {
                recoverUncertainSubmissions();
                submitQueued();
            } catch (Exception e) {
                log.error("提交队列处理异常", e);
            }
            try {
                pollRunning();
            } catch (Exception e) {
                log.error("运行中任务轮询异常", e);
            }
        } finally {
            dispatchLock.release(lockToken);
        }
    }

    private void submitQueued() {
        LocalDateTime now = LocalDateTime.now();
        List<GenerationTask> queued = taskMapper.selectList(Wrappers.<GenerationTask>lambdaQuery()
                .in(GenerationTask::getStatus, TaskStatus.RECEIVED.name(), TaskStatus.QUEUED.name())
                .and(w -> w.isNull(GenerationTask::getNextPollAt).or().le(GenerationTask::getNextPollAt, now))
                .orderByAsc(GenerationTask::getId)
                .last("LIMIT " + CANDIDATE_BATCH));
        Map<String, Integer> inFlight = currentInFlight();
        int submitted = 0;
        for (GenerationTask task : queued) {
            if (submitted >= SUBMIT_BATCH) break;
            ModelCatalog.ResolvedModel model;
            try {
                model = modelCatalog.resolveVideoTask(task.getProvider(), task.getModel());
            } catch (Exception e) {
                fail(task.getId(), "模型配置不可用：" + rootMessage(e));
                continue;
            }
            String key = capacityKey(model, task.getResolution());
            int used = inFlight.getOrDefault(key, 0);
            int limit = capacityLimit(task.getResolution());
            if (used >= limit) continue;
            SubmitDisposition disposition = submit(task, model);
            if (disposition == SubmitDisposition.RESERVED) {
                inFlight.put(key, used + 1);
                submitted++;
            } else if (disposition == SubmitDisposition.STOP_MODEL) {
                // 遇到模型级 429 后本轮停止向该模型继续发请求，避免连续触发限流。
                inFlight.put(key, limit);
            }
        }
        if (!queued.isEmpty()) {
            log.info("任务调度扫描 candidates={} submitted={} accountType={} activeByModel={}",
                    queued.size(), submitted, accountType, inFlight);
        }
    }

    private SubmitDisposition submit(GenerationTask task, ModelCatalog.ResolvedModel model) {
        LocalDateTime now = LocalDateTime.now();
        int updated = taskMapper.update(null, Wrappers.<GenerationTask>lambdaUpdate()
                .eq(GenerationTask::getId, task.getId())
                .in(GenerationTask::getStatus, TaskStatus.RECEIVED.name(), TaskStatus.QUEUED.name())
                .set(GenerationTask::getStatus, TaskStatus.SUBMITTING.name())
                .set(GenerationTask::getProgress, 3)
                .set(GenerationTask::getSubmittedAt, now)
                .set(GenerationTask::getNextPollAt, null)
                .set(GenerationTask::getErrorMsg, null)
                .set(GenerationTask::getUpdatedAt, now));
        if (updated == 0) return SubmitDisposition.RETRY_LATER;

        try {
            TaskNode node = taskService.node(task.getId());
            VideoContext context = context(model);
            List<String> referenceImages = node.imageUrls().stream()
                    .map(assetService::providerImageUrl).toList();
            SubmitResult result = providerRegistry.get(model.protocol()).submit(new SubmitCommand(
                    node.prompt(), node.title(), referenceImages, node.ratio(), node.duration(),
                    node.resolution(), context));
            LocalDateTime acceptedAt = LocalDateTime.now();
            int saved = taskMapper.update(null, Wrappers.<GenerationTask>lambdaUpdate()
                    .eq(GenerationTask::getId, task.getId())
                    .eq(GenerationTask::getStatus, TaskStatus.SUBMITTING.name())
                    .set(GenerationTask::getStatus, TaskStatus.SUBMITTED.name())
                    .set(GenerationTask::getProvider, model.providerKey())
                    .set(GenerationTask::getModel, model.modelId())
                    .set(GenerationTask::getProviderTaskId, result.providerTaskId())
                    .set(GenerationTask::getNextPollAt, acceptedAt.plusSeconds(2))
                    .set(GenerationTask::getPollCount, 0)
                    .set(GenerationTask::getUpdatedAt, acceptedAt));
            if (saved == 0) {
                // 用户可能在外部请求期间取消任务；保存失败时尽力撤销刚创建的远端任务。
                try {
                    providerRegistry.get(model.protocol()).cancel(result.providerTaskId(), context);
                } catch (Exception cancelError) {
                    log.error("任务已取消但远端撤销失败 id={} providerTaskId={}",
                            task.getId(), result.providerTaskId(), cancelError);
                }
                return SubmitDisposition.RETRY_LATER;
            }
            log.info("任务已提交 id={} model={} providerTaskId={}",
                    task.getId(), model.key(), result.providerTaskId());
            taskService.broadcastTask("node.progress", task.getId());
            return SubmitDisposition.RESERVED;
        } catch (RestClientResponseException e) {
            if (e.getStatusCode().value() == 429) {
                retryAfterRateLimit(task, e);
                return SubmitDisposition.STOP_MODEL;
            } else if (e.getStatusCode().is5xxServerError()) {
                markSubmitUnknown(task.getId(), "供应商返回 " + e.getStatusCode().value()
                        + "，无法确认任务是否已接收；请先检查方舟控制台，再手动重试。");
                return SubmitDisposition.RESERVED;
            } else {
                fail(task.getId(), "提交失败：" + rootMessage(e));
            }
            return SubmitDisposition.RETRY_LATER;
        } catch (ResourceAccessException e) {
            markSubmitUnknown(task.getId(), "提交请求超时或连接中断，无法确认供应商是否已接收；"
                    + "请先检查方舟控制台，再手动重试。");
            return SubmitDisposition.RESERVED;
        } catch (Exception e) {
            fail(task.getId(), "提交失败：" + rootMessage(e));
            return SubmitDisposition.RETRY_LATER;
        }
    }

    private void retryAfterRateLimit(GenerationTask task, RestClientResponseException e) {
        int attempts = (task.getPollCount() == null ? 0 : task.getPollCount()) + 1;
        if (attempts > maxSubmitRetries) {
            fail(task.getId(), "供应商持续限流，已达到自动重试上限；请稍后手动重试");
            return;
        }
        long delay = backoff(attempts);
        String retryAfter = e.getResponseHeaders() == null ? null
                : e.getResponseHeaders().getFirst("Retry-After");
        if (retryAfter != null) {
            try { delay = Math.max(delay, Math.min(300, Long.parseLong(retryAfter))); }
            catch (NumberFormatException ignored) { }
        }
        LocalDateTime now = LocalDateTime.now();
        taskMapper.update(null, Wrappers.<GenerationTask>lambdaUpdate()
                .eq(GenerationTask::getId, task.getId())
                .eq(GenerationTask::getStatus, TaskStatus.SUBMITTING.name())
                .set(GenerationTask::getStatus, TaskStatus.QUEUED.name())
                .set(GenerationTask::getPollCount, attempts)
                .set(GenerationTask::getNextPollAt, now.plusSeconds(delay))
                .set(GenerationTask::getSubmittedAt, null)
                .set(GenerationTask::getErrorMsg, "供应商限流，" + delay + " 秒后自动重试")
                .set(GenerationTask::getUpdatedAt, now));
        log.warn("任务触发供应商限流，稍后重试 id={} delaySeconds={} attempt={}",
                task.getId(), delay, attempts);
        taskService.broadcastTask("node.updated", task.getId());
    }

    private void markSubmitUnknown(Long taskId, String message) {
        taskMapper.update(null, Wrappers.<GenerationTask>lambdaUpdate()
                .eq(GenerationTask::getId, taskId)
                .eq(GenerationTask::getStatus, TaskStatus.SUBMITTING.name())
                .set(GenerationTask::getStatus, TaskStatus.SUBMIT_UNKNOWN.name())
                .set(GenerationTask::getErrorMsg, message)
                .set(GenerationTask::getNextPollAt, null)
                .set(GenerationTask::getUpdatedAt, LocalDateTime.now()));
        log.error("任务提交结果不确定 id={}：{}", taskId, message);
        taskService.broadcastTask("node.failed", taskId);
    }

    private void recoverUncertainSubmissions() {
        LocalDateTime cutoff = LocalDateTime.now().minusSeconds(submitUnknownTimeoutSeconds);
        List<GenerationTask> stale = taskMapper.selectList(Wrappers.<GenerationTask>lambdaQuery()
                .eq(GenerationTask::getStatus, TaskStatus.SUBMITTING.name())
                .lt(GenerationTask::getSubmittedAt, cutoff)
                .last("LIMIT " + CANDIDATE_BATCH));
        for (GenerationTask task : stale) {
            markSubmitUnknown(task.getId(), "服务在提交过程中中断，无法确认供应商是否已接收；"
                    + "请先检查方舟控制台，再手动重试。");
        }
    }

    private Map<String, Integer> currentInFlight() {
        Map<String, Integer> counts = new HashMap<>();
        List<GenerationTask> active = taskMapper.selectList(Wrappers.<GenerationTask>lambdaQuery()
                .in(GenerationTask::getStatus, CAPACITY_STATUSES));
        for (GenerationTask task : active) {
            try {
                ModelCatalog.ResolvedModel model = modelCatalog.resolveVideoTask(task.getProvider(), task.getModel());
                String key = capacityKey(model, task.getResolution());
                counts.merge(key, 1, Integer::sum);
            } catch (Exception ignored) {
                log.warn("无法计算任务并发占用 id={} provider={} model={}",
                        task.getId(), task.getProvider(), task.getModel());
            }
        }
        return counts;
    }

    private int capacityLimit(String resolution) {
        if (is4k(resolution)) return maxConcurrent4k;
        return "personal".equalsIgnoreCase(accountType)
                ? personalMaxConcurrent : enterpriseMaxConcurrent;
    }

    private String capacityKey(ModelCatalog.ResolvedModel model, String resolution) {
        // 方舟版本号可能随日期变化；并发配额按模型而非模型版本统计。
        String modelFamily = model.modelId().replaceFirst("-\\d{6,8}$", "");
        return model.providerKey() + "/" + modelFamily + "/" + is4k(resolution);
    }

    private boolean is4k(String resolution) {
        return "4K".equalsIgnoreCase(resolution == null ? "" : resolution.trim());
    }

    private void pollRunning() {
        LocalDateTime now = LocalDateTime.now();
        List<GenerationTask> running = taskMapper.selectList(Wrappers.<GenerationTask>lambdaQuery()
                .in(GenerationTask::getStatus, TaskStatus.SUBMITTED.name(),
                        TaskStatus.PROVIDER_QUEUED.name(), TaskStatus.RUNNING.name())
                .and(w -> w.isNull(GenerationTask::getNextPollAt).or().le(GenerationTask::getNextPollAt, now))
                .orderByAsc(GenerationTask::getNextPollAt)
                .last("LIMIT " + POLL_BATCH));
        for (GenerationTask task : running) poll(task);
    }

    private void poll(GenerationTask task) {
        LocalDateTime now = LocalDateTime.now();
        if (task.getProviderTaskId() == null || task.getProviderTaskId().isBlank()) {
            markSubmitUnknown(task.getId(), "数据库缺少供应商任务号，无法安全地自动重提；请先核对供应商控制台。");
            return;
        }
        if (task.getSubmittedAt() != null
                && task.getSubmittedAt().plusSeconds(pollTimeoutSeconds).isBefore(now)) {
            timeout(task.getId());
            return;
        }
        PollResult result;
        try {
            ModelCatalog.ResolvedModel model = modelCatalog.resolveVideoTask(task.getProvider(), task.getModel());
            result = providerRegistry.get(model.protocol()).poll(task.getProviderTaskId(), context(model));
        } catch (Exception e) {
            log.warn("轮询失败 id={}：{}", task.getId(), e.getMessage());
            int pollCount = (task.getPollCount() == null ? 0 : task.getPollCount()) + 1;
            taskMapper.update(null, Wrappers.<GenerationTask>lambdaUpdate()
                    .eq(GenerationTask::getId, task.getId())
                    .set(GenerationTask::getPollCount, pollCount)
                    .set(GenerationTask::getNextPollAt, now.plusSeconds(backoff(pollCount)))
                    .set(GenerationTask::getUpdatedAt, now));
            return;
        }

        int pollCount = (task.getPollCount() == null ? 0 : task.getPollCount()) + 1;
        switch (result.status()) {
            case "SUCCEEDED" -> {
                String videoUrl = assetService.transfer(result.videoUrl(), "videos", ".mp4");
                String coverUrl = result.coverUrl() == null ? null
                        : assetService.transfer(result.coverUrl(), "covers", ".jpg");
                taskMapper.update(null, Wrappers.<GenerationTask>lambdaUpdate()
                        .eq(GenerationTask::getId, task.getId())
                        .set(GenerationTask::getStatus, TaskStatus.SUCCEEDED.name())
                        .set(GenerationTask::getProgress, 100)
                        .set(GenerationTask::getVideoUrl, videoUrl)
                        .set(GenerationTask::getCoverUrl, coverUrl)
                        .set(GenerationTask::getErrorMsg, null)
                        .set(GenerationTask::getPollCount, pollCount)
                        .set(GenerationTask::getNextPollAt, null)
                        .set(GenerationTask::getUpdatedAt, now));
                log.info("任务完成 id={} videoUrl={}", task.getId(), videoUrl);
                taskService.broadcastTask("node.completed", task.getId());
            }
            case "FAILED" -> fail(task.getId(), result.error() == null ? "生成失败" : result.error());
            case "QUEUED" -> {
                long delay = backoff(pollCount);
                taskMapper.update(null, Wrappers.<GenerationTask>lambdaUpdate()
                        .eq(GenerationTask::getId, task.getId())
                        .set(GenerationTask::getStatus, TaskStatus.PROVIDER_QUEUED.name())
                        .set(GenerationTask::getProgress, Math.min(5, Math.max(0, result.progress())))
                        .set(GenerationTask::getPollCount, pollCount)
                        .set(GenerationTask::getNextPollAt, now.plusSeconds(delay))
                        .set(GenerationTask::getUpdatedAt, now));
                taskService.broadcastTask("node.progress", task.getId());
            }
            default -> {
                int progress = Math.max(task.getProgress() == null ? 5 : task.getProgress(),
                        Math.min(99, result.progress()));
                taskMapper.update(null, Wrappers.<GenerationTask>lambdaUpdate()
                        .eq(GenerationTask::getId, task.getId())
                        .set(GenerationTask::getStatus, TaskStatus.RUNNING.name())
                        .set(GenerationTask::getProgress, progress)
                        .set(GenerationTask::getPollCount, pollCount)
                        .set(GenerationTask::getNextPollAt, now.plusSeconds(backoff(pollCount)))
                        .set(GenerationTask::getUpdatedAt, now));
                taskService.broadcastTask("node.progress", task.getId());
            }
        }
    }

    private void fail(Long taskId, String message) {
        taskMapper.update(null, Wrappers.<GenerationTask>lambdaUpdate()
                .eq(GenerationTask::getId, taskId)
                .set(GenerationTask::getStatus, TaskStatus.FAILED.name())
                .set(GenerationTask::getErrorMsg, shorten(message))
                .set(GenerationTask::getNextPollAt, null)
                .set(GenerationTask::getUpdatedAt, LocalDateTime.now()));
        taskService.broadcastTask("node.failed", taskId);
    }

    private void timeout(Long taskId) {
        taskMapper.update(null, Wrappers.<GenerationTask>lambdaUpdate()
                .eq(GenerationTask::getId, taskId)
                .set(GenerationTask::getStatus, TaskStatus.TIMEOUT.name())
                .set(GenerationTask::getErrorMsg, "生成超时（超过 " + pollTimeoutSeconds + " 秒）")
                .set(GenerationTask::getNextPollAt, null)
                .set(GenerationTask::getUpdatedAt, LocalDateTime.now()));
        taskService.broadcastTask("node.failed", taskId);
    }

    private int backoff(int attempt) {
        return BACKOFF_SECONDS[Math.min(Math.max(attempt - 1, 0), BACKOFF_SECONDS.length - 1)];
    }

    private VideoContext context(ModelCatalog.ResolvedModel model) {
        return new VideoContext(model.providerKey(), model.baseUrl(), model.apiKey(),
                model.modelId(), model.watermark());
    }

    private String rootMessage(Throwable e) {
        Throwable cursor = e;
        while (cursor.getCause() != null) cursor = cursor.getCause();
        return cursor.getMessage() == null ? cursor.getClass().getSimpleName() : cursor.getMessage();
    }

    private String shorten(String message) {
        if (message == null) return null;
        return message.length() > 900 ? message.substring(0, 900) : message;
    }
}
