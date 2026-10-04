package org.icedamericanomall.marketing.infrastructure.messaging;

import lombok.extern.slf4j.Slf4j;
import org.icedamericanomall.marketing.domain.dto.FlashSaleOrderMessage;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 秒杀订单消息空实现（MQ 未开启时的兜底）。
 *
 * <p>装配开关与 {@link RocketFlashSaleOrderPublisher} 互斥，二者由同一属性
 * {@code rocketmq.producer.enabled} 决定：
 * 未配置/false → 本类生效（消息不投递，仅告警）；true → RocketMQ 实现生效。
 * 不再使用 {@code @ConditionalOnMissingBean}（作用在组件扫描的 @Component 上顺序不可靠）。
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "rocketmq.producer.enabled", havingValue = "false", matchIfMissing = true)
public class NoopFlashSaleOrderPublisher implements FlashSaleOrderPublisher {

    @Override
    public boolean publish(FlashSaleOrderMessage message) {
        log.warn("RocketMQ disabled, flash order stays pending: orderNo={}", message.getOrderNo());
        return true;
    }
}
