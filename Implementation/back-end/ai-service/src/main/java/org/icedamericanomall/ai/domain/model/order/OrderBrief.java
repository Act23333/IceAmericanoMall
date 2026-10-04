package org.icedamericanomall.ai.domain.model.order;

/**
 * 订单摘要（领域 DTO）—— 订单状态码与 trade-service 的 OrderStatusEnum 保持契约一致。
 *
 * @param orderNo     订单号
 * @param status      订单状态码（1-5，见 {@code OrderLookupTool} 常量）
 * @param totalAmount 订单金额（分）
 */
public record OrderBrief(
        String orderNo,
        Integer status,
        Integer totalAmount
) {}
