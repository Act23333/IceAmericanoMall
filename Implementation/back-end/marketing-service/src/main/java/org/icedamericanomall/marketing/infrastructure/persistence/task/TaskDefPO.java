package org.icedamericanomall.marketing.infrastructure.persistence.task;

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
 * 任务定义表映射对象（PO）—— {@code task_def}（V5.10 快照，ADR-047），仅存在于基础设施持久化层。
 *
 * <p>{@code code} 业务唯一键（{@code uk_code}）；{@code update_time} 由 DB
 * {@code ON UPDATE CURRENT_TIMESTAMP} 维护，PO 不映射。
 */
@Data
@TableName("task_def")
public class TaskDefPO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 任务编码（uk_code，业务唯一键） */
    private String code;

    /** 任务名称 */
    private String name;

    /** 类型：1=每日重置（Phase 1 唯一值） */
    private Integer type;

    /** 完成奖励积分 */
    private Integer rewardPoints;

    /** 状态：1=启用 0=停用 */
    private Integer status;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
