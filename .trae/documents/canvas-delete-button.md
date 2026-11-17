# 画布删除按钮实施计划

## Context

当前左侧画布栏只有画布下拉框和新增按钮，前后端均没有删除整个画布的能力。本次在画布下拉框右侧增加删除按钮，删除当前画布及其镜头、图片、普通节点、连线和提示词记录；删除是不可恢复操作，并至少保留一个画布。

## 实施步骤

1. **增加后端删除能力**
   * 新增 `WorkspaceService.deleteWorkspace(workspaceId)`，使用事务校验画布存在且总数大于 1。
   * 查询当前画布的任务 ID，按顺序删除 `task_image_ref`、`generation_task`、`prompt_record`、`canvas_image`、`canvas_node`、`canvas`、`workspace`，空 ID 集合不执行 `IN` 查询，确保不影响其他画布。
   * 在 `WorkspaceController` 增加 `DELETE /api/workspaces/{id}`；最后一个画布返回明确错误，不存在的画布返回 404。
   * 删除成功后通过现有 `CanvasSocketRegistry` 广播 `workspace.deleted`，供其他标签页切换画布。

2. **防止已删画布被旧请求重建**
   * 调整 `TaskService.requireCanvas` 与 `CanvasNodeService` 的画布校验：workspace 已不存在时返回 404，不再由迟到的读取或保存请求静默重建。
   * 保留已有 workspace 下缺失 canvas 快照时的兼容处理，不改变正常新建和任务流程。

3. **实现前端删除与切换**
   * `web/src/api.js` 增加 `deleteWorkspace`；`store/canvas.js` 处理 `workspace.deleted` 事件并通知 `App.vue`。
   * `App.vue` 增加删除方法：确认画布名称和永久删除提示，防止重复提交；删除前停止定时快照保存并断开当前实时连接，避免把已删画布重新保存回来。
   * 删除成功后刷新画布列表并复用 `openCanvas` 切换到剩余画布；失败时恢复当前画布和实时连接。其他标签页收到删除事件时，当前标签页自动切换，非当前标签页只更新列表。
   * 启动时先校验 localStorage 中的画布 ID，若已失效则选择现有画布，避免刷新后重新创建已删 ID。

4. **添加按钮与样式**
   * 在 `App.vue` 的 `.canvas-bar` 中，将删除按钮放在下拉框和新增按钮右侧；侧栏折叠时隐藏。
   * 复用 `icon-btn`，在 `style.css` 增加危险操作 hover 样式和删除中禁用状态；仅剩一个画布时禁用，并通过 `title` 说明“至少保留一个画布”。
   * 运行中的镜头沿用现有单镜头删除语义随画布删除，确认框明确所有内容将永久删除。

## 关键文件

* `server/src/main/java/com/aigc/video/api/WorkspaceController.java`
* `server/src/main/java/com/aigc/video/workspace/WorkspaceService.java`（新增）
* `server/src/main/java/com/aigc/video/task/TaskService.java`
* `server/src/main/java/com/aigc/video/task/CanvasNodeService.java`
* `web/src/api.js`
* `web/src/store/canvas.js`
* `web/src/App.vue`
* `web/src/style.css`
* `server/src/test/java/com/aigc/video/workspace/WorkspaceServiceTest.java`（新增）

## 验证

1. 为删除服务补充测试：完整级联清理、其他画布数据不受影响、最后一个画布禁止删除、不存在返回 404。
2. 执行后端 `mvn test`（JDK 17）和前端 `npm run build`。
3. 重新构建并用 `local` 配置启动后端，手动新建临时画布并添加节点后删除，确认列表、下拉框、当前页面和 localStorage 均切换到剩余画布。
4. 验证最后一个画布按钮禁用、删除失败可继续编辑，以及两个标签页打开同一画布时删除后的自动切换。
