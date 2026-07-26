package org.icedamericanomall.producer;

import org.icedamericanomall.domain.dto.FlashSaleOrderMessage;

public interface FlashSaleOrderPublisher {

    boolean publish(FlashSaleOrderMessage message);
}
