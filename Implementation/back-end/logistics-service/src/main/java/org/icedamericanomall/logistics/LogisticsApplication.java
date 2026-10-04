package org.icedamericanomall.logistics;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

/**
 * logistics-service 启动类（物流记录 / 状态跟踪）。
 *
 * <p><b>组件扫描范围</b>：基础包为 {@code org.icedamericanomall.logistics}，
 * 共享模块的 Bean 不在默认扫描路径内，故显式补充：
 * <ul>
 *   <li>{@code org.icedamericanomall.logistics} —— 本服务</li>
 *   <li>{@code org.icedamericanomall.fallback} —— ia-api 的 Feign 降级工厂（订单归属校验 fail-closed）</li>
 * </ul>
 * 不扫描 {@code org.icedamericanomall.config} —— 那是 ia-api 的 {@code DefaultFeignConfig}，
 * 只能通过 Feign 的 {@code defaultConfiguration} 挂载。
 */
@SpringBootApplication(scanBasePackages = {
        "org.icedamericanomall.logistics",
        "org.icedamericanomall.fallback"
})
@MapperScan("org.icedamericanomall.logistics.infrastructure.persistence")
@EnableFeignClients(basePackages = "org.icedamericanomall.client")
public class LogisticsApplication {

    public static void main(String[] args) {
        SpringApplication.run(LogisticsApplication.class, args);
    }
}
