package com.aigc.video.workspace;

import com.aigc.video.api.dto.ProjectView;
import com.aigc.video.common.BadRequestException;
import com.aigc.video.common.NotFoundException;
import com.aigc.video.domain.Canvas;
import com.aigc.video.domain.Project;
import com.aigc.video.domain.Workspace;
import com.aigc.video.mapper.CanvasMapper;
import com.aigc.video.mapper.ProjectMapper;
import com.aigc.video.mapper.WorkspaceMapper;
import com.aigc.video.user.UserContext;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** 用户项目管理：项目是画布的分组，一个项目下可以有多张画布。 */
@Service
@RequiredArgsConstructor
public class ProjectService {

    private final ProjectMapper projectMapper;
    private final WorkspaceMapper workspaceMapper;
    private final CanvasMapper canvasMapper;

    /** 当前用户的项目列表，附带画布数量与最近更新时间。 */
    public List<ProjectView> list() {
        Long userId = UserContext.requireUserId();
        List<Project> projects = projectMapper.selectList(Wrappers.<Project>lambdaQuery()
                .eq(Project::getUserId, userId)
                .orderByAsc(Project::getId));
        if (projects.isEmpty()) {
            return List.of();
        }
        List<Long> projectIds = projects.stream().map(Project::getId).toList();
        List<Workspace> workspaces = workspaceMapper.selectList(Wrappers.<Workspace>lambdaQuery()
                .select(Workspace::getId, Workspace::getProjectId)
                .eq(Workspace::getUserId, userId)
                .in(Workspace::getProjectId, projectIds));
        Map<Long, LocalDateTime> canvasUpdated = canvasUpdatedAt(workspaces);

        Map<Long, Long> counts = new HashMap<>();
        Map<Long, LocalDateTime> latest = new HashMap<>();
        for (Workspace workspace : workspaces) {
            Long projectId = workspace.getProjectId();
            counts.merge(projectId, 1L, Long::sum);
            LocalDateTime updated = canvasUpdated.get(workspace.getId());
            if (updated != null) {
                latest.merge(projectId, updated, (a, b) -> a.isAfter(b) ? a : b);
            }
        }
        return projects.stream()
                .map(project -> new ProjectView(
                        project.getId(),
                        project.getName(),
                        counts.getOrDefault(project.getId(), 0L),
                        latest(project.getUpdatedAt(), latest.get(project.getId()))))
                .toList();
    }

    /** 单个项目，用于项目画布页头部信息。 */
    public ProjectView viewOf(Long projectId) {
        Project project = requireOwned(projectId);
        List<Workspace> workspaces = workspaceMapper.selectList(Wrappers.<Workspace>lambdaQuery()
                .select(Workspace::getId)
                .eq(Workspace::getProjectId, projectId));
        Map<Long, LocalDateTime> canvasUpdated = canvasUpdatedAt(workspaces);
        LocalDateTime latest = project.getUpdatedAt();
        for (Workspace workspace : workspaces) {
            latest = latest(latest, canvasUpdated.get(workspace.getId()));
        }
        return new ProjectView(project.getId(), project.getName(), workspaces.size(), latest);
    }

    /** 新建项目；未提供名称时按序号生成。 */
    @Transactional
    public ProjectView create(String name) {
        Long userId = UserContext.requireUserId();
        String trimmed = normalize(name, false);
        if (trimmed == null) {
            long count = projectMapper.selectCount(Wrappers.<Project>lambdaQuery()
                    .eq(Project::getUserId, userId));
            trimmed = "项目 " + (count + 1);
        }
        Project project = new Project();
        project.setUserId(userId);
        project.setName(trimmed);
        project.setCreatedAt(LocalDateTime.now());
        project.setUpdatedAt(LocalDateTime.now());
        projectMapper.insert(project);
        return new ProjectView(project.getId(), project.getName(), 0L, project.getUpdatedAt());
    }

    /** 重命名项目。 */
    @Transactional
    public void rename(Long projectId, String name) {
        Project project = requireOwned(projectId);
        String trimmed = normalize(name, true);
        projectMapper.update(null, Wrappers.<Project>lambdaUpdate()
                .eq(Project::getId, project.getId())
                .set(Project::getName, trimmed)
                .set(Project::getUpdatedAt, LocalDateTime.now()));
    }

    /** 删除空项目；项目下仍有画布时拒绝，避免误删整组画布。 */
    @Transactional
    public void delete(Long projectId) {
        Project project = requireOwned(projectId);
        long count = workspaceMapper.selectCount(Wrappers.<Workspace>lambdaQuery()
                .eq(Workspace::getProjectId, project.getId()));
        if (count > 0) {
            throw new BadRequestException("项目下还有画布，请先删除画布后再删除项目");
        }
        projectMapper.deleteById(project.getId());
    }

    /** 校验项目归属，跨用户访问统一 404。 */
    public Project requireOwned(Long projectId) {
        Long userId = UserContext.currentUserId();
        if (userId == null || projectId == null) {
            throw new NotFoundException("项目不存在：" + projectId);
        }
        Project project = projectMapper.selectById(projectId);
        if (project == null || !userId.equals(project.getUserId())) {
            throw new NotFoundException("项目不存在：" + projectId);
        }
        return project;
    }

    /** 当前用户的默认项目：没有项目时自动创建一个，供新建画布、CLI 与 MCP 兜底。 */
    @Transactional
    public Long defaultProjectId(Long userId) {
        Project project = projectMapper.selectOne(Wrappers.<Project>lambdaQuery()
                .eq(Project::getUserId, userId)
                .orderByAsc(Project::getId)
                .last("LIMIT 1"));
        if (project != null) {
            return project.getId();
        }
        return createDefault(userId).getId();
    }

    /** 启动时把历史画布归入各用户的默认项目。 */
    @Transactional
    public int backfillDefaultProjects() {
        List<Workspace> pending = workspaceMapper.selectList(Wrappers.<Workspace>lambdaQuery()
                .select(Workspace::getId, Workspace::getUserId)
                .isNull(Workspace::getProjectId));
        Map<Long, List<Long>> byUser = pending.stream()
                .filter(workspace -> workspace.getUserId() != null)
                .collect(Collectors.groupingBy(Workspace::getUserId,
                        Collectors.mapping(Workspace::getId, Collectors.toList())));
        int updated = 0;
        for (Map.Entry<Long, List<Long>> entry : byUser.entrySet()) {
            Long projectId = defaultProjectId(entry.getKey());
            updated += workspaceMapper.update(null, Wrappers.<Workspace>lambdaUpdate()
                    .in(Workspace::getId, entry.getValue())
                    .set(Workspace::getProjectId, projectId));
        }
        return updated;
    }

    private Project createDefault(Long userId) {
        Project project = new Project();
        project.setUserId(userId);
        project.setName("默认项目");
        project.setCreatedAt(LocalDateTime.now());
        project.setUpdatedAt(LocalDateTime.now());
        projectMapper.insert(project);
        return project;
    }

    /** 画布最近更新时间：workspaceId -> canvas.updatedAt */
    private Map<Long, LocalDateTime> canvasUpdatedAt(List<Workspace> workspaces) {
        if (workspaces.isEmpty()) {
            return Map.of();
        }
        List<Long> workspaceIds = workspaces.stream().map(Workspace::getId).toList();
        return canvasMapper.selectList(Wrappers.<Canvas>lambdaQuery()
                        .select(Canvas::getWorkspaceId, Canvas::getUpdatedAt)
                        .in(Canvas::getWorkspaceId, workspaceIds))
                .stream()
                .filter(canvas -> canvas.getUpdatedAt() != null)
                .collect(Collectors.toMap(Canvas::getWorkspaceId, Canvas::getUpdatedAt, (a, b) -> a));
    }

    private LocalDateTime latest(LocalDateTime a, LocalDateTime b) {
        if (a == null) return b;
        if (b == null) return a;
        return a.isAfter(b) ? a : b;
    }

    private String normalize(String name, boolean required) {
        String trimmed = name == null ? "" : name.trim();
        if (trimmed.isEmpty()) {
            if (required) {
                throw new BadRequestException("项目名称不能为空");
            }
            return null;
        }
        if (trimmed.length() > 128) {
            throw new BadRequestException("项目名称不能超过 128 个字符");
        }
        return trimmed;
    }
}
