package org.icedamericanomall.user.adapter.response;

import lombok.Data;
import org.icedamericanomall.user.domain.model.oplog.OperationLog;

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

    /** 领域模型 → 视图对象（PO 不出基础设施层，ADR-003/038）。 */
    public static OperationLogVO from(OperationLog log) {
        OperationLogVO vo = new OperationLogVO();
        vo.setId(log.getId());
        vo.setUserId(log.getUserId());
        vo.setUsername(log.getUsername());
        vo.setAction(log.getAction());
        vo.setTarget(log.getTarget());
        vo.setDetail(log.getDetail());
        vo.setIp(log.getIp());
        vo.setCreateTime(log.getCreateTime());
        return vo;
    }
}
