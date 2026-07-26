package org.icedAmericanoMall.producer;

public interface OrderTimeoutPublisher {

    void publishTimeout(String orderNo);
}
