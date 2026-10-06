package org.icedamericanomall.wallet.domain.model.currency;

import org.icedamericanomall.common.enums.ErrorCode;
import org.icedamericanomall.common.exception.BizException;

import java.time.LocalDateTime;

/**
 * 展示汇率（领域值对象，落 {@code exchange_rate} 表，一行一币 upsert）。
 *
 * <p><b>定点整数纪律（§1.5 口径，硬约束）</b>：{@link #rateScaled()} 为定点整数汇率——
 * 每 1 元人民币兑换目标币<b>主单位</b>数 × 10^ {@link #SCALE_DIGITS}（如 USD 0.140300 → 140300、
 * JPY 20.910000 → 20910000）。全程严禁浮点表达；换算公式
 * {@code round(amountCNY × rate_scaled × 10^exponent ÷ 10⁸)}（half-up）由前端执行（D7-A）。
 *
 * <p><b>留痕口径</b>：{@code source}=API（定时拉取，updaterId 为 null）/ MANUAL（管理端人工修正，
 * updaterId 记录修正人）；{@code effectTime}=生效时点（上次成功更新时点；拉取失败保留上次值，D2）。
 * O6 裁决不做历史版本化。
 *
 * @param code       币种代码（ISO 4217，业务唯一键）
 * @param rateScaled 定点整数汇率（BIGINT，10⁻⁶ 刻度，恒正）
 * @param source     来源：{@link #SOURCE_API} / {@link #SOURCE_MANUAL}
 * @param effectTime 生效时间（上次成功更新时点）
 * @param updaterId  修正人（MANUAL 时记录；API 拉取为 null）
 * @param remark     备注（可空）
 */
public record ExchangeRate(String code, long rateScaled, String source, LocalDateTime effectTime,
                           Long updaterId, String remark) {

    /** 来源：定时拉取（汇率 API）。 */
    public static final String SOURCE_API = "API";

    /** 来源：管理端人工修正。 */
    public static final String SOURCE_MANUAL = "MANUAL";

    /** 定点刻度位数：rate_scaled = 主单位数 × 10⁶（§1.5 定点整数口径）。 */
    public static final int SCALE_DIGITS = 6;

    /**
     * 人工修正汇率（AD67）：source=MANUAL，生效时间取当前时刻，修正人从上下文传入。
     *
     * @param code       币种代码
     * @param rateScaled 定点整数汇率（恒正，管理端 UI 负责主单位小数 → 定点整数的换算）
     * @param updaterId  修正人（应用层从 UserContext 取）
     * @param remark     备注（可空）
     * @return 人工修正汇率值对象
     * @throws BizException 汇率非正数（{@link ErrorCode#PARAM_ERROR}，HTTP 400）
     */
    public static ExchangeRate manual(String code, long rateScaled, Long updaterId, String remark) {
        requirePositive(rateScaled);
        return new ExchangeRate(code, rateScaled, SOURCE_MANUAL, LocalDateTime.now(), updaterId, remark);
    }

    /**
     * 定时拉取汇率（Job 专用）：source=API，修正人为 null。
     *
     * @param code       币种代码
     * @param rateScaled 定点整数汇率（恒正；非法值由 Job 在解析侧拦截并保留上次值）
     * @param effectTime 生效时间（拉取成功时点）
     * @return API 拉取汇率值对象
     * @throws BizException 汇率非正数（{@link ErrorCode#PARAM_ERROR}，HTTP 400）
     */
    public static ExchangeRate apiPull(String code, long rateScaled, LocalDateTime effectTime) {
        requirePositive(rateScaled);
        return new ExchangeRate(code, rateScaled, SOURCE_API, effectTime, null, null);
    }

    /** 汇率恒正的不变式（0/负数定点值无意义，创建即拦截）。 */
    private static void requirePositive(long rateScaled) {
        if (rateScaled <= 0) {
            throw new BizException(ErrorCode.PARAM_ERROR, "定点整数汇率必须大于0: " + rateScaled);
        }
    }
}
