package org.icedamericanomall.item.adapter.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 搭配规则编辑请求（适配层入参）—— 平台后台 {@code PUT /api/admin/kg/rule/{id}}（AD63，契约 §4.13.8f）。
 *
 * <p><b>null-safe 局部更新</b>：{@code null} = 本次不改动该字段（对齐 AD51-53 先例）；
 * src/dst 为业务唯一键不可经编辑变更（契约编辑面仅 weight / remark / status 启停）。
 */
@Data
public class PairingRuleUpdateReq implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 排序权重（null = 不改动）。 */
    private Integer weight;

    /** 备注（null = 不改动，≤255）。 */
    @Size(max = 255)
    private String remark;

    /** 状态（null = 不改动；1=启用 0=停用，启停即时生效）。 */
    @Min(0)
    @Max(1)
    private Integer status;
}
