package com.aigc.video.task;

import com.aigc.video.api.dto.PromptUpdateRequest;
import com.aigc.video.api.dto.ShotRequest;
import com.aigc.video.api.dto.TaskNode;
import com.aigc.video.domain.PromptRecord;
import com.aigc.video.mapper.AppUserMapper;
import com.aigc.video.mapper.PromptRecordMapper;
import com.aigc.video.support.TestSecurity;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:prompt-record-test;MODE=MySQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE;DB_CLOSE_DELAY=-1",
        "aigc.ai.providers.mock.video-seconds=3600"
})
class PromptRecordServiceTest {

    @Autowired
    private TaskService taskService;

    @Autowired
    private PromptRecordMapper promptRecordMapper;
    @Autowired
    private AppUserMapper userMapper;

    @BeforeEach
    void login() {
        TestSecurity.loginAdmin(userMapper);
    }

    @AfterEach
    void logout() {
        TestSecurity.clear();
    }

    @Test
    void recordsCreateIdempotentRetryAndPromptUpdate() {
        ShotRequest request = new ShotRequest("测试提示词", "测试镜头", "测试分组", 1,
                null, null, "mock", "16:9", 5, "720P", null);

        TaskNode created = taskService.createShot(1L, request);
        TaskNode idempotent = taskService.createShot(1L, request);
        taskService.updatePrompt(created.id(),
                new PromptUpdateRequest("测试提示词（已修改）", false, null, null, null, null));

        List<PromptRecord> records = promptRecordMapper.selectList(null);
        assertEquals(created.id(), idempotent.id());
        assertEquals(3, records.size());
        assertEquals("测试提示词", records.get(0).getPrompt());
        assertEquals("测试提示词", records.get(1).getPrompt());
        assertEquals("测试提示词（已修改）", records.get(2).getPrompt());
        records.forEach(record -> {
            assertEquals(1L, record.getWorkspaceId());
            assertEquals(created.id(), record.getTaskId());
        });

        List<PromptRecord> latest = taskService.listPromptRecords(1L);
        assertEquals(3, latest.size());
        assertEquals("测试提示词（已修改）", latest.get(0).getPrompt());
    }
}
