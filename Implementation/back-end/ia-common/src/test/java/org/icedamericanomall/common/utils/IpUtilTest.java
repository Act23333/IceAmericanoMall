package org.icedamericanomall.common.utils;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("IpUtil — 客户端 IP 解析")
class IpUtilTest {

    private final HttpServletRequest request = mock(HttpServletRequest.class);

    @Test
    @DisplayName("X-Forwarded-For 多段链路 → 取首个有效段（回归：两段 IP 恰 15 字符的历史缺陷）")
    void shouldReturnFirstSegment_whenForwardedForHasMultiSegments() {
        when(request.getHeader("X-Forwarded-For")).thenReturn("1.2.3.4,5.6.7.8");
        assertEquals("1.2.3.4", IpUtil.getIpAddr(request));
    }

    @Test
    @DisplayName("首段为 unknown → 跳过取下一段")
    void shouldSkipUnknownSegment_whenFirstSegmentUnknown() {
        when(request.getHeader("X-Forwarded-For")).thenReturn("unknown, 1.2.3.4");
        assertEquals("1.2.3.4", IpUtil.getIpAddr(request));
    }

    @Test
    @DisplayName("段两端带空白 → 去空白后返回")
    void shouldTrimSegment_whenSurroundedByBlanks() {
        when(request.getHeader("X-Forwarded-For")).thenReturn(" 1.2.3.4 ");
        assertEquals("1.2.3.4", IpUtil.getIpAddr(request));
    }

    @Test
    @DisplayName("X-Forwarded-For 为 unknown → 依次尝试其余代理头")
    void shouldTryNextProxyHeader_whenForwardedForUnknown() {
        when(request.getHeader("X-Forwarded-For")).thenReturn("unknown");
        when(request.getHeader("Proxy-Client-IP")).thenReturn("10.0.0.1");
        assertEquals("10.0.0.1", IpUtil.getIpAddr(request));
    }

    @Test
    @DisplayName("全部代理头缺失 → 回退 remoteAddr")
    void shouldFallbackToRemoteAddr_whenNoProxyHeaders() {
        when(request.getRemoteAddr()).thenReturn("192.168.1.2");
        assertEquals("192.168.1.2", IpUtil.getIpAddr(request));
    }

    @Test
    @DisplayName("网关 X-Client-Ip 存在 → 优先取它（回归：此前优先取可伪造的 XFF 首段）")
    void shouldPreferGatewayClientIp_whenPresent() {
        when(request.getHeader("X-Client-Ip")).thenReturn("203.0.113.7");
        when(request.getHeader("X-Forwarded-For")).thenReturn("6.6.6.6");

        assertEquals("203.0.113.7", IpUtil.getIpAddr(request));
    }

    @Test
    @DisplayName("X-Client-Ip 为 unknown → 落回代理头链路")
    void shouldFallBackToProxyHeaders_whenGatewayClientIpUnknown() {
        when(request.getHeader("X-Client-Ip")).thenReturn("unknown");
        when(request.getHeader("X-Forwarded-For")).thenReturn("1.2.3.4");

        assertEquals("1.2.3.4", IpUtil.getIpAddr(request));
    }
}
