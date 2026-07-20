package org.icedAmericanoMall.config;


import feign.Logger;
import feign.RequestInterceptor;

import org.icedAmericanoMall.fallback.UserClientFallback;
import org.noLazy.common.utils.UserContext;
import org.springframework.context.annotation.Bean;

/**
 * @ClassName: DefaultFeignConfig
 * @Description: TODO
 * @Author: noLazy
 * @Date: 2025/8/20 21:52
 * @Version: 1.0.0
 * @ProjectName: hmall
 * @Package: com.hmall.api.config
 */
public class DefaultFeignConfig {

    @Bean
    public Logger.Level feignLoggerLevel() {
        return Logger.Level.FULL;
    }

    @Bean
    public RequestInterceptor requestInterceptor() {
        return requestTemplate -> {
            var userInfo = UserContext.getUser();
            if (userInfo != null) {
                requestTemplate.header("X-User-Id", String.valueOf(userInfo.userId()));
            }
        };
    }

    @Bean//为什么不使用@Component注解直接放入ioc容器，因为这个模块专门用来导入的用来调用其他服务的接口，所以不会扫描这个模块中的包，但这个配置类会被放到使用这个模块的ioc容器中
    public UserClientFallback userClientFallback() {
        return new UserClientFallback();
    }


}
