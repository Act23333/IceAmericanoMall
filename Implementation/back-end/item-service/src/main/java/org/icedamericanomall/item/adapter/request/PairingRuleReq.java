package org.icedamericanomall.item.adapter.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 搭配规则创建请求（适配层入参）—— 平台后台 {@code POST /api/admin/kg/rule}（AD62，契约 §4.13.8f）。
 *
 * <p>字段必填由 JSR-303 绑定期校验（非法 → {@code PARAM_ERROR(1101)} / 400）；
 * 源/目标类目存在性（悬空 → 1101）与业务唯一键 {@code uk_src_dst} 防重（先查后插 +
 * DuplicateKey 回落 400）由应用层 {@code KgAdminManager} 编排。
 */
@Data
public class PairingRuleReq implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 源类目 ID（必填，如：手机类目）。 */
    @NotNull
    private Long srcCategoryId;

    /** 目标类目 ID（必填，如：手机配件类目）。 */
    @NotNull
    private Long dstCategoryId;

    /** 排序权重（缺省 0，越大越靠前）。 */
    private Integer weight;

    /** 备注（可空，≤255）。 */
    @Size(max = 255)
    private String remark;
}
