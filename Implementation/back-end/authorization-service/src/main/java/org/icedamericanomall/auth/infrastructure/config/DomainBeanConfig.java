package org.icedamericanomall.auth.infrastructure.config;

import org.icedamericanomall.auth.domain.ability.auth.LoginStrategy;
import org.icedamericanomall.auth.domain.ability.auth.LoginStrategyFactory;
import org.icedamericanomall.auth.domain.ability.auth.impl.EmailLoginStrategy;
import org.icedamericanomall.auth.domain.ability.auth.impl.PasswordLoginStrategy;
import org.icedamericanomall.auth.domain.ability.auth.impl.SmsLoginStrategy;
import org.icedamericanomall.auth.domain.ability.auth.impl.WechatMiniLoginStrategy;
import org.icedamericanomall.auth.domain.ability.auth.impl.WechatWebLoginStrategy;
import org.icedamericanomall.auth.domain.ability.risk.RiskHandler;
import org.icedamericanomall.auth.domain.ability.risk.RiskHandlerFactory;
import org.icedamericanomall.auth.domain.ability.risk.impl.AccountFreezeHandler;
import org.icedamericanomall.auth.domain.ability.risk.impl.CaptchaHandler;
import org.icedamericanomall.auth.domain.ability.risk.impl.PassHandler;
import org.icedamericanomall.auth.domain.gateway.UserAccountGateway;
import org.icedamericanomall.auth.domain.gateway.WechatWebLoginGateway;
import org.icedamericanomall.auth.domain.port.DistributedLock;
import org.icedamericanomall.auth.domain.port.PasswordVerifier;
import org.icedamericanomall.auth.domain.port.VerificationCodeService;
import org.icedamericanomall.auth.domain.port.WechatMiniSessionService;
import org.icedamericanomall.auth.domain.repository.UserAuthRepository;
import org.icedamericanomall.auth.domain.service.UserAuthProvisioner;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * 领域 Bean 装配（基础设施层）。
 *
 * <p>领域层是纯 POJO（无 {@code @Component} 等框架注解），依赖注入关系在此显式声明，
 * 保证 domain 包零框架依赖，同时保持依赖倒置（领域只依赖自己定义的端口）。
 */
@Configuration
@EnableConfigurationProperties(CaptchaProperties.class)
public class DomainBeanConfig {

    // ========== 领域服务 ==========

    @Bean
    public UserAuthProvisioner userAuthProvisioner(UserAuthRepository userAuthRepository,
                                                   UserAccountGateway userAccountGateway,
                                                   DistributedLock distributedLock) {
        return new UserAuthProvisioner(userAuthRepository, userAccountGateway, distributedLock);
    }

    // ========== 登录策略 ==========

    @Bean
    public PasswordLoginStrategy passwordLoginStrategy(UserAuthRepository userAuthRepository,
                                                       PasswordVerifier passwordVerifier) {
        return new PasswordLoginStrategy(userAuthRepository, passwordVerifier);
    }

    @Bean
    public SmsLoginStrategy smsLoginStrategy(VerificationCodeService verificationCodeService,
                                             UserAuthProvisioner userAuthProvisioner) {
        return new SmsLoginStrategy(verificationCodeService, userAuthProvisioner);
    }

    @Bean
    public EmailLoginStrategy emailLoginStrategy(VerificationCodeService verificationCodeService,
                                                 UserAuthProvisioner userAuthProvisioner) {
        return new EmailLoginStrategy(verificationCodeService, userAuthProvisioner);
    }

    @Bean
    public WechatMiniLoginStrategy wechatMiniLoginStrategy(
            WechatMiniSessionService wechatMiniSessionService,
            UserAuthProvisioner userAuthProvisioner) {
        return new WechatMiniLoginStrategy(wechatMiniSessionService, userAuthProvisioner);
    }

    @Bean
    public WechatWebLoginStrategy wechatWebLoginStrategy(WechatWebLoginGateway wechatWebLoginGateway) {
        return new WechatWebLoginStrategy(wechatWebLoginGateway);
    }

    @Bean
    public LoginStrategyFactory loginStrategyFactory(List<LoginStrategy> strategies) {
        return new LoginStrategyFactory(strategies);
    }

    // ========== 风控处置 ==========

    @Bean
    public AccountFreezeHandler accountFreezeHandler() {
        return new AccountFreezeHandler();
    }

    @Bean
    public CaptchaHandler captchaHandler(CaptchaProperties captchaProperties) {
        return new CaptchaHandler(captchaProperties.getProviderMapping(),
                captchaProperties.getDefaultProvider(),
                captchaProperties.getCaptchaId(),
                captchaProperties.isEnabled());
    }

    @Bean
    public PassHandler passHandler() {
        return new PassHandler();
    }

    @Bean
    public RiskHandlerFactory riskHandlerFactory(List<RiskHandler> riskHandlers) {
        return new RiskHandlerFactory(riskHandlers);
    }
}
