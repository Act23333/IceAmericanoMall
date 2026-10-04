package org.icedamericanomall.dto;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 运费模板 CO（F2 运费模型，item-service → trade-service，I43 出参）。
 *
 * <p>契约字段（{@code 05-API-Specification.md} §4.14.17）：{@code id / templateNo / fee /
 * freeThreshold / status}；本类额外携带 {@code isDefault} 标记，使调用方<b>一次调用</b>即可完成
 * 「订单内绑定模板 + 商家默认模板回落」的解析（避免二次查询）。
 *
 * <p><b>{@code isDefault} 语义</b>：该模板是所属商家<b>最早创建的启用模板</b>
 * （{@code WHERE seller_id=? AND status=1 ORDER BY id ASC LIMIT 1}）——商品未绑定模板
 * （{@code freight_template_id} 为 NULL）或绑定模板已停用时的回落目标（见
 * {@code 04-Data-Model.md} §十二 12.2 计价规则第 5 条）。
 *
 * <p><b>只返回启用模板</b>：入参 {@code templateIds} 中已停用/不存在的模板不返回，
 * 调用方据此回落默认模板。
 */
@Data
public class FreightTemplateCO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 模板技术主键（{@code freight_template.id}）。 */
    private Long id;

    /** 模板业务编号（UUID）。 */
    private String templateNo;

    /** 固定运费（分，≥0）。 */
    private Integer fee;

    /** 满额包邮阈值（分）；0 = 不参与满额包邮。 */
    private Integer freeThreshold;

    /** 状态：1-启用，0-停用（本 CO 只会出现 1）。 */
    private Integer status;

    /** 是否该商家的默认模板（最早创建的启用模板）。 */
    private Boolean isDefault;
}
