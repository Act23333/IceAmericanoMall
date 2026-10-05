package org.icedamericanomall.marketing.infrastructure.persistence.task;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 任务完成记录表映射对象（PO）—— {@code task_record}（V5.10 快照，ADR-047），
 * 仅存在于基础设施持久化层。
 *
 * <p>幂等锚点：{@code uk_user_task_date(user_id, task_code, biz_date)}，并发重复插入
 * 抛 {@code DuplicateKeyException} 由应用层回落。
 */
@Data
@TableName("task_record")
public class TaskRecordPO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 用户 user_id */
    private Long userId;

    /** 任务编码 */
    private String taskCode;

    /** 实发奖励积分 */
    private Integer rewardPoints;

    /** 业务日（每日重置锚点） */
    private LocalDate bizDate;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
