package org.icedamericanomall.trade.app.order;

import com.baomidou.mybatisplus.core.metadata.IPage;
import lombok.RequiredArgsConstructor;
import org.icedamericanomall.dto.OrderSummaryCO;
import org.icedamericanomall.trade.adapter.assembler.OrderConverter;
import org.icedamericanomall.trade.adapter.response.OrderVO;
import org.icedamericanomall.trade.domain.model.order.Order;
import org.icedamericanomall.trade.domain.repository.OrderRepository;
import org.springframework.stereotype.Component;

/**
 * 订单查询用例族编排（应用层 · 读侧）—— 详情（含订单项）/ 分页 / 跨服务摘要。
 *
 * <p>归属校验（本人订单 / 本店订单）下沉在仓储实现，本层只做装配；
 * 出参一律为 VO 或 Feign CO，聚合不泄漏到适配层之外。
 */
@Component
@RequiredArgsConstructor
public class OrderQueryManager {

    private final OrderRepository orderRepository;
    private final OrderConverter orderConverter;

    /** 订单详情（本人）：归属校验失败抛 ORDER_NOT_FOUND / FORBIDDEN。 */
    public OrderVO getOwnedOrderDetail(String orderNo, Long userId) {
        Order order = orderRepository.findOwnedOrder(orderNo, userId);
        return toDetailVO(order);
    }

    /** 订单详情（本店）：归属校验失败抛 ORDER_NOT_FOUND / FORBIDDEN。 */
    public OrderVO getSellerOrderDetail(String orderNo, Long sellerId) {
        Order order = orderRepository.findSellerOrder(orderNo, sellerId);
        return toDetailVO(order);
    }

    /** 通用订单分页：userId/sellerId 可同时传（互斥）；均为 null 时查所有。 */
    public IPage<OrderVO> pageOrders(Long userId, Long sellerId, Integer status, int page, int size) {
        return orderRepository.pageOrders(userId, sellerId, status, page, size)
                .convert(orderConverter::toVO);
    }

    /** 订单摘要（按订单号；不存在返回 null）—— 供内部 Feign 端点。 */
    public OrderSummaryCO getOrderSummary(String orderNo) {
        return toSummary(orderRepository.findByOrderNo(orderNo));
    }

    /** 订单摘要（按技术主键；不存在返回 null）—— 供内部 Feign 端点。 */
    public OrderSummaryCO getOrderSummaryById(Long orderId) {
        return toSummary(orderRepository.findById(orderId));
    }

    /** 订单聚合 → 含订单项的详情 VO（列表页不返回订单项，故仅详情使用）。 */
    private OrderVO toDetailVO(Order order) {
        OrderVO vo = orderConverter.toVO(order);
        vo.setItems(orderConverter.toItemVOs(orderRepository.findItemsByOrderId(order.getId())));
        return vo;
    }

    /** 订单聚合 → 跨服务摘要（totalAmount=原价, payAmount=应付）。 */
    private OrderSummaryCO toSummary(Order order) {
        if (order == null) {
            return null;
        }
        OrderSummaryCO dto = new OrderSummaryCO();
        dto.setOrderNo(order.getOrderNo());
        dto.setUserId(order.getUserId());
        dto.setTotalAmount(order.getTotalAmount());
        dto.setPayAmount(order.getPayAmount());
        dto.setStatus(order.getStatus());
        return dto;
    }
}
