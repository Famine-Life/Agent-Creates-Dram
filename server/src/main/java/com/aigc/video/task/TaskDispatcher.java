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

import java.time.LocalDateTime;
import java.util.List;

/**
 * 任务调度：DB 轮询式，不依赖 Redis / MQ。
 * 1. 把 QUEUED 任务提交给供应商（CAS 抢占，避免重复提交）
 * 2. 按 next_poll_at 轮询 SUBMITTED / RUNNING 任务，退避 2/3/5/8/13/20/30 秒
 */
/**
 * 处理画布与生成任务的业务流程。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TaskDispatcher {

    private static final int[] BACKOFF_SECONDS = {2, 3, 5, 8, 13, 20, 30};

    private static final int SUBMIT_BATCH = 5;
    private static final int POLL_BATCH = 10;

    private final GenerationTaskMapper taskMapper;
    private final ModelCatalog modelCatalog;
    private final ProviderRegistry providerRegistry;
    private final AssetService assetService;
    private final TaskService taskService;
    private final DispatchLock dispatchLock;

    @Value("${aigc.poll-timeout-seconds:900}")
    private long pollTimeoutSeconds;

    @Scheduled(initialDelay = 2000, fixedDelayString = "${aigc.dispatch-interval-ms:800}")
    public void tick() {
        String lockToken = dispatchLock.tryAcquire();
        if (lockToken == null) {
            return;
        }
        try {
            try {
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
        List<GenerationTask> queued = taskMapper.selectList(Wrappers.<GenerationTask>lambdaQuery()
                .in(GenerationTask::getStatus, TaskStatus.RECEIVED.name(), TaskStatus.QUEUED.name())
                .orderByAsc(GenerationTask::getId)
                .last("LIMIT " + SUBMIT_BATCH));
        for (GenerationTask task : queued) {
            submit(task);
        }
    }

    private void submit(GenerationTask task) {
        LocalDateTime now = LocalDateTime.now();
        int updated = taskMapper.update(null, Wrappers.<GenerationTask>lambdaUpdate()
                .eq(GenerationTask::getId, task.getId())
                .in(GenerationTask::getStatus, TaskStatus.RECEIVED.name(), TaskStatus.QUEUED.name())
                .set(GenerationTask::getStatus, TaskStatus.SUBMITTED.name())
                .set(GenerationTask::getProgress, 3)
                .set(GenerationTask::getSubmittedAt, now)
                .set(GenerationTask::getNextPollAt, now.plusSeconds(2))
                .set(GenerationTask::getUpdatedAt, now));
        if (updated == 0) {
            return;
        }
        try {
            TaskNode node = taskService.node(task.getId());
            ModelCatalog.ResolvedModel model = modelCatalog.resolveVideoTask(task.getProvider(), task.getModel());
            VideoProvider provider = providerRegistry.get(model.protocol());
            VideoContext context = context(model);
            List<String> referenceImages = node.imageUrls().stream()
                    .map(assetService::providerImageUrl)
                    .toList();
            SubmitResult result = provider.submit(new SubmitCommand(node.prompt(), node.title(), referenceImages,
                    node.ratio(), node.duration(), node.resolution(), context));
            // provider 存平台标识、model 存真实模型 ID，便于后续按平台路由与存量兼容
            taskMapper.update(null, Wrappers.<GenerationTask>lambdaUpdate()
                    .eq(GenerationTask::getId, task.getId())
                    .set(GenerationTask::getProvider, model.providerKey())
                    .set(GenerationTask::getModel, model.modelId())
                    .set(GenerationTask::getProviderTaskId, result.providerTaskId())
                    .set(GenerationTask::getUpdatedAt, LocalDateTime.now()));
            log.info("任务已提交 id={} model={} protocol={} providerTaskId={}",
                    task.getId(), model.key(), model.protocol(), result.providerTaskId());
            taskService.broadcastTask("node.progress", task.getId());
        } catch (Exception e) {
            log.warn("任务提交失败 id={}：{}", task.getId(), e.getMessage());
            fail(task.getId(), "提交失败：" + rootMessage(e));
        }
    }

    private void pollRunning() {
        LocalDateTime now = LocalDateTime.now();
        List<GenerationTask> running = taskMapper.selectList(Wrappers.<GenerationTask>lambdaQuery()
                .in(GenerationTask::getStatus, TaskStatus.SUBMITTED.name(), TaskStatus.RUNNING.name())
                .and(w -> w.isNull(GenerationTask::getNextPollAt).or().le(GenerationTask::getNextPollAt, now))
                .orderByAsc(GenerationTask::getNextPollAt)
                .last("LIMIT " + POLL_BATCH));
        for (GenerationTask task : running) {
            poll(task);
        }
    }

    private void poll(GenerationTask task) {
        LocalDateTime now = LocalDateTime.now();
        if (task.getSubmittedAt() != null
                && task.getSubmittedAt().plusSeconds(pollTimeoutSeconds).isBefore(now)) {
            timeout(task.getId());
            return;
        }
        if (task.getProviderTaskId() == null || task.getProviderTaskId().isBlank()) {
            // 提交后进程中断等异常情况，重新排队而不是让任务卡死
            taskMapper.update(null, Wrappers.<GenerationTask>lambdaUpdate()
                    .eq(GenerationTask::getId, task.getId())
                    .set(GenerationTask::getStatus, TaskStatus.QUEUED.name())
                    .set(GenerationTask::getSubmittedAt, null)
                    .set(GenerationTask::getUpdatedAt, now));
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
                String coverUrl = result.coverUrl() == null
                        ? null
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

    private int backoff(int pollCount) {
        int index = Math.min(Math.max(pollCount - 1, 0), BACKOFF_SECONDS.length - 1);
        return BACKOFF_SECONDS[index];
    }

    private VideoContext context(ModelCatalog.ResolvedModel model) {
        return new VideoContext(model.providerKey(), model.baseUrl(), model.apiKey(),
                model.modelId(), model.watermark());
    }

    private String rootMessage(Throwable e) {
        Throwable cursor = e;
        while (cursor.getCause() != null) {
            cursor = cursor.getCause();
        }
        return cursor.getMessage() == null ? cursor.getClass().getSimpleName() : cursor.getMessage();
    }

    private String shorten(String message) {
        if (message == null) {
            return null;
        }
        return message.length() > 900 ? message.substring(0, 900) : message;
    }
}
