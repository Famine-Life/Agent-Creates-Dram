package com.aigc.video.api;

import com.aigc.video.api.dto.PromptUpdateRequest;
import com.aigc.video.api.dto.TaskNode;
import com.aigc.video.task.TaskService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 提供应用的 HTTP 接口。
 */
@RestController
@RequestMapping("/api/tasks/{taskId}")
@RequiredArgsConstructor
public class TaskController {

    private final TaskService taskService;

    @PutMapping("/prompt")
    public TaskNode updatePrompt(@PathVariable Long taskId,
                                 @RequestBody PromptUpdateRequest request) {
        return taskService.updatePrompt(taskId, request);
    }

    @PostMapping("/retry")
    public TaskNode retry(@PathVariable Long taskId) {
        return taskService.retry(taskId);
    }

    @PostMapping("/cancel")
    public TaskNode cancel(@PathVariable Long taskId) {
        return taskService.cancel(taskId);
    }

    @DeleteMapping
    public Map<String, Object> delete(@PathVariable Long taskId) {
        taskService.deleteTask(taskId);
        return Map.of("ok", true);
    }
}
