package org.icedamericanomall.merchant;

import org.apache.ibatis.annotations.Mapper;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

/**
 * 商家域服务启动类 —— 入驻申请/审核、商家身份（{@code seller}）、店铺档案与公开店铺页
 * （{@code shop}，1 商家 : N 店铺，ADR-019）、店铺关注。
 *
 * <p>归属说明（ADR-018 延伸的边界重构）：商家身份（{@code seller} 表）、店铺档案与公开店铺页
 * （{@code shop} 表，V5.4 拆分）、关注关系（Redis Set）与入驻审核自 user-service 迁入本服务；
 * 卖家角色的授予经 ia-api {@code AuthContextClient} 调 authorization-service（授权归授权域）。
 *
 * <p>组件扫描显式收窄到 {@code org.icedamericanomall.merchant}；
 * Feign 降级工厂走 {@code org.icedamericanomall.fallback}（ADR-009）。
 */
@SpringBootApplication(scanBasePackages = {
        "org.icedamericanomall.merchant",
        "org.icedamericanomall.fallback"
})
@EnableFeignClients(basePackages = "org.icedamericanomall.client")
@MapperScan(basePackages = "org.icedamericanomall.merchant.infrastructure.persistence",
        annotationClass = Mapper.class)
public class MerchantApplication {

    public static void main(String[] args) {
        SpringApplication.run(MerchantApplication.class, args);
    }
}
