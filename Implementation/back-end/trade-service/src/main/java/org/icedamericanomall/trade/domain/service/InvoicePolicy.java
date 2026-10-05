package org.icedamericanomall.trade.domain.service;

import org.icedamericanomall.common.enums.ErrorCode;
import org.icedamericanomall.common.exception.BizException;
import org.icedamericanomall.trade.domain.enums.InvoiceStatusEnum;

import java.time.LocalDateTime;

/**
 * 发票域策略（纯函数，零依赖）—— 金额口径 / 申请时间窗 / 催开限频判定（F14，PRD §十五）。
 *
 * <p>金额口径（决策 D3）：开票金额 = 实付 {@code payAmount}（含运费扣优惠，04-Data-Model §3.7
 * 不变式 V2）。本策略按订单金额三要素重算实付并<b>校验口径一致</b>，返回以 {@code payAmount}
 * 为准的快照值 —— 三要素与实付不一致说明订单数据被破坏，防御性拒绝开票。
 *
 * <p>时间窗（决策 D6/O1）：仅「已完成」订单可申请，起算 {@code orders.end_time}（确认收货时间），
 * 窗口 90 天；逾期入口关闭（管理端仍可补开）。
 *
 * <p>催开（决策 O10 最小实现）：待开具超过 7 天可催开；同发票单 24h 限频
 * （限频依据持久化的 {@code urge_time}，不引入 Redis 依赖，领域层保持零技术组件）。
 */
public final class InvoicePolicy {

    /** 开票申请时间窗（天，起算 orders.end_time，决策 O1：N=90）。 */
    public static final int APPLY_WINDOW_DAYS = 90;

    /** 催开最小等待（天）：待开具满 7 天才允许催开（PRD §15.5.3 场景口径）。 */
    public static final int URGE_MIN_PENDING_DAYS = 7;

    /** 催开限频窗口（小时）：同发票单 24h 一次。 */
    public static final int URGE_INTERVAL_HOURS = 24;

    private InvoicePolicy() {
    }

    /**
     * 解析开票金额快照（分）—— 按不变式 V2 重算实付并与订单实付对账，一致方放行。
     *
     * @param totalAmount    商品价合计（分）
     * @param discountAmount 优惠合计（分）
     * @param postFee        运费（分）
     * @param payAmount      订单实付（分，快照基准）
     * @return 开票金额（= payAmount）
     * @throws BizException 三要素与实付不一致（金额口径被破坏，拒绝开票防虚开）
     */
    public static int resolveInvoiceAmount(int totalAmount, int discountAmount,
                                           int postFee, int payAmount) {
        int expected = totalAmount - discountAmount + postFee;
        if (expected != payAmount) {
            throw new BizException(ErrorCode.BUSINESS_EXECUTION_EXCEPTION,
                    "订单金额口径异常，暂不支持开票");
        }
        return payAmount;
    }

    /**
     * 是否在开票时间窗内（end_time 起算 90 天，含边界当日）。
     *
     * @param endTime 订单完成时间（{@code orders.end_time}，确认收货时间）
     * @param now     当前时间
     * @return true=可申请；false=已超窗（管理端仍可补开）
     */
    public static boolean withinApplyWindow(LocalDateTime endTime, LocalDateTime now) {
        if (endTime == null) {
            return false;
        }
        return !now.isAfter(endTime.plusDays(APPLY_WINDOW_DAYS));
    }

    /**
     * 催开资格判定（纯函数，供应用层按裁决给出精确文案）。
     *
     * @param status     发票状态
     * @param createTime 发票申请时间
     * @param urgeTime   最近催开时间（从未催开为 null）
     * @param now        当前时间
     * @return 裁决结果（见 {@link UrgeVerdict}）
     */
    public static UrgeVerdict checkUrge(Integer status, LocalDateTime createTime,
                                        LocalDateTime urgeTime, LocalDateTime now) {
        if (status == null || InvoiceStatusEnum.PENDING_ISSUE.getCode() != status) {
            return UrgeVerdict.NOT_PENDING;
        }
        if (createTime == null || now.isBefore(createTime.plusDays(URGE_MIN_PENDING_DAYS))) {
            return UrgeVerdict.TOO_EARLY;
        }
        if (urgeTime != null
                && now.isBefore(urgeTime.plusHours(URGE_INTERVAL_HOURS))) {
            return UrgeVerdict.RATE_LIMITED;
        }
        return UrgeVerdict.ALLOWED;
    }

    /** 催开裁决（应用层据此映射错误码与文案）。 */
    public enum UrgeVerdict {

        /** 允许催开（记录 urge_time 并触达管理端，O10） */
        ALLOWED,

        /** 非待开具状态（已开具无需催开，终态不可催开） */
        NOT_PENDING,

        /** 待开具未满 7 天（开具处理中，不允许催开） */
        TOO_EARLY,

        /** 24 小时内已催开过（限频） */
        RATE_LIMITED
    }
}
