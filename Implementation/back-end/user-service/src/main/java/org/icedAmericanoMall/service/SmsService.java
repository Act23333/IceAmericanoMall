package org.icedAmericanoMall.service;

import org.icedAmericanoMall.domain.dto.SmsCodeSendReq;

public interface SmsService {
    void sendCode(SmsCodeSendReq req);
    void verifyCode(String phone, String code);
}
