package org.icedamericanomall.common.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import org.icedamericanomall.common.enums.ErrorCategory;
import org.icedamericanomall.common.enums.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link ErrorInfo} 线上形态 —— 锁死两件事：
 *
 * <ol>
 *   <li><b>键名与键集合稳定</b>：内部错误体的线上键恒为
 *       {@code code / msg / category / retryable / traceId}。<b>无论</b>服务端 Jackson 命名策略是
 *       默认还是 {@code SNAKE_CASE}、无论配置是否开了 {@code non_null}，都必须如此 ——
 *       否则 {@code UpstreamErrorDecoder} 会在某些服务上解析不到 {@code traceId}，
 *       静默降级为「未知」（透传铁律失效）。</li>
 *   <li><b>类别与可重试性不可手填</b>：构造入口只有派生，{@code retryable} 恒等于
 *       {@code category == TRANSIENT}，入参矛盾也无法造出「CLIENT 且可重试」的怪物。</li>
 * </ol>
 */
@DisplayName("ErrorInfo — 内部错误体线上形态（键名 / 键集合 / 派生）")
class ErrorInfoTest {

    /** 内部错误体的<b>契约键集合</b>（§三·八·3）。顺序无关，但成员逐字固定。 */
    private static final Set<String> CONTRACT_KEYS =
            new TreeSet<>(List.of("code", "msg", "category", "retryable", "traceId"));

    private static final String EXPECTED_JSON =
            "{\"code\":1103,\"msg\":\"商品不存在或已删除\",\"category\":\"CLIENT\","
                    + "\"retryable\":false,\"traceId\":\"trace-abc\"}";

    @Test
    @DisplayName("派生 — 1103(404) → CLIENT / 不可重试；1014(429) → TRANSIENT / 可重试")
    void shouldDeriveCategoryAndRetryableFromErrorCode() {
        ErrorInfo notFound = ErrorInfo.of(ErrorCode.NO_RESOURCE_FOUND, "商品不存在或已删除", "trace-abc");
        assertEquals(1103, notFound.code());
        assertSame(ErrorCategory.CLIENT, notFound.category());
        assertFalse(notFound.retryable());
        assertEquals("trace-abc", notFound.traceId());

        ErrorInfo rateLimited = ErrorInfo.of(ErrorCode.FREQUENT_ERROR, "操作过于频繁", null);
        assertSame(ErrorCategory.TRANSIENT, rateLimited.category());
        assertTrue(rateLimited.retryable());
        assertNull(rateLimited.traceId(), "取不到 traceId 时为 null，绝不抛异常");
    }

    @Test
    @DisplayName("矛盾入参 — 显式声称「CLIENT 且 retryable=true」也造不出来（retryable 只由 category 派生）")
    void shouldIgnoreContradictoryRetryable() {
        ErrorInfo info = ErrorInfo.of(1103, "商品不存在", ErrorCategory.CLIENT, "t-1");
        assertFalse(info.retryable(), "CLIENT 一律不可重试（重试客户错误是放大故障）");

        // category 缺省 → TRANSIENT（未知按可重试处理，绝不当成功）
        ErrorInfo unknown = ErrorInfo.of(1103, "未知", null, null);
        assertSame(ErrorCategory.TRANSIENT, unknown.category());
        assertTrue(unknown.retryable());
    }

    @Test
    @DisplayName("线上键 — 默认命名策略下序列化为 code/msg/category/retryable/traceId（category 渲染为枚举名）")
    void shouldSerializeWithStableKeysUnderDefaultNaming() throws Exception {
        String json = new ObjectMapper()
                .writeValueAsString(ErrorInfo.of(ErrorCode.NO_RESOURCE_FOUND, "商品不存在或已删除", "trace-abc"));
        assertEquals(EXPECTED_JSON, json);
    }

    @Test
    @DisplayName("线上键 — SNAKE_CASE 服务下 traceId 仍是 traceId（不得变成 trace_id，否则跨服务解析失败）")
    void shouldKeepTraceIdKeyUnderSnakeCaseNaming() throws Exception {
        ObjectMapper snake = new ObjectMapper()
                .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);
        JsonNode node = snake.readTree(snake.writeValueAsString(
                ErrorInfo.of(ErrorCode.NO_RESOURCE_FOUND, "商品不存在或已删除", "trace-abc")));

        assertEquals(CONTRACT_KEYS, fieldNames(node),
                "线上键集合必须与命名策略无关（@JsonProperty 显式钉名压过全局策略）");
        assertEquals("trace-abc", node.get("traceId").asText());
        assertTrue(node.get("trace_id") == null, "不得出现 snake 变体键");
    }

    @Test
    @DisplayName("键集合 — traceId=null 时该键仍存在（部分服务 global non_null 会让它消失，故类级 ALWAYS）")
    void shouldAlwaysIncludeTraceIdKeyEvenWhenNull() throws Exception {
        ObjectMapper snakeNonEmpty = new ObjectMapper()
                .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
                .setSerializationInclusion(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_EMPTY);
        JsonNode node = snakeNonEmpty.readTree(
                snakeNonEmpty.writeValueAsString(ErrorInfo.of(ErrorCode.SYSTEM_BUSY, "系统繁忙", null)));

        assertEquals(CONTRACT_KEYS, fieldNames(node), "内部错误体的键集合必须稳定，traceId 取不到时输出 null");
        assertTrue(node.get("traceId").isNull());
    }

    @Test
    @DisplayName("反序列化 — SNAKE_CASE 变体 trace_id 不影响其余字段（生产 FAIL_ON_UNKNOWN_PROPERTIES=false）")
    void shouldDeserializeSnakeCaseVariant() throws Exception {
        String json = "{\"code\":1103,\"msg\":\"商品不存在\",\"category\":\"CLIENT\","
                + "\"retryable\":false,\"trace_id\":\"trace-xyz\"}";
        // 与生产同口径：忽略未知键（Spring Boot 默认 FAIL_ON_UNKNOWN_PROPERTIES=false）
        ErrorInfo decoded = new ObjectMapper()
                .configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                .readValue(json, ErrorInfo.class);
        assertEquals(1103, decoded.code());
        assertSame(ErrorCategory.CLIENT, decoded.category());
        assertFalse(decoded.retryable());
        // 本类只认显式钉名的 traceId；trace_id 的容错在 UpstreamErrorDecoder 里用 JsonNode 双读
        // （见 UpstreamErrorDecoderTest#shouldReadSnakeCaseTraceId）—— 两处职责不重叠。
        assertNull(decoded.traceId());
    }

    @Test
    @DisplayName("字段集合 — 恰好 5 个实例字段（新增字段即需同步解码侧与契约，锁死以免静默漂移）")
    void shouldHaveExactlyContractFields() {
        List<String> names = new ArrayList<>();
        for (Field field : ErrorInfo.class.getDeclaredFields()) {
            if (!Modifier.isStatic(field.getModifiers()) && !field.isSynthetic()) {
                names.add(field.getName());
            }
        }
        names.sort(Comparator.naturalOrder());
        assertEquals(List.of("category", "code", "msg", "retryable", "traceId"), names);
    }

    private static Set<String> fieldNames(JsonNode node) {
        Set<String> names = new TreeSet<>();
        node.fieldNames().forEachRemaining(names::add);
        return names;
    }

    /** 与 {@link #shouldHaveExactlyContractFields()} 互补：record 组件即报文契约面。 */
    @Test
    @DisplayName("record 组件 — 5 个（与 CONTRACT_KEYS 一一对应）")
    void shouldHaveFiveRecordComponents() {
        assertTrue(ErrorInfo.class.isRecord(), "ErrorInfo 必须是不可变值对象（record）");
        assertEquals(CONTRACT_KEYS.size(), ErrorInfo.class.getRecordComponents().length);
    }
}
