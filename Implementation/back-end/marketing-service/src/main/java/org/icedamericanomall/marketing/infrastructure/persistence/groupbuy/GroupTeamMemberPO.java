package org.icedamericanomall.marketing.infrastructure.persistence.groupbuy;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 拼团团成员表映射对象（PO）—— {@code group_team_member} 表（V5.14，ADR-049），
 * 仅存在于基础设施持久化层。
 *
 * <p>双唯一键兜幂等：{@code uk_order_no}（一单一团一次）、{@code uk_team_user}（同团不重复）；
 * 撞键抛 {@code DuplicateKeyException} 由应用层回落处理。
 */
@Data
@TableName("group_team_member")
public class GroupTeamMemberPO implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long teamId;
    /** 参团用户 user_id（团长亦入列） */
    private Long userId;
    /** 关联拼团订单号（uk_order_no 幂等锚点） */
    private String orderNo;
    private LocalDateTime createTime;
}
