package org.noLazy.common.client.payment;

import java.util.Map;

public interface PaymentClient {

    /**
     * Initiate a payment and return a QR code URL / payment link.
     */
    String initiatePayment(String orderNo, int amount, String description);

    /**
     * Verify the signature of a payment callback from the payment provider.
     */
    boolean verifyCallback(Map<String, String> params);

    /**
     * Query the current status of a payment order.
     * @return status string (e.g. "SUCCESS", "NOTPAY", "CLOSED")
     */
    String queryStatus(String payOrderNo);
}
