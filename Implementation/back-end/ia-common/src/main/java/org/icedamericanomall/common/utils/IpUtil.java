package org.icedamericanomall.common.utils;


import jakarta.servlet.http.HttpServletRequest;
import org.icedamericanomall.common.constant.SecurityHeaders;

/**
 * 客户端 IP 解析工具（ia-common 共享）。
 *
 * <p><b>信任边界（重要）</b>：只有网关注入的 {@code X-Client-Ip} 可信。
 * gateway-service 的 {@code UntrustedHeaderSanitizerFilter}（order -1100，链路最早）
 * 会剥离入站的 {@code X-Client-Ip} / {@code X-Forwarded-For} / {@code X-Real-IP} /
 * {@code Forwarded}，然后把 {@code X-Client-Ip} 覆写为真实 TCP 对端地址；
 * 下游服务据此取值即可。因此解析优先级为：
 * <ol>
 *   <li>{@code X-Client-Ip} —— 可信（网关注入，入站同名头已被剥离）；</li>
 *   <li>{@code X-Forwarded-For} 等代理头 —— 仅可信代理链内可参考（兜底兼容），
 *       公网直连场景其首段可被客户端伪造，不得作为强安全依据；</li>
 *   <li>{@code remoteAddr} —— 兜底。</li>
 * </ol>
 *
 * <p><b>为何必须优先 X-Client-Ip（V5.3 修正，P2 缺陷）</b>：此前实现优先取
 * {@code X-Forwarded-For} 首段，而下游服务直接暴露（或经其他代理）时该头由客户端自由控制 ——
 * 攻击者每次请求伪造一个新 XFF，就能绕过所有 {@code @RateLimit(key = "ip")} 的接口
 * （验证码发送、AI 对话等按 IP 限流的入口首当其冲）。改为优先信任网关覆写的
 * {@code X-Client-Ip} 后，限流维度才真正落在「真实来源」上。
 *
 * <p><b>与网关侧解析器的分工（勿合并，勿互相替代）</b>：网关侧的 {@code EdgePeerIpResolver}
 * （gateway-service，本地类 {@code org.icedamericanomall.gateway.util.EdgePeerIpResolver}）是
 * <b>边缘对端口径</b> —— 输入是 {@code ServerWebExchange}，<b>只取 TCP 对端地址、绝不读任何
 * 入站代理头</b>（伪造 XFF 打穿限流即由此堵住），并负责把结果覆写进 {@code X-Client-Ip} 下发。
 * 本类则是<b>下游消费口径</b> —— 输入是 {@code HttpServletRequest}，信任「网关注入的头」。
 * 二者不可合并：类型系统不同（Servlet vs Reactive，ia-common 不引 webflux、网关不引 servlet），
 * 且信任边界正好相反。**任何情况下都不要让网关侧改为信任 XFF**（那是已被修复的安全漏洞）。
 *
 * <p><b>代理头回落分支的存活说明（复核结论：非死代码，保留）</b>：经网关的流量上，
 * {@code UntrustedHeaderSanitizerFilter}（order -1100）恒覆写 {@code X-Client-Ip}，
 * 该分支通常不命中；但仍有两类真实路径会落到这里 ——
 * <ol>
 *   <li>网关侧 {@code resolve} 取不到对端地址时下发值 {@code unknown}，本类判定为「无效」后继续回落；</li>
 *   <li>本地开发/灰度直连服务端口（绕过网关）时不存在 {@code X-Client-Ip}，此时 remoteAddr 兜底生效。</li>
 * </ol>
 * 因此保留该分支并在回落顺序上保持「代理头 → remoteAddr」，但不作为强安全依据（见上）。
 */
public final class IpUtil {

    private static final String UNKNOWN = "unknown";

    /** 网关注入的客户端 IP 头（可信，优先级最高）。 */
    private static final String GATEWAY_CLIENT_IP_HEADER = SecurityHeaders.X_CLIENT_IP;

    /** 依次尝试的代理头（兜底兼容；{@code X-Forwarded-For} 为网关透传标准头，其余为历史兼容）。 */
    private static final String[] PROXY_HEADERS = {
            SecurityHeaders.X_FORWARDED_FOR,
            SecurityHeaders.PROXY_CLIENT_IP,
            SecurityHeaders.HTTP_CLIENT_IP,
            SecurityHeaders.HTTP_X_FORWARDED_FOR,
            SecurityHeaders.HTTP_X_CONNECTING_IP
    };

    private IpUtil() {
    }

    /**
     * 解析客户端真实 IP。
     *
     * @param request 当前请求（非空）
     * @return 客户端 IP：优先 {@code X-Client-Ip}；否则按代理头取首个有效段并去空白；
     *         全部未命中回退 {@code remoteAddr}
     */
    public static String getIpAddr(HttpServletRequest request) {
        String gatewayClientIp = request.getHeader(GATEWAY_CLIENT_IP_HEADER);
        if (!isUnknown(gatewayClientIp)) {
            return firstSegment(gatewayClientIp);
        }
        for (String header : PROXY_HEADERS) {
            String value = request.getHeader(header);
            if (isUnknown(value)) {
                continue;
            }
            return firstSegment(value);
        }
        return request.getRemoteAddr();
    }

    private static boolean isUnknown(String value) {
        return value == null || value.isEmpty() || UNKNOWN.equalsIgnoreCase(value.trim());
    }

    /**
     * 取代理链第一段（最早一跳）。
     *
     * <p>历史实现按 {@code length() > 15} 才按逗号拆分，两段 IP 恰 15 字符时
     * 整串返回（如 {@code "1.2.3.4,5.6.7.8"}），属于缺陷，不再保留。
     */
    private static String firstSegment(String forwarded) {
        for (String segment : forwarded.split(",")) {
            String trimmed = segment.trim();
            if (!trimmed.isEmpty() && !UNKNOWN.equalsIgnoreCase(trimmed)) {
                return trimmed;
            }
        }
        return forwarded.trim();
    }
}
