package org.icedamericanomall.user.adapter.response;

import lombok.Data;
import org.icedamericanomall.user.infrastructure.persistence.oplog.OperationLogPO;

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

    /** 持久化对象 → 视图对象。 */
    public static OperationLogVO from(OperationLogPO dataObject) {
        OperationLogVO vo = new OperationLogVO();
        vo.setId(dataObject.getId());
        vo.setUserId(dataObject.getUserId());
        vo.setUsername(dataObject.getUsername());
        vo.setAction(dataObject.getAction());
        vo.setTarget(dataObject.getTarget());
        vo.setDetail(dataObject.getDetail());
        vo.setIp(dataObject.getIp());
        vo.setCreateTime(dataObject.getCreateTime());
        return vo;
    }
}
