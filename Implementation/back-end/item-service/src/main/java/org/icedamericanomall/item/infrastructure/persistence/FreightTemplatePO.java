package org.icedamericanomall.item.infrastructure.persistence;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 运费模板持久化对象（{@code freight_template} 表，F2 运费模型，V5.6）。
 *
 * <pre>
 * 不变量（由服务层保证，本类不承载行为）:
 *   1. (seller_id, template_name) 唯一 —— 同商家内模板名不可重复；
 *   2. fee >= 0；free_threshold >= 0（0 = 不参与满额包邮）；
 *   3. 停用（status=0）模板不参与下单计价。
 * </pre>
 *
 * <p><b>seller_id 口径</b>：商家 {@code user_id}（与 {@code spu.seller_id} 同口径，
 * ADR-019 Phase A），不是 {@code seller.id}。
 */
@Data
@TableName("freight_template")
public class FreightTemplatePO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 模板业务编号（UUID）。 */
    /** 模板业务编号（BINARY(16) ↔ byte[]；hex 形态仅在领域层，ADR-050） */
    private byte[] templateNo;

    /** 商家 ID（= 商家 user_id）。 */
    private Long sellerId;

    /** 模板名称（同商家内唯一）。 */
    private String templateName;

    /** 计费方式：1-固定运费（Phase 1 唯一值）。 */
    private Integer chargeMode;

    /** 固定运费（分，≥0）。 */
    private Integer fee;

    /** 满额包邮阈值（分）；0 = 不参与满额包邮。 */
    private Integer freeThreshold;

    /** 状态：1-启用，0-停用。 */
    private Integer status;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
