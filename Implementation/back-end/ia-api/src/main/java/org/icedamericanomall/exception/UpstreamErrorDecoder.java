package org.icedamericanomall.exception;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import feign.Response;
import feign.codec.ErrorDecoder;
import lombok.extern.slf4j.Slf4j;
import org.icedamericanomall.common.api.ErrorInfo;
import org.icedamericanomall.common.enums.ErrorCategory;
import org.icedamericanomall.common.enums.ErrorCode;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Optional;

/**
 * Feign 全局错误解码器 —— 把下游非 2xx 响应解析成 {@link ErrorInfo}，再抛 {@link UpstreamException}。
 *
 * <p><b>为什么需要它</b>（{@code collaboration/09-constraints.md} §三·八·1/3）：Feign 默认的
 * {@code ErrorDecoder.Default} 只给 {@code FeignException}（状态码 + 原始报文），调用方无法
 * 判定「下游说不存在（4xx）」和「下游挂了（5xx）」，实践中就退化成一律 {@code SYSTEM_BUSY}
 * —— 本仓实测过：商品详情下游 404 被降级成「商品服务暂不可用」，AI 助手于是把
 * 「商品不存在」说成「服务不可用」。
 *
 * <p><b>解析与降级规则</b>：
 * <table border="1">
 *   <caption>报文 → 处置</caption>
 *   <tr><th>下游响应</th><th>解析结果</th></tr>
 *   <tr>
 *     <td>JSON 且含数值 {@code code}</td>
 *     <td>业务码 / 文案原样保留；{@code category} 优先用下游给的（已类型化的下游），
 *         缺省则按 HTTP 状态码派生（混合部署期必须能工作）</td>
 *   </tr>
 *   <tr>
 *     <td>空体 / 非 JSON / 无 {@code code}</td>
 *     <td><b>降级</b>为 {@code TRANSIENT + retryable=true}（未知一律按可重试处理，
 *         <b>不得</b>当成功，也不得伪装成业务事实）</td>
 *   </tr>
 * </table>
 *
 * <p><b>{@code retryable} 不采信下游的报文值</b>：全仓唯一派生口径是
 * {@code retryable = (category == TRANSIENT)}（{@link ErrorCategory#isRetryable()}）。
 * 若下游写了 {@code category=CLIENT, retryable=true} 这类自相矛盾的报文，本解码器按
 * {@code category} <b>重新派生</b> —— 让「一处矛盾」停在解码层，不传染成「两个调用方各自
 * 采信其中一半」。
 *
 * <p><b>PII / 体积纪律</b>：只读下游错误体的前 {@value #MAX_BODY_BYTES} 字节（错误体本应很小，
 * 上限只为防异常大响应）；原始报文<b>不进日志</b>（内部面虽已脱敏，但错误体可能夹带业务数据），
 * 日志只记 {@code methodKey / 状态码 / 类别}。
 *
 * <p><b>注册位置</b>：{@code DefaultFeignConfig#upstreamErrorDecoder()} —— {@code ErrorDecoder}
 * 是<b>客户端级</b>全局配置（每个 Feign 客户端上下文各一份），注册在共享配置类里即可覆盖
 * {@code FeignClientsConfiguration} 的默认实现（{@code @ConditionalOnMissingBean} 回退）。
 */
@Slf4j
public class UpstreamErrorDecoder implements ErrorDecoder {

    /** 下游错误体的读取上限（字节）。错误体不该这么大；超限截断不影响 code/category 解析。 */
    private static final int MAX_BODY_BYTES = 8192;

    /** 只读、无 Spring 依赖 —— ia-api 是被各服务当 jar 引入的契约模块，不能假设容器里有定制 ObjectMapper。 */
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final String FIELD_CODE = "code";
    private static final String FIELD_MSG = "msg";
    private static final String FIELD_MESSAGE = "message";
    private static final String FIELD_CATEGORY = "category";
    private static final String FIELD_TRACE_ID = "traceId";
    private static final String FIELD_TRACE_ID_SNAKE = "trace_id";

    /** 降级文案：不给调用方假信息，只说「无法解析」。 */
    private static final String DEGRADED_MSG_TEMPLATE = "下游响应无法解析（HTTP %d），按暂时性故障处理";

    /**
     * 解码非 2xx 响应。
     *
     * @param methodKey Feign 方法标识（{@code 接口#方法(参数类型)}），仅用于日志
     * @param response  下游响应（body 可能为 {@code null}）
     * @return {@link UpstreamException}（永不返回 {@code null}，永不吞成成功）
     */
    @Override
    public Exception decode(String methodKey, Response response) {
        int status = response.status();
        ErrorInfo errorInfo = toErrorInfo(readBody(response), status);
        if (log.isDebugEnabled()) {
            log.debug("Feign 非 2xx: methodKey={}, status={}, category={}, code={}, retryable={}",
                    methodKey, status, errorInfo.category(), errorInfo.code(), errorInfo.retryable());
        }
        return new UpstreamException(errorInfo, status);
    }

    /**
     * 报文 → 错误体（解析失败即按未知降级）。
     *
     * @param body   下游响应体（可为 {@code null}）
     * @param status 下游 HTTP 状态码
     * @return 错误体（永不为 {@code null}）
     */
    static ErrorInfo toErrorInfo(String body, int status) {
        JsonNode root = readJson(body);
        if (root == null || !hasNumericCode(root)) {
            return degraded(status);
        }
        ErrorCategory category = parseCategory(root)
                .orElseGet(() -> ErrorCategory.ofStatus(status));
        return ErrorInfo.of(
                root.get(FIELD_CODE).asInt(),
                firstNonBlank(text(root, FIELD_MSG), text(root, FIELD_MESSAGE),
                        "下游返回 HTTP " + status),
                category,
                firstNonBlank(text(root, FIELD_TRACE_ID), text(root, FIELD_TRACE_ID_SNAKE)));
    }

    /**
     * 降级结果：未知一律 {@code TRANSIENT + retryable=true}。
     *
     * <p>为何不是 {@code BUG}：解析不出错误体既可能是「下游崩了吐 HTML」，也可能是
     * 「网关返回了 503 空体」——<b>未知</b>时按可重试处理最不坏；当成 BUG 会立刻制造
     * 无意义告警，当成成功则是最坏结果（§三·八·2 禁止）。
     */
    private static ErrorInfo degraded(int status) {
        return ErrorInfo.of(
                ErrorCode.SYSTEM_BUSY.getCode(),
                String.format(Locale.ROOT, DEGRADED_MSG_TEMPLATE, status),
                ErrorCategory.TRANSIENT,
                null);
    }

    /** 解析错误体 JSON；空体 / 非 JSON / 非对象一律 {@code null}。 */
    private static JsonNode readJson(String body) {
        if (body == null || body.isBlank()) {
            return null;
        }
        try {
            JsonNode root = MAPPER.readTree(body);
            return root != null && root.isObject() ? root : null;
        } catch (Exception ex) {
            return null;
        }
    }

    /** 报文是否带「可用于业务判断」的数值 code。 */
    private static boolean hasNumericCode(JsonNode root) {
        JsonNode codeNode = root.get(FIELD_CODE);
        if (codeNode == null || codeNode.isNull()) {
            return false;
        }
        return codeNode.isNumber() || codeNode.canConvertToInt();
    }

    /** 读下游给的 {@code category}：缺省 / 非法值一律空（交给状态码派生）。 */
    private static Optional<ErrorCategory> parseCategory(JsonNode root) {
        String raw = text(root, FIELD_CATEGORY);
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(ErrorCategory.valueOf(raw.trim().toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException ex) {
            return Optional.empty();
        }
    }

    /** 取字符串字段（数字节点也按文本返回，便于容错）；缺失/空返回 {@code null}。 */
    private static String text(JsonNode root, String field) {
        JsonNode node = root.get(field);
        if (node == null || node.isNull()) {
            return null;
        }
        String value = node.asText();
        return value == null || value.isBlank() ? null : value;
    }

    /** 返回第一个非空白值，全空则 {@code null}。 */
    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    /** 读取响应体（限量）；任何 IO 异常都按「无错误体」处理，绝不让解码本身失败。 */
    private static String readBody(Response response) {
        if (response.body() == null) {
            return null;
        }
        try (InputStream in = response.body().asInputStream()) {
            byte[] head = in.readNBytes(MAX_BODY_BYTES);
            return new String(head, StandardCharsets.UTF_8);
        } catch (Exception ex) {
            return null;
        }
    }
}
