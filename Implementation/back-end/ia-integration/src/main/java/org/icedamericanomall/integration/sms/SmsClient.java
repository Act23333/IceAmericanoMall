package org.icedamericanomall.integration.sms;

/**
 * 短信发送客户端接口。
 */
public interface SmsClient {
    void send(String phone, String code);
}
