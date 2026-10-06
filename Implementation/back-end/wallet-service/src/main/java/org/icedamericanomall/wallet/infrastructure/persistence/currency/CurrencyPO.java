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
 * 币种表映射对象（PO）—— {@code currency} 表（F16，Initialize.sql V5.17）。
 * 仅存在于基础设施持久化层，不外泄（领域侧消费 {@code Currency} record）。
 */
@Data
@TableName("currency")
public class CurrencyPO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 币种代码（ISO 4217，业务唯一键 uk_code，如 USD/EUR/JPY）。 */
    private String code;

    /** 币种名称。 */
    private String name;

    /** 符号（如 $/€/¥）。 */
    private String symbol;

    /** ISO 4217 最小单位指数（USD/EUR=2、JPY/KRW=0）。 */
    private Integer exponent;

    /** 1=启用 0=停用（停用后 C 端列表不返回，换算请求回落 CNY）。 */
    private Integer status;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
