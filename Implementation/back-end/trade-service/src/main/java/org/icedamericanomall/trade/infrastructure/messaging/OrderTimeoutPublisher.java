package org.icedamericanomall.trade.infrastructure.messaging;

public interface OrderTimeoutPublisher {

    void publishTimeout(String orderNo);
}
