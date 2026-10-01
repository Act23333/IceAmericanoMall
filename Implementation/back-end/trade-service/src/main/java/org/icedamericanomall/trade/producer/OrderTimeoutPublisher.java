package org.icedamericanomall.trade.producer;

public interface OrderTimeoutPublisher {

    void publishTimeout(String orderNo);
}
