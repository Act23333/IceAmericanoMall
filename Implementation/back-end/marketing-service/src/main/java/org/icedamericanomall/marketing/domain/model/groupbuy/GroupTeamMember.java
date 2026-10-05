package org.icedamericanomall.marketing.domain.model.groupbuy;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 拼团团成员聚合（纯 POJO）—— {@code group_team_member} 表（F13 拼团，V5.14，ADR-049）。
 *
 * <p>成员行是参团事实的落库锚点，双唯一键兜幂等（ADR-049）：{@code uk_order_no}
 * 一单一团一次（一单一次退款的天花板）、{@code uk_team_user} 防同团重复参团；
 * 团长亦入列（开团成员行）。经 {@code teamId} 关联团（聚合间 ID 引用，不持对象引用）。
 */
@Data
@Builder
public class GroupTeamMember {

    private Long id;
    /** 团 id（uk_team_user 组成列） */
    private Long teamId;
    /** 参团用户 user_id（团长亦入列） */
    private Long userId;
    /** 关联拼团订单号（uk_order_no 幂等锚点，orders.order_no） */
    private String orderNo;
    private LocalDateTime createTime;

    /**
     * 参团成员行工厂（开团首个成员/凑员共用）。
     *
     * @param teamId  团 id
     * @param userId  参团用户 user_id
     * @param orderNo 拼团订单号（幂等锚点）
     * @return 未落库的成员聚合
     */
    public static GroupTeamMember join(Long teamId, Long userId, String orderNo) {
        return GroupTeamMember.builder()
                .teamId(teamId)
                .userId(userId)
                .orderNo(orderNo)
                .build();
    }
}
