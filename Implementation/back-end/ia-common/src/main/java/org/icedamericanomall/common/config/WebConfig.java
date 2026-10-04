package org.icedamericanomall.common.config;

import org.icedamericanomall.common.interceptor.AccessLogInterceptor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.DispatcherServlet;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * @ClassName: WebConfig
 * @Description: TODO
 * @Author: noLazy
 * @Date: 2026/3/23 22:33
 * @Version: 1.0.0
 * @ProjectName: IcedAmericanoMall
 * @Package: org.icedamericanomall.common.config
 */
@Configuration
// 守卫：仅 Servlet Web 应用才需要注册拦截器；无 Web（或 WebFlux）环境跳过本配置
@ConditionalOnClass(DispatcherServlet.class)
public class WebConfig implements WebMvcConfigurer {

    /** 拦截全部路径（拦截器内自行决定放行策略）。 */
    private static final String ALL_PATH_PATTERN = "/**";

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        HandlerInterceptor userInteceptor = new AccessLogInterceptor();
        registry.addInterceptor(userInteceptor).addPathPatterns(ALL_PATH_PATTERN);
    }
}
