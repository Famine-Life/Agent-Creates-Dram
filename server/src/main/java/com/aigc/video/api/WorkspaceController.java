package com.aigc.video.api;

import com.aigc.video.api.dto.WorkspaceNameRequest;
import com.aigc.video.api.dto.WorkspaceView;
import com.aigc.video.domain.Workspace;
import com.aigc.video.workspace.WorkspaceService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 画布（工作空间）列表与新建。
 * 一张画布 = 一条 workspace 记录 + 一条 canvas 快照记录，前端按 workspace.id 切换。
 */
/**
 * 提供应用的 HTTP 接口。
 */
@RestController
@RequestMapping("/api/workspaces")
@RequiredArgsConstructor
public class WorkspaceController {

    private final WorkspaceService workspaceService;

    /** 画布列表：按 ID 升序返回，附带各自 canvas 的版本与更新时间 */
    @GetMapping
    public List<WorkspaceView> list() {
        return workspaceService.list();
    }

    /** 新建空白画布：同时创建 workspace 与 canvas，名称按序号自动生成 */
    @PostMapping
    public WorkspaceView create() {
        return workspaceService.createWorkspace();
    }

    /** 重命名画布 */
    @PutMapping("/{workspaceId}")
    public Map<String, Object> rename(@PathVariable Long workspaceId,
                                      @RequestBody WorkspaceNameRequest request) {
        workspaceService.renameWorkspace(workspaceId, request == null ? null : request.name());
        return Map.of("ok", true);
    }

    /** 复制画布及其全部内容，返回新画布信息供前端切换 */
    @PostMapping("/{workspaceId}/copy")
    public WorkspaceView copy(@PathVariable Long workspaceId) {
        Workspace copy = workspaceService.copyWorkspace(workspaceId);
        return workspaceService.viewOf(copy.getId());
    }

    /** 删除整个画布及其关联数据 */
    @DeleteMapping("/{workspaceId}")
    public Map<String, Object> delete(@PathVariable Long workspaceId) {
        workspaceService.deleteWorkspace(workspaceId);
        return Map.of("ok", true);
    }
}
