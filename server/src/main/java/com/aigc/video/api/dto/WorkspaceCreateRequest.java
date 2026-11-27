package com.aigc.video.api.dto;

/** 新建画布请求：指定所属项目；省略时归入当前用户的默认项目。 */
public record WorkspaceCreateRequest(Long projectId) {
}
