package org.icedAmericanoMall.service;

import org.icedAmericanoMall.dto.LoginRespDTO;
import org.icedAmericanoMall.dto.PasswordLoginReqDTO;
import org.icedAmericanoMall.dto.RegisterReqDTO;
import org.icedAmericanoMall.dto.SmsLoginReqDTO;

public interface AuthService {
    LoginRespDTO register(RegisterReqDTO request);
    LoginRespDTO loginByPassword(PasswordLoginReqDTO request);
    LoginRespDTO loginBySms(SmsLoginReqDTO request);
}
