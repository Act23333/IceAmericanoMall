package org.icedAmericanoMall.service;

import com.baomidou.mybatisplus.extension.service.IService;
import org.icedAmericanoMall.domain.dto.UpdateProfileReq;
import org.icedAmericanoMall.domain.entity.UserEntity;
import org.icedAmericanoMall.domain.vo.UserInfoResp;

public interface UserService extends IService<UserEntity> {
    UserInfoResp getByUserId(Long userId);
    void updateProfile(Long userId, UpdateProfileReq req);
    long countUsers();
}
