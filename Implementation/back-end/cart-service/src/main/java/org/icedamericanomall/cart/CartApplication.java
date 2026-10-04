package org.icedamericanomall.cart;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

/**
 * cart-service 启动类（购物车：加购去重合并 / 数量变更 / 选中态 / 结算取数）。
 *
 * <p><b>组件扫描范围</b>：基础包限定为 {@code org.icedamericanomall.cart}，
 * 共享模块的 Bean 不在默认扫描路径，故显式声明：
 * <ul>
 *   <li>{@code org.icedamericanomall.cart} —— 本服务</li>
 *   <li>{@code org.icedamericanomall.fallback} —— ia-api 的 Feign 降级工厂
 *       （{@code @Component} 在 ia-api 内，需显式纳入，否则开启熔断后取不到 Bean）</li>
 * </ul>
 * 购物车视图需要经 {@code SkuClient} 回填商品信息，因此开启 Feign 客户端扫描（ADR-009）。
 */
@SpringBootApplication(scanBasePackages = {
        "org.icedamericanomall.cart",
        "org.icedamericanomall.fallback"
})
@MapperScan("org.icedamericanomall.cart.infrastructure.persistence")
@EnableFeignClients(basePackages = "org.icedamericanomall.client")
public class CartApplication {

    public static void main(String[] args) {
        SpringApplication.run(CartApplication.class, args);
    }
}
