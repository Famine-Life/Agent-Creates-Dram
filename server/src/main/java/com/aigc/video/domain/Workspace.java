package com.aigc.video.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 表示持久化业务数据。
 */
@Data
@TableName("workspace")
public class Workspace {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 所属用户；单用户升级后统一归属当前管理员 */
    private Long userId;

    /** 所属项目；历史数据由启动引导归入默认项目 */
    private Long projectId;

    private String name;

    private LocalDateTime createdAt;
}
