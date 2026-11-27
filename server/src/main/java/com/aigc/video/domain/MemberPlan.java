package com.aigc.video.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 会员档位与权益。 */
@Data
@TableName("member_plan")
public class MemberPlan {

    public static final String FREE = "FREE";
    public static final String PRO = "PRO";
    public static final String STUDIO = "STUDIO";

    @TableId(type = IdType.AUTO)
    private Long id;

    private String code;

    private String name;

    private String description;

    private Integer priceCents;

    private Integer durationDays;

    private Integer dailyCredits;

    private Integer maxConcurrent;

    private String maxResolution;

    private Integer dispatchWeight;

    private Boolean enabled;

    private Integer sortOrder;

    private LocalDateTime updatedAt;
}
