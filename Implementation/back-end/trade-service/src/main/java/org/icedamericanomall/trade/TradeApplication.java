package org.icedamericanomall.trade;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

/**
 * trade-service 启动类（订单中心：下单编排 / 状态流转 / 超时关闭）。
 *
 * <p><b>组件扫描范围</b>：基础包随包名归位收窄为 {@code org.icedamericanomall.trade}，
 * 共享模块的 Bean 不再落进默认扫描路径，故显式声明。
 * 不扫描 {@code org.icedamericanomall.config} —— 那是 ia-api 的 {@code DefaultFeignConfig}，
 * 属于 Feign 专用配置，只能通过 {@code defaultConfiguration} 挂载。
 * <ul>
 *   <li>{@code org.icedamericanomall.trade} —— 本服务</li>
 *   <li>{@code org.icedamericanomall.fallback} —— ia-api 的 Feign 降级工厂。
 *       本服务启用了 {@code feign.sentinel.enabled=true}，Sentinel 目标器构建客户端时
 *       会按类型取 {@code fallbackFactory} Bean，缺了会启动失败</li>
 * </ul>
 *
 * <p>超时关单已全部迁移至 RocketMQ 延迟消息 + XXL-Job 兜底（{@code @XxlJob} 由
 * ia-common 的 {@code XxlJobConfig} 注册执行器，不依赖 Spring {@code @Scheduled}），
 * 故不再开启 {@code @EnableScheduling}。
 */
@SpringBootApplication(scanBasePackages = {
        "org.icedamericanomall.trade",
        "org.icedamericanomall.fallback"
})
@MapperScan("org.icedamericanomall.trade.mapper")
@EnableFeignClients(basePackages = "org.icedamericanomall.client")
public class TradeApplication {

    public static void main(String[] args) {
        SpringApplication.run(TradeApplication.class, args);
    }
}

