package org.icedamericanomall.wallet;

import org.apache.ibatis.annotations.Mapper;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 钱包服务启动类 —— 余额账户域（资金域）。
 *
 * <p>职责边界：只回答"这个用户还有多少钱"——查询、充值（含退款入账）、支付扣减。
 * 扣减原子性由单语句条件 UPDATE 承担（余额不足影响 0 行，与迁出前 user 表的实现同构）；
 * 账户行懒创建：入账 upsert 兜底初始化，无账户即无余额。
 *
 * <p>组件扫描显式收窄到 {@code org.icedamericanomall.wallet}：共享模块 Bean 走
 * 各模块 AutoConfiguration 注册（ADR-009），不通过组件扫描获得。
 * 本服务不使用 Feign，无需引入 ia-api 的 fallback 包。
 */
@SpringBootApplication(scanBasePackages = "org.icedamericanomall.wallet")
@MapperScan(basePackages = "org.icedamericanomall.wallet.infrastructure.persistence",
        annotationClass = Mapper.class)
public class WalletApplication {

    public static void main(String[] args) {
        SpringApplication.run(WalletApplication.class, args);
    }
}
