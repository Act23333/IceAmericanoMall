package org.icedamericanomall.user.adapter.response;

import lombok.Data;
import org.icedamericanomall.user.infrastructure.persistence.points.PointsLogDO;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 积分明细视图对象 —— 替代原直接返回 DO 的报文（字段与重构前完全一致）。
 */
@Data
public class PointsLogVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;

    private Long userId;

    private Integer points;

    /** 1=签到, 2=下单, 3=任务, 4=兑换消耗, 5=过期 */
    private Integer type;

    private String source;

    private LocalDateTime createTime;

    /** 持久化对象 → 视图对象。 */
    public static PointsLogVO from(PointsLogDO dataObject) {
        PointsLogVO vo = new PointsLogVO();
        vo.setId(dataObject.getId());
        vo.setUserId(dataObject.getUserId());
        vo.setPoints(dataObject.getPoints());
        vo.setType(dataObject.getType());
        vo.setSource(dataObject.getSource());
        vo.setCreateTime(dataObject.getCreateTime());
        return vo;
    }
}
