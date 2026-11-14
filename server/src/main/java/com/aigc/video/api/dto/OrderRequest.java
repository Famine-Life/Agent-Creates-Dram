package com.aigc.video.api.dto;

import java.util.List;

/** 拖拽排序后批量保存分组与镜号 */
public record OrderRequest(List<OrderItem> items) {

    public record OrderItem(Long taskId, String groupId, Integer shotIndex) {
    }
}
