package org.icedamericanomall.common.config;

import org.icedamericanomall.common.lock.DistributedLock;
import org.icedamericanomall.common.lock.RedissonDistributedLockAdapter;
import org.redisson.api.RedissonClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 业务键互斥锁装配（V5.4 归并，authorization-service / user-service 的重复副本收敛到 ia-common）。
 *
 * <p>{@code @ConditionalOnClass(RedissonClient.class)} 守卫：ia-common 对
 * {@code redisson-spring-boot-starter} 的依赖是 <b>optional</b>，未引入 Redisson 的服务
 * 类路径上没有 {@code RedissonClient}，本配置整体跳过（与 {@link RedisConfig} 同一手法）。
 *
 * <p>注册方式：{@code META-INF/spring/...AutoConfiguration.imports}（ia-common 的包不在各服务
 * 组件扫描路径内，{@code @Component} 扫描不到）。{@code @ConditionalOnMissingBean} 允许
 * 调用方以自定义 Bean 覆盖（如测试替身）。
 */
@Configuration
@ConditionalOnClass(RedissonClient.class)
public class DistributedLockConfig {

    /**
     * 业务键互斥锁 Bean（Redisson 实现）。
     *
     * @param redissonClient Redisson 客户端（由 redisson-spring-boot-starter 自动配置提供）
     * @return 领域/应用层可注入的互斥锁能力
     */
    @Bean
    @ConditionalOnMissingBean(DistributedLock.class)
    public DistributedLock distributedLock(RedissonClient redissonClient) {
        return new RedissonDistributedLockAdapter(redissonClient);
    }
}
