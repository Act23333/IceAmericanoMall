package org.icedamericanomall.producer;

public interface OrderTimeoutPublisher {

    void publishTimeout(String orderNo);
}
