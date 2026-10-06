package org.icedamericanomall.trade.app.order;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.icedamericanomall.client.CurrencyClient;
import org.icedamericanomall.dto.CurrencyRateCO;
import org.icedamericanomall.trade.app.order.strategy.OrderCreateContext;
import org.springframework.stereotype.Component;

/**
 * F16 多币种 Phase 1：下单展示汇率快照协作器（契约 §4.6.1 / §4.14.3b I61，PRD §17.4.3 D1-B）。
 *
 * <p><b>编排位置</b>：统一创建管道内、事务外（{@code OrderCreationManager} 整体无事务边界）——
 * validate 之后、buildOrder 之前查询 I61 汇率并回填 ctx，由
 * {@code NormalCartOrderStrategy.buildBaseOrder} 随单快照落 {@code orders.display_*} 两列。
 *
 * <p><b>fail-open（PRD §17.7.2 降级纪律，硬验收）</b>：未传/主币种 CNY 直通不查汇率；
 * 查询异常、Feign 降级哨兵 {@code rateScaled=-1}（ia-api {@code CurrencyClientFallback}
 * 哨兵常量）、币种未启用（wallet 1103 经降级转哨兵）一律<b>跳过快照</b>——ctx 两字段归空，
 * {@code orders.display_currency/display_rate} 两列 NULL，订单照常创建，
 * <b>绝不因汇率失败阻塞交易主流程</b>。快照列是展示参考、非结算币种（D1-B/D3-B），
 * 金额不变式 V2 与四金额字段（恒 CNY 分）不受影响。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderDisplaySnapshotService {

    /** 主结算币种（平台恒 CNY 计价）：展示币种为主币种时直通不快照（CNY 订单无参考价）。 */
    public static final String BASE_CURRENCY_CNY = "CNY";

    /** 定点整数汇率有效下界（×10⁶ 刻度）：合法汇率必为正（覆盖降级哨兵 -1 与无意义 0）。 */
    private static final long MIN_VALID_RATE_SCALED = 1L;

    private final CurrencyClient currencyClient;

    /**
     * 按需快照展示汇率（fail-open，本方法<b>永不抛出</b>）。
     *
     * <p>语义（快照成功后 ctx 两字段同非空，其余情况两者皆空，由 buildBaseOrder 原子落列）：
     * <ul>
     *   <li>未传/空白 → 归空返回（未传 = CNY 不快照）；</li>
     *   <li>{@code CNY}（主币种）→ 归空返回，不调汇率端点（主币种直通，写测试注释锚点）；</li>
     *   <li>查询成功且 rateScaled 为有效正值 → 回填 {@code ctx.displayRate}，保留 displayCurrency；</li>
     *   <li>哨兵 -1 / 非正值 / 空响应 / 任何异常 → log.warn 并将 displayCurrency 归空（两列 NULL）。</li>
     * </ul>
     *
     * @param ctx 下单上下文（读 displayCurrency，写 displayRate；失败路径归空 displayCurrency）
     */
    public void snapshotIfNeeded(OrderCreateContext ctx) {
        String code = ctx.getDisplayCurrency();
        if (code == null || code.isBlank() || BASE_CURRENCY_CNY.equals(code)) {
            // 未传=CNY 不快照；主币种直通：CNY 订单两列 NULL、无参考价，无需汇率换算
            ctx.setDisplayCurrency(null);
            return;
        }
        CurrencyRateCO rate = queryQuietly(code, ctx.getUserId());
        if (rate == null || rate.getRateScaled() == null
                || rate.getRateScaled() < MIN_VALID_RATE_SCALED) {
            // 降级哨兵 -1 / 未启用(1103 经 fallback 转哨兵) / 非正值 → 跳过快照，订单照常创建
            log.warn("展示汇率快照跳过(降级/无效): code={}, rateScaled={}, userId={} "
                    + "—— 订单两列置 NULL 照常创建(fail-open)", code,
                    rate == null ? null : rate.getRateScaled(), ctx.getUserId());
            ctx.setDisplayCurrency(null);
            return;
        }
        ctx.setDisplayRate(rate.getRateScaled());
        log.info("展示汇率快照: code={}, rateScaled={}, userId={}", code,
                rate.getRateScaled(), ctx.getUserId());
    }

    /** 汇率查询（吞异常版）：任何异常降级为 {@code null}（fail-open），由调用方统一跳过快照。 */
    private CurrencyRateCO queryQuietly(String code, Long userId) {
        try {
            return currencyClient.getCurrent(code);
        } catch (Exception e) {
            log.warn("展示汇率查询异常: code={}, userId={} —— 跳过快照照常创建(fail-open)",
                    code, userId, e);
            return null;
        }
    }
}
