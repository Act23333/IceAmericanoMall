package org.icedamericanomall.user.infrastructure.persistence.oplog;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 操作日志 PO —— 对应 {@code operation_log} 表。
 *
 * <p>当前仅被读取（管理后台分页查询）；原 ia-common 的 {@code OperationLogAspect} 已移除，
 * 该表暂无写入方。
 */
@Data
@TableName("operation_log")
public class OperationLogPO implements Serializable {
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
