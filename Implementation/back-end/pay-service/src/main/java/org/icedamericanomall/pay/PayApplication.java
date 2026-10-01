package org.icedamericanomall.pay;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * pay-service 启动类（支付单 / 渠道路由 / 回调 / 超时关单）。
 *
 * <p><b>组件扫描范围</b>：基础包随包名归位收窄为 {@code org.icedamericanomall.pay}，
 * 共享模块的 Bean 不再落进默认扫描路径，故显式声明。
 * 不扫描 {@code org.icedamericanomall.config} —— 那是 ia-api 的 {@code DefaultFeignConfig}，
 * 属于 Feign 专用配置，只能通过 {@code defaultConfiguration} 挂载。
 * <ul>
 *   <li>{@code org.icedamericanomall.pay} —— 本服务</li>
 *   <li>{@code org.icedamericanomall.fallback} —— ia-api 的 Feign 降级工厂</li>
 * </ul>
 *
 * <p>支付渠道客户端（{@code PaymentClient} / {@code AlipayPaymentClient} 及
 * {@code WechatPayProperties} / {@code AlipayProperties}）<b>不在此扫描</b>：
 * 它们由 ia-integration 的 {@code AutoConfiguration.imports} 统一登记，
 * 扫描与导入二选一，避免同一个类被注册两次。
 */
@SpringBootApplication(scanBasePackages = {
        "org.icedamericanomall.pay",
        "org.icedamericanomall.fallback"
})
@MapperScan("org.icedamericanomall.pay.mapper")
@EnableFeignClients(basePackages = "org.icedamericanomall.client")
@EnableScheduling
public class PayApplication {

    public static void main(String[] args) {
        SpringApplication.run(PayApplication.class, args);
    }
}
