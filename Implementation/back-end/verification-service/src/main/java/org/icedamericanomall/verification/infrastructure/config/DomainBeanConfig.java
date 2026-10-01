package org.icedamericanomall.verification.infrastructure.config;

import org.icedamericanomall.verification.domain.gateway.NotificationGateway;
import org.icedamericanomall.verification.domain.model.VerificationPolicy;
import org.icedamericanomall.verification.domain.port.CodeGenerator;
import org.icedamericanomall.verification.domain.repository.VerificationCodeRepository;
import org.icedamericanomall.verification.domain.service.VerificationCodeIssuer;
import org.icedamericanomall.verification.domain.service.VerificationCodeVerifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 领域 Bean 装配（基础设施层）。
 *
 * <p>领域层是纯 POJO（无 {@code @Component} 等框架注解），依赖关系在此显式声明，
 * 保证 domain 包零框架依赖；策略参数从 {@link VerificationCodeProperties} 注入，
 * 领域服务本身只依赖自己定义的端口。
 */
@Configuration
public class DomainBeanConfig {

    /** 验证码策略：由配置装配为领域值对象（默认 6 位 / 5 分钟 / 60 秒 / 5 次 / 30 分钟）。 */
    @Bean
    public VerificationPolicy verificationPolicy(VerificationCodeProperties properties) {
        return properties.toPolicy();
    }

    @Bean
    public VerificationCodeIssuer verificationCodeIssuer(VerificationCodeRepository repository,
                                                         CodeGenerator codeGenerator,
                                                         NotificationGateway notificationGateway,
                                                         VerificationPolicy verificationPolicy) {
        return new VerificationCodeIssuer(repository, codeGenerator, notificationGateway, verificationPolicy);
    }

    @Bean
    public VerificationCodeVerifier verificationCodeVerifier(VerificationCodeRepository repository,
                                                             VerificationPolicy verificationPolicy) {
        return new VerificationCodeVerifier(repository, verificationPolicy);
    }
}
