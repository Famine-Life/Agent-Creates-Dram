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

    /** 主键 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 档位编码：FREE、PRO、STUDIO */
    private String code;

    /** 档位展示名称 */
    private String name;

    /** 档位说明 */
    private String description;

    /** 价格（分） */
    private Integer priceCents;

    /** 单次购买有效天数 */
    private Integer durationDays;

    /** 每日积分额度，0 表示不限 */
    private Integer dailyCredits;

    /** 允许的最大并发任务数 */
    private Integer maxConcurrent;

    /** 允许的最高清晰度 */
    private String maxResolution;

    /** 调度权重，越高越优先 */
    private Integer dispatchWeight;

    /** 是否在售 */
    private Boolean enabled;

    /** 排序值，越小越靠前 */
    private Integer sortOrder;

    /** 更新时间 */
    private LocalDateTime updatedAt;
}
