package org.icedAmericanoMall.domain.vo;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 操作日志视图对象。
 */
@Data
public class OperationLogVO implements Serializable {
    private static final long serialVersionUID = 1L;

    private Long id;
    private Long userId;
    private String username;
    private String action;
    private String target;
    private String detail;
    private String ip;
    private LocalDateTime createTime;
}
