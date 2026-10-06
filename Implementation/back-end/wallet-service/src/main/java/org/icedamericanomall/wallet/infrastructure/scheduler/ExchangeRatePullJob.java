package org.icedamericanomall.wallet.infrastructure.scheduler;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.icedamericanomall.wallet.domain.model.currency.Currency;
import org.icedamericanomall.wallet.domain.model.currency.ExchangeRate;
import org.icedamericanomall.wallet.domain.repository.CurrencyRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestOperations;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 汇率定时拉取 Job（基础设施层，F16 Phase 1，契约 §4.13.8g 注记 + PRD §17.6 D2）。
 *
 * <p><b>开关与节奏</b>：{@code currency.rate.pull.enabled} 默认 {@code false}（不注册本 Bean，
 * {@code @ConditionalOnProperty} 守卫）；开启后按 {@code currency.rate.pull.cron}（默认每日凌晨
 * 3 点低峰）拉取一次。{@code currency.rate.pull.api-url} 未配置（默认空）→ 跳过并告警。
 *
 * <p><b>Phase 1 拉取实现</b>：读全部启用币种 → 调用可配置汇率源（开放汇率 API 常见形态
 * {@code {"base_code":"CNY","rates":{"USD":0.1403,...}}}，取 {@code rates} 对象）→
 * 按币种解析为定点整数 → upsert（source=API）。
 *
 * <p><b>失败保留上次值（D2 降级纪律）</b>：HTTP 失败 / 响应形态不符 / 单币解析非法 /
 * 汇率源缺失某币种，均只 {@code log.warn} 告警并保留该币上次汇率（stale 但可用），
 * 绝不抛出阻断调度器。
 *
 * <p><b>定点整数纪律（NFR §17.7.1 硬约束的唯一豁免边界）</b>：汇率源响应是小数文本，
 * 解析必须 {@code BigDecimal} 读入后立即 {@code movePointRight(6).longValueExact()} 转定点
 * long，转换完即弃用——<b>严禁 double 路径</b>（专用 ObjectMapper 开启
 * {@code USE_BIG_DECIMAL_FOR_FLOATS}，从 JSON 文本直接构造精确十进制，不走 Double 中转）。
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "currency.rate.pull.enabled", havingValue = "true")
public class ExchangeRatePullJob {

    /** 拉取解析专用 mapper：浮点一律读为 BigDecimal，杜绝 double 解析路径（NFR §17.7.1）。 */
    private static final ObjectMapper PULL_MAPPER = new ObjectMapper()
            .configure(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS, true);

    /** 汇率源连接超时（毫秒）—— 每日低峰一次的旁路任务，失败即保留上次值，不必久等。 */
    private static final int CONNECT_TIMEOUT_MS = 3_000;

    /** 汇率源读取超时（毫秒）。 */
    private static final int READ_TIMEOUT_MS = 5_000;

    /** 响应中汇率表节点名（开放汇率 API 常见形态）。 */
    private static final String RATES_FIELD = "rates";

    private final CurrencyRepository currencyRepository;
    private final RestOperations restOperations;
    private final String apiUrl;

    /** Spring 装配入口（默认 RestTemplate，超时按旁路任务口径收敛）。 */
    @Autowired
    public ExchangeRatePullJob(CurrencyRepository currencyRepository,
                               @Value("${currency.rate.pull.api-url:}") String apiUrl) {
        this(currencyRepository, apiUrl, defaultRestTemplate());
    }

    /** 测试装配入口（注入可 mock 的 {@link RestOperations}）。 */
    ExchangeRatePullJob(CurrencyRepository currencyRepository, String apiUrl, RestOperations restOperations) {
        this.currencyRepository = currencyRepository;
        this.apiUrl = apiUrl;
        this.restOperations = restOperations;
    }

    /** 调度入口：任何异常只告警不外抛（@Scheduled 抛异常会打断后续调度语义）。 */
    @Scheduled(cron = "${currency.rate.pull.cron:0 0 3 * * ?}")
    public void pullDaily() {
        try {
            pullOnce();
        } catch (Exception e) {
            log.warn("[currency][pull][fail] 汇率拉取失败，全部启用币种保留上次值（D2）: cause={}", e.getMessage());
        }
    }

    /**
     * 单次拉取：空配置跳过 → 启用币种 → HTTP 取数 → 逐币定点转换 upsert。
     *
     * @throws IOException 响应不可读/形态不符（由 {@link #pullDaily()} 统一兜底为保留上次值）
     * @throws RestClientException HTTP 调用失败（同上）
     */
    void pullOnce() throws IOException {
        if (apiUrl == null || apiUrl.isBlank()) {
            log.warn("[currency][pull][skip] 未配置 currency.rate.pull.api-url，本次跳过（启用币种保留上次汇率）");
            return;
        }
        List<Currency> currencies = currencyRepository.listEnabledWithRate();
        if (currencies.isEmpty()) {
            log.info("[currency][pull][skip] 无启用币种，无需拉取");
            return;
        }
        String body = restOperations.getForObject(apiUrl, String.class);
        JsonNode rates = readRates(body);
        LocalDateTime effectTime = LocalDateTime.now();
        int updated = 0;
        for (Currency currency : currencies) {
            if (updateRateFrom(rates, currency, effectTime)) {
                updated++;
            }
        }
        log.info("[currency][pull][ok] 启用币种={}, 更新成功={}, 保留上次值={}",
                currencies.size(), updated, currencies.size() - updated);
    }

    /** 逐币定点转换并 upsert；解析非法/汇率源缺失该币种 → 保留上次值并告警（D2）。 */
    private boolean updateRateFrom(JsonNode rates, Currency currency, LocalDateTime effectTime) {
        String code = currency.code();
        JsonNode node = rates.path(code);
        if (node.isMissingNode() || node.isNull()) {
            log.warn("[currency][pull][stale] 汇率源缺少 {} 报价，保留上次值（D2）", code);
            return false;
        }
        try {
            // 定点整数纪律唯一豁免边界：BigDecimal 仅在此解析瞬间存在，立即转定点 long（×10⁶）
            // 后弃用，全程不经过 double（NFR §17.7.1；longValueExact 防溢出静默截断）。
            BigDecimal decimal = new BigDecimal(node.asText().trim());
            long scaled = decimal.movePointRight(ExchangeRate.SCALE_DIGITS).longValueExact();
            if (scaled <= 0) {
                log.warn("[currency][pull][stale] {} 汇率值非正数({})，保留上次值（D2）", code, node.asText());
                return false;
            }
            currencyRepository.updateRate(ExchangeRate.apiPull(code, scaled, effectTime));
            return true;
        } catch (NumberFormatException | ArithmeticException e) {
            log.warn("[currency][pull][stale] {} 汇率值非法({})，保留上次值（D2）: cause={}",
                    code, node.asText(), e.getMessage());
            return false;
        }
    }

    /** 取响应中的 rates 对象（开放汇率 API 常见形态；形态不符视为本次拉取失败）。 */
    private JsonNode readRates(String body) throws IOException {
        JsonNode rates = PULL_MAPPER.readTree(body).path(RATES_FIELD);
        if (!rates.isObject()) {
            throw new IOException("汇率源响应缺少 " + RATES_FIELD + " 对象，形态不符");
        }
        return rates;
    }

    /** 默认汇率源客户端（旁路任务口径：连接/读取超时收敛，失败保留上次值）。 */
    private static RestTemplate defaultRestTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(CONNECT_TIMEOUT_MS);
        factory.setReadTimeout(READ_TIMEOUT_MS);
        return new RestTemplate(factory);
    }
}
