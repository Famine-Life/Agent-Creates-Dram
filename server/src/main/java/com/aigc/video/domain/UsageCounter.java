package com.aigc.video.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 日/小时用量计数。 */
@Data
@TableName("usage_counter")
public class UsageCounter {

    public static final String PERIOD_DAY = "DAY";
    public static final String PERIOD_HOUR = "HOUR";

    /** 主键 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 用户 ID */
    private Long userId;

    /** 统计周期类型：按天或按小时 */
    private String periodType;

    /** 周期键，如 20261127 或 2026112714 */
    private String periodKey;

    /** 调用次数 */
    private Integer calls;

    /** 消耗积分 */
    private Integer credits;

    /** 更新时间 */
    private LocalDateTime updatedAt;
}
