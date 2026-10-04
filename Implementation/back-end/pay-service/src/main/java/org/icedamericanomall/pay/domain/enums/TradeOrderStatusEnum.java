package org.icedamericanomall.pay.domain.enums;

import lombok.Getter;

/**
 * trade-service 订单状态码 —— pay-service 通过 Feign 更新订单状态时引用的跨服务契约值。
 *
 * <p><b>契约关系</b>：本枚举是 trade-service {@code OrderStatusEnum} 在支付域的<b>镜像子集</b>，
 * 数值必须与 trade 侧保持一致（改动 trade 的编码即破坏本契约，需双方同步）：
 * <pre>
 *   trade OrderStatusEnum:    1-待付款 2-待发货 3-待收货 4-已完成 5-已取消 6-待评价
 *   pay  TradeOrderStatusEnum:          2-待发货                5-已取消
 * </pre>
 * 支付成功后回写 2（待发货），支付超时回写 5（已取消）；trade 侧内部接口仅接受
 * 「待付款 → 待发货」「待付款 → 已取消」两条迁移。此处只声明支付域用到的两个状态，
 * 避免 pay-service 中散落魔法值 2/5。
 */
@Getter
public enum TradeOrderStatusEnum {
    PENDING_SHIPMENT(2, "待发货"),
    CANCELLED(5, "已取消");

    private final int code;
    private final String desc;

    TradeOrderStatusEnum(int code, String desc) {
        this.code = code;
        this.desc = desc;
    }
}
