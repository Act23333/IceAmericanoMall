package org.icedamericanomall.marketing.infrastructure.persistence.points;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 积分账本表映射对象（PO）—— {@code points_log} 单表流水（无跨聚合不变量），
 * 写为插入、读为按 userId 聚合；仅存在于基础设施持久化层。
 *
 * <p>表所有权随积分能力自 user-service 迁入本服务（ADR-016 收尾，表名不变，无数据迁移）。
 * type：1=签到, 2=下单, 3=任务, 4=兑换消耗, 5=过期。
 */
@Data
@TableName("points_log")
public class PointsLogPO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    /** 积分变动（正=获得，负=消耗） */
    private Integer points;

    /** 类型：1=签到, 2=下单, 3=任务, 4=兑换消耗, 5=过期 */
    private Integer type;

    /** 来源描述 */
    private String source;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
