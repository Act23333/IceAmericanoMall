package org.icedamericanomall.config;

import feign.Logger;
import feign.RequestInterceptor;
import feign.codec.ErrorDecoder;
import org.icedamericanomall.common.constant.SecurityHeaders;
import org.icedamericanomall.common.filter.TraceIdFilter;
import org.icedamericanomall.common.security.UserContext;
import org.icedamericanomall.exception.UpstreamErrorDecoder;
import org.icedamericanomall.fallback.AddressClientFallback;
import org.icedamericanomall.fallback.AfterSaleClientFallback;
import org.icedamericanomall.fallback.AuthContextClientFallback;
import org.icedamericanomall.fallback.BalanceClientFallback;
import org.icedamericanomall.fallback.CartClientFallback;
import org.icedamericanomall.fallback.CouponClientFallback;
import org.icedamericanomall.fallback.FreightTemplateClientFallback;
import org.icedamericanomall.fallback.KnowledgeVectorClientFallback;
import org.icedamericanomall.fallback.LogisticsClientFallback;
import org.icedamericanomall.fallback.NotificationClientFallback;
import org.icedamericanomall.fallback.OrderClientFallback;
import org.icedamericanomall.fallback.PointsClientFallback;
import org.icedamericanomall.fallback.SpuDetailClientFallback;
import org.icedamericanomall.fallback.RefundClientFallback;
import org.icedamericanomall.fallback.SearchClientFallback;
import org.icedamericanomall.fallback.SkuClientFallback;
import org.icedamericanomall.fallback.UserClientFallback;
import org.icedamericanomall.fallback.UserProfileClientFallback;
import org.icedamericanomall.fallback.ItemSearchClientFallback;
import org.icedamericanomall.fallback.VerificationClientFallback;
import org.slf4j.MDC;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;

/**
 * 共享 Feign 全局配置（ia-api 提供给各调用方服务）。
 *
 * <p>使用方式：在服务的启动类上声明
 * {@code @EnableFeignClients(basePackages = "org.icedamericanomall.client", defaultConfiguration = DefaultFeignConfig.class)}；
 * 也可在单个 {@code @FeignClient(configuration = DefaultFeignConfig.class)} 上声明。
 *
 * <p>包路径约定：ia-api 只使用 {@code org.icedamericanomall.client / dto / fallback / config /
 * exception} 五个规范包，<b>不得</b>出现与服务实现同名的子包（如 {@code org.icedamericanomall.auth.*}）——
 * 曾经因该配置类落在 {@code org.icedamericanomall.auth.config}，与 authorization-service 的
 * 基础包形成「拆分包（split package）」，导致组件扫描与 jar 边界混乱，V5.1 已迁回本包。
 * （{@code exception} 包是 2026-10-18 批次 ⑤ 新增：{@code UpstreamException} +
 * {@code UpstreamErrorDecoder} —— 跨服务错误的唯一载体与唯一解码器，仍属「契约侧」，故留在 ia-api。）
 *
 * <p>注意：本类<b>不加</b> {@code @Configuration}/{@code @Component}。ia-api 是被业务服务
 * 以 jar 方式引入的契约模块，其包不在业务服务的组件扫描范围内；配置类需由
 * {@code defaultConfiguration} / {@code configuration} 显式挂载到调用方的容器里。
 *
 * <p><b>V5.3 补强（P1）</b>：19 个 {@code fallbackFactory} 全部在本类登记（V5.5 资金线新增
 * {@code RefundClientFallback} / {@code AfterSaleClientFallback}；V5.6 履约线新增
 * {@code FreightTemplateClientFallback}）。
 * 此前只登记了 {@code UserClientFallback}，其余 14 个仅靠 {@code @Component} —— 而
 * {@code org.icedamericanomall.fallback} 是否在扫描路径内取决于各服务启动类的
 * {@code scanBasePackages}，漏配时「熔断开启 + 降级工厂取不到 Bean」会在启动期直接失败
 * （或更糟：运行期才暴露），属于典型的隐性配置耦合。登记后，「挂载了本配置类的 Feign
 * 上下文」一定拿得到全部降级工厂，与服务的扫描范围无关。
 */
public class DefaultFeignConfig {

    /** Feign 日志级别属性键。 */
    private static final String LOGGING_LEVEL_PROPERTY = "feign.logging.level";

    /**
     * 默认日志级别：{@code BASIC}。
     *
     * <p>此前硬编码 {@code FULL} —— {@code FULL} 会把请求/响应<b>体</b>整体写进日志，
     * 而本项目的 Feign 调用中夹杂手机号、邮箱、验证码、收货地址等 PII，
     * 等于把敏感数据落进日志文件（违反脱敏要求）。默认 {@code BASIC} 只记录
     * 方法、URL、状态码与耗时，足以定位问题。
     * 需要看 body 时显式配置 {@code feign.logging.level=FULL}（仅限 dev 排查）。
     */
    private static final Logger.Level DEFAULT_LOGGING_LEVEL = Logger.Level.BASIC;

    /**
     * 透传「当前登录用户 + 全链路 traceId」两个头。
     *
     * <p><b>① {@code X-User-Id}</b>：下游服务通过该头重建 UserContext。
     * 无登录上下文（内部任务/定时任务）时<b>不注入</b>。
     *
     * <p><b>② {@code X-Trace-Id}</b>（2026-10-18 批次 ⑤b）：把本服务的 MDC traceId 透传给下游。
     * <b>为什么必须透传</b>：服务端错误体的 {@code traceId} 字段取自
     * 「网关 {@code X-Trace-Id} → {@code TraceIdFilter} → MDC」这条现成链路
     * （见 {@code GlobalExceptionHandler#currentTraceId()}）。若 Feign 出站不带该头，
     * 每一跳都会由下游 {@code TraceIdFilter} <b>另生成一个新 UUID</b>——
     * 于是「调用方拿到的 {@code traceId}」与「下游日志里的 {@code traceId}」是两个值，
     * 错误体里的 {@code traceId} 就退化成「无法回捞日志的随机串」，§三·八·3 要求的
     * 「错误体必须足以判定处置」在追踪维度上失效。
     *
     * <p><b>取值口径与容错</b>：只读 {@link TraceIdFilter#TRACE_ID} 这个既有 MDC 键
     * （<b>复用</b>常量，不写字面量 —— 键名漂移会静默失效）；取不到 / 空白 / MDC 实现异常
     * 一律<b>不注入</b>，<b>绝不</b>因追踪号缺失让一次正常调用失败。
     * 头名复用 {@link SecurityHeaders#X_TRACE_ID}（与网关、{@code TraceIdFilter} 同一来源）。
     */
    @Bean
    public RequestInterceptor requestInterceptor() {
        return requestTemplate -> {
            var userInfo = UserContext.getUser();
            if (userInfo != null) {
                requestTemplate.header(SecurityHeaders.X_USER_ID, String.valueOf(userInfo.userId()));
            }
            String traceId = currentTraceId();
            if (traceId != null) {
                requestTemplate.header(SecurityHeaders.X_TRACE_ID, traceId);
            }
        };
    }

    /**
     * 读取本线程 MDC 里的 traceId。
     *
     * <p>取不到一律 {@code null}：异步线程 / 定时任务 / 未装配过滤器时 MDC 为空，
     * 这是<b>正常</b>状态，不是故障 —— 因此只跳过注入，不抛异常、不回落成随机 UUID
     * （随机值会让「调用方以为自己在追这条链」变成假线索；下游自己生成就是它自己的链路）。
     *
     * @return 非空白 traceId，或 {@code null}
     */
    private static String currentTraceId() {
        try {
            String traceId = MDC.get(TraceIdFilter.TRACE_ID);
            return traceId == null || traceId.isBlank() ? null : traceId;
        } catch (RuntimeException ex) {
            // MDC 实现异常（如上下文未装配）不得影响 Feign 调用本身
            return null;
        }
    }

    /**
     * Feign 日志级别：默认 {@code BASIC}，允许通过 {@code feign.logging.level} 覆盖
     * （取值 {@code NONE/BASIC/HEADERS/FULL}，大小写不敏感；非法值回落 BASIC）。
     *
     * @param environment 调用方环境（属性缺失时使用 {@link #DEFAULT_LOGGING_LEVEL}）
     * @return 日志级别
     */
    @Bean
    public Logger.Level feignLoggerLevel(Environment environment) {
        return parseLoggingLevel(environment.getProperty(LOGGING_LEVEL_PROPERTY));
    }

    /** 解析日志级别配置值：空/非法值一律回落 {@code BASIC}（绝不因配置拼错而放开 body 日志）。 */
    private static Logger.Level parseLoggingLevel(String configured) {
        if (configured == null || configured.isBlank()) {
            return DEFAULT_LOGGING_LEVEL;
        }
        for (Logger.Level level : Logger.Level.values()) {
            if (level.name().equalsIgnoreCase(configured.trim())) {
                return level;
            }
        }
        return DEFAULT_LOGGING_LEVEL;
    }

    /**
     * Feign 全局错误解码器 —— 把下游非 2xx 响应类型化成 {@link org.icedamericanomall.exception.UpstreamException}
     * （业务码 / 类别 / 可重试性 / traceId / 原始状态码）。
     *
     * <p><b>为什么注册在这里</b>：这是 ia-api 唯一的共享 Feign 配置类（19 个 fallback 与日志级别
     * 都登记在此），且各服务以 {@code defaultConfiguration = DefaultFeignConfig.class} 或客户端级
     * {@code configuration} 挂载它 —— 挂上就能拿到本解码器，不需要每个服务各写一份。
     *
     * <p><b>它不改变既有降级行为</b>（本批已核对）：19 个 fallback 工厂都只用 {@code cause} 打日志、
     * 然后抛本域异常，<b>没有任何一处</b> {@code instanceof FeignException} 或读
     * {@code FeignException#status()}（全仓 {@code FeignException} 零代码引用），故把异常类型从
     * {@code FeignException} 换成 {@code UpstreamException} 对它们是透明的。唯一按语义分支的是
     * {@code SpuDetailClientFallback}（本批随 §三·八·1 改为「CLIENT 透传」，见其类注释）。
     *
     * <p><b>与熔断的关系</b>：开启 {@code spring.cloud.openfeign.circuitbreaker.enabled=true} 的服务
     * （含 ai-service）里，本解码器抛出的异常同样会被记入熔断器失败计数并触发 fallback ——
     * 语义正确：4xx 也确实是「这次调用没成功」，只是不该被改写成 {@code SYSTEM_BUSY}。
     *
     * @return 共享的上游错误解码器
     */
    @Bean
    public ErrorDecoder upstreamErrorDecoder() {
        return new UpstreamErrorDecoder();
    }

    // ==================== Feign 降级工厂（全部 19 个 Client，逐个显式登记） ====================

    @Bean
    public AddressClientFallback addressClientFallback() {
        return new AddressClientFallback();
    }

    @Bean
    public AfterSaleClientFallback afterSaleClientFallback() {
        return new AfterSaleClientFallback();
    }

    @Bean
    public AuthContextClientFallback authContextClientFallback() {
        return new AuthContextClientFallback();
    }

    @Bean
    public BalanceClientFallback balanceClientFallback() {
        return new BalanceClientFallback();
    }

    @Bean
    public CartClientFallback cartClientFallback() {
        return new CartClientFallback();
    }

    @Bean
    public CouponClientFallback couponClientFallback() {
        return new CouponClientFallback();
    }

    @Bean
    public FreightTemplateClientFallback freightTemplateClientFallback() {
        return new FreightTemplateClientFallback();
    }

    @Bean
    public KnowledgeVectorClientFallback knowledgeVectorClientFallback() {
        return new KnowledgeVectorClientFallback();
    }

    @Bean
    public LogisticsClientFallback logisticsClientFallback() {
        return new LogisticsClientFallback();
    }

    @Bean
    public NotificationClientFallback notificationClientFallback() {
        return new NotificationClientFallback();
    }

    @Bean
    public OrderClientFallback orderClientFallback() {
        return new OrderClientFallback();
    }

    @Bean
    public PointsClientFallback pointsClientFallback() {
        return new PointsClientFallback();
    }

    @Bean
    public SpuDetailClientFallback spuDetailClientFallback() {
        return new SpuDetailClientFallback();
    }

    @Bean
    public RefundClientFallback refundClientFallback() {
        return new RefundClientFallback();
    }

    @Bean
    public SearchClientFallback searchClientFallback() {
        return new SearchClientFallback();
    }

    @Bean
    public SkuClientFallback skuClientFallback() {
        return new SkuClientFallback();
    }

    @Bean
    public UserClientFallback userClientFallback() {
        return new UserClientFallback();
    }

    @Bean
    public UserProfileClientFallback userProfileClientFallback() {
        return new UserProfileClientFallback();
    }

    @Bean
    public VerificationClientFallback verificationClientFallback() {
        return new VerificationClientFallback();
    }

    @Bean
    public ItemSearchClientFallback itemSearchClientFallback() {
        return new ItemSearchClientFallback();
    }
}
