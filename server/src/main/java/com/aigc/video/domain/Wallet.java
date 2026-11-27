package com.aigc.video.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 积分钱包：可用与冻结分列。 */
@Data
@TableName("wallet")
public class Wallet {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private Integer availableCredits;

    private Integer frozenCredits;

    private Integer version;

    private LocalDateTime updatedAt;
}
