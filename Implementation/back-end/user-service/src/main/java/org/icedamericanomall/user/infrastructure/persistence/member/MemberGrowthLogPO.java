package org.icedamericanomall.user.infrastructure.persistence.member;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * {@code member_growth_log} 表映射对象（V5.8 DDL；uk_order_no 幂等锚点 —— ADR-044）。
 */
@Data
@TableName("member_growth_log")
public class MemberGrowthLogPO implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 买家 user_id（跨服务 user_id 口径） */
    private Long userId;

    /** 来源订单号（幂等锚点，uk_order_no；BINARY(16) ↔ byte[]，hex 形态仅在领域层，ADR-050） */
    private byte[] orderNo;

    /** 本单成长值（=订单实付金额，分） */
    private Integer growthAmount;

    /** 入账后等级（String code，与 MemberLevelEnum 对齐） */
    private String afterLevel;

    /** 入账时间（审计字段填充，ADR-030） */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
