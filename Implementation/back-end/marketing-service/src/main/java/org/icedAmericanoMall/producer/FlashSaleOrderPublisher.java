package org.icedAmericanoMall.producer;

import org.icedAmericanoMall.domain.dto.FlashSaleOrderMessage;

public interface FlashSaleOrderPublisher {

    boolean publish(FlashSaleOrderMessage message);
}
