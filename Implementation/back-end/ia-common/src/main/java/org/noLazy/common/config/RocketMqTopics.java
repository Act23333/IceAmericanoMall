package org.noLazy.common.config;

public final class RocketMqTopics {

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
