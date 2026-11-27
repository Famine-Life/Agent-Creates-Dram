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

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private String periodType;

    private String periodKey;

    private Integer calls;

    private Integer credits;

    private LocalDateTime updatedAt;
}
