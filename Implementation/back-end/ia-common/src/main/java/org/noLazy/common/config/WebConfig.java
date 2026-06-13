package org.noLazy.common.config;

import org.noLazy.common.interceptor.UserInterceptor;
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
 * @Package: org.noLazy.common.config
 */
@Configuration
@ConditionalOnClass(DispatcherServlet.class) //当 Spring 容器中还没有 MybatisPlusInterceptor 类型的 Bean 时，才创建这个 Bean即使 com.example.SomeService 这个类在项目编译时不存在，上面的代码也能正常编译。这是因为 Spring Boot 在解析这个注解时，采用了 ASM 字节码解析技术，不会强制在编译期加载 value 属性中指定的类
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        HandlerInterceptor userInteceptor = new UserInterceptor();
        registry.addInterceptor(userInteceptor).addPathPatterns("/**");
    }
}
