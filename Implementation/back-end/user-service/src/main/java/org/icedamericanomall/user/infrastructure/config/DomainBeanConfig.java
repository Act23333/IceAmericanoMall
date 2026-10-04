package org.icedamericanomall.user.infrastructure.config;

import org.icedamericanomall.common.lock.DistributedLock;
import org.icedamericanomall.user.domain.port.NewUserRegistrar;
import org.icedamericanomall.user.domain.port.NewUserRewardService;
import org.icedamericanomall.user.domain.repository.UserRepository;
import org.icedamericanomall.user.domain.service.UserProvisioner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 领域 Bean 装配（基础设施层）。
 *
 * <p>领域层是纯 POJO（无 {@code @Component} 等框架注解），依赖注入关系在此显式声明，
 * 保证 domain 包零框架依赖，同时保持依赖倒置（领域只依赖自己定义的端口）。
 * 与 authorization-service 的 {@code DomainBeanConfig} 同构（严格复制已验证模式）。
 *
 * <p>边界重构（ADR-016 收尾/⑤）：商家域（入驻/店铺/关注）已整体迁出至 merchant-service；
 * RBAC 归属 authorization-service（ADR-018）——本装配仅剩用户档案域的领域服务。
 */
@Configuration
public class DomainBeanConfig {

    // ========== 领域服务 ==========

    @Bean
    public UserProvisioner userProvisioner(UserRepository userRepository,
                                           DistributedLock distributedLock,
                                           NewUserRegistrar newUserRegistrar,
                                           NewUserRewardService newUserRewardService) {
        return new UserProvisioner(userRepository, distributedLock, newUserRegistrar, newUserRewardService);
    }
}
