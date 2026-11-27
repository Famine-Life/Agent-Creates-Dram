package com.aigc.video.workspace;

import com.aigc.video.common.NotFoundException;
import com.aigc.video.domain.Workspace;
import com.aigc.video.mapper.WorkspaceMapper;
import com.aigc.video.user.UserContext;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** 统一的画布归属校验：跨用户访问一律 404。 */
@Component
@RequiredArgsConstructor
public class WorkspaceAccess {

    private final WorkspaceMapper workspaceMapper;
    private final ProjectService projectService;

    /** 校验画布归属并返回，越权访问统一按不存在处理。 */
    public Workspace requireOwned(Long workspaceId) {
        Long userId = UserContext.currentUserId();
        if (userId == null) {
            throw new NotFoundException("画布不存在：" + workspaceId);
        }
        Workspace workspace = workspaceId == null ? null : workspaceMapper.selectById(workspaceId);
        // 迁移完成前可能存在 user_id 为空的历史行，只允许管理员访问
        boolean owned = workspace != null && (userId.equals(workspace.getUserId())
                || (workspace.getUserId() == null && UserContext.isAdmin()));
        if (!owned) {
            throw new NotFoundException("画布不存在：" + workspaceId);
        }
        return workspace;
    }

    /** 校验归属并返回画布 ID。 */
    public Long requireOwnedId(Long workspaceId) {
        return requireOwned(workspaceId).getId();
    }

    /** 显式传入画布时校验归属；未传时回退当前用户第一个画布。 */
    public Long resolveOrDefault(Long workspaceId) {
        return workspaceId == null || workspaceId <= 0 ? firstOwnedId() : requireOwnedId(workspaceId);
    }

    /** 当前用户的第一个画布，CLI 与上传接口未显式指定时使用。 */
    public Long firstOwnedId() {
        Long userId = UserContext.requireUserId();
        Workspace workspace = workspaceMapper.selectOne(Wrappers.<Workspace>lambdaQuery()
                .eq(Workspace::getUserId, userId)
                .orderByAsc(Workspace::getId)
                .last("LIMIT 1"));
        if (workspace != null) {
            return workspace.getId();
        }
        Workspace created = new Workspace();
        created.setUserId(userId);
        created.setProjectId(projectService.defaultProjectId(userId));
        created.setName("画布 1");
        created.setCreatedAt(java.time.LocalDateTime.now());
        workspaceMapper.insert(created);
        return created.getId();
    }
}
