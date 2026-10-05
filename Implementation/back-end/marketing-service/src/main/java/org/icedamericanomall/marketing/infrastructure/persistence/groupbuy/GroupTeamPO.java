package org.icedamericanomall.marketing.infrastructure.persistence.groupbuy;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 拼团团表映射对象（PO）—— {@code group_team} 表（V5.14，ADR-049），仅存在于基础设施持久化层。
 *
 * <p>{@code team_no} 为业务唯一键（uk_team_no）；{@code current_members} 由条件更新推进
 * （竞态兜底在 SQL 语义）；该表无乐观锁列（状态推进全部走条件更新，无并发整行更新场景）。
 * {@code update_time} 由 DB {@code ON UPDATE CURRENT_TIMESTAMP} 维护，PO 不映射。
 */
@Data
@TableName("group_team")
public class GroupTeamPO implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;
    @TableId(type = IdType.AUTO)
    private Long id;
    /** 团号（业务唯一键，UUID） */
    private String teamNo;
    private Long activityId;
    /** 团长 user_id（开团者） */
    private Long leaderUserId;
    /** 成团人数（开团时从活动快照） */
    private Integer requiredMembers;
    /** 当前人数（已支付入团成员数，含团长） */
    private Integer currentMembers;
    /** 1=拼团中 2=已成团 3=已失败 */
    private Integer status;
    /** 成团截止时间 */
    private LocalDateTime expireTime;
    /** 成团时间（已失败团为 null） */
    private LocalDateTime successTime;
    private LocalDateTime createTime;
}
