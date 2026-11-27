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

    /** 主键 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 用户 ID */
    private Long userId;

    /** 可用积分 */
    private Integer availableCredits;

    /** 冻结积分，生成中任务占用 */
    private Integer frozenCredits;

    /** 乐观锁版本号 */
    private Integer version;

    /** 更新时间 */
    private LocalDateTime updatedAt;
}
