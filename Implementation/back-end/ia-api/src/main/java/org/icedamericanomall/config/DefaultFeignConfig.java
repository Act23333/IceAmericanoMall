package org.icedamericanomall.config;

import feign.Logger;
import feign.RequestInterceptor;
import org.icedamericanomall.common.constant.SecurityHeaders;
import org.icedamericanomall.common.security.UserContext;
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
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;

/**
 * 共享 Feign 全局配置（ia-api 提供给各调用方服务）。
 *
 * <p>使用方式：在服务的启动类上声明
 * {@code @EnableFeignClients(basePackages = "org.icedamericanomall.client", defaultConfiguration = DefaultFeignConfig.class)}；
 * 也可在单个 {@code @FeignClient(configuration = DefaultFeignConfig.class)} 上声明。
 *
 * <p>包路径约定：ia-api 只使用 {@code org.icedamericanomall.client / dto / fallback / config}
 * 四个规范包，<b>不得</b>出现与服务实现同名的子包（如 {@code org.icedamericanomall.auth.*}）——
 * 曾经因该配置类落在 {@code org.icedamericanomall.auth.config}，与 authorization-service 的
 * 基础包形成「拆分包（split package）」，导致组件扫描与 jar 边界混乱，V5.1 已迁回本包。
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
     * 透传当前登录用户标识：下游服务通过 {@code X-User-Id} 头重建 UserContext。
     * 无登录上下文（内部任务/定时任务）时不注入。
     */
    @Bean
    public RequestInterceptor requestInterceptor() {
        return requestTemplate -> {
            var userInfo = UserContext.getUser();
            if (userInfo != null) {
                requestTemplate.header(SecurityHeaders.X_USER_ID, String.valueOf(userInfo.userId()));
            }
        };
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
