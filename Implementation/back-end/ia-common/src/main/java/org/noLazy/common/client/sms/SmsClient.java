package org.noLazy.common.client.sms;

public interface SmsClient {
    void send(String phone, String code);
}