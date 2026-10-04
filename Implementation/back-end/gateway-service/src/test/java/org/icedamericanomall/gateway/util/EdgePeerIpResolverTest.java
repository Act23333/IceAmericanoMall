package org.icedamericanomall.gateway.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;

import java.net.InetSocketAddress;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 边缘对端地址解析单元测试 —— 锁定两条不变量：
 * ①只取 TCP 对端地址；②<b>绝不</b>读入站可伪造代理头（XFF/X-Real-IP/Forwarded）。
 */
@DisplayName("EdgePeerIpResolver — 边缘对端地址解析")
class EdgePeerIpResolverTest {

    private static final String REAL_REMOTE_IP = "10.1.2.3";
    private static final String FORGED_IP = "1.2.3.4";

    @Test
    @DisplayName("取 TCP 对端地址 —— 请求头里的 X-Forwarded-For/X-Real-IP/Forwarded 全部无视")
    void shouldReturnRemoteAddress_whenSpoofableProxyHeadersPresent() {
        ServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/auth/login")
                        .remoteAddress(new InetSocketAddress(REAL_REMOTE_IP, 12345))
                        .header("X-Forwarded-For", FORGED_IP + ", 5.6.7.8")
                        .header("X-Real-IP", FORGED_IP)
                        .header("Forwarded", "for=" + FORGED_IP));

        assertEquals(REAL_REMOTE_IP, EdgePeerIpResolver.resolve(exchange));
    }

    @Test
    @DisplayName("对端地址缺失 —— 返回 UNKNOWN 哨兵（同桶限流，不抛异常）")
    void shouldReturnUnknown_whenRemoteAddressMissing() {
        ServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/auth/login"));

        assertEquals(EdgePeerIpResolver.UNKNOWN, EdgePeerIpResolver.resolve(exchange));
    }
}
