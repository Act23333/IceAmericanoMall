package org.noLazy.common.config;

/**
 * RocketMQ Topic/Tag 常量 — 阿里标准：common 模块统一定义
 *
 * <pre>
 * 领域事件 Topic:
 *   domain-event-topic  (order.created / payment.succeeded / order.shipped)
 *
 * 系统 Topic:
 *   order-timeout-topic (timeout-cancel, 延迟消息 level=16/30min)
 *   flash-order-topic   (create, 秒杀异步统计)
 * </pre>
 */
public final class RocketMqTopics {

    // ==================== 领域事件 Topic ====================

    public static final String DOMAIN_EVENT_TOPIC = "domain-event-topic";

    /** 订单创建 → pay-service 发起支付 */
    public static final String TAG_ORDER_CREATED = "order.created";
    public static final String ORDER_CREATED = DOMAIN_EVENT_TOPIC + ":" + TAG_ORDER_CREATED;

    /** 支付成功 → logistics-service 创建物流 */
    public static final String TAG_PAYMENT_SUCCEEDED = "payment.succeeded";
    public static final String PAYMENT_SUCCEEDED = DOMAIN_EVENT_TOPIC + ":" + TAG_PAYMENT_SUCCEEDED;

    /** 订单发货 → gateway-service WebSocket 推送 */
    public static final String TAG_ORDER_SHIPPED = "order.shipped";
    public static final String ORDER_SHIPPED = DOMAIN_EVENT_TOPIC + ":" + TAG_ORDER_SHIPPED;

    // ==================== 系统 Topic ====================

    public static final String ORDER_TIMEOUT_TOPIC = "order-timeout-topic";
    public static final String ORDER_TIMEOUT_TAG = "timeout-cancel";
    public static final String ORDER_TIMEOUT_DESTINATION = ORDER_TIMEOUT_TOPIC + ":" + ORDER_TIMEOUT_TAG;
    public static final int ORDER_TIMEOUT_DELAY_LEVEL = 16;

    public static final String FLASH_ORDER_TOPIC = "flash-order-topic";
    public static final String FLASH_ORDER_TAG = "create";
    public static final String FLASH_ORDER_DESTINATION = FLASH_ORDER_TOPIC + ":" + FLASH_ORDER_TAG;

    private RocketMqTopics() {
    }
}
