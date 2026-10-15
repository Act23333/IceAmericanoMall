package org.icedamericanomall.wallet.adapter.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 币种视图（P36 {@code GET /api/currency/list}，契约 §4.2.11）。
 *
 * <p>仅启用币种；响应不含免责文案（前端静态常量，D6）。
 * 多词字段走**全局 snake_case**（契约口径 {@code rate_scaled}/{@code effect_time}）——
 * 全平台命名统一为 snake_case 后，原先「本服务 SNAKE_CASE + 显式钉 camelCase」的补丁已移除。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CurrencyVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 币种代码（ISO 4217）。 */
    private String code;

    /** 币种名称。 */
    private String name;

    /** 符号（如 $/€/¥）。 */
    private String symbol;

    /** ISO 4217 最小单位指数（USD/EUR=2、JPY/KRW=0）。 */
    private Integer exponent;

    /** 定点整数汇率：每 1 元人民币兑换目标币主单位数 × 10⁶（未配置为 null，前端回落 CNY）。 */
    private Long rateScaled;

    /** 汇率生效时间（上次成功更新时点；未配置为 null）。 */
    private LocalDateTime effectTime;
}
