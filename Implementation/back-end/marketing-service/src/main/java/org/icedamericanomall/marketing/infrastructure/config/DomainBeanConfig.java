package org.icedamericanomall.marketing.infrastructure.config;

import org.icedamericanomall.marketing.domain.service.SignInPointsPolicy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 领域服务装配（基础设施层）—— domain 包保持框架无关，纯计算领域服务的 Bean 在此注册。
 */
@Configuration
public class DomainBeanConfig {

    /** 签到积分策略（纯计算，无依赖）。 */
    @Bean
    public SignInPointsPolicy signInPointsPolicy() {
        return new SignInPointsPolicy();
    }
}
