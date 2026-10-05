package org.icedamericanomall.marketing.adapter.response;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 拼团活动视图对象（适配层出参）—— 管理端 AD54 创建回执 + C 端 M15 列表/M16 详情口径
 * （05-API-Specification §4.10.4/§4.13.8d）。
 *
 * <p>{@code status} 为时间窗推导状态在写入时刻的快照（1未开始/2进行中/3已结束）；
 * 不透出技术字段（{@code version} 等）。{@code teams} 仅 M16 详情填充
 * （最近进行中团 ≤10，元素不含他人 userId，防身份泄露）。
 */
@Data
public class GroupBuyActivityVO implements Serializable {
    private static final long serialVersionUID = 1L;

    private Long id;
    private String name;
    private Long sellerId;
    private Long productId;
    private Long skuId;
    /** 拼团价（分） */
    private Integer groupPrice;
    /** 成团人数 */
    private Integer requiredMembers;
    /** 成团时限（小时） */
    private Integer timeLimitHours;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private Integer status;

    /** 最近进行中团列表（仅 M16 详情填充，≤10；列表/管理端口径为 null）。 */
    private List<GroupTeamVO> teams;
}
