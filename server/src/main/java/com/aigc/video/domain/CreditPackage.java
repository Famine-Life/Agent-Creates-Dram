package com.aigc.video.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 积分充值商品。 */
@Data
@TableName("credit_package")
public class CreditPackage {

    /** 主键 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 套餐编码，下单时使用 */
    private String code;

    /** 套餐展示名称 */
    private String name;

    /** 包含积分数量 */
    private Integer credits;

    /** 售价（分） */
    private Integer priceCents;

    /** 是否上架 */
    private Boolean enabled;

    /** 排序值，越小越靠前 */
    private Integer sortOrder;

    /** 更新时间 */
    private LocalDateTime updatedAt;
}
