package org.icedamericanomall.notification.infrastructure.config;

import org.icedamericanomall.notification.domain.port.EmailChannelPort;
import org.icedamericanomall.notification.domain.port.SmsChannelPort;
import org.icedamericanomall.notification.domain.service.NotificationDispatcher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 领域 Bean 装配（基础设施层）。
 *
 * <p>领域层是纯 POJO（无 {@code @Component} 等框架注解），依赖关系在此显式声明，
 * 保证 {@code domain} 包零框架依赖，同时保持依赖倒置（领域只依赖自己定义的端口）。
 */
@Configuration
public class DomainBeanConfig {

    @Bean
    public NotificationDispatcher notificationDispatcher(SmsChannelPort smsChannelPort,
                                                         EmailChannelPort emailChannelPort) {
        return new NotificationDispatcher(smsChannelPort, emailChannelPort);
    }
}
