package org.icedamericanomall.user;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * @ClassName: UserApplication
 * @Description: TODO
 * @Author: noLazy
 * @Date: 2026/3/13 15:15
 * @Version: 1.0.0
 * @ProjectName: IcedAmericanoMall
 * @Package: org.icedamericanomall.user
 *
 * <p><b>组件扫描范围</b>：本服务基础包为 {@code org.icedamericanomall.user}，
 * 默认扫描不再覆盖共享模块（ia-integration 的第三方客户端），因此显式声明。
 * 不扫描 {@code org.icedamericanomall.config} —— 那是 ia-api 的 {@code DefaultFeignConfig}，
 * 属于 Feign 专用配置，只能通过 {@code defaultConfiguration} 挂载。
 * <ul>
 *   <li>{@code org.icedamericanomall.user} —— 本服务</li>
 *   <li>{@code org.icedamericanomall.integration.auth} —— ia-integration 微信第三方客户端
 *       （真实 / Mock 由 {@code wechat.*.enabled} 决定）</li>
 * </ul>
 *
 * <p>短信 / 极验 / 对象存储客户端（{@code integration.sms / captcha / storage}）
 * <b>不在此扫描</b>：它们由 ia-integration 的 {@code AutoConfiguration.imports} 统一登记，
 * 扫描与导入二选一，避免同一个类被注册两次。
 */
@SpringBootApplication(scanBasePackages = {
        "org.icedamericanomall.user",
        "org.icedamericanomall.integration.auth"
})
@MapperScan("org.icedamericanomall.user.mapper")
public class UserApplication {
    public static void main(String[] args) {
        SpringApplication.run(UserApplication.class, args);
    }
}
