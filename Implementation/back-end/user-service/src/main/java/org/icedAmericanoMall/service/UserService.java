package org.icedAmericanoMall.service;

import com.baomidou.mybatisplus.extension.service.IService;
import org.icedAmericanoMall.domain.dto.SmsCodeSendReq;
import org.icedAmericanoMall.domain.entity.UserEntity;
import org.icedAmericanoMall.domain.vo.UserInfoResp;
import org.icedAmericanoMall.dto.*;


public interface UserService extends IService<UserEntity> {
    /**
     * 用户注册
     * @param registerReqDTO
     * @return 用户注册成功视图
     */
     LoginRespDTO register(RegisterReqDTO registerReqDTO);

    void sendSmsCode(SmsCodeSendReq smsCodeSendReq);

    LoginRespDTO loginByPassword(PasswordLoginReqDTO passwordLoginDTO);

    LoginRespDTO loginBySms(SmsLoginReqDTO smsLoginDTO);

    UserInfoResp getByUserId(Long userId);

    void updateProfile(Long userId, String avatar);
}
