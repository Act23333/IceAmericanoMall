package org.icedAmericanoMall.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import org.icedAmericanoMall.domain.dto.UpdateProfileReq;
import org.icedAmericanoMall.domain.entity.UserEntity;
import org.icedAmericanoMall.domain.vo.UserInfoResp;

import java.util.Map;

public interface UserService extends IService<UserEntity> {
    UserInfoResp getByUserId(Long userId);
    long countUsers();

    /** 管理后台：分页查询用户列表。 */
    IPage<UserInfoResp> pageUsers(int page, int size);

    /** 管理后台：启用/禁用用户（status 见 UserStatusEnum）。 */
    void updateStatus(Long id, Integer status);

    /** 管理后台：设置用户角色（0=USER,1=SELLER,2=ADMIN）。 */
    void updateRole(Long id, Integer roleType);

    /** 查询账户余额（单位：分）。 */
    int getBalance(Long userId);

    /** 原子扣减余额（余额支付）；余额不足抛 BALANCE_INSUFFICIENT。 */
    void deductBalance(Long userId, int amount);

    /** 增加余额（充值/退款）。 */
    void addBalance(Long userId, int amount);

    // patchProfile 已迁移到 UserProfileService（V3.2 DDD 分层重构）
}
