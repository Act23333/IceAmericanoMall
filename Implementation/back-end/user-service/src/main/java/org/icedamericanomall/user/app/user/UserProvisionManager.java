package org.icedamericanomall.user.app.user;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.icedamericanomall.dto.UserAuthCO;
import org.icedamericanomall.user.domain.model.user.User;
import org.icedamericanomall.user.domain.service.UserProvisioner;
import org.springframework.stereotype.Component;

/**
 * 跨服务幂等建档（应用层编排）—— ADR-006 的内部端点 {@code /internal/user/getOrCreate}
 * 用例：按登录标识查找或创建用户，返回技术主键。
 *
 * <p>幂等性由领域服务 {@link UserProvisioner} 保证（先查短路 + 业务键互斥锁 + 唯一键冲突回查）；
 * 新建分支在同一漏斗内完成（默认昵称、user 行、默认角色、建档奖励），
 * 事务与落库编排见 {@code UserRegistrar}。
 *
 * <p>幂等口径（V5.2）：PHONE / EMAIL 按盲索引（phone_hash / email_hash）短路；
 * WECHAT 类标识的 user 表无定位列，幂等由调用方凭证唯一键（user_auth）保证（ADR-017）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UserProvisionManager {

    private final UserProvisioner userProvisioner;

    /**
     * 按登录标识查找或创建用户（ADR-006 幂等端点，authorization-service 注册/登录后回查技术主键）。
     *
     * @param userAuthDTO 登录标识契约对象（identityType + identifier + nickname）
     * @return 用户技术主键（跨服务 user_id 口径）
     */
    public Long getOrCreateUser(UserAuthCO userAuthDTO) {
        User user = userProvisioner.findOrCreate(
                userAuthDTO.getIdentityType(), userAuthDTO.getIdentifier(), userAuthDTO.getNickname());
        return user.getId();
    }
}
