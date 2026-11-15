package com.aigc.video.api;

import com.aigc.video.api.dto.WorkspaceView;
import com.aigc.video.domain.Canvas;
import com.aigc.video.domain.Workspace;
import com.aigc.video.mapper.CanvasMapper;
import com.aigc.video.mapper.WorkspaceMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 画布（工作空间）列表与新建。
 * 一张画布 = 一条 workspace 记录 + 一条 canvas 快照记录，前端按 workspace.id 切换。
 */
@RestController
@RequestMapping("/api/workspaces")
@RequiredArgsConstructor
public class WorkspaceController {

    /** 工作空间表操作：列表查询与新建 */
    private final WorkspaceMapper workspaceMapper;
    /** 画布快照表操作：只取版本号 / 更新时间，避免返回体积很大的 snapshot */
    private final CanvasMapper canvasMapper;

    /** 画布列表：按 ID 升序返回，附带各自 canvas 的版本与更新时间 */
    @GetMapping
    public List<WorkspaceView> list() {
        List<Workspace> workspaces = workspaceMapper.selectList(
                Wrappers.<Workspace>lambdaQuery().orderByAsc(Workspace::getId));
        // 显式 select 字段，避免把 snapshot 大字段一起查出来
        Map<Long, Canvas> canvases = canvasMapper.selectList(
                        Wrappers.<Canvas>lambdaQuery()
                                .select(Canvas::getId, Canvas::getWorkspaceId, Canvas::getVersion, Canvas::getUpdatedAt))
                .stream()
                .collect(Collectors.toMap(Canvas::getWorkspaceId, Function.identity(), (a, b) -> a));
        return workspaces.stream()
                .map(workspace -> toView(workspace, canvases.get(workspace.getId())))
                .toList();
    }

    /** 新建空白画布：同时创建 workspace 与 canvas，名称按序号自动生成 */
    @PostMapping
    public WorkspaceView create() {
        // 用现有画布数量 + 1 生成“画布 N”，同名校验不在此处做，重名不影响区分（前端显示 ID 对应项）
        long count = workspaceMapper.selectCount(null);
        Workspace workspace = new Workspace();
        workspace.setName("画布 " + (count + 1));
        workspace.setCreatedAt(LocalDateTime.now());
        workspaceMapper.insert(workspace);

        // 新画布从版本 0、空快照开始
        Canvas canvas = new Canvas();
        canvas.setWorkspaceId(workspace.getId());
        canvas.setVersion(0);
        canvas.setUpdatedAt(LocalDateTime.now());
        canvasMapper.insert(canvas);
        return toView(workspace, canvas);
    }

    /** 组装返回结构：workspace 与 canvas 一一对应，canvas 缺失时返回空版本 */
    private WorkspaceView toView(Workspace workspace, Canvas canvas) {
        return new WorkspaceView(
                workspace.getId(),
                workspace.getName(),
                canvas == null ? null : canvas.getId(),
                canvas == null ? 0 : canvas.getVersion(),
                canvas == null ? null : canvas.getUpdatedAt());
    }
}
