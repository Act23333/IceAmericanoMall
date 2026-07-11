package org.icedAmericanoMall.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import org.icedAmericanoMall.domain.dto.UpdateProfileReq;
import org.icedAmericanoMall.domain.entity.UserEntity;
import org.icedAmericanoMall.domain.vo.UserInfoResp;

public interface UserService extends IService<UserEntity> {
    UserInfoResp getByUserId(Long userId);
    void updateProfile(Long userId, UpdateProfileReq req);
    long countUsers();

    /** 管理后台：分页查询用户列表。 */
    IPage<UserInfoResp> pageUsers(int page, int size);

    /** 管理后台：启用/禁用用户（status 见 UserStatusEnum）。 */
    void updateStatus(Long id, Integer status);

    /** 管理后台：设置用户角色（0=USER,1=SELLER,2=ADMIN）。 */
    void updateRole(Long id, Integer roleType);
}
