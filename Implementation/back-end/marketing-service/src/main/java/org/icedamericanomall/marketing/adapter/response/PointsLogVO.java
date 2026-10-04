package org.icedamericanomall.marketing.adapter.response;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 积分明细视图对象（适配层出参）—— 报文形状与迁移前（user-service 的 PointsLogVO）保持一致。
 */
@Data
public class PointsLogVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;

    private Long userId;

    /** 积分变动（正=获得，负=消耗） */
    private Integer points;

    /** 类型：1=签到, 2=下单, 3=任务, 4=兑换消耗, 5=过期 */
    private Integer type;

    private String source;

    private LocalDateTime createTime;
}
