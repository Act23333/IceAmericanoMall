package org.icedamericanomall.marketing.infrastructure.messaging;

import org.icedamericanomall.marketing.domain.dto.FlashSaleOrderMessage;

public interface FlashSaleOrderPublisher {

    boolean publish(FlashSaleOrderMessage message);
}
