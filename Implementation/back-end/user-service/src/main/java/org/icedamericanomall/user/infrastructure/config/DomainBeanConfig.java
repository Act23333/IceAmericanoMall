package org.icedamericanomall.user.infrastructure.config;

import org.icedamericanomall.user.domain.port.DistributedLock;
import org.icedamericanomall.user.domain.port.SmsCodeStore;
import org.icedamericanomall.user.domain.repository.SellerRepository;
import org.icedamericanomall.user.domain.repository.UserRepository;
import org.icedamericanomall.user.domain.service.SellerRegistrar;
import org.icedamericanomall.user.domain.service.SignInPointsPolicy;
import org.icedamericanomall.user.domain.service.SmsCodeVerifier;
import org.icedamericanomall.user.domain.service.UserProvisioner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 领域 Bean 装配（基础设施层）。
 *
 * <p>领域层是纯 POJO（无 {@code @Component} 等框架注解），依赖注入关系在此显式声明，
 * 保证 domain 包零框架依赖，同时保持依赖倒置（领域只依赖自己定义的端口）。
 * 与 authorization-service 的 {@code DomainBeanConfig} 同构（严格复制已验证模式）。
 */
@Configuration
public class DomainBeanConfig {

    // ========== 领域服务 ==========

    @Bean
    public UserProvisioner userProvisioner(UserRepository userRepository,
                                           DistributedLock distributedLock) {
        return new UserProvisioner(userRepository, distributedLock);
    }

    @Bean
    public SellerRegistrar sellerRegistrar(SellerRepository sellerRepository) {
        return new SellerRegistrar(sellerRepository);
    }

    @Bean
    public SignInPointsPolicy signInPointsPolicy() {
        return new SignInPointsPolicy();
    }

    @Bean
    public SmsCodeVerifier smsCodeVerifier(SmsCodeStore smsCodeStore) {
        return new SmsCodeVerifier(smsCodeStore);
    }
}
