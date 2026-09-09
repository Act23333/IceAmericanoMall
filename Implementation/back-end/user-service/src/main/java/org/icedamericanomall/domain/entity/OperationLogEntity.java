package org.icedamericanomall.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 操作日志 DO —— 对应 operation_log 表，由 ia-common 的 OperationLogAspect 写入。
 */
@Data
@TableName("operation_log")
public class OperationLogEntity implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String username;
    private String action;
    private String target;
    private String detail;
    private String ip;
    private LocalDateTime createTime;
}
