package com.aigc.video.task;

import com.aigc.video.api.dto.EdgeRequest;
import com.aigc.video.api.dto.EdgeView;
import com.aigc.video.api.dto.ImageRequest;
import com.aigc.video.api.dto.ShotRequest;
import com.aigc.video.api.dto.TaskNode;
import com.aigc.video.common.BadRequestException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:image-ref-test;MODE=MySQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE;DB_CLOSE_DELAY=-1"
})
class ImageReferencePromptTest {

    private static final long WORKSPACE = 1;

    @Autowired
    private TaskService taskService;

    @Test
    void writesDescriptionAtTheActualReferencePositionAndRetriesWithoutDuplicates() {
        String base = "镜头缓慢推进。\n@图片10：原有说明";
        long task = draft(base);
        long removed = image("旧图", null);
        long setting = image("场景参考", null);
        long character = image("定妆照", "红色风衣女主");
        taskService.createEdge(WORKSPACE, new EdgeRequest(task, removed, null));
        taskService.createEdge(WORKSPACE, new EdgeRequest(task, setting, null));
        taskService.deleteImage(WORKSPACE, removed);
        assertEquals(base, taskService.node(task).prompt());

        EdgeView linked = taskService.createEdgeWithPrompt(WORKSPACE, new EdgeRequest(task, character, null));
        assertEquals(2, linked.sortIndex());
        assertEquals(base + "\n\n@图片2：红色风衣女主", taskService.node(task).prompt());

        String description = "女主定妆照，用于参考人物造型";
        EdgeRequest update = new EdgeRequest(task, character, description);
        assertEquals(linked, taskService.createEdgeWithPrompt(WORKSPACE, update));
        assertEquals(linked, taskService.createEdgeWithPrompt(WORKSPACE, update));
        TaskNode node = taskService.node(task);
        assertEquals(base + "\n\n@图片2：" + description, node.prompt());
        assertEquals(List.of(setting, character), node.imageIds());
        assertEquals("IDLE", node.status());
    }

    @Test
    void rejectsAnOversizedPromptWithoutCreatingTheLink() {
        String base = "景".repeat(2000);
        long task = draft(base);
        long image = image("场景参考", null);

        assertThrows(BadRequestException.class, () ->
                taskService.createEdgeWithPrompt(WORKSPACE, new EdgeRequest(task, image, "场景参考")));
        TaskNode node = taskService.node(task);
        assertEquals(base, node.prompt());
        assertEquals(List.of(), node.imageIds());
    }

    @Test
    void concurrentLinksKeepBothDescriptionsInReferenceOrder() throws Exception {
        long task = draft("");
        long first = image("山谷", null);
        long second = image("木屋", null);

        var one = CompletableFuture.runAsync(() ->
                taskService.createEdgeWithPrompt(WORKSPACE, new EdgeRequest(task, first, null)));
        var two = CompletableFuture.runAsync(() ->
                taskService.createEdgeWithPrompt(WORKSPACE, new EdgeRequest(task, second, null)));
        CompletableFuture.allOf(one, two).get(10, TimeUnit.SECONDS);

        TaskNode node = taskService.node(task);
        List<String> descriptions = node.imageIds().stream()
                .map(id -> id == first ? "山谷" : "木屋").toList();
        assertEquals(2, descriptions.size());
        assertEquals("@图片1：" + descriptions.get(0) + "\n\n@图片2：" + descriptions.get(1), node.prompt());
    }

    private long draft(String prompt) {
        return taskService.createShot(WORKSPACE, new ShotRequest(prompt, null, null, null,
                null, null, "mock", "16:9", 5, "720P", true)).id();
    }

    private long image(String name, String prompt) {
        return taskService.createImage(WORKSPACE, new ImageRequest(name, "https://example.test/photo.png", null,
                null, null, null, null, prompt, null, null, null)).id();
    }
}
