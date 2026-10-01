package org.icedamericanomall.user.service;

import org.icedamericanomall.user.domain.dto.SmsCodeSendReq;

public interface SmsService {
    void sendCode(SmsCodeSendReq req);
    void verifyCode(String phone, String code);
}
