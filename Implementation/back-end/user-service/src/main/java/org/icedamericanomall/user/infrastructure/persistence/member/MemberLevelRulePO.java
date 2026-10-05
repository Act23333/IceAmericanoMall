package org.icedamericanomall.user.infrastructure.persistence.member;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * {@code member_level_rule} 表映射对象（V5.8 DDL；Phase 1 只读，seed 预置 —— ADR-044）。
 */
@Data
@TableName("member_level_rule")
public class MemberLevelRulePO implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 等级（NORMAL/SILVER/GOLD/PLATINUM，与 MemberLevelEnum 对齐；uk_level） */
    private String level;

    /** 累计成长值门槛（分） */
    private Integer threshold;

    /** 会员折扣率万分比（10000=不折扣，9800=98%） */
    private Integer discountRate;

    /** 积分倍率百分比（100=1倍） */
    private Integer pointsRate;

    /** 比较顺序（升序，与 threshold 同序） */
    private Integer sort;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
