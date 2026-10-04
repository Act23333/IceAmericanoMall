package org.icedamericanomall.notification;

import org.apache.ibatis.annotations.Mapper;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 通信服务启动类（纯通道发送）。
 *
 * <p>职责：把「要发什么」投递到「具体通道」——短信（阿里云 dysmsapi / Mock）、
 * 邮件（SMTP / Mock）、站内信（落库 {@code notification} 表，ADR-016 收尾）。
 * <b>不生成验证码、不做模板编排</b>；验证码签发与校验属于 verification-service。
 *
 * <p>组件扫描显式收窄到 {@code org.icedamericanomall.notification}：
 * 共享模块 Bean 一律走各模块的 {@code AutoConfiguration.imports} 注册
 * （ia-integration 的 sms / email / captcha 等客户端、ia-common 的
 * GlobalExceptionHandler 等），不通过组件扫描获得——
 * 与 ADR-009 的「imports 或扫描二选一」约定一致。
 * 本服务不使用 Feign，无需引入 ia-api 的 fallback 包。
 *
 * <p>站内信落库需要 MyBatis / MySQL（{@code @MapperScan} 指向 infrastructure/persistence）；
 * SMS / EMAIL 通道仍为纯投递、无状态。
 *
 * <p>本服务也<b>不使用 Redis</b>（V5.2 起 pom 中已无 {@code spring-boot-starter-data-redis}）：
 * ia-common 的 {@code RedisConfig} / {@code RedisLuaScriptConfig} / {@code RateLimitUtils} /
 * {@code RateLimitAspect} 已加 {@code @ConditionalOnClass} 守卫，类路径无 Redis 时整体跳过
 * （见 {@code project-docs/adr/012}）。若将来需要 {@code @RateLimit}，再引回该依赖即可。
 */
@SpringBootApplication(scanBasePackages = "org.icedamericanomall.notification")
@MapperScan(basePackages = "org.icedamericanomall.notification.infrastructure.persistence",
        annotationClass = Mapper.class)
public class NotificationApplication {

    public static void main(String[] args) {
        SpringApplication.run(NotificationApplication.class, args);
    }
}
