package org.icedamericanomall.wallet.adapter.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 币种管理端视图（AD66 {@code GET /api/admin/currency/page}，契约 §4.13.8g）。
 *
 * <p>含 {@code source}/{@code effectTime}/{@code updaterId} 留痕字段，供汇率 stale 告警
 * 与人工兜底；不含技术主键 id 与 version/cipher/hash 技术字段（09-Constraints VO 约束）。
 * 多词字段走**全局 snake_case**（契约口径）。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CurrencyAdminVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 币种代码（ISO 4217，业务唯一键）。 */
    private String code;

    /** 币种名称。 */
    private String name;

    /** 符号（如 $/€/¥）。 */
    private String symbol;

    /** ISO 4217 最小单位指数（USD/EUR=2、JPY/KRW=0）。 */
    private Integer exponent;

    /** 1=启用 0=停用。 */
    private Integer status;

    /** 定点整数汇率：每 1 元人民币兑换目标币主单位数 × 10⁶（未配置为 null）。 */
    private Long rateScaled;

    /** 汇率来源：API（定时拉取）/ MANUAL（人工修正）（未配置为 null）。 */
    private String source;

    /** 汇率生效时间（上次成功更新时点；未配置为 null）。 */
    private LocalDateTime effectTime;

    /** 修正人（MANUAL 时记录；API 拉取为 null）。 */
    private Long updaterId;

    /** 备注。 */
    private String remark;
}
