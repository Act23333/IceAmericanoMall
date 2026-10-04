package org.icedamericanomall.item;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

/**
 * item-service 启动类（商品 / SKU / 分类 / 评价 / 首页配置）。
 *
 * <p><b>组件扫描范围</b>：基础包随包名归位收窄为 {@code org.icedamericanomall.item}，
 * 共享模块的 Bean 不再落进默认扫描路径，故显式声明。
 * 不扫描 {@code org.icedamericanomall.config} —— 那是 ia-api 的 {@code DefaultFeignConfig}，
 * 属于 Feign 专用配置，只能通过 {@code defaultConfiguration} 挂载。
 * <ul>
 *   <li>{@code org.icedamericanomall.item} —— 本服务</li>
 *   <li>{@code org.icedamericanomall.fallback} —— ia-api 的 Feign 降级工厂。
 *       {@code @Component} 在 ia-api 内、不在本服务扫描路径，需显式纳入，
 *       否则开启熔断后 {@code fallbackFactory} 取不到 Bean</li>
 * </ul>
 */
@SpringBootApplication(scanBasePackages = {
        "org.icedamericanomall.item",
        "org.icedamericanomall.fallback"
})
@MapperScan("org.icedamericanomall.item.infrastructure.persistence")
@EnableFeignClients(basePackages = "org.icedamericanomall.client")
public class ItemApplication {

    public static void main(String[] args) {
        SpringApplication.run(ItemApplication.class, args);
    }
}
