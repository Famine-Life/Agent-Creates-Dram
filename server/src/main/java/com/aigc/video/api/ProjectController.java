package com.aigc.video.api;

import com.aigc.video.api.dto.ProjectView;
import com.aigc.video.api.dto.WorkspaceNameRequest;
import com.aigc.video.workspace.ProjectService;
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

/** 项目管理：项目是画布的分组，一个项目下可以有多张画布。 */
@RestController
@RequestMapping("/api/projects")
@RequiredArgsConstructor
public class ProjectController {

    private final ProjectService projectService;

    /** 当前用户的项目列表，附带画布数量与最近更新时间 */
    @GetMapping
    public List<ProjectView> list() {
        return projectService.list();
    }

    /** 单个项目信息，用于项目画布页头部 */
    @GetMapping("/{projectId}")
    public ProjectView view(@PathVariable Long projectId) {
        return projectService.viewOf(projectId);
    }

    /** 新建项目；请求体省略 name 时按序号生成 */
    @PostMapping
    public ProjectView create(@RequestBody(required = false) WorkspaceNameRequest request) {
        return projectService.create(request == null ? null : request.name());
    }

    /** 重命名项目 */
    @PutMapping("/{projectId}")
    public Map<String, Object> rename(@PathVariable Long projectId,
                                      @RequestBody WorkspaceNameRequest request) {
        projectService.rename(projectId, request == null ? null : request.name());
        return Map.of("ok", true);
    }

    /** 删除空项目；项目下仍有画布时返回 400 */
    @DeleteMapping("/{projectId}")
    public Map<String, Object> delete(@PathVariable Long projectId) {
        projectService.delete(projectId);
        return Map.of("ok", true);
    }
}
