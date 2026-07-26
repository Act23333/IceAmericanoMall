package org.icedAmericanoMall.producer;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@ConditionalOnMissingBean(OrderTimeoutPublisher.class)
public class NoopOrderTimeoutPublisher implements OrderTimeoutPublisher {

    @Override
    public void publishTimeout(String orderNo) {
        log.warn("RocketMQ disabled, skip order timeout message: orderNo={}", orderNo);
    }
}
