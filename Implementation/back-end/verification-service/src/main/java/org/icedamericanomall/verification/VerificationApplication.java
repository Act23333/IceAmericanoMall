package org.icedamericanomall.verification;

import org.icedamericanomall.config.DefaultFeignConfig;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

/**
 * 校验服务启动类。
 *
 * <p>职责（「校验能力」的唯一出口）：
 * <ul>
 *   <li>验证码签发 / 校验：生成 6 位数字码 → Redis（TTL 5 分钟 + 60 秒重发限流 + 失败次数上限）→
 *       经 notification-service 投递（短信 / 邮件）</li>
 *   <li>人机验证：极验 Geetest v4 二次校验</li>
 * </ul>
 *
 * <p>验证码状态只存 Redis、不落库，因此不引入 MyBatis / MySQL，也不声明 {@code @MapperScan}。
 *
 * <p>组件扫描显式收窄：
 * <ul>
 *   <li>{@code org.icedamericanomall.verification} —— 本服务四层包；</li>
 *   <li>{@code org.icedamericanomall.fallback} —— ia-api 的 Feign 降级工厂
 *       （{@code VerificationClientFallback} 必须进容器，熔断降级才生效，见 ADR-007）；</li>
 *   <li>ia-integration 第三方客户端（captcha 组）与 ia-common 配置走各自
 *       {@code AutoConfiguration.imports} 注册，不经扫描——与 ADR-009
 *       「imports 或扫描二选一」约定一致。</li>
 * </ul>
 */
@SpringBootApplication(scanBasePackages = {"org.icedamericanomall.verification", "org.icedamericanomall.fallback"})
@EnableFeignClients(basePackages = "org.icedamericanomall.client", defaultConfiguration = DefaultFeignConfig.class)
public class VerificationApplication {

    public static void main(String[] args) {
        SpringApplication.run(VerificationApplication.class, args);
    }
}
