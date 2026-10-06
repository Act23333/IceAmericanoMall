package org.icedamericanomall.wallet.domain.model.currency;

import org.icedamericanomall.common.enums.ErrorCode;
import org.icedamericanomall.common.exception.BizException;

/**
 * 币种（领域模型，落 {@code currency} 表）—— 展示性多币种数据的聚合根概念，业务唯一键 {@link #code()}。
 *
 * <p><b>定位（F16 Phase 1，PRD §十七 D1-B/D7）</b>：币种 + 汇率是公开参考数据，
 * 仅服务「前端本币展示换算」与「订单 {@code display_*} 快照」，<b>不参与任何金额不变式</b>，
 * 资金链路恒 CNY 分。汇率作为 1:1 值组件 {@link ExchangeRate} 挂在本聚合上
 * （{@code exchange_rate.uk_code} 对应 {@code currency.uk_code}；未配置汇率时为 null，消费方 fail-open）。
 *
 * <p>用 {@code record} 表达不可变值组合；状态启停以「条件 UPDATE + 读侧过滤」承载
 * （仓储按业务键 {@code code} 更新，停用不影响既有订单快照展示——只影响 C 端列表与 I61）。
 *
 * @param code     币种代码（ISO 4217，如 USD/EUR/JPY；CNY 为主币种不入表）
 * @param name     币种名称
 * @param symbol   符号（如 $/€/¥）
 * @param exponent ISO 4217 最小单位指数（USD/EUR=2、JPY/KRW=0、BHD=3）
 * @param status   1=启用 0=停用
 * @param rate     当前汇率值组件（未配置为 null）
 */
public record Currency(String code, String name, String symbol, int exponent, int status, ExchangeRate rate) {

    /** 状态：启用（C 端列表返回、I61 可换算）。 */
    public static final int STATUS_ENABLED = 1;

    /** 状态：停用（C 端列表不返回；换算请求回落 CNY；既有快照展示不受影响）。 */
    public static final int STATUS_DISABLED = 0;

    /**
     * 是否启用。
     *
     * @return 启用返回 {@code true}
     */
    public boolean isEnabled() {
        return status == STATUS_ENABLED;
    }

    /**
     * 校验状态值域（仅允许 0/1，魔法值收敛为本聚合常量；入参取持久化/请求口径的包装类型）。
     *
     * @param status 待校验状态（null 视为非法）
     * @throws BizException 状态缺失或值域非法（{@link ErrorCode#PARAM_ERROR}，HTTP 400）
     */
    public static void requireValidStatus(Integer status) {
        if (status == null || (status != STATUS_ENABLED && status != STATUS_DISABLED)) {
            throw new BizException(ErrorCode.PARAM_ERROR, "币种状态仅允许 0(停用)/1(启用): " + status);
        }
    }
}
