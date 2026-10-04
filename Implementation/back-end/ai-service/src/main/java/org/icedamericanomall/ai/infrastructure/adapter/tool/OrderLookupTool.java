package org.icedamericanomall.ai.infrastructure.adapter.tool;

import dev.langchain4j.agent.tool.Tool;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.icedamericanomall.ai.domain.model.order.OrderBrief;
import org.icedamericanomall.ai.domain.port.OrderPort;
import org.springframework.stereotype.Component;

/**
 * LangChain4j @Tool — 订单查询。
 * <p>
 * 仅依赖领域端口 {@link OrderPort}，Feign 调用在基础设施层（防腐层）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderLookupTool {

    /**
     * 订单状态码常量 —— 与 trade-service {@code OrderStatusEnum} 的码值契约一致，
     * 任何一侧调整都需同步升级（不可按本类局部语义自行改动）。
     */
    private static final int STATUS_PENDING_PAYMENT = 1;
    private static final int STATUS_PENDING_SHIPMENT = 2;
    private static final int STATUS_PENDING_RECEIPT = 3;
    private static final int STATUS_COMPLETED = 4;
    private static final int STATUS_CANCELLED = 5;

    private final OrderPort orderPort;

    /**
     * 查询订单状态与金额。
     *
     * @param orderNo 订单号
     * @return 订单摘要文本；订单不存在或查询失败返回友好提示
     */
    @Tool("根据订单号查询订单状态、金额等信息")
    public String lookupOrder(String orderNo) {
        try {
            OrderBrief order = orderPort.getOrder(orderNo);
            if (order == null) {
                return "未找到订单 " + orderNo + "，请检查订单号是否正确";
            }
            return String.format("订单 %s: 状态=%s, 金额=¥%.2f",
                    order.orderNo(), statusText(order.status()), centsToYuan(order.totalAmount()));
        } catch (Exception e) {
            log.warn("OrderLookupTool error for orderNo={}: {}", orderNo, e.getMessage());
            return "查询订单失败，请稍后重试或联系人工客服";
        }
    }

    /** 订单状态码 → 中文描述（未知码值原样回显，便于排障）。 */
    private static String statusText(Integer status) {
        if (status == null) {
            return "未知";
        }
        return switch (status) {
            case STATUS_PENDING_PAYMENT -> "待付款";
            case STATUS_PENDING_SHIPMENT -> "待发货";
            case STATUS_PENDING_RECEIPT -> "待收货";
            case STATUS_COMPLETED -> "已完成";
            case STATUS_CANCELLED -> "已取消";
            default -> "状态码:" + status;
        };
    }

    /** 金额分 → 元。 */
    private static double centsToYuan(Integer cents) {
        return cents == null ? 0 : cents / 100.0;
    }
}
