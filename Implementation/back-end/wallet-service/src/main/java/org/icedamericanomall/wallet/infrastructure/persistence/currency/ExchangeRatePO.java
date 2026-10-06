package org.icedamericanomall.wallet.infrastructure.persistence.currency;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 展示汇率表映射对象（PO）—— {@code exchange_rate} 表（F16，Initialize.sql V5.17）。
 *
 * <p>定点整数纪律：{@code rate_scaled} 为 BIGINT 定点汇率（10⁻⁶ 刻度），类型即 {@code Long}，
 * <b>严禁浮点</b>。仅存在于基础设施持久化层，不外泄（领域侧消费 {@code ExchangeRate} record）。
 */
@Data
@TableName("exchange_rate")
public class ExchangeRatePO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 币种代码（业务唯一键 uk_code，对应 currency.code）。 */
    private String code;

    /** 定点整数汇率：每 1 元人民币兑换目标币主单位数 × 10⁶（如 USD 0.140300 → 140300）。 */
    private Long rateScaled;

    /** 来源：API（定时拉取）/ MANUAL（管理端人工修正）。 */
    private String source;

    /** 生效时间（上次成功更新时点；失败保留上次值 + 告警）。 */
    private LocalDateTime effectTime;

    /** 修正人（MANUAL 时记录；API 拉取为 NULL）。 */
    private Long updaterId;

    /** 备注。 */
    private String remark;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
