package org.icedamericanomall.auth;

import org.apache.ibatis.annotations.Mapper;
import org.icedamericanomall.config.DefaultFeignConfig;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

/**
 * 认证服务启动类。
 *
 * <p>Feign 客户端契约在 ia-api（{@code org.icedamericanomall.client}）中统一定义；
 * Mapper 只扫描基础设施层持久化包。
 *
 * <p><b>组件扫描范围</b>（本服务基础包是 {@code org.icedamericanomall.auth}，比其余服务深一层，
 * 因此必须显式声明，否则共享模块的 Bean 全部缺失）：
 * <ul>
 *   <li>{@code org.icedamericanomall.auth} —— 本服务四层</li>
 *   <li>{@code org.icedamericanomall.fallback} —— ia-api 的 Feign 降级工厂。
 *       <b>开启 Feign 熔断（R6）后 fallbackFactory 必须能取到 Bean</b>，
 *       仅靠 ia-api 里的 {@code @Component} 注解不够（ia-api 不在本服务的扫描路径内）</li>
 *   <li>{@code org.icedamericanomall.integration.auth} —— ia-integration 的微信第三方客户端
 *       （真实 / Mock 由 {@code wechat.*.enabled} 决定）。人机验证的第三方客户端不在其列：
 *       极验二次校验已收口到 verification-service</li>
 * </ul>
 *
 * <p>不扫描 {@code org.icedamericanomall.config}（ia-api 的 {@code DefaultFeignConfig}）：
 * 它是 Feign 专用配置类，需通过 {@code defaultConfiguration} 挂载，不能被全局扫描。
 */
@SpringBootApplication(scanBasePackages = {
        "org.icedamericanomall.auth",
        "org.icedamericanomall.fallback",
        "org.icedamericanomall.integration.auth"
})
@EnableFeignClients(basePackages = {"org.icedamericanomall.client"}, defaultConfiguration = DefaultFeignConfig.class)
@MapperScan(basePackages = "org.icedamericanomall.auth.infrastructure.persistence", annotationClass = Mapper.class)
public class AuthorizationApplication {

    public static void main(String[] args) {
        SpringApplication.run(AuthorizationApplication.class, args);
    }
}
