package com.aigc.video.workspace;

import com.aigc.video.common.BadRequestException;
import com.aigc.video.common.NotFoundException;
import com.aigc.video.domain.Canvas;
import com.aigc.video.domain.CanvasImage;
import com.aigc.video.domain.CanvasNode;
import com.aigc.video.domain.GenerationTask;
import com.aigc.video.domain.PromptRecord;
import com.aigc.video.domain.TaskImageRef;
import com.aigc.video.domain.Workspace;
import com.aigc.video.mapper.CanvasImageMapper;
import com.aigc.video.mapper.CanvasMapper;
import com.aigc.video.mapper.CanvasNodeMapper;
import com.aigc.video.mapper.GenerationTaskMapper;
import com.aigc.video.mapper.PromptRecordMapper;
import com.aigc.video.mapper.TaskImageRefMapper;
import com.aigc.video.mapper.WorkspaceMapper;
import com.aigc.video.task.TaskService;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:workspace-delete-test;MODE=MySQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE;DB_CLOSE_DELAY=-1",
        "aigc.ai.providers.mock.video-seconds=3600"
})
class WorkspaceServiceTest {

    @Autowired
    private WorkspaceService workspaceService;
    @Autowired
    private TaskService taskService;
    @Autowired
    private WorkspaceMapper workspaceMapper;
    @Autowired
    private CanvasMapper canvasMapper;
    @Autowired
    private GenerationTaskMapper taskMapper;
    @Autowired
    private PromptRecordMapper promptRecordMapper;
    @Autowired
    private CanvasImageMapper imageMapper;
    @Autowired
    private CanvasNodeMapper nodeMapper;
    @Autowired
    private TaskImageRefMapper refMapper;

    @Test
    void renamesAndCopiesAggregate() {
        Workspace workspace = new Workspace();
        workspace.setName("源画布");
        workspace.setCreatedAt(LocalDateTime.now());
        workspaceMapper.insert(workspace);
        Long sourceId = workspace.getId();

        Canvas canvas = new Canvas();
        canvas.setWorkspaceId(sourceId);
        canvas.setVersion(3);
        canvas.setUpdatedAt(LocalDateTime.now());
        canvasMapper.insert(canvas);

        CanvasImage image = new CanvasImage();
        image.setWorkspaceId(sourceId);
        image.setCanvasId(canvas.getId());
        image.setUrl("/files/ref.png");
        image.setSource("UPLOAD");
        image.setStatus("SUCCEEDED");
        imageMapper.insert(image);

        GenerationTask task = new GenerationTask();
        task.setWorkspaceId(sourceId);
        task.setCanvasId(canvas.getId());
        task.setPrompt("原镜头");
        task.setModel("ark/doubao-seedance-2-0-fast-260128");
        task.setProvider("ark");
        task.setProviderTaskId("ark-1");
        task.setIdemKey("idem-1");
        task.setStatus("RUNNING");
        task.setProgress(40);
        taskMapper.insert(task);

        TaskImageRef ref = new TaskImageRef();
        ref.setTaskId(task.getId());
        ref.setImageId(image.getId());
        ref.setSortIndex(0);
        refMapper.insert(ref);

        PromptRecord record = new PromptRecord();
        record.setWorkspaceId(sourceId);
        record.setTaskId(task.getId());
        record.setPrompt("原镜头");
        promptRecordMapper.insert(record);

        CanvasNode node = new CanvasNode();
        node.setWorkspaceId(sourceId);
        node.setNodeType(CanvasNode.TYPE_TEXT);
        node.setContent("文本内容");
        node.setStatus("RUNNING");
        nodeMapper.insert(node);

        // 快照里的坐标与连线按节点 ID 记录，复制画布后需要重映射
        String sourceSnapshot = "{\"positions\":{\"shot-" + task.getId() + "\":{\"x\":10,\"y\":20},"
                + "\"image-" + image.getId() + "\":{\"x\":30,\"y\":40},"
                + "\"node-" + node.getId() + "\":{\"x\":50,\"y\":60}},"
                + "\"links\":[{\"id\":\"l1\",\"source\":\"node-" + node.getId()
                + "\",\"target\":\"shot-" + task.getId() + "\"}],"
                + "\"viewport\":{\"x\":1,\"y\":2,\"zoom\":1}}";
        canvas.setSnapshot(sourceSnapshot);
        canvasMapper.updateById(canvas);

        workspaceService.renameWorkspace(sourceId, "  新名字  ");
        assertEquals("新名字", workspaceMapper.selectById(sourceId).getName());
        assertThrows(BadRequestException.class, () -> workspaceService.renameWorkspace(sourceId, "   "));
        assertThrows(NotFoundException.class, () -> workspaceService.renameWorkspace(999999L, "x"));

        Workspace copy = workspaceService.copyWorkspace(sourceId);
        Long copyId = copy.getId();
        assertNotEquals(sourceId, copyId);

        Canvas copiedCanvas = canvasMapper.selectOne(Wrappers.<Canvas>lambdaQuery()
                .eq(Canvas::getWorkspaceId, copyId));
        assertNotNull(copiedCanvas);
        assertEquals(0, copiedCanvas.getVersion());

        List<CanvasImage> copiedImages = imageMapper.selectList(Wrappers.<CanvasImage>lambdaQuery()
                .eq(CanvasImage::getWorkspaceId, copyId));
        assertEquals(1, copiedImages.size());
        assertNotEquals(image.getId(), copiedImages.get(0).getId());

        List<GenerationTask> copiedTasks = taskMapper.selectList(Wrappers.<GenerationTask>lambdaQuery()
                .eq(GenerationTask::getWorkspaceId, copyId));
        assertEquals(1, copiedTasks.size());
        GenerationTask copiedTask = copiedTasks.get(0);
        assertEquals("原镜头", copiedTask.getPrompt());
        // 进行中的任务复制后落为终态，且不继承供应商任务号，避免继续轮询原任务
        assertEquals("CANCELED", copiedTask.getStatus());
        assertNull(copiedTask.getProviderTaskId());
        assertNull(copiedTask.getIdemKey());

        assertEquals(1, refMapper.selectCount(Wrappers.<TaskImageRef>lambdaQuery()
                .eq(TaskImageRef::getTaskId, copiedTask.getId())));
        assertEquals(copiedImages.get(0).getId(), refMapper.selectList(Wrappers.<TaskImageRef>lambdaQuery()
                .eq(TaskImageRef::getTaskId, copiedTask.getId())).get(0).getImageId());
        assertEquals(1, promptRecordMapper.selectCount(Wrappers.<PromptRecord>lambdaQuery()
                .eq(PromptRecord::getTaskId, copiedTask.getId())));

        List<CanvasNode> copiedNodes = nodeMapper.selectList(Wrappers.<CanvasNode>lambdaQuery()
                .eq(CanvasNode::getWorkspaceId, copyId));
        assertEquals(1, copiedNodes.size());
        assertEquals("文本内容", copiedNodes.get(0).getContent());
        assertEquals("FAILED", copiedNodes.get(0).getStatus());

        // 快照坐标 / 连线重映射到副本的节点 ID
        String copiedSnapshot = copiedCanvas.getSnapshot();
        assertTrue(copiedSnapshot.contains("\"shot-" + copiedTask.getId() + "\":"));
        assertTrue(copiedSnapshot.contains("\"image-" + copiedImages.get(0).getId() + "\":"));
        assertTrue(copiedSnapshot.contains("\"node-" + copiedNodes.get(0).getId() + "\":"));
        assertTrue(copiedSnapshot.contains("\"target\":\"shot-" + copiedTask.getId() + "\""));
        assertFalse(copiedSnapshot.contains("\"shot-" + task.getId() + "\":"));

        // 源画布不受影响
        assertEquals("RUNNING", taskMapper.selectById(task.getId()).getStatus());
        assertEquals("ark-1", taskMapper.selectById(task.getId()).getProviderTaskId());

        workspaceService.deleteWorkspace(copyId);
        workspaceService.deleteWorkspace(sourceId);
    }

    @Test
    void deletesAggregateWithoutAffectingOtherWorkspaces() {
        assertThrows(NotFoundException.class, () -> workspaceService.deleteWorkspace(999999L));
        assertThrows(BadRequestException.class, () -> workspaceService.deleteWorkspace(1L));

        Workspace workspace = new Workspace();
        workspace.setName("待删除画布");
        workspace.setCreatedAt(LocalDateTime.now());
        workspaceMapper.insert(workspace);
        Long workspaceId = workspace.getId();

        Canvas canvas = new Canvas();
        canvas.setWorkspaceId(workspaceId);
        canvas.setVersion(0);
        canvas.setUpdatedAt(LocalDateTime.now());
        canvasMapper.insert(canvas);

        GenerationTask deletedTask = new GenerationTask();
        deletedTask.setWorkspaceId(workspaceId);
        deletedTask.setCanvasId(canvas.getId());
        deletedTask.setPrompt("待删除镜头");
        deletedTask.setStatus("SUCCEEDED");
        taskMapper.insert(deletedTask);

        GenerationTask retainedTask = new GenerationTask();
        retainedTask.setWorkspaceId(1L);
        retainedTask.setCanvasId(1L);
        retainedTask.setPrompt("保留镜头");
        retainedTask.setStatus("SUCCEEDED");
        taskMapper.insert(retainedTask);

        CanvasImage deletedImage = new CanvasImage();
        deletedImage.setWorkspaceId(workspaceId);
        deletedImage.setCanvasId(canvas.getId());
        deletedImage.setUrl("/files/deleted.png");
        imageMapper.insert(deletedImage);

        CanvasImage retainedImage = new CanvasImage();
        retainedImage.setWorkspaceId(1L);
        retainedImage.setCanvasId(1L);
        retainedImage.setUrl("/files/retained.png");
        imageMapper.insert(retainedImage);

        TaskImageRef deletedRef = new TaskImageRef();
        deletedRef.setTaskId(deletedTask.getId());
        deletedRef.setImageId(deletedImage.getId());
        deletedRef.setSortIndex(0);
        refMapper.insert(deletedRef);

        TaskImageRef retainedRef = new TaskImageRef();
        retainedRef.setTaskId(retainedTask.getId());
        retainedRef.setImageId(retainedImage.getId());
        retainedRef.setSortIndex(0);
        refMapper.insert(retainedRef);

        PromptRecord deletedPrompt = new PromptRecord();
        deletedPrompt.setWorkspaceId(workspaceId);
        deletedPrompt.setTaskId(deletedTask.getId());
        deletedPrompt.setPrompt("待删除提示词");
        promptRecordMapper.insert(deletedPrompt);

        PromptRecord retainedPrompt = new PromptRecord();
        retainedPrompt.setWorkspaceId(1L);
        retainedPrompt.setTaskId(retainedTask.getId());
        retainedPrompt.setPrompt("保留提示词");
        promptRecordMapper.insert(retainedPrompt);

        CanvasNode deletedNode = new CanvasNode();
        deletedNode.setWorkspaceId(workspaceId);
        deletedNode.setNodeType(CanvasNode.TYPE_TEXT);
        nodeMapper.insert(deletedNode);

        CanvasNode retainedNode = new CanvasNode();
        retainedNode.setWorkspaceId(1L);
        retainedNode.setNodeType(CanvasNode.TYPE_TEXT);
        nodeMapper.insert(retainedNode);

        workspaceService.deleteWorkspace(workspaceId);

        assertNull(workspaceMapper.selectById(workspaceId));
        assertEquals(0, canvasMapper.selectCount(Wrappers.<Canvas>lambdaQuery()
                .eq(Canvas::getWorkspaceId, workspaceId)));
        assertEquals(0, taskMapper.selectCount(Wrappers.<GenerationTask>lambdaQuery()
                .eq(GenerationTask::getWorkspaceId, workspaceId)));
        assertEquals(0, promptRecordMapper.selectCount(Wrappers.<PromptRecord>lambdaQuery()
                .eq(PromptRecord::getWorkspaceId, workspaceId)));
        assertEquals(0, imageMapper.selectCount(Wrappers.<CanvasImage>lambdaQuery()
                .eq(CanvasImage::getWorkspaceId, workspaceId)));
        assertEquals(0, nodeMapper.selectCount(Wrappers.<CanvasNode>lambdaQuery()
                .eq(CanvasNode::getWorkspaceId, workspaceId)));
        assertNull(refMapper.selectById(deletedRef.getId()));

        assertNotNull(workspaceMapper.selectById(1L));
        assertNotNull(taskMapper.selectById(retainedTask.getId()));
        assertNotNull(promptRecordMapper.selectById(retainedPrompt.getId()));
        assertNotNull(imageMapper.selectById(retainedImage.getId()));
        assertNotNull(nodeMapper.selectById(retainedNode.getId()));
        assertNotNull(refMapper.selectById(retainedRef.getId()));

        assertThrows(NotFoundException.class, () -> taskService.getCanvas(workspaceId));
        assertNull(workspaceMapper.selectById(workspaceId));
        assertThrows(BadRequestException.class, () -> workspaceService.deleteWorkspace(1L));
    }
}
