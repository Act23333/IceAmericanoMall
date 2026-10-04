package org.icedamericanomall.gateway.config;

import org.icedamericanomall.gateway.util.EdgePeerIpResolver;
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import reactor.core.publisher.Mono;

/**
 * 网关限流 Key 解析器。
 *
 * <p>限流配置在 {@code application.yml} 的路由 {@code RequestRateLimiter} 过滤器中引用
 * （{@code key-resolver: "#{@ipKeyResolver}"}），当前生效范围：
 * <ul>
 *   <li>{@code auth-service}（{@code /api/auth/**}）—— 登录/注册类端点按客户端 IP 限流；</li>
 *   <li>{@code verification-service}（{@code /api/verification/**}）—— 发码端点更严格限流。</li>
 * </ul>
 *
 * <p>IP 口径：真实 TCP 对端地址，不读可伪造的 {@code X-Forwarded-For}
 * （见 {@link EdgePeerIpResolver}；下游服务侧的对应口径是 ia-common 的
 * {@code IpUtil}，两者分工见该类 Javadoc）。此处仅保留实际引用的 Bean，删除未被任何路由引用的
 * user/path 两个 KeyResolver，避免"配置存在但从不生效"的误导。
 */
@Configuration
public class RateLimiterConfig {

    /**
     * 按客户端 IP 限流（认证/发码类路由使用）。
     *
     * <p>解析不到对端地址时返回 {@link EdgePeerIpResolver#UNKNOWN}，使这类请求共享同一个桶
     * （宁可限流偏严，也不放行成无限额通道）。
     */
    @Bean
    @Primary
    public KeyResolver ipKeyResolver() {
        return exchange -> Mono.just(EdgePeerIpResolver.resolve(exchange));
    }
}
