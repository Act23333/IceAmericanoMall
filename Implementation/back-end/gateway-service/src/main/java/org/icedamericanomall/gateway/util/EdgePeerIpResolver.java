package org.icedamericanomall.gateway.util;

import org.springframework.web.server.ServerWebExchange;

import java.net.InetSocketAddress;

/**
 * 网关侧<b>边缘对端地址</b>解析（不可伪造口径）。
 *
 * <p>只取 TCP 连接对端地址（{@code remoteAddress}），<b>绝不</b>读取
 * {@code X-Forwarded-For} 等可伪造的代理头 —— 这些入站头已被
 * {@link org.icedamericanomall.gateway.filter.UntrustedHeaderSanitizerFilter} 在链路入口剥离。
 *
 * <p><b>命名与分工（勿与下游 {@code IpUtil} 混用，也勿合并）</b>：
 * <ul>
 *   <li>本类是<b>边缘对端口径</b>：输入 {@link ServerWebExchange}，结果用于网关限流 Key、
 *       AI Token 配额 Key、以及覆写 {@code X-Client-Ip} 下发（{@code headers.set(...)}）。</li>
 *   <li>下游消费口径是 ia-common 的
 *       {@link org.icedamericanomall.common.utils.IpUtil#getIpAddr(jakarta.servlet.http.HttpServletRequest)}：
 *       输入 {@code HttpServletRequest}，优先信任本类经网关覆写的 {@code X-Client-Ip}。</li>
 *   <li><b>不可合并的原因</b>：类型系统不同（Reactive vs Servlet）且信任边界正好相反
 *       （边缘不信任何代理头 / 下游只信边缘注入头）；ia-common 不引 webflux、网关不引 servlet。
 *       类名刻意不再叫 {@code ClientIpUtil}，避免与 {@code IpUtil} 同形不同义。</li>
 * </ul>
 *
 * <p><b>安全红线</b>：任何情况下都不要在本类读取 {@code X-Forwarded-For} / {@code X-Real-IP} /
 * {@code Forwarded}（伪造这些头即可打穿按 IP 的限流，属已修复漏洞）。
 *
 * <p>部署提示：网关若部署在四层负载均衡之后，本方法拿到的是 LB 地址；需要真实客户端 IP 时，
 * 应由 LB 配置 PROXY protocol 或由网关直接对外（不要信任七层 HTTP 代理头）。
 */
public final class EdgePeerIpResolver {

    /** 无法解析对端地址时的兜底标识（限流 Key 与日志共用）。 */
    public static final String UNKNOWN = "unknown";

    private EdgePeerIpResolver() {
    }

    /** 解析客户端 IP，解析失败返回 {@link #UNKNOWN}（不抛异常，保证过滤器链不因取 IP 失败而中断）。 */
    public static String resolve(ServerWebExchange exchange) {
        InetSocketAddress remote = exchange.getRequest().getRemoteAddress();
        if (remote == null || remote.getAddress() == null) {
            return UNKNOWN;
        }
        return remote.getAddress().getHostAddress();
    }
}
