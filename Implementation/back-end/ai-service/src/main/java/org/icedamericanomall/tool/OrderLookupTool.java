package org.icedamericanomall.tool;

import dev.langchain4j.agent.tool.Tool;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.icedamericanomall.client.OrderClient;
import org.icedamericanomall.dto.OrderSummaryDTO;
import org.springframework.stereotype.Component;

/**
 * LangChain4j @Tool — 订单查询，通过 Feign Client 调用 trade-service 内部接口。
 * <p>
 * V2.5: 使用 {@link OrderClient} (ia-api Feign) 替代 {@code RestTemplate} 直连，
 * 调用 {@code /internal/trade/order/{orderNo}} 获取订单摘要（含状态和金额）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderLookupTool {

    private final OrderClient orderClient;

    @Tool("根据订单号查询订单状态、金额等信息")
    public String lookupOrder(String orderNo) {
        try {
            OrderSummaryDTO order = orderClient.getOrder(orderNo);
            if (order == null) {
                return "未找到订单 " + orderNo + "，请检查订单号是否正确";
            }
            String statusText = switch (order.getStatus()) {
                case 1 -> "待付款";
                case 2 -> "待发货";
                case 3 -> "待收货";
                case 4 -> "已完成";
                case 5 -> "已取消";
                default -> "状态码:" + order.getStatus();
            };
            return String.format("订单 %s: 状态=%s, 金额=¥%.2f",
                    order.getOrderNo(), statusText,
                    order.getTotalAmount() / 100.0);
        } catch (Exception e) {
            log.warn("OrderLookupTool error for orderNo={}: {}", orderNo, e.getMessage());
            return "查询订单失败，请稍后重试或联系人工客服";
        }
    }
}
