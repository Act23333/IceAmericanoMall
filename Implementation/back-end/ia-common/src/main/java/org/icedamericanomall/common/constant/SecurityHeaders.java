package org.icedamericanomall.common.constant;

/**
 * 可信上下文头 / 代理头名称常量（ia-common 共享内核，跨模块唯一定义）。
 *
 * <p><b>为什么要有本类</b>：网关（{@code gateway-service}）注入、下游（{@code ia-common}
 * 的 {@code UserContextFilter}）消费、Feign 透传（{@code ia-api}）、WebSocket 握手搬运
 * （{@code ai-service}）四处都写「同一批头名」的字面量。任何一处拼写漂移都会变成
 * <b>静默的信任边界失效</b>（网关注入了、下游读的是另一个名字 → 身份丢失）。集中定义
 * 后，「头名」只有一个来源。
 *
 * <p><b>取值逐字锁定</b>：这些字符串是 HTTP 线上协议的一部分，改动即破坏契约。
 * 集成方（如外部网关、压测脚本）依赖既有拼写，<b>不得</b>改名或改大小写。
 *
 * <p><b>信任边界（安全红线，勿误用）</b>：
 * <ul>
 *   <li>{@link #X_USER_ID} / {@link #X_USERNAME} / {@link #X_USER_ROLES} /
 *       {@link #X_USER_PERMISSIONS} / {@link #X_TENANT_ID} / {@link #X_CLIENT_IP}
 *       属于「<b>仅可由网关注入</b>」的可信头 —— 网关 {@code UntrustedHeaderSanitizerFilter}
 *       在链路入口无条件剥离入站同名头，故下游看到的值一定来自网关；下游服务<b>不得</b>自行
 *       用客户端可构造的同名头做鉴权。</li>
 *   <li>{@link #X_FORWARDED_FOR} / {@link #X_REAL_IP} / {@link #FORWARDED} 为「<b>可伪造</b>」
 *       代理头：公网直连时客户端可自由构造，<b>不得</b>作为强安全依据（限流维度一律以
 *       {@link #X_CLIENT_IP} 为准，见 {@code IpUtil}）。</li>
 *   <li>{@link #X_TRACE_ID} 例外：允许上游透传，网关不剥离（链路追踪语义）。</li>
 * </ul>
 */
public final class SecurityHeaders {

    private SecurityHeaders() {
    }

    // ==================== 网关注入的可信用户上下文头 ====================

    /** 用户 ID（网关依据 JWT 的 {@code userId} claim 注入）。 */
    public static final String X_USER_ID = "X-User-Id";

    /** 用户名（网关依据 JWT 的 {@code username} claim 注入，缺失时为空串）。 */
    public static final String X_USERNAME = "X-Username";

    /** 角色（逗号分隔，如 {@code "ROLE_USER,ROLE_VIP"}；网关从权限集合中筛出）。 */
    public static final String X_USER_ROLES = "X-User-Roles";

    /** 权限（逗号分隔，如 {@code "ai:chat,order:read"}）。 */
    public static final String X_USER_PERMISSIONS = "X-User-Permissions";

    /** 租户 ID（下游缺省回退 {@code "default"}）。 */
    public static final String X_TENANT_ID = "X-Tenant-Id";

    /** 客户端 IP（由网关按真实 TCP 对端地址覆写，下游只信任此头）。 */
    public static final String X_CLIENT_IP = "X-Client-Ip";

    // ==================== 链路追踪 ====================

    /** 全链路追踪 ID（允许上游透传，网关不剥离）。 */
    public static final String X_TRACE_ID = "X-Trace-Id";

    // ==================== 可伪造的代理头（仅兜底参考，非安全依据） ====================

    /** 标准代理链头（RFC 7239 之外的 X-Forwarded-For 事实标准）。 */
    public static final String X_FORWARDED_FOR = "X-Forwarded-For";

    /** 反向代理解析出的真实客户端 IP（Nginx 等约定头，可被客户端伪造）。 */
    public static final String X_REAL_IP = "X-Real-IP";

    /** RFC 7239 的 Forwarded 头（等价口径，一并视为可伪造）。 */
    public static final String FORWARDED = "Forwarded";

    /** 历史兼容代理头（部分容器/代理注入，可被客户端伪造）。 */
    public static final String PROXY_CLIENT_IP = "Proxy-Client-IP";

    /** 历史兼容代理头（Servlet 容器常见拼写，可被客户端伪造）。 */
    public static final String HTTP_CLIENT_IP = "HTTP_CLIENT_IP";

    /** 历史兼容代理头（Servlet 容器常见拼写，可被客户端伪造）。 */
    public static final String HTTP_X_FORWARDED_FOR = "HTTP_X_FORWARDED_FOR";

    /** 历史兼容代理头（Servlet 容器常见拼写，可被客户端伪造）。 */
    public static final String HTTP_X_CONNECTING_IP = "HTTP_X_CONNECTING_IP";
}
