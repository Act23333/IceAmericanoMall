package org.icedamericanomall.search;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

/**
 * search-service — 商品全文检索（ES 主链路 + item-service 检索端点降级，ADR-052）。
 *
 * <p><b>组件扫描范围</b>：基础包为 {@code org.icedamericanomall.search}（本服务）。
 * 共享模块的 Bean 由 ia-common 的 {@code AutoConfiguration.imports} 装配，无需扫描。
 *
 * <p><b>Feign 调用（ADR-052 直读退出）</b>：商品数据面归 item-service 所有——
 * LIKE 降级检索 / 联想词 / ES 重建数据源 / publishTime 回填经 ia-api {@code ItemSearchClient}
 * 在线获取，原直读 {@code product}/{@code sku} 镜像表的实现已退出；
 * 降级工厂（fail-closed 空结果）经 ia-api {@code DefaultFeignConfig} 登记。
 */
@SpringBootApplication(scanBasePackages = "org.icedamericanomall.search")
@EnableFeignClients(basePackages = "org.icedamericanomall.client")
public class SearchApplication {
    public static void main(String[] args) {
        SpringApplication.run(SearchApplication.class, args);
    }
}
