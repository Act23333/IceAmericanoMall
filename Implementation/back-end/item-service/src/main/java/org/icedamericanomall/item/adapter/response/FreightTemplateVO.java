package org.icedamericanomall.item.adapter.response;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 运费模板视图对象（F2 运费模型，契约 {@code 05-API-Specification.md} §4.4.9）。
 *
 * <p>{@code sellerId}（商家 ID）不外泄；{@code status=0} 表示停用模板。
 */
@Data
public class FreightTemplateVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 模板 ID。 */
    private Long id;

    /** 模板业务编号（UUID）。 */
    private String templateNo;

    /** 模板名称。 */
    private String templateName;

    /** 计费方式：1-固定运费。 */
    private Integer chargeMode;

    /** 固定运费（分）。 */
    private Integer fee;

    /** 满额包邮阈值（分）；0 = 不参与满额包邮。 */
    private Integer freeThreshold;

    /** 状态：1-启用，0-停用。 */
    private Integer status;

    /** 创建时间。 */
    private LocalDateTime createTime;
}
