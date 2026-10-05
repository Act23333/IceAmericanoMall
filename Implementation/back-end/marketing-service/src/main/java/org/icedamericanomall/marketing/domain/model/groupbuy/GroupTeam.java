package org.icedamericanomall.marketing.domain.model.groupbuy;

import lombok.Builder;
import lombok.Data;
import org.icedamericanomall.marketing.domain.enums.GroupTeamStatusEnum;

import java.time.LocalDateTime;

/**
 * 拼团团聚合（纯 POJO）—— {@code group_team} 表（F13 拼团，V5.14，ADR-049）。
 *
 * <p>成团状态机（1拼团中/2已成团/3已失败）：开团（团长即首个成员，{@code currentMembers=1}）
 * → 凑员 → 满员置已成团 / 超时置已失败。参团人数的条件推进由仓储承载
 * （{@code current_members} 条件更新 +1、满员条件置 2、过期条件置 3）——
 * 聚合持有状态与判定，竞态兜底归 SQL 语义，本类不做跨行写操作。
 *
 * <p>{@code requiredMembers} 为开团时刻从活动快照（活动后续改档不影响进行中的团）；
 * {@code teamNo} 为业务唯一键（UUID），团以团号寻址（uk_team_no）。
 */
@Data
@Builder
public class GroupTeam {

    private Long id;
    /** 团号（业务唯一键，UUID） */
    private String teamNo;
    /** 拼团活动 id */
    private Long activityId;
    /** 团长 user_id（开团者） */
    private Long leaderUserId;
    /** 成团人数（开团时从活动快照） */
    private Integer requiredMembers;
    /** 当前人数（已支付入团成员数，含团长） */
    private Integer currentMembers;
    /** 1=拼团中 2=已成团 3=已失败（见 GroupTeamStatusEnum） */
    private Integer status;
    /** 成团截止时间（开团时间 + 活动时限） */
    private LocalDateTime expireTime;
    /** 成团时间（已失败团恒为 null） */
    private LocalDateTime successTime;
    private LocalDateTime createTime;

    /**
     * 开团工厂：团长自占一位（{@code currentMembers=1}），状态=拼团中。
     *
     * <p>成团人数 ≥2 由活动不变式守护（{@code GroupBuyActivity.ensureFieldsLegal}），
     * 故开团瞬间不可能满员；团号与截止时间由应用层生成后传入（领域层保持纯 POJO）。
     *
     * @param activityId      拼团活动 id
     * @param leaderUserId    团长 user_id
     * @param requiredMembers 成团人数（活动快照）
     * @param teamNo          团号（UUID，应用层生成）
     * @param expireTime      成团截止时间（开团时刻 + 活动时限）
     * @return 拼团中状态的新团
     */
    public static GroupTeam open(Long activityId, Long leaderUserId, int requiredMembers,
                                 String teamNo, LocalDateTime expireTime) {
        return GroupTeam.builder()
                .teamNo(teamNo)
                .activityId(activityId)
                .leaderUserId(leaderUserId)
                .requiredMembers(requiredMembers)
                .currentMembers(1)
                .status(GroupTeamStatusEnum.ONGOING.getCode())
                .expireTime(expireTime)
                .build();
    }

    /** 是否拼团中（状态口径，供参团前置校验）。 */
    public boolean isOngoing() {
        return GroupTeamStatusEnum.ONGOING.getCode() == this.status;
    }

    /** 是否已满员（{@code currentMembers >= requiredMembers}）。 */
    public boolean isFull() {
        return this.currentMembers != null && this.requiredMembers != null
                && this.currentMembers >= this.requiredMembers;
    }
}
