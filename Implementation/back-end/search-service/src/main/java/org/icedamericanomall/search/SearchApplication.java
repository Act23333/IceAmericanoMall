package org.icedamericanomall.search;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

/**
 * search-service — 商品全文检索 (ES 7.17 + DB LIKE 降级)。
 * V1.1 正式启用 ElasticSearch，当前默认 DB 模式。
 *
 * <p><b>组件扫描范围</b>：基础包随包名归位收窄为 {@code org.icedamericanomall.search}，
 * 共享模块的 Bean 不再落进默认扫描路径，故显式声明。
 * 不扫描 {@code org.icedamericanomall.config} —— 那是 ia-api 的 {@code DefaultFeignConfig}，
 * 属于 Feign 专用配置，只能通过 {@code defaultConfiguration} 挂载。
 * <ul>
 *   <li>{@code org.icedamericanomall.search} —— 本服务</li>
 *   <li>{@code org.icedamericanomall.fallback} —— ia-api 的 Feign 降级工厂</li>
 * </ul>
 */
@SpringBootApplication(scanBasePackages = {
        "org.icedamericanomall.search",
        "org.icedamericanomall.fallback"
})
@MapperScan("org.icedamericanomall.search.mapper")
@EnableFeignClients(basePackages = "org.icedamericanomall.client")
public class SearchApplication {
    public static void main(String[] args) {
        SpringApplication.run(SearchApplication.class, args);
    }
}
