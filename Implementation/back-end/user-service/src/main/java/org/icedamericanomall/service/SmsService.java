package org.icedamericanomall.service;

import org.icedamericanomall.domain.dto.SmsCodeSendReq;

public interface SmsService {
    void sendCode(SmsCodeSendReq req);
    void verifyCode(String phone, String code);
}
