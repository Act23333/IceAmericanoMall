package org.icedamericanomall.marketing.producer;

import org.icedamericanomall.marketing.domain.dto.FlashSaleOrderMessage;

public interface FlashSaleOrderPublisher {

    boolean publish(FlashSaleOrderMessage message);
}
