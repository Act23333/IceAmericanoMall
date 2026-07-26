package org.icedAmericanoMall.producer;

import lombok.extern.slf4j.Slf4j;
import org.icedAmericanoMall.domain.dto.FlashSaleOrderMessage;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@ConditionalOnMissingBean(FlashSaleOrderPublisher.class)
public class NoopFlashSaleOrderPublisher implements FlashSaleOrderPublisher {

    @Override
    public boolean publish(FlashSaleOrderMessage message) {
        log.warn("RocketMQ disabled, flash order stays pending: orderNo={}", message.getOrderNo());
        return true;
    }
}
