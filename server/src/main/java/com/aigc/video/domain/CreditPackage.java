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

    @TableId(type = IdType.AUTO)
    private Long id;

    private String code;

    private String name;

    private Integer credits;

    private Integer priceCents;

    private Boolean enabled;

    private Integer sortOrder;

    private LocalDateTime updatedAt;
}
